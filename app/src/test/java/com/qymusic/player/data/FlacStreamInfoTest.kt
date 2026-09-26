package com.qymusic.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FlacStreamInfoTest {
    @Test
    fun parsesCdQualityStream() {
        val info = requireNotNull(
            parseFlacStreamInfo(flacHeader(sampleRate = 44_100, channels = 2, bitDepth = 16)),
        )
        assertEquals(44_100, info.sampleRateHz)
        assertEquals(2, info.channelCount)
        assertEquals(16, info.bitDepth)
    }

    @Test
    fun parsesHiResStream() {
        val info = requireNotNull(
            parseFlacStreamInfo(flacHeader(sampleRate = 96_000, channels = 2, bitDepth = 24)),
        )
        assertEquals(96_000, info.sampleRateHz)
        assertEquals(24, info.bitDepth)
    }

    @Test
    fun parsesMonoLowBitDepthStream() {
        val info = requireNotNull(
            parseFlacStreamInfo(flacHeader(sampleRate = 22_050, channels = 1, bitDepth = 8)),
        )
        assertEquals(22_050, info.sampleRateHz)
        assertEquals(1, info.channelCount)
        assertEquals(8, info.bitDepth)
    }

    @Test
    fun rejectsHeaderWithoutFlacMarker() {
        val header = flacHeader(sampleRate = 44_100, channels = 2, bitDepth = 16)
        header[0] = 'x'.code.toByte()
        assertNull(parseFlacStreamInfo(header))
    }

    @Test
    fun rejectsShortHeader() {
        assertNull(parseFlacStreamInfo(ByteArray(10)))
    }

    /**
     * 按 FLAC 规范单独拼出 STREAMINFO 的 64bit 字段：
     * 采样率(20bit) / 声道数-1(3bit) / 位深-1(5bit)。
     */
    private fun flacHeader(sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val header = ByteArray(26)
        header[0] = 'f'.code.toByte()
        header[1] = 'L'.code.toByte()
        header[2] = 'a'.code.toByte()
        header[3] = 'C'.code.toByte()
        header[4] = 0x80.toByte()
        header[5] = 0x00
        header[6] = 0x00
        header[7] = 0x22
        val field = (sampleRate.toLong() shl 44) or
            ((channels - 1).toLong() shl 41) or
            ((bitDepth - 1).toLong() shl 36)
        for (index in 0 until 8) {
            val shift = 56 - index * 8
            header[18 + index] = ((field shr shift) and 0xFF).toByte()
        }
        return header
    }
}
