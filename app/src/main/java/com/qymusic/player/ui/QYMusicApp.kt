package com.qymusic.player.ui

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.compose.runtime.DisposableEffect
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlinx.coroutines.isActive
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qymusic.player.R
import com.qymusic.player.data.AppUpdater
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.LaunchScanTiming
import com.qymusic.player.data.MusicFolder
import com.qymusic.player.data.QUALITY_STANDARD
import com.qymusic.player.data.ThemeMode
import com.qymusic.player.data.Track
import com.qymusic.player.data.UserPlaylist

@Composable
fun QYMusicApp(
    viewModel: MusicViewModel = viewModel(),
    downloadViewModel: MusicDownloadViewModel = viewModel(),
    platformPlaylistsViewModel: PlatformPlaylistsViewModel = viewModel(),
) {
    val context = LocalContext.current
    // 在线试听的歌词/封面抓取复用下载页的元数据插件链
    viewModel.attachMetadataFetchService(downloadViewModel.metadataFetchService)
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
    val clipPlayback by viewModel.clipPlayback.collectAsState()
    // 当前媒体项对应的片段：进度条高亮与拖出提示都以它为准。
    val activeClip = clipPlayback.clip?.takeIf { clip ->
        playerState.currentTrack?.id == "clip:" + clip.id
    }

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
    var tagEditorTrackId by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadSettingsReturnName by rememberSaveable {
        mutableStateOf(AppScreen.SETTINGS.name)
    }
    var karaokeEditorReturnScreenName by rememberSaveable {
        mutableStateOf(AppScreen.KARAOKE_HOME.name)
    }
    val appScreen = AppScreen.valueOf(appScreenName)

    // —— 推拉式抽屉 ——
    // progress：0 = 完全关闭，1 = 完全打开。只被手势与动画写入，
    // 位移 / 透明度都在布局、绘制阶段读取，抽屉动画期间不重组整页。
    var drawerProgress by remember { mutableFloatStateOf(0f) }
    // 抽屉（和遮罩）是否参与组合：只在 progress 离开 / 回到 0 的那一刻切换，
    // 关闭时不挡主界面的点击，动画时也不逐帧重组。
    var drawerVisible by remember { mutableStateOf(false) }
    var drawerAnimJob by remember { mutableStateOf<Job?>(null) }
    val drawerAnimScope = rememberCoroutineScope()

    fun animateDrawerTo(target: Float) {
        drawerAnimJob?.cancel()
        if (target > 0f) {
            drawerVisible = true
        }
        drawerAnimJob = drawerAnimScope.launch {
            animate(
                initialValue = drawerProgress,
                targetValue = target,
                animationSpec = tween(
                    durationMillis = DRAWER_ANIM_MS,
                    easing = FastOutSlowInEasing,
                ),
            ) { value, _ ->
                drawerProgress = value
            }
            // 收起动画跑完（progress 已是 0）才把抽屉移出组合。
            // 不能在每帧回调里判断：打开动画的第一帧 value 是初始值 0，
            // 会立刻把刚置为 true 的 drawerVisible 又翻回 false，抽屉就永远不出现。
            if (target <= 0f) {
                drawerVisible = false
            }
        }
    }

    val openDrawer = {
        // 菜单按钮开 / 关切换：抽屉开着时再点一次菜单就是收起。
        animateDrawerTo(if (drawerProgress >= 0.5f) 0f else 1f)
        Unit
    }

    // 进入播放页时若抽屉还开着，自动收起：
    // 播放页是全屏浮层，底下留一个开着的抽屉会让返回次序变得混乱。
    LaunchedEffect(nowPlayingOpen) {
        if (nowPlayingOpen && drawerVisible) {
            animateDrawerTo(0f)
        }
    }
    val closeDrawer = {
        animateDrawerTo(0f)
        Unit
    }

    // 信息卡片右上角的亮暗切换：跟随系统时按系统当前外观取反。
    val drawerDarkTheme = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }


    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let(viewModel::addFolder)
    }
    val downloadFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setDownloadDirTreeUri(uri.toString())
        }
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

    // 应用内检查更新：启动 2 秒后静默检查，发现新版本弹窗提示
    var updateInfo by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var updateDownloadId by remember { mutableStateOf<Long?>(null) }
    var updateProgress by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(2_000)
        updateInfo = AppUpdater.check(context)
    }
    val updateDownloadReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == updateDownloadId) {
                    AppUpdater.downloadedApkUri(ctx, id)?.let { apkUri ->
                        AppUpdater.installApk(ctx, apkUri)
                    }
                }
            }
        }
    }
    DisposableEffect(Unit) {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            updateDownloadReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(updateDownloadReceiver) }
    }
    LaunchedEffect(updateDownloadId) {
        val id = updateDownloadId ?: return@LaunchedEffect
        while (isActive) {
            val progress = AppUpdater.downloadProgress(context, id)
            updateProgress = progress.percent
            if (progress.status == DownloadManager.STATUS_FAILED) {
                updateDownloadId = null
                break
            }
            delay(500)
        }
    }

    // 更新弹窗：发现新版本时提示，确认后经系统下载器下载并自动拉起安装
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text(text = stringResource(R.string.update_title, info.versionName)) },
            text = {
                Column {
                    if (info.notes.isNotBlank()) {
                        Text(
                            text = info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    if (updateDownloadId != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.update_downloading, updateProgress),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                if (updateDownloadId == null) {
                    TextButton(onClick = {
                        if (AppUpdater.canInstall(context)) {
                            updateDownloadId = AppUpdater.enqueueDownload(context, info)
                        } else {
                            AppUpdater.openUnknownSourceSettings(context)
                        }
                    }) { Text(text = stringResource(R.string.update_now)) }
                }
            },
            dismissButton = {
                if (updateDownloadId == null) {
                    TextButton(onClick = { updateInfo = null }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            },
        )
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
            appScreen == AppScreen.TAG_EDITOR -> appScreenName = AppScreen.LIBRARY.name
            appScreen == AppScreen.HISTORY -> appScreenName = AppScreen.LIBRARY.name
            appScreen == AppScreen.DOWNLOAD_SETTINGS ->
                appScreenName = downloadSettingsReturnName
            appScreen == AppScreen.STATISTICS -> appScreenName = AppScreen.LIBRARY.name
            appScreen == AppScreen.CLIPS -> appScreenName = AppScreen.LIBRARY.name
            else -> appScreenName = AppScreen.LIBRARY.name
        }
    }

    // 抽屉开着时返回先收抽屉。放在已有 BackHandler 之后注册，优先级更高。
    BackHandler(enabled = drawerVisible) {
        animateDrawerTo(0f)
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
    val musicClips by viewModel.musicClips.collectAsState()
    val musicClipsForCurrentTrack = remember(musicClips, currentTrack?.id) {
        musicClips.filter { it.trackId == currentTrack?.id }
    }

    // 封面过渡动画：开始播放时从屏幕中心飞向底部播放控制栏
    val coverFlightAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    val coverFlightBitmap = libraryArtwork[currentTrack?.id]
    LaunchedEffect(Unit) {
        viewModel.coverFlightTick.collect {
            if (libraryArtwork[currentTrack?.id] == null || nowPlayingOpen) return@collect
            coverFlightAnim.snapTo(0f)
            coverFlightAnim.animateTo(1f, androidx.compose.animation.core.tween(450))
        }
    }
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

    // 平台歌单的登录浮层（WebView 登录 / 酷狗扫码）打开时必须禁用抽屉手势：
    // Compose 父层的拖动检测会抢走 interop WebView 的横向拖动，
    // 导致网页划不动、验证码滑块把整个屏幕拖走。
    val platformUiState by platformPlaylistsViewModel.state.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(platformUiState.loginOpen) {
                if (platformUiState.loginOpen) return@pointerInput
                // 右划拉出抽屉（任意位置），抽屉开着时左划跟随收起。
                // 松手时按进度过半回弹到全开 / 全关。
                // 子代自己消费掉的横向拖动（如横向滚动的筛选条）不会触发。
                var dragging = false
                var dragStartProgress = 0f
                var dragAccumPx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        dragStartProgress = drawerProgress
                        dragAccumPx = 0f
                        dragging = true
                    },
                    onDragEnd = {
                        if (dragging) {
                            dragging = false
                            animateDrawerTo(if (drawerProgress >= 0.5f) 1f else 0f)
                        }
                    },
                    onDragCancel = {
                        if (dragging) {
                            dragging = false
                            animateDrawerTo(if (drawerProgress >= 0.5f) 1f else 0f)
                        }
                    },
                ) { change, dragAmount ->
                    if (!dragging) return@detectHorizontalDragGestures
                    change.consume()
                    // 累计整段拖动的位移（dragAmount 是单帧增量，不能直接当总位移用）
                    dragAccumPx += dragAmount
                    drawerProgress = (
                        dragStartProgress +
                            dragAccumPx / (size.width * DRAWER_WIDTH_FRACTION)
                        ).coerceIn(0f, 1f)
                    if (drawerProgress > 0f) {
                        drawerVisible = true
                    }
                }
            },
    ) {
        val drawerWidthPx = with(LocalDensity.current) {
            (maxWidth * DRAWER_WIDTH_FRACTION).toPx()
        }
        // —— 抽屉本体：占屏宽一半，从左侧滑入 ——
        // 放在页面 Box 之前：页面（连同底部播放条）绘制在抽屉之上，
        // 播放条全屏宽覆盖抽屉的底部区域。
        if (drawerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(DRAWER_WIDTH_FRACTION)
                .offset {
                    IntOffset(x = ((drawerProgress - 1f) * drawerWidthPx).roundToInt(), y = 0)
                }
                .background(MaterialTheme.colorScheme.background),
            ) {
            AppDrawerContent(
                appScreen = appScreen,
                libraryTab = libraryTab,
                isDarkTheme = drawerDarkTheme,
                onToggleTheme = {
                    viewModel.setThemeMode(
                        if (drawerDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK,
                    )
                },
                onOpenSongs = {
                    libraryTab = 0
                    appScreenName = AppScreen.LIBRARY.name
                    animateDrawerTo(0f)
                },
                onOpenAlbums = {
                    libraryTab = 2
                    appScreenName = AppScreen.LIBRARY.name
                    animateDrawerTo(0f)
                },
                onOpenArtists = {
                    libraryTab = 1
                    appScreenName = AppScreen.LIBRARY.name
                    animateDrawerTo(0f)
                },
                onOpenPlaylists = {
                    libraryTab = 3
                    appScreenName = AppScreen.LIBRARY.name
                    animateDrawerTo(0f)
                },
                onOpenFolders = {
                    appScreenName = AppScreen.FOLDERS.name
                    animateDrawerTo(0f)
                },
                onOpenHistory = {
                    appScreenName = AppScreen.HISTORY.name
                    animateDrawerTo(0f)
                },
                onOpenSearch = {
                    appScreenName = AppScreen.SEARCH.name
                    animateDrawerTo(0f)
                },
                onOpenKaraoke = {
                    appScreenName = AppScreen.KARAOKE_HOME.name
                    animateDrawerTo(0f)
                },
                onOpenClips = {
                    appScreenName = AppScreen.CLIPS.name
                    animateDrawerTo(0f)
                },
                onOpenPlatformPlaylists = {
                    appScreenName = AppScreen.PLATFORM_PLAYLISTS.name
                    animateDrawerTo(0f)
                },
                onRescan = {
                    viewModel.rescan()
                    animateDrawerTo(0f)
                },
                onOpenStatistics = {
                    appScreenName = AppScreen.STATISTICS.name
                    animateDrawerTo(0f)
                },
                onOpenCache = {
                    appScreenName = AppScreen.CACHE.name
                    animateDrawerTo(0f)
                },
                onOpenSettings = {
                    appScreenName = AppScreen.SETTINGS.name
                    animateDrawerTo(0f)
                },
                onOpenAbout = {
                    appScreenName = AppScreen.ABOUT.name
                    animateDrawerTo(0f)
                },
            )
            }
        }
        // —— 主界面：抽屉推出时整体向右平移 ——
        // 保持页面原有布局不压缩、不重排，只是被推到右半边；
        // 左侧是滑出的抽屉，右侧页面照常显示、照常可点。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(x = (drawerWidthPx * drawerProgress).roundToInt(), y = 0)
                },
        ) {
        // 纵向分栏：上面是页面内容，底部是全局播放条（所有页面共用），
        // 播放页 / 启动扫描浮层盖在最上面，覆盖包括播放条在内的整屏。
        Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
        // 主界面常驻在最底层：进播放页 / 设置页 / 统计页都不再销毁它，
        // 返回时不用重新组合整页（实测那一帧要 150ms 以上），点下去立刻可见。
        Box(modifier = Modifier.fillMaxSize()) {
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
            artworkCache = libraryArtwork,
            onRescan = viewModel::rescan,
            onOpenDrawer = openDrawer,
            showTopTabs = false,
            onOpenSettings = { appScreenName = AppScreen.SETTINGS.name },
            onEditTags = { track ->
                tagEditorTrackId = track.id
                appScreenName = AppScreen.TAG_EDITOR.name
            },
            onDeleteTrack = { track ->
                viewModel.removeTrackFromLibrary(track.id)
                viewModel.rescan(showLoading = false, refreshing = true)
            },
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
            onTogglePlayPause = viewModel::togglePlayPause,
            onCreatePlaylist = viewModel::createPlaylist,
            onSetTrackInPlaylist = viewModel::setTrackInPlaylist,
            onDeletePlaylist = viewModel::deletePlaylist,
            onToggleArtistPinned = viewModel::setArtistPinned,
            onToggleAlbumPinned = viewModel::setAlbumPinned,
            onTogglePlaylistPinned = viewModel::setPlaylistPinned,
            artistViewMode = settings.artistViewMode,
            albumViewMode = settings.albumViewMode,
            playlistViewMode = settings.playlistViewMode,
            onArtistViewModeChange = viewModel::setArtistViewMode,
            onAlbumViewModeChange = viewModel::setAlbumViewMode,
            onPlaylistViewModeChange = viewModel::setPlaylistViewMode,
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
        }

        // 转场底衬：当前与上一个都是非曲库页面时铺一层不透明背景。
        // 作用有二：K 歌子页之间全宽滑动不留缝隙；一级导航页之间做淡切时，
        // 中间帧两页都是半透明，没有这层底衬就会透出底下的曲库。
        // 去往曲库（露出常驻底层）或从曲库出发时不铺，保持滑动 / 露出语义。
        val previousScreen = remember { mutableStateOf(appScreen) }
        val showNavBackdrop = appScreen != AppScreen.LIBRARY &&
            previousScreen.value != AppScreen.LIBRARY
        SideEffect { previousScreen.value = appScreen }
        if (showNavBackdrop) {
            Box(
                modifier = overlayTouchBlocker.background(
                    MaterialTheme.colorScheme.background,
                ),
            )
        }

        // 页面转场模型：
        //  - 导航页（抽屉一级目的地，互相平级）：曲库、文件夹、播放历史、下载、
        //    K 歌首页、统计、缓存、设置、关于、推荐。
        //  - 子页面（从某个父页面推入，返回时回到父页面）：标签编辑、下载设置、
        //    K 歌设置 / 草稿 / 演唱 / 编辑。
        //  - 曲库是常驻底层：去往曲库 = 旧页滑出露出底层；曲库里的艺术家 / 专辑 /
        //    歌单详情由曲库内部的 AnimatedContent 自己做推入 / 弹出。
        AnimatedContent(
            targetState = appScreen,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                val bothKaraoke = initialState.isKaraokeScreen() &&
                    targetState.isKaraokeScreen()
                when {
                    // K 歌家族内部：全宽滑动（防不透明页面之间露缝）
                    bothKaraoke && targetState.depth() != initialState.depth() -> {
                        if (targetState.depth() > initialState.depth()) {
                            slideInHorizontally(tween(SCREEN_SLIDE_MS)) { it } togetherWith
                                slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { -it }
                        } else {
                            slideInHorizontally(tween(SCREEN_SLIDE_MS)) { -it } togetherWith
                                slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { it }
                        }
                    }

                    // 去往曲库：曲库常驻在下面，旧页滑出即露出，新页只做轻微视差
                    targetState == AppScreen.LIBRARY ->
                        slideInHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 } togetherWith
                            slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { it }

                    // 推入子页面：整屏从右侧推入，旧页向左让位（视差）
                    targetState.isSubPage() ->
                        slideInHorizontally(tween(SCREEN_SLIDE_MS)) { it } togetherWith
                            slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 }

                    // 子页面返回父页面：父页从左侧轻微滑回，子页整屏滑出
                    initialState.isSubPage() ->
                        slideInHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 } togetherWith
                            slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { it }

                    // 一级导航页之间互跳（抽屉切换目的地）：平级，淡切即可，
                    // 不暗示层级方向；底衬保证中间帧不透出曲库
                    initialState.isTopLevelNavPage() && targetState.isTopLevelNavPage() ->
                        fadeIn(tween(SCREEN_FADE_MS)) togetherWith
                            fadeOut(tween(SCREEN_FADE_MS))

                    // 从曲库推入导航页：整屏从右侧推入
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

                AppScreen.FOLDERS -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    FoldersScreen(
                        folders = folders,
                        tracks = tracks,
                        isLoading = scanState is ScanUiState.Loading ||
                            scanState is ScanUiState.Scanning ||
                            scanState is ScanUiState.Refreshing,
                        viewMode = settings.foldersViewMode,
                        onViewModeChange = viewModel::setFoldersViewMode,
                        onOpenDrawer = openDrawer,
                        onAddFolder = { folderPicker.launch(null) },
                        onRescan = viewModel::rescan,
                        currentTrackId = currentTrack?.id,
                        isPlaying = libraryPlayerState.showPauseIcon,
                        artworkCache = libraryArtwork,
                        onRequestArtwork = viewModel::requestLibraryArtwork,
                        onPlayTrack = { track, queue ->
                            viewModel.playTrack(track, queue)
                        },
                        onPlayNext = viewModel::addToQueueNext,
                        onPlayAllTracks = viewModel::playAllTracks,
                        onEditTags = { track ->
                            tagEditorTrackId = track.id
                            appScreenName = AppScreen.TAG_EDITOR.name
                        },
                        onDeleteTrack = { track ->
                            viewModel.removeTrackFromLibrary(track.id)
                            viewModel.rescan(showLoading = false, refreshing = true)
                        },
                        playlists = playlists,
                        onCreatePlaylist = viewModel::createPlaylist,
                        onSetTrackInPlaylist = viewModel::setTrackInPlaylist,
                        onOpenAlbum = { title ->
                            libraryGroupRequest = LibraryGroupRequest(
                                kind = LibraryGroupKind.ALBUM,
                                title = title,
                            )
                            appScreenName = AppScreen.LIBRARY.name
                        },
                        onOpenArtist = { title ->
                            libraryGroupRequest = LibraryGroupRequest(
                                kind = LibraryGroupKind.ARTIST,
                                title = title,
                            )
                            appScreenName = AppScreen.LIBRARY.name
                        },
                    )
                }

                AppScreen.SETTINGS -> Box(modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background)) {
                SettingsScreen(
            settings = settings,
            folders = folders,
            equalizerState = equalizerState,
            audioOutput = audioOutput,
            onBack = { appScreenName = AppScreen.LIBRARY.name },
            onOpenDrawer = openDrawer,
            backEnabled = !playerVisible,
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
                        onOpenDownloadSettings = {
                            downloadSettingsReturnName = AppScreen.SETTINGS.name
                            appScreenName = AppScreen.DOWNLOAD_SETTINGS.name
                        },
        )
                }

                AppScreen.TAG_EDITOR -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    val tagTrack = tracks.firstOrNull { it.id == tagEditorTrackId }
                    if (tagTrack == null) {
                        LaunchedEffect(Unit) {
                            appScreenName = AppScreen.LIBRARY.name
                        }
                    } else {
                        TagEditorScreen(
                            track = tagTrack,
                            metadataService = downloadViewModel.metadataFetchService,
                            onBack = {
                                tagEditorTrackId = null
                                appScreenName = AppScreen.LIBRARY.name
                            },
                            onTagsSaved = { track ->
                                // 正在播放的歌被改了标签：不切歌直接刷新封面 / 歌词 / 元数据
                                viewModel.refreshPlayingTrackAfterTagEdit(track.id)
                                viewModel.invalidateLibraryArtwork(track.id)
                                viewModel.rescan(showLoading = false, refreshing = true)
                            },
                        )
                    }
                }

                AppScreen.HISTORY -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    PlayHistoryScreen(
                        viewModel = viewModel,
                        tracks = tracks,
                        artworkCache = libraryArtwork,
                        onRequestArtwork = viewModel::requestLibraryArtwork,
                        onOpenDrawer = openDrawer,
                    )
                }

                AppScreen.CLIPS -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    ClipsScreen(
                        viewModel = viewModel,
                        tracks = tracks,
                        artworkCache = libraryArtwork,
                        onRequestArtwork = viewModel::requestLibraryArtwork,
                        onOpenDrawer = openDrawer,
                    )
                }

                AppScreen.CACHE -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    CacheScreen(
                        viewModel = viewModel,
                        downloadViewModel = downloadViewModel,
                        destinationTreeUri = settings.downloadDirTreeUri,
                        onOpenDrawer = openDrawer,
                    )
                }

                AppScreen.DOWNLOAD_SETTINGS -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    DownloadSettingsScreen(
                        viewModel = downloadViewModel,
                        downloadDirTreeUri = settings.downloadDirTreeUri,
                        onBack = { appScreenName = downloadSettingsReturnName },
                        onChooseDownloadDir = {
                            downloadFolderPicker.launch(null)
                        },
                        onResetDownloadDir = {
                            viewModel.setDownloadDirTreeUri(null)
                        },
                    )
                }

                AppScreen.ABOUT -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 和主页对齐：内容从状态栏下方开始。
                                .statusBarsPadding()
                                .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = openDrawer,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Menu,
                                    contentDescription = stringResource(
                                        R.string.navigation_menu,
                                    ),
                                    // 这行标题不在任何 Surface 内，取不到暗色内容色，需显式指定
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            Text(
                                text = stringResource(R.string.about),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        AboutSettingsContent(modifier = Modifier.fillMaxSize())
                    }
                }

                AppScreen.RECOMMEND -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    RecommendationScreen(
                        tracks = tracks,
                        trackStats = trackStats,
                        artworkCache = libraryArtwork,
                        playerState = playerState,
                        onRequestArtwork = viewModel::requestLibraryArtwork,
                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onOpenPlayer = { nowPlayingOpen = true },
                        onOpenDrawer = openDrawer,
                    )
                }

                AppScreen.SEARCH -> Box(
                    modifier = overlayTouchBlocker.background(MaterialTheme.colorScheme.background),
                ) {
                    MusicDownloadScreen(
                        viewModel = downloadViewModel,
                        downloadDirTreeUri = settings.downloadDirTreeUri,
                        onBack = { appScreenName = AppScreen.LIBRARY.name },
                        onOpenDownloadSettings = {
                            downloadSettingsReturnName = appScreenName
                            appScreenName = AppScreen.DOWNLOAD_SETTINGS.name
                        },
                        onPreviewStart = viewModel::playOnlineTrackPending,
                        onPlayOnline = { song, url ->
                            viewModel.playOnlineTrack(song, url)
                            nowPlayingOpen = true
                        },
                        onPreviewFailed = viewModel::previewResolveFailed,
                        onDownloadFinished = {
                            viewModel.rescan(showLoading = false, refreshing = true)
                        },
                        onOpenDrawer = openDrawer,
                        showBackButton = false,
                    )
                }

                AppScreen.DOWNLOAD -> Box(modifier = overlayTouchBlocker) {
                    MusicDownloadScreen(
                        viewModel = downloadViewModel,
                        downloadDirTreeUri = settings.downloadDirTreeUri,
                        onBack = { appScreenName = AppScreen.LIBRARY.name },
                        onOpenDownloadSettings = {
                            downloadSettingsReturnName = appScreenName
                            appScreenName = AppScreen.DOWNLOAD_SETTINGS.name
                        },
                        onPreviewStart = viewModel::playOnlineTrackPending,
                        onPlayOnline = { song, url ->
                            viewModel.playOnlineTrack(song, url)
                            nowPlayingOpen = true
                        },
                        onPreviewFailed = viewModel::previewResolveFailed,
                        onDownloadFinished = {
                            viewModel.rescan(showLoading = false, refreshing = true)
                        },
                    )
                }

                AppScreen.PLATFORM_PLAYLISTS -> Box(modifier = overlayTouchBlocker) {
                    PlatformPlaylistsScreen(
                        viewModel = platformPlaylistsViewModel,
                        downloadViewModel = downloadViewModel,
                        downloadDirTreeUri = settings.downloadDirTreeUri,
                        currentTrackId = currentTrack?.id,
                        onOpenDrawer = openDrawer,
                        onPreviewStart = viewModel::playOnlineTrackPending,
                        onPlayOnline = { song, url ->
                            viewModel.playOnlineTrack(song, url)
                            nowPlayingOpen = true
                        },
                        onPreviewFailed = viewModel::previewResolveFailed,
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
                        onOpenDrawer = openDrawer,
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
                        backEnabled = !playerVisible,
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
                        backEnabled = !playerVisible,
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

                AppScreen.STATISTICS -> Box(
                    modifier = overlayTouchBlocker.background(
                        MaterialTheme.colorScheme.background,
                    ),
                ) {
                StatisticsScreen(
            rangeStats = rangeStats,
            statsRange = statsRange,
            statsAnchorMs = statsAnchorMs,
            trackStats = trackStats,
            onSelectRange = viewModel::setStatsRange,
            onShiftAnchor = viewModel::shiftStatsAnchor,
            onSelectAnchor = viewModel::setStatsAnchorAt,
            onResetAnchor = viewModel::resetStatsAnchor,
            onOpenDrawer = openDrawer,
            onRefresh = viewModel::refreshStats,
            )
            }
        }
        }
        }

        // —— 全局播放条：挂在所有页面底部，只要有歌在播就显示（所有页面共用）。
        // 播放页盖住整屏时读冻结快照，位置刷新不会带着下面的页面重组。
        // 抽屉推出时页面整体右移，播放条反向补偿同样的位移，
        // 在屏幕上保持不动、始终全屏宽（抽屉打开时盖住抽屉的底部）。
        AnimatedVisibility(
            visible = currentTrack != null,
            modifier = Modifier.offset {
                IntOffset(x = -(drawerWidthPx * drawerProgress).roundToInt(), y = 0)
            },
            enter = slideInVertically(tween(SCREEN_SLIDE_MS)) { it } +
                fadeIn(tween(SCREEN_FADE_MS)),
            exit = slideOutVertically(tween(SCREEN_SLIDE_MS)) { it } +
                fadeOut(tween(SCREEN_FADE_MS)),
        ) {
            currentTrack?.let { track ->
                MiniPlayer(
                    track = track,
                    isPlaying = libraryPlayerState.showPauseIcon,
                    resolving = playerState.previewResolving,
                    positionMs = libraryPlayerState.positionMs,
                    durationMs = libraryPlayerState.durationMs,
                    artwork = artwork,
                    onOpen = { nowPlayingOpen = true },
                    onPrevious = viewModel::playPrevious,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::playNext,
                )
            }
        }
        }
        }

        // 封面过渡：开始播放时，封面从屏幕中心飞向底部播放控制栏的封面位
        if (coverFlightAnim.value < 1f && coverFlightBitmap != null && !nowPlayingOpen) {
            CoverFlightOverlay(
                progress = coverFlightAnim.value,
                bitmap = coverFlightBitmap,
            )
        }

        // 播放页是最上层浮层：进出只动它自己，盖住包括播放条在内的整屏。
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
            currentTrackTitle = currentTrack?.title.orEmpty(),
            musicClips = musicClipsForCurrentTrack,
            onPlayMusicClip = viewModel::playMusicClip,
            onAddClip = viewModel::addMusicClip,
            onExitClipPlayback = viewModel::exitClipPlayback,
            activeClip = activeClip,
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
            onPlayerQuickActionsChange = viewModel::setPlayerQuickActions,
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

        if (
            settings.rescanOnLaunch &&
            settings.launchScanTiming == LaunchScanTiming.DURING_STARTUP &&
            scanState is ScanUiState.Scanning
        ) {
            StartupScanScreen()
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

}
private enum class AppScreen {
    LIBRARY,
    FOLDERS,
    RECOMMEND,
    SEARCH,
    SETTINGS,
    TAG_EDITOR,
    DOWNLOAD_SETTINGS,
    ABOUT,
    DOWNLOAD,
    PLATFORM_PLAYLISTS,
    STATISTICS,
    CACHE,
    HISTORY,
    CLIPS,
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
    AppScreen.TAG_EDITOR,
    AppScreen.FOLDERS,
    AppScreen.RECOMMEND,
    AppScreen.SEARCH,
    AppScreen.ABOUT,
    AppScreen.DOWNLOAD,
    AppScreen.PLATFORM_PLAYLISTS,
    AppScreen.STATISTICS,
    AppScreen.KARAOKE_HOME,
    AppScreen.CACHE,
    AppScreen.HISTORY,
    AppScreen.CLIPS,
    -> 1

    AppScreen.DOWNLOAD_SETTINGS,
    AppScreen.KARAOKE_DRAFTS,
    AppScreen.KARAOKE_SETTINGS,
    AppScreen.KARAOKE_LIVE,
    -> 2

    AppScreen.KARAOKE_EDITOR -> 3
}

/** 导航页：抽屉一级目的地，互相平级；之间的转场用淡切，不暗示层级方向。 */
private fun AppScreen.isTopLevelNavPage(): Boolean = when (this) {
    AppScreen.LIBRARY,
    AppScreen.FOLDERS,
    AppScreen.RECOMMEND,
    AppScreen.SEARCH,
    AppScreen.SETTINGS,
    AppScreen.ABOUT,
    AppScreen.DOWNLOAD,
    AppScreen.PLATFORM_PLAYLISTS,
    AppScreen.STATISTICS,
    AppScreen.KARAOKE_HOME,
    AppScreen.CACHE,
    AppScreen.HISTORY,
    AppScreen.CLIPS,
    -> true

    else -> false
}

/** 子页面：从某个父页面推入（标签编辑、下载设置、K 歌的各层），返回时回到父页面。 */
private fun AppScreen.isSubPage(): Boolean = when (this) {
    AppScreen.TAG_EDITOR,
    AppScreen.DOWNLOAD_SETTINGS,
    AppScreen.KARAOKE_SETTINGS,
    AppScreen.KARAOKE_DRAFTS,
    AppScreen.KARAOKE_LIVE,
    AppScreen.KARAOKE_EDITOR,
    -> true

    else -> false
}

/** 抽屉内容：顶部卡片（头像 / 名称 / 重扫 / 亮暗切换）+ 三张白色菜单卡片。 */
@Composable
private fun AppDrawerContent(
    appScreen: AppScreen,
    libraryTab: Int,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenKaraoke: () -> Unit,
    onOpenClips: () -> Unit,
    onOpenPlatformPlaylists: () -> Unit,
    onRescan: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenCache: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 和主页对齐：主页内容在 Scaffold 里从状态栏下方开始，
            // 抽屉也要让出状态栏，顶部卡片才和主页的按钮行平齐；
            // 底部让出导航条，避免最后一张卡片压在手势条上。
            // insets 放在 scroll 外侧，滚动时也不会把让出的区域滚没。
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 信息卡片：头像、名称、亮暗切换。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onRescan) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.rescan),
                    )
                }
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) {
                            Icons.Rounded.LightMode
                        } else {
                            Icons.Rounded.DarkMode
                        },
                        contentDescription = stringResource(
                            if (isDarkTheme) R.string.theme_light else R.string.theme_dark,
                        ),
                    )
                }
            }
        }

        // 卡片一：曲库导航。
        DrawerMenuCard {
            DrawerMenuItem(
                icon = Icons.Rounded.MusicNote,
                label = stringResource(R.string.nav_song_list),
                color = Color(0xFF3D7FFF),
                selected = appScreen == AppScreen.LIBRARY && libraryTab == 0,
                onClick = onOpenSongs,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Album,
                label = stringResource(R.string.albums_tab),
                color = Color(0xFF5C6BC0),
                selected = appScreen == AppScreen.LIBRARY && libraryTab == 2,
                onClick = onOpenAlbums,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.artists_tab),
                color = Color(0xFFEC407A),
                selected = appScreen == AppScreen.LIBRARY && libraryTab == 1,
                onClick = onOpenArtists,
            )
            DrawerMenuItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = stringResource(R.string.playlists_tab),
                color = Color(0xFF9C27B0),
                selected = appScreen == AppScreen.LIBRARY && libraryTab == 3,
                onClick = onOpenPlaylists,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.FolderOpen,
                label = stringResource(R.string.folders_title),
                color = Color(0xFFF9A825),
                selected = appScreen == AppScreen.FOLDERS,
                onClick = onOpenFolders,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.History,
                label = stringResource(R.string.history_page_title),
                color = Color(0xFF26A69A),
                selected = appScreen == AppScreen.HISTORY,
                onClick = onOpenHistory,
            )
        }

        // 卡片二：下载与 K 歌。
        DrawerMenuCard {
            DrawerMenuItem(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.download_entry),
                color = Color(0xFF00897B),
                selected = appScreen == AppScreen.SEARCH || appScreen == AppScreen.DOWNLOAD,
                onClick = onOpenSearch,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.CloudQueue,
                label = stringResource(R.string.platform_playlists_title),
                color = Color(0xFF3949AB),
                selected = appScreen == AppScreen.PLATFORM_PLAYLISTS,
                onClick = onOpenPlatformPlaylists,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.RecordVoiceOver,
                label = stringResource(R.string.karaoke),
                color = Color(0xFFE5533C),
                selected = appScreen.isKaraokeScreen(),
                onClick = onOpenKaraoke,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.ContentCut,
                label = stringResource(R.string.clips_title),
                color = Color(0xFF7E57C2),
                selected = appScreen == AppScreen.CLIPS,
                onClick = onOpenClips,
            )
        }

        // 卡片三：统计 / 设置 / 关于。
        DrawerMenuCard {
            DrawerMenuItem(
                icon = Icons.Rounded.BarChart,
                label = stringResource(R.string.playback_statistics),
                color = Color(0xFF00ACC1),
                selected = appScreen == AppScreen.STATISTICS,
                onClick = onOpenStatistics,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.CleaningServices,
                label = stringResource(R.string.cache_page_title),
                color = Color(0xFFE5533C),
                selected = appScreen == AppScreen.CACHE,
                onClick = onOpenCache,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.settings),
                color = Color(0xFF5F6B7A),
                selected = appScreen == AppScreen.SETTINGS,
                onClick = onOpenSettings,
            )
            DrawerMenuItem(
                icon = Icons.Rounded.Info,
                label = stringResource(R.string.about),
                color = Color(0xFF795548),
                selected = appScreen == AppScreen.ABOUT,
                onClick = onOpenAbout,
            )
        }
    }
}

/** 白底圆角菜单卡片，纵向排布若干菜单项。 */
@Composable
private fun DrawerMenuCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            content = content,
        )
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != Color.Unspecified) {
            // 彩色图标：同色系浅色圆角底，与歌曲弹窗菜单风格一致
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color,
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private const val DRAWER_WIDTH_FRACTION = 0.5f

/** 抽屉开合补间时长。 */
private const val DRAWER_ANIM_MS = 280

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
