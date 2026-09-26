package com.qymusic.player.lyrics

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlacLyricsReaderTest {
    @Test
    fun readsLyricsFromVorbisComment() {
        val flac = flacFile(
            "ARTIST=Demo Artist",
            "LYRICS=[00:01.00]Hello\n[00:02.50]World",
        )

        val lyrics = FlacLyricsReader.read(ByteArrayInputStream(flac))

        assertEquals("[00:01.00]Hello\n[00:02.50]World", lyrics)
    }

    @Test
    fun prefersSyncedLyricsWhenMultipleFieldsExist() {
        val flac = flacFile(
            "UNSYNCEDLYRICS=Plain lyrics",
            "LYRICS=[00:01.00]Timed lyrics",
        )

        val lyrics = FlacLyricsReader.read(ByteArrayInputStream(flac))

        assertEquals("[00:01.00]Timed lyrics", lyrics)
    }

    @Test
    fun returnsNullForNonFlacData() {
        assertNull(FlacLyricsReader.read(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))))
    }

    private fun flacFile(vararg comments: String): ByteArray {
        val vorbisComment = ByteArrayOutputStream()
        vorbisComment.writeLittleEndian(0)
        vorbisComment.writeLittleEndian(comments.size)
        comments.forEach { comment ->
            val bytes = comment.toByteArray(Charsets.UTF_8)
            vorbisComment.writeLittleEndian(bytes.size)
            vorbisComment.write(bytes)
        }

        val block = vorbisComment.toByteArray()
        val output = ByteArrayOutputStream()
        output.write("fLaC".toByteArray(Charsets.US_ASCII))
        output.write(0x84)
        output.write((block.size ushr 16) and 0xFF)
        output.write((block.size ushr 8) and 0xFF)
        output.write(block.size and 0xFF)
        output.write(block)
        return output.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLittleEndian(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }
}
