package com.qymusic.player.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 在 PCM 缓冲区上直接做「人声/伴奏分离 + 循环声道」，原地改写，不改变缓冲区的
 * position/limit。给 [EffectAudioSink] 用：不经过 Media3 的 AudioProcessor 链，
 * 所以任何设备 / 解码路径都能生效。
 *
 * 音频处理在播放线程上执行，逐样本的 ByteBuffer 访问、状态读取和函数调用都会
 * 直接吃掉 AudioTrack 的余量。这里先把 PCM 批量搬到基本类型数组里处理，再把
 * 整块写回；循环声道的相位步长也只按缓冲区计算一次。
 */
@UnstableApi
object AudioEffects {

    fun apply(
        buffer: ByteBuffer,
        format: AudioProcessor.AudioFormat,
        vocalSplit: VocalSplitProcessor,
        rotation: RotatingChannelProcessor,
    ) {
        if (format == AudioProcessor.AudioFormat.NOT_SET || format.channelCount != 2) return
        if (!buffer.hasRemaining()) return

        val splitMode = vocalSplit.mode
        val rotationState = rotation.state
        val rotationEnabled = rotationState.enabled
        if (splitMode == VocalSplitMode.BOTH && !rotationEnabled) return

        val rotationStep = if (rotationEnabled) {
            rotation.phaseStepFor(rotationState, format.sampleRate)
        } else {
            0.0
        }
        var phase = if (rotationEnabled) rotation.phaseSnapshot() else 0.0

        phase = when (format.encoding) {
            C.ENCODING_PCM_FLOAT -> applyFloat(
                buffer,
                splitMode,
                rotation,
                rotationEnabled,
                phase,
                rotationStep,
            )

            C.ENCODING_PCM_24BIT -> apply24(
                buffer,
                splitMode,
                rotation,
                rotationEnabled,
                phase,
                rotationStep,
            )

            C.ENCODING_PCM_32BIT -> apply32(
                buffer,
                splitMode,
                rotation,
                rotationEnabled,
                phase,
                rotationStep,
            )

            C.ENCODING_PCM_16BIT -> apply16(
                buffer,
                splitMode,
                rotation,
                rotationEnabled,
                phase,
                rotationStep,
            )

            else -> phase
        }
        if (rotationEnabled) rotation.updatePhase(phase)
    }

    private fun apply16(
        buffer: ByteBuffer,
        splitMode: VocalSplitMode,
        rotation: RotatingChannelProcessor,
        rotationEnabled: Boolean,
        phase: Double,
        rotationStep: Double,
    ): Double {
        val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val sampleCount = samples.remaining()
        val scratch = shortScratch(sampleCount)
        samples.get(scratch, 0, sampleCount)

        var nextPhase = phase
        val out = frameOut
        var i = 0
        while (i + 1 < sampleCount) {
            nextPhase = mixInto(
                splitMode,
                rotation,
                rotationEnabled,
                scratch[i].toFloat(),
                scratch[i + 1].toFloat(),
                nextPhase,
                rotationStep,
                out,
            )
            scratch[i] = clampShort(out[0])
            scratch[i + 1] = clampShort(out[1])
            i += 2
        }

        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            .put(scratch, 0, sampleCount)
        return nextPhase
    }

    /** 24bit PCM：3 字节小端，按满量程归一化后走同一套混音公式。 */
    private fun apply24(
        buffer: ByteBuffer,
        splitMode: VocalSplitMode,
        rotation: RotatingChannelProcessor,
        rotationEnabled: Boolean,
        phase: Double,
        rotationStep: Double,
    ): Double {
        val start = buffer.position()
        val byteCount = buffer.remaining()
        val scratch = byteScratch(byteCount)
        buffer.duplicate().get(scratch, 0, byteCount)

        var nextPhase = phase
        val out = frameOut
        var i = 0
        while (i + 5 < byteCount) {
            val left = read24(scratch, i) / FULL_SCALE_24
            val right = read24(scratch, i + 3) / FULL_SCALE_24
            nextPhase = mixInto(
                splitMode,
                rotation,
                rotationEnabled,
                left,
                right,
                nextPhase,
                rotationStep,
                out,
            )
            write24(scratch, i, out[0])
            write24(scratch, i + 3, out[1])
            i += 6
        }

        buffer.position(start)
        buffer.put(scratch, 0, byteCount)
        buffer.position(start)
        return nextPhase
    }

    private fun apply32(
        buffer: ByteBuffer,
        splitMode: VocalSplitMode,
        rotation: RotatingChannelProcessor,
        rotationEnabled: Boolean,
        phase: Double,
        rotationStep: Double,
    ): Double {
        val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
        val sampleCount = samples.remaining()
        val scratch = intScratch(sampleCount)
        samples.get(scratch, 0, sampleCount)

        var nextPhase = phase
        val out = frameOut
        var i = 0
        while (i + 1 < sampleCount) {
            nextPhase = mixInto(
                splitMode,
                rotation,
                rotationEnabled,
                scratch[i] / FULL_SCALE_32,
                scratch[i + 1] / FULL_SCALE_32,
                nextPhase,
                rotationStep,
                out,
            )
            scratch[i] = (out[0] * FULL_SCALE_32).toInt()
            scratch[i + 1] = (out[1] * FULL_SCALE_32).toInt()
            i += 2
        }

        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            .put(scratch, 0, sampleCount)
        return nextPhase
    }

    private fun applyFloat(
        buffer: ByteBuffer,
        splitMode: VocalSplitMode,
        rotation: RotatingChannelProcessor,
        rotationEnabled: Boolean,
        phase: Double,
        rotationStep: Double,
    ): Double {
        val samples = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val sampleCount = samples.remaining()
        val scratch = floatScratch(sampleCount)
        samples.get(scratch, 0, sampleCount)

        var nextPhase = phase
        val out = frameOut
        var i = 0
        while (i + 1 < sampleCount) {
            nextPhase = mixInto(
                splitMode,
                rotation,
                rotationEnabled,
                scratch[i],
                scratch[i + 1],
                nextPhase,
                rotationStep,
                out,
            )
            scratch[i] = out[0].coerceIn(-1f, 1f)
            scratch[i + 1] = out[1].coerceIn(-1f, 1f)
            i += 2
        }

        buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            .put(scratch, 0, sampleCount)
        return nextPhase
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun mixInto(
        splitMode: VocalSplitMode,
        rotation: RotatingChannelProcessor,
        rotationEnabled: Boolean,
        left: Float,
        right: Float,
        phase: Double,
        rotationStep: Double,
        out: FloatArray,
    ): Double {
        var mixedLeft = left
        var mixedRight = right
        when (splitMode) {
            VocalSplitMode.BOTH -> Unit
            VocalSplitMode.VOCALS -> {
                val mid = (left + right) * 0.5f
                mixedLeft = mid
                mixedRight = mid
            }

            VocalSplitMode.INSTRUMENTAL -> {
                val side = (left - right) * SIDE_GAIN
                mixedLeft = side
                mixedRight = side
            }
        }
        if (!rotationEnabled) {
            out[0] = mixedLeft
            out[1] = mixedRight
            return phase
        }
        return rotation.rotateInto(mixedLeft, mixedRight, phase, rotationStep, out)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun clampShort(value: Float): Short = when {
        value <= MIN_SHORT -> Short.MIN_VALUE
        value >= MAX_SHORT -> Short.MAX_VALUE
        else -> value.toInt().toShort()
    }

    private fun read24(bytes: ByteArray, index: Int): Float {
        val b0 = bytes[index].toInt() and 0xFF
        val b1 = bytes[index + 1].toInt() and 0xFF
        val b2 = bytes[index + 2].toInt()
        return ((b2 shl 16) or (b1 shl 8) or b0).toFloat()
    }

    private fun write24(bytes: ByteArray, index: Int, value: Float) {
        val clamped = (value * FULL_SCALE_24)
            .toInt()
            .coerceIn(MIN_24, MAX_24)
        bytes[index] = (clamped and 0xFF).toByte()
        bytes[index + 1] = ((clamped shr 8) and 0xFF).toByte()
        bytes[index + 2] = ((clamped shr 16) and 0xFF).toByte()
    }

    private fun shortScratch(size: Int): ShortArray {
        if (shortScratch.size < size) shortScratch = ShortArray(size)
        return shortScratch
    }

    private fun intScratch(size: Int): IntArray {
        if (intScratch.size < size) intScratch = IntArray(size)
        return intScratch
    }

    private fun floatScratch(size: Int): FloatArray {
        if (floatScratch.size < size) floatScratch = FloatArray(size)
        return floatScratch
    }

    private fun byteScratch(size: Int): ByteArray {
        if (byteScratch.size < size) byteScratch = ByteArray(size)
        return byteScratch
    }

    private val frameOut = FloatArray(2)
    private var shortScratch = ShortArray(0)
    private var intScratch = IntArray(0)
    private var floatScratch = FloatArray(0)
    private var byteScratch = ByteArray(0)

    private const val SIDE_GAIN = 0.9f
    private const val FULL_SCALE_24 = 8388608f
    private const val FULL_SCALE_32 = 2147483648f
    private const val MIN_24 = -8388608
    private const val MAX_24 = 8388607
    private const val MIN_SHORT = -32768f
    private const val MAX_SHORT = 32767f
}
