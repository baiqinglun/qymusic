package com.qymusic.player.data

import java.io.File
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.id3.ID3v1Tag
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AudioTagWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun sampleFlac(): File? {
        val candidates = File("..\\assets\\Music")
            .takeIf { it.isDirectory }
            ?.listFiles { file -> file.extension.equals("flac", ignoreCase = true) }
            .orEmpty()
        return candidates.firstOrNull()
    }

    @Test
    fun embedWritesLyricsCoverAndMetadataIntoFlac() {
        val source = sampleFlac() ?: return
        assumeTrue(source.length() > 0)

        val working = temp.newFile("sample.flac")
        source.copyTo(working, overwrite = true)

        val report = AudioTagWriter.embed(
            file = working,
            tags = EmbeddedTags(
                title = "测试歌曲",
                artist = "测试歌手",
                album = "测试专辑",
                coverBytes = byteArrayOf(
                    0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                ),
                coverMime = "image/jpeg",
                lyrics = "[00:01.00]第一句歌词\n[00:02.00]第二句歌词",
            ),
        )

        assertTrue(report.wroteMetadata)
        assertTrue(report.wroteCover)
        assertTrue(report.wroteLyrics)

        val reread = AudioFileIO.read(working)
        val tag = reread.tag
        assertEquals("测试歌曲", tag.getFirst(FieldKey.TITLE))
        assertEquals("测试歌手", tag.getFirst(FieldKey.ARTIST))
        assertEquals("测试专辑", tag.getFirst(FieldKey.ALBUM))
        assertTrue(tag.getFirst(FieldKey.LYRICS).contains("第一句歌词"))
        assertTrue(tag.hasField(FieldKey.COVER_ART))
    }

    @Test
    fun embedRemovesLyricsWhenRemoveLyricsIsSet() {
        val source = sampleFlac() ?: return
        assumeTrue(source.length() > 0)

        val working = temp.newFile("sample.flac")
        source.copyTo(working, overwrite = true)

        AudioTagWriter.embed(
            file = working,
            tags = EmbeddedTags(
                title = "测试歌曲",
                artist = "测试歌手",
                album = "测试专辑",
                lyrics = "[00:01.00]第一句歌词",
            ),
        )
        val withLyrics = AudioFileIO.read(working)
        assertTrue(withLyrics.tag.hasField(FieldKey.LYRICS))

        // 清空歌词 + removeLyrics：保存后文件里的歌词帧应被移除
        val report = AudioTagWriter.embed(
            file = working,
            tags = EmbeddedTags(
                title = "测试歌曲",
                artist = "测试歌手",
                album = "测试专辑",
                removeLyrics = true,
            ),
        )
        assertTrue(report.wroteLyrics)

        val reread = AudioFileIO.read(working)
        assertTrue(!reread.tag.hasField(FieldKey.LYRICS))
    }

    @Test
    fun embedSkipsUnsupportedExtensionsGracefully() {
        val fake = temp.newFile("sample.ape")
        fake.writeBytes(ByteArray(64))
        val report = AudioTagWriter.embed(
            file = fake,
            tags = EmbeddedTags(title = "t", artist = "a", album = "b"),
        )
        assertTrue(report.unsupportedFormat)
    }

    @Test
    fun readFromStreamUsesOriginalExtension() {
        val source = sampleFlac() ?: return
        val result = source.inputStream().use { input ->
            AudioTagWriter.read(input, "flac")
        }
        assertTrue(!result.unsupported)
        assertTrue(result.error == null)
        assertTrue(result.tags != null)
    }

    @Test
    fun readRejectsUnsupportedExtension() {
        val fake = temp.newFile("sample.ape")
        fake.writeBytes(ByteArray(64))
        val result = fake.inputStream().use { input ->
            AudioTagWriter.read(input, "ape")
        }
        assertTrue(result.unsupported)
    }

    /** 构造一个最小的合法 MP3（MPEG1 Layer III 128kbps 44.1kHz 静音帧）。 */
    private fun writeSilentMp3(target: File, frameCount: Int, withId3v1: Boolean) {
        val frame = ByteArray(417)
        frame[0] = 0xFF.toByte()
        frame[1] = 0xFB.toByte()
        frame[2] = 0x90.toByte()
        frame[3] = 0x00
        target.outputStream().use { output ->
            repeat(frameCount) { output.write(frame) }
            if (withId3v1) {
                output.write("TAG".toByteArray(Charsets.ISO_8859_1))
                output.write(ByteArray(125))
            }
        }
    }

    @Test
    fun embedWritesCoverIntoMp3WithoutExistingTag() {
        val mp3 = temp.newFile("sample.mp3")
        writeSilentMp3(mp3, 200, withId3v1 = false)
        val report = AudioTagWriter.embed(
            file = mp3,
            tags = EmbeddedTags(
                title = "测试",
                artist = "歌手",
                album = "专辑",
                coverBytes = TEST_JPEG_BYTES,
                coverMime = "image/jpeg",
                lyrics = "[00:01.00]歌词",
            ),
        )
        assertTrue(report.wroteMetadata)
        assertTrue(report.wroteCover)
        assertTrue(report.wroteLyrics)

        val reread = AudioFileIO.read(mp3)
        assertTrue(reread.tag.hasField(FieldKey.COVER_ART))
        assertTrue(reread.tag.getFirst(FieldKey.LYRICS).contains("歌词"))
        // 新建标签应为兼容性最好的 ID3v2.3
        assertTrue(reread.tag is ID3v23Tag)
    }

    @Test
    fun embedUpgradesMp3WithOnlyId3v1Tag() {
        val mp3 = temp.newFile("sample_v1.mp3")
        writeSilentMp3(mp3, 200, withId3v1 = true)
        val before = AudioFileIO.read(mp3)
        assumeTrue(before.tag is ID3v1Tag)

        val report = AudioTagWriter.embed(
            file = mp3,
            tags = EmbeddedTags(
                title = "测试",
                artist = "歌手",
                album = "专辑",
                coverBytes = TEST_JPEG_BYTES,
                coverMime = "image/jpeg",
            ),
        )
        assertTrue(report.wroteCover)

        val reread = AudioFileIO.read(mp3)
        assertTrue(reread.tag.hasField(FieldKey.COVER_ART))
        assertEquals("测试", reread.tag.getFirst(FieldKey.TITLE))
    }

    private companion object {
        /** 最小 JPEG 头像字节（JFIF 头），仅供标签结构验证。 */
        val TEST_JPEG_BYTES = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
        )
    }
}
