package com.qymusic.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val left = buffer.getShort(0).toInt()
        assertTrue(left in 24_000 until Short.MAX_VALUE.toInt())
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
        val left = buffer.read24(0)
        assertTrue(left in 6_000_000 until MAX_24)
        assertEquals(0, buffer.read24(3))
    }

    @Test
    fun `rotation keeps center power and ducks hard-left and hard-right edges`() {
        val rotation = RotatingChannelProcessor().apply {
            state = RotationUiState(enabled = true)
        }
        val out = FloatArray(2)

        rotation.rotateInto(1f, 1f, 0.0, 0.0, out)
        val hardLeft = out[0]

        rotation.rotateInto(1f, 1f, Math.PI / 2.0, 0.0, out)
        val hardRight = out[1]

        rotation.rotateInto(1f, 1f, Math.PI / 4.0, 0.0, out)
        val centerLeft = out[0]
        val centerRight = out[1]

        assertEquals(0.78f, hardLeft, 0.001f)
        assertEquals(0.78f, hardRight, 0.001f)
        assertEquals(0.7071f, centerLeft, 0.001f)
        assertEquals(centerLeft, centerRight, 0.0001f)
        val centerEnergy = centerLeft * centerLeft + centerRight * centerRight
        assertTrue(centerEnergy > hardLeft * hardLeft)
        assertTrue(centerEnergy > hardRight * hardRight)
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
