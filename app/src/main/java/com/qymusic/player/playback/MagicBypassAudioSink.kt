package com.qymusic.player.playback

import android.media.AudioDeviceInfo
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import java.nio.ByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MagicAudioOutputState {
    private val _switching = MutableStateFlow(false)
    val switching: StateFlow<Boolean> = _switching.asStateFlow()

    internal fun setSwitching(switching: Boolean) {
        _switching.value = switching
    }
}

/**
 * 未启用魔音时直接把 PCM 交给 Media3 默认 AudioSink；启用速度、变调、
 * 人声分离或循环声道后，在下一个 PCM 缓冲区边界切换到效果 Sink。
 */
@UnstableApi
class MagicBypassAudioSink(
    private val directSink: AudioSink,
    private val effectSink: AudioSink,
    private val isMagicActive: () -> Boolean,
) : AudioSink {
    private var activeSink = if (isMagicActive()) effectSink else directSink
    private var listener: AudioSink.Listener? = null
    private var configuredFormat: Format? = null
    private var configuredBufferSize = 0
    private var configuredOutputChannels: IntArray? = null
    private var playing = false

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
        directSink.setListener(listener)
        effectSink.setListener(listener)
    }

    override fun setPlayerId(playerId: PlayerId?) {
        directSink.setPlayerId(playerId)
        effectSink.setPlayerId(playerId)
    }

    override fun setClock(clock: Clock) {
        directSink.setClock(clock)
        effectSink.setClock(clock)
    }

    override fun supportsFormat(format: Format): Boolean = activeSink.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int = activeSink.getFormatSupport(format)

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport =
        if (isMagicActive()) {
            AudioOffloadSupport.DEFAULT_UNSUPPORTED
        } else {
            directSink.getFormatOffloadSupport(format)
        }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long =
        activeSink.getCurrentPositionUs(sourceEnded)

    override fun configure(
        inputFormat: Format,
        specifiedBufferSize: Int,
        outputChannels: IntArray?,
    ) {
        configuredFormat = inputFormat
        configuredBufferSize = specifiedBufferSize
        configuredOutputChannels = outputChannels?.copyOf()
        directSink.configure(inputFormat, specifiedBufferSize, outputChannels)
        effectSink.configure(inputFormat, specifiedBufferSize, outputChannels)
        activeSink = if (isMagicActive()) effectSink else directSink
        playing = false
    }

    override fun play() {
        playing = true
        activeSink.play()
    }

    override fun handleDiscontinuity() {
        activeSink.handleDiscontinuity()
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        switchIfNeeded()
        return activeSink.handleBuffer(
            buffer,
            presentationTimeUs,
            encodedAccessUnitCount,
        )
    }

    override fun playToEndOfStream() {
        activeSink.playToEndOfStream()
    }

    override fun isEnded(): Boolean = activeSink.isEnded()

    override fun hasPendingData(): Boolean = activeSink.hasPendingData()

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        directSink.setPlaybackParameters(playbackParameters)
        effectSink.setPlaybackParameters(playbackParameters)
    }

    override fun getPlaybackParameters(): PlaybackParameters =
        activeSink.playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        directSink.setSkipSilenceEnabled(skipSilenceEnabled)
        effectSink.setSkipSilenceEnabled(skipSilenceEnabled)
    }

    override fun getSkipSilenceEnabled(): Boolean = activeSink.skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        directSink.setAudioAttributes(audioAttributes)
        effectSink.setAudioAttributes(audioAttributes)
    }

    override fun getAudioAttributes(): AudioAttributes? = activeSink.audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {
        directSink.setAudioSessionId(audioSessionId)
        effectSink.setAudioSessionId(audioSessionId)
    }

    override fun setAuxEffectInfo(auxEffectInfo: androidx.media3.common.AuxEffectInfo) {
        directSink.setAuxEffectInfo(auxEffectInfo)
        effectSink.setAuxEffectInfo(auxEffectInfo)
    }

    override fun setPreferredDevice(preferredAudioDevice: AudioDeviceInfo?) {
        directSink.setPreferredDevice(preferredAudioDevice)
        effectSink.setPreferredDevice(preferredAudioDevice)
    }

    override fun setOutputStreamOffsetUs(outputStreamOffsetUs: Long) {
        directSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
        effectSink.setOutputStreamOffsetUs(outputStreamOffsetUs)
    }

    override fun getAudioTrackBufferSizeUs(): Long = activeSink.audioTrackBufferSizeUs

    override fun enableTunnelingV21() {
        directSink.enableTunnelingV21()
        effectSink.enableTunnelingV21()
    }

    override fun disableTunneling() {
        directSink.disableTunneling()
        effectSink.disableTunneling()
    }

    override fun setOffloadMode(offloadMode: Int) {
        directSink.setOffloadMode(offloadMode)
        effectSink.setOffloadMode(offloadMode)
    }

    override fun setOffloadDelayPadding(
        delayInFrames: Int,
        paddingInFrames: Int,
    ) {
        directSink.setOffloadDelayPadding(delayInFrames, paddingInFrames)
        effectSink.setOffloadDelayPadding(delayInFrames, paddingInFrames)
    }

    override fun setVolume(volume: Float) {
        directSink.setVolume(volume)
        effectSink.setVolume(volume)
    }

    override fun pause() {
        playing = false
        activeSink.pause()
    }

    override fun flush() {
        directSink.flush()
        effectSink.flush()
    }

    override fun reset() {
        playing = false
        configuredFormat = null
        configuredOutputChannels = null
        directSink.reset()
        effectSink.reset()
        activeSink = if (isMagicActive()) effectSink else directSink
    }

    override fun release() {
        directSink.release()
        effectSink.release()
    }

    private fun switchIfNeeded() {
        val target = if (isMagicActive()) effectSink else directSink
        if (target === activeSink) return

        MagicAudioOutputState.setSwitching(true)
        try {
            activeSink.pause()
            activeSink.flush()
            listener?.let(target::setListener)
            configuredFormat?.let { format ->
                target.configure(
                    format,
                    configuredBufferSize,
                    configuredOutputChannels,
                )
            }
            activeSink = target
            if (playing) target.play()
        } finally {
            MagicAudioOutputState.setSwitching(false)
        }
    }
}
