package com.qymusic.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qymusic.player.R
import com.qymusic.player.data.MusicFolder
import com.qymusic.player.data.UserPlaylist

@Composable
fun QYMusicApp(viewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()
    val artwork by viewModel.artwork.collectAsState()
    val libraryArtwork by viewModel.libraryArtwork.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val equalizerState by viewModel.equalizerState.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    val audioOutput by viewModel.audioOutput.collectAsState()
    val playbackSummary by viewModel.playbackSummary.collectAsState()
    val trackStats by viewModel.trackStats.collectAsState()
    val statsRange by viewModel.statsRange.collectAsState()
    val statsAnchorMs by viewModel.statsAnchorMs.collectAsState()
    val vocalSplitMode by viewModel.vocalSplitMode.collectAsState()
    val audioEffectStatus by viewModel.audioEffectStatus.collectAsState()
    val audioSinkStatus by viewModel.audioSinkStatus.collectAsState()
    val rotationState by viewModel.rotationState.collectAsState()
    val rangeStats by viewModel.rangeStats.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlistCovers by viewModel.playlistCovers.collectAsState()

    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    var appScreenName by rememberSaveable { mutableStateOf(AppScreen.LIBRARY.name) }
    // 主界面 tab 与歌曲列表滚动位置放在最外层，进播放页 / 设置页再回来时保持原样。
    var libraryTab by rememberSaveable { mutableStateOf(0) }
    val songsListState = rememberLazyListState()
    var folderToRemove by remember { mutableStateOf<MusicFolder?>(null) }
    var libraryGroupRequest by remember { mutableStateOf<LibraryGroupRequest?>(null) }
    var playlistCoverTarget by remember { mutableStateOf<UserPlaylist?>(null) }
    val appScreen = AppScreen.valueOf(appScreenName)

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(viewModel::addFolder)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    val playlistCoverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val target = playlistCoverTarget
        playlistCoverTarget = null
        if (uri != null && target != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.setPlaylistCover(target.id, uri)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BackHandler(enabled = nowPlayingOpen || appScreen != AppScreen.LIBRARY) {
        when {
            nowPlayingOpen -> {
                nowPlayingOpen = false
            }
            // 统计只从主界面进，返回就回主界面。
            appScreen == AppScreen.STATISTICS -> appScreenName = AppScreen.LIBRARY.name
            else -> appScreenName = AppScreen.LIBRARY.name
        }
    }

    val currentTrack = playerState.currentTrack
    val playerVisible = nowPlayingOpen && currentTrack != null
    // 播放页盖住主界面时主界面既看不见、也用不到实时播放位置，
    // 给一份冻结快照：播放位置每 200ms 更新就不会带着整页重组。
    val libraryPlayerState = if (playerVisible) remember { playerState } else playerState
    // 浮层盖在主界面上时，浮层空隙里的点击不能漏到下面的列表，
    // 否则在设置页点空白处会点到隐藏的歌曲行。
    val overlayInteraction = remember { MutableInteractionSource() }
    val overlayTouchBlocker = Modifier
        .fillMaxSize()
        .clickable(
            interactionSource = overlayInteraction,
            indication = null,
            onClick = {},
        )

    Box(modifier = Modifier.fillMaxSize()) {
        // 主界面常驻在最底层：进播放页 / 设置页 / 统计页都不再销毁它，
        // 返回时不用重新组合整页（实测那一帧要 150ms 以上），点下去立刻可见。
        LibraryScreen(
            selectedTab = libraryTab,
            onSelectTab = { libraryTab = it },
            songsListState = songsListState,
            tracks = tracks,
            folderCount = folders.size,
            playlists = playlists,
            pinnedArtists = settings.pinnedArtists,
            pinnedAlbums = settings.pinnedAlbums,
            pinnedPlaylistIds = settings.pinnedPlaylistIds,
            scanState = scanState,
            playerState = libraryPlayerState,
            artwork = artwork,
            artworkCache = libraryArtwork,
            onRescan = viewModel::rescan,
            onOpenSettings = { appScreenName = AppScreen.SETTINGS.name },
            onOpenStatistics = {
                appScreenName = AppScreen.STATISTICS.name
            },
            onRequestArtwork = viewModel::requestLibraryArtwork,
            onPlayTrack = { track, queue ->
                // 列表里切歌只更新播放状态，不自动跳进播放页。
                viewModel.playTrack(track, queue)
            },
            onPlayNext = viewModel::addToQueueNext,
            onPrevious = viewModel::playPrevious,
            onTogglePlayPause = viewModel::togglePlayPause,
            onNext = viewModel::playNext,
            onOpenPlayer = { nowPlayingOpen = true },
            onCreatePlaylist = viewModel::createPlaylist,
            onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
            onDeletePlaylist = viewModel::deletePlaylist,
            onToggleArtistPinned = viewModel::setArtistPinned,
            onToggleAlbumPinned = viewModel::setAlbumPinned,
            onTogglePlaylistPinned = viewModel::setPlaylistPinned,
            playlistCovers = playlistCovers,
            onRequestPlaylistCover = viewModel::requestPlaylistCover,
            onPickPlaylistCover = { playlist ->
                playlistCoverTarget = playlist
                playlistCoverPicker.launch(arrayOf("image/*"))
            },
            onResetPlaylistCover = { playlist ->
                viewModel.setPlaylistCover(playlist.id, null)
            },
            onRenamePlaylist = viewModel::renamePlaylist,
            requestedGroup = libraryGroupRequest,
            onRequestedGroupHandled = { libraryGroupRequest = null },
            onReturnToPlayer = { nowPlayingOpen = true },
            onPlayAllTracks = viewModel::playAllTracks,
            playerCovering = playerVisible,
        )

        // 播放页是最上层浮层：进出只动它自己，主界面一直留在下面。
        AnimatedVisibility(
            visible = playerVisible,
            enter = slideInVertically(tween(SCREEN_SLIDE_MS)) { it } +
                fadeIn(tween(SCREEN_FADE_MS)),
            exit = slideOutVertically(tween(SCREEN_SLIDE_MS)) { it } +
                fadeOut(tween(SCREEN_FADE_MS)),
        ) {
            Box(modifier = overlayTouchBlocker) {
            NowPlayingScreen(
            playerState = playerState,
            lyricsState = lyricsState,
            settings = settings,
            playlists = playlists,
            equalizerState = equalizerState,
            sleepTimer = sleepTimer,
            artwork = artwork,
            onBack = {
                nowPlayingOpen = false
            },
            onTogglePlayPause = viewModel::togglePlayPause,
            onPrevious = viewModel::playPrevious,
            onNext = viewModel::playNext,
            onCyclePlaybackMode = viewModel::cyclePlaybackMode,
            onSelectQueueIndex = viewModel::playQueueIndex,
            onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
            onCreatePlaylist = viewModel::createPlaylist,
            onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
            onEqualizerPresetChange = viewModel::setEqualizerPreset,
            onEqualizerBandChange = viewModel::setEqualizerBandLevel,
            onBassStrengthChange = viewModel::setBassStrength,
            onVirtualizerStrengthChange = viewModel::setVirtualizerStrength,
            onLoudnessGainChange = viewModel::setLoudnessGain,
            onReverbPresetChange = viewModel::setReverbPreset,
            onReverbLevelChange = viewModel::setReverbLevel,
            onStartSleepTimer = viewModel::startSleepTimer,
            onStartSleepTimerAtTrackEnd = viewModel::startSleepTimerAtTrackEnd,
            onCancelSleepTimer = viewModel::cancelSleepTimer,
            onPlaybackSpeedChange = viewModel::setPlaybackSpeed,
            onPitchSemitonesChange = viewModel::setPlaybackPitchSemitones,
            vocalSplitMode = vocalSplitMode,
            onVocalSplitModeChange = viewModel::setVocalSplitMode,
            audioEffectStatus = audioEffectStatus,
            audioSinkStatus = audioSinkStatus,
            rotationState = rotationState,
            onRotationEnabledChange = viewModel::setRotationEnabled,
            onRotationSpeedChange = viewModel::setRotationSpeed,
            onRotationClockwiseChange = viewModel::setRotationClockwise,
            onSeek = viewModel::seekTo,
            onOpenArtist = { artistName ->
                libraryGroupRequest = LibraryGroupRequest(
                    kind = LibraryGroupKind.ARTIST,
                    title = artistName,
                )
                nowPlayingOpen = false
            },
            onOpenAlbum = { albumName ->
                libraryGroupRequest = LibraryGroupRequest(
                    kind = LibraryGroupKind.ALBUM,
                    title = albumName,
                )
                nowPlayingOpen = false
            },
            onMoveQueueItem = viewModel::moveQueueItem,
            )
            }
        }

        // 设置 / 统计盖在主界面之上，只给这两页做转场。
        AnimatedContent(
            targetState = appScreen,
            transitionSpec = {
                // 只做横向位移、不做淡入淡出：设置页和统计页都是整屏不透明，
                // 一起滑动时能拼满整屏；一旦有淡出，中间帧就会透出下面的主界面。
                when {
                    // 统计页内容多，滑出时逐帧重绘很吃绘制（模拟器上要 30ms+/帧）；
                    // 主界面本来就常驻在下面，直接换过去反而最顺。
                    initialState == AppScreen.STATISTICS &&
                        targetState == AppScreen.LIBRARY ->
                        EnterTransition.None togetherWith ExitTransition.None

                    // 回到主界面：设置页滑出去，主界面本来就在下面，直接露出来。
                    targetState == AppScreen.LIBRARY ->
                        slideInHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 } togetherWith
                            slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { it / 4 }

                    // 进入设置 / 统计：从右侧滑进来
                    else ->
                        slideInHorizontally(tween(SCREEN_SLIDE_MS)) { it / 4 } togetherWith
                            slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 }
                }
            },
            label = "app-route",
        ) { screen ->
            when (screen) {
                // 主界面本身常驻在下面，这里只放一个透明占位，不挡手势。
                AppScreen.LIBRARY -> Box(modifier = Modifier.fillMaxSize())

                AppScreen.SETTINGS -> Box(modifier = overlayTouchBlocker) {
                SettingsScreen(
            settings = settings,
            folders = folders,
            equalizerState = equalizerState,
            audioOutput = audioOutput,
            onBack = { appScreenName = AppScreen.LIBRARY.name },
            onAddFolder = { folderPicker.launch(null) },
            onRemoveFolder = { folderToRemove = it },
            onRescan = viewModel::rescan,
            onThemeModeChange = viewModel::setThemeMode,
            onMusicReactiveBackgroundChange = viewModel::setMusicReactiveBackground,
            onLyricAlignmentChange = viewModel::setLyricAlignment,
            onLyricFontScaleChange = viewModel::setLyricFontScale,
            onLyricBoldChange = viewModel::setLyricBold,
            onLyricInactiveBlurChange = viewModel::setLyricInactiveBlur,
            onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
            onEqualizerBandChange = viewModel::setEqualizerBandLevel,
            onEqualizerPresetChange = viewModel::setEqualizerPreset,
            onBassStrengthChange = viewModel::setBassStrength,
            onVirtualizerStrengthChange = viewModel::setVirtualizerStrength,
            onLoudnessGainChange = viewModel::setLoudnessGain,
            onReverbPresetChange = viewModel::setReverbPreset,
            onReverbLevelChange = viewModel::setReverbLevel,
            )
                }

                AppScreen.STATISTICS -> Box(modifier = overlayTouchBlocker) {
                StatisticsScreen(
            rangeStats = rangeStats,
            statsRange = statsRange,
            statsAnchorMs = statsAnchorMs,
            trackStats = trackStats,
            onSelectRange = viewModel::setStatsRange,
            onShiftAnchor = viewModel::shiftStatsAnchor,
            onSelectAnchor = viewModel::setStatsAnchorAt,
            onResetAnchor = viewModel::resetStatsAnchor,
            onBack = { appScreenName = AppScreen.LIBRARY.name },
            onRefresh = viewModel::refreshStats,
            )
                }
            }
        }
    }

    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(text = androidx.compose.ui.res.stringResource(R.string.remove_folder_title)) },
            text = {
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        R.string.remove_folder_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFolder(folder)
                        folderToRemove = null
                    },
                ) {
                    Text(text = androidx.compose.ui.res.stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text(text = androidx.compose.ui.res.stringResource(R.string.cancel))
                }
            },
        )
    }
}

private enum class AppScreen {
    LIBRARY,
    SETTINGS,
    STATISTICS,
}

/** 转场时长：短一些更跟手，返回时不会觉得点了没反应。 */
private const val SCREEN_SLIDE_MS = 200
private const val SCREEN_FADE_MS = 140
