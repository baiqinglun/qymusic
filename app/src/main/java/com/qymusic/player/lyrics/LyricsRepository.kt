package com.qymusic.player.lyrics

import android.content.Context
import android.net.Uri
import com.qymusic.player.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class LyricsRepository(private val context: Context) {
    suspend fun load(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        track.lyricUri
            ?.let(::readText)
            ?.takeIf { it.isNotBlank() }
            ?.let(LrcParser::parse)
            ?: loadEmbeddedFlacLyrics(track)
    }

    private fun loadEmbeddedFlacLyrics(track: Track): Lyrics? {
        if (track.extension != "flac") return null
        return runCatching {
            context.contentResolver.openInputStream(track.uri)?.use { input ->
                FlacLyricsReader.read(input)
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(LrcParser::parse)
    }

    private fun readText(uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            decodeText(readLimited(input))
        }
    }.getOrNull()

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (total < MAX_LYRICS_FILE_BYTES) {
            val read = input.read(buffer, 0, minOf(buffer.size, MAX_LYRICS_FILE_BYTES - total))
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun decodeText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
        }

        return try {
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            runCatching { String(bytes, charset("GB18030")) }
                .getOrElse { String(bytes, StandardCharsets.ISO_8859_1) }
        }
    }

    private companion object {
        const val MAX_LYRICS_FILE_BYTES = 8 * 1024 * 1024
    }
}
