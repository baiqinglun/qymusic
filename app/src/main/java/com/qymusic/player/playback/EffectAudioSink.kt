package com.qymusic.player.playback

import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink

/**
 * 只负责关闭 offload，让解码后的 PCM 进入 [EffectAudioProcessor]。
 *
 * 不在这里改写 handleBuffer：自定义复用缓冲会和 DefaultAudioSink 的 buffer
 * 所有权检查冲突，在部分 OPPO / ColorOS 设备上会在播放中卡死。
 */
@UnstableApi
class EffectAudioSink(
    private val delegate: AudioSink,
) : AudioSink by delegate {

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        AudioOffloadSupport.DEFAULT_UNSUPPORTED
}
