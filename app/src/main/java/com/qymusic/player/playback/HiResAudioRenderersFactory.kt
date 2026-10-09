package com.qymusic.player.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * 尽量按源位深输出高解析度音频：
 * - 把当前输出路由支持的高位深 PCM 编码交给 [DefaultAudioSink]；
 * - 固定使用整数 PCM，让 Sonic 负责变速 / 变调，避免 float 路径绕过处理器；
 * - 采样率仍交给 Media3 处理（源采样率设备支持时不会重采样）。
 *
 * 真正的 bit-perfect 直通还需要 Android 14 的 AudioMixerAttributes 或 AAudio 独占模式，
 * 这里先把「位深 + 路由能力」这部分打通，并把结论写进日志，便于定位实际输出格式。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class HiResAudioRenderersFactory(
    context: Context,
    private val outputInfo: AudioOutputInfo,
    private val vocalSplitProcessor: VocalSplitProcessor,
    private val rotatingProcessor: RotatingChannelProcessor,
    private val isMagicActive: () -> Boolean,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink {
        val encodings = buildList {
            add(C.ENCODING_PCM_16BIT)
            if (outputInfo.supports24Bit) add(C.ENCODING_PCM_24BIT)
        }.toIntArray()
        val capabilities = AudioCapabilities(encodings, outputInfo.maxChannelCount)
        // Media3 的 float 输出会绕过 SonicAudioProcessor；部分 OPPO / Hi-Res
        // 设备又无法用 AudioTrack PlaybackParams 稳定变速。这里固定输出整数 PCM，
        // 24bit 素材仍按 24bit 输出，变速 / 变调统一交给 Sonic。
        val floatOutput = false

        Log.i(
            AUDIO_LOG_TAG,
            "音频输出：设备原生 ${outputInfo.nativeDescription}，" +
                "float输出=$floatOutput，24bit=${outputInfo.supports24Bit}，" +
                "96kHz=${outputInfo.supports96k}，bitPerfect=${outputInfo.bitPerfectAvailable}",
        )
        VocalSplitController.sinkState.value =
            "音频输出：无魔音时直出，魔音开启时切换效果链"

        val directSink = requireNotNull(
            super.buildAudioSink(
                context,
                outputInfo.supportsFloat,
                enableAudioTrackPlaybackParams,
            ),
        )
        val effectBaseSink = DefaultAudioSink.Builder(context)
            .setAudioCapabilities(capabilities)
            .setEnableFloatOutput(floatOutput)
            .setEnableAudioTrackPlaybackParams(false)
            .setAudioProcessors(
                arrayOf(
                    EffectAudioProcessor(vocalSplitProcessor, rotatingProcessor),
                ),
            )
            .build()
        return MagicBypassAudioSink(
            directSink = directSink,
            effectSink = EffectAudioSink(effectBaseSink),
            isMagicActive = isMagicActive,
        )
    }

    private companion object {
        const val AUDIO_LOG_TAG = "QYMusicAudioOut"
    }
}
