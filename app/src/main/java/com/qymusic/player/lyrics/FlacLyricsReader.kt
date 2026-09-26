package com.qymusic.player.lyrics

import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

object FlacLyricsReader {
    private const val FLAC_METADATA_BLOCK_VORBIS_COMMENT = 4
    private const val MAX_LYRICS_BLOCK_BYTES = 16 * 1024 * 1024

    private val lyricsKeys = setOf(
        "LYRICS",
        "LYRIC",
        "UNSYNCEDLYRICS",
        "UNSYNCED LYRICS",
        "UNSYNCEDLYRIC",
        "UNSYNCED LYRIC",
    )

    fun read(source: InputStream): String? {
        val input = if (source is BufferedInputStream) source else BufferedInputStream(source)
        val marker = ByteArray(4)
        if (!readFully(input, marker) || String(marker, StandardCharsets.US_ASCII) != "fLaC") {
            return null
        }

        while (true) {
            val header = input.read()
            if (header == -1) return null

            val isLastBlock = header and 0x80 != 0
            val blockType = header and 0x7F
            val blockLength = readBigEndian24(input) ?: return null

            if (blockType == FLAC_METADATA_BLOCK_VORBIS_COMMENT) {
                if (blockLength !in 0..MAX_LYRICS_BLOCK_BYTES) return null
                val block = ByteArray(blockLength)
                if (!readFully(input, block)) return null
                return parseVorbisComment(block)
            }

            if (!skipFully(input, blockLength.toLong())) return null
            if (isLastBlock) return null
        }
    }

    internal fun parseVorbisComment(block: ByteArray): String? {
        val buffer = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN)

        fun readLength(): Int? {
            if (buffer.remaining() < Int.SIZE_BYTES) return null
            val length = buffer.int
            return length.takeIf { it >= 0 && it <= buffer.remaining() }
        }

        val vendorLength = readLength() ?: return null
        buffer.position(buffer.position() + vendorLength)

        val commentCount = readLengthForCount(buffer) ?: return null
        val candidates = mutableListOf<String>()

        repeat(commentCount) {
            val commentLength = readLength() ?: return null
            val commentBytes = ByteArray(commentLength)
            buffer.get(commentBytes)
            val comment = String(commentBytes, StandardCharsets.UTF_8)
            val separator = comment.indexOf('=')
            if (separator <= 0) return@repeat

            val key = comment.substring(0, separator)
                .trim()
                .uppercase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
            val value = comment.substring(separator + 1).trim()
            if (key in lyricsKeys && value.isNotEmpty()) {
                candidates += value
            }
        }

        return candidates.firstOrNull(LrcParser::containsTimestamp) ?: candidates.firstOrNull()
    }

    private fun readLengthForCount(buffer: ByteBuffer): Int? {
        if (buffer.remaining() < Int.SIZE_BYTES) return null
        val count = buffer.int
        return count.takeIf { it >= 0 && it <= 10_000 }
    }

    private fun readBigEndian24(input: InputStream): Int? {
        val first = input.read()
        val second = input.read()
        val third = input.read()
        if (first == -1 || second == -1 || third == -1) return null
        return (first shl 16) or (second shl 8) or third
    }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read == -1) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, byteCount: Long): Boolean {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (input.read() == -1) {
                return false
            } else {
                remaining--
            }
        }
        return true
    }
}
