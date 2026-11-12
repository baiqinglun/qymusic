package com.qymusic.player.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.spring
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.qymusic.player.data.CLIP_TYPES
import com.qymusic.player.data.CoverStyle
import com.qymusic.player.data.TrackComment
import com.qymusic.player.data.musicClipDisplayName
import com.qymusic.player.data.stripClipRangeSuffix
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.State
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.qymusic.player.R
import com.qymusic.player.playback.VocalSplitMode
import com.qymusic.player.playback.RotationUiState
import com.qymusic.player.data.AppSettings
import com.qymusic.player.data.LyricAlignment
import com.qymusic.player.data.LyricWordAnimationStyle
import com.qymusic.player.data.PlayerQuickAction
import com.qymusic.player.data.SettingsStore
import com.qymusic.player.data.ThemeMode
import com.qymusic.player.data.Track
import com.qymusic.player.data.UserPlaylist
import com.qymusic.player.lyrics.LyricLine
import com.qymusic.player.lyrics.LrcParser
import com.qymusic.player.lyrics.Lyrics
import com.qymusic.player.playback.EqualizerUiState
import com.qymusic.player.playback.KaraokeRecordingPhase
import com.qymusic.player.playback.KaraokeRecordingState
import com.qymusic.player.playback.KaraokePlayerState
import com.qymusic.player.playback.KaraokePreviewPlaybackState
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.KaraokePublishState
import com.qymusic.player.playback.MAX_PITCH_SEMITONES
import com.qymusic.player.playback.MAX_PLAYBACK_SPEED
import com.qymusic.player.playback.MIN_PITCH_SEMITONES
import com.qymusic.player.playback.MIN_PLAYBACK_SPEED
import com.qymusic.player.playback.PlaybackMode
import com.qymusic.player.playback.ReverbPreset
import com.qymusic.player.playback.semitonesOfPitchFactor
import com.qymusic.player.ui.theme.Night
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    playerState: PlayerUiState,
    currentTrackTitle: String,
    musicClips: List<com.qymusic.player.data.MusicClip>,
    comments: List<TrackComment>,
    onAddComment: (String, String?, Long?) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onPlayMusicClip: (com.qymusic.player.data.MusicClip) -> Unit,
    /** 定义片段确定后保存片段（trackId, type, tag, startMs, endMs）。 */
    onAddClip: (String, String, String, Long, Long) -> Unit,
    /** 退出片段播放：把当前项换回完整曲目并从指定位置继续播。 */
    onExitClipPlayback: (Long) -> Unit,
    /** 当前媒体项对应的播放中片段；用于进度条高亮片段区间与拖出提示。 */
    activeClip: com.qymusic.player.data.MusicClip?,
    lyricsState: LyricsUiState,
    lyricOffsetMs: Long,
    settings: AppSettings,
    playlists: List<UserPlaylist>,
    equalizerState: EqualizerUiState,
    sleepTimer: SleepTimerState,
    artwork: Bitmap?,
    karaokeRecordingState: KaraokeRecordingState,
    karaokePlayerState: KaraokePlayerState,
    karaokePreviewPlaybackState: KaraokePreviewPlaybackState,
    karaokeDraft: KaraokeEditProject?,
    karaokePublishState: KaraokePublishState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onSetTrackInPlaylist: (Long, String, Boolean) -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerPresetChange: (Int) -> Unit,
    onEqualizerBandChange: (Int, Int) -> Unit,
    onSeek: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onLoudnessGainChange: (Int) -> Unit,
    onReverbPresetChange: (ReverbPreset) -> Unit,
    onReverbLevelChange: (Int) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onStartSleepTimerAtTrackEnd: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onStartKaraokeRecording: () -> Unit,
    onFinishKaraokeRecording: () -> Unit,
    onDiscardKaraokeRecording: () -> Unit,
    onStartKaraokePlayback: (Track) -> Unit,
    onStopKaraokePlayback: () -> Unit,
    onToggleKaraokePlayPause: () -> Unit,
    onSeekKaraoke: (Long) -> Unit,
    onKaraokeVocalSplitModeChange: (VocalSplitMode) -> Unit,
    onKaraokePitchSemitonesChange: (Float) -> Unit,
    onKaraokeSpeedChange: (Float) -> Unit,
    onKaraokeDraftChange: (KaraokeEditProject) -> Unit,
    onSaveKaraokeDraft: (KaraokeEditProject) -> Unit,
    onDiscardKaraokeDraft: () -> Unit,
    onStartKaraokePreview: (KaraokeEditProject) -> Unit,
    onPauseKaraokePreview: () -> Unit,
    onSeekKaraokePreview: (KaraokeEditProject, Long) -> Unit,
    onUpdateKaraokePreview: (KaraokeEditProject, Boolean) -> Unit,
    onPublishKaraoke: (KaraokeEditProject) -> Unit,
    onPlayerQuickActionsChange: (List<PlayerQuickAction>) -> Unit,
    onCoverStyleChange: (CoverStyle) -> Unit,
    onMusicReactiveBackgroundChange: (Boolean) -> Unit,
    onLyricAlignmentChange: (LyricAlignment) -> Unit,
    onLyricFontScaleChange: (Float) -> Unit,
    onLyricBoldChange: (Boolean) -> Unit,
    onLyricInactiveBlurChange: (Float) -> Unit,
    onLyricOffsetChange: (Long) -> Unit,
    onLyricCenterStartEndChange: (Boolean) -> Unit,
    onLyricWordAnimationStyleChange: (LyricWordAnimationStyle) -> Unit,
    vocalSplitMode: VocalSplitMode,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    audioEffectStatus: String,
    audioSinkStatus: String,
    rotationState: RotationUiState,
    onRotationEnabledChange: (Boolean) -> Unit,
    onRotationSpeedChange: (Float) -> Unit,
    onRotationClockwiseChange: (Boolean) -> Unit,
) {
    val track = playerState.currentTrack ?: return
    val context = LocalContext.current
    val view = LocalView.current
    // 播放页整屏是深色封面背景：状态栏 / 导航栏图标用白色，退出后按主题恢复。
    DisposableEffect(view, settings.themeMode) {
        val window = context.findActivity()?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(window, view) }
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        onDispose {
            val lightTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> (view.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
            }
            insetsController?.isAppearanceLightStatusBars = lightTheme
            insetsController?.isAppearanceLightNavigationBars = lightTheme
        }
    }
    val displayArtwork = remember(artwork, track.id) {
        artwork ?: createProceduralCover(track.id.hashCode())
    }
    val blurredCover = remember(displayArtwork) {
        displayArtwork.createBlurredCover()
    }
    val backgroundMotion = rememberMusicBackgroundMotion(
        enabled = settings.musicReactiveBackground && playerState.isPlaying,
        positionMs = playerState.positionMs,
    )
    val pagerState = rememberPagerState(
        initialPage = INITIAL_PAGER_PAGE,
        pageCount = { PAGER_PAGE_COUNT },
    )
    var karaokeOpen by rememberSaveable { mutableStateOf(false) }
    var karaokePreviewOpen by rememberSaveable { mutableStateOf(false) }
    var landscapeMode by rememberSaveable { mutableStateOf(false) }
    // 评论页：点击评论入口跳转的全屏页面，支持新增与回复。
    var showCommentPage by rememberSaveable { mutableStateOf(false) }
    // 更多菜单：查看歌曲信息弹窗；分享走系统分享面板。
    var showTrackInfo by rememberSaveable { mutableStateOf(false) }
    val onShareCurrentTrack: () -> Unit = {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = currentTrackMimeType(track)
            putExtra(Intent.EXTRA_STREAM, track.uri)
            clipData = ClipData.newRawUri("audio", track.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    intent,
                    context.getString(R.string.player_share_track),
                ),
            )
        }
    }
    // 片段播放时跳到片段范围之外（拖进度条 / 点歌词）：先记下目标位置，弹窗确认后退出片段播放。
    var pendingClipExitSeekMs by remember { mutableStateOf<Long?>(null) }

    // 定义片段：起始默认为点击时刻、向后 30s；定义过程中歌曲正常播放。
    var defineClipActive by remember { mutableStateOf(false) }
    var defineClipStartMs by remember { mutableStateOf(0L) }
    var defineClipEndMs by remember { mutableStateOf(0L) }
    var showDefineClipDialog by remember { mutableStateOf(false) }
    val trackDurationMs = playerState.durationMs
    val startDefineClip: () -> Unit = {
        // 片段模式下定义片段：自动切回完整曲目播放（保持在当前位置）
        if (activeClip != null) {
            onExitClipPlayback(playerState.positionMs)
        }
        val start = playerState.positionMs
            .coerceIn(0L, (trackDurationMs - 100L).coerceAtLeast(0L))
        defineClipStartMs = start
        defineClipEndMs = (start + DEFINE_CLIP_DEFAULT_SPAN_MS)
            .coerceAtMost(trackDurationMs)
            .coerceAtLeast(start + 100L)
        defineClipActive = true
    }
    var karaokePermissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val karaokePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        karaokePermissionGranted = granted
        if (granted && karaokeOpen && !karaokePreviewOpen) {
            onStartKaraokeRecording()
        }
    }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = karaokeOpen) {
        if (karaokePreviewOpen) {
            onPauseKaraokePreview()
            onDiscardKaraokeRecording()
            onStopKaraokePlayback()
            karaokePreviewOpen = false
            karaokeOpen = false
        } else {
            onDiscardKaraokeRecording()
            onStopKaraokePlayback()
            karaokeOpen = false
        }
    }

    // 横屏歌词跟随设备方向：转到横屏直接进入沉浸横屏模式，转回竖屏自动退出。
    // 不锁定 Activity 方向——锁成 SENSOR_LANDSCAPE 后物理转回竖屏页面不会跟着转，
    // 自动退出就无从谈起。
    val hostActivity = remember(context) { context.findActivity() }
    val deviceLandscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(deviceLandscape) {
        landscapeMode = deviceLandscape
    }
    // 横屏沉浸模式：隐藏状态栏 / 导航栏（下滑临时呼出），退回竖屏时恢复显示。
    DisposableEffect(landscapeMode, hostActivity) {
        val controller = hostActivity?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }
        if (landscapeMode) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    BackHandler(enabled = landscapeMode) { landscapeMode = false }
    BackHandler(enabled = showCommentPage) { showCommentPage = false }

    LaunchedEffect(karaokeOpen, karaokePreviewOpen) {
        if (!karaokeOpen || karaokePreviewOpen) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        karaokePermissionGranted = granted
        if (!granted) {
            karaokePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (karaokeRecordingState.phase == KaraokeRecordingPhase.IDLE) {
            onStartKaraokeRecording()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (karaokeOpen && karaokePreviewOpen) {
            KaraokeEditorScreen(
                recordingState = karaokeRecordingState,
                draft = karaokeDraft,
                playbackState = karaokePreviewPlaybackState,
                publishState = karaokePublishState,
                trackTitle = track.title,
                artist = track.artist,
                artwork = displayArtwork,
                blurredCover = blurredCover,
                defaultLyrics = (lyricsState as? LyricsUiState.Ready)
                    ?.lyrics
                    ?.rawText
                    .orEmpty(),
                onRerecord = {
                    onPauseKaraokePreview()
                    onDiscardKaraokeRecording()
                    onDiscardKaraokeDraft()
                    onSeekKaraoke(0L)
                    if (!karaokePlayerState.showPauseIcon) {
                        onToggleKaraokePlayPause()
                    }
                    karaokePreviewOpen = false
                },
                onDelete = {
                    onPauseKaraokePreview()
                    onDiscardKaraokeDraft()
                    onDiscardKaraokeRecording()
                    onStopKaraokePlayback()
                    karaokePreviewOpen = false
                    karaokeOpen = false
                },
                onDraftChange = onKaraokeDraftChange,
                onUpdatePreview = onUpdateKaraokePreview,
                onTogglePreviewPlayback = { project ->
                    if (karaokePreviewPlaybackState.isPlaying) {
                        onPauseKaraokePreview()
                    } else {
                        onStartKaraokePreview(project)
                    }
                },
                onSeekPreview = onSeekKaraokePreview,
                onSaveDraft = onSaveKaraokeDraft,
                onPublish = onPublishKaraoke,
            )
        } else if (karaokeOpen) {
            KaraokeScreen(
                playerState = karaokePlayerState,
                lyricsState = lyricsState,
                settings = settings,
                track = track,
                artwork = displayArtwork,
                blurredCover = blurredCover,
                vocalSplitMode = karaokePlayerState.vocalSplitMode,
                recordingState = karaokeRecordingState,
                recordingPermissionGranted = karaokePermissionGranted,
                onRequestRecordingPermission = {
                    karaokePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onBack = {
                    onDiscardKaraokeRecording()
                    onStopKaraokePlayback()
                    karaokeOpen = false
                },
                onTogglePlayPause = onToggleKaraokePlayPause,
                onSeek = onSeekKaraoke,
                onVocalSplitModeChange = onKaraokeVocalSplitModeChange,
                onPitchSemitonesChange = onKaraokePitchSemitonesChange,
                onPlaybackSpeedChange = onKaraokeSpeedChange,
                onFinishRecording = {
                    if (karaokePlayerState.showPauseIcon) {
                        onToggleKaraokePlayPause()
                    }
                    onFinishKaraokeRecording()
                    karaokePreviewOpen = true
                },
            )
        } else {
            HorizontalPager(
                state = pagerState,
                // 相邻那一页提前组合好，左右滑过去时不会先空一帧。
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (page % 2 == 0) {
                    if (pagerState.targetPage == page) {
                        LyricsPage(
                            lyricsState = lyricsState,
                            positionMs = playerState.positionMs,
                            blurredCover = blurredCover,
                            backgroundMotion = backgroundMotion,
                            backgroundMotionEnabled = settings.musicReactiveBackground,
                            settings = settings,
                            // 只有当前显示的歌词页才做逐字补间，隐藏页只按进度对齐。
                            animate = pagerState.currentPage == page,
                            onPlayFrom = { positionMs ->
                                // 片段播放时点到片段范围之外的歌词行：先弹窗确认，不直接跳。
                                val clip = activeClip
                                if (clip != null &&
                                    (positionMs < clip.startMs || positionMs > clip.endMs)
                                ) {
                                    pendingClipExitSeekMs = positionMs
                                } else {
                                    onSeek(positionMs)
                                    if (!playerState.showPauseIcon) {
                                        onTogglePlayPause()
                                    }
                                }
                            },
                        )
                    } else {
                        // 还没滑到歌词页时先不构建歌词内容：大字号加粗的整段排版很贵，
                        // 放在这里会拖慢进入播放页的转场。
                        // 但背景要先铺上：空页是透明的，拖动切页时会透出下面的主界面。
                        CoverBackdrop(
                            bitmap = blurredCover,
                            musicMotion = backgroundMotion,
                            musicMotionEnabled = settings.musicReactiveBackground,
                        )
                    }
                } else {
                    PlaybackPage(
                        playerState = playerState,
                        currentTrackTitle = currentTrackTitle,
                        musicClips = musicClips,
                        onOpenComments = { showCommentPage = true },
                        onPlayMusicClip = onPlayMusicClip,
                        activeClip = activeClip,
                        defineClipActive = defineClipActive,
                        onStartDefineClip = startDefineClip,
                        lyricOffsetMs = lyricOffsetMs,
                        artwork = displayArtwork,
                        blurredCover = blurredCover,
                        backgroundMotion = backgroundMotion,
                        backgroundMotionEnabled = settings.musicReactiveBackground,
                        track = track,
                        settings = settings,
                        playlists = playlists,
                        equalizerState = equalizerState,
                        sleepTimer = sleepTimer,
                        onBack = onBack,
                        onTogglePlayPause = onTogglePlayPause,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onCyclePlaybackMode = onCyclePlaybackMode,
                        onShowTrackInfo = { showTrackInfo = true },
                        onShareTrack = onShareCurrentTrack,
                        onPlayerQuickActionsChange = onPlayerQuickActionsChange,
                        onCoverStyleChange = onCoverStyleChange,
                        onCoverClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    nearestLyricsPage(pagerState.currentPage),
                                )
                            }
                        },
                        onOpenKaraoke = {
                            onDiscardKaraokeRecording()
                            onStartKaraokePlayback(track)
                            karaokePreviewOpen = false
                            karaokeOpen = true
                        },
                        onSelectQueueIndex = onSelectQueueIndex,
                        onSetTrackInPlaylist = onSetTrackInPlaylist,
                        onCreatePlaylist = onCreatePlaylist,
                        onEqualizerEnabledChange = onEqualizerEnabledChange,
                        onEqualizerPresetChange = onEqualizerPresetChange,
                        onEqualizerBandChange = onEqualizerBandChange,
                        onSeek = onSeek,
                        onSeekOutsideClip = { pendingClipExitSeekMs = it },
                        onOpenArtist = onOpenArtist,
                        onOpenAlbum = onOpenAlbum,
                        onMoveQueueItem = onMoveQueueItem,
                        onBassStrengthChange = onBassStrengthChange,
                        onVirtualizerStrengthChange = onVirtualizerStrengthChange,
                        onLoudnessGainChange = onLoudnessGainChange,
                        onReverbPresetChange = onReverbPresetChange,
                        onReverbLevelChange = onReverbLevelChange,
                        onStartSleepTimer = onStartSleepTimer,
                        onStartSleepTimerAtTrackEnd = onStartSleepTimerAtTrackEnd,
                        onCancelSleepTimer = onCancelSleepTimer,
                        onPlaybackSpeedChange = onPlaybackSpeedChange,
                        onPitchSemitonesChange = onPitchSemitonesChange,
                        onMusicReactiveBackgroundChange = onMusicReactiveBackgroundChange,
                        onLyricAlignmentChange = onLyricAlignmentChange,
                        onLyricFontScaleChange = onLyricFontScaleChange,
                        onLyricBoldChange = onLyricBoldChange,
                        onLyricInactiveBlurChange = onLyricInactiveBlurChange,
                        onLyricOffsetChange = onLyricOffsetChange,
                        onLyricCenterStartEndChange = onLyricCenterStartEndChange,
                        onLyricWordAnimationStyleChange = onLyricWordAnimationStyleChange,
                        vocalSplitMode = vocalSplitMode,
                        onVocalSplitModeChange = onVocalSplitModeChange,
                        audioEffectStatus = audioEffectStatus,
                        audioSinkStatus = audioSinkStatus,
                        rotationState = rotationState,
                        onRotationEnabledChange = onRotationEnabledChange,
                        onRotationSpeedChange = onRotationSpeedChange,
                        onRotationClockwiseChange = onRotationClockwiseChange,
                    )
                }
            }
        }
        if (landscapeMode) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 盖住下层播放页：歌词面板之外的触摸都在这一层被吃掉，避免误触分页。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                )
                LandscapeLyricsPlayer(
                    artwork = displayArtwork,
                    lyricsState = lyricsState,
                    positionMs = playerState.positionMs,
                    settings = settings,
                    blurredCover = blurredCover,
                    backgroundMotion = backgroundMotion,
                    backgroundMotionEnabled = settings.musicReactiveBackground,
                    onExit = { landscapeMode = false },
                    onPlayFrom = { seekMs ->
                        // 横屏歌词同样受片段范围约束：范围外先弹窗确认。
                        val clip = activeClip
                        if (clip != null && (seekMs < clip.startMs || seekMs > clip.endMs)) {
                            pendingClipExitSeekMs = seekMs
                        } else {
                            onSeek(seekMs)
                            if (!playerState.showPauseIcon) {
                                onTogglePlayPause()
                            }
                        }
                    },
                )
            }
        }

        // 歌曲评论页：全屏覆盖播放页，卡片式评论列表 + 新增 / 回复。
        if (showCommentPage) {
            TrackCommentPage(
                comments = comments,
                blurredCover = blurredCover,
                onBack = { showCommentPage = false },
                onAdd = { text, emoji, parentId ->
                    onAddComment(text, emoji, parentId)
                },
                onDelete = onDeleteComment,
            )
        }

        // 更多菜单：查看歌曲信息
        if (showTrackInfo) {
            TrackPropertiesDialog(
                track = track,
                onDismiss = { showTrackInfo = false },
            )
        }

        // 定义片段悬浮按钮：只在播放页 / 歌词页（分页两层）显示，歌曲继续正常播放。
        if (defineClipActive && !karaokeOpen && !landscapeMode) {
            DefineClipOverlay(
                modifier = Modifier.align(Alignment.CenterEnd),
                startMs = defineClipStartMs,
                endMs = defineClipEndMs,
                onSetStart = {
                    defineClipStartMs = playerState.positionMs
                        .coerceIn(0L, (defineClipEndMs - 100L).coerceAtLeast(0L))
                },
                onSetEnd = {
                    defineClipEndMs = playerState.positionMs
                        .coerceAtLeast(defineClipStartMs + 100L)
                        .coerceAtMost(trackDurationMs)
                },
                onCancel = { defineClipActive = false },
                onConfirm = { showDefineClipDialog = true },
            )
        }

        // 定义片段确认：设置类型与标签后保存
        if (showDefineClipDialog) {
            DefineClipDialog(
                startMs = defineClipStartMs,
                endMs = defineClipEndMs,
                onDismiss = { showDefineClipDialog = false },
                onConfirm = { type, tag ->
                    showDefineClipDialog = false
                    defineClipActive = false
                    onAddClip(track.id, type, tag, defineClipStartMs, defineClipEndMs)
                    Toast.makeText(context, R.string.define_clip_success, Toast.LENGTH_SHORT)
                        .show()
                },
            )
        }

        // 跳转目标在片段范围之外（拖进度条 / 点歌词 / 横屏歌词）：
        // 确认后退出片段播放并跳转，取消则留在片段里。
        pendingClipExitSeekMs?.let { targetMs ->
            AlertDialog(
                onDismissRequest = { pendingClipExitSeekMs = null },
                title = { Text(text = stringResource(R.string.clip_exit_prompt_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.clip_exit_prompt_message,
                            formatDuration(targetMs),
                            activeClip?.let { formatDuration(it.startMs) }.orEmpty(),
                            activeClip?.let { formatDuration(it.endMs) }.orEmpty(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingClipExitSeekMs = null
                            onSeek(targetMs)
                        },
                    ) {
                        Text(text = stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingClipExitSeekMs = null }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackPage(
    playerState: PlayerUiState,
    currentTrackTitle: String,
    musicClips: List<com.qymusic.player.data.MusicClip>,
    onOpenComments: () -> Unit,
    onPlayMusicClip: (com.qymusic.player.data.MusicClip) -> Unit,
    activeClip: com.qymusic.player.data.MusicClip?,
    defineClipActive: Boolean,
    onStartDefineClip: () -> Unit,
    lyricOffsetMs: Long,
    artwork: Bitmap?,
    blurredCover: Bitmap,
    backgroundMotion: Float,
    backgroundMotionEnabled: Boolean,
    track: Track,
    settings: AppSettings,
    playlists: List<UserPlaylist>,
    equalizerState: EqualizerUiState,
    sleepTimer: SleepTimerState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onShowTrackInfo: () -> Unit,
    onShareTrack: () -> Unit,
    onPlayerQuickActionsChange: (List<PlayerQuickAction>) -> Unit,
    onCoverStyleChange: (CoverStyle) -> Unit,
    onCoverClick: () -> Unit,
    onOpenKaraoke: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onSetTrackInPlaylist: (Long, String, Boolean) -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerPresetChange: (Int) -> Unit,
    onEqualizerBandChange: (Int, Int) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekOutsideClip: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onLoudnessGainChange: (Int) -> Unit,
    onReverbPresetChange: (ReverbPreset) -> Unit,
    onReverbLevelChange: (Int) -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onStartSleepTimerAtTrackEnd: () -> Unit,
    onCancelSleepTimer: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onMusicReactiveBackgroundChange: (Boolean) -> Unit,
    onLyricAlignmentChange: (LyricAlignment) -> Unit,
    onLyricFontScaleChange: (Float) -> Unit,
    onLyricBoldChange: (Boolean) -> Unit,
    onLyricInactiveBlurChange: (Float) -> Unit,
    onLyricOffsetChange: (Long) -> Unit,
    onLyricCenterStartEndChange: (Boolean) -> Unit,
    onLyricWordAnimationStyleChange: (LyricWordAnimationStyle) -> Unit,
    vocalSplitMode: VocalSplitMode,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    audioEffectStatus: String,
    audioSinkStatus: String,
    rotationState: RotationUiState,
    onRotationEnabledChange: (Boolean) -> Unit,
    onRotationSpeedChange: (Float) -> Unit,
    onRotationClockwiseChange: (Boolean) -> Unit,
) {
    var showPlaylist by remember(track.id) { mutableStateOf(false) }
    var showAddToPlaylist by remember(track.id) { mutableStateOf(false) }
    var showCreatePlaylist by remember(track.id) { mutableStateOf(false) }
    var showEqualizer by remember(track.id) { mutableStateOf(false) }
    var showSleepTimerSheet by remember(track.id) { mutableStateOf(false) }
    var showMagicSheet by remember(track.id) { mutableStateOf(false) }
    var showLyricSettingsSheet by remember(track.id) { mutableStateOf(false) }
    var showBackgroundSettingsSheet by remember(track.id) { mutableStateOf(false) }
    var showPlayerActionsSheet by remember(track.id) { mutableStateOf(false) }
    var showCoverStyleSheet by remember(track.id) { mutableStateOf(false) }
    var showClipPicker by remember(track.id) { mutableStateOf(false) }
    // 4 + 1：排序前 4 个功能直接显示为按钮，全部功能都在「更多」网格弹层里
    val allOrderedActions = remember(settings.playerQuickActions) {
        settings.playerQuickActions +
            PlayerQuickAction.entries.filterNot { it in settings.playerQuickActions }
    }
    val rowActions = remember(allOrderedActions) { allOrderedActions.take(4) }
    val onPlayerAction: (PlayerQuickAction) -> Unit = { action ->
        when (action) {
            PlayerQuickAction.LYRIC_SETTINGS -> showLyricSettingsSheet = true
            PlayerQuickAction.BACKGROUND_SETTINGS -> showBackgroundSettingsSheet = true
            PlayerQuickAction.SLEEP_TIMER -> showSleepTimerSheet = true
            PlayerQuickAction.MAGIC -> showMagicSheet = true
            PlayerQuickAction.CLIPS -> showClipPicker = true
            PlayerQuickAction.DEFINE_CLIP -> onStartDefineClip()
            PlayerQuickAction.PLAYBACK_MODE -> onCyclePlaybackMode()
            PlayerQuickAction.QUEUE -> showPlaylist = true
            PlayerQuickAction.ADD_TO_PLAYLIST -> showAddToPlaylist = true
            PlayerQuickAction.EQUALIZER -> showEqualizer = true
            PlayerQuickAction.KARAOKE -> onOpenKaraoke()
            PlayerQuickAction.COVER_STYLE -> showCoverStyleSheet = true
            PlayerQuickAction.COMMENT -> onOpenComments()
        }
    }
    var playlistName by remember(track.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val baseScheme = MaterialTheme.colorScheme
    val playbackScheme = baseScheme.copy(
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.78f),
        outline = Color.White.copy(alpha = 0.30f),
    )

    MaterialTheme(colorScheme = playbackScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 切歌时背景跟着新封面渐变，避免整屏硬切。
            OpaqueCoverCrossfade(
                blurredCover = blurredCover,
                backgroundMotion = backgroundMotion,
                backgroundMotionEnabled = backgroundMotionEnabled,
                label = "playback-backdrop",
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                PlaybackTopBar(
                    onBack = onBack,
                    onShowTrackInfo = onShowTrackInfo,
                    onShareTrack = onShareTrack,
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    val landscape = availableWidth > availableHeight

                    if (landscape) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            val coverSize = minOf(
                                availableWidth * 0.46f,
                                availableHeight,
                                430.dp,
                            ).coerceAtLeast(180.dp)
                            Column(
                                modifier = Modifier
                                    .width(coverSize)
                                    .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Crossfade(
                                    targetState = artwork,
                                    animationSpec = tween(TRACK_CHANGE_FADE_MS),
                                    label = "playback-cover-land",
                                ) { bitmap ->
                                    ClickableCover(
                                        bitmap = bitmap,
                                        size = coverSize,
                                        style = settings.coverStyle,
                                        isPlaying = playerState.showPauseIcon,
                                        onClick = onCoverClick,
                                        onStyleSelect = onCoverStyleChange,
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 72.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Crossfade(
                                        targetState = track,
                                        animationSpec = tween(TRACK_CHANGE_FADE_MS),
                                        label = "playback-track-info-land",
                                    ) { target ->
                                        TrackIdentity(
                                            track = target,
                                            onOpenArtist = onOpenArtist,
                                            onOpenAlbum = onOpenAlbum,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ProgressControls(
                                        playerState = playerState,
                                        track = track,
                                        activeClip = activeClip,
                                        onSeek = onSeek,
                                        onSeekOutsideClip = onSeekOutsideClip,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PrimaryControls(
                                        playerState = playerState,
                                        onTogglePlayPause = onTogglePlayPause,
                                        onPrevious = onPrevious,
                                        onNext = onNext,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    SecondaryControls(
                                        playerState = playerState,
                                        actions = rowActions,
                                        onAction = onPlayerAction,
                                        onOpenMore = { showPlayerActionsSheet = true },
                                        playlistActive = showPlaylist,
                                        addToPlaylistActive = showAddToPlaylist,
                                        equalizerActive = showEqualizer,
                                        defineClipActive = defineClipActive,
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val coverSize = minOf(
                                availableWidth * 0.90f,
                                availableHeight * 0.44f,
                                420.dp,
                            ).coerceAtLeast(180.dp)
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                // 封面跟着歌曲一起渐变
                                Crossfade(
                                    targetState = artwork,
                                    animationSpec = tween(TRACK_CHANGE_FADE_MS),
                                    label = "playback-cover",
                                ) { bitmap ->
                                    ClickableCover(
                                        bitmap = bitmap,
                                        size = coverSize,
                                        style = settings.coverStyle,
                                        isPlaying = playerState.showPauseIcon,
                                        onClick = onCoverClick,
                                        onStyleSelect = onCoverStyleChange,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Crossfade(
                                    targetState = track,
                                    animationSpec = tween(TRACK_CHANGE_FADE_MS),
                                    label = "playback-track-info",
                                ) { target ->
                                    TrackIdentity(
                                        track = target,
                                        onOpenArtist = onOpenArtist,
                                        onOpenAlbum = onOpenAlbum,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ProgressControls(
                                    playerState = playerState,
                                    track = track,
                                    activeClip = activeClip,
                                    onSeek = onSeek,
                                    onSeekOutsideClip = onSeekOutsideClip,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                PrimaryControls(
                                    playerState = playerState,
                                    onTogglePlayPause = onTogglePlayPause,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                SecondaryControls(
                                    playerState = playerState,
                                    actions = rowActions,
                                    onAction = onPlayerAction,
                                    onOpenMore = { showPlayerActionsSheet = true },
                                    playlistActive = showPlaylist,
                                    addToPlaylistActive = showAddToPlaylist,
                                    equalizerActive = showEqualizer,
                                    defineClipActive = defineClipActive,
                                )
                            }
                        }
                    }
                }
            }
    if (showPlaylist) {
        val closeSheet = LocalSheetClose.current
        PlaybackBottomSheet(
            onDismiss = { showPlaylist = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.playlist),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                            )
                            Text(
                                text = stringResource(
                                    R.string.track_count,
                                    playerState.queue.size,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.72f),
                            )
                        }
                        PlaylistDialogContent(
                            queue = playerState.queue,
                            currentTrackId = track.id,
                            onSelectQueueIndex = { index ->
                                onSelectQueueIndex(index)
                                closeSheet()
                            },
                            onMoveQueueItem = onMoveQueueItem,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            playlists = playlists,
            trackId = track.id,
            onDismiss = { showAddToPlaylist = false },
            onTogglePlaylist = { playlistId, included ->
                onSetTrackInPlaylist(playlistId, track.id, included)
            },
            onCreatePlaylist = {
                showAddToPlaylist = false
                playlistName = ""
                showCreatePlaylist = true
            },
            blurredCover = blurredCover,
        )
    }

    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(text = stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = playlistName.isNotBlank(),
                    onClick = {
                        onCreatePlaylist(playlistName, track.id)
                        showCreatePlaylist = false
                    },
                ) {
                    Text(text = stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showEqualizer) {
        // 均衡器底部弹窗：背景沿用播放页的封面高斯模糊，视觉上连成一体。
        PlaybackBottomSheet(
            onDismiss = { showEqualizer = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.equalizer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        EqualizerControls(
                            state = equalizerState,
                            onEnabledChange = onEqualizerEnabledChange,
                            onPresetChange = onEqualizerPresetChange,
                            onBandChange = onEqualizerBandChange,
                            onBassStrengthChange = onBassStrengthChange,
                            onVirtualizerStrengthChange = onVirtualizerStrengthChange,
                            onLoudnessGainChange = onLoudnessGainChange,
                            onReverbPresetChange = onReverbPresetChange,
                            onReverbLevelChange = onReverbLevelChange,
                        )
                    }
            }
        }
    }

    if (showSleepTimerSheet) {
        PlaybackBottomSheet(
            onDismiss = { showSleepTimerSheet = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.sleep_timer_title),
        ) {
            val selectedMinutes = sleepTimerSelectedMinutes(sleepTimer)
            if (sleepTimer.active) {
                Text(
                    text = when (sleepTimer.mode) {
                        SleepTimerMode.TIMER -> stringResource(
                            R.string.sleep_timer_remaining,
                            formatDuration(sleepTimer.remainingMs),
                        )

                        SleepTimerMode.END_OF_TRACK ->
                            stringResource(R.string.sleep_timer_end_of_track)

                        SleepTimerMode.OFF -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            SLEEP_TIMER_MINUTES.forEach { minutes ->
                SheetOptionRow(
                    label = stringResource(R.string.sleep_timer_minutes, minutes),
                    selected = selectedMinutes == minutes,
                    onClick = {
                        onStartSleepTimer(minutes)
                        showSleepTimerSheet = false
                    },
                )
            }
            SheetOptionRow(
                label = stringResource(R.string.sleep_timer_end_of_track),
                selected = sleepTimer.mode == SleepTimerMode.END_OF_TRACK,
                onClick = {
                    onStartSleepTimerAtTrackEnd()
                    showSleepTimerSheet = false
                },
            )
            if (sleepTimer.active) {
                SheetOptionRow(
                    label = stringResource(R.string.sleep_timer_off),
                    selected = false,
                    onClick = {
                        onCancelSleepTimer()
                        showSleepTimerSheet = false
                    },
                )
            }
        }
    }

    if (showLyricSettingsSheet) {
        PlaybackBottomSheet(
            onDismiss = { showLyricSettingsSheet = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.lyrics_settings),
            scrollable = true,
        ) {
            LyricSettingsSheetContent(
                settings = settings,
                lyricOffsetMs = lyricOffsetMs,
                onAlignmentChange = onLyricAlignmentChange,
                onFontScaleChange = onLyricFontScaleChange,
                onBoldChange = onLyricBoldChange,
                onInactiveBlurChange = onLyricInactiveBlurChange,
                onLyricOffsetChange = onLyricOffsetChange,
                onCenterStartEndChange = onLyricCenterStartEndChange,
                onWordAnimationStyleChange = onLyricWordAnimationStyleChange,
            )
        }
    }

    if (showBackgroundSettingsSheet) {
        PlaybackBottomSheet(
            onDismiss = { showBackgroundSettingsSheet = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.settings_background),
        ) {
            BackgroundSettingsSheetContent(
                musicReactiveBackground = settings.musicReactiveBackground,
                onMusicReactiveBackgroundChange = onMusicReactiveBackgroundChange,
            )
        }
    }

    if (showClipPicker) {
        val closeSheet = LocalSheetClose.current
        PlaybackBottomSheet(
            onDismiss = { showClipPicker = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.clips_pick_title),
        ) {
            if (musicClips.isEmpty()) {
                Text(
                    text = stringResource(R.string.clips_none_for_track),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                musicClips.forEach { clip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onPlayMusicClip(clip)
                                closeSheet()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCut,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = musicClipDisplayName(
                                    currentTrackTitle,
                                    clip.startMs, clip.endMs,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                            )
                            Text(
                                text = clip.tag + "  ·  " + clip.startMs / 1000 / 60 + ":" +
                                    "%02d".format(clip.startMs / 1000 % 60) + " - " +
                                    clip.endMs / 1000 / 60 + ":" +
                                    "%02d".format(clip.endMs / 1000 % 60),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.68f),
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (showCoverStyleSheet) {
        PlaybackBottomSheet(
            onDismiss = { showCoverStyleSheet = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.cover_style),
        ) {
            CoverStyleSheetContent(
                currentStyle = settings.coverStyle,
                onStyleChange = onCoverStyleChange,
            )
        }
    }

    if (showMagicSheet) {
        PlaybackBottomSheet(
            onDismiss = { showMagicSheet = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.magic),
        ) {
            MagicSheetContent(
                playerState = playerState,
                vocalSplitMode = vocalSplitMode,
                onVocalSplitModeChange = onVocalSplitModeChange,
                audioEffectStatus = audioEffectStatus,
                audioSinkStatus = audioSinkStatus,
                rotationState = rotationState,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onPitchSemitonesChange = onPitchSemitonesChange,
                onRotationEnabledChange = onRotationEnabledChange,
                onRotationSpeedChange = onRotationSpeedChange,
                onRotationClockwiseChange = onRotationClockwiseChange,
            )
        }
    }

    if (showPlayerActionsSheet) {
        PlaybackBottomSheet(
            onDismiss = { showPlayerActionsSheet = false },
            blurredCover = blurredCover,
            playbackScheme = playbackScheme,
            title = stringResource(R.string.player_actions_settings),
            scrollable = true,
        ) {
            PlayerActionsGridSheetContent(
                actions = allOrderedActions,
                onActionsChange = onPlayerQuickActionsChange,
                onActionClick = { action ->
                    showPlayerActionsSheet = false
                    onPlayerAction(action)
                },
            )
        }
    }
        }
    }
    }



private fun VocalSplitMode.labelRes(): Int = when (this) {
    VocalSplitMode.BOTH -> R.string.vocal_split_both
    VocalSplitMode.VOCALS -> R.string.vocal_split_vocals
    VocalSplitMode.INSTRUMENTAL -> R.string.vocal_split_instrumental
}

@Composable
internal fun AddToPlaylistDialog(
    playlists: List<UserPlaylist>,
    trackId: String,
    onDismiss: () -> Unit,
    onTogglePlaylist: (Long, Boolean) -> Unit,
    onCreatePlaylist: () -> Unit,
    blurredCover: Bitmap? = null,
) {
    if (blurredCover == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = stringResource(R.string.add_to_playlist)) },
            text = {
                PlaylistPickerList(
                    playlists = playlists,
                    trackId = trackId,
                    onTogglePlaylist = onTogglePlaylist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                )
            },
            confirmButton = {
                TextButton(onClick = onCreatePlaylist) {
                    Text(text = stringResource(R.string.create_playlist))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.close))
                }
            },
        )
        return
    }

    // 播放页里的“添加到歌单”使用和播放页一致的封面高斯模糊背景与底部弹窗。
val closeSheet = LocalSheetClose.current
    PlaybackBottomSheet(
        onDismiss = onDismiss,
        blurredCover = blurredCover,
        playbackScheme = MaterialTheme.colorScheme,
        title = stringResource(R.string.add_to_playlist),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
                PlaylistPickerList(
                    playlists = playlists,
                    trackId = trackId,
                    onTogglePlaylist = onTogglePlaylist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { closeSheet() },
                    ) {
                        Text(text = stringResource(R.string.close), color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            onCreatePlaylist()
                        },
                    ) {
                        Text(text = stringResource(R.string.create_playlist), color = Color.White)
                    }
                }
            }
        }
    }


@Composable
private fun PlaylistPickerList(
    playlists: List<UserPlaylist>,
    trackId: String,
    onTogglePlaylist: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playlists.isEmpty()) {
        Text(
            text = stringResource(R.string.no_playlists),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, playlist -> playlist.id },
        ) { index, playlist ->
            val checked = trackId in playlist.trackIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clickable(
                        role = Role.Checkbox,
                        onClick = { onTogglePlaylist(playlist.id, !checked) },
                    )
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = playlist.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.track_count, playlist.trackIds.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Checkbox(
                    checked = checked,
                    onCheckedChange = { included ->
                        onTogglePlaylist(playlist.id, included)
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        checkmarkColor = Night,
                        uncheckedColor = Color.White,
                    ),
                )
            }
            if (index < playlists.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun LyricsPage(
    lyricsState: LyricsUiState,
    positionMs: Long,
    blurredCover: Bitmap,
    backgroundMotion: Float,
    backgroundMotionEnabled: Boolean,
    settings: AppSettings,
    animate: Boolean,
    onPlayFrom: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 切歌时歌词页背景同样渐变
        OpaqueCoverCrossfade(
            blurredCover = blurredCover,
            backgroundMotion = backgroundMotion,
            backgroundMotionEnabled = backgroundMotionEnabled,
            label = "lyrics-backdrop",
        )
        LyricsPanel(
            lyricsState = lyricsState,
            positionMs = positionMs,
            settings = settings,
            animate = animate,
            onPlayFrom = onPlayFrom,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

/** 播放页顶栏：返回 + 更多菜单（查看歌曲信息 / 分享音乐）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackTopBar(
    onBack: () -> Unit,
    onShowTrackInfo: () -> Unit,
    onShareTrack: () -> Unit,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.22f)),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Box {
            IconButton(
                onClick = { moreMenuExpanded = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.22f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.player_more),
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                // 播放页是深色封面背景：菜单也用深色半透明面板 + 细白描边，
                // 避免亮色主题下弹出刺眼的白色菜单；文字色沿用页面的白色系。
                shape = RoundedCornerShape(16.dp),
                containerColor = PLAYER_MENU_BACKGROUND,
                tonalElevation = 0.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.player_track_info)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        moreMenuExpanded = false
                        onShowTrackInfo()
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.player_share_track)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        moreMenuExpanded = false
                        onShareTrack()
                    },
                )
            }
        }
    }
}

@Composable
private fun MagicSheetContent(
    playerState: PlayerUiState,
    vocalSplitMode: VocalSplitMode,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    audioEffectStatus: String,
    audioSinkStatus: String,
    rotationState: RotationUiState,
    onPlaybackSpeedChange: (Float) -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onRotationEnabledChange: (Boolean) -> Unit,
    onRotationSpeedChange: (Float) -> Unit,
    onRotationClockwiseChange: (Boolean) -> Unit,
) {
    val tabs = MagicTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pageHeight = if (maxHeight == Dp.Infinity) {
            MAGIC_PAGE_FALLBACK_HEIGHT
        } else {
            (maxHeight * 0.56f).coerceIn(
                MAGIC_PAGE_MIN_HEIGHT,
                MAGIC_PAGE_MAX_HEIGHT,
            )
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            MagicTabRow(
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pageHeight),
                beyondViewportPageCount = 1,
            ) { page ->
                when (tabs[page]) {
                    MagicTab.SPEED -> MagicPageContainer {
                        MagicSpeedPage(
                            playerState = playerState,
                            onPlaybackSpeedChange = onPlaybackSpeedChange,
                        )
                    }

                    MagicTab.PITCH -> MagicPageContainer {
                        MagicPitchPage(
                            playerState = playerState,
                            onPitchSemitonesChange = onPitchSemitonesChange,
                        )
                    }

                    MagicTab.VOCAL -> MagicPageContainer {
                        MagicVocalPage(
                            vocalSplitMode = vocalSplitMode,
                            onVocalSplitModeChange = onVocalSplitModeChange,
                            audioEffectStatus = audioEffectStatus,
                            audioSinkStatus = audioSinkStatus,
                        )
                    }

                    MagicTab.ROTATION -> MagicPageContainer {
                        MagicRotationPage(
                            rotationState = rotationState,
                            onRotationEnabledChange = onRotationEnabledChange,
                            onRotationSpeedChange = onRotationSpeedChange,
                            onRotationClockwiseChange = onRotationClockwiseChange,
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = Color.White.copy(alpha = 0.16f),
            )
            TextButton(
                onClick = {
                    onPlaybackSpeedChange(1f)
                    onPitchSemitonesChange(0f)
                    onVocalSplitModeChange(VocalSplitMode.BOTH)
                    onRotationEnabledChange(false)
                    onRotationSpeedChange(RotationUiState.DEFAULT_REVOLUTIONS_PER_SECOND)
                    onRotationClockwiseChange(true)
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(R.string.reset_all),
                    color = Color.White,
                )
            }
        }
    }
}

private enum class MagicTab(val labelRes: Int) {
    SPEED(R.string.playback_speed),
    PITCH(R.string.pitch_shift),
    VOCAL(R.string.vocal_split),
    ROTATION(R.string.rotation_effect),
}

@Composable
private fun MagicTabRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(4.dp),
    ) {
        MagicTab.entries.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                    )
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.66f)
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MagicPageContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 14.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun MagicSpeedPage(
    playerState: PlayerUiState,
    onPlaybackSpeedChange: (Float) -> Unit,
) {
    QyRowSlider(
        label = stringResource(R.string.playback_speed),
        value = playerState.speed,
        valueRange = MIN_PLAYBACK_SPEED..MAX_PLAYBACK_SPEED,
        valueFormatter = ::formatPlaybackSpeed,
        onValueChange = onPlaybackSpeedChange,
        neutralValue = 1f,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SPEED_PRESETS) { preset ->
            FilterChip(
                selected = abs(playerState.speed - preset) < 0.001f,
                onClick = { onPlaybackSpeedChange(preset) },
                label = { Text(text = formatPlaybackSpeed(preset)) },
            )
        }
    }
    MagicResetAction(onClick = { onPlaybackSpeedChange(1f) })
}

@Composable
private fun MagicPitchPage(
    playerState: PlayerUiState,
    onPitchSemitonesChange: (Float) -> Unit,
) {
    val semitones = semitonesOfPitchFactor(playerState.pitch)
    QyRowSlider(
        label = stringResource(R.string.pitch_shift),
        value = semitones,
        valueRange = MIN_PITCH_SEMITONES..MAX_PITCH_SEMITONES,
        valueFormatter = ::formatSemitones,
        onValueChange = { raw -> onPitchSemitonesChange(raw.roundToInt().toFloat()) },
        neutralValue = 0f,
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(PITCH_PRESETS) { preset ->
            FilterChip(
                selected = semitones.roundToInt() == preset.roundToInt(),
                onClick = { onPitchSemitonesChange(preset) },
                label = { Text(text = formatSemitones(preset)) },
            )
        }
    }
    MagicResetAction(onClick = { onPitchSemitonesChange(0f) })
}

@Composable
private fun MagicVocalPage(
    vocalSplitMode: VocalSplitMode,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    audioEffectStatus: String,
    audioSinkStatus: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VocalSplitMode.entries.forEach { option ->
            FilterChip(
                selected = option == vocalSplitMode,
                onClick = { onVocalSplitModeChange(option) },
                label = { Text(text = stringResource(option.labelRes())) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(
        text = stringResource(R.string.vocal_split_hint),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.72f),
    )
    Text(
        text = audioSinkStatus,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
    )
    Text(
        text = audioEffectStatus,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
    )
    MagicResetAction(onClick = { onVocalSplitModeChange(VocalSplitMode.BOTH) })
}

@Composable
private fun MagicRotationPage(
    rotationState: RotationUiState,
    onRotationEnabledChange: (Boolean) -> Unit,
    onRotationSpeedChange: (Float) -> Unit,
    onRotationClockwiseChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.rotation_enabled),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MorphSwitch(
            checked = rotationState.enabled,
            onCheckedChange = onRotationEnabledChange,
        )
    }
    QyRowSlider(
        label = stringResource(R.string.rotation_speed),
        value = rotationState.revolutionsPerSecond,
        valueRange = RotationUiState.MIN_REVOLUTIONS_PER_SECOND..
            RotationUiState.MAX_REVOLUTIONS_PER_SECOND,
        valueFormatter = { secondsPerTurn ->
            "%.1f".format(1f / secondsPerTurn.coerceAtLeast(0.01f))
        },
        onValueChange = onRotationSpeedChange,
        neutralValue = RotationUiState.DEFAULT_REVOLUTIONS_PER_SECOND,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(
            selected = rotationState.clockwise,
            onClick = { onRotationClockwiseChange(true) },
            label = { Text(text = stringResource(R.string.rotation_clockwise)) },
        )
        FilterChip(
            selected = !rotationState.clockwise,
            onClick = { onRotationClockwiseChange(false) },
            label = { Text(text = stringResource(R.string.rotation_counter_clockwise)) },
        )
    }
    MagicResetAction(
        onClick = {
            onRotationEnabledChange(false)
            onRotationSpeedChange(RotationUiState.DEFAULT_REVOLUTIONS_PER_SECOND)
            onRotationClockwiseChange(true)
        },
    )
}

@Composable
private fun MagicSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun MagicResetAction(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.reset_current),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.72f),
        modifier = Modifier
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 底部弹窗升起 / 下滑关闭共用：ease-out 曲线，约 250ms。 */
private val SheetEaseOut = tween<Float>(durationMillis = 250, easing = EaseOutCubic)
/** 弹窗内容自适应高度，但最高不超过屏高的这个比例，超高部分内部滚动。 */
private const val SHEET_MAX_HEIGHT_FRACTION = 0.85f
/** 下拉甩动关闭的速度阈值（px/s）。 */
private const val SHEET_FLING_CLOSE_DP = 350f
/** progress 安全上限（正常拖动只降不升，防嵌套滚动边界把进度顶过 1）。 */
private const val SHEET_MAX_PROGRESS = 1.15f

/** 弹窗内容里主动请求关闭（弹窗内“关闭 / 选择歌曲”等按钮用），带完整下滑动画。 */
val LocalSheetClose = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 统一的播放页底部弹窗：从屏幕底部滑入就位，内容自适应高度（超高部分内部滚动）。
 * 手指下拉 1:1 跟手：超过弹窗高度 1/3 或快速一甩即关闭，否则弹回原位；
 * 可滚动内容滚到顶后继续下拉，把位移转给弹窗整体。
 * 背板压暗随进度走，点击空白处关闭。
 */
@Composable
private fun PlaybackBottomSheet(
    onDismiss: () -> Unit,
    blurredCover: Bitmap,
    playbackScheme: ColorScheme,
    title: String? = null,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    // 0 = 完全在屏下，1 = 就位。拖动要在手势回调里非挂起写值，
    // 所以进度本体是 FloatState，动画用 Animatable 桥接、逐帧写回。
    val progress = remember { mutableFloatStateOf(0f) }
    val progressAnim = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }

    // 升起：从屏下 ease-out 滑到就位（约 250ms）。
    LaunchedEffect(Unit) {
        progressAnim.snapTo(0f)
        progressAnim.animateTo(1f, SheetEaseOut) { progress.floatValue = value }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 滑动基准距离：用整屏高，保证初始完全在屏外。
        val slidePx = constraints.maxHeight.toFloat()
        val flingClosePx = with(density) { SHEET_FLING_CLOSE_DP.dp.toPx() }
        val maxHeightPx = constraints.maxHeight * SHEET_MAX_HEIGHT_FRACTION

        fun close() {
            if (dismissed) return
            dismissed = true
            scope.launch {
                progressAnim.snapTo(progress.floatValue)
                progressAnim.animateTo(0f, SheetEaseOut) { progress.floatValue = value }
                onDismiss()
            }
        }
        // 系统返回：先收起当前弹窗（带下滑动画），不把返回事件穿透到外层退出播放页。
        // 本组件只在弹窗可见时进入组合，注册也晚于应用层退出播放页的 BackHandler，
        // 因此弹窗期间返回优先在这里被消费。
        BackHandler { close() }
        fun settle(flingPxPerS: Float) {
            if (dismissed) return
            val offsetPx = (1f - progress.floatValue) * slidePx
            if (offsetPx > contentHeightPx / 3f || flingPxPerS > flingClosePx) {
                close()
            } else {
                scope.launch {
                    progressAnim.snapTo(progress.floatValue)
                    progressAnim.animateTo(1f, SheetEaseOut) { progress.floatValue = value }
                }
            }
        }
        fun dragBy(dyPx: Float) {
            if (dismissed) return
            progress.floatValue =
                (progress.floatValue - dyPx / slidePx).coerceIn(0f, SHEET_MAX_PROGRESS)
        }

        // 可滚动内容：滚到顶后继续向下拖，把位移转给弹窗整体（与手势拖动同一套关闭判定）。
        val nestedConnection = object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && !dismissed && progress.value <= 1.0001f) {
                    val before = progress.value
                    dragBy(available.y)
                    return Offset(0f, (before - progress.value) * slidePx)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y > 0f && !dismissed) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }

        val dragModifier = Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    // 抓住弹窗时若升起动画还在跑，先停掉，避免动画每帧覆写 progress 不跟手。
                    scope.launch { progressAnim.stop() }
                },
                onDragEnd = {
                    settle(velocityTracker.calculateVelocity().y)
                },
                onDragCancel = { settle(0f) },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    // 下拉 1:1 跟手；上拉不做整体位移（交给内部列表自己滚）。
                    if (dragAmount > 0f) dragBy(dragAmount)
                },
            )
        }

        // 背板压暗：点击空白处关闭，透明度跟进度走（含升起 / 拖动 / 下滑全程）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.42f))
                .pointerInput(Unit) { detectTapGestures { close() } },
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = with(density) { maxHeightPx.toDp() })
                .navigationBarsPadding()
                .graphicsLayer { translationY = (1f - progress.value) * slidePx }
                .onSizeChanged { contentHeightPx = it.height }
                .nestedScroll(nestedConnection)
                .then(dragModifier),
        ) {
            MaterialTheme(colorScheme = playbackScheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)),
                ) {
                    Box(modifier = Modifier.matchParentSize()) {
                        CoverBackdrop(bitmap = blurredCover)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (scrollable) {
                                    Modifier.verticalScroll(scrollState)
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp),
                    ) {
                        SheetDragHandle()
                        if (title != null) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        CompositionLocalProvider(
                            LocalSheetClose provides {
                                close()
                            },
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.45f)),
        )
    }
}

@Composable
private fun SheetOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            maxLines = 1,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun LyricSettingsSheetContent(
    settings: AppSettings,
    lyricOffsetMs: Long,
    onAlignmentChange: (LyricAlignment) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onBoldChange: (Boolean) -> Unit,
    onInactiveBlurChange: (Float) -> Unit,
    onLyricOffsetChange: (Long) -> Unit,
    onCenterStartEndChange: (Boolean) -> Unit,
    onWordAnimationStyleChange: (LyricWordAnimationStyle) -> Unit,
) {
    QyStepperSlider(
        label = stringResource(R.string.lyric_offset),
        value = lyricOffsetMs / 1_000f,
        valueRange = -3f..3f,
        step = 0.1f,
        valueFormatter = { seconds ->
            val roundedTenths = (seconds * 10f).roundToInt()
            val sign = if (roundedTenths > 0) "+" else ""
            "$sign%.1fs".format(roundedTenths / 10f)
        },
        onValueChange = { seconds ->
            onLyricOffsetChange((seconds * 1_000f).roundToInt().toLong())
        },
        neutralValue = 0f,
    )
    Text(
        text = stringResource(R.string.lyric_alignment),
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.76f),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LyricAlignment.entries.forEach { alignment ->
            FilterChip(
                selected = settings.lyricAlignment == alignment,
                onClick = { onAlignmentChange(alignment) },
                label = {
                    Text(
                        text = stringResource(
                            when (alignment) {
                                LyricAlignment.LEFT -> R.string.lyric_align_left
                                LyricAlignment.CENTER -> R.string.lyric_align_center
                                LyricAlignment.RIGHT -> R.string.lyric_align_right
                            },
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
    QyRowSlider(
        label = stringResource(R.string.lyric_font_size),
        value = settings.lyricFontScale,
        valueRange = SettingsStore.MIN_LYRIC_FONT_SCALE..
            SettingsStore.MAX_LYRIC_FONT_SCALE,
        valueFormatter = { value -> "${(value * 100).roundToInt()}%" },
        onValueChange = onFontScaleChange,
        modifier = Modifier.padding(top = 10.dp),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Switch,
                onClick = { onBoldChange(!settings.lyricBold) },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.lyric_bold),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        MorphSwitch(
            checked = settings.lyricBold,
            onCheckedChange = onBoldChange,
        )
    }
    QyRowSlider(
        label = stringResource(R.string.lyric_blur_inactive),
        value = settings.lyricInactiveBlurDp,
        valueRange = SettingsStore.MIN_LYRIC_INACTIVE_BLUR_DP..
            SettingsStore.MAX_LYRIC_INACTIVE_BLUR_DP,
        valueFormatter = { value -> "%.1f dp".format(value) },
        onValueChange = onInactiveBlurChange,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Switch,
                onClick = {
                    onCenterStartEndChange(!settings.lyricCenterStartEnd)
                },
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.lyric_center_start_end),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        MorphSwitch(
            checked = settings.lyricCenterStartEnd,
            onCheckedChange = onCenterStartEndChange,
        )
    }
    Text(
        text = stringResource(R.string.lyric_word_animation),
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.76f),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterChip(
            selected = settings.lyricWordAnimationStyle ==
                LyricWordAnimationStyle.HIGHLIGHT,
            onClick = {
                onWordAnimationStyleChange(LyricWordAnimationStyle.HIGHLIGHT)
            },
            label = {
                Text(text = stringResource(R.string.lyric_word_animation_highlight))
            },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = settings.lyricWordAnimationStyle == LyricWordAnimationStyle.STAR,
            onClick = {
                onWordAnimationStyleChange(LyricWordAnimationStyle.STAR)
            },
            label = {
                Text(text = stringResource(R.string.lyric_word_animation_star))
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BackgroundSettingsSheetContent(
    musicReactiveBackground: Boolean,
    onMusicReactiveBackgroundChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                role = Role.Switch,
                onClick = {
                    onMusicReactiveBackgroundChange(!musicReactiveBackground)
                },
            )
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.music_reactive_background),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        MorphSwitch(
            checked = musicReactiveBackground,
            onCheckedChange = onMusicReactiveBackgroundChange,
        )
    }
}

private fun PlaybackMode.labelRes(): Int = when (this) {
    PlaybackMode.SEQUENTIAL -> R.string.playback_mode_sequential
    PlaybackMode.REPEAT_ONE -> R.string.playback_mode_repeat_one
    PlaybackMode.SHUFFLE -> R.string.playback_mode_shuffle
}

private fun formatPlaybackSpeed(speed: Float): String {
    val rounded = (speed * 100f).roundToInt() / 100f
    return if (rounded % 1f == 0f) {
        "${rounded.toInt()}x"
    } else {
        "${rounded.toString().trimEnd('0').trimEnd('.')}x"
    }
}

private fun formatSemitones(semitones: Float): String {
    val rounded = semitones.roundToInt()
    val sign = if (rounded > 0) "+" else ""
    return "$sign$rounded"
}

/** 例：「1.5x · +5 半音」；速度和音调都没改时返回空串。 */
private fun formatPlaybackTuning(speed: Float, pitch: Float): String {
    val parts = mutableListOf<String>()
    if (abs(speed - 1f) > 0.01f) {
        parts += formatPlaybackSpeed(speed)
    }
    val semitones = semitonesOfPitchFactor(pitch).roundToInt()
    if (semitones != 0) {
        parts += "${formatSemitones(semitones.toFloat())} 半音"
    }
    return parts.joinToString(" · ")
}

@Composable
private fun sleepTimerMenuValue(state: SleepTimerState): String = when (state.mode) {
    SleepTimerMode.OFF -> ""
    SleepTimerMode.TIMER -> formatDuration(state.remainingMs)
    SleepTimerMode.END_OF_TRACK -> stringResource(R.string.sleep_timer_end_of_track)
}

private fun sleepTimerSelectedMinutes(state: SleepTimerState): Int =
    if (state.mode == SleepTimerMode.TIMER) {
        ((state.remainingMs + 59_999L) / 60_000L).toInt()
    } else {
        -1
    }

@Composable
internal fun rememberMusicBackgroundMotion(
    enabled: Boolean,
    positionMs: Long,
): Float {
    val target = if (enabled) {
        val phase = (positionMs.mod(BACKGROUND_MOTION_PERIOD_MS)) /
            BACKGROUND_MOTION_PERIOD_MS.toFloat()
        kotlin.math.sin(phase * (2f * Math.PI).toFloat()).toFloat()
    } else {
        0f
    }
    val motion by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = BACKGROUND_MOTION_SMOOTH_MS,
            easing = LinearEasing,
        ),
        label = "music-background-motion",
    )
    return motion
}

@Composable
private fun OpaqueCoverCrossfade(
    blurredCover: Bitmap,
    backgroundMotion: Float,
    backgroundMotionEnabled: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    var previousCover by remember { mutableStateOf(blurredCover) }
    var currentCover by remember { mutableStateOf(blurredCover) }

    LaunchedEffect(blurredCover) {
        if (blurredCover !== currentCover) {
            previousCover = currentCover
            currentCover = blurredCover
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121418)),
    ) {
        // 旧封面始终作为不透明底板，防止交叉渐变中间帧透出常驻的主界面。
        CoverBackdrop(
            bitmap = previousCover,
            musicMotion = backgroundMotion,
            musicMotionEnabled = backgroundMotionEnabled,
        )
        Crossfade(
            targetState = currentCover,
            animationSpec = tween(TRACK_CHANGE_FADE_MS),
            label = label,
            modifier = Modifier.fillMaxSize(),
        ) { bitmap ->
            CoverBackdrop(
                bitmap = bitmap,
                musicMotion = backgroundMotion,
                musicMotionEnabled = backgroundMotionEnabled,
            )
        }
    }
}

@Composable
private fun BlurredMusicLightOverlay(motion: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sweep = motion.coerceIn(-1f, 1f)
        val pulse = 0.55f + 0.45f * ((sweep + 1f) * 0.5f)
        val maxRadius = size.maxDimension

        // 大范围柔焦光斑：没有清晰边界，只让颜色和亮度缓慢移动。
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00D9FF).copy(alpha = 0.18f + pulse * 0.12f),
                    Color(0xFF00D9FF).copy(alpha = 0.07f),
                    Color.Transparent,
                ),
                center = Offset(
                    size.width * (0.16f + sweep * 0.16f),
                    size.height * (0.25f - sweep * 0.14f),
                ),
                radius = maxRadius * 0.78f,
            ),
            radius = maxRadius * 0.78f,
            center = Offset(
                size.width * (0.16f + sweep * 0.16f),
                size.height * (0.25f - sweep * 0.14f),
            ),
            blendMode = BlendMode.Screen,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF4FD8).copy(alpha = 0.17f + (1f - pulse) * 0.12f),
                    Color(0xFFFF4FD8).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(
                    size.width * (0.86f - sweep * 0.14f),
                    size.height * (0.58f + sweep * 0.14f),
                ),
                radius = maxRadius * 0.72f,
            ),
            radius = maxRadius * 0.72f,
            center = Offset(
                size.width * (0.86f - sweep * 0.14f),
                size.height * (0.58f + sweep * 0.14f),
            ),
            blendMode = BlendMode.Screen,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF5878FF).copy(alpha = 0.15f + pulse * 0.1f),
                    Color(0xFF5878FF).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(
                    size.width * (0.54f + sweep * 0.12f),
                    size.height * (0.82f - sweep * 0.1f),
                ),
                radius = maxRadius * 0.68f,
            ),
            radius = maxRadius * 0.68f,
            center = Offset(
                size.width * (0.54f + sweep * 0.12f),
                size.height * (0.82f - sweep * 0.1f),
            ),
            blendMode = BlendMode.Screen,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFC857).copy(alpha = 0.1f + pulse * 0.08f),
                    Color.Transparent,
                ),
                center = Offset(
                    size.width * (0.48f - sweep * 0.18f),
                    size.height * (0.46f + sweep * 0.1f),
                ),
                radius = maxRadius * 0.5f,
            ),
            radius = maxRadius * 0.5f,
            center = Offset(
                size.width * (0.48f - sweep * 0.18f),
                size.height * (0.46f + sweep * 0.1f),
            ),
            blendMode = BlendMode.Screen,
        )
    }
}

@Composable
internal fun CoverBackdrop(
    bitmap: Bitmap,
    scrimAlpha: Float = 0f,
    musicMotion: Float = 0f,
    musicMotionEnabled: Boolean = false,
) {
    val motion = if (musicMotionEnabled) musicMotion.coerceIn(-1f, 1f) else 0f
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (musicMotionEnabled) {
            BlurredMusicLightOverlay(motion = motion)
        }
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha)),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClickableCover(
    bitmap: Bitmap?,
    size: Dp,
    style: CoverStyle,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onStyleSelect: (CoverStyle) -> Unit,
) {
    val description = stringResource(R.string.switch_to_lyrics)
    var styleMenuOpen by remember { mutableStateOf(false) }
    Box {
        val coverModifier = Modifier
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = { styleMenuOpen = true },
            )
            .semantics { contentDescription = description }
        when (style) {
            CoverStyle.SQUARE -> CoverArt(
                bitmap = bitmap,
                modifier = Modifier
                    .size(size)
                    .then(coverModifier),
            )
            CoverStyle.CIRCLE -> {
                // 圆形封面与唱片同款旋转：播放时匀速转，暂停停在当前角度。
                val rotation = rememberDiscRotation(isPlaying)
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(coverModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationZ = rotation.value },
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
            CoverStyle.VINYL -> {
                // 唱片 + 拨杆不是规则形状，方形的涟漪会穿帮，这里不画点击指示。
                val interactionSource = remember { MutableInteractionSource() }
                VinylCover(
                    bitmap = bitmap,
                    size = size,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
                            onLongClick = { styleMenuOpen = true },
                        )
                        .semantics { contentDescription = description },
                )
            }
        }
        // 长按封面切换样式：方形 / 圆形 / 唱片。
        DropdownMenu(
            expanded = styleMenuOpen,
            onDismissRequest = { styleMenuOpen = false },
        ) {
            CoverStyle.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(option.coverStyleLabel)) },
                    leadingIcon = {
                        Icon(option.coverStyleIcon, contentDescription = null)
                    },
                    trailingIcon = {
                        if (option == style) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        styleMenuOpen = false
                        onStyleSelect(option)
                    },
                )
            }
        }
    }
}

private val CoverStyle.coverStyleLabel: Int
    get() = when (this) {
        CoverStyle.SQUARE -> R.string.cover_style_square
        CoverStyle.CIRCLE -> R.string.cover_style_circle
        CoverStyle.VINYL -> R.string.cover_style_vinyl
    }

private val CoverStyle.coverStyleIcon: ImageVector
    get() = when (this) {
        CoverStyle.SQUARE -> Icons.Rounded.CropSquare
        CoverStyle.CIRCLE -> Icons.Rounded.Circle
        CoverStyle.VINYL -> Icons.Rounded.Album
    }

/** 唱片转一圈的毫秒数，接近网易云唱机的转速观感。 */
private const val VINYL_ROTATION_PERIOD_MS = 18_000f

/**
 * 唱片 / 圆形封面共用的匀速旋转角：播放时按帧累加，暂停时停在当前角度；
 * 不能用 infiniteTransition，那种暂停/恢复会跳回起始角。
 */
@Composable
private fun rememberDiscRotation(isPlaying: Boolean): State<Float> {
    val rotation = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                rotation.floatValue = (rotation.floatValue + (frameNanos - lastFrameNanos) /
                    1_000_000f / VINYL_ROTATION_PERIOD_MS * 360f) % 360f
            }
            lastFrameNanos = frameNanos
        }
    }
    return rotation
}
/** 拨杆角度（度，0° = 竖直向下，负值向左偏）：播放时唱头搭在唱片外圈纹路上。 */
private const val VINYL_ARM_PLAY_ANGLE = -46f
/** 暂停时拨杆抬起、向画布外侧摆开的角度。 */
private const val VINYL_ARM_PAUSE_ANGLE = -75f
private const val VINYL_ARM_SWING_MS = 420

private val VinylDiscColor = Color(0xFF17181C)
private val VinylGrooveColor = Color.White.copy(alpha = 0.045f)
private val VinylEdgeColor = Color.White.copy(alpha = 0.10f)
private val VinylArtRingColor = Color.Black.copy(alpha = 0.55f)
private val VinylHoleColor = Color(0xFF0A0A0C)
private val VinylArmColor = Color(0xFFCDD2DC)
private val VinylArmShadowColor = Color(0xFF565B66)
private val VinylArmCounterweightColor = Color(0xFF4A4E58)
private val VinylArmHeadColor = Color(0xFFE6E8EE)
private val VinylArmHeadInnerColor = Color(0xFF3B3E47)
private val VinylPivotOuterColor = Color(0xFF31343C)
private val VinylPivotInnerColor = Color(0xFF1C1E24)

/** 黑胶唱片封面：播放时唱片匀速旋转、拨杆搭在唱片上；暂停时唱片停在当前角度、拨杆抬起摆离。 */
@Composable
private fun VinylCover(
    bitmap: Bitmap?,
    size: Dp,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val image = bitmap?.asImageBitmap()
    // 唱片匀速旋转：播放时转、暂停停在当前角度。
    val rotation = rememberDiscRotation(isPlaying)
    // 拨杆抬起 / 落下的摆动角度。
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) VINYL_ARM_PLAY_ANGLE else VINYL_ARM_PAUSE_ANGLE,
        animationSpec = tween(VINYL_ARM_SWING_MS, easing = FastOutSlowInEasing),
        label = "vinyl-arm",
    )
    Box(modifier = modifier.size(size)) {
        // 唱片盘面（旋转），拨杆单独一层不跟着转。
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation.value },
        ) {
            drawVinylDisc(image)
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawVinylTonearm(armAngle)
        }
    }
}

private fun DrawScope.drawVinylDisc(image: ImageBitmap?) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val discR = size.minDimension * 0.44f
    val artR = discR * 0.68f
    drawCircle(VinylDiscColor, radius = discR, center = center)
    // 外缘一圈高光边。
    drawCircle(
        VinylEdgeColor,
        radius = discR * 0.985f,
        center = center,
        style = Stroke(width = size.minDimension * 0.010f),
    )
    // 封面外的密纹。
    val grooveCount = 6
    for (i in 0 until grooveCount) {
        val t = if (grooveCount == 1) 0f else i / (grooveCount - 1).toFloat()
        val r = artR * 1.07f + (discR * 0.96f - artR * 1.07f) * t
        drawCircle(
            VinylGrooveColor,
            radius = r,
            center = center,
            style = Stroke(width = 1f),
        )
    }
    // 中央圆形封面（跟随唱片旋转）。
    if (image != null) {
        val srcSide = minOf(image.width, image.height)
        val srcOffset = IntOffset((image.width - srcSide) / 2, (image.height - srcSide) / 2)
        val dstSide = (artR * 2f).toInt() + 2
        val dstOffset = IntOffset((center.x - dstSide / 2f).toInt(), (center.y - dstSide / 2f).toInt())
        clipPath(
            Path().apply {
                addOval(
                    Rect(
                        left = center.x - artR,
                        top = center.y - artR,
                        right = center.x + artR,
                        bottom = center.y + artR,
                    ),
                )
            },
        ) {
            drawImage(
                image = image,
                srcOffset = srcOffset,
                srcSize = IntSize(srcSide, srcSide),
                dstOffset = dstOffset,
                dstSize = IntSize(dstSide, dstSide),
                filterQuality = FilterQuality.Low,
            )
        }
        drawCircle(
            VinylArtRingColor,
            radius = artR,
            center = center,
            style = Stroke(width = size.minDimension * 0.006f),
        )
    } else {
        drawCircle(Color(0xFF2A2C31), radius = artR, center = center)
    }
    // 中心轴孔。
    drawCircle(
        Color.White.copy(alpha = 0.22f),
        radius = size.minDimension * 0.023f,
        center = center,
    )
    drawCircle(VinylHoleColor, radius = size.minDimension * 0.016f, center = center)
    // 表面反光：中心透明、边缘微亮，转动时有黑胶的光泽感。
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.055f)),
            center = center,
            radius = discR,
        ),
        radius = discR,
        center = center,
    )
}

/** 拨杆：支点固定在右上角，整体绕支点摆动；播放落在唱片上、暂停抬起摆离。 */
private fun DrawScope.drawVinylTonearm(angleDeg: Float) {
    val w = size.minDimension
    val pivot = Offset(w * 0.87f, w * 0.075f)
    // 臂长与盘面半径（0.44w）配套：播放角下唱头中心落在距盘心 0.40w 处，正好搭在边缘纹路上。
    val armLength = w * 0.165f
    val rad = Math.toRadians(angleDeg.toDouble())
    val dir = Offset(sin(rad).toFloat(), cos(rad).toFloat())
    val end = pivot + dir * armLength
    val strokeWidth = w * 0.024f

    // 支点另一侧的配重臂。
    val counterEnd = pivot - dir * (armLength * 0.32f)
    drawLine(
        color = VinylArmCounterweightColor,
        start = pivot,
        end = counterEnd,
        strokeWidth = strokeWidth * 1.25f,
        cap = StrokeCap.Round,
    )
    // 主臂：深色打底 + 浅色覆盖，做出金属杆的立体感。
    drawLine(
        color = VinylArmShadowColor,
        start = pivot,
        end = end,
        strokeWidth = strokeWidth * 1.15f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = VinylArmColor,
        start = pivot,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    // 唱头。
    drawCircle(VinylArmHeadColor, radius = w * 0.044f, center = end)
    drawCircle(VinylArmHeadInnerColor, radius = w * 0.019f, center = end)
    // 支点底座。
    drawCircle(VinylPivotOuterColor, radius = w * 0.052f, center = pivot)
    drawCircle(VinylPivotInnerColor, radius = w * 0.021f, center = pivot)
}

@Composable
private fun TrackIdentity(
    track: Track,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    val artistName = track.artist.ifBlank { stringResource(R.string.unknown_artist) }
    val albumName = track.album.ifBlank { stringResource(R.string.unknown_album) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stripClipRangeSuffix(track.title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { onOpenArtist(artistName) }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White.copy(alpha = 0.72f),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Button) { onOpenAlbum(albumName) }
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Album,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = Color.White.copy(alpha = 0.66f),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = albumName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.74f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistDialogContent(
    queue: List<Track>,
    currentTrackId: String,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.empty_library_title),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val items = remember(queue) {
        mutableStateListOf<Track>().apply { addAll(queue) }
    }
    // 打开播放列表时自动定位到正在播放的那一首，并把前面两首留在上方当上下文。
    LaunchedEffect(Unit) {
        val currentIndex = items.indexOfFirst { it.id == currentTrackId }
        if (currentIndex >= 0) {
            listState.scrollToItem((currentIndex - QUEUE_SCROLL_CONTEXT_ITEMS).coerceAtLeast(0))
        }
    }
    var draggingTrackId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = items,
            key = { _, track -> track.id },
        ) { index, track ->
            val selected = track.id == currentTrackId
            val dragging = track.id == draggingTrackId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (dragging) dragOffsetY else 0f
                    }
                    .background(
                        if (selected) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
                    )
                    .clickable(
                        role = Role.Button,
                        onClick = { onSelectQueueIndex(index) },
                    )
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 56.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingTrackId = track.id
                                    dragStartIndex = items.indexOfFirst { it.id == track.id }
                                    dragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val fromIndex = items.indexOfFirst { it.id == track.id }
                                    if (fromIndex < 0) return@detectDragGesturesAfterLongPress
                                    dragOffsetY += dragAmount.y
                                    val draggedInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.index == fromIndex }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val draggedCenter =
                                        draggedInfo.offset + draggedInfo.size / 2f + dragOffsetY
                                    val targetInfo = listState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { info ->
                                            info.index != fromIndex &&
                                                draggedCenter >= info.offset &&
                                                draggedCenter <= info.offset + info.size
                                        }
                                        ?: return@detectDragGesturesAfterLongPress
                                    items.add(targetInfo.index, items.removeAt(fromIndex))
                                    dragOffsetY += draggedInfo.offset - targetInfo.offset
                                },
                                onDragEnd = {
                                    val toIndex = draggingTrackId
                                        ?.let { id -> items.indexOfFirst { it.id == id } }
                                        ?: -1
                                    draggingTrackId = null
                                    dragOffsetY = 0f
                                    if (dragStartIndex >= 0 &&
                                        toIndex >= 0 &&
                                        dragStartIndex != toIndex
                                    ) {
                                        onMoveQueueItem(dragStartIndex, toIndex)
                                    }
                                    dragStartIndex = -1
                                },
                                onDragCancel = {
                                    draggingTrackId = null
                                    dragOffsetY = 0f
                                    dragStartIndex = -1
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.reorder_track),
                        modifier = Modifier.size(22.dp),
                        tint = Color.White.copy(alpha = if (dragging) 0.95f else 0.55f),
                    )
                }
                Text(
                    text = (index + 1).toString(),
                    modifier = Modifier.width(22.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        // 片段队列项显示「歌曲名 + 时间段」，普通曲目仍显示原始标题
                        text = if (track.id.startsWith("clip:")) {
                            track.title
                        } else {
                            stripClipRangeSuffix(track.title)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist.ifBlank {
                            stringResource(R.string.unknown_artist)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(20.dp),
                        tint = Color.White,
                    )
                }
            }
            if (index < items.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp),
                    color = Color.White.copy(alpha = 0.14f),
                )
            }
        }
    }
}

@Composable
internal fun LyricsPanel(
    lyricsState: LyricsUiState,
    positionMs: Long,
    settings: AppSettings,
    animate: Boolean,
    onPlayFrom: (Long) -> Unit,
    karaokeMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        when (lyricsState) {
            LyricsUiState.Loading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.lyrics_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }

            LyricsUiState.None,
            is LyricsUiState.Failed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White.copy(alpha = 0.72f),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.no_lyrics),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }

            is LyricsUiState.Ready -> {
                if (lyricsState.lyrics.isSynchronized) {
                    SyncedLyrics(
                        lyrics = lyricsState.lyrics,
                        positionMs = positionMs,
                        settings = settings,
                        animate = animate,
                        onPlayFrom = onPlayFrom,
                        karaokeMode = karaokeMode,
                    )
                } else {
                    PlainLyrics(
                        text = lyricsState.lyrics.rawText,
                        settings = settings,
                        karaokeMode = karaokeMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    positionMs: Long,
    settings: AppSettings,
    animate: Boolean,
    onPlayFrom: (Long) -> Unit,
    karaokeMode: Boolean,
) {
    val listState = rememberLazyListState()
    var previewIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
    var draggingLyrics by remember(lyrics) { mutableStateOf(false) }
    val activeIndex = remember(lyrics, positionMs) {
        LrcParser.findActiveLine(lyrics.lines, positionMs)
    }
    val density = LocalDensity.current
    val starAnimation = !karaokeMode &&
        settings.lyricWordAnimationStyle == LyricWordAnimationStyle.STAR
    val lyricTextCenterOffsetPx = if (
        starAnimation
    ) {
        with(density) { STAR_LANE_HEIGHT.toPx() / 2f }
    } else {
        0f
    }

    fun lyricIndexAt(y: Float): Int? {
        val layoutInfo = listState.layoutInfo
        val items = layoutInfo.visibleItemsInfo
        if (items.isEmpty()) return null
        val viewportStartOffset = layoutInfo.viewportStartOffset
        return items.minByOrNull { item ->
            val textCenterY = item.offset - viewportStartOffset +
                item.size / 2f +
                lyricTextCenterOffsetPx
            abs(textCenterY - y)
        }?.index
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fallbackItemHeight = with(density) { 56.dp.roundToPx() }
        val centerStartEnd = karaokeMode || settings.lyricCenterStartEnd
        val contentPadding = if (centerStartEnd && maxHeight != Dp.Infinity) {
            maxHeight / 2
        } else {
            28.dp
        }
        val overlayWidthPx = with(density) { maxWidth.toPx() }
        val overlayHeightPx = with(density) { maxHeight.toPx() }

        LaunchedEffect(activeIndex, centerStartEnd, contentPadding, lyrics, draggingLyrics) {
            if (draggingLyrics) return@LaunchedEffect
            if (activeIndex < 0) return@LaunchedEffect
            // 等新一轮布局应用 contentPadding 后再定位，避免切到居中模式时读到旧位置。
            withFrameNanos { }
            val viewportHeight = listState.layoutInfo.viewportSize.height
            // 视觉目标是让当前句按设置停在视口中央或上方三分之一。
            fun lyricTargetTop(itemHeight: Int): Int = if (viewportHeight > 0) {
                if (centerStartEnd) {
                    ((viewportHeight - itemHeight) / 2).coerceAtLeast(0)
                } else {
                    viewportHeight / 3
                }
            } else {
                120
            }
            val contentPaddingPx = with(density) { contentPadding.roundToPx() }
            // 逐行推进时目标行基本都在视口里：直接按差值做一次平滑滚动。
            // 只有跳播 / 点选到很远的一行时才退回 LazyColumn 自带的滚动动画。
            var pass = 0
            while (pass < LYRIC_SPRING_SCROLL_PASSES) {
                pass++
                val viewportStart = listState.layoutInfo.viewportStartOffset
                val activeItem = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == activeIndex }
                if (activeItem == null) {
                    listState.animateScrollToItem(
                        index = activeIndex,
                        scrollOffset = contentPaddingPx -
                            lyricTargetTop(fallbackItemHeight),
                    )
                    break
                }
                // 目标行已经可见：按"当前顶部 - 期望顶部"的差值平滑滚过去。
                val delta = (
                    activeItem.offset - viewportStart - lyricTargetTop(activeItem.size)
                    ).toFloat()
                if (abs(delta) < 1f) break
                // 行高可能这一轮才排版完，滚到目标后再补一次差值。
                listState.animateScrollBy(delta, lyricScrollSpec)
            }
        }

        LaunchedEffect(draggingLyrics, lyrics) {
            if (!draggingLyrics) return@LaunchedEffect
            snapshotFlow {
                lyricIndexAt(overlayHeightPx / 2f)
            }.collectLatest { centerIndex ->
                if (centerIndex != null) {
                    previewIndex = centerIndex
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "$index-${line.timeMs}" },
            ) { index, line ->
            val active = index == activeIndex
            val preview = index == previewIndex
            val baseStyle = if (karaokeMode) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.titleMedium
            }
            val baseFontSize = baseStyle.fontSize.value *
                settings.lyricFontScale *
                if (karaokeMode) KARAOKE_LYRIC_FONT_SCALE else 1f
            val lineAnimation = !karaokeMode &&
                settings.lyricWordAnimationStyle == LyricWordAnimationStyle.STAR
            // 排版始终按当前行的字号进行，放大/缩小只做视觉缩放。
            // 这样一句歌词的换行位置在切换前后完全一致，不会因为行数变化而突然跳动。
            val layoutFontSize = baseFontSize *
                if (karaokeMode) 1f else LYRIC_ACTIVE_SCALE
            // 距离梯度：0 = 当前行，+1 = 即将进入高亮的下一句，-1 = 刚唱完的上一句。
            // 每行按距离取形变档位（横向 / 纵向 / 透明度 / 模糊权重），全部由弹簧
            // 插值驱动：换行时整页歌词同时挪一档，呈现随旋律"呼吸舒展"的起伏。
            val breathSpec = spring<Float>(
                dampingRatio = LYRIC_BREATH_DAMPING,
                stiffness = LYRIC_BREATH_STIFFNESS,
            )
            val tier = lyricTier(
                if (karaokeMode || activeIndex < 0) Int.MAX_VALUE else index - activeIndex,
            )
            val animatedScaleX by animateFloatAsState(
                targetValue = tier.scaleX,
                animationSpec = breathSpec,
                label = "lyric-scale-x-$index",
            )
            val animatedScaleY by animateFloatAsState(
                targetValue = tier.scaleY,
                animationSpec = breathSpec,
                label = "lyric-scale-y-$index",
            )
            val animatedAlpha by animateFloatAsState(
                targetValue = tier.alpha,
                animationSpec = breathSpec,
                label = "lyric-alpha-$index",
            )
            // 拖动预览的行保持清晰，其余按距离加权用户设置的基准模糊。
            val animatedBlurWeight by animateFloatAsState(
                targetValue = if (preview) 0f else tier.blurWeight,
                animationSpec = breathSpec,
                label = "lyric-blur-$index",
            )
            val lyricScaleX = if (karaokeMode) 1f else animatedScaleX
            val lyricScaleY = if (karaokeMode) 1f else animatedScaleY
            val lyricAlpha = if (karaokeMode) 1f else animatedAlpha
            val lyricBlurRadius = if (karaokeMode) {
                0.dp
            } else {
                (animatedBlurWeight * settings.lyricInactiveBlurDp).dp
            }
            val textAlign = if (karaokeMode) {
                TextAlign.Center
            } else {
                when (settings.lyricAlignment) {
                    LyricAlignment.LEFT -> TextAlign.Start
                    LyricAlignment.CENTER -> TextAlign.Center
                    LyricAlignment.RIGHT -> TextAlign.End
                }
            }
            val transformOrigin = if (karaokeMode) {
                TransformOrigin.Center
            } else {
                TransformOrigin(
                    pivotFractionX = when (settings.lyricAlignment) {
                        LyricAlignment.LEFT -> 0f
                        LyricAlignment.CENTER -> 0.5f
                        LyricAlignment.RIGHT -> 1f
                    },
                    pivotFractionY = 0.5f,
                )
            }
            val modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (preview) 2f else if (active) 1f else 0f)
                .clickable(role = Role.Button) {
                    previewIndex = null
                    onPlayFrom(line.timeMs)
                }
                .padding(
                    horizontal = LYRIC_LINE_HORIZONTAL_PADDING,
                    vertical = if (lineAnimation) {
                        STAR_LYRIC_LINE_VERTICAL_PADDING
                    } else {
                        LYRIC_LINE_VERTICAL_PADDING
                    },
                )
                .blur(radius = lyricBlurRadius)
                .graphicsLayer {
                    scaleX = lyricScaleX
                    scaleY = lyricScaleY
                    alpha = lyricAlpha
                    this.transformOrigin = transformOrigin
                }
            val style = baseStyle.copy(
                fontSize = layoutFontSize.sp,
                lineHeight = (
                    layoutFontSize * if (lineAnimation) {
                        STAR_LYRIC_WRAPPED_LINE_HEIGHT_RATIO
                    } else {
                        LYRIC_WRAPPED_LINE_HEIGHT_RATIO
                    }
                    ).sp,
            )
            val fontWeight = if (settings.lyricBold) {
                FontWeight.Black
            } else if (active) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }

            if (active && line.segments.isNotEmpty()) {
                KaraokeLyricText(
                    line = line,
                    positionMs = positionMs,
                    animate = animate,
                    wordAnimationStyle = settings.lyricWordAnimationStyle,
                    modifier = modifier,
                    style = style,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                )
            } else {
                Column(modifier = modifier) {
                    if (lineAnimation) {
                        Spacer(modifier = Modifier.height(STAR_LANE_HEIGHT))
                    }
                    Text(
                        text = line.text,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (active || preview) {
                            Color.White
                        } else {
                            // 未播放的行和"当前行未唱到的部分"用同一个灰度，保持一致。
                            Color.White.copy(alpha = LYRIC_UNSUNG_ALPHA)
                        },
                        style = style,
                        fontWeight = fontWeight,
                        textAlign = textAlign,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f)
                .pointerInput(lyrics) {
                    detectTapGestures { offset ->
                        lyricIndexAt(offset.y)?.let { index ->
                            lyrics.lines.getOrNull(index)?.let { line ->
                                onPlayFrom(line.timeMs)
                            }
                        }
                    }
                }
                .pointerInput(lyrics) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            draggingLyrics = true
                            previewIndex = lyricIndexAt(overlayHeightPx / 2f)
                        },
                        onDragEnd = {
                            val centerIndex = lyricIndexAt(overlayHeightPx / 2f)
                            centerIndex?.let { index ->
                                lyrics.lines.getOrNull(index)?.let { line ->
                                    onPlayFrom(line.timeMs)
                                }
                            }
                            previewIndex = null
                            draggingLyrics = false
                        },
                        onDragCancel = {
                            previewIndex = null
                            draggingLyrics = false
                        },
                    ) { change, _ ->
                        // 负向 raw delta 让歌词内容跟手指同向移动；固定横线始终对齐中央行。
                        val deltaY = change.position.y - change.previousPosition.y
                        listState.dispatchRawDelta(-deltaY)
                        change.consume()
                    }
                },
        ) {
            val selectedIndex = previewIndex
            if (selectedIndex != null) {
                val lineY = overlayHeightPx / 2f
                val lineStartX = with(density) { 14.dp.toPx() }
                val lineEndX = overlayWidthPx - lineStartX
                val dashOnPx = with(density) { 8.dp.toPx() }
                val dashOffPx = with(density) { 6.dp.toPx() }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.82f),
                        start = Offset(lineStartX, lineY),
                        end = Offset(lineEndX, lineY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(dashOnPx, dashOffPx),
                            phase = 0f,
                        ),
                    )
                }
                Text(
                    text = formatDuration(
                        lyrics.lines.getOrNull(selectedIndex)?.timeMs ?: 0L,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                x = -lineStartX.roundToInt(),
                                y = (lineY - with(density) { 34.dp.toPx() })
                                    .coerceAtLeast(0f)
                                    .roundToInt(),
                            )
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun KaraokeLyricText(
    line: LyricLine,
    positionMs: Long,
    animate: Boolean,
    wordAnimationStyle: LyricWordAnimationStyle,
    modifier: Modifier,
    style: TextStyle,
    fontWeight: FontWeight,
    textAlign: TextAlign,
) {
    val animatedPositionMs = remember(line.timeMs) {
        Animatable(positionMs.toFloat())
    }
    LaunchedEffect(positionMs, animate) {
        if (animate) {
            animatedPositionMs.animateTo(
                targetValue = positionMs.toFloat(),
                animationSpec = tween(
                    durationMillis = KARAOKE_POSITION_ANIMATION_MS,
                    easing = LinearEasing,
                ),
            )
        } else {
            // 歌词页不在前台时直接对齐进度，不做逐帧补间。
            animatedPositionMs.snapTo(positionMs.toFloat())
        }
    }
    // 逐字高亮量化到 50ms：每秒重建文本的次数从 ~60 次降到 ~20 次，
    // 中间的帧复用同一份文本布局，播放页/转场就不会被文字重排拖住。
    val quantizedPositionMs by remember(line.timeMs) {
        derivedStateOf {
            val stepped = (animatedPositionMs.value / KARAOKE_POSITION_STEP_MS).toInt()
            stepped * KARAOKE_POSITION_STEP_MS
        }
    }
    val sungColor = Color.White
    // 未唱到的部分压暗一些，和已唱到的纯白拉开对比。
    val unsungColor = Color.White.copy(alpha = LYRIC_UNSUNG_ALPHA)
    val activeSegmentIndex = remember(line, quantizedPositionMs) {
        line.segments.indexOfLast { it.timeMs <= quantizedPositionMs }.coerceAtLeast(0)
    }
    val activeSegment = line.segments.getOrNull(activeSegmentIndex)
    val activeSegmentEndMs = line.segments.getOrNull(activeSegmentIndex + 1)?.timeMs
        ?: (activeSegment?.timeMs?.plus(KARAOKE_LAST_SEGMENT_DURATION_MS)
            ?: KARAOKE_LAST_SEGMENT_DURATION_MS)
    val activeSegmentDurationMs = (activeSegmentEndMs - (activeSegment?.timeMs ?: 0L))
        .coerceAtLeast(1L)
    val isLastSegment = activeSegmentIndex >= line.segments.lastIndex
    val idleRoll = remember(line.timeMs) { Animatable(0f) }
    LaunchedEffect(activeSegmentIndex, isLastSegment) {
        idleRoll.snapTo(0f)
        if (!isLastSegment) return@LaunchedEffect
        while (true) {
            idleRoll.animateTo(
                targetValue = STAR_ROLL_DEGREES,
                animationSpec = tween(
                    durationMillis = STAR_IDLE_ROLL_PERIOD_MS,
                    easing = LinearEasing,
                ),
            )
            idleRoll.snapTo(0f)
        }
    }
    val nextSegmentIndex = (activeSegmentIndex + 1)
        .coerceAtMost(line.segments.lastIndex.coerceAtLeast(0))
    val previousSegmentIndex = (activeSegmentIndex - 1).coerceAtLeast(0)
    val segmentStartOffset = remember(line, activeSegmentIndex) {
        line.segments.take(activeSegmentIndex).sumOf { it.text.length }
    }
    val nextSegmentStartOffset = remember(line, nextSegmentIndex) {
        line.segments.take(nextSegmentIndex).sumOf { it.text.length }
    }
    val previousSegmentStartOffset = remember(line, previousSegmentIndex) {
        line.segments.take(previousSegmentIndex).sumOf { it.text.length }
    }
    val activeSegmentEndOffset = segmentStartOffset +
        (activeSegment?.text?.length ?: 0)
    // 正在唱的词的字形横向包络，由 onTextLayout 回填。布局结果对象每次换新，
    // 这里只存"(词序号, 边界)"并按结构相等去重，避免重建文本→重新布局→再重建的死循环。
    var activeSweep by remember(line) {
        mutableStateOf<WordSweepBounds?>(null, structuralEqualityPolicy())
    }
    val text = remember(line, quantizedPositionMs, activeSweep) {
        buildAnnotatedString {
            line.segments.forEachIndexed { index, segment ->
                val nextTime = line.segments.getOrNull(index + 1)?.timeMs
                    ?: (segment.timeMs + KARAOKE_LAST_SEGMENT_DURATION_MS)
                val progress = if (quantizedPositionMs <= segment.timeMs) {
                    0f
                } else {
                    (
                        (quantizedPositionMs - segment.timeMs.toFloat()) /
                            (nextTime - segment.timeMs).toFloat().coerceAtLeast(1f)
                        ).coerceIn(0f, 1f)
                }
                when {
                    progress <= 0f ->
                        withStyle(SpanStyle(color = unsungColor)) { append(segment.text) }

                    progress >= 1f ->
                        withStyle(SpanStyle(color = sungColor)) { append(segment.text) }

                    else -> {
                        // 正在唱的词：亮暗分界从词的左边缘扫到右边缘；
                        // 首帧布局未就绪或词跨了两行时，退化为词内逐字变亮。
                        val bounds = activeSweep
                            ?.takeIf { it.segmentIndex == index }
                            ?.bounds
                        if (bounds != null) {
                            withStyle(
                                SpanStyle(
                                    brush = wordSweepBrush(
                                        bounds = bounds,
                                        progress = progress,
                                        unsungColor = unsungColor,
                                        sungColor = sungColor,
                                    ),
                                ),
                            ) {
                                append(segment.text)
                            }
                        } else {
                            appendCharSweep(segment.text, progress, unsungColor, sungColor)
                        }
                    }
                }
            }
        }
    }
    var textLayoutResult by remember(line) { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val starIconHalfSizePx = with(density) { STAR_ICON_SIZE.toPx() / 2f }
    val starIconSizePx = with(density) { STAR_ICON_SIZE.toPx() }
    val starLaneHeightPx = with(density) { STAR_LANE_HEIGHT.toPx() }
    val starGapPx = with(density) { STAR_ICON_GAP.toPx() }
    val starJumpDistancePx = with(density) { STAR_ICON_JUMP.toPx() }
    val starMotion = if (wordAnimationStyle == LyricWordAnimationStyle.STAR) {
        val layout = textLayoutResult
        if (layout != null && layout.layoutInput.text.length > 0) {
            // 以第一行字形的上缘为统一视觉基准，B 行也按相同基线高度对齐。
            val referenceBaseline = layout.getLineBaseline(0)
            val referenceGlyphTop = layout.getBoundingBox(0).top
            val glyphAscent = (referenceBaseline - referenceGlyphTop).coerceAtLeast(1f)

            fun starAnchor(offset: Int): Offset {
                val safeOffset = offset.coerceIn(0, layout.layoutInput.text.length - 1)
                val bounds = layout.getBoundingBox(safeOffset)
                val lineIndex = layout.getLineForOffset(safeOffset)
                val visualGlyphTop = layout.getLineBaseline(lineIndex) - glyphAscent
                return Offset(
                    x = bounds.center.x - starIconHalfSizePx,
                    y = visualGlyphTop + starLaneHeightPx - starGapPx - starIconSizePx,
                )
            }

            val currentOffset = segmentStartOffset.coerceIn(
                0,
                layout.layoutInput.text.length - 1,
            )
            val nextOffset = nextSegmentStartOffset.coerceIn(
                0,
                layout.layoutInput.text.length - 1,
            )
            val previousOffset = previousSegmentStartOffset.coerceIn(
                0,
                layout.layoutInput.text.length - 1,
            )
            val currentLine = layout.getLineForOffset(currentOffset)
            val nextLine = layout.getLineForOffset(nextOffset)
            val previousLine = layout.getLineForOffset(previousOffset)
            val startsVisualLine = activeSegmentIndex > 0 && previousLine != currentLine
            // 整句唱完后星星保留到下一句成为当前行；只有同句内部换行时才提前淡出。
            val endsVisualLine = nextLine != currentLine
            StarMotionAnchors(
                start = starAnchor(currentOffset),
                end = starAnchor(if (endsVisualLine) currentOffset else nextOffset),
                startsVisualLine = startsVisualLine,
                endsVisualLine = endsVisualLine,
            )
        } else {
            null
        }
    } else {
        null
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopStart,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (wordAnimationStyle == LyricWordAnimationStyle.STAR) {
                Spacer(modifier = Modifier.height(STAR_LANE_HEIGHT))
            }
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style = style,
                fontWeight = fontWeight,
                textAlign = textAlign,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Visible,
                onTextLayout = { result ->
                    textLayoutResult = result
                    activeSweep = result?.let { layout ->
                        WordSweepBounds(
                            segmentIndex = activeSegmentIndex,
                            bounds = segmentSweepBounds(
                                layout = layout,
                                startOffset = segmentStartOffset,
                                endOffsetExclusive = activeSegmentEndOffset,
                            ),
                        )
                    }
                },
            )
        }
        if (starMotion != null && activeSegment != null) {
            val starStart = starMotion.start
            val starEnd = starMotion.end
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = starStart.x.roundToInt(),
                            y = starStart.y.roundToInt(),
                        )
                    }
                    .size(STAR_ICON_SIZE)
                    .graphicsLayer {
                        if (isLastSegment) {
                            // 到最后一个字后固定在原处，只保留很慢的旋转。
                            alpha = STAR_ICON_MIN_ALPHA
                            translationX = 0f
                            translationY = 0f
                            rotationZ = idleRoll.value
                        } else {
                            val progress = (
                                (animatedPositionMs.value - activeSegment.timeMs.toFloat()) /
                                    activeSegmentDurationMs.toFloat()
                                ).coerceIn(0f, 1f)
                            val pulse = 0.5f -
                                0.5f * cos(progress * STAR_PULSE_RADIANS)
                            alpha = when {
                                starMotion.startsVisualLine && starMotion.endsVisualLine ->
                                    STAR_ICON_MAX_ALPHA * pulse

                                starMotion.endsVisualLine ->
                                    STAR_ICON_MIN_ALPHA * (1f - progress)

                                else -> {
                                    val settledAlpha = STAR_ICON_MIN_ALPHA +
                                        (STAR_ICON_MAX_ALPHA - STAR_ICON_MIN_ALPHA) * pulse
                                    if (starMotion.startsVisualLine) {
                                        settledAlpha * progress
                                    } else {
                                        settledAlpha
                                    }
                                }
                            }
                            translationX = (starEnd.x - starStart.x) * progress
                            translationY = (starEnd.y - starStart.y) * progress -
                                starJumpDistancePx * pulse
                            rotationZ = progress * STAR_ROLL_DEGREES
                        }
                        scaleX = 1f
                        scaleY = 1f
                    },
                tint = Color.White,
            )
        }
    }
}

/** 正在唱的词的扫亮信息：词在行内的序号 + 字形横向包络（跨行词为 null）。 */
private data class WordSweepBounds(
    val segmentIndex: Int,
    val bounds: Rect?,
)

/** 活动词的字形横向包络；词跨了两行（或退化成零宽）时返回 null。 */
private fun segmentSweepBounds(
    layout: TextLayoutResult,
    startOffset: Int,
    endOffsetExclusive: Int,
): Rect? {
    val textLength = layout.layoutInput.text.length
    if (textLength == 0 || startOffset >= endOffsetExclusive) return null
    val firstOffset = startOffset.coerceIn(0, textLength - 1)
    val lastOffset = (endOffsetExclusive - 1).coerceIn(0, textLength - 1)
    if (layout.getLineForOffset(firstOffset) != layout.getLineForOffset(lastOffset)) return null
    val first = layout.getBoundingBox(firstOffset)
    val last = layout.getBoundingBox(lastOffset)
    val bounds = Rect(
        left = minOf(first.left, last.left),
        top = minOf(first.top, last.top),
        right = maxOf(first.right, last.right),
        bottom = maxOf(first.bottom, last.bottom),
    )
    return bounds.takeIf { it.width >= 1f }
}

/** 词内扫亮的渐变笔刷：亮色在左、暗色在右，分界带一点软边。 */
private fun wordSweepBrush(
    bounds: Rect,
    progress: Float,
    unsungColor: Color,
    sungColor: Color,
): Brush {
    val halfFeather = WORD_SWEEP_FEATHER_RATIO / 2f
    val sungStop = (progress - halfFeather).coerceIn(0f, 1f)
    val unsungStop = (progress + halfFeather).coerceIn(0f, 1f)
    return Brush.horizontalGradient(
        *buildList {
            add(0f to sungColor)
            if (sungStop > 0f) add(sungStop to sungColor)
            if (unsungStop < 1f) add(unsungStop to unsungColor)
            add(1f to unsungColor)
        }.toTypedArray(),
        startX = bounds.left,
        endX = bounds.right,
    )
}

/** 没有布局信息时的退化路径：把亮度按字符从词首摊到词尾。 */
private fun AnnotatedString.Builder.appendCharSweep(
    text: String,
    progress: Float,
    unsungColor: Color,
    sungColor: Color,
) {
    val weights = text.map { it.sweepWeight() }
    val totalWeight = weights.sum()
    if (totalWeight <= 0f) {
        withStyle(SpanStyle(color = lerp(unsungColor, sungColor, progress))) { append(text) }
        return
    }
    var consumed = 0f
    weights.forEachIndexed { index, weight ->
        val startFrac = consumed / totalWeight
        consumed += weight
        val endFrac = consumed / totalWeight
        val charProgress = ((progress - startFrac) / (endFrac - startFrac)).coerceIn(0f, 1f)
        withStyle(SpanStyle(color = lerp(unsungColor, sungColor, charProgress))) {
            append(text[index].toString())
        }
    }
}

/** 半角字符按较窄宽度参与分摊，让中英混排的扫亮速度更均匀。 */
private fun Char.sweepWeight(): Float = if (code < 0x2E80) 0.55f else 1f

@Composable
private fun PlainLyrics(
    text: String,
    settings: AppSettings,
    karaokeMode: Boolean,
) {
    val textAlign = if (karaokeMode) {
        TextAlign.Center
    } else {
        when (settings.lyricAlignment) {
            LyricAlignment.LEFT -> TextAlign.Start
            LyricAlignment.CENTER -> TextAlign.Center
            LyricAlignment.RIGHT -> TextAlign.End
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 20.dp),
        verticalArrangement = if (karaokeMode) Arrangement.Center else Arrangement.Top,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = (if (karaokeMode) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodyLarge
            }).let { baseStyle ->
                val fontSize = baseStyle.fontSize.value *
                    settings.lyricFontScale *
                    if (karaokeMode) KARAOKE_LYRIC_FONT_SCALE else 1f
                baseStyle.copy(
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * 1.34f).sp,
                )
            },
            color = Color.White.copy(alpha = 0.80f),
            fontWeight = if (settings.lyricBold) FontWeight.Bold else FontWeight.Normal,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun ProgressControls(
    playerState: PlayerUiState,
    track: Track,
    activeClip: com.qymusic.player.data.MusicClip?,
    onSeek: (Long) -> Unit,
    onSeekOutsideClip: (Long) -> Unit,
) {
    var draggingValue by remember(playerState.currentTrack?.id) {
        mutableStateOf<Float?>(null)
    }
    val duration = playerState.durationMs.coerceAtLeast(0L)
    val maxValue = duration.toFloat().coerceAtLeast(1f)
    // 拖动中只做本地预览（draggingValue），其余时间用逐帧插值的实时进度
    val livePositionMs = rememberLivePositionMs(playerState)
    val displayedValue = (draggingValue
        ?: livePositionMs.toFloat())
        .coerceIn(0f, maxValue)
    val clipRange = activeClip?.let { clip ->
        clip.startMs.toFloat().coerceIn(0f, maxValue)..clip.endMs.toFloat().coerceIn(0f, maxValue)
    }
    val audioInfo = remember(track) { formatAudioInfo(track) }
    // 缓冲/解析状态直接占用音频参数那一格：不新增高度，横竖屏都不会挤压布局。
    val phaseLabel = when (playerState.phase) {
        PlaybackPhase.RESOLVING -> stringResource(R.string.playback_resolving)
        PlaybackPhase.BUFFERING -> playerState.bufferedCachePercent?.let {
            stringResource(R.string.playback_buffered_percent, it)
        } ?: stringResource(R.string.playback_buffering)
        else -> null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ProgressSlider(
            value = displayedValue,
            valueRange = 0f..maxValue,
            clipRange = clipRange,
            enabled = duration > 0L,
            // 拖动过程中只更新显示，松手后才真正跳转，避免频繁 seek 让播放状态抖动。
            onValueChange = { draggingValue = it },
            onValueChangeFinished = {
                val target = draggingValue
                draggingValue = null
                if (
                    target != null &&
                    clipRange != null &&
                    (target < clipRange.start || target > clipRange.endInclusive)
                ) {
                    // 落点在片段范围之外：交给上层弹窗确认是否退出片段播放。
                    onSeekOutsideClip(target.toLong())
                } else {
                    target?.let { onSeek(it.toLong()) }
                }
            },
            onDragCancelled = { draggingValue = null },
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration(displayedValue.toLong()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaybackQualityBadge(quality = track.quality)
                if (phaseLabel != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else if (audioInfo.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audioInfo,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 变速 / 变调生效时给个明确标识，方便确认设置真的起作用了。
                val tuningLabel = formatPlaybackTuning(playerState.speed, playerState.pitch)
                if (tuningLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tuningLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ProgressSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    clipRange: ClosedFloatingPointRange<Float>? = null,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onDragCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackThickness = 3.dp
    val thumbRadius = 7.dp
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
    val tickWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
    val tickHeightPx = with(LocalDensity.current) { 10.dp.toPx() }

    fun valueAt(x: Float, width: Float): Float {
        val travel = (width - thumbRadiusPx * 2f).coerceAtLeast(1f)
        val ratio = ((x - thumbRadiusPx) / travel).coerceIn(0f, 1f)
        return valueRange.start + range * ratio
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange(valueAt(offset.x, size.width.toFloat()))
                    },
                    onDragEnd = { onValueChangeFinished() },
                    // 手势被系统或翻页抢走时按取消处理：没松手就不跳转。
                    onDragCancel = { onDragCancelled() },
                ) { change, _ ->
                    change.consume()
                    onValueChange(valueAt(change.position.x, size.width.toFloat()))
                }
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onValueChange(valueAt(offset.x, size.width.toFloat()))
                    onValueChangeFinished()
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(valueRange))
                        onValueChangeFinished()
                        true
                    }
                }
            },
    ) {
        val centerY = size.height / 2f
        val thickness = trackThickness.toPx()
        val radius = thumbRadius.toPx()
        val travel = (size.width - radius * 2f).coerceAtLeast(1f)
        val thumbX = radius + travel * fraction
        val cornerRadius = CornerRadius(thickness / 2f)

        fun xFor(v: Float): Float =
            radius + travel * (((v - valueRange.start) / range).coerceIn(0f, 1f))

        if (clipRange == null) {
            // 普通播放：底轨 + 已播进度。
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f),
                topLeft = Offset(0f, centerY - thickness / 2f),
                size = Size(size.width, thickness),
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, centerY - thickness / 2f),
                size = Size(thumbX.coerceAtLeast(thickness), thickness),
                cornerRadius = cornerRadius,
            )
        } else {
            // 片段播放：片段区间激活（已播点亮、未播半亮），区间外整体置灰。
            val clipStartX = xFor(clipRange.start)
            val clipEndX = xFor(clipRange.endInclusive)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(0f, centerY - thickness / 2f),
                size = Size(size.width, thickness),
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.38f),
                topLeft = Offset(clipStartX, centerY - thickness / 2f),
                size = Size((clipEndX - clipStartX).coerceAtLeast(thickness), thickness),
                cornerRadius = cornerRadius,
            )
            val playedEnd = thumbX.coerceAtMost(clipEndX)
            if (playedEnd > clipStartX) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(clipStartX, centerY - thickness / 2f),
                    size = Size(playedEnd - clipStartX, thickness),
                    cornerRadius = cornerRadius,
                )
            }
            // 片段起止刻度：拖动时能看清可停留的范围。
            val tickCorner = CornerRadius(tickWidthPx / 2f)
            listOf(clipStartX, clipEndX).forEach { tickX ->
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f),
                    topLeft = Offset(tickX - tickWidthPx / 2f, centerY - tickHeightPx / 2f),
                    size = Size(tickWidthPx, tickHeightPx),
                    cornerRadius = tickCorner,
                )
            }
        }
        drawCircle(
            color = Color.White,
            radius = radius,
            center = Offset(thumbX, centerY),
        )
    }
}

@Composable
private fun PlaybackQualityBadge(quality: String) {
    if (quality.isBlank()) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.20f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = quality,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

@Composable
private fun PrimaryControls(
    playerState: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
        }
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.18f),
                contentColor = Color.White,
            ),
        ) {
            PlayPauseMorphIcon(
                showPause = playerState.showPauseIcon,
                modifier = Modifier.size(54.dp),
            )
        }
        IconButton(
            onClick = onNext,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.next),
                modifier = Modifier.size(40.dp),
                tint = Color.White,
            )
        }
    }
}

/** 播放 ↔ 暂停的变形动画时长。 */
private const val PLAY_PAUSE_MORPH_MS = 260

/**
 * 播放/暂停的路径变形图标：同一组 8 个顶点在「播放三角」与「暂停双条」之间按进度插值，
 * 全程只改顶点坐标——不切换图片、不参与透明度交叉。
 *
 * 顶点分两个四边形 subpath：播放态左块是三角左半、右块末两点重合成右尖（拼成完整三角）；
 * 暂停态两块分别张开为左、右竖条。
 */
@Composable
private fun PlayPauseMorphIcon(
    showPause: Boolean,
    modifier: Modifier = Modifier,
) {
    val morph by animateFloatAsState(
        targetValue = if (showPause) 1f else 0f,
        animationSpec = tween(PLAY_PAUSE_MORPH_MS, easing = FastOutSlowInEasing),
        label = "play-pause-morph",
    )
    val description = stringResource(if (showPause) R.string.pause else R.string.play)
    val color = LocalContentColor.current
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        drawPlayPauseMorph(morph, color = color)
    }
}

private fun DrawScope.drawPlayPauseMorph(t: Float, color: Color) {
    val s = minOf(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f

    // 暂停态：两根竖条（宽 0.16s、高 0.60s，中心距 0.27s）。
    val barHalfW = s * 0.08f
    val barHalfH = s * 0.30f
    val barHalfSpan = s * 0.135f
    val lBar = cx - barHalfSpan
    val rBar = cx + barHalfSpan

    // 播放态：实心三角，高 0.64s；整体右移补偿三角质心偏左，保持视觉居中。
    val shift = s * 0.0467f
    val triLeft = cx - s * 0.22f + shift
    val triRight = cx + s * 0.30f + shift
    val triHalfH = s * 0.32f
    val midX = (triLeft + triRight) / 2f
    // 斜边上中点离水平中线的竖直距离（上/下边中点）。
    val midHalfH = triHalfH / 2f

    fun lerp(play: Float, pause: Float) = play + (pause - play) * t

    val path = Path()
    // 左 subpath：左上 → 左下 → 右下 → 右上。
    path.moveTo(lerp(triLeft, lBar - barHalfW), lerp(cy - triHalfH, cy - barHalfH))
    path.lineTo(lerp(triLeft, lBar - barHalfW), lerp(cy + triHalfH, cy + barHalfH))
    path.lineTo(lerp(midX, lBar + barHalfW), lerp(cy + midHalfH, cy + barHalfH))
    path.lineTo(lerp(midX, lBar + barHalfW), lerp(cy - midHalfH, cy - barHalfH))
    path.close()
    // 右 subpath：上中 → 下中 → 右尖(下) → 右尖(上)；播放态末两点重合为尖角。
    path.moveTo(lerp(midX, rBar - barHalfW), lerp(cy - midHalfH, cy - barHalfH))
    path.lineTo(lerp(midX, rBar - barHalfW), lerp(cy + midHalfH, cy + barHalfH))
    path.lineTo(lerp(triRight, rBar + barHalfW), lerp(cy, cy + barHalfH))
    path.lineTo(lerp(triRight, rBar + barHalfW), lerp(cy, cy - barHalfH))
    path.close()
    drawPath(path, color, style = Fill)
}

@Composable
private fun SecondaryControls(
    playerState: PlayerUiState,
    actions: List<PlayerQuickAction>,
    onAction: (PlayerQuickAction) -> Unit,
    onOpenMore: () -> Unit,
    playlistActive: Boolean,
    addToPlaylistActive: Boolean,
    equalizerActive: Boolean,
    defineClipActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { action ->
            when (action) {
                PlayerQuickAction.PLAYBACK_MODE -> IconButton(
                    onClick = { onAction(PlayerQuickAction.PLAYBACK_MODE) },
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            if (playerState.playbackMode != PlaybackMode.SEQUENTIAL) {
                                Color.White.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        ),
                ) {
                    Icon(
                        imageVector = when (playerState.playbackMode) {
                            PlaybackMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                            PlaybackMode.SHUFFLE -> Icons.Rounded.Shuffle
                            PlaybackMode.SEQUENTIAL -> Icons.Outlined.Repeat
                        },
                        contentDescription = stringResource(playerState.playbackMode.labelRes()),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.QUEUE -> IconButton(
                    onClick = { onAction(PlayerQuickAction.QUEUE) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = if (playlistActive) {
                            Icons.AutoMirrored.Rounded.QueueMusic
                        } else {
                            Icons.AutoMirrored.Outlined.QueueMusic
                        },
                        contentDescription = stringResource(R.string.play_queue),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.ADD_TO_PLAYLIST -> IconButton(
                    onClick = { onAction(PlayerQuickAction.ADD_TO_PLAYLIST) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = if (addToPlaylistActive) {
                            Icons.AutoMirrored.Rounded.PlaylistAdd
                        } else {
                            Icons.AutoMirrored.Outlined.PlaylistAdd
                        },
                        contentDescription = stringResource(R.string.add_to_playlist),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.KARAOKE -> IconButton(
                    onClick = { onAction(PlayerQuickAction.KARAOKE) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RecordVoiceOver,
                        contentDescription = stringResource(R.string.karaoke),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.EQUALIZER -> IconButton(
                    onClick = { onAction(PlayerQuickAction.EQUALIZER) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = if (equalizerActive) {
                            Icons.Rounded.Equalizer
                        } else {
                            Icons.Outlined.Equalizer
                        },
                        contentDescription = stringResource(R.string.equalizer),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.LYRIC_SETTINGS -> IconButton(
                    onClick = { onAction(PlayerQuickAction.LYRIC_SETTINGS) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = stringResource(R.string.lyrics_settings),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.BACKGROUND_SETTINGS -> IconButton(
                    onClick = { onAction(PlayerQuickAction.BACKGROUND_SETTINGS) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wallpaper,
                        contentDescription = stringResource(R.string.settings_background),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.SLEEP_TIMER -> IconButton(
                    onClick = { onAction(PlayerQuickAction.SLEEP_TIMER) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = stringResource(R.string.sleep_timer),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.MAGIC -> IconButton(
                    onClick = { onAction(PlayerQuickAction.MAGIC) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = stringResource(R.string.magic),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.CLIPS -> IconButton(
                    onClick = { onAction(PlayerQuickAction.CLIPS) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = stringResource(R.string.clips_pick_title),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.DEFINE_CLIP -> IconButton(
                    onClick = { onAction(PlayerQuickAction.DEFINE_CLIP) },
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(
                            if (defineClipActive) {
                                Color.White.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            },
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = stringResource(R.string.player_action_define_clip),
                        modifier = Modifier.size(30.dp),
                        tint = if (defineClipActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    )
                }

                PlayerQuickAction.COMMENT -> IconButton(
                    onClick = { onAction(PlayerQuickAction.COMMENT) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Chat,
                        contentDescription = stringResource(R.string.comment_title),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }

                PlayerQuickAction.COVER_STYLE -> IconButton(
                    onClick = { onAction(PlayerQuickAction.COVER_STYLE) },
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Photo,
                        contentDescription = stringResource(R.string.cover_style),
                        modifier = Modifier.size(30.dp),
                        tint = Color.White,
                    )
                }
            }
        }

        // 更多：底部弹层网格展示全部功能，长按拖动排序
        IconButton(
            onClick = onOpenMore,
            modifier = Modifier.size(58.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = stringResource(R.string.playback_more),
                modifier = Modifier.size(30.dp),
                tint = Color.White,
            )
        }
    }
}

/** 横屏歌词模式：左侧封面（双击退出），右侧滚动歌词；进入时封面滑入、歌词随后淡入。 */
@Composable
private fun LandscapeLyricsPlayer(
    artwork: Bitmap?,
    lyricsState: LyricsUiState,
    positionMs: Long,
    settings: AppSettings,
    blurredCover: Bitmap,
    backgroundMotion: Float,
    backgroundMotionEnabled: Boolean,
    onExit: () -> Unit,
    onPlayFrom: (Long) -> Unit,
) {
    val exitDescription = stringResource(R.string.exit_landscape_mode)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // 入场进度：0 -> 1。封面先滑入，歌词按比例延后淡入。
    val enterProgress = remember { Animatable(0f) }
    // 退场进度：1 -> 0。双击封面先淡出，再真正离开横屏模式。
    val exitProgress = remember { Animatable(1f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        enterProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = LANDSCAPE_ENTER_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    val requestExit: () -> Unit = {
        if (!leaving) {
            leaving = true
            coroutineScope.launch {
                exitProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = LANDSCAPE_EXIT_MS),
                )
                onExit()
            }
        }
    }

    val lyricsDelayFraction = (
        LANDSCAPE_LYRICS_DELAY_MS.toFloat() / LANDSCAPE_ENTER_MS.toFloat()
        ).coerceIn(0f, 0.9f)
    val lyricsShiftPx = with(density) { LANDSCAPE_LYRICS_ENTER_SHIFT.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // 背景随入场进度淡入，与竖屏播放页做交叉渐变，避免整屏硬切。
                    alpha = (enterProgress.value * exitProgress.value).coerceIn(0f, 1f)
                },
        ) {
            OpaqueCoverCrossfade(
                blurredCover = blurredCover,
                backgroundMotion = backgroundMotion,
                backgroundMotionEnabled = backgroundMotionEnabled,
                label = "landscape-lyrics-backdrop",
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.42f),
                contentAlignment = Alignment.Center,
            ) {
                val coverSize = minOf(maxWidth, maxHeight)
                val coverShiftPx = with(density) {
                    (coverSize * LANDSCAPE_COVER_ENTER_SHIFT).toPx()
                }
                // 双击封面退出横屏模式。
                CoverArt(
                    bitmap = artwork,
                    modifier = Modifier
                        .size(coverSize)
                        .graphicsLayer {
                            // 在绘制阶段读取进度，避免逐帧重组右侧歌词列表。
                            val reveal = (enterProgress.value * exitProgress.value)
                                .coerceIn(0f, 1f)
                            // 封面从视口中部方向滑到左侧，并轻微放大到最终尺寸。
                            alpha = reveal
                            translationX = (1f - reveal) * coverShiftPx
                            val coverScale = 0.92f + 0.08f * reveal
                            scaleX = coverScale
                            scaleY = coverScale
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { requestExit() })
                        }
                        .semantics { contentDescription = exitDescription },
                )
            }
            LyricsPanel(
                lyricsState = lyricsState,
                positionMs = positionMs,
                settings = settings,
                animate = true,
                onPlayFrom = onPlayFrom,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        // 歌词比封面晚一点淡入，并带一点横向位移，形成层次。
                        val reveal = (enterProgress.value * exitProgress.value)
                            .coerceIn(0f, 1f)
                        val lyricsReveal = (
                            (reveal - lyricsDelayFraction) /
                                (1f - lyricsDelayFraction)
                            ).coerceIn(0f, 1f)
                        alpha = lyricsReveal
                        translationX = (1f - lyricsReveal) * lyricsShiftPx
                    },
            )
        }
    }
}

/**
 * 播放页 / 歌词页左右都能一直滑：页码取一个大值、按奇偶映射到两种页面，
 * 起点放正中间，这样往左往右都不会撞到尽头，可以来回循环。
 */
/** 定义片段：起始默认向后延续的时长。 */
private const val DEFINE_CLIP_DEFAULT_SPAN_MS = 30_000L
/** 播放页「更多」菜单底色：贴近深色封面的暗面板，留一点透明度透出封面氛围。 */
private val PLAYER_MENU_BACKGROUND = Color(0xF217171C)
/** 功能按钮网格弹层的列数。 */
private const val GRID_COLUMNS = 3
private const val PAGER_PAGE_COUNT = 100_000
private const val INITIAL_PAGER_PAGE = 50_001
private val MAGIC_PAGE_FALLBACK_HEIGHT = 360.dp
private val MAGIC_PAGE_MIN_HEIGHT = 280.dp
private val MAGIC_PAGE_MAX_HEIGHT = 420.dp
private const val BACKGROUND_MOTION_PERIOD_MS = 8_000L
private const val BACKGROUND_MOTION_SMOOTH_MS = 220
private const val KARAOKE_LAST_SEGMENT_DURATION_MS = 600L
/** 略短于进度刷新间隔（200ms），补间能在下一帧进度到来前收尾。 */
private const val KARAOKE_POSITION_ANIMATION_MS = 180
/** 逐字高亮的量化步长，越小越丝滑，越大越省电。 */
private const val KARAOKE_POSITION_STEP_MS = 50
/** 词内扫亮软边占词宽的比例，让亮暗分界不是一条硬切线。 */
private const val WORD_SWEEP_FEATHER_RATIO = 0.18f
/** 沿 ContextWrapper 链找到宿主 Activity，用于横屏沉浸模式控制系统栏。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 歌曲文件的 MIME 类型（播放页分享用，与媒体库页的分享保持同一映射）。 */
private fun currentTrackMimeType(track: Track): String = when (track.extension) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "m4a", "mp4" -> "audio/mp4"
    "ogg", "opus" -> "audio/ogg"
    "wav" -> "audio/wav"
    "aac" -> "audio/aac"
    "ape" -> "audio/ape"
    else -> "audio/*"
}

/** 交换列表中的两项，用于快捷按钮排序。 */
private fun <T> List<T>.swapAt(first: Int, second: Int): List<T> {
    if (first !in indices || second !in indices) return this
    return toMutableList().also { list ->
        val moved = list[first]
        list[first] = list[second]
        list[second] = moved
    }
}

private fun PlayerQuickAction.labelRes(): Int = when (this) {
    PlayerQuickAction.LYRIC_SETTINGS -> R.string.lyrics_settings
    PlayerQuickAction.BACKGROUND_SETTINGS -> R.string.settings_background
    PlayerQuickAction.SLEEP_TIMER -> R.string.sleep_timer
    PlayerQuickAction.MAGIC -> R.string.magic
    PlayerQuickAction.CLIPS -> R.string.clips_pick_title
    PlayerQuickAction.DEFINE_CLIP -> R.string.player_action_define_clip
    PlayerQuickAction.PLAYBACK_MODE -> R.string.player_action_playback_mode
    PlayerQuickAction.QUEUE -> R.string.play_queue
    PlayerQuickAction.ADD_TO_PLAYLIST -> R.string.add_to_playlist
    PlayerQuickAction.EQUALIZER -> R.string.equalizer
    PlayerQuickAction.KARAOKE -> R.string.karaoke
    PlayerQuickAction.COVER_STYLE -> R.string.cover_style
    PlayerQuickAction.COMMENT -> R.string.comment_title
}

/** 同级功能在菜单 / 溢出列表里的图标（播放模式的动态图标在按钮处单独处理）。 */
private fun PlayerQuickAction.icon(): ImageVector = when (this) {
    PlayerQuickAction.LYRIC_SETTINGS -> Icons.Rounded.Lyrics
    PlayerQuickAction.BACKGROUND_SETTINGS -> Icons.Rounded.Wallpaper
    PlayerQuickAction.SLEEP_TIMER -> Icons.Rounded.Timer
    PlayerQuickAction.MAGIC -> Icons.Rounded.AutoAwesome
    PlayerQuickAction.CLIPS -> Icons.Rounded.ContentCut
    PlayerQuickAction.DEFINE_CLIP -> Icons.Rounded.BookmarkAdd
    PlayerQuickAction.PLAYBACK_MODE -> Icons.Rounded.Repeat
    PlayerQuickAction.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
    PlayerQuickAction.ADD_TO_PLAYLIST -> Icons.AutoMirrored.Rounded.PlaylistAdd
    PlayerQuickAction.EQUALIZER -> Icons.Rounded.Equalizer
    PlayerQuickAction.KARAOKE -> Icons.Rounded.RecordVoiceOver
    PlayerQuickAction.COVER_STYLE -> Icons.Rounded.Photo
    PlayerQuickAction.COMMENT -> Icons.Rounded.Chat
}

/** 播放列表定位时，正在播放的那首上面留几行做上下文。 */
private const val QUEUE_SCROLL_CONTEXT_ITEMS = 2
/** 切歌时背景 / 封面 / 歌曲信息的渐变时长。 */
private const val TRACK_CHANGE_FADE_MS = 520
/** 进入横屏歌词模式的总时长：封面先滑入，歌词随后淡入。 */
private const val LANDSCAPE_ENTER_MS = 620
/** 歌词相对封面进入的延迟，做出"慢慢渐变出来"的层次。 */
private const val LANDSCAPE_LYRICS_DELAY_MS = 200
/** 退出横屏歌词模式的淡出时长。 */
private const val LANDSCAPE_EXIT_MS = 240
/** 封面入场时从视口中部方向滑入的距离，相对封面尺寸。 */
private const val LANDSCAPE_COVER_ENTER_SHIFT = 0.45f
private val LANDSCAPE_LYRICS_ENTER_SHIFT = 42.dp
/**
 * 歌词距离梯度档位：以当前句为视觉中心，按行距给出形变 / 透明度 / 模糊。
 * scale 以当前句的视觉大小为 1.0（排版字号已含 LYRIC_ACTIVE_SCALE），
 * 普通行即 LYRIC_INACTIVE_SCALE ≈ 0.806。
 */
private data class LyricTier(
    val scaleX: Float,
    val scaleY: Float,
    val alpha: Float,
    /** 相对用户设置的基准模糊的权重（0 清晰 → 1 打满）。 */
    val blurWeight: Float,
)

/** 当前行：无模糊、全透明度。 */
private val LYRIC_TIER_ACTIVE = LyricTier(1f, 1f, 1f, 0f)
/** 下一句：约 1.1 倍横向拉伸 + 1.05 倍字号放大，预告即将进入高亮。 */
private val LYRIC_TIER_NEXT = LyricTier(0.89f, 0.85f, 0.85f, 0.25f)
/** 上一句：反向横向收缩 + 字号缩小，退回普通行。 */
private val LYRIC_TIER_PREV = LyricTier(0.78f, 0.82f, 0.85f, 0.25f)
/** 次邻行：形变与弱化幅度按距离衰减。 */
private val LYRIC_TIER_NEAR = LyricTier(0.83f, 0.83f, 0.72f, 0.6f)
/** 更远的行：普通大小，透明度最低、模糊打满，焦点留给当前句。 */
private val LYRIC_TIER_FAR = LyricTier(LYRIC_INACTIVE_SCALE, LYRIC_INACTIVE_SCALE, 0.6f, 1f)

private fun lyricTier(distance: Int): LyricTier = when (distance) {
    0 -> LYRIC_TIER_ACTIVE
    1 -> LYRIC_TIER_NEXT
    -1 -> LYRIC_TIER_PREV
    2, -2 -> LYRIC_TIER_NEAR
    else -> LYRIC_TIER_FAR
}

/** 距离梯度弹簧：轻微欠阻尼，换行时整页歌词随节拍"呼吸舒展"、无生硬跳变。 */
private const val LYRIC_BREATH_DAMPING = 0.92f
private const val LYRIC_BREATH_STIFFNESS = 90f
/** 逐行滚动定位：固定时长的标准减速曲线，切行干脆平滑，没有弹簧拖拽感。 */
private const val LYRIC_SCROLL_DURATION_MS = 320
/** 逐行滚动定位最多逼近轮数，用来消化首次排版还没定下来的行高。 */
private const val LYRIC_SPRING_SCROLL_PASSES = 3
/** 逐行滚动定位复用的动画规格。 */
private val lyricScrollSpec: AnimationSpec<Float> = tween(
    durationMillis = LYRIC_SCROLL_DURATION_MS,
    easing = FastOutSlowInEasing,
)
/** 歌词未播放部分的白色透明度（已播放部分是纯白）。 */
private const val LYRIC_UNSUNG_ALPHA = 0.45f
private const val LYRIC_ACTIVE_SCALE = 1.24f
private const val LYRIC_INACTIVE_SCALE = 1f / LYRIC_ACTIVE_SCALE
private const val KARAOKE_LYRIC_FONT_SCALE = 0.95f
private const val LYRIC_WRAPPED_LINE_HEIGHT_RATIO = 1.18f
private const val STAR_LYRIC_WRAPPED_LINE_HEIGHT_RATIO = 1.78f
private const val STAR_ROLL_DEGREES = 360f
private const val STAR_ICON_MAX_ALPHA = 0.96f
private const val STAR_ICON_MIN_ALPHA = 0.58f
private val STAR_PULSE_RADIANS = (2.0 * PI).toFloat()
private const val STAR_IDLE_ROLL_PERIOD_MS = 3000
private const val PLAYLIST_SHEET_HEIGHT_FRACTION = 0.72f
private const val EQUALIZER_SHEET_HEIGHT_FRACTION = 0.66f
private const val ADD_TO_PLAYLIST_SHEET_HEIGHT_FRACTION = 0.5f
private val LYRIC_LINE_HORIZONTAL_PADDING = 14.dp
private val LYRIC_LINE_VERTICAL_PADDING = 15.dp
private val STAR_LYRIC_LINE_VERTICAL_PADDING = 6.dp
private val STAR_LANE_HEIGHT = 30.dp
private val STAR_ICON_SIZE = 22.dp
private val STAR_ICON_GAP = (-3).dp
private val STAR_ICON_JUMP = 4.dp
private val PLAYLIST_SHEET_FALLBACK_HEIGHT = 520.dp
private val EQUALIZER_SHEET_FALLBACK_HEIGHT = 520.dp
private val ADD_TO_PLAYLIST_SHEET_FALLBACK_HEIGHT = 380.dp
private val SLEEP_TIMER_MINUTES = listOf(10, 20, 30, 60)
private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private val PITCH_PRESETS = listOf(-12f, -7f, -5f, 0f, 5f, 7f, 12f)
private val PLAYBACK_MENU_COLOR = Color(0xFF23262B)

private data class StarMotionAnchors(
    val start: Offset,
    val end: Offset,
    val startsVisualLine: Boolean,
    val endsVisualLine: Boolean,
)

/** 奇数页是播放页、偶数页是歌词页：从播放页往右一格就是歌词页。 */
private fun nearestLyricsPage(currentPage: Int): Int =
    if (currentPage % 2 == 0) currentPage else currentPage + 1

/** 定义片段悬浮按钮层：设为起始 / 设为末尾 / 取消 / 确定，歌曲继续正常播放。 */
@Composable
private fun DefineClipOverlay(
    startMs: Long,
    endMs: Long,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCut,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(
                        R.string.define_clip_range,
                        formatDuration(startMs),
                        formatDuration(endMs),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
        DefineClipButton(
            text = stringResource(R.string.define_clip_set_start),
            onClick = onSetStart,
        )
        DefineClipButton(
            text = stringResource(R.string.define_clip_set_end),
            onClick = onSetEnd,
        )
        DefineClipButton(
            text = stringResource(R.string.cancel),
            onClick = onCancel,
        )
        DefineClipButton(
            text = stringResource(R.string.confirm),
            onClick = onConfirm,
            emphasized = true,
        )
    }
}

@Composable
private fun DefineClipButton(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Black.copy(alpha = 0.55f)
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasized) MaterialTheme.colorScheme.onPrimary else Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        )
    }
}

/** 定义片段确认弹窗：选择类型、填写标签后保存片段。 */
@Composable
private fun DefineClipDialog(
    startMs: Long,
    endMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (type: String, tag: String) -> Unit,
) {
    var type by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.define_clip_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(
                        R.string.define_clip_range,
                        formatDuration(startMs),
                        formatDuration(endMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.clip_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CLIP_TYPES.forEach { candidate ->
                        val selected = type == candidate
                        Text(
                            text = candidate,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { type = if (selected) "" else candidate }
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.clip_tag)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(type, tag) }) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

/** 歌曲评论的内置小表情：点击追加到输入框末尾。 */
private val COMMENT_EMOJIS = listOf(
    "😀", "😂", "🥰", "😭", "😮", "🤔",
    "👍", "👏", "🔥", "❤️", "🎶", "💤",
)

/** 评论线程：一条顶级评论 + 它下面的全部回复（回复统一挂顶级，时间正序）。 */
private data class CommentThread(
    val comment: TrackComment,
    val replies: List<TrackComment>,
)

/** 顶级评论（新→旧），回复归到各自线程（旧→新）。 */
private fun buildCommentThreads(comments: List<TrackComment>): List<CommentThread> {
    val byId = comments.associateBy(TrackComment::id)
    fun rootIdOf(comment: TrackComment): Long {
        var current = comment
        var guard = 0
        while (current.parentId != null && guard++ < 32) {
            current = byId[current.parentId] ?: break
        }
        return current.id
    }
    val repliesByRoot = comments.filter { it.parentId != null }
        .groupBy(::rootIdOf)
        .mapValues { (_, replies) -> replies.sortedBy(TrackComment::createdAtMs) }
    return comments.filter { it.parentId == null }
        .sortedByDescending(TrackComment::createdAtMs)
        .map { CommentThread(it, repliesByRoot[it.id].orEmpty()) }
}

/** 歌曲评论页：全屏卡片式评论列表，底部输入条支持新增与回复（回复挂到线程顶级）。 */
@Composable
private fun TrackCommentPage(
    comments: List<TrackComment>,
    blurredCover: Bitmap,
    onBack: () -> Unit,
    onAdd: (String, String?, Long?) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val threads = remember(comments) { buildCommentThreads(comments) }
    val totalCount = threads.sumOf { 1 + it.replies.size }
    var replyTo by remember { mutableStateOf<TrackComment?>(null) }
    var draft by remember { mutableStateOf("") }
    var pickedEmoji by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 页面用深色系主题：输入框、占位文字在封面背景上以白色系渲染。
    val pageScheme = MaterialTheme.colorScheme.copy(
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White.copy(alpha = 0.78f),
        outline = Color.White.copy(alpha = 0.30f),
    )

    MaterialTheme(colorScheme = pageScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            CoverBackdrop(bitmap = blurredCover)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.22f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(24.dp),
                            tint = Color.White,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.comment_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = stringResource(R.string.comment_total, totalCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (threads.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = stringResource(R.string.comment_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.62f),
                                modifier = Modifier.padding(vertical = 40.dp),
                            )
                        }
                    }
                    items(items = threads, key = { "thread-${it.comment.id}" }) { thread ->
                        CommentThreadCard(
                            thread = thread,
                            onReply = { replyTo = it },
                            onDelete = onDelete,
                        )
                    }
                }
                CommentInputBar(
                    draft = draft,
                    onDraftChange = { draft = it },
                    pickedEmoji = pickedEmoji,
                    onEmojiChange = { pickedEmoji = it },
                    replyTo = replyTo,
                    onCancelReply = { replyTo = null },
                    canSend = draft.isNotBlank() || pickedEmoji != null,
                    onSend = {
                        val wasReply = replyTo != null
                        if (draft.isNotBlank() || pickedEmoji != null) {
                            onAdd(draft, pickedEmoji, replyTo?.id)
                            draft = ""
                            pickedEmoji = null
                            replyTo = null
                            if (!wasReply) {
                                // 新增顶级评论排最前，滚回顶部让它立即可见。
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 评论线程卡片：顶级评论 + 缩进的回复小卡片。 */
@Composable
private fun CommentThreadCard(
    thread: CommentThread,
    onReply: (TrackComment) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CommentCard(comment = thread.comment, onReply = onReply, onDelete = onDelete)
        if (thread.replies.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 40.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                thread.replies.forEach { reply ->
                    CommentCard(
                        comment = reply,
                        onReply = onReply,
                        onDelete = onDelete,
                        compact = true,
                    )
                }
            }
        }
    }
}

/** 单张评论卡片：头像 / 名称 / 时间 / 内容，底部回复操作，右上删除。 */
@Composable
private fun CommentCard(
    comment: TrackComment,
    onReply: (TrackComment) -> Unit,
    onDelete: (Long) -> Unit,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (compact) 0.06f else 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CommentAvatar(comment = comment, compact = compact)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.comment_author_me),
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    color = Color.White,
                )
                Text(
                    text = formatCommentTime(comment.createdAtMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
            IconButton(
                onClick = { onDelete(comment.id) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.comment_delete),
                    modifier = Modifier.size(16.dp),
                    tint = Color.White.copy(alpha = 0.55f),
                )
            }
        }
        Text(
            text = comment.text.ifBlank { comment.emoji.orEmpty() },
            style = if (compact) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.comment_reply),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onReply(comment) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 评论头像：表情作头像，没有表情时用音符占位。 */
@Composable
private fun CommentAvatar(comment: TrackComment, compact: Boolean = false) {
    val size = if (compact) 26.dp else 34.dp
    val fontSize = if (compact) 13.sp else 16.sp
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = comment.emoji ?: "🎵", fontSize = fontSize)
    }
}

/** 评论输入条：回复指示 + 表情行 + 输入框 / 发送。 */
@Composable
private fun CommentInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    pickedEmoji: String?,
    onEmojiChange: (String?) -> Unit,
    replyTo: TrackComment?,
    onCancelReply: () -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (replyTo != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.comment_replying) + "：" +
                        replyTo.text.ifBlank { replyTo.emoji.orEmpty() }.take(16),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = onCancelReply,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cancel),
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.72f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            COMMENT_EMOJIS.forEach { emoji ->
                val selected = pickedEmoji == emoji
                Text(
                    text = emoji,
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onEmojiChange(if (selected) null else emoji) }
                        .padding(4.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(
                            if (replyTo != null) {
                                R.string.comment_reply_hint
                            } else {
                                R.string.comment_hint
                            },
                        ),
                    )
                },
                singleLine = true,
                maxLines = 3,
            )
            TextButton(onClick = onSend, enabled = canSend) {
                Text(
                    text = stringResource(R.string.comment_send),
                    color = if (canSend) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.4f)
                    },
                )
            }
        }
    }
}

private fun formatCommentTime(createdAtMs: Long): String {
    val formatter = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(createdAtMs))
}

/** 封面样式选择：点击立即生效，弹层保持打开方便连续对比，下滑关闭。 */

/** 封面样式选择：点击立即生效，弹层保持打开方便连续对比，下滑关闭。 */
@Composable
private fun CoverStyleSheetContent(
    currentStyle: CoverStyle,
    onStyleChange: (CoverStyle) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.cover_style_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        CoverStyle.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { onStyleChange(option) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = option.coverStyleIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(option.coverStyleLabel),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                if (option == currentStyle) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * 功能按钮网格：点击直接使用并关闭弹层；长按拖动调整顺序，排序前 4 个显示在播放页。
 * 列数随弹层宽度自适应；拖动时被挤开的图标按弹簧平滑滑到新槽位。
 */
@Composable
private fun PlayerActionsGridSheetContent(
    actions: List<PlayerQuickAction>,
    onActionsChange: (List<PlayerQuickAction>) -> Unit,
    onActionClick: (PlayerQuickAction) -> Unit,
) {
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val cellRects = remember { mutableMapOf<Int, Rect>() }
    val cellTranslations = remember {
        mutableMapOf<PlayerQuickAction, Animatable<Offset, AnimationVector2D>>()
    }
    val currentActions by rememberUpdatedState(actions)
    val scope = rememberCoroutineScope()

    // 重排：把每个换位的功能从旧槽位平滑滑到新槽位（被挤出去的手感）
    fun reorder(from: Int, to: Int) {
        val current = currentActions
        if (from !in current.indices || to !in current.indices || from == to) return
        val moved = current.toMutableList()
        moved.add(to, moved.removeAt(from))
        moved.forEachIndexed { newIndex, action ->
            val oldIndex = current.indexOf(action)
            if (oldIndex == newIndex || oldIndex == draggingIndex) return@forEachIndexed
            val oldCenter = cellRects[oldIndex]?.center
            val newCenter = cellRects[newIndex]?.center
            if (oldCenter != null && newCenter != null) {
                scope.launch {
                    val anim = cellTranslations.getOrPut(action) {
                        Animatable(Offset.Zero, Offset.VectorConverter)
                    }
                    anim.snapTo(Offset(oldCenter.x - newCenter.x, oldCenter.y - newCenter.y))
                    anim.animateTo(
                        Offset.Zero,
                        spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
                    )
                }
            }
        }
        onActionsChange(moved)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / 104.dp).toInt().coerceIn(3, 5)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.player_actions_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            actions.chunked(columns).forEachIndexed { rowIndex, rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowActions.forEachIndexed { colIndex, action ->
                        val index = rowIndex * columns + colIndex
                        val isDragging = draggingIndex == index
                        val shift = cellTranslations[action]?.value ?: Offset.Zero
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isDragging) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationX = dragOffset.x
                                        translationY = dragOffset.y
                                        scaleX = 1.1f
                                        scaleY = 1.1f
                                    } else {
                                        translationX = shift.x
                                        translationY = shift.y
                                    }
                                }
                                .onGloballyPositioned { cellRects[index] = it.boundsInRoot() }
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingIndex = index
                                            dragOffset = Offset.Zero
                                            android.util.Log.d("QYDrag", "start idx=$index")
                                        },
                                        onDragEnd = {
                                            android.util.Log.d("QYDrag", "end")
                                            draggingIndex = -1
                                            dragOffset = Offset.Zero
                                        },
                                        onDragCancel = {
                                            draggingIndex = -1
                                            dragOffset = Offset.Zero
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (draggingIndex < 0) {
                                                return@detectDragGesturesAfterLongPress
                                            }
                                            dragOffset += amount
                                            val from = draggingIndex
                                            val fromRect = cellRects[from]
                                                ?: return@detectDragGesturesAfterLongPress
                                            val draggedCenter = fromRect.center + dragOffset
                                            // 换位条件：拖拽中心到目标槽位中心的距离
                                            // 小于到当前槽位的 80%（20% 迟滞带），
                                            // 避免在相邻槽位边界处来回换、拖不动。
                                            var nearest = -1
                                            var nearestDist = Float.MAX_VALUE
                                            var currentDist = Float.MAX_VALUE
                                            cellRects.forEach { (key, rect) ->
                                                val dist = (
                                                    rect.center - draggedCenter
                                                    ).getDistance()
                                                if (key == from) {
                                                    currentDist = dist
                                                } else if (dist < nearestDist) {
                                                    nearestDist = dist
                                                    nearest = key
                                                }
                                            }
                                            if (
                                                nearest >= 0 &&
                                                nearestDist < currentDist * 0.8f
                                            ) {
                                                android.util.Log.d(
                                                    "QYDrag",
                                                    "swap from=$from to=$nearest cur=$currentDist near=$nearestDist",
                                                )
                                                reorder(from, nearest)
                                                // 交换后保持拖拽项跟随手指
                                                dragOffset += fromRect.center -
                                                    (cellRects[nearest] ?: fromRect).center
                                                draggingIndex = nearest
                                            }
                                        },
                                    )
                                }
                                .clickable { onActionClick(action) }
                                .padding(vertical = 12.dp),
                        ) {
                            Icon(
                                imageVector = action.icon(),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(action.labelRes()),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(columns - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
