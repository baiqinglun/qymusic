package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.CollectionViewMode
import com.qymusic.player.data.MusicFolder
import com.qymusic.player.data.Track
import com.qymusic.player.data.UserPlaylist

/** 文件夹详情页整屏推入 / 退回的时长，与曲库详情页一致。 */
private const val FOLDER_DETAIL_SLIDE_MS = 280

@Composable
fun FoldersScreen(
    folders: List<MusicFolder>,
    tracks: List<Track>,
    isLoading: Boolean,
    viewMode: CollectionViewMode,
    onViewModeChange: (CollectionViewMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onPlayAllTracks: (List<Track>, Boolean) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    playlists: List<UserPlaylist>,
    onCreatePlaylist: (String, String?) -> Unit,
    onSetTrackInPlaylist: (Long, String, Boolean) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val countByFolder = tracks.groupingBy(Track::folderName).eachCount()
    // 加载期间显示骨架屏；数据到位后骨架淡出，条目分块浮现（只播一次）。
    val skeletonOverlay = rememberSkeletonOverlay(isLoading)
    var revealDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (!isLoading) revealDone = true
    }

    // 打开的文件夹详情（用 uri 字符串作 key，可随进程恢复）。
    var selectedFolderKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFolder = remember(selectedFolderKey, folders) {
        selectedFolderKey?.let { key -> folders.firstOrNull { it.uri.toString() == key } }
    }
    val selectedTracks = remember(selectedFolder, tracks) {
        selectedFolder?.let { folder -> tracks.filter { it.folderName == folder.name } }
            .orEmpty()
    }

    // 滚动状态提升：列表页 / 详情页会被 AnimatedContent 移出组合，
    // 状态放在内部组件里返回时就会丢位置。
    val foldersListState = rememberLazyListState()
    val foldersGridState = rememberLazyGridState()
    val folderDetailListState = rememberLazyListState()

    var addToPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var propertiesTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistSeedTrack by remember { mutableStateOf<Track?>(null) }

    BackHandler(enabled = selectedFolderKey != null) {
        selectedFolderKey = null
    }

    // 页面没有 Scaffold：包一层 Surface 提供 LocalContentColor（= onSurface），
    // 否则暗色模式下顶栏等未显式指定颜色的文字仍是默认黑色。
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {

    AnimatedContent(
        targetState = selectedFolderKey,
        transitionSpec = {
            if (targetState == null) {
                // 返回列表：详情页整屏右移退出、列表从左侧推回。
                slideInHorizontally(tween(FOLDER_DETAIL_SLIDE_MS)) { -it } togetherWith
                    slideOutHorizontally(tween(FOLDER_DETAIL_SLIDE_MS)) { it }
            } else {
                // 进入详情页：新页整屏从右侧推入、旧页整屏退出。
                slideInHorizontally(tween(FOLDER_DETAIL_SLIDE_MS)) { it } togetherWith
                    slideOutHorizontally(tween(FOLDER_DETAIL_SLIDE_MS)) { -it }
            }
        },
        label = "folders-page",
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) { key ->
        if (key == null) {
            FoldersListPage(
                folders = folders,
                tracks = tracks,
                countByFolder = countByFolder,
                viewMode = viewMode,
                listState = foldersListState,
                gridState = foldersGridState,
                onViewModeChange = onViewModeChange,
                onOpenDrawer = onOpenDrawer,
                onAddFolder = onAddFolder,
                onRescan = onRescan,
                skeletonOverlay = skeletonOverlay,
                revealDone = revealDone,
                onOpenFolder = { selectedFolderKey = it },
            )
        } else {
            FoldersDetailPage(
                folder = selectedFolder,
                folderTracks = selectedTracks,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                listState = folderDetailListState,
                artworkCache = artworkCache,
                onRequestArtwork = onRequestArtwork,
                onBack = { selectedFolderKey = null },
                onPlayTrack = onPlayTrack,
                onPlayNext = onPlayNext,
                onPlayAllTracks = onPlayAllTracks,
                onAddToPlaylist = { addToPlaylistTrack = it },
                onShowProperties = { propertiesTrack = it },
                onEditTags = onEditTags,
                onDeleteTrack = onDeleteTrack,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
            )
        }
    }

    }
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
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
                        onCreatePlaylist(playlistName, playlistSeedTrack?.id)
                        showCreatePlaylistDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    addToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            trackId = track.id,
            onDismiss = { addToPlaylistTrack = null },
            onTogglePlaylist = { playlistId, included ->
                onSetTrackInPlaylist(playlistId, track.id, included)
            },
            onCreatePlaylist = {
                addToPlaylistTrack = null
                playlistSeedTrack = track
                playlistName = ""
                showCreatePlaylistDialog = true
            },
        )
    }

    propertiesTrack?.let { track ->
        TrackPropertiesDialog(
            track = track,
            onDismiss = { propertiesTrack = null },
        )
    }
}

/** 文件夹列表页：顶部标题行 + 添加 / 重扫按钮 + 集合列表。 */
@Composable
private fun FoldersListPage(
    folders: List<MusicFolder>,
    tracks: List<Track>,
    countByFolder: Map<String, Int>,
    viewMode: CollectionViewMode,
    listState: LazyListState,
    gridState: LazyGridState,
    onViewModeChange: (CollectionViewMode) -> Unit,
    onOpenDrawer: () -> Unit,
    onAddFolder: () -> Unit,
    onRescan: () -> Unit,
    skeletonOverlay: SkeletonOverlayState,
    revealDone: Boolean,
    onOpenFolder: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 和主页对齐：内容从状态栏下方开始。
                .statusBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.navigation_menu),
                )
            }
            Text(
                text = stringResource(R.string.folders_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            ViewModeToggleButton(
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = onAddFolder) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.add_folder))
            }
            OutlinedButton(onClick = onRescan) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.rescan))
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            CollectionContent(
                listState = listState,
                gridState = gridState,
                items = folders.map { folder ->
                    CollectionItem(
                        key = folder.uri.toString(),
                        title = folder.name,
                        subtitle = folder.uri.toString(),
                        trackCount = countByFolder[folder.name] ?: 0,
                        cover = { coverModifier ->
                            Box(
                                modifier = coverModifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                },
                viewMode = viewMode,
                reveal = !revealDone,
                emptyContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.empty_library_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.empty_library_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onItemClick = { item -> onOpenFolder(item.key) },
            )
            // 数据刚到位时骨架仍盖在列表上淡出，与条目的分块浮现交叉进行。
            if (skeletonOverlay.showing) {
                CollectionSkeleton(
                    viewMode = viewMode,
                    showSubtitle = true,
                    showChevron = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = skeletonOverlay.alpha },
                )
            }
        }
    }
}

/** 文件夹详情页：与专辑详情页同款布局——返回行 + 头部（图标 / 标题 / 播放 / 随机）+ 曲目列表。 */
@Composable
private fun FoldersDetailPage(
    folder: MusicFolder?,
    folderTracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    listState: LazyListState,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onPlayAllTracks: (List<Track>, Boolean) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowProperties: (Track) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    val unknownAlbum = stringResource(R.string.unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val totalDurationMs = remember(folderTracks) { folderTracks.sumOf { it.durationMs } }
    val trackCountDuration = stringResource(
        R.string.track_count_duration,
        folderTracks.size,
        formatDuration(totalDurationMs),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        // 返回单独一行，与曲库详情页一致；让出状态栏。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back_to_list),
                )
            }
        }
        // 封面、信息、播放、随机同一行。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder?.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = trackCountDuration,
                    modifier = Modifier.padding(vertical = 3.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            FolderDetailActionButton(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.play),
                onClick = {
                    folderTracks.firstOrNull()?.let { track ->
                        onPlayTrack(track, folderTracks)
                    }
                },
            )
            FolderDetailActionButton(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.library_shuffle_all),
                onClick = {
                    if (folderTracks.isNotEmpty()) {
                        onPlayAllTracks(folderTracks, true)
                    }
                },
            )
        }
        if (folderTracks.isEmpty()) {
            EmptyLibrary(
                icon = Icons.Rounded.FolderOpen,
                title = stringResource(R.string.empty_library_title),
                message = stringResource(R.string.empty_library_message),
            )
        } else {
            TrackList(
                tracks = folderTracks,
                listState = listState,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                artworkCache = artworkCache,
                onRequestArtwork = onRequestArtwork,
                onPlayTrack = { track -> onPlayTrack(track, folderTracks) },
                onPlayNext = onPlayNext,
                onAddToPlaylist = onAddToPlaylist,
                onShowProperties = onShowProperties,
                onEditTags = onEditTags,
                onDeleteTrack = onDeleteTrack,
                onOpenAlbum = { track ->
                    onOpenAlbum(track.album.ifBlank { unknownAlbum })
                },
                onOpenArtist = { track ->
                    onOpenArtist(track.artist.ifBlank { unknownArtist })
                },
            )
        }
    }
}

@Composable
private fun FolderDetailActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(38.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
