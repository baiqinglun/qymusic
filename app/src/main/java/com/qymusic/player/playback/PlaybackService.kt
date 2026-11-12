package com.qymusic.player.playback

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.util.Log
import com.qymusic.player.MainActivity
import com.qymusic.player.data.QYMusicDatabase
import com.qymusic.player.data.SettingsStore
import com.qymusic.player.data.toPlaybackMediaItem
import com.qymusic.player.widget.NowPlayingWidget
import com.qymusic.player.widget.NowPlayingWidgetProvider
import com.qymusic.player.widget.WidgetPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

@androidx.annotation.OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    /** 缓存统计只写日志的轻活，也别占主线程。 */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** 组件推送/冷启动恢复都要读 player（主线程约束），统一挂主线程 scope。 */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var widgetTicker: Job? = null
    /** 当前封面缓存键（trackId + artwork 字节数），变了才重新解码。 */
    private var lastArtworkKey: String? = null
    /** 组件冷启动恢复进行中标记，防止连点触发多次重建队列。 */
    private var restoreInFlight = false
    private val settingsStore by lazy { SettingsStore(this) }
    private val database by lazy { QYMusicDatabase(this) }
    private val equalizerController by lazy {
        EqualizerControllerProvider.get(this)
    }
    private val playerListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizerController.attachAudioSession(audioSessionId)
        }

        // onEvents 是批量回调，天然把一次换曲/播放态切换合并成一次推送。
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                )
            ) {
                refreshWidget()
            }
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
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(PlaybackCache.dataSourceFactory(this)),
            )
            // 起播优先：250ms 起播缓冲、1s 重缓冲、1s 最小缓冲、30s 最大缓冲。
            // Media3 1.8 断言 minBufferMs >= bufferForPlaybackAfterRebufferMs；
            // 250ms 是为了在快网下第一声更早，同时让首次出声时缓存还没写满 1MB。
            // 弱网下 250ms 容易再卡，S4 会按网络类型把 afterRebuffer 提到 3s。
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1000, 30_000, 250, 1000)
                    .build(),
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
        // 起播验收打点：READY 是解码器准备好，AUDIO_START 才是真正出声；
        // 两个时刻都记 cachedBytes，用来证明播放没有等整首缓存完。
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onPlaybackStateChanged(
                    eventTime: AnalyticsListener.EventTime,
                    state: Int,
                ) {
                    if (state == Player.STATE_READY) {
                        logStartupStage("READY", player)
                    }
                }

                override fun onAudioPositionAdvancing(
                    eventTime: AnalyticsListener.EventTime,
                    playoutStartSystemTimeMs: Long,
                ) {
                    logStartupStage("AUDIO_START", player)
                }
            },
        )
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

    /**
     * Player 只能在主线程读；缓存字节数丢到 IO 线程算，避免一次打点卡住播放事件。
     */
    private fun logStartupStage(stage: String, player: ExoPlayer) {
        val mediaItem = player.currentMediaItem ?: return
        val trackId = mediaItem.mediaId
        val cacheKey = mediaItem.localConfiguration?.customCacheKey
        ioScope.launch {
            val cachedBytes = cacheKey
                ?.let { PlaybackCache.cachedBytes(this@PlaybackService, it) }
                ?: 0L
            val totalBytes = OnlinePlaybackRegistry.find(cacheKey)?.contentLengthBytes ?: -1L
            OnlinePlaybackRegistry.logStage(trackId, stage, cachedBytes, totalBytes)
        }
    }

    /**
     * 播放状态/曲目变化时整版推一次，并按播放态启停进度 tick。
     * 桌面上没有组件实例时 provider 内部会跳过渲染，这里不用关心。
     */
    private fun refreshWidget() {
        mainScope.launch {
            pushWidgetSnapshot()
            if (mediaSession?.player?.isPlaying == true) {
                startWidgetTicker()
            } else {
                widgetTicker?.cancel()
                widgetTicker = null
            }
        }
    }

    private fun startWidgetTicker() {
        if (widgetTicker?.isActive == true) return
        widgetTicker = mainScope.launch {
            while (isActive) {
                delay(WIDGET_PROGRESS_INTERVAL_MS)
                pushWidgetSnapshot()
            }
        }
    }

    /**
     * 组一帧 [WidgetPlaybackState] 推给组件；封面在 artworkData 就绪后异步解码
     * （连同播放页同源的背景色），再补推一次带封面的画面。
     *
     * 冷启动恢复时 ViewModel 没跑、artworkData 不会被写入——本地曲目直接从
     * 文件读内嵌封面兜底（在线曲目没有本地文件可读，回落占位图标）。
     * 按 artworkKey 去重，不会反复解码；应用打开后 ViewModel 的
     * replaceMediaItem 会带来正式 artworkData，key 变化自然覆盖。
     */
    private fun pushWidgetSnapshot() {
        val player = mediaSession?.player ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val state = WidgetPlaybackState(
            trackId = item?.mediaId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = if (player.duration > 0) player.duration else 0L,
        )
        NowPlayingWidgetProvider.push(this, state)
        val sessionArtwork = metadata?.artworkData
        if (sessionArtwork != null) {
            val artworkKey = "${item.mediaId}|${sessionArtwork.size}"
            if (artworkKey == lastArtworkKey) return
            lastArtworkKey = artworkKey
            mainScope.launch(Dispatchers.Default) {
                val decoded = NowPlayingWidget.decodeArtwork(sessionArtwork) ?: return@launch
                NowPlayingWidgetProvider.push(this@PlaybackService, state, decoded, artworkKey)
            }
            return
        }
        val localUri = item?.localConfiguration?.uri?.takeIf {
            it.scheme == ContentResolver.SCHEME_CONTENT || it.scheme == ContentResolver.SCHEME_FILE
        } ?: return
        val localKey = "${item.mediaId}|local"
        if (localKey == lastArtworkKey) return
        lastArtworkKey = localKey
        val appContext = applicationContext
        mainScope.launch(Dispatchers.Default) {
            val bytes = NowPlayingWidget.readEmbeddedArtworkBytes(appContext, localUri)
                ?: return@launch
            val decoded = NowPlayingWidget.decodeArtwork(bytes) ?: return@launch
            NowPlayingWidgetProvider.push(this@PlaybackService, state, decoded, localKey)
        }
    }

    /**
     * 组件按钮走 media button PendingIntent 进到这里，按键由本服务直接分发，
     * 不依赖 media3 默认回调——默认回调要求内部通知控制器已连接才处理
     * NEXT/PREVIOUS，暂停超时/冷启动时连接断开，事件会被静默丢弃。
     *
     * 队列为空说明进程是冷的（App 没开过/被杀过），先在后台重建曲库队列
     * 再执行这次按键——"点组件自动后台启动软件并完成操作"。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyCode = IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_KEY_EVENT,
                KeyEvent::class.java,
            )?.keyCode ?: 0
            val player = mediaSession?.player
            if (player != null && keyCode in WIDGET_HANDLED_KEY_CODES) {
                val cold = player.currentMediaItem == null
                if (cold && !restoreInFlight) {
                    restoreInFlight = true
                    restoreForWidgetCommand(keyCode)
                    return START_STICKY
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_NEXT -> player.seekToNextMediaItem()
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> player.seekToPreviousMediaItem()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
                    KeyEvent.KEYCODE_MEDIA_REWIND -> player.seekBack()
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> player.seekForward()
                    else -> player.play()
                }
                return START_STICKY
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * 与应用内恢复同源：数据库曲目缓存 → 整库队列 → 上次曲目/进度。
     * PLAY/上一首/下一首都是用户明确按键，无论 autoPlayOnLaunch 都播起来；
     * PAUSE 则恢复成暂停态。恢复不了（没缓存/没记忆/曲目没了）就静默放弃。
     *
     * 上一首/下一首的目标曲目直接换算成起始 index 传给 setMediaItems——
     * ExoPlayer 的队列变更是异步生效的，set 完立刻 seekToNext 会落在旧
     * （空）时间线上被吞掉。
     */
    private fun restoreForWidgetCommand(keyCode: Int) {
        ioScope.launch {
            val cached = runCatching { database.loadTrackCache() }.getOrNull().orEmpty()
            val memory = settingsStore.loadLastPlayback()
            val tracks = cached.values.map { it.track }.sortedBy { it.title.lowercase() }
            val trackIndex = memory?.let { m -> tracks.indexOfFirst { it.id == m.trackId } } ?: -1
            if (memory == null || trackIndex < 0) {
                restoreInFlight = false
                // 冷启动是 startForegroundService 拉起来的，恢复失败必须自行结束，
                // 否则 FGS 超时（ForegroundServiceDidNotStartInTime）崩溃。
                stopSelf()
                return@launch
            }
            var startIndex = trackIndex
            var startPositionMs = memory.positionMs
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    startIndex = (trackIndex + 1).coerceAtMost(tracks.lastIndex)
                    startPositionMs = 0L
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    startIndex = (trackIndex - 1).coerceAtLeast(0)
                    startPositionMs = 0L
                }
            }
            Log.i(AUDIO_LOG_TAG, "组件冷恢复：keyCode=$keyCode trackIndex=$trackIndex startIndex=$startIndex")
            mainScope.launch {
                restoreInFlight = false
                val player = mediaSession?.player ?: return@launch
                if (player.currentMediaItem != null) return@launch
                player.setMediaItems(
                    tracks.map { it.toPlaybackMediaItem() },
                    startIndex,
                    startPositionMs,
                )
                player.prepare()
                if (keyCode != KeyEvent.KEYCODE_MEDIA_PAUSE) {
                    player.play()
                }
            }
        }
    }

    private companion object {
        const val AUDIO_LOG_TAG = "QYMusicAudioOut"
        const val WIDGET_PROGRESS_INTERVAL_MS = 1000L

        /** 组件/媒体按键里由本服务直接分发的键；STOP 之类忽略走默认。 */
        val WIDGET_HANDLED_KEY_CODES = setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_HEADSETHOOK,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        widgetTicker?.cancel()
        NowPlayingWidgetProvider.pushEmpty(this)
        ioScope.cancel()
        mainScope.cancel()
        mediaSession?.player?.removeListener(playerListener)
        equalizerController.release()
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
