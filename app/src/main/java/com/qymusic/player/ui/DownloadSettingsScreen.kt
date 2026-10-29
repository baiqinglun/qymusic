package com.qymusic.player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.qymusic.player.R
import com.qymusic.player.data.DownloadEmbedOptions
import com.qymusic.player.data.DownloadNamingFormat
import com.qymusic.player.data.LxMusicSource
import com.qymusic.player.data.MetadataPlugin
import kotlinx.coroutines.launch

/** 下载设置页的三个分区。 */
private enum class DownloadSettingsTab(val labelRes: Int) {
    SOURCES(R.string.download_settings_tab_sources),
    METADATA(R.string.download_settings_tab_metadata),
    OTHERS(R.string.download_settings_tab_others),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSettingsScreen(
    viewModel: MusicDownloadViewModel,
    downloadDirTreeUri: String?,
    onBack: () -> Unit,
    onChooseDownloadDir: () -> Unit,
    onResetDownloadDir: () -> Unit = {},
    showBackButton: Boolean = true,
    onOpenDrawer: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val localSourcePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let(viewModel::importLocalSource)
    }
    val pluginFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let(viewModel::importMetadataPluginLocal)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var importUrl by rememberSaveable { mutableStateOf("") }
    var pluginUrl by rememberSaveable { mutableStateOf("") }

    val pagerState = rememberPagerState(initialPage = selectedTab, pageCount = { DownloadSettingsTab.entries.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            selectedTab = page
        }
    }
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(notice)
        viewModel.dismissNotice()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
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
                    text = stringResource(R.string.download_settings_page_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            DownloadSettingsTabBar(
                selectedTab = selectedTab,
                onSelect = { tab ->
                    selectedTab = tab
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (DownloadSettingsTab.entries[page]) {
                    DownloadSettingsTab.SOURCES -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        DownloadSourceManagerContent(
                            state = state,
                            importUrl = importUrl,
                            onImportUrlChange = { importUrl = it },
                            onImport = { viewModel.importSource(importUrl) },
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
                            onActivateSource = viewModel::activateSource,
                            onDeleteSource = viewModel::deleteSource,
                            onDismissMessages = viewModel::dismissImportMessage,
                        )
                    }

                    DownloadSettingsTab.METADATA -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        MetadataPluginsContent(
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
                            includeEmbedToggles = false,
                            embedOptions = state.embedOptions,
                            onEmbedOptionsChange = viewModel::setEmbedOptions,
                        )
                    }

                    DownloadSettingsTab.OTHERS -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DownloadDirectoryRow(
                            treeUri = downloadDirTreeUri,
                            onChange = onChooseDownloadDir,
                            onReset = onResetDownloadDir,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.download_naming_format),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    DownloadNamingFormat.entries.forEach { format ->
                                        DownloadQualityChip(
                                            label = format.label,
                                            selected = format == state.namingFormat,
                                            onClick = { viewModel.setNamingFormat(format) },
                                        )
                                    }
                                }
                            }
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                MetadataEmbedToggle(
                                    title = stringResource(R.string.download_metadata_write_tags),
                                    summary = stringResource(R.string.download_metadata_write_tags_summary),
                                    checked = state.embedOptions.writeMetadata,
                                    onCheckedChange = {
                                        viewModel.setEmbedOptions(
                                            state.embedOptions.copy(writeMetadata = it),
                                        )
                                    },
                                )
                                MetadataEmbedToggle(
                                    title = stringResource(R.string.download_metadata_embed_lyrics),
                                    summary = stringResource(R.string.download_metadata_embed_lyrics_summary),
                                    checked = state.embedOptions.embedLyrics,
                                    onCheckedChange = {
                                        viewModel.setEmbedOptions(
                                            state.embedOptions.copy(embedLyrics = it),
                                        )
                                    },
                                )
                                MetadataEmbedToggle(
                                    title = stringResource(R.string.download_metadata_embed_cover),
                                    summary = stringResource(R.string.download_metadata_embed_cover_summary),
                                    checked = state.embedOptions.embedCover,
                                    onCheckedChange = {
                                        viewModel.setEmbedOptions(
                                            state.embedOptions.copy(embedCover = it),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 顶部 Tab 栏：横向滑动查看全部分区，点击直接跳转；
 * 选中项高亮并自动滚动至可见区域。
 */
@Composable
private fun DownloadSettingsTabBar(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedTab) {
        if (selectedTab >= 0) {
            listState.animateScrollToItem(selectedTab)
        }
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(DownloadSettingsTab.entries.size) { index ->
                val tab = DownloadSettingsTab.entries[index]
                val selected = index == selectedTab
                Surface(
                    modifier = Modifier.clickable(
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

/* ---- 以下内容组件同时被下载页弹窗与下载设置页复用 ---- */

@Composable
internal fun DownloadSourceManagerContent(
    state: MusicDownloadUiState,
    importUrl: String,
    onImportUrlChange: (String) -> Unit,
    onImport: () -> Unit,
    onPickLocalSource: () -> Unit,
    onActivateSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = importUrl,
            onValueChange = onImportUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(R.string.download_source_url)) },
            placeholder = { Text(text = stringResource(R.string.download_source_url_hint)) },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Link, contentDescription = null)
            },
        )
        Button(
            onClick = onImport,
            enabled = !state.isImporting && importUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.isImporting) {
                    stringResource(R.string.download_source_importing)
                } else {
                    stringResource(R.string.download_source_import)
                },
            )
        }
        OutlinedButton(
            onClick = onPickLocalSource,
            enabled = !state.isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.download_source_import_local))
        }
        state.importError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.importSuccess?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.importError != null || state.importSuccess != null) {
            TextButton(onClick = onDismissMessages) {
                Text(text = stringResource(R.string.close))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Text(
            text = stringResource(R.string.download_source_imported),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.sources.isEmpty()) {
            Text(
                text = stringResource(R.string.download_source_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.sources.forEach { source ->
                DownloadSourceRow(
                    source = source,
                    selected = source.id == state.activeSourceId,
                    onActivate = { onActivateSource(source.id) },
                    onDelete = { onDeleteSource(source.id) },
                )
            }
        }
    }
}

@Composable
internal fun MetadataPluginsContent(
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
    includeEmbedToggles: Boolean,
    embedOptions: DownloadEmbedOptions,
    onEmbedOptionsChange: (DownloadEmbedOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plugins = state.metadataPlugins.plugins
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 拉取歌词类型：逐字 / 普通（同时作用于下载嵌入与标签编辑的抓取）
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.download_lyrics_fetch_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DownloadQualityChip(
                        label = stringResource(R.string.download_lyrics_type_word),
                        selected = lyricsPreferWordLevel,
                        onClick = { onLyricsPreferWordLevelChange(true) },
                    )
                    DownloadQualityChip(
                        label = stringResource(R.string.download_lyrics_type_line),
                        selected = !lyricsPreferWordLevel,
                        onClick = { onLyricsPreferWordLevelChange(false) },
                    )
                }
                Text(
                    text = stringResource(R.string.download_lyrics_type_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        if (includeEmbedToggles) {
            MetadataEmbedToggle(
                title = stringResource(R.string.download_metadata_write_tags),
                summary = stringResource(R.string.download_metadata_write_tags_summary),
                checked = embedOptions.writeMetadata,
                onCheckedChange = { onEmbedOptionsChange(embedOptions.copy(writeMetadata = it)) },
            )
            MetadataEmbedToggle(
                title = stringResource(R.string.download_metadata_embed_lyrics),
                summary = stringResource(R.string.download_metadata_embed_lyrics_summary),
                checked = embedOptions.embedLyrics,
                onCheckedChange = { onEmbedOptionsChange(embedOptions.copy(embedLyrics = it)) },
            )
            MetadataEmbedToggle(
                title = stringResource(R.string.download_metadata_embed_cover),
                summary = stringResource(R.string.download_metadata_embed_cover_summary),
                checked = embedOptions.embedCover,
                onCheckedChange = { onEmbedOptionsChange(embedOptions.copy(embedCover = it)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
        Text(
            text = stringResource(R.string.download_metadata_plugins),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (plugins.isEmpty()) {
            Text(
                text = stringResource(R.string.download_metadata_plugin_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            plugins.forEach { plugin ->
                MetadataPluginRow(
                    plugin = plugin,
                    enabled = state.metadataPlugins.isEnabled(plugin.id),
                    isFirst = plugin.id == plugins.first().id,
                    isLast = plugin.id == plugins.last().id,
                    onEnabledChange = { onSetPluginEnabled(plugin.id, it) },
                    onMoveUp = { onMoveUp(plugin.id) },
                    onMoveDown = { onMoveDown(plugin.id) },
                    onDelete = { onDelete(plugin.id) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Text(
            text = stringResource(R.string.download_metadata_plugin_import),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = pluginUrl,
            onValueChange = onPluginUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(text = stringResource(R.string.download_metadata_plugin_url)) },
            placeholder = {
                Text(text = stringResource(R.string.download_metadata_plugin_url_hint))
            },
            leadingIcon = {
                Icon(imageVector = Icons.Rounded.Link, contentDescription = null)
            },
        )
        Button(
            onClick = onImportUrl,
            enabled = !state.metadataPlugins.isImporting && pluginUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.metadataPlugins.isImporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.metadataPlugins.isImporting) {
                    stringResource(R.string.download_metadata_plugin_importing)
                } else {
                    stringResource(R.string.download_metadata_plugin_import)
                },
            )
        }
        OutlinedButton(
            onClick = onPickLocalPlugin,
            enabled = !state.metadataPlugins.isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.download_metadata_plugin_local))
        }
        state.metadataPlugins.importError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.metadataPlugins.importMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (state.metadataPlugins.importError != null ||
            state.metadataPlugins.importMessage != null
        ) {
            TextButton(onClick = onDismissMessage) {
                Text(text = stringResource(R.string.close))
            }
        }
    }
}

@Composable
internal fun MetadataEmbedToggle(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun MetadataPluginRow(
    plugin: MetadataPlugin,
    enabled: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (plugin.builtin) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.download_metadata_plugin_builtin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val detail = listOfNotNull(
                plugin.capabilityLabel(),
                plugin.manifest.versionName.takeIf(String::isNotBlank)?.let { "v$it" },
                plugin.manifest.author.takeIf(String::isNotBlank),
            ).joinToString(" · ")
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMoveUp, enabled = !isFirst) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowUp,
                contentDescription = stringResource(R.string.download_metadata_plugin_move_up),
            )
        }
        IconButton(onClick = onMoveDown, enabled = !isLast) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.download_metadata_plugin_move_down),
            )
        }
        if (!plugin.builtin) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.delete),
                )
            }
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
internal fun DownloadSourceRow(
    source: LxMusicSource,
    selected: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
            )
            .clickable(role = Role.RadioButton, onClick = onActivate)
            .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = listOf(
                source.version.takeIf(String::isNotBlank)?.let { "v$it" },
                source.description.takeIf(String::isNotBlank),
            ).filterNotNull().joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
}

@Composable
internal fun DownloadDirectoryRow(
    treeUri: String?,
    onChange: () -> Unit,
    onReset: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.download_directory),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = downloadDirectoryLabel(treeUri),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChange) {
                Text(text = stringResource(R.string.download_directory_change))
            }
            if (treeUri != null) {
                TextButton(onClick = onReset) {
                    Text(
                        text = stringResource(R.string.download_directory_reset),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun downloadDirectoryLabel(treeUri: String?): String {
    val context = LocalContext.current
    return remember(treeUri, context) {
        treeUri
            ?.takeIf(String::isNotBlank)
            ?.let { uri ->
                runCatching {
                    DocumentFile.fromTreeUri(context, Uri.parse(uri))?.name
                }.getOrNull()
            }
            ?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.download_directory_default)
    }
}

/** 弹窗包装：把某段设置内容装进对话框（下载页内的快捷入口）。 */
@Composable
internal fun ContentDialog(
    title: String,
    onClose: () -> Unit,
    maxHeightDp: Int = 560,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeightDp.dp),
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text(text = stringResource(R.string.close))
            }
        },
    )
}
