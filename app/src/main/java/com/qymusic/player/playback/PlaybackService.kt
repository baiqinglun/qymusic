package com.qymusic.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.util.Log
import com.qymusic.player.MainActivity
import com.qymusic.player.data.SettingsStore
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val equalizerController by lazy {
        EqualizerControllerProvider.get(this)
    }
    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizerController.attachAudioSession(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 输出能力探测一次，交给自定义 RenderersFactory 决定位深/浮点输出方式。
        val outputInfo = probeAudioOutput(this)
        Log.i(
            AUDIO_LOG_TAG,
            "输出探测：${outputInfo.nativeDescription}，路由=${outputInfo.deviceNames}，" +
                outputInfo.highResDescription,
        )

        val settingsStore = SettingsStore(this)
        val isMagicActive = {
            val settings = settingsStore.settings.value
            abs(settings.playbackSpeed - 1f) > 0.001f ||
                abs(settings.playbackPitchSemitones) > 0.001f ||
                VocalSplitController.processor.mode != VocalSplitMode.BOTH ||
                RotatingChannelController.processor.state.enabled
        }

        val player = ExoPlayer.Builder(
            this,
            HiResAudioRenderersFactory(
                this,
                outputInfo,
                VocalSplitController.processor,
                RotatingChannelController.processor,
                isMagicActive,
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
            }
        player.addListener(playerListener)
        if (player.audioSessionId > 0) {
            equalizerController.attachAudioSession(player.audioSessionId)
        }

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    private companion object {
        const val AUDIO_LOG_TAG = "QYMusicAudioOut"
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.player?.removeListener(playerListener)
        equalizerController.release()
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
