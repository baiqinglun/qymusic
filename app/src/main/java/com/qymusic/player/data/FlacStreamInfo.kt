package com.qymusic.player.data

/** FLAC 音频参数。 */
data class FlacStreamInfo(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepth: Int,
)

/**
 * 解析 FLAC 文件开头的 STREAMINFO 元数据块。
 *
 * [header] 至少包含文件起始的 [STREAMINFO_END_OFFSET] 个字节：
 * "fLaC"（4 字节）+ 元数据块头（4 字节）+ STREAMINFO 前 14 字节。
 */
fun parseFlacStreamInfo(header: ByteArray): FlacStreamInfo? {
    if (header.size < STREAMINFO_END_OFFSET) return null
    if (header[0] != 'f'.code.toByte() ||
        header[1] != 'L'.code.toByte() ||
        header[2] != 'a'.code.toByte() ||
        header[3] != 'C'.code.toByte()
    ) {
        return null
    }

    // 64bit 字段：采样率(20) / 声道数-1(3) / 位深-1(5) / 总采样数(36)
    val first = header[STREAMINFO_FIELD_OFFSET].toInt() and 0xFF
    val second = header[STREAMINFO_FIELD_OFFSET + 1].toInt() and 0xFF
    val third = header[STREAMINFO_FIELD_OFFSET + 2].toInt() and 0xFF
    val fourth = header[STREAMINFO_FIELD_OFFSET + 3].toInt() and 0xFF

    val sampleRateHz = (first shl 12) or (second shl 4) or (third shr 4)
    val channelCount = ((third shr 1) and 0x07) + 1
    val bitDepth = (((third and 0x01) shl 4) or (fourth shr 4)) + 1
    if (sampleRateHz <= 0 || channelCount <= 0 || bitDepth <= 0) return null

    return FlacStreamInfo(
        sampleRateHz = sampleRateHz,
        channelCount = channelCount,
        bitDepth = bitDepth,
    )
}

private const val STREAMINFO_FIELD_OFFSET = 18
private const val STREAMINFO_END_OFFSET = 22
