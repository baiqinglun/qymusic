package com.qymusic.player.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.qymusic.player.R
import com.qymusic.player.data.DownloadEmbedOptions
import com.qymusic.player.data.LxMusicSong
import com.qymusic.player.data.LxMusicSource
import com.qymusic.player.data.LxSourceCapability
import com.qymusic.player.data.MetadataPlugin
import com.qymusic.player.data.MusicSearchOption

@Composable
fun MusicDownloadScreen(
    viewModel: MusicDownloadViewModel,
    downloadDirTreeUri: String?,
    onBack: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onPreviewStart: (LxMusicSong) -> Unit,
    onPlayOnline: (LxMusicSong, String) -> Unit,
    onPreviewFailed: (LxMusicSong) -> Unit = {},
    onDownloadFinished: () -> Unit,
    showBackButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val localSourcePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importLocalSource)
    }

    var showSourceManager by rememberSaveable { mutableStateOf(false) }
    var showMetadataManager by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // 滚动到接近底部时自动加载下一页搜索结果
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            last.index >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.hasMoreResults, state.isLoadingMore, state.isSearching) {
        if (shouldLoadMore && state.hasMoreResults &&
            !state.isLoadingMore && !state.isSearching && !state.isSourceLoading
        ) {
            viewModel.loadMoreResults()
        }
    }
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }
    var showDownloadProgressPage by rememberSaveable { mutableStateOf(false) }
    var downloadQualitySong by remember { mutableStateOf<LxMusicSong?>(null) }
    var importUrl by rememberSaveable { mutableStateOf("") }
    var pluginUrl by rememberSaveable { mutableStateOf("") }

    val pluginFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::importMetadataPluginLocal)
    }

    LaunchedEffect(Unit) {
        viewModel.ensureSourceLoaded()
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewModel.dismissNotice()
    }

    var lastCompletedCount by remember { mutableIntStateOf(0) }
    val completedCount = state.downloads.values.count {
        it.phase == MusicDownloadPhase.COMPLETED
    }
    LaunchedEffect(completedCount) {
        if (completedCount > lastCompletedCount) {
            onDownloadFinished()
            lastCompletedCount = completedCount
        }
    }

    BackHandler(enabled = showDownloadProgressPage) {
        showDownloadProgressPage = false
    }
    if (showDownloadProgressPage) {
        DownloadProgressScreen(
            tasks = state.downloads.values.toList(),
            onBack = { showDownloadProgressPage = false },
            onClearFinished = viewModel::clearFinishedDownloads,
        )
        return
    }

    // 不再注册无条件的 BackHandler：播放页浮层打开时，返回应先关播放页，
    // 由宿主 QYMusicApp 的顶层 BackHandler 统一处理（下载界面返回即回曲库）。

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
            ) {
                DownloadTopBar(
                    onBack = onBack,
                    showBackButton = showBackButton,
                    onOpenDrawer = onOpenDrawer,
                    onOpenDownloadSettings = onOpenDownloadSettings,
                    onOpenProgress = { showDownloadProgressPage = true },
                )
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    DownloadSearchField(
                        query = state.query,
                        onQueryChange = viewModel::updateQuery,
                        onClear = viewModel::clearSearch,
                        onSearch = viewModel::search,
                        isSearching = state.isSearching,
                        searchSources = state.searchSources,
                        selectedSourceId = state.selectedSearchSourceKey,
                        selectedSourceName = state.selectedSearchSourceName,
                        onSourceChange = viewModel::setSearchSource,
                        enabled = state.selectedSearchSourceKey != null &&
                            !state.isSourceLoading,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 8.dp,
                        end = 20.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when {
                        state.isSourceLoading -> {
                            item(key = "loading") {
                                DownloadMessage(
                                    icon = Icons.Rounded.Link,
                                    title = stringResource(R.string.download_source_loading),
                                    message = stringResource(
                                        R.string.download_source_loading_message,
                                    ),
                                    showProgress = true,
                                )
                            }
                        }

                        state.activeSourceId == null -> {
                            item(key = "no-source") {
                                DownloadMessage(
                                    icon = Icons.Rounded.Link,
                                    title = stringResource(R.string.download_source_empty_title),
                                    message = stringResource(
                                        R.string.download_source_empty_message,
                                    ),
                                )
                            }
                        }

                        state.query.isBlank() -> {
                            item(key = "prompt") {
                                DownloadMessage(
                                    icon = Icons.Rounded.Search,
                                    title = stringResource(R.string.download_search_prompt_title),
                                    message = stringResource(
                                        R.string.download_search_prompt_message,
                                    ),
                                )
                            }
                        }

                        state.isSearching -> {
                            item(key = "searching") {
                                DownloadMessage(
                                    icon = Icons.Rounded.Search,
                                    title = stringResource(R.string.download_searching),
                                    message = stringResource(
                                        R.string.download_searching_message,
                                    ),
                                    showProgress = true,
                                )
                            }
                        }

                        state.searchError != null -> {
                            item(key = "search-error") {
                                DownloadMessage(
                                    icon = Icons.Rounded.Refresh,
                                    title = stringResource(R.string.download_search_failed),
                                    message = state.searchError.orEmpty(),
                                )
                            }
                        }

                        state.results.isEmpty() -> {
                            item(key = "empty") {
                                DownloadMessage(
                                    icon = Icons.Rounded.Search,
                                    title = stringResource(
                                        R.string.download_search_empty_title,
                                        state.query.trim(),
                                    ),
                                    message = stringResource(
                                        R.string.download_search_empty_message,
                                    ),
                                )
                            }
                        }

                        else -> {
                            items(
                                items = state.results,
                                key = { song -> "${song.sourceKey}-${song.id}" },
                            ) { song ->
                                DownloadSongRow(
                                    song = song,
                                    task = taskForSong(song, state.downloads),
                                    onDownload = {
                                        downloadQualitySong = song
                                    },
                                    onPlay = {
                                        viewModel.previewSong(
                                            song = song,
                                            onPreviewStart = onPreviewStart,
                                            onResolved = { resolvedSong, url ->
                                                onPlayOnline(resolvedSong, url)
                                            },
                                            onResolveFailed = onPreviewFailed,
                                        )
                                    },
                                )
                            }
                            if (state.isLoadingMore) {
                                item(key = "loading-more") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            }
                            item(key = "bottom-space") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBackToTop && state.results.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 24.dp),
                    enter = fadeIn() + scaleIn(initialScale = 0.6f),
                    exit = fadeOut() + scaleOut(targetScale = 0.6f),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp,
                    ) {
                        IconButton(onClick = {
                            scope.launch { listState.animateScrollToItem(0) }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.back_to_top),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                }
            }
        }
    }

    if (showMetadataManager) {
        MetadataPluginManagerDialog(
            state = state,
            lyricsPreferWordLevel = state.lyricsPreferWordLevel,
            onLyricsPreferWordLevelChange = viewModel::setLyricsPreferWordLevel,
            pluginUrl = pluginUrl,
            onPluginUrlChange = { pluginUrl = it },
            onImportUrl = { viewModel.importMetadataPluginUrl(pluginUrl) },
            onPickLocalPlugin = {
                pluginFilePicker.launch(
                    arrayOf(
                        "application/zip",
                        "application/x-zip-compressed",
                        "application/octet-stream",
                        "application/javascript",
                        "text/javascript",
                        "*/*",
                    ),
                )
            },
            onSetPluginEnabled = viewModel::setPluginEnabled,
            onMoveUp = viewModel::movePluginUp,
            onMoveDown = viewModel::movePluginDown,
            onDelete = viewModel::deleteMetadataPlugin,
            onDismissMessage = viewModel::dismissPluginImportMessage,
            embedOptions = state.embedOptions,
            onEmbedOptionsChange = viewModel::setEmbedOptions,
            onClose = { showMetadataManager = false },
        )
    }

    if (showSourceManager) {
        DownloadSourceManagerDialog(
            state = state,
            importUrl = importUrl,
            onImportUrlChange = { importUrl = it },
            onImport = {
                viewModel.importSource(importUrl)
            },
            onActivateSource = viewModel::activateSource,
            onDeleteSource = viewModel::deleteSource,
            onDismissMessages = viewModel::dismissImportMessage,
            onClose = { showSourceManager = false },
            onPickLocalSource = {
                localSourcePicker.launch(
                    arrayOf(
                        "application/javascript",
                        "text/javascript",
                        "application/x-javascript",
                        "text/plain",
                        "*/*",
                    ),
                )
            },
        )
    }



    downloadQualitySong?.let { song ->
        DownloadQualityDialog(
            song = song,
            qualities = state.qualities.ifEmpty { DEFAULT_QUALITY_OPTIONS },
            sizes = state.qualitySizes,
            onQuerySizes = { qualities ->
                viewModel.queryQualitySizes(song, qualities)
            },
            onSelect = { quality ->
                downloadQualitySong = null
                viewModel.download(
                    song = song,
                    destinationTreeUri = downloadDirTreeUri,
                    quality = quality,
                )
            },
            onDismiss = { downloadQualitySong = null },
        )
    }
}




@Composable
private fun DownloadTopBar(
    onBack: () -> Unit,
    onOpenDownloadSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    showBackButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }
        } else {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.navigation_menu),
                )
            }
        }
        Text(
            text = stringResource(R.string.music_download),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onOpenDownloadSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.download_settings),
            )
        }
        IconButton(onClick = onOpenProgress) {
            Icon(
                imageVector = Icons.Rounded.Download,
                contentDescription = stringResource(R.string.download_progress_title),
            )
        }
    }
}

@Composable
private fun DownloadQualityDialog(
    song: LxMusicSong,
    qualities: List<String>,
    sizes: Map<String, Long>,
    onQuerySizes: (List<String>) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(song.sourceKey, song.id, qualities) {
        onQuerySizes(qualities)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.download_select_quality)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.download_quality_for, song.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                qualities.forEach { quality ->
                    val size = sizes[buildTaskId(song, quality)]
                    OutlinedButton(
                        onClick = { onSelect(quality) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = qualityLabel(quality),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start,
                        )
                        QualitySizeText(size = size)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun QualitySizeText(size: Long?) {
    when {
        size == null -> CircularProgressIndicator(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp),
            strokeWidth = 2.dp,
        )

        size > 0 -> Text(
            text = formatBytes(size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> Text(
            text = stringResource(R.string.download_quality_size_failed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


@Composable
private fun DownloadProgressScreen(
    tasks: List<MusicDownloadTaskState>,
    onBack: () -> Unit,
    onClearFinished: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val activeTasks = tasks.filter { it.isActive }
    val finishedTasks = tasks.filterNot { it.isActive }
    val visibleTasks = if (selectedTab == 0) activeTasks else finishedTasks
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.download_progress_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (finishedTasks.isNotEmpty()) {
                    TextButton(onClick = onClearFinished) {
                        Text(text = stringResource(R.string.download_clear_finished))
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    label = { Text(stringResource(R.string.download_tab_active)) },
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    label = { Text(stringResource(R.string.download_tab_completed)) },
                )
            }
            if (visibleTasks.isEmpty()) {
                DownloadMessage(
                    icon = Icons.Rounded.Download,
                    title = stringResource(
                        if (selectedTab == 0) {
                            R.string.download_no_active
                        } else {
                            R.string.download_no_completed
                        },
                    ),
                    message = stringResource(
                        if (selectedTab == 0) {
                            R.string.download_tab_active
                        } else {
                            R.string.download_tab_completed
                        },
                    ),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 8.dp,
                        end = 20.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = visibleTasks,
                        key = MusicDownloadTaskState::taskId,
                    ) { task ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                DownloadTaskRow(task = task)
                                if (task.isActive) {
                                    val progress = task.progress
                                    if (progress == null) {
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    isSearching: Boolean,
    searchSources: List<MusicSearchOption>,
    selectedSourceId: String?,
    selectedSourceName: String,
    onSourceChange: (String) -> Unit,
    enabled: Boolean,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            label = { Text(text = stringResource(R.string.download_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_search),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onSearch()
                },
            ),
        )
        Box {
            OutlinedButton(
                onClick = { sourceMenuExpanded = true },
                enabled = enabled && searchSources.isNotEmpty(),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(
                    text = selectedSourceName.ifBlank {
                        stringResource(R.string.download_search_platform)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = stringResource(R.string.download_search_platform),
                )
            }
            DropdownMenu(
                expanded = sourceMenuExpanded,
                onDismissRequest = { sourceMenuExpanded = false },
            ) {
                searchSources.forEach { source ->
                    DropdownMenuItem(
                        text = { Text(text = source.name) },
                        trailingIcon = {
                            if (source.id == selectedSourceId) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                )
                            }
                        },
                        onClick = {
                            sourceMenuExpanded = false
                            onSourceChange(source.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSourceSummary(
    state: MusicDownloadUiState,
    onManage: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = 14.dp,
                    top = 12.dp,
                    end = 8.dp,
                    bottom = 12.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.activeSourceName.ifBlank {
                            stringResource(R.string.download_source_none)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.selectedSearchSourceName.isBlank()) {
                            stringResource(R.string.download_source_search_unavailable)
                        } else {
                            stringResource(
                                R.string.download_source_search_engine,
                                state.selectedSearchSourceName,
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onManage) {
                    Text(text = stringResource(R.string.download_source_manage))
                }
            }
            if (state.isSourceLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                )
            }
        }
    }
}

@Composable
private fun DownloadQualityRow(
    qualities: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.download_quality),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            qualities.forEach { quality ->
                DownloadQualityChip(
                    label = qualityLabel(quality),
                    selected = quality == selected,
                    onClick = { onSelect(quality) },
                )
            }
        }
    }
}

@Composable
internal fun DownloadQualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DownloadTasksSection(
    tasks: List<MusicDownloadTaskState>,
    onClearFinished: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.download_tasks),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (tasks.any { !it.isActive }) {
                    TextButton(onClick = onClearFinished) {
                        Text(text = stringResource(R.string.download_clear_finished))
                    }
                }
            }
            tasks
                .sortedByDescending { it.isActive }
                .take(5)
                .forEach { task ->
                    DownloadTaskRow(task = task)
                }
        }
    }
}

@Composable
private fun DownloadTaskRow(task: MusicDownloadTaskState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (task.phase) {
            MusicDownloadPhase.RESOLVING,
            MusicDownloadPhase.DOWNLOADING,
            MusicDownloadPhase.EMBEDDING,
            -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }

            MusicDownloadPhase.COMPLETED -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            MusicDownloadPhase.FAILED -> {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.song.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = downloadTaskMessage(task),
                style = MaterialTheme.typography.bodySmall,
                color = if (task.phase == MusicDownloadPhase.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = qualityLabel(task.quality),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadSongRow(
    song: LxMusicSong,
    task: MusicDownloadTaskState?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPlay)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, top = 11.dp, end = 6.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(song.artist, song.album)
                    .filter(String::isNotBlank)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.sourceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        when (task?.phase) {
            MusicDownloadPhase.RESOLVING,
            MusicDownloadPhase.DOWNLOADING,
            MusicDownloadPhase.EMBEDDING,
            -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val progress = task.progress
                    if (task.phase == MusicDownloadPhase.DOWNLOADING && progress != null) {
                        // 下载中：进度圆环
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 3.dp,
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                    task.progress?.let { progress ->
                        Text(
                            text = buildString {
                                append("${(progress * 100).toInt()}%")
                                task.totalBytes.takeIf { it > 0L }?.let {
                                    append(" · ")
                                    append(formatBytes(it))
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            MusicDownloadPhase.COMPLETED -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.download_completed),
                    modifier = Modifier
                        .padding(12.dp)
                        .size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            MusicDownloadPhase.FAILED,
            null,
            -> {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = if (task?.phase == MusicDownloadPhase.FAILED) {
                            Icons.Rounded.Refresh
                        } else {
                            Icons.Rounded.Download
                        },
                        contentDescription = if (task?.phase == MusicDownloadPhase.FAILED) {
                            stringResource(R.string.download_retry)
                        } else {
                            stringResource(R.string.download)
                        },
                        tint = if (task?.phase == MusicDownloadPhase.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadMessage(
    icon: ImageVector,
    title: String,
    message: String,
    showProgress: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MetadataPluginManagerDialog(
    state: MusicDownloadUiState,
    lyricsPreferWordLevel: Boolean,
    onLyricsPreferWordLevelChange: (Boolean) -> Unit,
    pluginUrl: String,
    onPluginUrlChange: (String) -> Unit,
    onImportUrl: () -> Unit,
    onPickLocalPlugin: () -> Unit,
    onSetPluginEnabled: (String, Boolean) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismissMessage: () -> Unit,
    embedOptions: DownloadEmbedOptions,
    onEmbedOptionsChange: (DownloadEmbedOptions) -> Unit,
    onClose: () -> Unit,
) {
    ContentDialog(
        title = stringResource(R.string.download_metadata_title),
        onClose = onClose,
        maxHeightDp = 560,
    ) {
        MetadataPluginsContent(
            state = state,
            lyricsPreferWordLevel = lyricsPreferWordLevel,
            onLyricsPreferWordLevelChange = onLyricsPreferWordLevelChange,
            pluginUrl = pluginUrl,
            onPluginUrlChange = onPluginUrlChange,
            onImportUrl = onImportUrl,
            onPickLocalPlugin = onPickLocalPlugin,
            onSetPluginEnabled = onSetPluginEnabled,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDelete = onDelete,
            onDismissMessage = onDismissMessage,
            includeEmbedToggles = true,
            embedOptions = embedOptions,
            onEmbedOptionsChange = onEmbedOptionsChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DownloadSourceManagerDialog(
    state: MusicDownloadUiState,
    importUrl: String,
    onImportUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onPickLocalSource: () -> Unit,
    onActivateSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDismissMessages: () -> Unit,
    onClose: () -> Unit,
) {
    ContentDialog(
        title = stringResource(R.string.download_source_management),
        onClose = onClose,
        maxHeightDp = 520,
    ) {
        DownloadSourceManagerContent(
            state = state,
            importUrl = importUrl,
            onImportUrlChange = onImportUrlChange,
            onImport = onImport,
            onPickLocalSource = onPickLocalSource,
            onActivateSource = onActivateSource,
            onDeleteSource = onDeleteSource,
            onDismissMessages = onDismissMessages,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun buildTaskId(
    song: LxMusicSong,
    quality: String,
): String = "${song.sourceKey}|${song.id}|$quality"

/**
 * 按歌曲查找下载任务（不限音质）：实际下载的音质可能与列表当前选中的不同
 * （音质回退、用户在弹窗里另选），按「源|id」前缀匹配才能让每一行的
 * 进度圆环都正确显示；同首歌有多个任务时优先显示进行中的那个。
 */
private fun taskForSong(
    song: LxMusicSong,
    downloads: Map<String, MusicDownloadTaskState>,
): MusicDownloadTaskState? {
    val prefix = "${song.sourceKey}|${song.id}|"
    var found: MusicDownloadTaskState? = null
    downloads.forEach { (key, value) ->
        if (key.startsWith(prefix)) {
            found = if (value.isActive) value else found ?: value
        }
    }
    return found
}

private fun qualityLabel(quality: String): String = when (quality.lowercase()) {
    "128k" -> "128K"
    "192k" -> "192K"
    "320k" -> "320K"
    "flac" -> "FLAC"
    "flac24bit", "24bit" -> "24BIT"
    "hires" -> "Hi-Res"
    else -> quality.uppercase()
}

@Composable
private fun downloadTaskMessage(task: MusicDownloadTaskState): String = when (
    task.phase
) {
    MusicDownloadPhase.RESOLVING -> stringResource(R.string.download_resolving)
    MusicDownloadPhase.DOWNLOADING -> {
        val progress = task.progress
        val downloaded = formatBytes(task.downloadedBytes)
        when {
            progress == null -> stringResource(R.string.download_downloading)
            task.totalBytes > 0L -> stringResource(
                R.string.download_progress_size,
                downloaded,
                formatBytes(task.totalBytes),
            )

            else -> stringResource(R.string.download_progress_size_unknown, downloaded)
        }
    }

    MusicDownloadPhase.EMBEDDING -> stringResource(
        R.string.download_embedding_size,
        formatBytes(task.downloadedBytes),
    )

    MusicDownloadPhase.COMPLETED -> listOfNotNull(
        task.downloadedBytes.takeIf { it > 0L }?.let(::formatBytes),
        task.displayPath,
        task.embedNote?.takeIf(String::isNotBlank),
    ).joinToString("  ·  ")
        .ifBlank { stringResource(R.string.download_completed) }

    MusicDownloadPhase.FAILED -> task.message
        ?: stringResource(R.string.download_failed)
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "${bytes}B"
}

private val DEFAULT_QUALITY_OPTIONS = listOf("128k", "320k", "flac", "flac24bit")
