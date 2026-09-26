package com.qymusic.player.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 听哪一路：原声 / 只要人声 / 只要伴奏。 */
enum class VocalSplitMode {
    BOTH,
    VOCALS,
    INSTRUMENTAL,
}

/**
 * 立体声中/侧分离，用来做「人声 / 伴奏」切换。
 *
 * 人声在混音里通常放在正中间（两个声道同相），所以：
 * - 中置 (L + R) / 2 ≈ 人声 + 居中乐器 → 取它当「人声」
 * - 侧向 (L - R) ≈ 去掉中间人声后的伴奏 → 取它当「伴奏」
 *
 * 这是零延迟、不吃性能的经典做法（和卡拉OK消音同源），
 * 不是 AI 模型分离：人声不在正中或带混响的曲子会有残留。
 */
@UnstableApi
class VocalSplitProcessor : BaseAudioProcessor() {

    /** 最近一次接入的音频格式，用来在界面里确认效果到底有没有挂上。 */
    // 前缀带版本号：如果界面上看到的还是旧文案（没有 v3），说明装的是旧包。
    private val _formatState = MutableStateFlow("v3 · 尚未接入音频链（开始播放后再看这行）")
    val formatState: StateFlow<String> = _formatState

    @Volatile
    var mode: VocalSplitMode = VocalSplitMode.BOTH
        set(value) {
            if (field == value) return
            field = value
            Log.i(TAG, "切换播放内容：$value")
        }

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        // 16/24/32bit 整数与 float PCM 都要支持：FLAC 常见的 24bit Hi-Res
        // 解码出来是 24bit PCM，只认 16bit 的话它会整条被旁路（听着就是没效果）。
        val supported = encoding in SUPPORTED_ENCODINGS && inputAudioFormat.channelCount == 2
        _formatState.value = buildString {
            append(bitDepthLabel(encoding))
            append(" / ")
            append(inputAudioFormat.sampleRate / 1000)
            append("kHz / ")
            append(inputAudioFormat.channelCount)
            append("ch · ")
            append(if (supported) "效果已生效" else "已旁路（仅支持双声道 PCM）")
        }
        Log.i(
            TAG,
            "接入音频链路：encoding=$encoding channels=${inputAudioFormat.channelCount} " +
                "sampleRate=${inputAudioFormat.sampleRate} 可处理=$supported",
        )
        if (!supported) {
            Log.w(
                TAG,
                "当前输出格式不支持人声分离（只支持双声道 PCM），本条音轨按原声播放。" +
                    "如果是蓝牙单声道路由，切回立体声输出即可。",
            )
        }
        // 只处理双声道 PCM，其它格式直接旁路，避免破坏音频链路。
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // 空缓冲直接返回：这里如果产出 0 字节的输出缓冲，会和 Media3 内部的 EMPTY_BUFFER
        // 是同一个对象，链路校验会报 "The source buffer is this buffer" 导致播放报错。
        if (!inputBuffer.hasRemaining()) return
        val size = inputBuffer.remaining()
        val output = replaceOutputBuffer(size)
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output)
            C.ENCODING_PCM_24BIT -> process24(inputBuffer, output)
            C.ENCODING_PCM_32BIT -> process32(inputBuffer, output)
            else -> processShort(inputBuffer, output)
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    /** 给 EffectAudioSink 包装层直接用：对一帧左右声道做分离。 */
    fun transform(left: Float, right: Float): Pair<Float, Float> = mix(mode, left, right)

    /** 无装箱版本：结果写进 out[0] / out[1]。 */
    fun transformInto(left: Float, right: Float, out: FloatArray) {
        when (mode) {
            VocalSplitMode.BOTH -> {
                out[0] = left
                out[1] = right
            }

            VocalSplitMode.VOCALS -> {
                val mid = (left + right) * 0.5f
                out[0] = mid
                out[1] = mid
            }

            VocalSplitMode.INSTRUMENTAL -> {
                val side = (left - right) * SIDE_GAIN
                out[0] = side
                out[1] = side
            }
        }
    }

    /** AudioProcessor 链接入时上报真实格式。 */
    fun reportConfigured(audioFormat: AudioProcessor.AudioFormat) {
        _formatState.value = buildString {
            append(bitDepthLabel(audioFormat.encoding))
            append(" / ")
            append(audioFormat.sampleRate / 1000)
            append("kHz / ")
            append(audioFormat.channelCount)
            append("ch · 效果已生效")
        }
    }

    fun reportUnsupported(audioFormat: AudioProcessor.AudioFormat) {
        _formatState.value = buildString {
            append(bitDepthLabel(audioFormat.encoding))
            append(" / ")
            append(audioFormat.sampleRate / 1000)
            append("kHz / ")
            append(audioFormat.channelCount)
            append("ch · 已旁路（仅支持双声道 PCM）")
        }
    }

    private fun processShort(input: ByteBuffer, output: ByteBuffer) {
        val current = mode
        val inputShort = input.asShortBuffer()
        val outputShort = output.asShortBuffer()
        val frames = inputShort.remaining() / 2
        repeat(frames) { index ->
            val left = inputShort.get().toInt()
            val right = inputShort.get().toInt()
            val (outLeft, outRight) = mix(current, left.toFloat(), right.toFloat())
            outputShort.put(shortOf(outLeft))
            outputShort.put(shortOf(outRight))
        }
        input.position(input.limit())
        output.position(output.position() + frames * 4)
    }

    private fun processFloat(input: ByteBuffer, output: ByteBuffer) {
        val current = mode
        val inputFloat = input.asFloatBuffer()
        val outputFloat = output.asFloatBuffer()
        val frames = inputFloat.remaining() / 2
        repeat(frames) {
            val left = inputFloat.get()
            val right = inputFloat.get()
            val (outLeft, outRight) = mix(current, left, right)
            outputFloat.put(outLeft.coerceIn(-1f, 1f))
            outputFloat.put(outRight.coerceIn(-1f, 1f))
        }
        input.position(input.limit())
        output.position(output.position() + frames * 8)
    }

    /** 24bit PCM：每声道 3 字节小端有符号，按满量程归一化后复用同一套混音公式。 */
    private fun process24(input: ByteBuffer, output: ByteBuffer) {
        val current = mode
        val frames = input.remaining() / 6
        repeat(frames) {
            val left = read24(input) / FULL_SCALE_24
            val right = read24(input) / FULL_SCALE_24
            val (outLeft, outRight) = mix(current, left, right)
            write24(output, (outLeft * FULL_SCALE_24).toInt())
            write24(output, (outRight * FULL_SCALE_24).toInt())
        }
        input.position(input.limit())
        output.position(output.position() + frames * 6)
    }

    /** 32bit 整数 PCM。 */
    private fun process32(input: ByteBuffer, output: ByteBuffer) {
        val current = mode
        val inputInt = input.asIntBuffer()
        val outputInt = output.asIntBuffer()
        val frames = inputInt.remaining() / 2
        repeat(frames) {
            val left = inputInt.get() / FULL_SCALE_32
            val right = inputInt.get() / FULL_SCALE_32
            val (outLeft, outRight) = mix(current, left, right)
            outputInt.put((outLeft * FULL_SCALE_32).toInt())
            outputInt.put((outRight * FULL_SCALE_32).toInt())
        }
        input.position(input.limit())
        output.position(output.position() + frames * 8)
    }

    private fun read24(buffer: ByteBuffer): Float {
        val b0 = buffer.get().toInt() and 0xFF
        val b1 = buffer.get().toInt() and 0xFF
        val b2 = buffer.get().toInt()
        // b2 保留符号位，直接拼出 24bit 有符号值。
        return ((b2 shl 16) or (b1 shl 8) or b0).toFloat()
    }

    private fun write24(buffer: ByteBuffer, value: Int) {
        val clamped = value.coerceIn(MIN_24, MAX_24)
        buffer.put((clamped and 0xFF).toByte())
        buffer.put(((clamped shr 8) and 0xFF).toByte())
        buffer.put(((clamped shr 16) and 0xFF).toByte())
    }

    /** 返回这一帧左右声道的目标值（已按模式换算）。 */
    private fun mix(mode: VocalSplitMode, left: Float, right: Float): Pair<Float, Float> {
        return when (mode) {
            // 原声：原样输出。
            VocalSplitMode.BOTH -> left to right

            // 人声：只保留中置（两侧同相的部分）。
            VocalSplitMode.VOCALS -> {
                val mid = (left + right) * 0.5f
                mid to mid
            }

            // 伴奏：只保留侧向，中间的人声互相抵消。
            VocalSplitMode.INSTRUMENTAL -> {
                val side = (left - right) * SIDE_GAIN
                side to side
            }
        }
    }

    private fun shortOf(value: Float): Short =
        value.coerceIn(MIN_SHORT, MAX_SHORT).toInt().toShort()

    private fun bitDepthLabel(encoding: Int): String = when (encoding) {
        C.ENCODING_PCM_16BIT -> "16bit"
        C.ENCODING_PCM_24BIT -> "24bit"
        C.ENCODING_PCM_32BIT -> "32bit"
        C.ENCODING_PCM_FLOAT -> "float"
        else -> "未知($encoding)"
    }

    private companion object {
        const val TAG = "QYMusicVocalSplit"
        const val SIDE_GAIN = 0.9f
        const val MIN_SHORT = -32768f
        const val MAX_SHORT = 32767f
        const val FULL_SCALE_24 = 8388608f
        const val MIN_24 = -8388608
        const val MAX_24 = 8388607
        const val FULL_SCALE_32 = 2147483648f
        val SUPPORTED_ENCODINGS = intArrayOf(
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT,
        )
    }
}

/** 播放服务和界面共用同一个处理器实例。 */
@androidx.annotation.OptIn(UnstableApi::class)
object VocalSplitController {
    val processor: VocalSplitProcessor by lazy { VocalSplitProcessor() }

    /** 播放服务有没有创建出带效果处理器的那条音频链。 */
    val sinkState = MutableStateFlow("自定义音频链：尚未创建")

}
