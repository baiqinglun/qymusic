package com.qymusic.player.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

class MusicScanner(private val context: Context) {
    private val metadataSemaphore = Semaphore(permits = METADATA_CONCURRENCY)

    suspend fun scan(
        folders: List<MusicFolder>,
        cached: Map<String, CachedTrack> = emptyMap(),
    ): List<CachedTrack> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<FileEntry>()
        val seen = mutableSetOf<String>()

        folders.forEach { folder ->
            val rootDocumentId = runCatching {
                DocumentsContract.getTreeDocumentId(folder.uri)
            }.getOrNull() ?: return@forEach
            collectEntries(
                treeUri = folder.uri,
                documentId = rootDocumentId,
                folder = folder,
                pathSegments = emptyList(),
                entries = entries,
                seen = seen,
            )
        }

        // 读取元数据是最慢的一步，这里并行跑；没变化的文件直接复用上次结果。
        val scanned = coroutineScope {
            entries.map { entry ->
                async {
                    val cachedTrack = cached[entry.uri.toString()]
                    if (cachedTrack != null &&
                        cachedTrack.fileSize == entry.fileSize &&
                        cachedTrack.modifiedAt == entry.modifiedAt
                    ) {
                        cachedTrack
                    } else {
                        metadataSemaphore.withPermit { readTrack(entry) }
                    }
                }
            }.awaitAll()
        }

        val collator = Collator.getInstance(Locale.getDefault())
        scanned.sortedWith { first, second ->
            collator.compare(first.track.title, second.track.title)
                .takeIf { it != 0 }
                ?: collator.compare(first.track.relativePath, second.track.relativePath)
        }
    }

    private fun collectEntries(
        treeUri: Uri,
        documentId: String,
        folder: MusicFolder,
        pathSegments: List<String>,
        entries: MutableList<FileEntry>,
        seen: MutableSet<String>,
    ) {
        val children = queryChildren(treeUri, documentId)
        val lyricsByBaseName = children
            .asSequence()
            .filter { !it.isDirectory && extensionOf(it.name) == "lrc" }
            .associateBy { baseName(it.name).lowercase(Locale.ROOT) }

        children.forEach { child ->
            if (child.isDirectory) {
                if (child.name.isBlank()) return@forEach
                collectEntries(
                    treeUri = treeUri,
                    documentId = child.documentId,
                    folder = folder,
                    pathSegments = pathSegments + child.name,
                    entries = entries,
                    seen = seen,
                )
            } else if (extensionOf(child.name) in SUPPORTED_AUDIO_EXTENSIONS) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
                if (!seen.add(uri.toString())) return@forEach
                val fileName = child.name.takeIf { it.isNotBlank() } ?: return@forEach
                val lyricDocumentId = lyricsByBaseName[
                    baseName(fileName).lowercase(Locale.ROOT),
                ]?.documentId
                entries += FileEntry(
                    uri = uri,
                    fileName = fileName,
                    folderName = folder.name,
                    relativePath = (pathSegments + fileName).joinToString("/"),
                    lyricUri = lyricDocumentId?.let { id ->
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    },
                    fileSize = child.size,
                    modifiedAt = child.modifiedAt,
                )
            }
        }
    }

    /** 一次 query 取回整个目录的子项，比 DocumentFile 逐个查询快得多。 */
    private fun queryChildren(treeUri: Uri, documentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            documentId,
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val result = mutableListOf<DocumentEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)
        }.getOrNull()?.use { cursor ->
            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(2).orEmpty()
                result += DocumentEntry(
                    documentId = cursor.getString(0).orEmpty(),
                    name = cursor.getString(1).orEmpty(),
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = cursor.getLong(3),
                    modifiedAt = cursor.getLong(4),
                )
            }
        }
        return result
    }

    private data class DocumentEntry(
        val documentId: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val modifiedAt: Long,
    )

    private fun readTrack(entry: FileEntry): CachedTrack {
        val extension = extensionOf(entry.fileName)
        val metadata = readMetadata(entry.uri, baseName(entry.fileName), extension)
        return CachedTrack(
            track = Track(
                id = entry.uri.toString(),
                uri = entry.uri,
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                durationMs = metadata.durationMs,
                folderName = entry.folderName,
                relativePath = entry.relativePath,
                lyricUri = entry.lyricUri,
                quality = qualityClass(
                    extension = extension,
                    bitrate = metadata.bitrate,
                    bitDepth = metadata.bitDepth,
                    sampleRateHz = metadata.sampleRateHz,
                ),
                bitrateKbps = metadata.bitrate / 1_000,
                bitDepth = metadata.bitDepth,
                sampleRateHz = metadata.sampleRateHz,
                channelCount = metadata.channelCount,
            ),
            fileSize = entry.fileSize,
            modifiedAt = entry.modifiedAt,
        )
    }

    private data class FileEntry(
        val uri: Uri,
        val fileName: String,
        val folderName: String,
        val relativePath: String,
        val lyricUri: Uri?,
        val fileSize: Long,
        val modifiedAt: Long,
    )

    private fun readMetadata(
        uri: Uri,
        fallbackTitle: String,
        extension: String,
    ): TrackMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val format = readAudioFormat(uri, extension)
            TrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: "",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }
                    ?: "",
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L,
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()
                    ?.coerceAtLeast(0)
                    ?: 0,
                bitDepth = format.bitDepth,
                sampleRateHz = format.sampleRateHz.takeIf { it > 0 }
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                        ?.coerceAtLeast(0)
                    ?: 0,
                channelCount = format.channelCount,
            )
        } catch (_: Exception) {
            TrackMetadata(
                title = fallbackTitle,
                artist = "",
                album = "",
                durationMs = 0L,
                bitrate = 0,
                bitDepth = 0,
                sampleRateHz = 0,
                channelCount = 0,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 读取采样率、声道数、位深；FLAC 额外从 STREAMINFO 里取准确值。 */
    private fun readAudioFormat(uri: Uri, extension: String): AudioFormat {
        // FLAC 的采样率 / 声道 / 位深能直接从 STREAMINFO 拿到，省掉一次 MediaExtractor 解析。
        if (extension == "flac") {
            readFlacStreamInfo(uri)?.let { flac ->
                return AudioFormat(
                    sampleRateHz = flac.sampleRateHz,
                    channelCount = flac.channelCount,
                    bitDepth = flac.bitDepth,
                )
            }
        }

        val fromExtractor = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (!mime.startsWith("audio/")) continue
                    return@runCatching AudioFormat(
                        sampleRateHz = format.intValue(MediaFormat.KEY_SAMPLE_RATE),
                        channelCount = format.intValue(MediaFormat.KEY_CHANNEL_COUNT),
                        bitDepth = format.intValue(KEY_BITS_PER_SAMPLE),
                    )
                }
                null
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrNull()

        return AudioFormat(
            sampleRateHz = fromExtractor?.sampleRateHz ?: 0,
            channelCount = fromExtractor?.channelCount ?: 0,
            bitDepth = fromExtractor?.bitDepth ?: 0,
        )
    }

    private fun MediaFormat.intValue(key: String): Int =
        if (containsKey(key)) {
            runCatching { getInteger(key) }.getOrDefault(0)
        } else {
            0
        }

    /** 读取 FLAC 文件头的 STREAMINFO，拿到准确的采样率、声道数和位深。 */
    private fun readFlacStreamInfo(uri: Uri): FlacStreamInfo? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val header = ByteArray(FLAC_STREAMINFO_HEADER_SIZE)
            var read = 0
            while (read < header.size) {
                val count = input.read(header, read, header.size - read)
                if (count <= 0) break
                read += count
            }
            if (read < header.size) null else parseFlacStreamInfo(header)
        }
    }.getOrNull()

    private fun extensionOf(name: String?): String =
        name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()

    private fun baseName(name: String?): String =
        name?.substringBeforeLast('.', name).orEmpty()

    private fun qualityClass(
        extension: String,
        bitrate: Int,
        bitDepth: Int,
        sampleRateHz: Int,
    ): String {
        val kbps = bitrate / 1_000
        return when (extension) {
            "flac", "wav" -> if (bitDepth > 16 || sampleRateHz > 48_000) {
                QUALITY_HI_RES
            } else {
                QUALITY_SQ
            }

            else -> if (kbps >= 256) QUALITY_HQ else QUALITY_STANDARD
        }
    }

    private data class TrackMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val bitrate: Int,
        val bitDepth: Int,
        val sampleRateHz: Int,
        val channelCount: Int,
    )

    private data class AudioFormat(
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitDepth: Int,
    )

    private companion object {
        const val METADATA_CONCURRENCY = 6
        const val KEY_BITS_PER_SAMPLE = "bits-per-sample"
        const val FLAC_STREAMINFO_HEADER_SIZE = 22
        val SUPPORTED_AUDIO_EXTENSIONS = setOf(
            "mp3",
            "flac",
            "m4a",
            "aac",
            "ogg",
            "opus",
            "wav",
        )
    }
}
