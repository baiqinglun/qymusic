package com.qymusic.player.playback

import android.content.Context
import android.media.audiofx.PresetReverb
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.KaraokePublishSelection
import com.qymusic.player.data.KaraokeVoiceEffect
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KaraokePreviewPlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * 预览页把已经录好的干声和原曲伴奏重新混音播放。该播放器只负责试听，
 * 不改变曲库播放器，也不改变 K 歌录制播放器。
 */
class KaraokePreviewPlayer(
    context: Context,
    private val backgroundPlayer: KaraokePlayer,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voicePlayer = ExoPlayer.Builder(appContext).build()
    private val _state = MutableStateFlow(KaraokePreviewPlaybackState())
    val state: StateFlow<KaraokePreviewPlaybackState> = _state.asStateFlow()

    private var preparedVoicePath: String? = null
    private var currentProject: KaraokeEditProject? = null
    private var reverb: PresetReverb? = null
    private var stopAtMs: Long? = null
    private var previewActive = false
    private var resumePositionMs: Long? = null

    private val voiceListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            attachVoiceEffect(audioSessionId, currentProject?.effect)
        }
    }

    init {
        voicePlayer.addListener(voiceListener)
        scope.launch {
            backgroundPlayer.state.collect { background ->
                _state.update {
                    it.copy(
                        positionMs = background.positionMs,
                        durationMs = background.durationMs,
                        isPlaying = it.isPlaying || background.isPlaying,
                    )
                }
            }
        }
    }

    fun start(
        project: KaraokeEditProject,
        startOverrideMs: Long? = null,
    ) {
        stop()
        previewActive = true
        currentProject = project
        val voiceAvailable = project.voicePath.isNotBlank() &&
            runCatching { File(project.voicePath).isFile }.getOrDefault(false)
        if (voiceAvailable) {
            preparedVoicePath = prepareVoice(project.voicePath)
        } else {
            voicePlayer.pause()
            voicePlayer.clearMediaItems()
            preparedVoicePath = null
        }
        backgroundPlayer.setVocalSplitMode(VocalSplitMode.INSTRUMENTAL)
        backgroundPlayer.setPlaybackSpeed(1f)
        backgroundPlayer.setPitchSemitones(0f)
        backgroundPlayer.setVolume(project.musicVolume)
        voicePlayer.volume = project.vocalVolume.coerceIn(0f, 1f)
        applyVoiceEffect(project.effect)

        val effectiveDuration = maxOf(
            project.durationMs,
            backgroundPlayer.state.value.durationMs,
        ).coerceAtLeast(1L)
        val requestedStart = (
            startOverrideMs ?: resumePositionMs ?: project.selectionStartMs()
            ).coerceIn(0L, effectiveDuration)
        val selectionStart = requestedStart.takeIf { it < effectiveDuration } ?: 0L
        val selectionEnd = if (project.publishSelection == KaraokePublishSelection.SELECTED) {
            project.trimEndMs.coerceIn(selectionStart + 1L, effectiveDuration)
        } else {
            effectiveDuration
        }
        if (selectionEnd <= selectionStart) return
        stopAtMs = selectionEnd
        val effectiveOffset = if (project.vocalOffsetEnabled) {
            project.vocalOffsetMs
        } else {
            0L
        }
        val voicePosition = selectionStart - effectiveOffset

        backgroundPlayer.seekTo(selectionStart)
        if (voicePosition >= 0L) {
            voicePlayer.seekTo(voicePosition)
            backgroundPlayer.play()
            if (voiceAvailable) {
                voicePlayer.play()
            }
        } else {
            voicePlayer.seekTo(0L)
            if (voiceAvailable) {
                voicePlayer.play()
            }
            mainHandler.postDelayed(
                {
                    backgroundPlayer.seekTo(selectionStart)
                    backgroundPlayer.play()
                },
                -voicePosition,
            )
        }
        scheduleStop(selectionEnd - selectionStart)
        resumePositionMs = selectionStart
        _state.update {
            it.copy(
                isPlaying = true,
                positionMs = selectionStart,
                durationMs = effectiveDuration,
            )
        }
        syncState()
    }

    fun seekTo(
        project: KaraokeEditProject,
        positionMs: Long,
    ) {
        currentProject = project
        val target = positionMs.coerceIn(
            project.selectionStartMs(),
            project.selectionEndMs(),
        )
        val continuePlaying = isPlaying()
        stop()
        resumePositionMs = target
        backgroundPlayer.seekTo(target)
        _state.update {
            it.copy(positionMs = target)
        }
        if (continuePlaying) {
            start(project, target)
        }
    }

    fun pause() {
        val pausedPosition = maxOf(
            backgroundPlayer.currentPositionMs(),
            _state.value.positionMs,
        ).coerceAtLeast(0L)
        previewActive = false
        resumePositionMs = pausedPosition
        mainHandler.removeCallbacksAndMessages(null)
        backgroundPlayer.pause()
        voicePlayer.pause()
        _state.update {
            it.copy(
                isPlaying = false,
                positionMs = pausedPosition,
            )
        }
        syncState()
    }

    fun resetPosition() {
        stop()
        resumePositionMs = null
        backgroundPlayer.seekTo(0L)
        voicePlayer.seekTo(0L)
        _state.value = KaraokePreviewPlaybackState()
    }

    fun update(project: KaraokeEditProject, restart: Boolean = false) {
        currentProject = project
        backgroundPlayer.setVolume(project.musicVolume)
        voicePlayer.volume = project.vocalVolume.coerceIn(0f, 1f)
        applyVoiceEffect(project.effect)
        if (restart && isPlaying()) {
            resumePositionMs = null
            start(project, project.selectionStartMs())
        }
    }

    fun release() {
        stop()
        resumePositionMs = null
        scope.cancel()
        voicePlayer.removeListener(voiceListener)
        voicePlayer.release()
        releaseReverb()
        _state.value = KaraokePreviewPlaybackState()
    }

    private fun stop() {
        previewActive = false
        mainHandler.removeCallbacksAndMessages(null)
        backgroundPlayer.pause()
        voicePlayer.pause()
        stopAtMs = null
        syncState()
    }

    private fun prepareVoice(path: String): String? {
        if (preparedVoicePath == path) return path
        voicePlayer.stop()
        voicePlayer.clearMediaItems()
        voicePlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
        voicePlayer.prepare()
        preparedVoicePath = path
        return path
    }

    private fun scheduleStop(durationMs: Long) {
        mainHandler.postDelayed(
            {
                if (stopAtMs != null) {
                    backgroundPlayer.pause()
                    voicePlayer.pause()
                    stopAtMs = null
                    previewActive = false
                    resumePositionMs = null
                    syncState()
                }
            },
            durationMs.coerceAtLeast(0L),
        )
    }

    private fun isPlaying(): Boolean =
        previewActive && (voicePlayer.isPlaying || backgroundPlayer.state.value.isPlaying)

    private fun syncState() {
        _state.update {
            it.copy(
                isPlaying = isPlaying(),
                positionMs = backgroundPlayer.state.value.positionMs,
                durationMs = backgroundPlayer.state.value.durationMs,
            )
        }
    }

    private fun applyVoiceEffect(effect: KaraokeVoiceEffect) {
        applyVoicePitch(effect)
        val reverbPreset = when (effect) {
            KaraokeVoiceEffect.STUDIO -> PresetReverb.PRESET_SMALLROOM
            KaraokeVoiceEffect.HALL -> PresetReverb.PRESET_MEDIUMHALL
            KaraokeVoiceEffect.DISTANT -> PresetReverb.PRESET_LARGEROOM
            KaraokeVoiceEffect.ELECTRONIC -> PresetReverb.PRESET_PLATE
        }
        runCatching {
            reverb?.preset = reverbPreset
            reverb?.enabled = effect != KaraokeVoiceEffect.STUDIO
        }
    }

    @Suppress("DEPRECATION")
    private fun attachVoiceEffect(
        audioSessionId: Int,
        effect: KaraokeVoiceEffect?,
    ) {
        releaseReverb()
        if (audioSessionId <= 0 || effect == null) return
        runCatching {
            reverb = PresetReverb(0, audioSessionId).also {
                it.enabled = effect != KaraokeVoiceEffect.STUDIO
                it.preset = when (effect) {
                    KaraokeVoiceEffect.STUDIO -> PresetReverb.PRESET_SMALLROOM
                    KaraokeVoiceEffect.HALL -> PresetReverb.PRESET_MEDIUMHALL
                    KaraokeVoiceEffect.DISTANT -> PresetReverb.PRESET_LARGEROOM
                    KaraokeVoiceEffect.ELECTRONIC -> PresetReverb.PRESET_PLATE
                }
            }
        }
    }

    private fun applyVoicePitch(effect: KaraokeVoiceEffect) {
        val semitones = when (effect) {
            KaraokeVoiceEffect.ELECTRONIC -> 4f
            else -> 0f
        }
        voicePlayer.setPlaybackParameters(
            PlaybackParameters(1f, pitchFactorOfSemitones(semitones)),
        )
    }

    private fun releaseReverb() {
        runCatching {
            reverb?.enabled = false
            reverb?.release()
        }
        reverb = null
    }

    private fun KaraokeEditProject.selectionStartMs(): Long =
        if (publishSelection == KaraokePublishSelection.SELECTED) {
            trimStartMs.coerceIn(0L, durationMs)
        } else {
            0L
        }

    private fun KaraokeEditProject.selectionEndMs(): Long =
        if (publishSelection == KaraokePublishSelection.SELECTED) {
            trimEndMs.coerceIn(selectionStartMs(), durationMs)
        } else {
            durationMs
    }
}
