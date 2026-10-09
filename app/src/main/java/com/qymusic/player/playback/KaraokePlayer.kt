package com.qymusic.player.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.qymusic.player.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class KaraokePlayerState(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val pitchSemitones: Float = 0f,
    val vocalSplitMode: VocalSplitMode = VocalSplitMode.INSTRUMENTAL,
    val audioSessionId: Int = 0,
    val errorMessage: String? = null,
) {
    val showPauseIcon: Boolean get() = isPlaying || playWhenReady
}

/**
 * K 歌使用独立播放器，避免改变曲库播放器的进度、暂停状态和音效参数。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class KaraokePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val vocalSplitProcessor = VocalSplitProcessor()
    private val rotatingProcessor = RotatingChannelProcessor()
    private val _state = MutableStateFlow(KaraokePlayerState())
    val state: StateFlow<KaraokePlayerState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentTrack: Track? = null
    private var positionJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            syncState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _state.update { it.copy(audioSessionId = audioSessionId) }
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.update {
                it.copy(errorMessage = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private val player: ExoPlayer = ExoPlayer.Builder(
        appContext,
        HiResAudioRenderersFactory(
            appContext,
            probeAudioOutput(appContext),
            vocalSplitProcessor,
            rotatingProcessor,
            isMagicActive = { true },
        ),
    )
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(playerListener)
        }

    init {
        startPositionUpdates()
    }

    fun start(
        track: Track,
        autoPlay: Boolean = true,
    ) {
        currentTrack = track
        vocalSplitProcessor.mode = VocalSplitMode.INSTRUMENTAL
        player.setMediaItem(track.toMediaItem())
        player.setPlaybackParameters(
            PlaybackParameters(1f, pitchFactorOfSemitones(0f)),
        )
        player.prepare()
        player.seekTo(0L)
        if (autoPlay) {
            player.play()
        } else {
            player.pause()
        }
        _state.value = KaraokePlayerState(
            trackId = track.id,
            playWhenReady = autoPlay,
            durationMs = track.durationMs,
            speed = 1f,
            pitchSemitones = 0f,
            vocalSplitMode = VocalSplitMode.INSTRUMENTAL,
            audioSessionId = player.audioSessionId,
        )
        syncState()
    }

    fun togglePlayPause() {
        if (player.playWhenReady) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun pause() {
        player.pause()
    }

    fun play() {
        player.play()
    }

    fun seekTo(positionMs: Long) {
        val duration = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: currentTrack?.durationMs
            ?: 0L
        val target = if (duration > 0L) {
            positionMs.coerceIn(0L, duration)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        player.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    fun setPlaybackSpeed(speed: Float) {
        val snapped = snapPlaybackSpeed(speed)
        applyPlaybackParameters(
            speed = snapped,
            pitchSemitones = _state.value.pitchSemitones,
        )
    }

    fun setPitchSemitones(semitones: Float) {
        val clamped = semitones.coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        applyPlaybackParameters(
            speed = _state.value.speed,
            pitchSemitones = clamped,
        )
    }

    fun setVocalSplitMode(mode: VocalSplitMode) {
        vocalSplitProcessor.mode = mode
        _state.update { it.copy(vocalSplitMode = mode) }
    }

    fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    fun stopAndClear() {
        player.stop()
        player.clearMediaItems()
        currentTrack = null
        _state.value = KaraokePlayerState()
    }

    fun release() {
        positionJob?.cancel()
        positionJob = null
        scope.cancel()
        player.removeListener(playerListener)
        player.release()
        currentTrack = null
        _state.value = KaraokePlayerState()
    }

    private fun startPositionUpdates() {
        positionJob = scope.launch {
            while (isActive) {
                syncState()
                delay(POSITION_REFRESH_MS)
            }
        }
    }

    private fun syncState() {
        val duration = player.duration
            .takeIf { it != C.TIME_UNSET && it > 0L }
            ?: currentTrack?.durationMs
            ?: 0L
        _state.update {
            it.copy(
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                audioSessionId = player.audioSessionId,
            )
        }
    }

    private fun applyPlaybackParameters(
        speed: Float,
        pitchSemitones: Float,
    ) {
        val snapped = snapPlaybackSpeed(speed)
        val clampedPitch = pitchSemitones.coerceIn(
            MIN_PITCH_SEMITONES,
            MAX_PITCH_SEMITONES,
        )
        player.setPlaybackParameters(
            PlaybackParameters(snapped, pitchFactorOfSemitones(clampedPitch)),
        )
        _state.update {
            it.copy(
                speed = snapped,
                pitchSemitones = clampedPitch,
            )
        }
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist.takeIf { it.isNotBlank() })
                    .setAlbumTitle(album.takeIf { it.isNotBlank() })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

    private companion object {
        const val POSITION_REFRESH_MS = 100L
    }
}
