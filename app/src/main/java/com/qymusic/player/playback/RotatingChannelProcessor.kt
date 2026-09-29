package com.qymusic.player.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/** 循环声道的界面状态：是否开启、每秒转多少圈、顺时针还是逆时针。 */
data class RotationUiState(
    val enabled: Boolean = false,
    val revolutionsPerSecond: Float = DEFAULT_REVOLUTIONS_PER_SECOND,
    val clockwise: Boolean = true,
) {
    companion object {
        const val MIN_REVOLUTIONS_PER_SECOND = 0.05f
        const val MAX_REVOLUTIONS_PER_SECOND = 0.6f
        const val DEFAULT_REVOLUTIONS_PER_SECOND = 0.2f
    }
}

/**
 * 循环声道（8D 环绕）：把声音按等功率法则在左右声道之间转圈，
 * 听感上就是声音绕着脑袋转。相位按采样逐点推进，转速和方向可调。
 *
 * 用 cos/sin 做等功率分配（左 cos、右 sin），转到中间时音量不会塌，
 * 一整圈是「左 → 中 → 右 → 中 → 左」。
 * 接近最左 / 最右时额外做很轻的平滑衰减，避免单侧耳朵长时间承受满幅声音。
 */
@UnstableApi
class RotatingChannelProcessor : BaseAudioProcessor() {

    @Volatile
    var state: RotationUiState = RotationUiState()
        set(value) {
            if (field == value) return
            field = value
            Log.i(TAG, "循环声道：开启=${value.enabled} 转速=${value.revolutionsPerSecond} 顺时针=${value.clockwise}")
        }

    private var phase = 0.0

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        // 同样要覆盖 24bit / 32bit 整数 PCM，否则 FLAC 这类 Hi-Res 会被整条旁路。
        val supported = inputAudioFormat.encoding in SUPPORTED_ENCODINGS &&
            inputAudioFormat.channelCount == 2
        if (!supported) {
            Log.w(
                TAG,
                "当前输出格式不支持循环声道（只支持双声道 PCM）：" +
                    "encoding=${inputAudioFormat.encoding} channels=${inputAudioFormat.channelCount}",
            )
        }
        phase = 0.0
        return if (supported) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onReset() {
        phase = 0.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        // 空缓冲直接返回：产出 0 字节输出会和 Media3 的 EMPTY_BUFFER 撞成同一个对象，
        // 链路校验会抛 "The source buffer is this buffer"。
        if (!inputBuffer.hasRemaining()) return
        val output = replaceOutputBuffer(inputBuffer.remaining())
        val current = state
        if (!current.enabled) {
            // 没开启时纯直通，别动音频。
            output.put(inputBuffer)
        } else {
            when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_FLOAT -> processFloat(inputBuffer, output, current)
                C.ENCODING_PCM_24BIT -> process24(inputBuffer, output, current)
                C.ENCODING_PCM_32BIT -> process32(inputBuffer, output, current)
                else -> processShort(inputBuffer, output, current)
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun processShort(
        input: ByteBuffer,
        output: ByteBuffer,
        current: RotationUiState,
    ) {
        val inputShort = input.asShortBuffer()
        val outputShort = output.asShortBuffer()
        val frames = inputShort.remaining() / 2
        val step = phaseStep(current, inputAudioFormat.sampleRate)
        repeat(frames) {
            val left = inputShort.get().toFloat()
            val right = inputShort.get().toFloat()
            val (outLeft, outRight) = rotate(left, right, step)
            outputShort.put(outLeft.coerceIn(MIN_SHORT, MAX_SHORT).toInt().toShort())
            outputShort.put(outRight.coerceIn(MIN_SHORT, MAX_SHORT).toInt().toShort())
        }
        input.position(input.limit())
        output.position(output.position() + frames * 4)
    }

    private fun processFloat(
        input: ByteBuffer,
        output: ByteBuffer,
        current: RotationUiState,
    ) {
        val inputFloat = input.asFloatBuffer()
        val outputFloat = output.asFloatBuffer()
        val frames = inputFloat.remaining() / 2
        val step = phaseStep(current, inputAudioFormat.sampleRate)
        repeat(frames) {
            val left = inputFloat.get()
            val right = inputFloat.get()
            val (outLeft, outRight) = rotate(left, right, step)
            outputFloat.put(outLeft.coerceIn(-1f, 1f))
            outputFloat.put(outRight.coerceIn(-1f, 1f))
        }
        input.position(input.limit())
        output.position(output.position() + frames * 8)
    }

    /** 24bit PCM：3 字节小端，按满量程归一化后走同一套旋转公式。 */
    private fun process24(
        input: ByteBuffer,
        output: ByteBuffer,
        current: RotationUiState,
    ) {
        val frames = input.remaining() / 6
        val step = phaseStep(current, inputAudioFormat.sampleRate)
        repeat(frames) {
            val left = read24(input) / FULL_SCALE_24
            val right = read24(input) / FULL_SCALE_24
            val (outLeft, outRight) = rotate(left, right, step)
            write24(output, (outLeft * FULL_SCALE_24).toInt())
            write24(output, (outRight * FULL_SCALE_24).toInt())
        }
        input.position(input.limit())
        output.position(output.position() + frames * 6)
    }

    /** 32bit 整数 PCM。 */
    private fun process32(
        input: ByteBuffer,
        output: ByteBuffer,
        current: RotationUiState,
    ) {
        val inputInt = input.asIntBuffer()
        val outputInt = output.asIntBuffer()
        val frames = inputInt.remaining() / 2
        val step = phaseStep(current, inputAudioFormat.sampleRate)
        repeat(frames) {
            val left = inputInt.get() / FULL_SCALE_32
            val right = inputInt.get() / FULL_SCALE_32
            val (outLeft, outRight) = rotate(left, right, step)
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
        return ((b2 shl 16) or (b1 shl 8) or b0).toFloat()
    }

    private fun write24(buffer: ByteBuffer, value: Int) {
        val clamped = value.coerceIn(MIN_24, MAX_24)
        buffer.put((clamped and 0xFF).toByte())
        buffer.put(((clamped shr 8) and 0xFF).toByte())
        buffer.put(((clamped shr 16) and 0xFF).toByte())
    }

    /** 每个采样点相位推进多少弧度（正数顺时针、负数逆时针）。 */
    /** 给 EffectAudioSink 包装层直接用：对一帧左右声道做旋转。 */
    fun transform(left: Float, right: Float, sampleRate: Int): Pair<Float, Float> =
        rotate(left, right, phaseStep(state, sampleRate))

    /** 给批量处理用：一个缓冲区内只读取一次状态，步长也只计算一次。 */
    internal fun phaseSnapshot(): Double = phase

    internal fun phaseStepFor(current: RotationUiState, sampleRate: Int): Double =
        phaseStep(current, sampleRate)

    internal fun updatePhase(value: Double) {
        phase = value
    }

    /**
     * 内联到 AudioEffects 的逐样本循环里，避免每帧调用方法、重复读取 volatile 状态
     * 和重复计算相位步长。返回值是下一帧的相位。
     */
    @Suppress("NOTHING_TO_INLINE")
    internal inline fun rotateInto(
        left: Float,
        right: Float,
        phase: Double,
        step: Double,
        out: FloatArray,
    ): Double {
        val source = (left + right) * 0.5f
        val index = ((phase / TWO_PI * TABLE_SIZE).toInt()) and TABLE_MASK
        val leftGain = SIN_TABLE[(index + TABLE_SIZE / 4) and TABLE_MASK]
        val rightGain = SIN_TABLE[index]
        val edgeAmount = kotlin.math.abs(leftGain - rightGain)
        val comfortGain = 1f - ENDPOINT_ATTENUATION * edgeAmount * edgeAmount
        out[0] = source * leftGain * comfortGain
        out[1] = source * rightGain * comfortGain
        var nextPhase = phase + step
        if (nextPhase > TWO_PI) nextPhase -= TWO_PI
        if (nextPhase < -TWO_PI) nextPhase += TWO_PI
        return nextPhase
    }

    /** 无装箱版本：结果写进 out[0] / out[1]。 */
    fun transformInto(
        left: Float,
        right: Float,
        sampleRate: Int,
        out: FloatArray,
    ) {
        if (!state.enabled) {
            out[0] = left
            out[1] = right
            return
        }
        val source = (left + right) * 0.5f
        val index = ((phase / TWO_PI * TABLE_SIZE).toInt()) and (TABLE_SIZE - 1)
        val leftGain = SIN_TABLE[(index + TABLE_SIZE / 4) and (TABLE_SIZE - 1)]
        val rightGain = SIN_TABLE[index]
        val edgeAmount = kotlin.math.abs(leftGain - rightGain)
        val comfortGain = 1f - ENDPOINT_ATTENUATION * edgeAmount * edgeAmount
        out[0] = source * leftGain * comfortGain
        out[1] = source * rightGain * comfortGain
        phase += phaseStep(state, sampleRate)
        if (phase > TWO_PI) phase -= TWO_PI
        if (phase < -TWO_PI) phase += TWO_PI
    }

    private fun phaseStep(current: RotationUiState, sampleRate: Int): Double {
        val safeRate = sampleRate.coerceAtLeast(1)
        val revolutions = current.revolutionsPerSecond
            .coerceIn(
                RotationUiState.MIN_REVOLUTIONS_PER_SECOND,
                RotationUiState.MAX_REVOLUTIONS_PER_SECOND,
            )
        val direction = if (current.clockwise) 1.0 else -1.0
        return 2.0 * Math.PI * revolutions * direction / safeRate
    }

    private fun rotate(left: Float, right: Float, step: Double): Pair<Float, Float> {
        // 先取中置作为"声源"，再按等功率分配到左右。
        val source = (left + right) * 0.5f
        // 用查表代替逐样本 sin/cos：这是音频线程，逐样本三角函数会把实时播放拖卡。
        val index = ((phase / TWO_PI * TABLE_SIZE).toInt()) and (TABLE_SIZE - 1)
        val leftGain = SIN_TABLE[(index + TABLE_SIZE / 4) and (TABLE_SIZE - 1)]
        val rightGain = SIN_TABLE[index]
        val edgeAmount = kotlin.math.abs(leftGain - rightGain)
        val comfortGain = 1f - ENDPOINT_ATTENUATION * edgeAmount * edgeAmount
        phase += step
        if (phase > TWO_PI) phase -= TWO_PI
        if (phase < -TWO_PI) phase += TWO_PI
        return (source * leftGain * comfortGain) to
            (source * rightGain * comfortGain)
    }

    internal companion object {
        const val TAG = "QYMusicRotation"
        internal const val TWO_PI = Math.PI * 2
        internal const val TABLE_SIZE = 4096
        internal const val TABLE_MASK = TABLE_SIZE - 1
        const val MIN_SHORT = -32768f
        const val MAX_SHORT = 32767f
        const val ENDPOINT_ATTENUATION = 0.22f
        internal val SIN_TABLE = FloatArray(TABLE_SIZE) {
            kotlin.math.sin(it * (TWO_PI / TABLE_SIZE)).toFloat()
        }
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
object RotatingChannelController {
    val processor: RotatingChannelProcessor by lazy { RotatingChannelProcessor() }
}
