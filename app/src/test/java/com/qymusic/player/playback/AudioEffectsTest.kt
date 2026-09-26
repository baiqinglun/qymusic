package com.qymusic.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class AudioEffectsTest {

    @Test
    fun `16-bit rotation keeps buffer bounds and pans full-left frame to left`() {
        val split = VocalSplitProcessor()
        val rotation = RotatingChannelProcessor().apply {
            updatePhase(0.0)
            state = RotationUiState(
                enabled = true,
                revolutionsPerSecond = 0.25f,
                clockwise = true,
            )
        }
        val buffer = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putShort(Short.MAX_VALUE)
                putShort(Short.MAX_VALUE)
                flip()
            }

        AudioEffects.apply(
            buffer,
            AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT),
            split,
            rotation,
        )

        assertEquals(0, buffer.position())
        assertEquals(4, buffer.limit())
        assertEquals(Short.MAX_VALUE, buffer.getShort(0))
        assertEquals(0, buffer.getShort(2).toInt())
    }

    @Test
    fun `24-bit rotation keeps buffer bounds and pans full-left frame to left`() {
        val split = VocalSplitProcessor()
        val rotation = RotatingChannelProcessor().apply {
            updatePhase(0.0)
            state = RotationUiState(
                enabled = true,
                revolutionsPerSecond = 0.25f,
                clockwise = true,
            )
        }
        val buffer = ByteBuffer.allocate(6)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put24(MAX_24)
                put24(MAX_24)
                flip()
            }

        AudioEffects.apply(
            buffer,
            AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_24BIT),
            split,
            rotation,
        )

        assertEquals(0, buffer.position())
        assertEquals(6, buffer.limit())
        assertEquals(MAX_24, buffer.read24(0))
        assertEquals(0, buffer.read24(3))
    }

    @Test
    fun `disabled effects leave pcm bytes untouched`() {
        val split = VocalSplitProcessor()
        val rotation = RotatingChannelProcessor()
        val buffer = ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putShort(12_345)
                putShort(-6_789)
                flip()
            }
        val before = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }

        AudioEffects.apply(
            buffer,
            AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT),
            split,
            rotation,
        )

        val after = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
        assertEquals(0, buffer.position())
        assertEquals(4, buffer.limit())
        assertEquals(before.toList(), after.toList())
    }

    private fun ByteBuffer.put24(value: Int) {
        put((value and 0xFF).toByte())
        put(((value shr 8) and 0xFF).toByte())
        put(((value shr 16) and 0xFF).toByte())
    }

    private fun ByteBuffer.read24(index: Int): Int {
        val b0 = get(index).toInt() and 0xFF
        val b1 = get(index + 1).toInt() and 0xFF
        val b2 = get(index + 2).toInt()
        return (b2 shl 16) or (b1 shl 8) or b0
    }

    private companion object {
        const val MAX_24 = 8_388_607
    }
}
