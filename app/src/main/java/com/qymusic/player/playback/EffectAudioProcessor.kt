package com.qymusic.player.playback

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * 把「人声/伴奏 + 循环声道」接入 Media3 的标准 AudioProcessor 链。
 *
 * 之前的实现直接包住 AudioSink 并自己复用一份 handleBuffer 缓冲。开启音效时
 * 会从原生 buffer 切换到复用 buffer，Media3 要求同一段未消费 PCM 必须一直使用
 * 同一个实例；在部分 ColorOS 设备上这个切换会把渲染线程卡住，表现为声音停在
 * 一个位置、seek 后只恢复一小段。
 */
@UnstableApi
class EffectAudioProcessor(
    private val vocalSplit: VocalSplitProcessor,
    private val rotation: RotatingChannelProcessor,
) : BaseAudioProcessor() {

    private var loggedFirstBuffer = false

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat,
    ): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding in SUPPORTED_ENCODINGS &&
            inputAudioFormat.channelCount == 2
        Log.i(
            TAG,
            "接入标准音频链：encoding=${inputAudioFormat.encoding} " +
                "sampleRate=${inputAudioFormat.sampleRate} " +
                "channels=${inputAudioFormat.channelCount} 可处理=$supported",
        )
        VocalSplitController.sinkState.value = if (supported) {
            "标准 AudioProcessor 链：已接入"
        } else {
            "标准 AudioProcessor 链：已旁路（仅支持双声道 PCM）"
        }
        if (supported) {
            vocalSplit.reportConfigured(inputAudioFormat)
            return inputAudioFormat
        }
        vocalSplit.reportUnsupported(inputAudioFormat)
        return AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (!loggedFirstBuffer) {
            loggedFirstBuffer = true
            Log.i(TAG, "首个 PCM 缓冲进入效果链：${inputBuffer.remaining()} bytes")
        }

        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer)
        output.flip()
        AudioEffects.apply(output, inputAudioFormat, vocalSplit, rotation)
    }

    override fun onReset() = Unit

    private companion object {
        const val TAG = "QYMusicEffectChain"
        val SUPPORTED_ENCODINGS = intArrayOf(
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT,
        )
    }
}
