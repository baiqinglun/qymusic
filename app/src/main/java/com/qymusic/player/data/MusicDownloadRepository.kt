package com.qymusic.player.data

import android.content.ContentValues
import android.content.Context
import android.util.Log
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MusicDownloadResult(
    val fileName: String,
    val displayPath: String,
    val uri: Uri,
    val sizeBytes: Long,
)

/** 下载到本地缓存的中间产物：待嵌入标签后提交到目标目录。 */
data class PendingAudioDownload(
    val tempFile: File,
    val extension: String,
    val baseName: String,
)

class DownloadHttpException(
    val statusCode: Int,
    val requestUrl: String,
) : IllegalStateException("下载失败：HTTP $statusCode")

class DownloadContentException(
    message: String,
    val requestUrl: String,
) : IllegalStateException(message)


/** 音源拼音缩写：酷狗 kg / 酷我 kw / 网易云 wy / 咪咕 mg / QQ 音乐 qq。 */
fun musicSourceAbbreviation(sourceKey: String): String = when (sourceKey) {
    "tx" -> "qq"
    else -> sourceKey
}

class MusicDownloadRepository(context: Context) {
    private val appContext = context.applicationContext

    fun fetchText(
        url: String,
        timeoutMs: Int = SOURCE_IMPORT_TIMEOUT_MS,
    ): String {
        val response = LxHttpClient.execute(
            url = url,
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", timeoutMs),
        )
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("导入失败：HTTP ${response.statusCode}")
        }
        return response.body
    }

    suspend fun download(
        url: String,
        song: LxMusicSong,
        namingFormat: DownloadNamingFormat = DownloadNamingFormat.TITLE_ARTIST,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): PendingAudioDownload = withContext(Dispatchers.IO) {
        val connection = openDownloadConnection(url, song.sourceKey)
        var tempFile: File? = null
        try {
            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                Log.w(
                    LOG_TAG,
                    "download failed source=${song.sourceKey} status=$statusCode url=${connection.url}",
                )
                throw DownloadHttpException(statusCode, connection.url.toString())
            }
            val extension = resolveExtension(
                url = connection.url.toString(),
                contentDisposition = connection.getHeaderField("Content-Disposition"),
                contentType = connection.contentType,
            )
            val baseName = buildFileName(song, namingFormat)
            val downloadDir = File(appContext.cacheDir, TEMP_FOLDER_NAME).apply { mkdirs() }
            val file = File(downloadDir, "dl_${System.currentTimeMillis()}_${baseName.hashCode()}.$extension")
            tempFile = file
            val totalBytes = connection.contentLengthLong
                .takeIf { it > 0L }
                ?: -1L
            val input = BufferedInputStream(connection.inputStream, DOWNLOAD_BUFFER_SIZE)
            validateDownloadContent(
                input = input,
                sourceUrl = connection.url.toString(),
                contentType = connection.contentType,
            )
            input.use { sourceInput ->
                file.outputStream().buffered(DOWNLOAD_BUFFER_SIZE).use { output ->
                    copyWithProgress(
                        input = sourceInput,
                        output = output,
                        totalBytes = totalBytes,
                        onProgress = onProgress,
                    )
                }
            }
            PendingAudioDownload(
                tempFile = file,
                extension = extension,
                baseName = baseName,
            )
        } catch (error: Exception) {
            tempFile?.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    /** 把嵌入完标签的临时音频提交到下载目录（SAF 或应用目录）。 */
    suspend fun commit(
        pending: PendingAudioDownload,
        destinationTreeUri: String?,
    ): MusicDownloadResult = withContext(Dispatchers.IO) {
        try {
            val target = createTarget(
                baseName = pending.baseName,
                extension = pending.extension,
                destinationTreeUri = destinationTreeUri,
            )
            try {
                pending.tempFile.inputStream().buffered(DOWNLOAD_BUFFER_SIZE).use { input ->
                    target.openOutput().use { output ->
                        input.copyTo(output, DOWNLOAD_BUFFER_SIZE)
                        output.flush()
                    }
                }
                MusicDownloadResult(
                    fileName = target.fileName,
                    displayPath = target.displayPath,
                    uri = target.uri,
                    sizeBytes = target.sizeBytes(),
                )
            } catch (error: Exception) {
                target.delete()
                throw error
            }
        } finally {
            pending.tempFile.delete()
        }
    }

    /**
     * 探测下载地址对应的文件大小（字节）。先 HEAD 拿 Content-Length，
     * 不支持时退回 GET + Range 解析 Content-Range；取不到返回 -1。
     */
    fun probeSize(url: String, sourceKey: String): Long {
        probeConnection(url, sourceKey, method = "HEAD", range = null)?.let { connection ->
            try {
                val length = connection.contentLengthLong
                if (connection.responseCode in 200..299 && length > 0L) {
                    return length
                }
            } finally {
                connection.disconnect()
            }
        }
        probeConnection(url, sourceKey, method = "GET", range = "bytes=0-0")?.let { connection ->
            try {
                if (connection.responseCode !in 200..299) return@let
                connection.contentLengthLong.takeIf { it > 0L }?.let { return it }
                val contentRange = connection.getHeaderField("Content-Range") ?: return@let
                // 形如 "bytes 0-0/12345678"
                contentRange.substringAfterLast('/', "")
                    .trim()
                    .toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?.let { return it }
            } finally {
                connection.disconnect()
            }
        }
        return -1L
    }

    private fun probeConnection(
        url: String,
        sourceKey: String,
        method: String,
        range: String?,
    ): HttpURLConnection? {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val connection = try {
                URL(currentUrl).openConnection() as HttpURLConnection
            } catch (error: Exception) {
                return null
            }
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
                connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                connection.requestMethod = method
                connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
                refererForSource(sourceKey)?.let { connection.setRequestProperty("Referer", it) }
                range?.let { connection.setRequestProperty("Range", it) }
                connection.setRequestProperty("Accept", "*/*")
                val statusCode = connection.responseCode
                if (statusCode in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || redirects >= MAX_REDIRECTS) return null
                    redirects += 1
                    currentUrl = URL(URL(currentUrl), location).toString()
                    continue
                }
                return connection
            } catch (error: Exception) {
                runCatching { connection.disconnect() }
                return null
            }
        }
    }

    private fun validateDownloadContent(
        input: BufferedInputStream,
        sourceUrl: String,
        contentType: String?,
    ) {
        input.mark(CONTENT_PROBE_SIZE)
        val probe = ByteArray(CONTENT_PROBE_SIZE)
        val read = input.read(probe)
        input.reset()
        if (read <= 0) throw DownloadContentException("音源返回空内容", sourceUrl)
        val text = String(probe, 0, read, Charsets.UTF_8).trimStart()
        if (text.startsWith("{") || text.startsWith("[")) {
            throw DownloadContentException("音源返回错误内容", sourceUrl)
        }
        if (text.startsWith("<") && text.take(128).contains("html", ignoreCase = true)) {
            throw DownloadContentException("音源返回了网页而不是音频", sourceUrl)
        }
        if (!looksLikeAudio(contentType, probe, read) && read <= 1024) {
            throw DownloadContentException("音源返回的不是音频文件", sourceUrl)
        }
    }

    private fun looksLikeAudio(
        contentType: String?,
        probe: ByteArray,
        length: Int,
    ): Boolean {
        val type = contentType.orEmpty().lowercase(Locale.ROOT)
        if (type.startsWith("audio/") || type.contains("octet-stream")) return true
        if (length >= 4) {
            val magic = String(probe, 0, 4, Charsets.ISO_8859_1)
            if (magic == "fLaC" || magic == "OggS" || magic == "RIFF") return true
            if (magic.startsWith("ID3")) return true
            if (probe[0] == 0xFF.toByte() && (probe[1].toInt() and 0xE0) == 0xE0) {
                return true
            }
            if (length >= 12 && String(probe, 4, 4, Charsets.ISO_8859_1) == "ftyp") {
                return true
            }
        }
        return false
    }


    private fun openDownloadConnection(
        url: String,
        sourceKey: String,
    ): HttpURLConnection {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            refererForSource(sourceKey)?.let { referer ->
                connection.setRequestProperty("Referer", referer)
            }
            connection.setRequestProperty("Accept", "*/*")
            val statusCode = connection.responseCode
            if (statusCode in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("下载地址重定向缺少 Location")
                }
                redirects += 1
                if (redirects > MAX_REDIRECTS) {
                    throw IllegalStateException("下载地址重定向次数过多")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                continue
            }
            return connection
        }
    }

    private fun refererForSource(sourceKey: String): String? = when (sourceKey) {
        "wy" -> "https://music.163.com/"
        "tx" -> "https://y.qq.com/"
        "kw" -> "https://www.kuwo.cn/"
        "kg" -> "https://www.kugou.com/"
        "mg" -> "https://music.migu.cn/"
        else -> null
    }


    private fun createTarget(
        baseName: String,
        extension: String,
        destinationTreeUri: String?,
    ): DownloadTarget {
        val treeUri = destinationTreeUri?.takeIf(String::isNotBlank)
        if (treeUri != null) {
            val directory = DocumentFile.fromTreeUri(appContext, Uri.parse(treeUri))
                ?: throw IllegalStateException("无法访问下载目录")
            if (!directory.canWrite()) {
                throw IllegalStateException("下载目录没有写入权限")
            }
            val fileName = uniqueFileName(
                baseName = baseName,
                extension = extension,
                exists = { candidate -> directory.findFile(candidate) != null },
            )
            val document = directory.createFile(mimeTypeForExtension(extension), fileName)
                ?: throw IllegalStateException("无法在下载目录创建文件")
            val output = appContext.contentResolver.openOutputStream(document.uri, "w")
                ?: throw IllegalStateException("无法写入下载文件")
            return DocumentDownloadTarget(
                document = document,
                output = output,
                displayPath = directory.name?.let { "$it/$fileName" } ?: fileName,
            )
        }

        // 默认位置：系统下载目录 Download/QYMusic。
        // Q 及以上走 MediaStore（无需存储权限）；低版本直写公共目录，失败再回退应用目录。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val fileName = mediaStoreFileName(resolver, baseName, extension)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeForExtension(extension))
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + DEFAULT_FOLDER_NAME,
                )
            }
            val collection = MediaStore.Downloads
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("无法在下载目录创建文件")
            val output = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("无法写入下载文件")
            return MediaStoreDownloadTarget(
                resolver = resolver,
                uri = uri,
                fileName = fileName,
                displayPath = "Download/$DEFAULT_FOLDER_NAME/$fileName",
                output = output,
            )
        }
        runCatching {
            val downloads = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS,
            )
            val directory = File(downloads, DEFAULT_FOLDER_NAME).apply { mkdirs() }
            val fileName = uniqueFileName(
                baseName = baseName,
                extension = extension,
                exists = { candidate -> File(directory, candidate).exists() },
            )
            val file = File(directory, fileName)
            return FileDownloadTarget(
                file = file,
                output = file.outputStream().buffered(),
                displayPath = "Download/$DEFAULT_FOLDER_NAME/$fileName",
            )
        }
        val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?.resolve(DEFAULT_FOLDER_NAME)
            ?: File(appContext.filesDir, "Music/$DEFAULT_FOLDER_NAME")
        directory.mkdirs()
        val fileName = uniqueFileName(
            baseName = baseName,
            extension = extension,
            exists = { candidate -> File(directory, candidate).exists() },
        )
        val file = File(directory, fileName)
        return FileDownloadTarget(file = file, output = file.outputStream().buffered())
    }

    /** 在 MediaStore.Downloads 里找不重名的文件名。 */
    private fun mediaStoreFileName(
        resolver: android.content.ContentResolver,
        baseName: String,
        extension: String,
    ): String {
        var candidate = "$baseName.$extension"
        var index = 1
        while (mediaStoreFileExists(resolver, candidate)) {
            candidate = "$baseName ($index).$extension"
            index += 1
        }
        return candidate
    }

    private fun mediaStoreFileExists(
        resolver: android.content.ContentResolver,
        fileName: String,
    ): Boolean = runCatching {
        val collection = MediaStore.Downloads
            .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(fileName),
            null,
        )?.use { cursor -> cursor.count > 0 } ?: false
    }.getOrDefault(false)

    private fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var downloaded = 0L
        var lastReportedAt = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            downloaded += read
            val now = System.currentTimeMillis()
            if (now - lastReportedAt >= PROGRESS_REPORT_INTERVAL_MS) {
                onProgress(downloaded, totalBytes)
                lastReportedAt = now
            }
        }
        output.flush()
        onProgress(downloaded, totalBytes)
    }

    private fun buildFileName(
        song: LxMusicSong,
        namingFormat: DownloadNamingFormat,
    ): String {
        val title = song.title.trim().ifBlank { "未知歌曲" }
        val artist = song.artist.trim()
        val rawName = when (namingFormat) {
            DownloadNamingFormat.TITLE_ARTIST ->
                if (artist.isBlank() || artist == "未知歌手") title else "$title - $artist"

            DownloadNamingFormat.ARTIST_TITLE ->
                if (artist.isBlank() || artist == "未知歌手") title else "$artist - $title"

            DownloadNamingFormat.TITLE -> title
        }
        // 文件名尾部带上音源拼音缩写：酷狗 kg / 酷我 kw / 网易云 wy / 咪咕 mg / QQ 音乐 qq
        val withSource = "${rawName.trim()} [${musicSourceAbbreviation(song.sourceKey)}]"
        return withSource
            .replace(INVALID_FILE_NAME_CHARS, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { "QYMusic_${System.currentTimeMillis()}" }
    }

    private fun uniqueFileName(
        baseName: String,
        extension: String,
        exists: (String) -> Boolean,
    ): String {
        var candidate = "$baseName.$extension"
        var index = 1
        while (exists(candidate)) {
            candidate = "$baseName ($index).$extension"
            index += 1
        }
        return candidate
    }

    private fun resolveExtension(
        url: String,
        contentDisposition: String?,
        contentType: String?,
    ): String {
        val candidates = listOf(
            contentDisposition
                ?.substringAfter("filename*=", "")
                ?.substringAfter("''", "")
                ?.substringBefore(';')
                ?.trim('"', ' '),
            contentDisposition
                ?.substringAfter("filename=", "")
                ?.substringBefore(';')
                ?.trim('"', ' '),
            runCatching { Uri.parse(url).lastPathSegment }.getOrNull(),
        )
        candidates.forEach { candidate ->
            val extension = candidate
                ?.substringAfterLast('.', "")
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it in SUPPORTED_EXTENSIONS }
            if (extension != null) return extension
        }
        return when {
            contentType?.contains("flac", ignoreCase = true) == true -> "flac"
            contentType?.contains("mpeg", ignoreCase = true) == true -> "mp3"
            contentType?.contains("mp4", ignoreCase = true) == true -> "m4a"
            contentType?.contains("aac", ignoreCase = true) == true -> "aac"
            contentType?.contains("wav", ignoreCase = true) == true -> "wav"
            contentType?.contains("ogg", ignoreCase = true) == true -> "ogg"
            else -> "mp3"
        }
    }

    private fun mimeTypeForExtension(extension: String): String = when (extension) {
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "ape" -> "audio/ape"
        else -> "audio/mpeg"
    }

    private sealed interface DownloadTarget {
        val fileName: String
        val displayPath: String
        val uri: Uri

        fun openOutput(): OutputStream
        fun sizeBytes(): Long
        fun delete()
    }

    private class DocumentDownloadTarget(
        private val document: DocumentFile,
        private var output: OutputStream,
        override val displayPath: String,
    ) : DownloadTarget {
        override val fileName: String = document.name.orEmpty()
        override val uri: Uri = document.uri

        override fun openOutput(): OutputStream = output

        override fun sizeBytes(): Long = document.length()

        override fun delete() {
            runCatching { output.close() }
            runCatching { document.delete() }
        }
    }

    private class MediaStoreDownloadTarget(
        private val resolver: android.content.ContentResolver,
        override val uri: Uri,
        override val fileName: String,
        override val displayPath: String,
        private var output: OutputStream?,
    ) : DownloadTarget {
        override fun openOutput(): OutputStream =
            output ?: throw IllegalStateException("下载文件输出流已关闭")

        override fun sizeBytes(): Long = runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)

        override fun delete() {
            runCatching { output?.close() }
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private class FileDownloadTarget(
        private val file: File,
        private var output: OutputStream,
        override val displayPath: String = file.absolutePath,
    ) : DownloadTarget {
        override val fileName: String = file.name
        override val uri: Uri = Uri.fromFile(file)

        override fun openOutput(): OutputStream = output

        override fun sizeBytes(): Long = file.length()

        override fun delete() {
            runCatching { output.close() }
            runCatching { file.delete() }
        }
    }

    companion object {
        const val DEFAULT_FOLDER_NAME = "QYMusic"

        private const val LOG_TAG = "QYMusicDownload"
        private const val TEMP_FOLDER_NAME = "pending_downloads"
        private const val SOURCE_IMPORT_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 15_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_BUFFER_SIZE = 128 * 1024
        private const val CONTENT_PROBE_SIZE = 8 * 1024
        private const val PROGRESS_REPORT_INTERVAL_MS = 180L
        private const val MAX_REDIRECTS = 5
        private const val MAX_FILE_NAME_LENGTH = 120
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        private val INVALID_FILE_NAME_CHARS = Regex("""[\\/:*?"<>|]""")
        private val SUPPORTED_EXTENSIONS = setOf(
            "flac",
            "mp3",
            "m4a",
            "aac",
            "wav",
            "ogg",
            "ape",
        )
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
