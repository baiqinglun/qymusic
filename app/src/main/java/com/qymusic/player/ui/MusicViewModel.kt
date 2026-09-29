package com.qymusic.player.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.qymusic.player.data.AppSettings
import com.qymusic.player.data.FolderStore
import com.qymusic.player.data.LaunchScanTiming
import com.qymusic.player.data.LyricAlignment
import com.qymusic.player.data.LyricWordAnimationStyle
import com.qymusic.player.data.MusicFolder
import com.qymusic.player.data.MusicScanner
import com.qymusic.player.data.PlaybackSummary
import com.qymusic.player.data.QYMusicDatabase
import com.qymusic.player.data.SettingsStore
import com.qymusic.player.data.ThemeMode
import com.qymusic.player.data.Track
import com.qymusic.player.data.TrackPlaybackStats
import com.qymusic.player.data.UserPlaylist
import com.qymusic.player.lyrics.Lyrics
import com.qymusic.player.lyrics.LyricsRepository
import com.qymusic.player.lyrics.withOffset
import com.qymusic.player.playback.EqualizerControllerProvider
import com.qymusic.player.playback.EqualizerUiState
import com.qymusic.player.playback.AudioOutputInfo
import com.qymusic.player.playback.MAX_PITCH_SEMITONES
import com.qymusic.player.playback.MAX_PLAYBACK_SPEED
import com.qymusic.player.playback.MIN_PITCH_SEMITONES
import com.qymusic.player.playback.MIN_PLAYBACK_SPEED
import com.qymusic.player.playback.PlaybackMode
import com.qymusic.player.playback.ReverbPreset
import com.qymusic.player.playback.PlaybackService
import com.qymusic.player.playback.VocalSplitController
import com.qymusic.player.playback.VocalSplitMode
import com.qymusic.player.playback.RotatingChannelController
import com.qymusic.player.playback.RotationUiState
import com.qymusic.player.playback.pitchFactorOfSemitones
import com.qymusic.player.playback.playbackModeOf
import com.qymusic.player.playback.snapPlaybackSpeed
import com.qymusic.player.playback.probeAudioOutput
import java.util.Calendar
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    /** 播放意图：缓冲或临时中断时仍为 true，用于让播放/暂停按钮保持稳定。 */
    val playWhenReady: Boolean = false,
    val currentTrack: Track? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val playbackMode: PlaybackMode = PlaybackMode.SEQUENTIAL,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val errorMessage: String? = null,
)

/** 定时关闭：关闭 / 倒计时 / 播完当前曲目。 */
enum class SleepTimerMode { OFF, TIMER, END_OF_TRACK }

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val remainingMs: Long = 0L,
) {
    val active: Boolean get() = mode != SleepTimerMode.OFF
}

/** 缓冲或 seek 造成瞬时 isPlaying=false 时，按钮仍应显示为“暂停”，避免图标跳动。 */
val PlayerUiState.showPauseIcon: Boolean
    get() = isPlaying || playWhenReady

sealed interface ScanUiState {
    /** 启动时读本地缓存的加载态：读完之前界面只显示 loading。 */
    data object Loading : ScanUiState
    data object Idle : ScanUiState
    data object Scanning : ScanUiState
    /** 已显示旧列表，正在后台刷新；只在歌曲列表区域显示轻量 loading。 */
    data object Refreshing : ScanUiState
    data class Complete(val trackCount: Int) : ScanUiState
    data class Failed(val message: String) : ScanUiState
}

sealed interface LyricsUiState {
    data object None : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lyrics: Lyrics) : LyricsUiState
    data class Failed(val message: String) : LyricsUiState
}

@androidx.annotation.OptIn(UnstableApi::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val folderStore = FolderStore(application)
    private val scanner = MusicScanner(application)
    private val lyricsRepository = LyricsRepository(application)
    private val settingsStore = SettingsStore(application)
    private val database = QYMusicDatabase(application)
    private val equalizerController = EqualizerControllerProvider.get(application)

    val settings: StateFlow<AppSettings> = settingsStore.settings
    val equalizerState: StateFlow<EqualizerUiState> = equalizerController.state

    /** 人声 / 伴奏切换：原声 / 仅人声 / 仅伴奏。 */
    private val _vocalSplitMode = MutableStateFlow(VocalSplitController.processor.mode)
    val vocalSplitMode: StateFlow<VocalSplitMode> = _vocalSplitMode.asStateFlow()

    /** 当前音轨的音频格式，以及人声 / 伴奏效果有没有接上（显示在弹窗里）。 */
    val audioEffectStatus: StateFlow<String> = VocalSplitController.processor.formatState

    /** 自定义音频链有没有被播放服务创建（和上一行一起排查效果链路）。 */
    val audioSinkStatus: StateFlow<String> = VocalSplitController.sinkState

    /** 循环声道（8D 环绕）状态。 */
    private val _rotationState = MutableStateFlow(RotatingChannelController.processor.state)
    val rotationState: StateFlow<RotationUiState> = _rotationState.asStateFlow()

    private val _folders = MutableStateFlow(folderStore.load())
    val folders: StateFlow<List<MusicFolder>> = _folders.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    /** 当前输出链路能力，用于设置页展示 Hi-Res 直通情况。 */
    private val _audioOutput = MutableStateFlow(probeAudioOutput(application))
    val audioOutput: StateFlow<AudioOutputInfo> = _audioOutput.asStateFlow()

    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.None)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private val _lyricOffsetMs = MutableStateFlow(0L)
    val lyricOffsetMs: StateFlow<Long> = _lyricOffsetMs.asStateFlow()

    private val _artwork = MutableStateFlow<Bitmap?>(null)
    val artwork: StateFlow<Bitmap?> = _artwork.asStateFlow()

    /** 列表用的封面缩略图，值是 null 表示这首歌曲没有内嵌封面。 */
    private val _libraryArtwork = MutableStateFlow<Map<String, Bitmap?>>(emptyMap())
    val libraryArtwork: StateFlow<Map<String, Bitmap?>> = _libraryArtwork.asStateFlow()

    private val artworkRequests = mutableSetOf<String>()
    private val artworkOrder = ArrayDeque<String>()
    private val artworkSemaphore = Semaphore(permits = ARTWORK_LOAD_CONCURRENCY)
    /** 已解码但还没提交给界面的封面，攒一批一起发，避免逐张触发整页重组。 */
    private val pendingArtwork = LinkedHashMap<String, Bitmap?>()
    private var artworkFlushJob: Job? = null

    private val _playbackSummary = MutableStateFlow(PlaybackSummary())
    val playbackSummary: StateFlow<PlaybackSummary> = _playbackSummary.asStateFlow()

    private val _trackStats = MutableStateFlow<List<TrackPlaybackStats>>(emptyList())
    val trackStats: StateFlow<List<TrackPlaybackStats>> = _trackStats.asStateFlow()

    /** 统计页当前选择的时间范围（天 / 周 / 月 / 年）。 */
    private val _statsRange = MutableStateFlow(StatsRange.DAY)
    val statsRange: StateFlow<StatsRange> = _statsRange.asStateFlow()
    /** 统计页当前查看的日期锚点，默认今天；可以前后翻或直接选日期。 */
    private val _statsAnchorMs = MutableStateFlow(System.currentTimeMillis())
    val statsAnchorMs: StateFlow<Long> = _statsAnchorMs.asStateFlow()
    private val _rangeStats = MutableStateFlow(RangeStats())
    val rangeStats: StateFlow<RangeStats> = _rangeStats.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    /** 歌单自定义封面，按歌单 id 缓存；null 表示解析失败，界面会退回默认封面。 */
    private val _playlistCovers = MutableStateFlow<Map<Long, Bitmap?>>(emptyMap())
    val playlistCovers: StateFlow<Map<Long, Bitmap?>> = _playlistCovers.asStateFlow()
    private val playlistCoverRequests = mutableSetOf<Long>()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var queueTracks: List<Track> = emptyList()
    private var scanJob: Job? = null
    private var lyricsJob: Job? = null
    private var lyricOffsetSaveJob: Job? = null
    private var lyricOffsetSaveTrackId: String? = null
    private var currentLyrics: Lyrics? = null
    private var playbackFadeJob: Job? = null
    private var playbackVolume = 1f
    private var artworkJob: Job? = null
    private var pendingPlayback: Pair<Track, List<Track>>? = null
    private var playbackRestoreAttempted = false
    private var lastPlaybackMemorySavedAtMs = 0L

    private data class ListeningSession(
        val trackId: String,
        val startedAt: Long,
        val historyReady: Job,
    )

    private var currentListeningSession: ListeningSession? = null
    private var listeningTrackId: String? = null
    private var listeningSession: ListeningSession? = null
    private var listeningPositionMs: Long = 0L
    private var listeningTickAtMs: Long = 0L
    private var pendingListenedMs: Long = 0L
    private var pendingListenedTrack: Track? = null
    private var pendingListenedSession: ListeningSession? = null
    private var sleepTimerJob: Job? = null
    /** 定时方式选「播完当前曲目」时，等这首播完再暂停。 */
    private var pauseWhenTrackEnds = false

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncControllerState()
            persistPlaybackMemory(force = !player.playWhenReady)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            equalizerController.attachAudioSession(audioSessionId)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                consumeEndOfTrackSleepTimer()
            }
            val track = mediaItem?.mediaId?.let { mediaId ->
                queueTracks.firstOrNull { it.id == mediaId }
            } ?: return
            recordPlayStart(track)
            persistPlaybackMemory(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                consumeEndOfTrackSleepTimer()
            }
            persistPlaybackMemory(force = true)
        }

        override fun onPlayerError(error: PlaybackException) {
            _playerState.update { it.copy(errorMessage = error.message) }
        }
    }

    init {
        connectToPlaybackService()
        startPositionTicker()
        // 启动默认只读上次的扫描缓存；用户开启自动重扫且已有目录时，先停在启动扫描页。
        if (
            settings.value.rescanOnLaunch &&
            _folders.value.isNotEmpty() &&
            settings.value.launchScanTiming == LaunchScanTiming.DURING_STARTUP
        ) {
            rescan()
        } else {
            _scanState.value = ScanUiState.Loading
            loadCachedTracks()
        }
        refreshStats()
        refreshPlaylists()
    }

    /** 启动时加载数据库里的曲目缓存；没有缓存就保持空列表等待用户手动扫描。 */
    private fun loadCachedTracks() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { database.loadTrackCache() }
            if (cached.isEmpty()) {
                _scanState.value = ScanUiState.Idle
                launchPostStartupRescanIfNeeded()
                return@launch
            }
            _tracks.value = cached.values
                .map { it.track }
                .sortedBy { it.title.lowercase() }
            if (
                settings.value.rescanOnLaunch &&
                settings.value.launchScanTiming == LaunchScanTiming.AFTER_STARTUP &&
                _folders.value.isNotEmpty()
            ) {
                rescan(showLoading = false, refreshing = true)
            } else {
                _scanState.value = ScanUiState.Complete(cached.size)
            }
            restorePlaybackIfReady()
        }
    }

    private fun launchPostStartupRescanIfNeeded() {
        if (
            settings.value.rescanOnLaunch &&
            settings.value.launchScanTiming == LaunchScanTiming.AFTER_STARTUP &&
            _folders.value.isNotEmpty()
        ) {
            rescan(showLoading = false, refreshing = true)
        }
    }

    fun addFolder(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }

        val folderName = DocumentFile.fromTreeUri(app, uri)?.name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
            ?: uri.toString()
        val updated = _folders.value
            .filterNot { it.uri == uri }
            .plus(MusicFolder(uri = uri, name = folderName))

        _folders.value = updated
        folderStore.save(updated)
        rescan()
    }

    fun removeFolder(folder: MusicFolder) {
        runCatching {
            app.contentResolver.releasePersistableUriPermission(
                folder.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val updated = _folders.value.filterNot { it.uri == folder.uri }
        _folders.value = updated
        folderStore.save(updated)
        rescan()
    }

    fun rescan(
        showLoading: Boolean = true,
        refreshing: Boolean = false,
    ) {
        scanJob?.cancel()
        val foldersToScan = _folders.value
        if (foldersToScan.isEmpty()) {
            _tracks.value = emptyList()
            if (showLoading || refreshing) {
                _scanState.value = ScanUiState.Complete(0)
            }
            return
        }

        if (refreshing) {
            _scanState.value = ScanUiState.Refreshing
        } else if (showLoading) {
            _scanState.value = ScanUiState.Scanning
        }
        scanJob = viewModelScope.launch {
            val scanStartedAt = SystemClock.elapsedRealtime()
            // 扫描期间界面只显示 loading，这里不再中途回填列表：
            // 扫完一次性渲染，避免边扫边刷列表看着卡。
            val cached = withContext(Dispatchers.IO) { database.loadTrackCache() }

            runCatching { scanner.scan(foldersToScan, cached) }
                .onSuccess { scannedTracks ->
                    _tracks.value = scannedTracks.map { it.track }
                    _scanState.value = ScanUiState.Complete(scannedTracks.size)
                    withContext(Dispatchers.IO) { database.replaceTrackCache(scannedTracks) }
                    Log.i(
                        SCAN_LOG_TAG,
                        "扫描完成：${scannedTracks.size} 首，用时 " +
                            "${SystemClock.elapsedRealtime() - scanStartedAt}ms，" +
                            "复用缓存 ${scannedTracks.count { cached.containsKey(it.track.id) }} 首",
                    )
                    restorePlaybackIfReady()
                }
                .onFailure { error ->
                    if (showLoading) {
                        _scanState.value = ScanUiState.Failed(
                            error.message ?: error.javaClass.simpleName,
                        )
                    } else if (refreshing) {
                        _scanState.value = ScanUiState.Complete(_tracks.value.size)
                    }
                }
        }
    }

    fun playTrack(track: Track, queue: List<Track> = _tracks.value) {
        if (queue.isEmpty()) return
        val activeController = controller
        if (activeController == null) {
            pendingPlayback = track to queue
            return
        }
        playTrack(activeController, track, queue)
    }

    private fun playTrack(
        activeController: MediaController,
        track: Track,
        queue: List<Track>,
    ) {
        playbackRestoreAttempted = true
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        queueTracks = queue
        activeController.setMediaItems(queue.map(::toMediaItem), index, 0L)
        activeController.prepare()
        playWithFade(activeController)
        syncControllerState()
        persistPlaybackMemory(force = true)
    }

    fun togglePlayPause() {
        val activeController = controller ?: return
        if (activeController.mediaItemCount == 0) {
            _tracks.value.firstOrNull()?.let(::playTrack)
            return
        }
        if (activeController.isPlaying) {
            pauseWithFade(activeController)
        } else {
            playWithFade(activeController)
        }
    }

    private fun playWithFade(activeController: MediaController) {
        playbackFadeJob?.cancel()
        if (!settings.value.playbackFadeEnabled) {
            setPlaybackVolume(activeController, 1f)
            activeController.play()
            return
        }
        playbackFadeJob = viewModelScope.launch {
            setPlaybackVolume(activeController, 0f)
            activeController.play()
            fadePlaybackVolume(
                activeController = activeController,
                from = 0f,
                to = 1f,
            )
        }
    }

    private fun pauseWithFade(activeController: MediaController) {
        playbackFadeJob?.cancel()
        if (!settings.value.playbackFadeEnabled) {
            setPlaybackVolume(activeController, 1f)
            activeController.pause()
            return
        }
        playbackFadeJob = viewModelScope.launch {
            val from = playbackVolume.coerceIn(0f, 1f)
            if (from > PLAYBACK_FADE_MIN_VOLUME) {
                fadePlaybackVolume(
                    activeController = activeController,
                    from = from,
                    to = 0f,
                )
            }
            setPlaybackVolume(activeController, 0f)
            activeController.pause()
        }
    }

    private suspend fun fadePlaybackVolume(
        activeController: MediaController,
        from: Float,
        to: Float,
    ) {
        repeat(PLAYBACK_FADE_STEPS) { index ->
            val fraction = (index + 1).toFloat() / PLAYBACK_FADE_STEPS
            val eased = fraction * fraction * (3f - 2f * fraction)
            setPlaybackVolume(
                activeController,
                from + (to - from) * eased,
            )
            if (index < PLAYBACK_FADE_STEPS - 1) {
                delay(PLAYBACK_FADE_STEP_MS)
            }
        }
    }

    private fun setPlaybackVolume(
        activeController: MediaController,
        volume: Float,
    ) {
        val normalized = volume.coerceIn(0f, 1f)
        playbackVolume = normalized
        runCatching { activeController.volume = normalized }
    }

    fun playPrevious() {
        val activeController = controller ?: return
        if (activeController.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
            activeController.seekTo(0L)
        } else {
            activeController.seekToPreviousMediaItem()
        }
    }

    fun playNext() {
        controller?.seekToNextMediaItem()
    }

    /**
     * 整个列表从头播放；[shuffle] 为 true 时打开随机模式并从随机一首开始。
     */
    fun playAllTracks(tracks: List<Track>, shuffle: Boolean) {
        if (tracks.isEmpty()) return
        val startIndex = if (shuffle) Random.nextInt(tracks.size) else 0
        playTrack(tracks[startIndex], tracks)
        val activeController = controller ?: return
        runCatching {
            activeController.shuffleModeEnabled = shuffle
            activeController.repeatMode = if (shuffle) {
                Player.REPEAT_MODE_ALL
            } else {
                Player.REPEAT_MODE_OFF
            }
        }
        syncControllerState()
    }

    /** 顺序播放 → 单曲循环 → 随机播放 → 顺序播放。 */
    fun cyclePlaybackMode() {
        val activeController = controller ?: return
        val next = playbackModeOf(
            repeatMode = activeController.repeatMode,
            shuffleEnabled = activeController.shuffleModeEnabled,
        ).next()
        activeController.shuffleModeEnabled = next == PlaybackMode.SHUFFLE
        activeController.repeatMode = when (next) {
            PlaybackMode.SEQUENTIAL -> Player.REPEAT_MODE_OFF
            PlaybackMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            // 随机播放内部用列表循环，保证打乱后不会播到一半就停。
            PlaybackMode.SHUFFLE -> Player.REPEAT_MODE_ALL
        }
        syncControllerState()
    }

    fun setPlaybackSpeed(speed: Float) {
        val activeController = controller ?: return
        val snapped = snapPlaybackSpeed(speed)
        // 以本地记录的目标值为准拼参数：MediaController 回读的 playbackParameters
        // 可能是上一次的旧值（命令是异步下发的），拿它当基准会把另一半设置冲掉。
        val pitchFactor = pitchFactorOfSemitones(settings.value.playbackPitchSemitones)
        _playerState.update { it.copy(speed = snapped) }
        runCatching { activeController.playbackParameters = PlaybackParameters(snapped, pitchFactor) }
        // 记下来，下次启动继续用这个倍速。
        settingsStore.setPlaybackSpeed(snapped)
    }

    fun setPlaybackPitchSemitones(semitones: Float) {
        val activeController = controller ?: return
        val clamped = semitones.coerceIn(MIN_PITCH_SEMITONES, MAX_PITCH_SEMITONES)
        val speed = snapPlaybackSpeed(settings.value.playbackSpeed)
        _playerState.update { it.copy(pitch = pitchFactorOfSemitones(clamped)) }
        runCatching {
            activeController.playbackParameters =
                PlaybackParameters(speed, pitchFactorOfSemitones(clamped))
        }
        settingsStore.setPlaybackPitchSemitones(clamped)
    }

    fun setPlaybackFadeEnabled(enabled: Boolean) {
        settingsStore.setPlaybackFadeEnabled(enabled)
        if (!enabled) {
            playbackFadeJob?.cancel()
            controller?.let { setPlaybackVolume(it, 1f) }
        }
    }

    fun setAutoPlayOnLaunch(enabled: Boolean) {
        settingsStore.setAutoPlayOnLaunch(enabled)
    }

    fun setRescanOnLaunch(enabled: Boolean) {
        settingsStore.setRescanOnLaunch(enabled)
    }

    fun setLaunchScanTiming(timing: LaunchScanTiming) {
        settingsStore.setLaunchScanTiming(timing)
    }

    /**
     * 切换「原声 / 仅人声 / 仅伴奏」。
     * 处理器实例在播放服务里，这里改的是同一个对象，播放中切换立即生效。
     */
    fun setVocalSplitMode(mode: VocalSplitMode) {
        if (_vocalSplitMode.value == mode) return
        VocalSplitController.processor.mode = mode
        _vocalSplitMode.value = mode
    }

    /** 循环声道：开启 / 关闭。 */
    fun setRotationEnabled(enabled: Boolean) {
        updateRotation { it.copy(enabled = enabled) }
    }

    /** 循环声道转速：每秒转多少圈。 */
    fun setRotationSpeed(revolutionsPerSecond: Float) {
        updateRotation {
            it.copy(
                revolutionsPerSecond = revolutionsPerSecond.coerceIn(
                    RotationUiState.MIN_REVOLUTIONS_PER_SECOND,
                    RotationUiState.MAX_REVOLUTIONS_PER_SECOND,
                ),
            )
        }
    }

    /** 循环声道方向：true = 顺时针（左→右），false = 逆时针。 */
    fun setRotationClockwise(clockwise: Boolean) {
        updateRotation { it.copy(clockwise = clockwise) }
    }

    private fun updateRotation(transform: (RotationUiState) -> RotationUiState) {
        val updated = transform(_rotationState.value)
        if (updated == _rotationState.value) return
        RotatingChannelController.processor.state = updated
        _rotationState.value = updated
    }

    /** 倒计时到点后自动暂停播放。 */
    fun startSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        pauseWhenTrackEnds = false
        var remainingMs = minutes * 60_000L
        _sleepTimer.value = SleepTimerState(SleepTimerMode.TIMER, remainingMs)
        sleepTimerJob = viewModelScope.launch {
            while (remainingMs > 0L) {
                delay(1_000L)
                remainingMs = (remainingMs - 1_000L).coerceAtLeast(0L)
                _sleepTimer.value = SleepTimerState(SleepTimerMode.TIMER, remainingMs)
            }
            controller?.let(::pauseWithFade)
            _sleepTimer.value = SleepTimerState()
        }
    }

    /** 当前这首播完就暂停。 */
    fun startSleepTimerAtTrackEnd() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenTrackEnds = true
        _sleepTimer.value = SleepTimerState(SleepTimerMode.END_OF_TRACK)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenTrackEnds = false
        _sleepTimer.value = SleepTimerState()
    }

    private fun consumeEndOfTrackSleepTimer() {
        if (!pauseWhenTrackEnds) return
        pauseWhenTrackEnds = false
        _sleepTimer.value = SleepTimerState()
        controller?.let(::pauseWithFade)
    }

    fun playQueueIndex(index: Int) {
        val activeController = controller ?: return
        if (index !in 0 until activeController.mediaItemCount) return
        val wasCurrent = activeController.currentMediaItemIndex == index
        val track = queueTracks.getOrNull(index)
        activeController.seekToDefaultPosition(index)
        activeController.prepare()
        playWithFade(activeController)
        if (wasCurrent && track != null) {
            recordPlayStart(track)
        }
        syncControllerState()
    }

    /** 拖动排序播放队列，不会打断当前播放。 */
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val activeController = controller ?: return
        val itemCount = activeController.mediaItemCount
        if (fromIndex !in 0 until itemCount || toIndex !in 0 until itemCount) return
        if (fromIndex == toIndex) return
        activeController.moveMediaItem(fromIndex, toIndex)
        queueTracks = queueTracks.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        syncControllerState()
    }

    /** 把歌曲插到当前播放的下一首。 */
    fun addToQueueNext(track: Track) {
        val activeController = controller
        if (activeController == null || activeController.mediaItemCount == 0) {
            playTrack(track, _tracks.value)
            return
        }
        var currentIndex = activeController.currentMediaItemIndex
        // 正在播放的这首本身就是“当前”，不用再排一次。
        if (queueTracks.getOrNull(currentIndex)?.id == track.id) return

        // 队列里已经有这首歌时移动到下一首，避免出现重复项。
        val existingIndex = queueTracks.indexOfFirst { it.id == track.id }
        if (existingIndex >= 0) {
            activeController.removeMediaItem(existingIndex)
            if (existingIndex < currentIndex) currentIndex--
        }

        val insertIndex = (currentIndex + 1)
            .coerceIn(0, activeController.mediaItemCount)
        activeController.addMediaItem(insertIndex, toMediaItem(track))
        queueTracks = queueTracks.toMutableList().apply {
            val removed = indexOfFirst { it.id == track.id }
            if (removed >= 0) removeAt(removed)
            add(insertIndex.coerceIn(0, size), track)
        }
        syncControllerState()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        _playerState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun setThemeMode(mode: ThemeMode) {
        settingsStore.setThemeMode(mode)
    }

    fun setMusicReactiveBackground(enabled: Boolean) {
        settingsStore.setMusicReactiveBackground(enabled)
    }

    fun setArtistPinned(name: String, pinned: Boolean) {
        settingsStore.setArtistPinned(name, pinned)
    }

    fun setAlbumPinned(name: String, pinned: Boolean) {
        settingsStore.setAlbumPinned(name, pinned)
    }

    fun setPlaylistPinned(playlistId: Long, pinned: Boolean) {
        settingsStore.setPlaylistPinned(playlistId, pinned)
    }

    fun setLyricAlignment(alignment: LyricAlignment) {
        settingsStore.setLyricAlignment(alignment)
    }

    fun setLyricFontScale(scale: Float) {
        settingsStore.setLyricFontScale(scale)
    }

    fun setLyricBold(bold: Boolean) {
        settingsStore.setLyricBold(bold)
    }

    fun setLyricInactiveBlur(blurDp: Float) {
        settingsStore.setLyricInactiveBlur(blurDp)
    }

    fun setLyricOffset(offsetMs: Long) {
        val trackId = _playerState.value.currentTrack?.id ?: return
        val clamped = offsetMs.coerceIn(
            -MAX_LYRIC_OFFSET_MS,
            MAX_LYRIC_OFFSET_MS,
        )
        _lyricOffsetMs.value = clamped
        // 界面上的正数表示歌词提前显示，因此内部时间轴使用相反符号。
        val appliedOffsetMs = -clamped
        currentLyrics?.let { lyrics ->
            _lyricsState.value = LyricsUiState.Ready(
                lyrics.withOffset(appliedOffsetMs),
            )
        }

        if (lyricOffsetSaveTrackId == trackId) {
            lyricOffsetSaveJob?.cancel()
        }
        lyricOffsetSaveTrackId = trackId
        lyricOffsetSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(LYRIC_OFFSET_SAVE_DEBOUNCE_MS)
            database.setLyricOffset(trackId, clamped)
        }
    }

    fun setLyricCenterStartEnd(enabled: Boolean) {
        settingsStore.setLyricCenterStartEnd(enabled)
    }

    fun setLyricWordAnimationStyle(style: LyricWordAnimationStyle) {
        settingsStore.setLyricWordAnimationStyle(style)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerController.setEnabled(enabled)
    }

    fun setEqualizerBandLevel(bandIndex: Int, level: Int) {
        equalizerController.setBandLevel(bandIndex, level)
    }

    fun setEqualizerPreset(presetIndex: Int) {
        equalizerController.setPreset(presetIndex)
    }

    fun setBassStrength(strength: Int) {
        equalizerController.setBassStrength(strength)
    }

    fun setVirtualizerStrength(strength: Int) {
        equalizerController.setVirtualizerStrength(strength)
    }

    fun setLoudnessGain(gainMb: Int) {
        equalizerController.setLoudnessGain(gainMb)
    }

    fun setReverbPreset(preset: ReverbPreset) {
        equalizerController.setReverbPreset(preset)
    }

    fun setReverbLevel(level: Int) {
        equalizerController.setReverbLevel(level)
    }

    /**
     * 按需读取某首歌曲的内嵌封面。读取结果会缓存，未读到封面时也记录为空，
     * 避免列表滚动时反复解析同一个文件。
     */
    fun requestLibraryArtwork(track: Track) {
        val cached = _libraryArtwork.value
        if (cached.containsKey(track.id) || track.id in artworkRequests) return
        artworkRequests += track.id
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                artworkSemaphore.withPermit { readArtworkThumbnail(track) }
            }
            artworkRequests -= track.id
            pendingArtwork[track.id] = bitmap
            scheduleArtworkFlush()
        }
    }

    /**
     * 封面是一张张解码好的，逐张提交会让列表整页重组（十几行一起重画，很卡）。
     * 攒一小段时间一起提交，滚动或改排序时就不会一顿一顿。
     */
    private fun scheduleArtworkFlush() {
        if (artworkFlushJob?.isActive == true) return
        artworkFlushJob = viewModelScope.launch {
            delay(ARTWORK_FLUSH_INTERVAL_MS)
            if (pendingArtwork.isEmpty()) return@launch
            val updated = LinkedHashMap(_libraryArtwork.value)
            pendingArtwork.forEach { (trackId, bitmap) ->
                updated[trackId] = bitmap
                artworkOrder.remove(trackId)
                artworkOrder.addLast(trackId)
            }
            pendingArtwork.clear()
            while (artworkOrder.size > LIBRARY_ARTWORK_CACHE_SIZE) {
                updated.remove(artworkOrder.removeFirst())
            }
            _libraryArtwork.value = updated
        }
    }

    fun refreshStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _playbackSummary.value = database.getPlaybackSummary()
            _trackStats.value = database.getTrackStats()
        }
        refreshRangeStats()
    }

    fun setStatsRange(range: StatsRange) {
        if (_statsRange.value == range) return
        _statsRange.value = range
        refreshRangeStats()
    }

    /** 按当前范围前后翻一段（天 → 1 天、周 → 1 周、月 → 1 月、年 → 1 年）。 */
    fun shiftStatsAnchor(steps: Int) {
        if (steps == 0) return
        _statsAnchorMs.value = shiftedStatsAnchor(
            range = _statsRange.value,
            anchorMs = _statsAnchorMs.value,
            steps = steps,
        )
        refreshRangeStats()
    }

    /** 直接跳到某个日期（日期选择器）。 */
    fun setStatsAnchorAt(timestampMs: Long) {
        _statsAnchorMs.value = timestampMs
        refreshRangeStats()
    }

    /** 回到今天 / 本周 / 本月 / 今年。 */
    fun resetStatsAnchor() {
        _statsAnchorMs.value = System.currentTimeMillis()
        refreshRangeStats()
    }

    /** 重新计算当前时间范围的汇总、热力图与播放次数前 20。 */
    private fun refreshRangeStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val range = _statsRange.value
            val anchorMs = _statsAnchorMs.value
            val calendar = Calendar.getInstance().apply { timeInMillis = anchorMs }
            val starts = statsBucketStarts(range, calendar)
            val since = starts.firstOrNull() ?: 0L
            val until = statsRangeEnd(range, calendar)
            val summary = database.getRangeSummary(since, until)
            val entries = database.getRangeHistory(since, until)
            val topTracks = database.getRangeTopTracks(since, until)
            _rangeStats.value = RangeStats(
                range = range,
                anchorMs = anchorMs,
                rangeStartMs = since,
                rangeEndMs = until,
                plays = summary.totalPlays,
                listenedMs = summary.totalListenedMs,
                buckets = aggregateStatsBuckets(range, starts, entries, calendar),
                topTracks = topTracks,
            )
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _playlists.value = database.getPlaylists()
        }
    }

    fun createPlaylist(name: String, initialTrackId: String? = null) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.createPlaylist(normalizedName, initialTrackId)
            _playlists.value = database.getPlaylists()
        }
    }

    fun setTrackInPlaylist(
        playlistId: Long,
        trackId: String,
        included: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (included) {
                database.addTrackToPlaylist(playlistId, trackId)
            } else {
                database.removeTrackFromPlaylist(playlistId, trackId)
            }
            _playlists.value = database.getPlaylists()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.deletePlaylist(playlistId)
            _playlists.value = database.getPlaylists()
        }
    }

    fun setPlaylistCover(playlistId: Long, coverUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            database.setPlaylistCover(playlistId, coverUri?.toString())
            _playlists.value = database.getPlaylists()
        }
        // 让界面重新按新封面加载。
        _playlistCovers.value = _playlistCovers.value - playlistId
        playlistCoverRequests -= playlistId
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.renamePlaylist(playlistId, normalized)
            _playlists.value = database.getPlaylists()
        }
    }

    fun requestPlaylistCover(playlist: UserPlaylist) {
        val coverUri = playlist.coverUri ?: return
        if (_playlistCovers.value.containsKey(playlist.id) ||
            !playlistCoverRequests.add(playlist.id)
        ) {
            return
        }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                artworkSemaphore.withPermit { readImageBitmap(coverUri) }
            }
            playlistCoverRequests -= playlist.id
            _playlistCovers.value = _playlistCovers.value + (playlist.id to bitmap)
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(
            app,
            ComponentName(app, PlaybackService::class.java),
        )
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { mediaController ->
                        controller = mediaController
                        mediaController.addListener(playerListener)
                        // 连接（或重新连接）时刷新一次输出能力，插拔 USB DAC 后能看到最新结果。
                        _audioOutput.value = probeAudioOutput(app)
                        // 恢复上次保存的变速 / 变调，避免重启后回到 1x。
                        val savedSettings = settings.value
                        playbackVolume = mediaController.volume.coerceIn(0f, 1f)
                        if (!savedSettings.playbackFadeEnabled) {
                            setPlaybackVolume(mediaController, 1f)
                        }
                        runCatching {
                            mediaController.playbackParameters = PlaybackParameters(
                                savedSettings.playbackSpeed.coerceIn(
                                    MIN_PLAYBACK_SPEED,
                                    MAX_PLAYBACK_SPEED,
                                ),
                                pitchFactorOfSemitones(
                                    savedSettings.playbackPitchSemitones.coerceIn(
                                        MIN_PITCH_SEMITONES,
                                        MAX_PITCH_SEMITONES,
                                    ),
                                ),
                            )
                        }
                        _playerState.update { it.copy(connected = true) }
                        syncControllerState()
                        pendingPlayback?.let { (track, queue) ->
                            pendingPlayback = null
                            playTrack(mediaController, track, queue)
                        }
                        restorePlaybackIfReady()
                    }
                    .onFailure { error ->
                        _playerState.update {
                            it.copy(
                                connected = false,
                                errorMessage = error.message ?: error.javaClass.simpleName,
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(app),
        )
    }

    private fun startPositionTicker() {
        viewModelScope.launch {
            while (isActive) {
                val activeController = controller
                if (activeController != null) {
                    val duration = activeController.duration
                        .takeIf { it != C.TIME_UNSET && it >= 0L }
                        ?: 0L
                    val currentTrack = _playerState.value.currentTrack
                    _playerState.update {
                        it.copy(
                            positionMs = activeController.currentPosition.coerceAtLeast(0L),
                            durationMs = duration.takeIf { value -> value > 0L }
                                ?: currentTrack?.durationMs
                                ?: 0L,
                            bufferedPositionMs = activeController.bufferedPosition
                                .coerceAtLeast(0L),
                        )
                    }
                    updateListeningStats(activeController)
                    persistPlaybackMemory()
                }
                delay(POSITION_REFRESH_MS)
            }
        }
    }

    private fun updateListeningStats(activeController: MediaController) {
        val track = queueTracks.firstOrNull {
            it.id == activeController.currentMediaItem?.mediaId
        }
        val positionMs = activeController.currentPosition.coerceAtLeast(0L)
        val session = currentListeningSession?.takeIf { it.trackId == track?.id }
        val nowMs = SystemClock.elapsedRealtime()
        if (!activeController.isPlaying || track == null || session == null) {
            flushPendingListenedTime()
            listeningTrackId = track?.id
            listeningSession = session
            listeningPositionMs = positionMs
            listeningTickAtMs = nowMs
            return
        }

        if (listeningTrackId != track.id || listeningSession?.startedAt != session.startedAt) {
            flushPendingListenedTime()
            listeningTrackId = track.id
            listeningSession = session
            listeningPositionMs = positionMs
            listeningTickAtMs = nowMs
            return
        }

        val wallDeltaMs = nowMs - listeningTickAtMs
        val positionDeltaMs = positionMs - listeningPositionMs
        val speed = _playerState.value.speed.coerceAtLeast(0.5f)
        val maxPositionStepMs = (MAX_LISTENING_STEP_MS * speed).toLong() +
            MAX_LISTENING_STEP_MS
        if (
            wallDeltaMs in 1L..MAX_LISTENING_STEP_MS &&
            positionDeltaMs in 1L..maxPositionStepMs
        ) {
            pendingListenedMs += wallDeltaMs
            pendingListenedTrack = track
            pendingListenedSession = session
        }
        listeningPositionMs = positionMs
        listeningTickAtMs = nowMs

        if (pendingListenedMs >= LISTENING_FLUSH_MS) {
            flushPendingListenedTime()
        }
    }

    /** 曲库和播放器都准备好后，恢复上次歌曲与进度；是否继续播放由设置决定。 */
    private fun restorePlaybackIfReady() {
        if (playbackRestoreAttempted) return
        val activeController = controller ?: return
        val tracks = _tracks.value
        if (tracks.isEmpty()) return

        if (activeController.mediaItemCount > 0) {
            playbackRestoreAttempted = true
            if (queueTracks.isEmpty()) {
                val currentMediaId = activeController.currentMediaItem?.mediaId
                if (tracks.any { it.id == currentMediaId }) {
                    queueTracks = tracks
                }
            }
            if (settings.value.autoPlayOnLaunch) {
                if (activeController.playbackState != Player.STATE_ENDED) {
                    playWithFade(activeController)
                }
            } else {
                activeController.pause()
            }
            syncControllerState()
            return
        }

        val memory = settingsStore.loadLastPlayback() ?: run {
            playbackRestoreAttempted = true
            return
        }
        val trackIndex = tracks.indexOfFirst { it.id == memory.trackId }
        // 曲库可能还没扫描到这首歌，保留恢复机会，等缓存或重新扫描后再试。
        if (trackIndex < 0) return

        queueTracks = tracks
        activeController.setMediaItems(
            tracks.map(::toMediaItem),
            trackIndex,
            memory.positionMs,
        )
        activeController.prepare()
        if (settings.value.autoPlayOnLaunch) {
            playWithFade(activeController)
        } else {
            activeController.pause()
        }
        playbackRestoreAttempted = true
        syncControllerState()
    }

    private fun persistPlaybackMemory(force: Boolean = false) {
        val activeController = controller ?: return
        val trackId = activeController.currentMediaItem?.mediaId ?: return
        val nowMs = SystemClock.elapsedRealtime()
        if (!force && nowMs - lastPlaybackMemorySavedAtMs < PLAYBACK_MEMORY_SAVE_INTERVAL_MS) {
            return
        }
        val track = queueTracks.firstOrNull { it.id == trackId }
            ?: _tracks.value.firstOrNull { it.id == trackId }
            ?: return
        lastPlaybackMemorySavedAtMs = nowMs
        settingsStore.saveLastPlayback(
            trackId = track.id,
            positionMs = activeController.currentPosition.coerceAtLeast(0L),
        )
    }

    private fun flushPendingListenedTime() {
        val track = pendingListenedTrack
        val listenedMs = pendingListenedMs
        val session = pendingListenedSession
        pendingListenedTrack = null
        pendingListenedMs = 0L
        pendingListenedSession = null
        if (track == null || listenedMs <= 0L) return

        viewModelScope.launch(Dispatchers.IO) {
            session?.historyReady?.join()
            database.addListenedTime(track, listenedMs, session?.startedAt)
            _playbackSummary.value = database.getPlaybackSummary()
            _trackStats.value = database.getTrackStats()
        }
    }

    private fun recordPlayStart(track: Track) {
        val startedAt = System.currentTimeMillis()
        val historyReady = viewModelScope.launch(Dispatchers.IO) {
            database.recordPlayStart(track, startedAt)
            _playbackSummary.value = database.getPlaybackSummary()
            _trackStats.value = database.getTrackStats()
        }
        currentListeningSession = ListeningSession(
            trackId = track.id,
            startedAt = startedAt,
            historyReady = historyReady,
        )
    }

    private fun syncControllerState() {
        val activeController = controller ?: return
        val currentMediaId = activeController.currentMediaItem?.mediaId
        val currentTrack = queueTracks.firstOrNull { it.id == currentMediaId }
        val duration = activeController.duration
            .takeIf { it != C.TIME_UNSET && it >= 0L }
            ?: currentTrack?.durationMs
            ?: 0L
        val previousTrackId = _playerState.value.currentTrack?.id
        // 速度 / 变调以本地保存的目标值为准：MediaController 回读的可能是旧值，
        // 播放中每次事件都同步一次，界面上刚调好的值就会被冲回去，看着像"改不了"。
        val desiredSpeed = snapPlaybackSpeed(settings.value.playbackSpeed)
        val desiredPitch = pitchFactorOfSemitones(settings.value.playbackPitchSemitones)

        _playerState.update {
            it.copy(
                connected = true,
                isPlaying = activeController.isPlaying,
                playWhenReady = activeController.playWhenReady,
                currentTrack = currentTrack,
                positionMs = activeController.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedPositionMs = activeController.bufferedPosition.coerceAtLeast(0L),
                queue = queueTracks,
                playbackMode = playbackModeOf(
                    repeatMode = activeController.repeatMode,
                    shuffleEnabled = activeController.shuffleModeEnabled,
                ),
                speed = desiredSpeed,
                pitch = desiredPitch,
                errorMessage = null,
            )
        }

        if (previousTrackId != currentTrack?.id) {
            loadTrackDetails(currentTrack)
        }
    }

    private fun loadTrackDetails(track: Track?) {
        lyricsJob?.cancel()
        artworkJob?.cancel()
        _artwork.value = null

        if (track == null) {
            currentLyrics = null
            _lyricOffsetMs.value = 0L
            _lyricsState.value = LyricsUiState.None
            return
        }

        currentLyrics = null
        _lyricOffsetMs.value = 0L
        _lyricsState.value = LyricsUiState.Loading
        artworkJob = viewModelScope.launch {
            _artwork.value = readArtwork(track)
        }
        lyricsJob = viewModelScope.launch {
            runCatching {
                val lyrics = lyricsRepository.load(track)
                val lyricOffsetMs = withContext(Dispatchers.IO) {
                    database.getLyricOffset(track.id).coerceIn(
                        -MAX_LYRIC_OFFSET_MS,
                        MAX_LYRIC_OFFSET_MS,
                    )
                }
                lyrics to lyricOffsetMs
            }
                .onSuccess { (lyrics, lyricOffsetMs) ->
                    currentLyrics = lyrics
                    _lyricOffsetMs.value = lyricOffsetMs
                    _lyricsState.value = if (lyrics == null) {
                        LyricsUiState.None
                    } else {
                        LyricsUiState.Ready(
                            lyrics.withOffset(-lyricOffsetMs),
                        )
                    }
                }
                .onFailure { error ->
                    _lyricsState.value = LyricsUiState.Failed(
                        error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }

    private suspend fun readArtwork(track: Track): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(app, track.uri)
            retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readArtworkThumbnail(track: Track): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(app, track.uri)
            retriever.embeddedPicture?.let { bytes ->
                decodeArtwork(bytes, ARTWORK_THUMBNAIL_SIZE_PX)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeArtwork(bytes: ByteArray, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
            bounds.outHeight / (sampleSize * 2) >= targetSizePx
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** 读取用户选的歌单封面图片，按缩略图尺寸解码。 */
    private fun readImageBitmap(uri: String): Bitmap? = runCatching {
        app.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            decodeArtwork(input.readBytes(), ARTWORK_THUMBNAIL_SIZE_PX)
        }
    }.getOrNull()

    private fun toMediaItem(track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist.takeIf { it.isNotBlank() })
                    .setAlbumTitle(track.album.takeIf { it.isNotBlank() })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

    override fun onCleared() {
        persistPlaybackMemory(force = true)
        flushPendingListenedTime()
        playbackFadeJob?.cancel()
        sleepTimerJob?.cancel()
        controller?.removeListener(playerListener)
        if (controller == null) {
            controllerFuture?.let { MediaController.releaseFuture(it) }
        } else {
            controller?.release()
        }
        controller = null
        controllerFuture = null
        pendingPlayback = null
    }

    private companion object {
        const val SCAN_LOG_TAG = "QYMusicScan"
        const val POSITION_REFRESH_MS = 200L
        const val PLAYBACK_MEMORY_SAVE_INTERVAL_MS = 5_000L
        const val MAX_LYRIC_OFFSET_MS = 3_000L
        const val LYRIC_OFFSET_SAVE_DEBOUNCE_MS = 250L
        const val PLAYBACK_FADE_STEPS = 12
        const val PLAYBACK_FADE_STEP_MS = 20L
        const val PLAYBACK_FADE_MIN_VOLUME = 0.001f
        const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
        const val MAX_LISTENING_STEP_MS = 1_500L
        const val LISTENING_FLUSH_MS = 5_000L
        const val ARTWORK_LOAD_CONCURRENCY = 3
        /** 封面解码结果攒多久一起提交（毫秒）：太短会频繁重组，太长会觉得封面出来慢。 */
        const val ARTWORK_FLUSH_INTERVAL_MS = 120L
        const val ARTWORK_THUMBNAIL_SIZE_PX = 200
        const val LIBRARY_ARTWORK_CACHE_SIZE = 64
    }
}
