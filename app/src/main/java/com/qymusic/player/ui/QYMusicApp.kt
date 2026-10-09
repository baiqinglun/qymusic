package com.qymusic.player.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qymusic.player.R
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.LaunchScanTiming
import com.qymusic.player.data.MusicFolder
import com.qymusic.player.data.QUALITY_STANDARD
import com.qymusic.player.data.Track
import com.qymusic.player.data.UserPlaylist

@Composable
fun QYMusicApp(viewModel: MusicViewModel = viewModel()) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val lyricsState by viewModel.lyricsState.collectAsState()
    val trackLyricsAvailability by viewModel.trackLyricsAvailability.collectAsState()
    val lyricOffsetMs by viewModel.lyricOffsetMs.collectAsState()
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
    val karaokeRecordingState by viewModel.karaokeRecordingState.collectAsState()
    val karaokePlayerState by viewModel.karaokePlayerState.collectAsState()
    val karaokePreviewPlaybackState by viewModel.karaokePreviewPlaybackState.collectAsState()
    val karaokeDraft by viewModel.karaokeDraft.collectAsState()
    val karaokeDrafts by viewModel.karaokeDrafts.collectAsState()
    val karaokePublishState by viewModel.karaokePublishState.collectAsState()
    val magicAudioSwitching by viewModel.magicAudioSwitching.collectAsState()
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
    var showMagicSwitchLoading by remember { mutableStateOf(false) }
    var karaokeLiveTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var karaokeDraftEditorTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var karaokeEditorReturnScreenName by rememberSaveable {
        mutableStateOf(AppScreen.KARAOKE_HOME.name)
    }
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
            appScreen == AppScreen.KARAOKE_EDITOR -> {
                viewModel.pauseKaraokePreview()
                viewModel.discardKaraokeRecording()
                viewModel.stopKaraokePlayback()
                karaokeDraftEditorTrackId = null
                appScreenName = karaokeEditorReturnScreenName
            }
            appScreen == AppScreen.KARAOKE_LIVE -> {
                viewModel.discardKaraokeRecording()
                viewModel.stopKaraokePlayback()
                karaokeLiveTrackId = null
                appScreenName = AppScreen.KARAOKE_HOME.name
            }
            appScreen == AppScreen.KARAOKE_DRAFTS ->
                appScreenName = AppScreen.KARAOKE_HOME.name
            appScreen == AppScreen.KARAOKE_SETTINGS ->
                appScreenName = AppScreen.KARAOKE_HOME.name
            // 统计只从主界面进，返回就回主界面。
            appScreen == AppScreen.STATISTICS -> appScreenName = AppScreen.LIBRARY.name
            else -> appScreenName = AppScreen.LIBRARY.name
        }
    }

    LaunchedEffect(magicAudioSwitching) {
        if (magicAudioSwitching) {
            delay(MAGIC_SWITCH_LOADING_DELAY_MS)
            showMagicSwitchLoading = true
        } else {
            showMagicSwitchLoading = false
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
            onOpenKaraoke = { appScreenName = AppScreen.KARAOKE_HOME.name },
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
            onSetTrackInPlaylist = viewModel::setTrackInPlaylist,
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
            lyricOffsetMs = lyricOffsetMs,
            settings = settings,
            playlists = playlists,
            equalizerState = equalizerState,
            sleepTimer = sleepTimer,
            artwork = artwork,
            karaokeRecordingState = karaokeRecordingState,
            karaokePlayerState = karaokePlayerState,
            karaokePreviewPlaybackState = karaokePreviewPlaybackState,
            karaokeDraft = karaokeDraft,
            karaokePublishState = karaokePublishState,
            onBack = {
                nowPlayingOpen = false
            },
            onTogglePlayPause = viewModel::togglePlayPause,
            onPrevious = viewModel::playPrevious,
            onNext = viewModel::playNext,
            onCyclePlaybackMode = viewModel::cyclePlaybackMode,
            onSelectQueueIndex = viewModel::playQueueIndex,
            onSetTrackInPlaylist = viewModel::setTrackInPlaylist,
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
            onStartKaraokeRecording = viewModel::startKaraokeRecording,
            onFinishKaraokeRecording = viewModel::finishKaraokeRecording,
            onDiscardKaraokeRecording = viewModel::discardKaraokeRecording,
            onStartKaraokePlayback = viewModel::startKaraokePlayback,
            onStopKaraokePlayback = viewModel::stopKaraokePlayback,
            onToggleKaraokePlayPause = viewModel::toggleKaraokePlayPause,
            onSeekKaraoke = viewModel::seekKaraoke,
            onKaraokeVocalSplitModeChange = viewModel::setKaraokeVocalSplitMode,
            onKaraokePitchSemitonesChange = viewModel::setKaraokePitchSemitones,
            onKaraokeSpeedChange = viewModel::setKaraokeSpeed,
            onKaraokeDraftChange = viewModel::updateKaraokeDraft,
            onSaveKaraokeDraft = { project ->
                viewModel.saveKaraokeDraft(project)
            },
            onDiscardKaraokeDraft = viewModel::discardKaraokeDraft,
            onStartKaraokePreview = viewModel::startKaraokePreview,
            onPauseKaraokePreview = viewModel::pauseKaraokePreview,
            onSeekKaraokePreview = viewModel::seekKaraokePreview,
            onUpdateKaraokePreview = viewModel::updateKaraokePreview,
            onPublishKaraoke = viewModel::publishKaraoke,
            onMusicReactiveBackgroundChange = viewModel::setMusicReactiveBackground,
            onLyricAlignmentChange = viewModel::setLyricAlignment,
            onLyricFontScaleChange = viewModel::setLyricFontScale,
            onLyricBoldChange = viewModel::setLyricBold,
            onLyricInactiveBlurChange = viewModel::setLyricInactiveBlur,
            onLyricOffsetChange = viewModel::setLyricOffset,
            onLyricCenterStartEndChange = viewModel::setLyricCenterStartEnd,
            onLyricWordAnimationStyleChange = viewModel::setLyricWordAnimationStyle,
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

        // K歌子页面切换时先铺一层不透明背景，避免滑动转场缝隙里露出常驻主界面。
        if (appScreen.isKaraokeScreen()) {
            Box(
                modifier = overlayTouchBlocker.background(
                    MaterialTheme.colorScheme.background,
                ),
            )
        }

        // 设置 / 统计盖在主界面之上，只给这两页做转场。
        AnimatedContent(
            targetState = appScreen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                // 只做横向位移、不做淡入淡出：设置页和统计页都是整屏不透明，
                // 一起滑动时能拼满整屏；一旦有淡出，中间帧就会透出下面的主界面。
                when {
                    initialState.isKaraokeScreen() && targetState.isKaraokeScreen() -> {
                        if (targetState.depth() > initialState.depth()) {
                            slideInHorizontally(tween(SCREEN_SLIDE_MS)) { it } togetherWith
                                slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { -it }
                        } else {
                            slideInHorizontally(tween(SCREEN_SLIDE_MS)) { -it } togetherWith
                                slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { it }
                        }
                    }

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
            onRescanOnLaunchChange = viewModel::setRescanOnLaunch,
            onLaunchScanTimingChange = viewModel::setLaunchScanTiming,
            onThemeModeChange = viewModel::setThemeMode,
            onThemeColorChange = viewModel::setThemeColor,
            onMusicReactiveBackgroundChange = viewModel::setMusicReactiveBackground,
            onLyricAlignmentChange = viewModel::setLyricAlignment,
            onLyricFontScaleChange = viewModel::setLyricFontScale,
            onLyricBoldChange = viewModel::setLyricBold,
            onLyricInactiveBlurChange = viewModel::setLyricInactiveBlur,
            onLyricCenterStartEndChange = viewModel::setLyricCenterStartEnd,
            onLyricWordAnimationStyleChange = viewModel::setLyricWordAnimationStyle,
            onAutoPlayOnLaunchChange = viewModel::setAutoPlayOnLaunch,
            onPlaybackFadeEnabledChange = viewModel::setPlaybackFadeEnabled,
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

                AppScreen.KARAOKE_HOME -> Box(modifier = overlayTouchBlocker) {
                    KaraokeHomeScreen(
                        tracks = tracks,
                        artworkCache = libraryArtwork,
                        lyricsAvailability = trackLyricsAvailability,
                        onRequestArtwork = viewModel::requestLibraryArtwork,
                        onRequestLyricsAvailability = viewModel::requestLyricsAvailability,
                        onBack = { appScreenName = AppScreen.LIBRARY.name },
                        onOpenDrafts = {
                            appScreenName = AppScreen.KARAOKE_DRAFTS.name
                        },
                        onOpenSettings = {
                            appScreenName = AppScreen.KARAOKE_SETTINGS.name
                        },
                        onStartKaraoke = { track ->
                            viewModel.prepareKaraokePlayback(track)
                            viewModel.loadKaraokeTrackDetails(track)
                            karaokeLiveTrackId = track.id
                            appScreenName = AppScreen.KARAOKE_LIVE.name
                        },
                    )
                }

                AppScreen.KARAOKE_SETTINGS -> Box(modifier = overlayTouchBlocker) {
                    KaraokeSettingsScreen(
                        outputTreeUri = settings.karaokeOutputTreeUri,
                        keepDraftAfterPublish = settings.keepKaraokeDraftAfterPublish,
                        onOutputTreeChange = viewModel::setKaraokeOutputTreeUri,
                        onKeepDraftAfterPublishChange =
                            viewModel::setKeepKaraokeDraftAfterPublish,
                        onBack = { appScreenName = AppScreen.KARAOKE_HOME.name },
                    )
                }

                AppScreen.KARAOKE_DRAFTS -> Box(modifier = overlayTouchBlocker) {
                    KaraokeDraftsScreen(
                        drafts = karaokeDrafts,
                        onBack = { appScreenName = AppScreen.KARAOKE_HOME.name },
                        onOpenDraft = { draft ->
                            val loaded = viewModel.loadKaraokeDraft(draft.draftId)
                            if (loaded != null) {
                                val track = tracks.firstOrNull {
                                    it.id == loaded.trackId
                                } ?: loaded.toTrack()
                                viewModel.prepareKaraokePlayback(track)
                                viewModel.loadKaraokeTrackDetails(track)
                                karaokeDraftEditorTrackId = track.id
                                karaokeEditorReturnScreenName =
                                    AppScreen.KARAOKE_DRAFTS.name
                                appScreenName = AppScreen.KARAOKE_EDITOR.name
                            }
                        },
                        onDeleteDrafts = viewModel::deleteKaraokeDrafts,
                    )
                }

                AppScreen.KARAOKE_LIVE -> Box(modifier = overlayTouchBlocker) {
                    val liveTrack = tracks.firstOrNull { it.id == karaokeLiveTrackId }
                    if (liveTrack == null) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        val liveArtwork = libraryArtwork[liveTrack.id]
                            ?: remember(liveTrack.id) {
                                createProceduralCover(liveTrack.id.hashCode())
                            }
                        val liveBlurredCover = remember(liveArtwork) {
                            liveArtwork.createBlurredCover()
                        }
                        KaraokeLiveScreen(
                            track = liveTrack,
                            playerState = karaokePlayerState,
                            lyricsState = lyricsState,
                            settings = settings,
                            artwork = liveArtwork,
                            blurredCover = liveBlurredCover,
                            recordingState = karaokeRecordingState,
                            onStartRecording = viewModel::startKaraokeRecording,
                            onFinishRecording = viewModel::finishKaraokeRecording,
                            onBack = {
                                viewModel.discardKaraokeRecording()
                                viewModel.stopKaraokePlayback()
                                karaokeLiveTrackId = null
                                appScreenName = AppScreen.KARAOKE_HOME.name
                            },
                            onTogglePlayPause = viewModel::toggleKaraokePlayPause,
                            onSeek = viewModel::seekKaraoke,
                            onVocalSplitModeChange = viewModel::setKaraokeVocalSplitMode,
                            onPitchSemitonesChange = viewModel::setKaraokePitchSemitones,
                            onPlaybackSpeedChange = viewModel::setKaraokeSpeed,
                            onFinish = {
                                if (karaokePlayerState.showPauseIcon) {
                                    viewModel.toggleKaraokePlayPause()
                                }
                                viewModel.finishKaraokeRecording()
                                karaokeDraftEditorTrackId = liveTrack.id
                                karaokeEditorReturnScreenName =
                                    AppScreen.KARAOKE_HOME.name
                                appScreenName = AppScreen.KARAOKE_EDITOR.name
                            },
                        )
                    }
                }

                AppScreen.KARAOKE_EDITOR -> Box(modifier = overlayTouchBlocker) {
                    val editorTrack = tracks.firstOrNull {
                        it.id == karaokeDraftEditorTrackId
                    } ?: karaokeDraft?.takeIf {
                        it.trackId == karaokeDraftEditorTrackId
                    }?.toTrack()
                    if (editorTrack == null) {
                        Box(modifier = Modifier.fillMaxSize())
                    } else {
                        val editorArtwork = libraryArtwork[editorTrack.id]
                            ?: remember(editorTrack.id) {
                                createProceduralCover(editorTrack.id.hashCode())
                            }
                        val editorBlurredCover = remember(editorArtwork) {
                            editorArtwork.createBlurredCover()
                        }
                        val closeEditor = {
                            viewModel.pauseKaraokePreview()
                            viewModel.discardKaraokeRecording()
                            viewModel.stopKaraokePlayback()
                            karaokeDraftEditorTrackId = null
                            appScreenName = karaokeEditorReturnScreenName
                        }
                        KaraokeEditorScreen(
                            recordingState = karaokeRecordingState,
                            draft = karaokeDraft,
                            playbackState = karaokePreviewPlaybackState,
                            publishState = karaokePublishState,
                            trackTitle = editorTrack.title,
                            artist = editorTrack.artist,
                            artwork = editorArtwork,
                            blurredCover = editorBlurredCover,
                            defaultLyrics = (lyricsState as? LyricsUiState.Ready)
                                ?.lyrics
                                ?.rawText
                                .orEmpty(),
                            onRerecord = {
                                closeEditor()
                                viewModel.prepareKaraokePlayback(editorTrack)
                                viewModel.loadKaraokeTrackDetails(editorTrack)
                                karaokeLiveTrackId = editorTrack.id
                                appScreenName = AppScreen.KARAOKE_LIVE.name
                            },
                            onDelete = {
                                viewModel.pauseKaraokePreview()
                                viewModel.discardKaraokeDraft()
                                viewModel.discardKaraokeRecording()
                                viewModel.stopKaraokePlayback()
                                karaokeDraftEditorTrackId = null
                                karaokeEditorReturnScreenName =
                                    AppScreen.KARAOKE_HOME.name
                                appScreenName = AppScreen.KARAOKE_HOME.name
                            },
                            onDraftChange = viewModel::updateKaraokeDraft,
                            onUpdatePreview = viewModel::updateKaraokePreview,
                            onTogglePreviewPlayback = { project ->
                                if (karaokePreviewPlaybackState.isPlaying) {
                                    viewModel.pauseKaraokePreview()
                                } else {
                                    viewModel.startKaraokePreview(project)
                                }
                            },
                            onSeekPreview = viewModel::seekKaraokePreview,
                            onSaveDraft = { project ->
                                viewModel.pauseKaraokePreview()
                                viewModel.saveKaraokeDraft(project) {
                                    viewModel.discardKaraokeRecording()
                                    viewModel.stopKaraokePlayback()
                                    karaokeDraftEditorTrackId = null
                                    karaokeEditorReturnScreenName =
                                        AppScreen.KARAOKE_DRAFTS.name
                                    appScreenName = AppScreen.KARAOKE_DRAFTS.name
                                }
                            },
                            onPublish = viewModel::publishKaraoke,
                        )
                    }
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

        if (
            settings.rescanOnLaunch &&
            settings.launchScanTiming == LaunchScanTiming.DURING_STARTUP &&
            scanState is ScanUiState.Scanning
        ) {
            StartupScanScreen()
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

    if (showMagicSwitchLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = stringResource(R.string.magic_switching)) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }
}

private enum class AppScreen {
    LIBRARY,
    SETTINGS,
    STATISTICS,
    KARAOKE_HOME,
    KARAOKE_SETTINGS,
    KARAOKE_DRAFTS,
    KARAOKE_EDITOR,
    KARAOKE_LIVE,
}

private fun AppScreen.isKaraokeScreen(): Boolean = when (this) {
    AppScreen.KARAOKE_HOME,
    AppScreen.KARAOKE_SETTINGS,
    AppScreen.KARAOKE_DRAFTS,
    AppScreen.KARAOKE_EDITOR,
    AppScreen.KARAOKE_LIVE,
    -> true

    else -> false
}

private fun AppScreen.depth(): Int = when (this) {
    AppScreen.LIBRARY -> 0
    AppScreen.SETTINGS,
    AppScreen.STATISTICS,
    AppScreen.KARAOKE_HOME,
    -> 1

    AppScreen.KARAOKE_DRAFTS,
    AppScreen.KARAOKE_SETTINGS,
    AppScreen.KARAOKE_LIVE,
    -> 2

    AppScreen.KARAOKE_EDITOR -> 3
}

private fun KaraokeEditProject.toTrack(): Track = Track(
    id = trackId,
    uri = Uri.parse(sourceUri),
    title = trackTitle,
    artist = artist,
    album = "",
    durationMs = durationMs.coerceAtLeast(0L),
    folderName = "",
    relativePath = "",
    lyricUri = null,
    quality = QUALITY_STANDARD,
)

/** 转场时长：短一些更跟手，返回时不会觉得点了没反应。 */
private const val SCREEN_SLIDE_MS = 200
private const val SCREEN_FADE_MS = 140
private const val MAGIC_SWITCH_LOADING_DELAY_MS = 250L
