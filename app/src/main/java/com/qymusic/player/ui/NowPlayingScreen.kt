package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.qymusic.player.R
import com.qymusic.player.playback.VocalSplitMode
import com.qymusic.player.playback.RotationUiState
import com.qymusic.player.data.AppSettings
import com.qymusic.player.data.LyricAlignment
import com.qymusic.player.data.LyricWordAnimationStyle
import com.qymusic.player.data.SettingsStore
import com.qymusic.player.data.Track
import com.qymusic.player.data.UserPlaylist
import com.qymusic.player.lyrics.LyricLine
import com.qymusic.player.lyrics.LrcParser
import com.qymusic.player.lyrics.Lyrics
import com.qymusic.player.playback.EqualizerUiState
import com.qymusic.player.playback.MAX_PITCH_SEMITONES
import com.qymusic.player.playback.MAX_PLAYBACK_SPEED
import com.qymusic.player.playback.MIN_PITCH_SEMITONES
import com.qymusic.player.playback.MIN_PLAYBACK_SPEED
import com.qymusic.player.playback.PlaybackMode
import com.qymusic.player.playback.ReverbPreset
import com.qymusic.player.playback.semitonesOfPitchFactor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    playerState: PlayerUiState,
    lyricsState: LyricsUiState,
    lyricOffsetMs: Long,
    settings: AppSettings,
    playlists: List<UserPlaylist>,
    equalizerState: EqualizerUiState,
    sleepTimer: SleepTimerState,
    artwork: Bitmap?,
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
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
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
                            onSeek(positionMs)
                            if (!playerState.showPauseIcon) {
                                onTogglePlayPause()
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
                    onCoverClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                nearestLyricsPage(pagerState.currentPage),
                            )
                        }
                    },
                    onSelectQueueIndex = onSelectQueueIndex,
                    onSetTrackInPlaylist = onSetTrackInPlaylist,
                    onCreatePlaylist = onCreatePlaylist,
                    onEqualizerEnabledChange = onEqualizerEnabledChange,
                    onEqualizerPresetChange = onEqualizerPresetChange,
                    onEqualizerBandChange = onEqualizerBandChange,
                    onSeek = onSeek,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackPage(
    playerState: PlayerUiState,
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
    onCoverClick: () -> Unit,
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
    var playlistName by remember(track.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val playlistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val equalizerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sleepTimerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val magicSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lyricSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val backgroundSettingsSheetState =
        rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                    sleepTimer = sleepTimer,
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenMagic = { showMagicSheet = true },
                    onOpenLyricSettings = { showLyricSettingsSheet = true },
                    onOpenBackgroundSettings = { showBackgroundSettingsSheet = true },
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
                                        onClick = onCoverClick,
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
                                        onSeek = onSeek,
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
                                        onCyclePlaybackMode = onCyclePlaybackMode,
                                        onOpenPlaylist = { showPlaylist = true },
                                        onOpenAddToPlaylist = { showAddToPlaylist = true },
                                        onOpenEqualizer = { showEqualizer = true },
                                        playlistActive = showPlaylist,
                                        addToPlaylistActive = showAddToPlaylist,
                                        equalizerActive = showEqualizer,
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
                                        onClick = onCoverClick,
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
                                    onSeek = onSeek,
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
                                    onCyclePlaybackMode = onCyclePlaybackMode,
                                    onOpenPlaylist = { showPlaylist = true },
                                    onOpenAddToPlaylist = { showAddToPlaylist = true },
                                    onOpenEqualizer = { showEqualizer = true },
                                    playlistActive = showPlaylist,
                                    addToPlaylistActive = showAddToPlaylist,
                                    equalizerActive = showEqualizer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPlaylist) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    playlistSheetState.hide()
                    showPlaylist = false
                }
            },
            sheetState = playlistSheetState,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = 0.36f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = null,
        ) {
            MaterialTheme(colorScheme = playbackScheme) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // 固定高度：歌曲数量变化时弹窗高度不变，列表内部滚动。
                    val sheetHeight = if (maxHeight == Dp.Infinity) {
                        PLAYLIST_SHEET_FALLBACK_HEIGHT
                    } else {
                        maxHeight * PLAYLIST_SHEET_HEIGHT_FRACTION
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    ) {
                        CoverBackdrop(bitmap = blurredCover)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 2.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 34.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.45f)),
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
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
                                    scope.launch {
                                        playlistSheetState.hide()
                                        showPlaylist = false
                                    }
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
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    equalizerSheetState.hide()
                    showEqualizer = false
                }
            },
            sheetState = equalizerSheetState,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = 0.36f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = null,
        ) {
            MaterialTheme(colorScheme = playbackScheme) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val sheetHeight = if (maxHeight == Dp.Infinity) {
                        EQUALIZER_SHEET_FALLBACK_HEIGHT
                    } else {
                        maxHeight * EQUALIZER_SHEET_HEIGHT_FRACTION
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                    ) {
                        CoverBackdrop(bitmap = blurredCover)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp, bottom = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 34.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.45f)),
                                )
                            }
                            Text(
                                text = stringResource(R.string.equalizer),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
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
                                Spacer(modifier = Modifier.height(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSleepTimerSheet) {
        PlaybackBottomSheet(
            sheetState = sleepTimerSheetState,
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
            sheetState = lyricSettingsSheetState,
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
            sheetState = backgroundSettingsSheetState,
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

    if (showMagicSheet) {
        PlaybackBottomSheet(
            sheetState = magicSheetState,
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
}

private fun VocalSplitMode.labelRes(): Int = when (this) {
    VocalSplitMode.BOTH -> R.string.vocal_split_both
    VocalSplitMode.VOCALS -> R.string.vocal_split_vocals
    VocalSplitMode.INSTRUMENTAL -> R.string.vocal_split_instrumental
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.36f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sheetHeight = if (maxHeight == Dp.Infinity) {
                ADD_TO_PLAYLIST_SHEET_FALLBACK_HEIGHT
            } else {
                maxHeight * ADD_TO_PLAYLIST_SHEET_HEIGHT_FRACTION
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            ) {
                CoverBackdrop(bitmap = blurredCover)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 34.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.45f)),
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_to_playlist),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    PlaylistPickerList(
                        playlists = playlists,
                        trackId = trackId,
                        onTogglePlaylist = onTogglePlaylist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onDismiss()
                                }
                            },
                        ) {
                            Text(text = stringResource(R.string.close), color = Color.White)
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    sheetState.hide()
                                    onCreatePlaylist()
                                }
                            },
                        ) {
                            Text(text = stringResource(R.string.create_playlist), color = Color.White)
                        }
                    }
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

@Composable
private fun PlaybackTopBar(
    onBack: () -> Unit,
    sleepTimer: SleepTimerState,
    onOpenSleepTimer: () -> Unit,
    onOpenMagic: () -> Unit,
    onOpenLyricSettings: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.22f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.playback_menu),
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.width(210.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = PLAYBACK_MENU_COLOR,
            ) {
                PlaybackMenuItem(
                    icon = Icons.Rounded.Lyrics,
                    label = stringResource(R.string.lyrics_settings),
                    value = "",
                    onClick = {
                        menuExpanded = false
                        onOpenLyricSettings()
                    },
                )
                PlaybackMenuItem(
                    icon = Icons.Rounded.Wallpaper,
                    label = stringResource(R.string.settings_background),
                    value = "",
                    onClick = {
                        menuExpanded = false
                        onOpenBackgroundSettings()
                    },
                )
                PlaybackMenuItem(
                    icon = Icons.Rounded.Timer,
                    label = stringResource(R.string.sleep_timer),
                    value = sleepTimerMenuValue(sleepTimer),
                    onClick = {
                        menuExpanded = false
                        onOpenSleepTimer()
                    },
                )
                PlaybackMenuItem(
                    icon = Icons.Rounded.AutoAwesome,
                    label = stringResource(R.string.magic),
                    value = "",
                    onClick = {
                        menuExpanded = false
                        onOpenMagic()
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
        )
        Switch(
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

@Composable
private fun PlaybackMenuItem(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White.copy(alpha = 0.92f),
            )
        },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 1,
                )
                if (value.isNotBlank()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.68f),
                        maxLines = 1,
                    )
                }
            }
        },
    )
}

/** 播放页的底部弹窗外壳：沿用封面高斯模糊背景 + 白色文字。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    blurredCover: Bitmap,
    playbackScheme: ColorScheme,
    title: String,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val contentScrollModifier = if (scrollable) {
        Modifier.verticalScroll(scrollState)
    } else {
        Modifier
    }
    ModalBottomSheet(
        onDismissRequest = {
            scope.launch {
                sheetState.hide()
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.36f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = null,
    ) {
        MaterialTheme(colorScheme = playbackScheme) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            ) {
                Box(modifier = Modifier.matchParentSize()) {
                    CoverBackdrop(bitmap = blurredCover)
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(contentScrollModifier)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp),
                ) {
                    SheetDragHandle()
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    content()
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
        Switch(
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
        Switch(
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
        Switch(
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
private fun rememberMusicBackgroundMotion(
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
        modifier = Modifier
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
private fun CoverBackdrop(
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

@Composable
private fun ClickableCover(
    bitmap: Bitmap?,
    size: Dp,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.switch_to_lyrics)
    CoverArt(
        bitmap = bitmap,
        modifier = Modifier
            .size(size)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    )
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
            text = track.title,
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
                        text = track.title,
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
private fun LyricsPanel(
    lyricsState: LyricsUiState,
    positionMs: Long,
    settings: AppSettings,
    animate: Boolean,
    onPlayFrom: (Long) -> Unit,
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
                    )
                } else {
                    PlainLyrics(
                        text = lyricsState.lyrics.rawText,
                        settings = settings,
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
) {
    val listState = rememberLazyListState()
    var previewIndex by remember(lyrics) { mutableStateOf<Int?>(null) }
    var draggingLyrics by remember(lyrics) { mutableStateOf(false) }
    val activeIndex = remember(lyrics, positionMs) {
        LrcParser.findActiveLine(lyrics.lines, positionMs)
    }
    val density = LocalDensity.current
    val lyricTextCenterOffsetPx = if (
        settings.lyricWordAnimationStyle == LyricWordAnimationStyle.STAR
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
        val centerStartEnd = settings.lyricCenterStartEnd
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
            val visible = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == activeIndex }
            // 开启后让当前句始终居中；关闭时仍停在视口上方三分之一。
            val targetTop = if (viewportHeight > 0) {
                if (centerStartEnd) {
                    val itemHeight = visible?.size ?: fallbackItemHeight
                    ((viewportHeight - itemHeight) / 2).coerceAtLeast(0)
                } else {
                    viewportHeight / 3
                }
            } else {
                120
            }
            // contentPadding 会参与 item 的实际位置，需要把这部分算进 scrollOffset。
            val contentPaddingPx = with(density) { contentPadding.roundToPx() }
            val initialScrollOffset = contentPaddingPx - targetTop
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = initialScrollOffset,
            )
            if (centerStartEnd) {
                // 首次定位可能用了预估行高；拿到实际 item 高度后再补一次精确居中。
                val settled = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == activeIndex }
                if (settled != null) {
                    val settledTargetTop = (
                        (viewportHeight - settled.size) / 2
                        ).coerceAtLeast(0)
                    val settledScrollOffset = contentPaddingPx - settledTargetTop
                    if (abs(settledScrollOffset - initialScrollOffset) > 1) {
                        listState.animateScrollToItem(
                            index = activeIndex,
                            scrollOffset = settledScrollOffset,
                        )
                    }
                }
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
            val baseStyle = MaterialTheme.typography.titleMedium
            val baseFontSize = baseStyle.fontSize.value * settings.lyricFontScale
            val starAnimation = settings.lyricWordAnimationStyle ==
                LyricWordAnimationStyle.STAR
            // 排版始终按当前行的字号进行，放大/缩小只做视觉缩放。
            // 这样一句歌词的换行位置在切换前后完全一致，不会因为行数变化而突然跳动。
            val layoutFontSize = baseFontSize * LYRIC_ACTIVE_SCALE
            val lyricScale by animateFloatAsState(
                targetValue = if (active) 1f else LYRIC_INACTIVE_SCALE,
                animationSpec = tween(
                    // 换句时放大/缩小放慢一些，衔接更从容。
                    durationMillis = LYRIC_LINE_TRANSITION_MS,
                    easing = FastOutSlowInEasing,
                ),
                label = "lyric-scale-$index",
            )
            val textAlign = when (settings.lyricAlignment) {
                LyricAlignment.LEFT -> TextAlign.Start
                LyricAlignment.CENTER -> TextAlign.Center
                LyricAlignment.RIGHT -> TextAlign.End
            }
            val transformOrigin = TransformOrigin(
                pivotFractionX = when (settings.lyricAlignment) {
                    LyricAlignment.LEFT -> 0f
                    LyricAlignment.CENTER -> 0.5f
                    LyricAlignment.RIGHT -> 1f
                },
                pivotFractionY = 0.5f,
            )
            val modifier = Modifier
                .fillMaxWidth()
                .zIndex(if (preview) 2f else if (active) 1f else 0f)
                .clickable(role = Role.Button) {
                    previewIndex = null
                    onPlayFrom(line.timeMs)
                }
                .padding(
                    horizontal = LYRIC_LINE_HORIZONTAL_PADDING,
                    vertical = if (starAnimation) {
                        STAR_LYRIC_LINE_VERTICAL_PADDING
                    } else {
                        LYRIC_LINE_VERTICAL_PADDING
                    },
                )
                .blur(
                    radius = if (!active && !preview) {
                        settings.lyricInactiveBlurDp.dp
                    } else {
                        0.dp
                    },
                )
                .graphicsLayer {
                    scaleX = lyricScale
                    scaleY = lyricScale
                    this.transformOrigin = transformOrigin
                }
            val style = baseStyle.copy(
                fontSize = layoutFontSize.sp,
                lineHeight = (
                    layoutFontSize * if (starAnimation) {
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
                    if (starAnimation) {
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
    val text = remember(line, quantizedPositionMs) {
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
                withStyle(SpanStyle(color = lerp(unsungColor, sungColor, progress))) {
                    append(segment.text)
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
                onTextLayout = { textLayoutResult = it },
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

@Composable
private fun PlainLyrics(
    text: String,
    settings: AppSettings,
) {
    val textAlign = when (settings.lyricAlignment) {
        LyricAlignment.LEFT -> TextAlign.Start
        LyricAlignment.CENTER -> TextAlign.Center
        LyricAlignment.RIGHT -> TextAlign.End
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 20.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge.let { baseStyle ->
                val fontSize = baseStyle.fontSize.value * settings.lyricFontScale
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
    onSeek: (Long) -> Unit,
) {
    var draggingValue by remember(playerState.currentTrack?.id) {
        mutableStateOf<Float?>(null)
    }
    val duration = playerState.durationMs.coerceAtLeast(0L)
    val maxValue = duration.toFloat().coerceAtLeast(1f)
    val displayedValue = (draggingValue
        ?: playerState.positionMs.toFloat())
        .coerceIn(0f, maxValue)
    val audioInfo = remember(track) { formatAudioInfo(track) }

    Column(modifier = Modifier.fillMaxWidth()) {
        ProgressSlider(
            value = displayedValue,
            valueRange = 0f..maxValue,
            enabled = duration > 0L,
            // 拖动过程中只更新显示，松手后才真正跳转，避免频繁 seek 让播放状态抖动。
            onValueChange = { draggingValue = it },
            onValueChangeFinished = {
                draggingValue?.let { onSeek(it.toLong()) }
                draggingValue = null
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
                if (audioInfo.isNotBlank()) {
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
            Icon(
                imageVector = if (playerState.showPauseIcon) {
                    Icons.Rounded.Pause
                } else {
                    Icons.Rounded.PlayArrow
                },
                contentDescription = stringResource(
                    if (playerState.showPauseIcon) R.string.pause else R.string.play,
                ),
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

@Composable
private fun SecondaryControls(
    playerState: PlayerUiState,
    onCyclePlaybackMode: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenEqualizer: () -> Unit,
    playlistActive: Boolean,
    addToPlaylistActive: Boolean,
    equalizerActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(
            onClick = onCyclePlaybackMode,
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
        IconButton(
            onClick = onOpenPlaylist,
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
        IconButton(
            onClick = onOpenAddToPlaylist,
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
        IconButton(
            onClick = onOpenEqualizer,
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
    }
}

/**
 * 播放页 / 歌词页左右都能一直滑：页码取一个大值、按奇偶映射到两种页面，
 * 起点放正中间，这样往左往右都不会撞到尽头，可以来回循环。
 */
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
/** 播放列表定位时，正在播放的那首上面留几行做上下文。 */
private const val QUEUE_SCROLL_CONTEXT_ITEMS = 2
/** 切歌时背景 / 封面 / 歌曲信息的渐变时长。 */
private const val TRACK_CHANGE_FADE_MS = 520
/** 歌词切到下一句时的过渡时长（缩放 + 滚动）。 */
private const val LYRIC_LINE_TRANSITION_MS = 760
/** 歌词未播放部分的白色透明度（已播放部分是纯白）。 */
private const val LYRIC_UNSUNG_ALPHA = 0.45f
private const val LYRIC_ACTIVE_SCALE = 1.24f
private const val LYRIC_INACTIVE_SCALE = 1f / LYRIC_ACTIVE_SCALE
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
