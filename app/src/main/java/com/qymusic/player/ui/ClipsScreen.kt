package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.CLIP_TYPES
import com.qymusic.player.data.ClipPlaybackRule
import com.qymusic.player.data.MusicClip
import com.qymusic.player.data.clipMatchesFilter
import com.qymusic.player.data.musicClipDisplayName
import androidx.compose.ui.graphics.asImageBitmap
import com.qymusic.player.data.Track
import java.util.Locale
import kotlin.math.roundToInt

private val CLIP_TAG_SUGGESTIONS = listOf("副歌", "前奏", "间奏", "高潮", "尾奏")

/** 显示模式：片段平铺列表 / 按歌曲分组。 */
private enum class ClipViewMode { LIST, SONGS }

/** 排序：按片段名称 / 按添加时间 / 按所属歌曲。 */
private enum class ClipSortMode(val labelRes: Int) {
    NAME(R.string.sort_by_clip_name),
    CREATED(R.string.sort_by_created),
    SONG(R.string.sort_by_song),
}

/** 新建 / 编辑片段的底部弹窗请求；clip 为空表示新建。 */
private data class ClipEditRequest(val track: Track, val clip: MusicClip?)

private fun formatClipRange(startMs: Long, endMs: Long): String {
    fun fmt(ms: Long): String {
        val totalSeconds = ms / 1000
        val cents = (ms % 1000) / 100
        return String.format(
            Locale.US, "%d:%02d.%d",
            totalSeconds / 60, totalSeconds % 60, cents,
        )
    }
    return fmt(startMs) + " ~ " + fmt(endMs)
}

private fun formatClipTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val cents = (ms % 1000) / 100
    return String.format(
        Locale.US, "%d:%02d.%d",
        totalSeconds / 60, totalSeconds % 60, cents,
    )
}

private fun parseClipSeconds(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(":")
    val seconds = runCatching {
        if (parts.size == 2) {
            parts[0].trim().toDouble() * 60 + parts[1].trim().toDouble()
        } else {
            trimmed.toDouble()
        }
    }.getOrNull() ?: return null
    return (seconds * 1000).toLong().coerceAtLeast(0L)
}

/** 音乐片段页：与歌曲列表页一致的顶栏与操作，列表 / 按歌曲两种显示模式。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/** 封面过渡覆盖层：从屏幕中心飞向底部播放控制栏的封面位。 */
@Composable
internal fun CoverFlightOverlay(progress: Float, bitmap: android.graphics.Bitmap) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenW = configuration.screenWidthDp.dp
    val screenH = configuration.screenHeightDp.dp
    val startSide = 300.dp
    val endSide = 44.dp
    val side = startSide + (endSide - startSide) * progress
    val startX = (screenW - startSide) / 2
    val startY = (screenH - startSide) / 2
    val endX = 12.dp
    val endY = screenH - 96.dp
    val x = startX + (endX - startX) * progress
    val y = startY + (endY - startY) * progress
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    with(density) { x.roundToPx() },
                    with(density) { y.roundToPx() },
                )
            }
            .size(side)
            .clip(RoundedCornerShape(10.dp)),
    ) {
        androidx.compose.foundation.Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ClipsScreen(
    viewModel: MusicViewModel,
    tracks: List<Track>,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val clips by viewModel.musicClips.collectAsState()
    val clipPlayback by viewModel.clipPlayback.collectAsState()
    val clipRules by viewModel.clipRules.collectAsState()
    val tracksById = remember(tracks) { tracks.associateBy { it.id } }

    var viewMode by rememberSaveable { mutableStateOf(ClipViewMode.LIST.name) }
    var sortMode by rememberSaveable { mutableStateOf(ClipSortMode.NAME.name) }
    val view = ClipViewMode.valueOf(viewMode)
    val sort = ClipSortMode.valueOf(sortMode)

    var editRequest by remember { mutableStateOf<ClipEditRequest?>(null) }
    var showSongPicker by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showRulesSheet by remember { mutableStateOf(false) }
    var activeSearch by remember { mutableStateOf<ClipSearchFilter?>(null) }
    var deleteClip by remember { mutableStateOf<MusicClip?>(null) }
    var sheetTrackId by remember { mutableStateOf<String?>(null) }
    var rowSheetClip by remember { mutableStateOf<MusicClip?>(null) }

    val sortedClips = remember(clips, sort) {
        when (sort) {
            ClipSortMode.NAME -> clips.sortedWith(
                compareBy(
                    { (tracksById[it.trackId]?.title ?: " ") + formatClipTime(it.startMs) },
                ),
            )
            ClipSortMode.CREATED -> clips.sortedByDescending { it.createdAtMs }
            ClipSortMode.SONG -> clips.sortedWith(
                compareBy(
                    { tracksById[it.trackId]?.title?.lowercase(Locale.US) ?: " " },
                    { it.startMs },
                ),
            )
        }
    }
    // 搜索筛选只影响列表展示，顺序 / 随机播放仍针对全部片段
    val displayedClips = remember(sortedClips, activeSearch, tracksById) {
        val filter = activeSearch
        if (filter == null) {
            sortedClips
        } else {
            sortedClips.filter { clip ->
                clipMatchesFilter(
                    clip = clip,
                    trackTitle = tracksById[clip.trackId]?.title.orEmpty(),
                    query = filter.query,
                    types = filter.types,
                    tags = filter.tags,
                    requireAll = filter.requireAll,
                )
            }
        }
    }
    val groupedByTrack = remember(displayedClips) { displayedClips.groupBy { it.trackId } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 顶部操作栏与缓存 / 播放历史等页面统一：状态栏留白 + 64dp 高度
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                    text = stringResource(R.string.clips_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showSearchSheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.clips_search),
                    )
                }
                IconButton(onClick = { showRulesSheet = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Rule,
                        contentDescription = stringResource(R.string.clips_rules),
                    )
                }
                IconButton(onClick = {
                    viewMode = if (view == ClipViewMode.LIST) {
                        ClipViewMode.SONGS.name
                    } else {
                        ClipViewMode.LIST.name
                    }
                }) {
                    Icon(
                        imageVector = if (view == ClipViewMode.LIST) {
                            Icons.Rounded.GridView
                        } else {
                            Icons.Rounded.ViewList
                        },
                        contentDescription = stringResource(R.string.clips_toggle_view),
                    )
                }
                IconButton(onClick = {
                    sortMode = when (sort) {
                        ClipSortMode.NAME -> ClipSortMode.CREATED.name
                        ClipSortMode.CREATED -> ClipSortMode.SONG.name
                        ClipSortMode.SONG -> ClipSortMode.NAME.name
                    }
                }) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = stringResource(sort.labelRes),
                    )
                }
            }
        },
        floatingActionButton = {
            Surface(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
            ) {
                Box(
                    modifier = Modifier
                        .clickable { showSongPicker = true }
                        .padding(14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.clip_new),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // 列表上方：片段数量 + 顺序 / 随机播放
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (clips.isEmpty()) {
                        stringResource(R.string.clips_empty)
                    } else {
                        stringResource(R.string.clips_summary, clips.size, groupedByTrack.size) +
                            " · " + stringResource(sort.labelRes)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    enabled = clips.isNotEmpty(),
                    onClick = { viewModel.playClipsSequential(sortedClips) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = stringResource(R.string.clips_play_sequential),
                    )
                }
                IconButton(
                    enabled = clips.isNotEmpty(),
                    onClick = { viewModel.playClipsShuffled(sortedClips) },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.clips_play_shuffled),
                    )
                }
            }

            // 搜索生效横幅：显示命中数并可一键清除
            activeSearch?.let { filter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.clip_search_banner_results,
                            displayedClips.size,
                            clips.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.clip_search_clear),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { activeSearch = null }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }

            when {
                clips.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCut,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.clips_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                view == ClipViewMode.SONGS -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(groupedByTrack.entries.toList(), key = { it.key }) { entry ->
                            val track = tracksById[entry.key]
                            Column(
                                modifier = Modifier.clickable { sheetTrackId = entry.key },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (track != null) {
                                        TrackCoverThumbnail(
                                            track = track,
                                            artworkCache = artworkCache,
                                            onRequestArtwork = onRequestArtwork,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Text(
                                    text = track?.title
                                        ?: stringResource(R.string.clip_track_missing),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.clips_song_count,
                                        entry.value.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                    ) {
                        items(displayedClips, key = { it.id }) { clip ->
                            val track = tracksById[clip.trackId]
                            ClipRow(
                                clip = clip,
                                track = track,
                                artworkCache = artworkCache,
                                onRequestArtwork = onRequestArtwork,
                                playing = clipPlayback.clip?.id == clip.id,
                                onClick = { viewModel.playMusicClip(clip) },
                                onExpand = { rowSheetClip = clip },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 84.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSongPicker) {
        SongPickerDialog(
            tracks = tracks,
            onDismiss = { showSongPicker = false },
            onPick = { track ->
                showSongPicker = false
                editRequest = ClipEditRequest(track, null)
            },
        )
    }

    if (showSearchSheet) {
        ClipSearchSheet(
            clips = clips,
            tracksById = tracksById,
            initial = activeSearch,
            onDismiss = { showSearchSheet = false },
            onApply = {
                activeSearch = it
                showSearchSheet = false
            },
        )
    }

    if (showRulesSheet) {
        ClipRulesSheet(
            clips = clips,
            tracksById = tracksById,
            rules = clipRules,
            onDismiss = { showRulesSheet = false },
            onPlayRule = { query, types, tags, requireAll ->
                showRulesSheet = false
                viewModel.playFilteredClips(query, types, tags, requireAll)
            },
            onSaveRule = viewModel::saveClipRule,
            onRemoveRule = viewModel::removeClipRule,
        )
    }

    editRequest?.let { request ->
        ClipEditSheet(
            request = request,
            trackTitle = request.track.title,
            trackDurationMs = request.track.durationMs,
            lyricsLoader = viewModel::loadTrackLyricLines,
            onDismiss = { editRequest = null },
            onConfirm = { type, tag, startMs, endMs ->
                editRequest = null
                val base = request.clip
                if (base == null) {
                    viewModel.addMusicClip(request.track.id, type, tag, startMs, endMs)
                } else {
                    viewModel.updateMusicClip(base, type, tag, startMs, endMs)
                }
            },
        )
    }

    rowSheetClip?.let { clip ->
        val rowTrack = tracksById[clip.trackId]
        ModalBottomSheet(
            onDismissRequest = { rowSheetClip = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = musicClipDisplayName(
                        rowTrack?.title ?: stringResource(R.string.clip_track_missing),
                        clip.startMs, clip.endMs,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = listOfNotNull(
                        rowTrack?.title,
                        rowTrack?.artist?.takeIf { it.isNotBlank() },
                        formatClipRange(clip.startMs, clip.endMs),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                SheetActionRow(
                    icon = Icons.Rounded.Edit,
                    label = stringResource(R.string.edit),
                    color = Color(0xFF9C27B0),
                    onClick = {
                        rowSheetClip = null
                        if (rowTrack != null) editRequest = ClipEditRequest(rowTrack, clip)
                    },
                )
                SheetActionRow(
                    icon = Icons.Rounded.PlaylistAdd,
                    label = stringResource(R.string.clip_play_next),
                    color = Color(0xFF3D7FFF),
                    onClick = {
                        rowSheetClip = null
                        viewModel.playClipNext(clip)
                    },
                )
                SheetActionRow(
                    icon = Icons.Rounded.Delete,
                    label = stringResource(R.string.delete),
                    color = Color(0xFFE53935),
                    onClick = {
                        rowSheetClip = null
                        deleteClip = clip
                    },
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    deleteClip?.let { clip ->
        AlertDialog(
            onDismissRequest = { deleteClip = null },
            title = { Text(text = stringResource(R.string.clip_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.clip_delete_confirm_message,
                        musicClipDisplayName(
                            tracksById[clip.trackId]?.title
                                ?: stringResource(R.string.clip_track_missing),
                            clip.startMs, clip.endMs,
                        ),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteClip = null
                        viewModel.removeMusicClip(clip.id)
                    },
                ) { Text(text = stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteClip = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    // 按歌曲模式下点歌：底部弹窗列出这首歌的全部片段
    sheetTrackId?.let { trackId ->
        val trackClips = groupedByTrack[trackId].orEmpty().sortedBy { it.startMs }
        val track = tracksById[trackId]
        ModalBottomSheet(
            onDismissRequest = { sheetTrackId = null },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = track?.title ?: stringResource(R.string.clip_track_missing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                if (trackClips.isEmpty()) {
                    Text(
                        text = stringResource(R.string.clips_none_for_track),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                trackClips.forEach { clip ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sheetTrackId = null
                                viewModel.playMusicClip(clip)
                            }
                            .padding(vertical = 10.dp),
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
                        track?.title ?: stringResource(R.string.clip_track_missing),
                        clip.startMs, clip.endMs,
                    ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = listOf(clip.tag, formatClipRange(clip.startMs, clip.endMs))
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ClipRow(
    clip: MusicClip,
    track: Track?,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    playing: Boolean,
    onClick: () -> Unit,
    onExpand: () -> Unit,
) {
    // 行样式与歌曲列表一致：无卡片底、72dp 行高、左侧 20dp 缩进，行间细分隔线由列表画
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(start = 20.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (track != null) {
                TrackCoverThumbnail(
                    track = track,
                    artworkCache = artworkCache,
                    onRequestArtwork = onRequestArtwork,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
                Text(
                    text = track?.title ?: stringResource(R.string.clip_track_missing),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (playing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (clip.type.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = clip.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (clip.tag.isNotBlank() && clip.tag != clip.type) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = clip.tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
        }
        Text(
            text = formatClipRange(clip.startMs, clip.endMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onExpand) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 底部弹窗动作行：彩色圆角图标 + 文字。 */
@Composable
private fun SheetActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 选歌对话框：从曲库选要截取片段的歌曲。 */
@Composable
private fun SongPickerDialog(
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val filtered = remember(keyword, tracks) {
        val q = keyword.trim().lowercase(Locale.US)
        if (q.isEmpty()) tracks
        else tracks.filter { track ->
            track.title.lowercase(Locale.US).contains(q) ||
                track.artist.lowercase(Locale.US).contains(q)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.clip_pick_song)) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    placeholder = { Text(text = stringResource(R.string.clip_pick_song_hint)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it.id }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onDismiss()
                                    onPick(track)
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        },
    )
}

/**
 * 新建 / 编辑片段的底部弹窗：顶部两个 tab——
 * 基础信息（名称 / 标签）、调整始末位置（边界选择 / 区间条 / 精调 / 歌词对时 / 时间填写）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ClipEditSheet(
    request: ClipEditRequest,
    trackTitle: String,
    trackDurationMs: Long,
    lyricsLoader: suspend (Track) -> List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onConfirm: (type: String, tag: String, startMs: Long, endMs: Long) -> Unit,
) {
    val clip = request.clip
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    var type by remember { mutableStateOf(clip?.type.orEmpty()) }
    var tag by remember { mutableStateOf(clip?.tag.orEmpty()) }
    var startMs by remember { mutableLongStateOf(clip?.startMs ?: 0L) }
    var endMs by remember { mutableLongStateOf(clip?.endMs ?: trackDurationMs) }
    // 当前正在调整的边界：0 = 起点，1 = 末尾
    var boundary by remember { mutableStateOf(0) }
    var lyricLines by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }
    var lyricsLoading by remember { mutableStateOf(true) }

    val duration = trackDurationMs.coerceAtLeast(1000L)
    val selectedMs = if (boundary == 0) startMs else endMs

    LaunchedEffect(request.track) {
        lyricLines = runCatching { lyricsLoader(request.track) }.getOrDefault(emptyList())
        lyricsLoading = false
    }

    fun applyBoundary(ms: Long) {
        val clamped = ms.coerceIn(0L, duration)
        if (boundary == 0) {
            startMs = clamped.coerceAtMost(endMs - 100)
        } else {
            endMs = clamped.coerceAtLeast(startMs + 100)
        }
    }

    val confirmValid = endMs > startMs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(
                    if (clip == null) R.string.clip_new else R.string.clip_edit,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            TabRow(
                selectedTabIndex = tabIndex,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    text = { Text(text = stringResource(R.string.clip_tab_basic)) },
                )
                Tab(
                    selected = tabIndex == 1,
                    onClick = { tabIndex = 1 },
                    text = { Text(text = stringResource(R.string.clip_tab_position)) },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tabIndex == 0) {
                    Text(
                        text = stringResource(R.string.clip_of_song, trackTitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // 类型：预设单选，再次点选可取消
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        type = if (selected) "" else candidate
                                    }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CLIP_TAG_SUGGESTIONS.forEach { suggestion ->
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (tag == suggestion) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { tag = suggestion }
                                    .background(
                                        if (tag == suggestion) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    // 边界选择：当前正在调整起点还是末尾
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            stringResource(R.string.clip_start) to 0,
                            stringResource(R.string.clip_end) to 1,
                        ).forEach { (label, index) ->
                            val selected = boundary == index
                            Text(
                                text = label + " " + formatClipTime(
                                    if (index == 0) startMs else endMs,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { boundary = index }
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }

                    // 双端区间条：起点→末尾 填充；两端滑块可拖；点轨道移动选中边界
                    val density = LocalDensity.current
                    var trackWidthPx by remember { mutableStateOf(1f) }
                    val startFraction = (startMs.toFloat() / duration).coerceIn(0f, 1f)
                    val endFraction = (endMs.toFloat() / duration).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    applyBoundary(
                                        (offset.x / trackWidthPx * duration).toLong(),
                                    )
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        (trackWidthPx * startFraction).roundToInt(),
                                        0,
                                    )
                                }
                                .width(
                                    with(density) {
                                        (trackWidthPx * (endFraction - startFraction)).toDp()
                                    },
                                )
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        (trackWidthPx * startFraction).roundToInt() - 33,
                                        0,
                                    )
                                }
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .pointerInput(duration) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { boundary = 0 },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val deltaMs = (
                                            dragAmount / trackWidthPx * duration
                                            ).toLong()
                                        startMs = (startMs + deltaMs)
                                            .coerceIn(0L, endMs - 100)
                                    }
                                },
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    IntOffset(
                                        (trackWidthPx * endFraction).roundToInt() - 33,
                                        0,
                                    )
                                }
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .pointerInput(duration) {
                                    detectHorizontalDragGestures(
                                        onDragStart = { boundary = 1 },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val deltaMs = (
                                            dragAmount / trackWidthPx * duration
                                            ).toLong()
                                        endMs = (endMs + deltaMs)
                                            .coerceIn(startMs + 100, duration)
                                    }
                                },
                        )
                    }
                    // 精调：± 0.1 秒（作用于当前选中的边界）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.clip_fine_adjust),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            applyBoundary(
                                if (boundary == 0) startMs - 100 else endMs - 100,
                            )
                        }) {
                            Text(text = stringResource(R.string.clip_minus_100ms))
                        }
                        TextButton(onClick = {
                            applyBoundary(
                                if (boundary == 0) startMs + 100 else endMs + 100,
                            )
                        }) {
                            Text(text = stringResource(R.string.clip_plus_100ms))
                        }
                    }

                    // 歌词对时：点歌词行把所选边界对到该行时间
                    if (lyricsLoading) {
                        Text(
                            text = stringResource(R.string.clip_lyrics_loading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (lyricLines.isEmpty()) {
                        Text(
                            text = stringResource(R.string.clip_lyrics_missing),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.clip_lyrics_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        ) {
                            itemsIndexed(
                                lyricLines,
                                key = { _, line -> line.first },
                            ) { index, (timeMs, text) ->
                                // 起点 = 该行开始前 0.1s；末尾 = 该行结束后 0.1s
                                // （行结束按下一行开始计，末行按歌曲时长计）
                                val lineEnd = lyricLines.getOrNull(index + 1)?.first
                                    ?: duration
                                val targetTime = if (boundary == 0) {
                                    (timeMs - 100).coerceAtLeast(0L)
                                } else {
                                    (lineEnd + 100).coerceAtMost(duration)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyBoundary(targetTime) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = formatClipTime(targetTime),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(64.dp),
                                    )
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
                TextButton(
                    enabled = confirmValid,
                    onClick = {
                        onConfirm(type, tag, startMs, endMs)
                        onDismiss()
                    },
                ) { Text(text = stringResource(R.string.confirm)) }
            }
        }
    }
}

/** 规则自动命名：由歌曲名关键字 / 类型集合 / 标签集合拼出可读名称。 */
private fun clipRuleName(
    query: String,
    types: List<String>,
    tags: List<String>,
    requireAll: Boolean,
): String {
    val name = buildString {
        if (query.isNotBlank()) {
            append("「")
            append(query.trim())
            append("」")
        }
        if (types.isNotEmpty()) append(types.joinToString("/"))
        if (tags.isNotEmpty()) {
            if (types.isNotEmpty()) append(if (requireAll) " 且 " else " 或 ")
            append(tags.joinToString("/"))
        }
    }
    return name.ifBlank { "全部片段" }
}

/** 搜索筛选条件：歌曲名关键字 + 类型集合 + 标签集合 + 与/或。 */
private data class ClipSearchFilter(
    val query: String = "",
    val types: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val requireAll: Boolean = true,
)

/** 片段搜索弹层：筛选条件作用于片段列表的展示（与规则播放相互独立）。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ClipSearchSheet(
    clips: List<MusicClip>,
    tracksById: Map<String, Track>,
    initial: ClipSearchFilter?,
    onDismiss: () -> Unit,
    onApply: (ClipSearchFilter) -> Unit,
) {
    var query by remember { mutableStateOf(initial?.query.orEmpty()) }
    var selectedTypes by remember { mutableStateOf(initial?.types?.toSet() ?: setOf()) }
    var selectedTags by remember { mutableStateOf(initial?.tags?.toSet() ?: setOf()) }
    var requireAll by remember { mutableStateOf(initial?.requireAll ?: true) }

    val tagCandidates = remember(clips) {
        (clips.map { it.tag } + CLIP_TAG_SUGGESTIONS)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val matchCount = remember(clips, query, selectedTypes, selectedTags, requireAll) {
        clips.count { clip ->
            clipMatchesFilter(
                clip = clip,
                trackTitle = tracksById[clip.trackId]?.title.orEmpty(),
                query = query,
                types = selectedTypes.toList(),
                tags = selectedTags.toList(),
                requireAll = requireAll,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.clips_search),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ClipFilterForm(
                query = query,
                selectedTypes = selectedTypes,
                selectedTags = selectedTags,
                requireAll = requireAll,
                tagCandidates = tagCandidates,
                matchCount = matchCount,
                onQueryChange = { query = it },
                onToggleType = { candidate ->
                    selectedTypes = if (candidate in selectedTypes) {
                        selectedTypes - candidate
                    } else {
                        selectedTypes + candidate
                    }
                },
                onToggleTag = { candidate ->
                    selectedTags = if (candidate in selectedTags) {
                        selectedTags - candidate
                    } else {
                        selectedTags + candidate
                    }
                },
                onRequireAllChange = { requireAll = it },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SheetPrimaryButton(
                    text = stringResource(R.string.clip_search_apply),
                    icon = Icons.Rounded.Check,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onApply(
                            ClipSearchFilter(
                                query = query.trim(),
                                types = selectedTypes.toList(),
                                tags = selectedTags.toList(),
                                requireAll = requireAll,
                            ),
                        )
                    },
                )
                SheetSecondaryButton(
                    text = stringResource(R.string.clip_search_reset),
                    icon = Icons.Rounded.Close,
                    enabled = query.isNotBlank() || selectedTypes.isNotEmpty() ||
                        selectedTags.isNotEmpty() || requireAll != true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        query = ""
                        selectedTypes = setOf()
                        selectedTags = setOf()
                        requireAll = true
                    },
                )
            }
        }
    }
}

/** 片段播放规则弹层：新建 / 管理播放规则，▶ 直接按规则筛选播放。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ClipRulesSheet(
    clips: List<MusicClip>,
    tracksById: Map<String, Track>,
    rules: List<ClipPlaybackRule>,
    onDismiss: () -> Unit,
    onPlayRule: (query: String, types: List<String>, tags: List<String>, requireAll: Boolean) -> Unit,
    onSaveRule: (ClipPlaybackRule) -> Unit,
    onRemoveRule: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedTypes by remember { mutableStateOf(setOf<String>()) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var requireAll by remember { mutableStateOf(true) }
    // 已保存规则的搜索关键字：按规则名过滤下方列表
    var ruleQuery by remember { mutableStateOf("") }

    val tagCandidates = remember(clips) {
        (clips.map { it.tag } + CLIP_TAG_SUGGESTIONS)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val filtersEmpty = query.isBlank() && selectedTypes.isEmpty() && selectedTags.isEmpty()
    val matchCount = remember(clips, query, selectedTypes, selectedTags, requireAll) {
        clips.count { clip ->
            clipMatchesFilter(
                clip = clip,
                trackTitle = tracksById[clip.trackId]?.title.orEmpty(),
                query = query,
                types = selectedTypes.toList(),
                tags = selectedTags.toList(),
                requireAll = requireAll,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.clips_rules),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ClipFilterForm(
                query = query,
                selectedTypes = selectedTypes,
                selectedTags = selectedTags,
                requireAll = requireAll,
                tagCandidates = tagCandidates,
                matchCount = matchCount,
                onQueryChange = { query = it },
                onToggleType = { candidate ->
                    selectedTypes = if (candidate in selectedTypes) {
                        selectedTypes - candidate
                    } else {
                        selectedTypes + candidate
                    }
                },
                onToggleTag = { candidate ->
                    selectedTags = if (candidate in selectedTags) {
                        selectedTags - candidate
                    } else {
                        selectedTags + candidate
                    }
                },
                onRequireAllChange = { requireAll = it },
            )
            SheetSecondaryButton(
                text = stringResource(R.string.clip_search_save_rule),
                icon = Icons.Rounded.PlaylistAdd,
                enabled = !filtersEmpty,
                onClick = {
                    onSaveRule(
                        ClipPlaybackRule(
                            id = java.util.UUID.randomUUID().toString(),
                            name = clipRuleName(
                                query,
                                selectedTypes.toList(),
                                selectedTags.toList(),
                                requireAll,
                            ),
                            query = query.trim(),
                            types = selectedTypes.toList(),
                            tags = selectedTags.toList(),
                            requireAll = requireAll,
                            createdAtMs = System.currentTimeMillis(),
                        ),
                    )
                },
            )
            if (rules.isNotEmpty()) {
                OutlinedTextField(
                    value = ruleQuery,
                    onValueChange = { ruleQuery = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.clip_search_rules_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.clip_search_saved_rules),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val visibleRules = remember(rules, ruleQuery) {
                    val keyword = ruleQuery.trim().lowercase()
                    if (keyword.isBlank()) {
                        rules
                    } else {
                        rules.filter { it.name.lowercase().contains(keyword) }
                    }
                }
                visibleRules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                // 点规则行套用到上方筛选条件，可微调后再保存
                                query = rule.query
                                selectedTypes = rule.types.toSet()
                                selectedTags = rule.tags.toSet()
                                requireAll = rule.requireAll
                            }
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            onPlayRule(rule.query, rule.types, rule.tags, rule.requireAll)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.clip_search_play),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { onRemoveRule(rule.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 搜索 / 规则共用的筛选表单：歌曲名 + 类型多选 + 标签多选 + 与/或 + 命中数。 */
@Composable
private fun ClipFilterForm(
    query: String,
    selectedTypes: Set<String>,
    selectedTags: Set<String>,
    requireAll: Boolean,
    tagCandidates: List<String>,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onToggleType: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onRequireAllChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text(text = stringResource(R.string.clip_search_song_hint)) },
            modifier = Modifier.fillMaxWidth(),
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
                SearchChip(
                    text = candidate,
                    selected = candidate in selectedTypes,
                    onClick = { onToggleType(candidate) },
                )
            }
        }
        Text(
            text = stringResource(R.string.clip_tag),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tagCandidates.forEach { candidate ->
                SearchChip(
                    text = candidate,
                    selected = candidate in selectedTags,
                    onClick = { onToggleTag(candidate) },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.clip_search_logic_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SearchChip(
                text = stringResource(R.string.clip_search_logic_and),
                selected = requireAll,
                onClick = { onRequireAllChange(true) },
            )
            SearchChip(
                text = stringResource(R.string.clip_search_logic_or),
                selected = !requireAll,
                onClick = { onRequireAllChange(false) },
            )
        }
        Text(
            text = stringResource(R.string.clip_search_match_count, matchCount),
            style = MaterialTheme.typography.bodySmall,
            color = if (matchCount > 0) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 弹层里通栏的主操作 / 次操作按钮。 */
@Composable
private fun SheetPrimaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SheetSecondaryButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 搜索弹层里的多选 / 单选小 chip。 */
@Composable
private fun SearchChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
