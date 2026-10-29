package com.qymusic.player.data

import android.util.Log
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import org.jaudiotagger.audio.AudioFile
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.SupportedFileFormat
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v1Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.jaudiotagger.tag.images.StandardArtwork
import org.jaudiotagger.tag.mp4.Mp4Tag

data class EmbeddedTags(
    val title: String,
    val artist: String,
    val album: String,
    val coverBytes: ByteArray? = null,
    val coverMime: String? = null,
    val lyrics: String? = null,
    val genre: String = "",
    val year: String = "",
    val trackNumber: String = "",
    /** 标签编辑页删除封面：写库时移除现有封面且不新增。 */
    val removeCover: Boolean = false,
    /** 标签编辑页清空歌词：写库时移除现有歌词帧（USLT / ©lyr / Vorbis LYRICS）。 */
    val removeLyrics: Boolean = false,
) {
    val hasCover: Boolean get() = coverBytes != null && coverBytes.isNotEmpty()
    val hasLyrics: Boolean get() = !lyrics.isNullOrBlank()
    val hasMetadata: Boolean get() = title.isNotBlank() || artist.isNotBlank() || album.isNotBlank()
}

/** 从音频文件读出的标签（标签编辑页用）。 */
data class AudioTags(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val trackNumber: String,
    val lyrics: String?,
    val coverBytes: ByteArray?,
)

/** 借助 jaudiotagger 把标题 / 歌手 / 专辑 / 封面 / 歌词写进音频文件标签。 */
object AudioTagWriter {

    data class EmbedReport(
        val wroteMetadata: Boolean,
        val wroteCover: Boolean,
        val wroteLyrics: Boolean,
        val unsupportedFormat: Boolean = false,
    ) {
        val wroteAnything: Boolean get() = wroteMetadata || wroteCover || wroteLyrics
    }

    /**
     * 就地写入标签。任何一步失败都不会抛出（下载本身不受影响），
     * 通过返回值告知各部分是否写入成功。
     */
    fun embed(file: File, tags: EmbeddedTags): EmbedReport {
        val extension = file.extension.lowercase(Locale.US)
        if (extension !in TAGGABLE_EXTENSIONS) {
            Log.d(LOG_TAG, "跳过标签写入：不支持的格式 .$extension")
            return EmbedReport(false, false, false, unsupportedFormat = true)
        }
        return try {
            val audioFile: AudioFile = AudioFileIO.read(file)
            var tag: Tag? = audioFile.tag
            // MP3 只带 ID3v1 时无法容纳封面 / 歌词，升级为新的 v2.3 标签
            if (extension == MP3_EXTENSION && (tag == null || tag is ID3v1Tag)) {
                tag = ID3v23Tag().also { audioFile.tag = it }
            }
            val resolvedTag = tag ?: audioFile.getTagOrCreateAndSetDefault()
            val report = writeFields(resolvedTag, tags)
            if (report.wroteAnything) {
                AudioFileIO.write(audioFile)
            }
            report
        } catch (error: Exception) {
            Log.w(LOG_TAG, "标签写入失败（${file.name}）: ${error.message}")
            EmbedReport(false, false, false)
        }
    }

    private fun writeFields(tag: Tag, tags: EmbeddedTags): EmbedReport {
        var wroteMetadata = false
        var wroteCover = false
        var wroteLyrics = false

        if (tags.hasMetadata) {
            runCatching {
                if (tags.title.isNotBlank()) tag.setField(FieldKey.TITLE, tags.title)
                if (tags.artist.isNotBlank()) tag.setField(FieldKey.ARTIST, tags.artist)
                if (tags.album.isNotBlank()) tag.setField(FieldKey.ALBUM, tags.album)
                if (tags.genre.isNotBlank()) tag.setField(FieldKey.GENRE, tags.genre)
                if (tags.year.isNotBlank()) tag.setField(FieldKey.YEAR, tags.year)
                if (tags.trackNumber.isNotBlank()) tag.setField(FieldKey.TRACK, tags.trackNumber)
                wroteMetadata = true
            }.onFailure { error ->
                Log.w(LOG_TAG, "写入标题/歌手/专辑失败: ${error.message}")
            }
        }
        if (tags.hasCover) {
            runCatching {
                writeCover(tag, tags.coverBytes!!, tags.coverMime ?: "image/jpeg")
                wroteCover = true
            }.onFailure { error ->
                Log.w(LOG_TAG, "写入封面失败: ${error.message}")
            }
        } else if (tags.removeCover) {
            runCatching {
                if (tag.hasField(FieldKey.COVER_ART)) tag.deleteField(FieldKey.COVER_ART)
                wroteCover = true
            }.onFailure { error ->
                Log.w(LOG_TAG, "删除封面失败: ${error.message}")
            }
        }
        if (tags.hasLyrics) {
            runCatching {
                tag.setField(FieldKey.LYRICS, tags.lyrics)
                wroteLyrics = true
            }.onFailure { error ->
                Log.w(LOG_TAG, "写入歌词失败: ${error.message}")
            }
        } else if (tags.removeLyrics) {
            runCatching {
                if (tag.hasField(FieldKey.LYRICS)) {
                    tag.deleteField(FieldKey.LYRICS)
                    wroteLyrics = true
                }
            }.onFailure { error ->
                Log.w(LOG_TAG, "删除歌词失败: ${error.message}")
            }
        }
        return EmbedReport(wroteMetadata, wroteCover, wroteLyrics)
    }

    /**
     * 按格式选择不会触发图片解码的封面写入路径：
     * FlacTag.createField(Artwork) 等默认实现会调用 setImageFromData()
     * 强制解码图片（依赖 javax.imageio / android.graphics），这里全部绕开。
     */
    private fun writeCover(tag: Tag, bytes: ByteArray, mimeType: String) {
        when (tag) {
            is FlacTag -> {
                runCatching { if (tag.hasField(FieldKey.COVER_ART)) tag.deleteField(FieldKey.COVER_ART) }
                val field = tag.createArtworkField(
                    bytes,
                    PICTURE_TYPE_FRONT_COVER,
                    mimeType,
                    "cover",
                    0,
                    0,
                    0,
                    0,
                )
                tag.addField(field)
            }

            is Mp4Tag -> {
                runCatching { if (tag.hasField(FieldKey.COVER_ART)) tag.deleteField(FieldKey.COVER_ART) }
                tag.addField(tag.createArtworkField(bytes))
            }

            else -> {
                // MP3(APIC)、OGG(VorbisComment)、WAV(ID3 chunk) 均直接使用原始字节，
                // 不会解码图片内容。
                runCatching { if (tag.hasField(FieldKey.COVER_ART)) tag.deleteField(FieldKey.COVER_ART) }
                val artwork = StandardArtwork()
                artwork.binaryData = bytes
                artwork.mimeType = mimeType
                artwork.pictureType = PICTURE_TYPE_FRONT_COVER
                artwork.description = "cover"
                tag.addField(artwork)
            }
        }
    }

    private val TAGGABLE_EXTENSIONS = setOf(
        SupportedFileFormat.OGG.filesuffix,
        SupportedFileFormat.MP3.filesuffix,
        SupportedFileFormat.FLAC.filesuffix,
        SupportedFileFormat.M4A.filesuffix,
        SupportedFileFormat.MP4.filesuffix,
        SupportedFileFormat.WAV.filesuffix,
    )

    /**
     * 读取音频文件的标签。不支持的格式返回 null（unsupported=true）。
     * 音频字节来自输入流（SAF document 等），读取经临时文件完成；
     * [extension] 是原始文件扩展名（临时文件需沿用，供格式白名单判断）。
     */
    fun read(source: InputStream, extension: String): ReadResult {
        val temp = createTempFile("tagread_", extension)
        try {
            source.use { input -> input.copyTo(temp.outputStream().buffered(BUFFER_SIZE)) }
            return read(temp)
        } finally {
            temp.delete()
        }
    }

    fun read(file: File): ReadResult {
        val extension = file.extension.lowercase(Locale.US)
        if (extension !in TAGGABLE_EXTENSIONS) {
            return ReadResult(unsupported = true)
        }
        return try {
            val audioFile: AudioFile = AudioFileIO.read(file)
            val tag = audioFile.tag
            fun first(key: FieldKey): String = runCatching {
                tag?.getFirst(key).orEmpty()
            }.getOrDefault("").trim()
            ReadResult(
                tags = AudioTags(
                    title = first(FieldKey.TITLE),
                    artist = first(FieldKey.ARTIST),
                    album = first(FieldKey.ALBUM),
                    genre = first(FieldKey.GENRE),
                    year = first(FieldKey.YEAR),
                    trackNumber = first(FieldKey.TRACK),
                    lyrics = runCatching {
                        tag?.getFirst(FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
                    }.getOrNull(),
                    coverBytes = runCatching {
                        tag?.firstArtwork?.binaryData
                    }.getOrNull(),
                ),
            )
        } catch (error: Exception) {
            Log.w(LOG_TAG, "读取标签失败: ${error.message}")
            ReadResult(error = error.message ?: "读取标签失败")
        }
    }

    /**
     * 两阶段保存的第一阶段：把 [source] 的标签改写结果落到临时文件并返回。
     * 本阶段完全不触碰原文件——只有调用方把临时文件成功写回后原文件才会变化，
     * 因此改写阶段（jaudiotagger 解析 / 写入）抛异常时原文件安然无恙。
     * 调用方负责在写回结束后 [File.delete] 临时文件。
     * 不支持的格式返回 `null to report`。
     * [extension] 是原始文件扩展名，供格式白名单判断。
     */
    fun editToTemp(
        source: InputStream,
        tags: EmbeddedTags,
        extension: String,
    ): Pair<File, EmbedReport> {
        val temp = createTempFile("tagedit_", extension)
        try {
            source.use { input -> input.copyTo(temp.outputStream().buffered(BUFFER_SIZE)) }
            val report = embed(temp, tags)
            if (report.unsupportedFormat) {
                temp.delete()
                return File("unsupported") to report
            }
            return temp to report
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun createTempFile(prefix: String, extension: String): File =
        File.createTempFile(prefix, "." + extension.lowercase(Locale.US).ifBlank { "tmp" })

    data class ReadResult(
        val tags: AudioTags? = null,
        val unsupported: Boolean = false,
        val error: String? = null,
    )

    private const val BUFFER_SIZE = 128 * 1024

    private const val LOG_TAG = "QYAudioTag"
    private const val PICTURE_TYPE_FRONT_COVER = 3
    private const val MP3_EXTENSION = "mp3"
}
