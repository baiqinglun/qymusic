package com.qymusic.player.ui

import android.graphics.Bitmap
import android.icu.text.Transliterator
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.CollectionViewMode
import com.qymusic.player.data.Track
import com.qymusic.player.data.UserPlaylist
import java.text.Collator
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class LibraryGroupKind {
    ARTIST,
    ALBUM,
    PLAYLIST,
}

/** 从播放页跳转到某个艺术家 / 专辑时用到的请求。 */
data class LibraryGroupRequest(
    val kind: LibraryGroupKind,
    val title: String,
)

@Composable
fun LibraryScreen(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    songsListState: LazyListState,
    tracks: List<Track>,
    folderCount: Int,
    playlists: List<UserPlaylist>,
    pinnedArtists: Set<String>,
    pinnedAlbums: Set<String>,
    pinnedPlaylistIds: Set<Long>,
    scanState: ScanUiState,
    playerState: PlayerUiState,
    artworkCache: Map<String, Bitmap?>,
    onRescan: () -> Unit,
    onOpenKaraoke: () -> Unit,
    onOpenDrawer: () -> Unit,
    showTopTabs: Boolean = true,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onTogglePlayPause: () -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onSetTrackInPlaylist: (Long, String, Boolean) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onToggleArtistPinned: (String, Boolean) -> Unit,
    onToggleAlbumPinned: (String, Boolean) -> Unit,
    onTogglePlaylistPinned: (Long, Boolean) -> Unit,
    artistViewMode: CollectionViewMode,
    albumViewMode: CollectionViewMode,
    playlistViewMode: CollectionViewMode,
    onArtistViewModeChange: (CollectionViewMode) -> Unit,
    onAlbumViewModeChange: (CollectionViewMode) -> Unit,
    onPlaylistViewModeChange: (CollectionViewMode) -> Unit,
    playlistCovers: Map<Long, Bitmap?>,
    onRequestPlaylistCover: (UserPlaylist) -> Unit,
    onPickPlaylistCover: (UserPlaylist) -> Unit,
    onResetPlaylistCover: (UserPlaylist) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    requestedGroup: LibraryGroupRequest?,
    onRequestedGroupHandled: () -> Unit,
    onReturnToPlayer: () -> Unit,
    onPlayAllTracks: (List<Track>, Boolean) -> Unit,
    /** 播放页是否正盖在主界面上面（浮层展开中 / 已展开）。 */
    playerCovering: Boolean = false,
) {
    if (scanState is ScanUiState.Scanning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            LibraryLoading(
                title = stringResource(R.string.scanning),
                hint = stringResource(R.string.scanning_hint),
            )
        }
        return
    }

    var playlistSeedTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var propertiesTrack by remember { mutableStateOf<Track?>(null) }
    // 歌曲列表只在下发数据后的首次渲染播放入场动画；滚动时 LazyColumn
    // 回收并重新组合条目，不再重复触发推出效果。
    var animateTrackEntrance by rememberSaveable { mutableStateOf(true) }

    // 歌曲列表数据未就绪时显示骨架屏；数据到位后骨架淡出，条目分块浮现。
    val songsLoading = scanState is ScanUiState.Scanning ||
        scanState is ScanUiState.Loading ||
        scanState is ScanUiState.Refreshing
    val skeletonOverlay = rememberSkeletonOverlay(songsLoading)

    // 集合页（艺术家 / 专辑 / 歌单）的分块浮现各播一次：该页数据首次组合时播放，
    // 首帧组合完成后立即翻标记，之后滚动进来 / 切回来的条目不再重复。
    var artistRevealDone by rememberSaveable { mutableStateOf(false) }
    var albumRevealDone by rememberSaveable { mutableStateOf(false) }
    var playlistRevealDone by rememberSaveable { mutableStateOf(false) }

    // 主页四个 tab 的分页状态。提到最外层，让 tab 底片、统计栏和右下角按钮
    // 都读同一个来源，滑动 / 点按不会再出现「指示器停在这页、数量显示那页」。
    val tabPagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, LIBRARY_TAB_COUNT - 1),
        pageCount = { LIBRARY_TAB_COUNT },
    )
    val activeTab = tabPagerState.currentPage.coerceIn(0, LIBRARY_TAB_COUNT - 1)
    val tabScope = rememberCoroutineScope()
    LaunchedEffect(selectedTab) {
        val page = selectedTab.coerceIn(0, LIBRARY_TAB_COUNT - 1)
        if (tabPagerState.currentPage != page) {
            tabPagerState.scrollToPage(page)
        }
    }


    val currentTrack = playerState.currentTrack
    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    // 从播放页点艺术家 / 专辑进来时，在这一帧就把详情页算出来：
    // 播放页往下滑走的时候，下面已经是详情页，中间不会再闪过列表。
    val requestedSelection = remember(
        requestedGroup,
        tracks,
        playlists,
        unknownArtist,
        unknownAlbum,
    ) {
        requestedGroup?.let { request ->
            LibraryGroupSelection(
                title = request.title,
                kind = request.kind,
                trackIds = when (request.kind) {
                    LibraryGroupKind.ARTIST ->
                        trackIdsForArtist(tracks, request.title, unknownArtist)

                    LibraryGroupKind.ALBUM -> tracks
                        .filter { it.album.ifBlank { unknownAlbum } == request.title }
                        .map(Track::id)

                    LibraryGroupKind.PLAYLIST -> playlists
                        .firstOrNull { it.name == request.title }
                        ?.trackIds
                        .orEmpty()
                },
                openedFromPlayer = true,
            )
        }
    }
    var selectedGroup by remember { mutableStateOf(requestedSelection) }
    var groupHistory by remember { mutableStateOf<List<LibraryGroupSelection>>(emptyList()) }
    // 请求还没落到 state 之前先用这份渲染，页面上不会多等一帧。
    val activeGroup = selectedGroup ?: requestedSelection
    // 曲目索引：详情页 / 歌单封面都靠它做 O(1) 查找，避免每次重组都全表扫描。
    val tracksById = remember(tracks) { tracks.associateBy(Track::id) }
    // 艺术家 / 专辑分组懒加载：只有对应 tab 真正打开过才计算，算完缓存住（重扫后失效）。
    var artistGroupsEnabled by remember { mutableStateOf(false) }
    var albumGroupsEnabled by remember { mutableStateOf(false) }
    // 每个 tab 独立保存排序方式，切换 tab 不会互相覆盖。
    var artistSortModeName by rememberSaveable {
        mutableStateOf(GroupSortMode.ALPHABETICAL.name)
    }
    var albumSortModeName by rememberSaveable {
        mutableStateOf(GroupSortMode.ALPHABETICAL.name)
    }
    var playlistSortModeName by rememberSaveable {
        mutableStateOf(GroupSortMode.ALPHABETICAL.name)
    }

    // 集合 Tab 的滚动状态提升到这一层：进入详情页时列表会被 AnimatedContent
    // 移出组合，状态放在内部组件里，返回时滚动位置会丢、共享封面飞不回原格位。
    val artistListState = rememberLazyListState()
    val artistGridState = rememberLazyGridState()
    val albumListState = rememberLazyListState()
    val albumGridState = rememberLazyGridState()
    val playlistListState = rememberLazyListState()
    val playlistGridState = rememberLazyGridState()
    val artistSortMode = runCatching { GroupSortMode.valueOf(artistSortModeName) }
        .getOrDefault(GroupSortMode.ALPHABETICAL)
    val albumSortMode = runCatching { GroupSortMode.valueOf(albumSortModeName) }
        .getOrDefault(GroupSortMode.ALPHABETICAL)
    val playlistSortMode = runCatching { GroupSortMode.valueOf(playlistSortModeName) }
        .getOrDefault(GroupSortMode.ALPHABETICAL)
    // 歌曲列表的排序方式（标题 / 艺术家 / 专辑 + 正倒序）。
    var trackSortFieldName by rememberSaveable { mutableStateOf(TrackSortField.TITLE.name) }
    var trackSortAscending by rememberSaveable { mutableStateOf(true) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // 打开搜索时所在的 tab：结果区把这一类放在最前面。
    var searchOriginTab by rememberSaveable { mutableStateOf(0) }
    val trackSortField = runCatching { TrackSortField.valueOf(trackSortFieldName) }
        .getOrDefault(TrackSortField.TITLE)
    val artistGroups = remember(tracks, unknownArtist, artistGroupsEnabled) {
        if (artistGroupsEnabled) buildArtistGroups(tracks, unknownArtist) else emptyList()
    }
    val albumGroups = remember(tracks, unknownAlbum, albumGroupsEnabled) {
        if (albumGroupsEnabled) buildAlbumGroups(tracks, unknownAlbum) else emptyList()
    }
    // 歌曲列表按当前排序方式生成；按标题时保持和右侧 A-Z 索引一致的分段顺序。
    val songsTracks = remember(tracks, trackSortField, trackSortAscending) {
        sortTracksFor(tracks, trackSortField, trackSortAscending)
    }
    LaunchedEffect(
        activeTab,
        activeGroup,
        searchOpen,
        scanState,
        songsTracks.isNotEmpty(),
        animateTrackEntrance,
    ) {
        if (
            animateTrackEntrance &&
            activeTab == 0 &&
            activeGroup == null &&
            !searchOpen &&
            songsTracks.isNotEmpty() &&
            scanState !is ScanUiState.Loading &&
            scanState !is ScanUiState.Scanning &&
            scanState !is ScanUiState.Refreshing
        ) {
            animateTrackEntrance = false
        }
    }
    val artistCount = remember(tracks, unknownArtist) {
        tracks.map { it.artist.ifBlank { unknownArtist } }.distinct().size
    }
    val albumCount = remember(tracks, unknownAlbum) {
        tracks.map { it.album.ifBlank { unknownAlbum } }.distinct().size
    }
    // 只在选中的详情页变化时重建曲目列表（原来是每次重组都做一次 O(n²) 查找）。
    val selectedTracks = remember(activeGroup, tracksById) {
        activeGroup?.trackIds?.mapNotNull(tracksById::get).orEmpty()
    }
    val sortedArtistGroups = remember(artistGroups, artistSortMode, pinnedArtists) {
        sortGroups(artistGroups, artistSortMode, pinnedArtists)
    }
    val sortedAlbumGroups = remember(albumGroups, albumSortMode, pinnedAlbums) {
        sortGroups(albumGroups, albumSortMode, pinnedAlbums)
    }
    // 歌单 tab 的排序：字母 / 数量（按歌单内仍存在的曲目数）。
    val sortedPlaylists = remember(playlists, playlistSortMode, tracksById, pinnedPlaylistIds) {
        sortPlaylists(playlists, playlistSortMode, tracksById, pinnedPlaylistIds)
    }
    // 搜索只在打开时建立完整索引，普通浏览仍然保持按 tab 懒加载。
    val searchArtistGroups = remember(
        tracks,
        unknownArtist,
        searchOpen,
        artistSortMode,
        pinnedArtists,
    ) {
        if (searchOpen) {
            sortGroups(buildArtistGroups(tracks, unknownArtist), artistSortMode, pinnedArtists)
        } else {
            emptyList()
        }
    }
    val searchAlbumGroups = remember(
        tracks,
        unknownAlbum,
        searchOpen,
        albumSortMode,
        pinnedAlbums,
    ) {
        if (searchOpen) {
            sortGroups(buildAlbumGroups(tracks, unknownAlbum), albumSortMode, pinnedAlbums)
        } else {
            emptyList()
        }
    }
    val searchResults = remember(
        searchQuery,
        songsTracks,
        searchArtistGroups,
        searchAlbumGroups,
        sortedPlaylists,
    ) {
        filterLibrarySearchResults(
            query = searchQuery,
            songs = songsTracks,
            artists = searchArtistGroups,
            albums = searchAlbumGroups,
            playlists = sortedPlaylists,
        )
    }
    // 改排序时先亮一小段 loading：列表在 loading 底下把新顺序组合、排版好，
    // 再整体显示出来，重排那一帧的停顿就被挡在 loading 后面了。
    var sortingLoading by remember { mutableStateOf(false) }
    val sortSignature = listOf(
        trackSortField.name,
        trackSortAscending.toString(),
        artistSortMode.name,
        albumSortMode.name,
        playlistSortMode.name,
    )
    var lastSortSignature by remember { mutableStateOf(sortSignature) }
    LaunchedEffect(sortSignature) {
        if (lastSortSignature != sortSignature) {
            lastSortSignature = sortSignature
            sortingLoading = true
            delay(SORT_LOADING_MS)
            sortingLoading = false
        }
    }

    // 排序变化后歌曲列表回到顶部，否则 key 锚定会让顺序看起来没变。
    // 首次组合不滚动，从播放页 / 设置页回来时会保留原来的滚动位置。
    var lastTrackSort by remember {
        mutableStateOf(trackSortField.name to trackSortAscending)
    }
    LaunchedEffect(trackSortField, trackSortAscending) {
        val current = trackSortField.name to trackSortAscending
        if (lastTrackSort != current) {
            lastTrackSort = current
            // 已经在顶部时不用再滚动，scrollToItem 会强制整列重新测量。
            if (songsListState.firstVisibleItemIndex != 0 ||
                songsListState.firstVisibleItemScrollOffset != 0
            ) {
                songsListState.scrollToItem(0)
            }
        }
    }
    // 当前 tab 的完整曲目列表（「播放 / 随机播放」用），按该 tab 的顺序展开。
    val currentTabTracks: (Int) -> List<Track> = { tab ->
        when (tab) {
            0 -> songsTracks
            1 -> sortedArtistGroups
                .flatMap { group -> group.trackIds.mapNotNull(tracksById::get) }

            2 -> sortedAlbumGroups
                .flatMap { group -> group.trackIds.mapNotNull(tracksById::get) }

            else -> sortedPlaylists
                .flatMap { playlist -> playlist.trackIds.mapNotNull(tracksById::get) }
                .distinctBy { it.id }
        }
    }
    // tab 下面一行：总数 + 播放 / 随机播放 / 排序。加载中和加载完成后渲染同一份，
    // 这样骨架屏切到真实列表时页面高度不变，不会整体跳一下。
    val libraryTabToolbar: @Composable () -> Unit = {
        LibraryTabToolbar(
            tab = activeTab,
            countText = when (activeTab) {
                0 -> stringResource(R.string.library_count_songs, tracks.size)
                1 -> stringResource(R.string.library_count_artists, artistCount)
                2 -> stringResource(R.string.library_count_albums, albumCount)
                else -> stringResource(
                    R.string.library_count_playlists,
                    playlists.size,
                )
            },
            viewMode = when (activeTab) {
                1 -> artistViewMode
                2 -> albumViewMode
                3 -> playlistViewMode
                else -> null
            },
            onToggleViewMode = when (activeTab) {
                1 -> {
                    { onArtistViewModeChange(artistViewMode.toggled()) }
                }

                2 -> {
                    { onAlbumViewModeChange(albumViewMode.toggled()) }
                }

                3 -> {
                    { onPlaylistViewModeChange(playlistViewMode.toggled()) }
                }

                else -> null
            },
            groupSortMode = when (activeTab) {
                1 -> artistSortMode
                2 -> albumSortMode
                else -> playlistSortMode
            },
            onGroupSortModeChange = { mode ->
                when (activeTab) {
                    1 -> artistSortModeName = mode.name
                    2 -> albumSortModeName = mode.name
                    else -> playlistSortModeName = mode.name
                }
            },
            trackSortField = trackSortField,
            trackSortAscending = trackSortAscending,
            onTrackSortFieldChange = { trackSortFieldName = it.name },
            onTrackSortOrderChange = { trackSortAscending = it },
            onPlayAll = {
                val tracksToPlay = currentTabTracks(activeTab)
                if (tracksToPlay.isNotEmpty()) {
                    onPlayAllTracks(tracksToPlay, false)
                }
            },
            onShuffleAll = {
                val tracksToPlay = currentTabTracks(activeTab)
                if (tracksToPlay.isNotEmpty()) {
                    onPlayAllTracks(tracksToPlay, true)
                }
            },
        )
    }
    // 集合页骨架屏：艺术家 / 专辑 / 歌单三个 tab 各按当前视图模式取形状，
    // 行高、网格边距、字母导航留白都与真实列表对齐，骨架切换到真内容不跳动。
    val libraryCollectionSkeleton: @Composable (Modifier) -> Unit = { skeletonModifier ->
        when (activeTab) {
            1 -> CollectionSkeleton(
                viewMode = artistViewMode,
                modifier = skeletonModifier,
                circleCovers = artistViewMode == CollectionViewMode.LIST,
                endPadding = if (artistSortMode.showsAlphabetRail) {
                    ALPHABET_RAIL_WIDTH
                } else {
                    0.dp
                },
            )

            2 -> CollectionSkeleton(
                viewMode = albumViewMode,
                modifier = skeletonModifier,
                endPadding = if (albumSortMode.showsAlphabetRail) {
                    ALPHABET_RAIL_WIDTH
                } else {
                    0.dp
                },
            )

            else -> CollectionSkeleton(
                viewMode = playlistViewMode,
                modifier = skeletonModifier,
            )
        }
    }
    // 打开新的详情页时把当前页压栈，返回时回到进入前的页面。
    val openGroup: (LibraryGroupSelection) -> Unit = { group ->
        selectedGroup?.let { current -> groupHistory = groupHistory + current }
        selectedGroup = group
    }
    val closeGroup: () -> Unit = {
        if (groupHistory.isNotEmpty()) {
            selectedGroup = groupHistory.last()
            groupHistory = groupHistory.dropLast(1)
        } else {
            val closed = selectedGroup
            if (closed?.openedFromPlayer == true) {
                // 从播放页跳进来的详情页：直接把播放页推上来，详情页留在下面被整个盖住，
                // 否则会先闪一帧歌曲列表再被播放页盖掉。
                onReturnToPlayer()
            } else {
                selectedGroup = null
            }
        }
    }
    val openArtist: (String) -> Unit = { artistName ->
        openGroup(
            LibraryGroupSelection(
                title = artistName,
                kind = LibraryGroupKind.ARTIST,
                trackIds = trackIdsForArtist(tracks, artistName, unknownArtist),
                openedFromPlayer = selectedGroup?.openedFromPlayer == true,
            ),
        )
    }
    val openAlbum: (String) -> Unit = { albumName ->
        openGroup(
            LibraryGroupSelection(
                title = albumName,
                kind = LibraryGroupKind.ALBUM,
                trackIds = tracks
                    .filter { it.album.ifBlank { unknownAlbum } == albumName }
                    .map(Track::id),
            ),
        )
    }
    val showAddToPlaylist: (Track) -> Unit = { track ->
        playlistSeedTrack = track
        showAddToPlaylistDialog = true
    }
    val showProperties: (Track) -> Unit = { track -> propertiesTrack = track }
    val selectedPlaylist = activeGroup?.playlistId?.let { playlistId ->
        playlists.firstOrNull { it.id == playlistId }
    }

    LaunchedEffect(selectedPlaylist?.id, selectedPlaylist?.coverUri) {
        if (selectedPlaylist?.coverUri != null) {
            onRequestPlaylistCover(selectedPlaylist)
        }
    }

    // 渲染已经用 requestedSelection 顶上了，这里只把它落到状态里，
    // 值相等所以不会再触发一次切页动画。
    LaunchedEffect(requestedSelection) {
        val selection = requestedSelection ?: return@LaunchedEffect
        if (selectedGroup != selection) {
            // 从播放页进来的全新跳转，清掉旧的详情页栈。
            groupHistory = emptyList()
            selectedGroup = selection
        }
        onRequestedGroupHandled()
    }

    // 从播放页进来的详情页：等播放页完全盖住之后再收起来，
    // 这样下次从播放页返回时看到的是主界面，和进播放页之前一致。
    LaunchedEffect(playerCovering) {
        if (!playerCovering) return@LaunchedEffect
        delay(PLAYER_COVER_SETTLE_MS)
        if (selectedGroup?.openedFromPlayer == true) {
            groupHistory = emptyList()
            selectedGroup = null
        }
    }

    // 播放页盖在主界面上时，这里不抢返回键：返回应该退出播放页。
    BackHandler(enabled = activeGroup != null && !playerCovering) {
        closeGroup()
    }
    BackHandler(enabled = searchOpen && activeGroup == null) {
        searchOpen = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 底部播放条是全局的，挂在 QYMusicApp 里，所有页面共享；主页不再自带。
        floatingActionButton = {
            // 歌单 tab 的新建入口固定在右下角，列表里不再占一行。
            if (activeGroup == null && activeTab == LIBRARY_TAB_COUNT - 1 && !searchOpen) {
                FloatingActionButton(
                    onClick = {
                        playlistSeedTrack = null
                        playlistName = ""
                        showCreatePlaylistDialog = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = stringResource(R.string.create_playlist),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                // 封面在列表与详情之间共享：进入时飞出、返回时飞回，实现迁移过渡。
                val sharedTransitionScope = this
                AnimatedContent(
                    targetState = activeGroup,
                    transitionSpec = {
                        if (targetState == null) {
                            if (initialState?.openedFromPlayer == true) {
                                // 从播放页进来的详情页：返回时播放页会整屏盖上来，底下不用动画。
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                // 返回列表：详情页整屏右移退出、列表整屏从左侧推回，
                                // 封面沿同一个共享元素飞回原来的格位。
                                slideInHorizontally(tween(DETAIL_SLIDE_MS)) { -it } togetherWith
                                    slideOutHorizontally(tween(DETAIL_SLIDE_MS)) { it }
                            }
                        } else if (targetState?.openedFromPlayer == true && initialState == null) {
                            // 从播放页点进来：这一帧直接换页。动画本来也发生在播放页底下，
                            // 保留动画只会让下面先露出列表再切过去，看着像闪了一下。
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            // 进入详情页：新页整屏从右侧推入、旧页整屏退出，两页严丝合缝地
                            // 分屏衔接，不会叠加；不用淡入淡出，避免中间帧透出底下内容。
                            slideInHorizontally(tween(DETAIL_SLIDE_MS)) { it } togetherWith
                                slideOutHorizontally(tween(DETAIL_SLIDE_MS)) { -it }
                        }
                    },
                    label = "library-page",
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                ) { group ->
                    // 共享元素需要 AnimatedContent 的可见性作用域。
                    val animatedContentScope = this
                    if (group != null) {
                        // 返回转场期间 activeGroup 已经变回 null，外层的 selectedTracks /
                        // selectedPlaylist 会跟着清空；详情内容必须用自己的 group 快照渲染，
                        // 否则退出动画里详情页会闪成空页、封面也无法飞回列表格位。
                        val selectedTracks = remember(group, tracksById) {
                            group.trackIds.mapNotNull(tracksById::get)
                        }
                        val selectedPlaylist = group.playlistId?.let { playlistId ->
                            playlists.firstOrNull { it.id == playlistId }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            val albumArtists = if (group.kind == LibraryGroupKind.ALBUM) {
                                selectedTracks
                                    .map { it.artist.ifBlank { unknownArtist } }
                                    .distinct()
                            } else {
                                emptyList()
                            }
                            val singleAlbumArtist = albumArtists.singleOrNull()
                            val totalDurationMs = remember(selectedTracks) {
                                selectedTracks.sumOf { it.durationMs }
                            }
                            val trackCountDuration = stringResource(
                                R.string.track_count_duration,
                                selectedTracks.size,
                                formatDuration(totalDurationMs),
                            )
                            var playlistMenuExpanded by remember(group.playlistId) {
                                mutableStateOf(false)
                            }
                            val groupPinned = when (group.kind) {
                                LibraryGroupKind.ARTIST -> group.title in pinnedArtists
                                LibraryGroupKind.ALBUM -> group.title in pinnedAlbums
                                LibraryGroupKind.PLAYLIST ->
                                    group.playlistId?.let { it in pinnedPlaylistIds } == true
                            }
                            val toggleGroupPinned: (() -> Unit)? = when (group.kind) {
                                LibraryGroupKind.ARTIST -> {
                                    { onToggleArtistPinned(group.title, !groupPinned) }
                                }
    
                                LibraryGroupKind.ALBUM -> {
                                    { onToggleAlbumPinned(group.title, !groupPinned) }
                                }
    
                                LibraryGroupKind.PLAYLIST -> {
                                    group.playlistId?.let { playlistId ->
                                        {
                                            onTogglePlaylistPinned(
                                                playlistId,
                                                playlistId !in pinnedPlaylistIds,
                                            )
                                        }
                                    }
                                }
                            }
                            GroupDetailHeader(
                                title = group.title,
                                subtitle = when {
                                    group.kind == LibraryGroupKind.ALBUM && singleAlbumArtist != null ->
                                        stringResource(
                                            R.string.collection_meta_join,
                                            singleAlbumArtist,
                                            trackCountDuration,
                                        )
    
                                    group.kind == LibraryGroupKind.ALBUM ->
                                        stringResource(
                                            R.string.collection_meta_join,
                                            stringResource(R.string.various_artists),
                                            trackCountDuration,
                                        )
    
                                    else -> trackCountDuration
                                },
                                onSubtitleClick = if (singleAlbumArtist != null) {
                                    { openArtist(singleAlbumArtist) }
                                } else {
                                    null
                                },
                                // 专辑、艺术家、歌单都默认用第一首歌的封面。
                                coverTrack = selectedTracks.firstOrNull(),
                                coverBitmap = selectedPlaylist?.let { playlistCovers[it.id] },
                                artworkCache = artworkCache,
                                onRequestArtwork = onRequestArtwork,
                                // 封面从列表格位平移到详情页头部：歌单 / 专辑 / 艺术家共用。
                                coverModifier = group.coverTransitionKey()?.let { coverKey ->
                                    with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            rememberSharedContentState(coverKey),
                                            animatedContentScope,
                                        )
                                    }
                                } ?: Modifier,
                                onBack = closeGroup,
                                // 播放按钮：用这个详情页里的歌曲整体替换播放列表。
                                onPlayAll = {
                                    selectedTracks.firstOrNull()?.let { track ->
                                        selectedGroup = selectedGroup?.copy(openedFromPlayer = false)
                                        onPlayTrack(track, selectedTracks)
                                    }
                                },
                                onShuffleAll = {
                                    if (selectedTracks.isNotEmpty()) {
                                        selectedGroup = selectedGroup?.copy(openedFromPlayer = false)
                                        onPlayAllTracks(selectedTracks, true)
                                    }
                                },
                                isPinned = groupPinned,
                                onTogglePinned = toggleGroupPinned,
                                actions = if (selectedPlaylist != null) {
                                    {
                                        Box {
                                            IconButton(onClick = { playlistMenuExpanded = true }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.MoreVert,
                                                    contentDescription = stringResource(
                                                        R.string.playlist_options,
                                                    ),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = playlistMenuExpanded,
                                                onDismissRequest = { playlistMenuExpanded = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = stringResource(
                                                                R.string.playlist_cover_set,
                                                            ),
                                                        )
                                                    },
                                                    onClick = {
                                                        playlistMenuExpanded = false
                                                        onPickPlaylistCover(selectedPlaylist)
                                                    },
                                                )
                                                if (selectedPlaylist.coverUri != null) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                text = stringResource(
                                                                    R.string.playlist_cover_reset,
                                                                ),
                                                            )
                                                        },
                                                        onClick = {
                                                            playlistMenuExpanded = false
                                                            onResetPlaylistCover(selectedPlaylist)
                                                        },
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = stringResource(
                                                                R.string.rename_playlist,
                                                            ),
                                                        )
                                                    },
                                                    onClick = {
                                                        playlistMenuExpanded = false
                                                        renamePlaylistName = selectedPlaylist.name
                                                        playlistToRename = selectedPlaylist
                                                    },
                                                )
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = stringResource(
                                                                R.string.delete_playlist,
                                                            ),
                                                        )
                                                    },
                                                    onClick = {
                                                        playlistMenuExpanded = false
                                                        onDeletePlaylist(selectedPlaylist.id)
                                                        closeGroup()
                                                    },
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                            GroupTracksContent(
                                tracks = selectedTracks,
                                currentTrackId = currentTrack?.id,
                                isPlaying = playerState.showPauseIcon,
                                artworkCache = artworkCache,
                                onRequestArtwork = onRequestArtwork,
                                onPlayTrack = { track ->
                                    if (track.id == currentTrack?.id) {
                                        onTogglePlayPause()
                                    } else {
                                        // 在详情页里主动播放后，返回行为恢复成普通的返回列表。
                                        selectedGroup = selectedGroup?.copy(openedFromPlayer = false)
                                        onPlayTrack(track, selectedTracks)
                                    }
                                },
                                onPlayNext = onPlayNext,
                                onAddToPlaylist = showAddToPlaylist,
                                onShowProperties = showProperties,
                                onEditTags = onEditTags,
                                onDeleteTrack = onDeleteTrack,
                                onOpenAlbum = { track ->
                                    openAlbum(track.album.ifBlank { unknownAlbum })
                                },
                                onOpenArtist = { track ->
                                    openArtist(track.artist.ifBlank { unknownArtist })
                                },
                            )
                        }
                    } else if (scanState is ScanUiState.Scanning) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LibraryHeader(
                            showFullHeader = showTopTabs,
                                trackCount = tracks.size,
                                folderCount = folderCount,
                                onRescan = onRescan,
                                onOpenKaraoke = onOpenKaraoke,
                                onOpenDrawer = onOpenDrawer,
                                onOpenSearch = {
                                    searchQuery = ""
                                    searchOriginTab = activeTab
                                    searchOpen = true
                                },
                                onOpenSettings = onOpenSettings,
                                onOpenStatistics = onOpenStatistics,
                            )
                            if (group == null) {
                                LibraryTabs(
                                    visible = showTopTabs,
                                    pagerPosition = tabPagerState.currentPage +
                                        tabPagerState.currentPageOffsetFraction,
                                    onSelect = { index ->
                                        tabScope.launch {
                                            tabPagerState.animateScrollToPage(
                                                page = index.coerceIn(0, LIBRARY_TAB_COUNT - 1),
                                                animationSpec = tween(TAB_SWITCH_MS),
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            libraryTabToolbar()
                            if (activeTab == 0) {
                                SongsSkeletonList(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            } else {
                                libraryCollectionSkeleton(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                    } else if (scanState is ScanUiState.Loading) {
                        // 普通启动读缓存时不显示 loading，只留空背景；列表数据到位后渐变推出。
                        Column(modifier = Modifier.fillMaxSize()) {
                            LibraryHeader(
                            showFullHeader = showTopTabs,
                                trackCount = tracks.size,
                                folderCount = folderCount,
                                onRescan = onRescan,
                                onOpenKaraoke = onOpenKaraoke,
                                onOpenDrawer = onOpenDrawer,
                                onOpenSearch = {
                                    searchQuery = ""
                                    searchOriginTab = activeTab
                                    searchOpen = true
                                },
                                onOpenSettings = onOpenSettings,
                                onOpenStatistics = onOpenStatistics,
                            )
                            if (group == null) {
                                LibraryTabs(
                                    visible = showTopTabs,
                                    pagerPosition = tabPagerState.currentPage +
                                        tabPagerState.currentPageOffsetFraction,
                                    onSelect = { index ->
                                        tabScope.launch {
                                            tabPagerState.animateScrollToPage(
                                                page = index.coerceIn(0, LIBRARY_TAB_COUNT - 1),
                                                animationSpec = tween(TAB_SWITCH_MS),
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            libraryTabToolbar()
                            if (activeTab == 0) {
                                SongsSkeletonList(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            } else {
                                libraryCollectionSkeleton(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                    } else if (scanState is ScanUiState.Refreshing) {
                        // 启动页后扫描：歌曲列表区域保留 loading，扫描完成后逐条推出。
                        Column(modifier = Modifier.fillMaxSize()) {
                            LibraryHeader(
                            showFullHeader = showTopTabs,
                                trackCount = tracks.size,
                                folderCount = folderCount,
                                onRescan = onRescan,
                                onOpenKaraoke = onOpenKaraoke,
                                onOpenDrawer = onOpenDrawer,
                                onOpenSearch = {
                                    searchQuery = ""
                                    searchOriginTab = activeTab
                                    searchOpen = true
                                },
                                onOpenSettings = onOpenSettings,
                                onOpenStatistics = onOpenStatistics,
                            )
                            if (group == null) {
                                LibraryTabs(
                                    visible = showTopTabs,
                                    pagerPosition = tabPagerState.currentPage +
                                        tabPagerState.currentPageOffsetFraction,
                                    onSelect = { index ->
                                        tabScope.launch {
                                            tabPagerState.animateScrollToPage(
                                                page = index.coerceIn(0, LIBRARY_TAB_COUNT - 1),
                                                animationSpec = tween(TAB_SWITCH_MS),
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            libraryTabToolbar()
                            if (activeTab == 0) {
                                SongsSkeletonList(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            } else {
                                libraryCollectionSkeleton(
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                )
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            LibraryHeader(
                            showFullHeader = showTopTabs,
                                trackCount = tracks.size,
                                folderCount = folderCount,
                                onRescan = onRescan,
                                onOpenKaraoke = onOpenKaraoke,
                                onOpenDrawer = onOpenDrawer,
                                onOpenSearch = {
                                    searchQuery = ""
                                    searchOriginTab = activeTab
                                    searchOpen = true
                                },
                                onOpenSettings = onOpenSettings,
                                onOpenStatistics = onOpenStatistics,
                            )
    
                        when (scanState) {
                            is ScanUiState.Failed -> {
                                Text(
                                    text = stringResource(R.string.scan_failed, scanState.message),
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
    
                            else -> Unit
                        }
    
                        // tab 支持左右滑动切换，指示器直接跟着分页进度连续移动。
                        if (group == null) {
                            LibraryTabs(
                                visible = showTopTabs,
                                pagerPosition = tabPagerState.currentPage +
                                    tabPagerState.currentPageOffsetFraction,
                                onSelect = { index ->
                                    tabScope.launch {
                                        // 点 tab 用短补间：默认 spring 会拖到 400ms 以上，
                                        // 低端设备上整页重绘跟不上就显得卡顿。
                                        tabPagerState.animateScrollToPage(
                                            page = index.coerceIn(0, LIBRARY_TAB_COUNT - 1),
                                            animationSpec = tween(TAB_SWITCH_MS),
                                        )
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                            // tab 下面一行：总数 + 播放 / 随机播放 / 排序
                            libraryTabToolbar()
                        }
    
                        // 分页器是唯一驱动：页面变化只向上汇报，不再反向触发滚动动画，
                        // 否则动画会被自己触发的重组取消，停在中间的页面。
                        LaunchedEffect(tabPagerState) {
                            snapshotFlow { tabPagerState.currentPage }.collect { page ->
                                if (page != selectedTab) {
                                    onSelectTab(page)
                                }
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = tabPagerState,
                            // 只多留相邻一页：四个 tab 全留在组合里的话，
                            // 每一帧都要为看不见的两页发绘制指令，播放页 / 设置页滑出时会卡。
                            beyondViewportPageCount = 1,
                            userScrollEnabled = showTopTabs,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                        when {
                            page == 0 -> SongsContent(
                                tracks = songsTracks,
                                animateEntrance = animateTrackEntrance,
                                listState = songsListState,
                                currentTrackId = currentTrack?.id,
                                isPlaying = playerState.showPauseIcon,
                                artworkCache = artworkCache,
                                onRequestArtwork = onRequestArtwork,
                                onPlayTrack = { track ->
                                    if (track.id == currentTrack?.id) {
                                        onTogglePlayPause()
                                    } else {
                                        onPlayTrack(track, songsTracks)
                                    }
                                },
                                onPlayNext = onPlayNext,
                                onAddToPlaylist = showAddToPlaylist,
                                onShowProperties = showProperties,
                                onEditTags = onEditTags,
                                onDeleteTrack = onDeleteTrack,
                                onOpenAlbum = { track ->
                                    openAlbum(track.album.ifBlank { unknownAlbum })
                                },
                                onOpenArtist = { track ->
                                    openArtist(track.artist.ifBlank { unknownArtist })
                                },
                                // 只有按标题排序时才和 A-Z 索引对应，其它排序隐藏字母导航。
                                showAlphabetRail = trackSortField == TrackSortField.TITLE,
                            )
    
                            page == 1 -> {
                                // 懒加载：第一次真正显示艺术家页时才做分组。
                                LaunchedEffect(Unit) { artistGroupsEnabled = true }
                                if (artistGroupsEnabled) {
                                    // 本帧行带着浮现动画组合，组合完成后立刻翻标记，
                                    // 之后滚动进来 / 切回来的行不再重复播放。
                                    LaunchedEffect(Unit) { artistRevealDone = true }
                                    val coverModifierFor: @Composable (String) -> Modifier =
                                        { title ->
                                            with(sharedTransitionScope) {
                                                Modifier.sharedElement(
                                                    rememberSharedContentState(
                                                        artistCoverKey(title),
                                                    ),
                                                    animatedContentScope,
                                                )
                                            }
                                        }
                                    CollectionContent(
                                        listState = artistListState,
                                        gridState = artistGridState,
                                        items = sortedArtistGroups.map { group ->
                                            // 列表里用圆形头像，网格里与其它集合一致用方角封面。
                                            val coverShape =
                                                if (artistViewMode == CollectionViewMode.LIST) {
                                                    CircleShape
                                                } else {
                                                    RoundedCornerShape(8.dp)
                                                }
                                            CollectionItem(
                                                key = group.title,
                                                title = group.title,
                                                trackCount = group.trackIds.size,
                                                pinned = group.title in pinnedArtists,
                                                cover = { coverModifier ->
                                                    val coverTrack = group.coverTrack
                                                    if (coverTrack != null) {
                                                        TrackCoverThumbnail(
                                                            track = coverTrack,
                                                            artworkCache = artworkCache,
                                                            onRequestArtwork = onRequestArtwork,
                                                            modifier = coverModifierFor(group.title)
                                                                .then(coverModifier)
                                                                .clip(coverShape),
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = coverModifier
                                                                .clip(coverShape)
                                                                .background(
                                                                    MaterialTheme.colorScheme
                                                                        .surfaceVariant,
                                                                ),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                imageVector =
                                                                    Icons.Rounded.Person,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                },
                                            )
                                        },
                                        viewMode = artistViewMode,
                                        showAlphabetRail = artistSortMode.showsAlphabetRail,
                                        reveal = !artistRevealDone,
                                        emptyContent = {
                                            EmptyLibrary(
                                                icon = Icons.Rounded.MusicOff,
                                                title = stringResource(
                                                    R.string.empty_library_title,
                                                ),
                                                message = stringResource(
                                                    R.string.empty_library_message,
                                                ),
                                            )
                                        },
                                        onItemClick = { item ->
                                            openGroup(
                                                LibraryGroupSelection(
                                                    title = item.title,
                                                    kind = LibraryGroupKind.ARTIST,
                                                    trackIds = trackIdsForArtist(
                                                        tracks,
                                                        item.title,
                                                        unknownArtist,
                                                    ),
                                                ),
                                            )
                                        },
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
    
                            page == 2 -> {
                                // 懒加载：第一次真正显示专辑页时才做分组。
                                LaunchedEffect(Unit) { albumGroupsEnabled = true }
                                if (albumGroupsEnabled) {
                                    LaunchedEffect(Unit) { albumRevealDone = true }
                                    val coverModifierFor: @Composable (String) -> Modifier =
                                        { title ->
                                            with(sharedTransitionScope) {
                                                Modifier.sharedElement(
                                                    rememberSharedContentState(
                                                        albumCoverKey(title),
                                                    ),
                                                    animatedContentScope,
                                                )
                                            }
                                        }
                                    CollectionContent(
                                        listState = albumListState,
                                        gridState = albumGridState,
                                        items = sortedAlbumGroups.map { group ->
                                            CollectionItem(
                                                key = group.title,
                                                title = group.title,
                                                trackCount = group.trackIds.size,
                                                pinned = group.title in pinnedAlbums,
                                                cover = { coverModifier ->
                                                    val coverTrack = group.coverTrack
                                                    if (coverTrack != null) {
                                                        TrackCoverThumbnail(
                                                            track = coverTrack,
                                                            artworkCache = artworkCache,
                                                            onRequestArtwork = onRequestArtwork,
                                                            modifier = coverModifierFor(group.title)
                                                                .then(coverModifier),
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = coverModifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    MaterialTheme.colorScheme
                                                                        .surfaceVariant,
                                                                ),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                imageVector =
                                                                    Icons.Rounded.Album,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                            )
                                                        }
                                                    }
                                                },
                                            )
                                        },
                                        viewMode = albumViewMode,
                                        showAlphabetRail = albumSortMode.showsAlphabetRail,
                                        reveal = !albumRevealDone,
                                        emptyContent = {
                                            EmptyLibrary(
                                                icon = Icons.Rounded.MusicOff,
                                                title = stringResource(
                                                    R.string.empty_library_title,
                                                ),
                                                message = stringResource(
                                                    R.string.empty_library_message,
                                                ),
                                            )
                                        },
                                        onItemClick = { item ->
                                            openGroup(
                                                LibraryGroupSelection(
                                                    title = item.title,
                                                    kind = LibraryGroupKind.ALBUM,
                                                    trackIds = tracks
                                                        .filter {
                                                            it.album.ifBlank {
                                                                unknownAlbum
                                                            } == item.title
                                                        }
                                                        .map(Track::id),
                                                ),
                                            )
                                        },
                                    )
                                } else {
                                    Box(modifier = Modifier.fillMaxSize())
                                }
                            }
    
                            else -> {
                                LaunchedEffect(Unit) { playlistRevealDone = true }
                                val playlistCoverModifierFor: @Composable (Long) -> Modifier =
                                    { playlistId ->
                                        with(sharedTransitionScope) {
                                            Modifier.sharedElement(
                                                rememberSharedContentState(
                                                    playlistCoverKey(playlistId),
                                                ),
                                                animatedContentScope,
                                            )
                                        }
                                    }
                                CollectionContent(
                                    listState = playlistListState,
                                    gridState = playlistGridState,
                                    items = sortedPlaylists.map { playlist ->
                                        val existingTrackIds =
                                            playlist.trackIds.filter(tracksById::containsKey)
                                        val firstTrack =
                                            existingTrackIds.firstOrNull()?.let(tracksById::get)
                                        CollectionItem(
                                            key = playlist.id.toString(),
                                            title = playlist.name,
                                            trackCount = existingTrackIds.size,
                                            pinned = playlist.id in pinnedPlaylistIds,
                                            cover = { coverModifier ->
                                                LaunchedEffect(playlist.id, playlist.coverUri) {
                                                    if (playlist.coverUri != null) {
                                                        onRequestPlaylistCover(playlist)
                                                    }
                                                }
                                                PlaylistCoverArt(
                                                    customCover = playlistCovers[playlist.id],
                                                    firstTrack = firstTrack,
                                                    artworkCache = artworkCache,
                                                    onRequestArtwork = onRequestArtwork,
                                                    modifier = playlistCoverModifierFor(
                                                        playlist.id,
                                                    ).then(coverModifier),
                                                )
                                            },
                                        )
                                    },
                                    viewMode = playlistViewMode,
                                    reveal = !playlistRevealDone,
                                    emptyContent = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 72.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Icon(
                                                imageVector =
                                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                            )
                                            Text(
                                                text = stringResource(R.string.no_playlists),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    },
                                    onItemClick = { item ->
                                        sortedPlaylists
                                            .firstOrNull { it.id.toString() == item.key }
                                            ?.let { playlist ->
                                                openGroup(
                                                    LibraryGroupSelection(
                                                        title = playlist.name,
                                                        kind = LibraryGroupKind.PLAYLIST,
                                                        trackIds = playlist.trackIds.filter(
                                                            tracksById::containsKey,
                                                        ),
                                                        playlistId = playlist.id,
                                                    ),
                                                )
                                            }
                                    },
                                )
                            }
                                }
                            }
                        // 排序时先在 loading 底下把新顺序组合 / 排版好，再整体显示出来：
                        // 重排那一帧的停顿被 loading 挡住，用户看不到卡顿。
                        if (sortingLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                        // 数据刚到位时骨架仍盖在列表上淡出，和条目的分块浮现交叉进行，
                        // 中间不会出现空白帧；位置与真实列表完全对齐，页面不跳动。
                        if (skeletonOverlay.showing) {
                            val overlayModifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = skeletonOverlay.alpha }
                            if (activeTab == 0) {
                                SongsSkeletonList(
                                    modifier = overlayModifier.then(
                                        if (trackSortField == TrackSortField.TITLE) {
                                            Modifier.padding(end = ALPHABET_RAIL_WIDTH)
                                        } else {
                                            Modifier
                                        },
                                    ),
                                )
                            } else {
                                libraryCollectionSkeleton(overlayModifier)
                            }
                        }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = searchOpen && activeGroup == null,
                enter = fadeIn(tween(PAGE_FADE_MS)),
                exit = fadeOut(tween(PAGE_FADE_MS)),
            ) {
                LibrarySearchOverlay(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = { searchOpen = false },
                    results = searchResults,
                    originTab = searchOriginTab,
                    unknownArtist = unknownArtist,
                    unknownAlbum = unknownAlbum,
                    tracksById = tracksById,
                    onSongClick = { track ->
                        searchOpen = false
                        if (track.id == currentTrack?.id) {
                            onTogglePlayPause()
                        } else {
                            onPlayTrack(track, searchResults.songs)
                        }
                    },
                    onArtistClick = { group ->
                        searchOpen = false
                        openGroup(
                            LibraryGroupSelection(
                                title = group.title,
                                kind = LibraryGroupKind.ARTIST,
                                trackIds = group.trackIds,
                            ),
                        )
                    },
                    onAlbumClick = { group ->
                        searchOpen = false
                        openGroup(
                            LibraryGroupSelection(
                                title = group.title,
                                kind = LibraryGroupKind.ALBUM,
                                trackIds = group.trackIds,
                            ),
                        )
                    },
                    onPlaylistClick = { playlist ->
                        searchOpen = false
                        openGroup(
                            LibraryGroupSelection(
                                title = playlist.name,
                                kind = LibraryGroupKind.PLAYLIST,
                                trackIds = playlist.trackIds.filter(tracksById::containsKey),
                                playlistId = playlist.id,
                            ),
                        )
                    },
                )
            }
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

    playlistToRename?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text(text = stringResource(R.string.rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    label = { Text(text = stringResource(R.string.playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renamePlaylistName.isNotBlank(),
                    onClick = {
                        onRenamePlaylist(playlist.id, renamePlaylistName)
                        selectedGroup = selectedGroup
                            ?.takeIf { it.playlistId == playlist.id }
                            ?.copy(title = renamePlaylistName.trim())
                        playlistToRename = null
                    },
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            trackId = playlistSeedTrack?.id.orEmpty(),
            onDismiss = { showAddToPlaylistDialog = false },
            onTogglePlaylist = { playlistId, included ->
                playlistSeedTrack?.let { track ->
                    onSetTrackInPlaylist(playlistId, track.id, included)
                }
            },
            onCreatePlaylist = {
                showAddToPlaylistDialog = false
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

@Composable
private fun LibraryHeader(
    trackCount: Int,
    folderCount: Int,
    onRescan: () -> Unit,
    onOpenKaraoke: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    showFullHeader: Boolean = true,
) {
    if (!showFullHeader) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = stringResource(R.string.navigation_menu),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                )
            }
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Rounded.Menu,
                contentDescription = stringResource(R.string.navigation_menu),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.library_summary, trackCount, folderCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search),
            )
        }
        IconButton(onClick = onOpenKaraoke) {
            Icon(
                imageVector = Icons.Rounded.RecordVoiceOver,
                contentDescription = stringResource(R.string.karaoke),
            )
        }
        IconButton(onClick = onRescan) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.rescan),
            )
        }
        IconButton(onClick = onOpenStatistics) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = stringResource(R.string.playback_statistics),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }
    }
}

@Composable
private fun GroupDetailHeader(
    title: String,
    subtitle: String,
    onSubtitleClick: (() -> Unit)?,
    coverTrack: Track?,
    coverBitmap: Bitmap?,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    coverModifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPlayAll: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null,
    isPinned: Boolean = false,
    onTogglePinned: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    val hasCover = coverBitmap != null || coverTrack != null
    Column(modifier = Modifier.fillMaxWidth()) {
        // 返回单独一行
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
        // 封面、信息、播放、菜单同一行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (coverBitmap != null) {
                CoverArt(
                    bitmap = coverBitmap,
                    modifier = coverModifier.size(DETAIL_COVER_SIZE),
                )
            } else if (coverTrack != null) {
                TrackCoverThumbnail(
                    track = coverTrack,
                    artworkCache = artworkCache,
                    onRequestArtwork = onRequestArtwork,
                    modifier = coverModifier.size(DETAIL_COVER_SIZE),
                )
            }
            if (hasCover) {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    modifier = if (onSubtitleClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Button, onClick = onSubtitleClick)
                            .padding(vertical = 3.dp)
                    } else {
                        Modifier.padding(vertical = 3.dp)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onSubtitleClick != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onPlayAll != null) {
                GroupPlaybackActionButton(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    onClick = onPlayAll,
                )
            }
            if (onShuffleAll != null) {
                GroupPlaybackActionButton(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.library_shuffle_all),
                    onClick = onShuffleAll,
                )
            }
            if (onTogglePinned != null) {
                IconButton(
                    onClick = onTogglePinned,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = stringResource(
                            if (isPinned) R.string.unpin else R.string.pin,
                        ),
                        modifier = Modifier.size(20.dp),
                        tint = if (isPinned) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            if (actions != null) {
                actions()
            }
        }
    }
}

@Composable
internal fun TrackCoverThumbnail(
    track: Track,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(track.id) { onRequestArtwork(track) }
    CoverArt(
        bitmap = rememberCoverBitmap(track, artworkCache),
        modifier = modifier,
    )
}

/**
 * 已解析过封面时返回真实封面，没有内嵌封面则返回默认封面；
 * 解析中返回 null，由 CoverArt 显示占位图。
 */
@Composable
private fun rememberCoverBitmap(
    track: Track,
    artworkCache: Map<String, Bitmap?>,
): Bitmap? {
    if (!artworkCache.containsKey(track.id)) return null
    val embedded = artworkCache[track.id]
    if (embedded != null) return embedded
    return remember(track.id) { createProceduralCover(track.id.hashCode()) }
}

@Composable
private fun LibraryTabs(
    visible: Boolean = true,
    pagerPosition: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val scheme = MaterialTheme.colorScheme
    val labels = listOf(
        stringResource(R.string.songs_tab),
        stringResource(R.string.artists_tab),
        stringResource(R.string.albums_tab),
        stringResource(R.string.playlists_tab),
    )
    val position = pagerPosition.coerceIn(0f, (LIBRARY_TAB_COUNT - 1).toFloat())
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = scheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            val tabWidth = maxWidth / LIBRARY_TAB_COUNT
            // 选中底片跟着手指（分页进度）一起滑动，滑动过程中就是连续的过渡动画。
            Box(
                modifier = Modifier
                    .offset(x = tabWidth * position)
                    .width(tabWidth)
                    .height(LIBRARY_TAB_HEIGHT)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.surface),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    val distance = abs(position - index).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(LIBRARY_TAB_HEIGHT)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(role = Role.Tab) { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            // 越接近当前页越"点亮"，滑动时颜色是渐变的。
                            color = lerp(scheme.onSurfaceVariant, scheme.onSurface, 1f - distance),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongsContent(
    tracks: List<Track>,
    animateEntrance: Boolean,
    listState: LazyListState,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowProperties: (Track) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
    showAlphabetRail: Boolean = true,
) {
    if (tracks.isEmpty()) {
        EmptyLibrary(
            icon = Icons.Rounded.MusicOff,
            title = stringResource(R.string.empty_library_title),
            message = stringResource(R.string.empty_library_message),
        )
        return
    }

    val sectionStarts = remember(tracks) { sectionStartIndexes(tracks) }
    val scope = rememberCoroutineScope()
    val activeLetter by remember(tracks, listState) {
        derivedStateOf {
            tracks.getOrNull(listState.firstVisibleItemIndex)
                ?.let { sectionKeyOf(it.title) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TrackList(
            tracks = tracks,
            animateEntrance = animateEntrance,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            artworkCache = artworkCache,
            onRequestArtwork = onRequestArtwork,
            onPlayTrack = onPlayTrack,
            onPlayNext = onPlayNext,
            onAddToPlaylist = onAddToPlaylist,
            onShowProperties = onShowProperties,
            onEditTags = onEditTags,
            onDeleteTrack = onDeleteTrack,
            onOpenAlbum = onOpenAlbum,
            onOpenArtist = onOpenArtist,
            listState = listState,
            modifier = if (showAlphabetRail) {
                Modifier.padding(end = ALPHABET_RAIL_WIDTH)
            } else {
                Modifier
            },
        )
        if (showAlphabetRail) {
        AlphabetRail(
            available = sectionStarts.keys,
            activeLetter = activeLetter,
            onLetterSelected = { letter ->
                sectionStarts[letter]?.let { index ->
                    scope.launch { listState.scrollToItem(index) }
                }
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.dp),
        )
        }

        // 常驻定位按钮：跳回正在播放的那首歌。
        val currentIndex = currentTrackId?.let { trackId ->
            tracks.indexOfFirst { it.id == trackId }
        } ?: -1
        if (currentIndex >= 0) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val viewportHeight = listState.layoutInfo.viewportSize.height
                        listState.animateScrollToItem(
                            index = currentIndex,
                            scrollOffset = -viewportHeight / 3,
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = stringResource(R.string.locate_current_track),
                )
            }
        }
    }
}

@Composable
private fun GroupPlaybackActionButton(
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

@Composable
private fun GroupTracksContent(
    tracks: List<Track>,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowProperties: (Track) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyLibrary(
            icon = Icons.Rounded.MusicOff,
            title = stringResource(R.string.playlist_empty),
            message = stringResource(R.string.empty_library_message),
        )
        return
    }
    TrackList(
        tracks = tracks,
        currentTrackId = currentTrackId,
        isPlaying = isPlaying,
        artworkCache = artworkCache,
        onRequestArtwork = onRequestArtwork,
        onPlayTrack = onPlayTrack,
        onPlayNext = onPlayNext,
        onAddToPlaylist = onAddToPlaylist,
        onShowProperties = onShowProperties,
        onEditTags = onEditTags,
        onDeleteTrack = onDeleteTrack,
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    )
}

@Composable
internal fun TrackList(
    tracks: List<Track>,
    animateEntrance: Boolean = false,
    currentTrackId: String?,
    isPlaying: Boolean,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowProperties: (Track) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { _, track ->
            LaunchedEffect(track.id) { onRequestArtwork(track) }
            TrackRow(
                track = track,
                listState = listState,
                artwork = rememberCoverBitmap(track, artworkCache),
                isCurrent = track.id == currentTrackId,
                isPlaying = isPlaying && track.id == currentTrackId,
                reveal = animateEntrance,
                onClick = { onPlayTrack(track) },
                onPlayNext = onPlayNext,
                onAddToPlaylist = onAddToPlaylist,
                onShowProperties = onShowProperties,
            onEditTags = onEditTags,
            onDeleteTrack = onDeleteTrack,
                onOpenAlbum = onOpenAlbum,
                onOpenArtist = onOpenArtist,
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 84.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
/** 歌曲文件的 MIME 类型（分享 / 外部编辑用）。 */
private fun trackMimeType(track: Track): String = when (track.extension) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "m4a", "mp4" -> "audio/mp4"
    "ogg", "opus" -> "audio/ogg"
    "wav" -> "audio/wav"
    "aac" -> "audio/aac"
    "ape" -> "audio/ape"
    else -> "audio/*"
}

/** 通过系统分享面板分享歌曲文件。 */
private fun shareTrackFile(context: Context, track: Track) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = trackMimeType(track)
        putExtra(Intent.EXTRA_STREAM, track.uri)
        clipData = ClipData.newRawUri("audio", track.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_track)),
    )
}

/** 用 Lyrico 打开单曲标签编辑页；未安装或调起失败返回 false 并提示。 */
private fun editTrackWithLyrico(context: Context, track: Track): Boolean {
    // 逐个包名尝试；任何调起异常（未安装 / 安全策略 / 版本不兼容）都不应带崩宿主
    for (packageName in listOf("com.lonx.lyrico", "com.lonx.lyrico.debug")) {
        val launched = runCatching {
            val intent = Intent("com.lonx.lyrico.action.EDIT_TAG").apply {
                setPackage(packageName)
                setDataAndType(track.uri, "audio/*")
                clipData = ClipData.newRawUri("audio", track.uri)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            context.startActivity(intent)
        }
        if (launched.isSuccess) return true
    }
    // 兜底：调不起来就走系统分享面板，用户仍可在分享目标里选择 Lyrico
    runCatching { shareTrackFile(context, track) }
    Toast.makeText(
        context,
        context.getString(R.string.lyrico_not_installed),
        Toast.LENGTH_SHORT,
    ).show()
    return false
}

/** 通过 SAF 永久删除歌曲文件（连同其歌词文件）；失败（无权限等）返回 false。 */
private fun deleteTrackFile(context: Context, track: Track): Boolean {
    val deleted = runCatching {
        DocumentsContract.deleteDocument(context.contentResolver, track.uri)
    }.onFailure { error ->
        android.util.Log.w("QYLibrary", "删除歌曲失败: ${error.message}")
    }.getOrDefault(false)
    if (deleted) {
        track.lyricUri?.let { lyricUri ->
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, lyricUri)
            }
        }
    }
    return deleted
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackRow(
    track: Track,
    listState: LazyListState,
    artwork: Bitmap?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    reveal: Boolean,
    onClick: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowProperties: (Track) -> Unit,
    onEditTags: (Track) -> Unit,
    onDeleteTrack: (Track) -> Unit,
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember(track.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(track.id) { mutableStateOf(false) }
    // 数据到位后：封面 → 歌名 → 艺术家 → 音质标签依次淡入并上移 8px，每块错开 60ms。
    val coverReveal = rememberBlockReveal(reveal, order = 0)
    val titleReveal = rememberBlockReveal(reveal, order = 1)
    val artistReveal = rememberBlockReveal(reveal, order = 2)
    val qualityReveal = rememberBlockReveal(reveal, order = 3)
    val revealOffsetPx = with(LocalDensity.current) { BLOCK_REVEAL_OFFSET.toPx() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            // 从底部滑入的条目由小变大，缩放原点在左侧中点，封面与歌名始终左对齐。
            .listScrollScale(listState, track.id)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(
            bitmap = artwork,
            modifier = Modifier
                .size(48.dp)
                .blockReveal(coverReveal, revealOffsetPx),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title,
                    // fill = false：标题只占自身宽度，音质标识紧跟在歌名后面。
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .blockReveal(titleReveal, revealOffsetPx),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                QualityBadge(
                    quality = track.quality,
                    compact = true,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .blockReveal(qualityReveal, revealOffsetPx),
                )
                if (isCurrent) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                        tint = if (isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Text(
                text = track.artist.ifBlank { stringResource(R.string.unknown_artist) },
                modifier = Modifier.blockReveal(artistReveal, revealOffsetPx),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            if (menuExpanded) {
                ModalBottomSheet(onDismissRequest = { menuExpanded = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                    // 「标签」按需求不带图标。
                    TrackMenuSheetRow(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = stringResource(R.string.play_next),
                        color = Color(0xFF3D7FFF),
                        onClick = {
                            menuExpanded = false
                            onPlayNext(track)
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.PlaylistAdd,
                        label = stringResource(R.string.add_to_playlist),
                        color = Color(0xFF34A853),
                        onClick = {
                            menuExpanded = false
                            onAddToPlaylist(track)
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.Person,
                        label = stringResource(R.string.view_artist),
                        color = Color(0xFFEC407A),
                        onClick = {
                            menuExpanded = false
                            onOpenArtist(track)
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.Album,
                        label = stringResource(R.string.view_album),
                        color = Color(0xFF5C6BC0),
                        onClick = {
                            menuExpanded = false
                            onOpenAlbum(track)
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.Lyrics,
                        label = stringResource(R.string.tag_editor_menu),
                        color = Color(0xFF00ACC1),
                        onClick = {
                            menuExpanded = false
                            onEditTags(track)
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.Info,
                        label = stringResource(R.string.properties),
                        color = Color(0xFFF9A825),
                        onClick = {
                            menuExpanded = false
                            onShowProperties(track)
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.Edit,
                        label = stringResource(R.string.track_menu_lyrico),
                        color = Color(0xFF9C27B0),
                        onClick = {
                            menuExpanded = false
                            if (!editTrackWithLyrico(context, track)) {
                                menuExpanded = false
                            }
                        },
                    )
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.Share,
                        label = stringResource(R.string.share_track),
                        color = Color(0xFF00897B),
                        onClick = {
                            menuExpanded = false
                            shareTrackFile(context, track)
                        },
                    )
                    HorizontalDivider()
                    TrackMenuSheetRow(
                        icon = Icons.Rounded.DeleteForever,
                        label = stringResource(R.string.track_menu_delete),
                        color = Color(0xFFE53935),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            menuExpanded = false
                            showDeleteConfirm = true
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(text = stringResource(R.string.track_delete_confirm_title)) },
                text = {
                    Text(
                        text = stringResource(
                            R.string.track_delete_confirm_message,
                            track.title,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        val deleted = deleteTrackFile(context, track)
                        val message = if (deleted) {
                            context.getString(R.string.track_delete_done, track.title)
                        } else {
                            context.getString(R.string.track_delete_failed)
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        if (deleted) onDeleteTrack(track)
                    }) {
                        Text(
                            text = stringResource(R.string.track_menu_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun TrackMenuSheetRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    color: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null && color != Color.Unspecified) {
            // 彩色图标：浅色圆角底 + 同色系图标
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.14f)),
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
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (tint == Color.Unspecified) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    tint
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (tint == Color.Unspecified) {
                MaterialTheme.colorScheme.onSurface
            } else {
                tint
            },
        )
    }
}

/** 歌单封面：自定义封面 > 第一首歌的封面 > 默认封面。 */
@Composable
private fun PlaylistCoverArt(
    customCover: Bitmap?,
    firstTrack: Track?,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        customCover != null -> CoverArt(bitmap = customCover, modifier = modifier)
        firstTrack != null -> TrackCoverThumbnail(
            track = firstTrack,
            artworkCache = artworkCache,
            onRequestArtwork = onRequestArtwork,
            modifier = modifier,
        )

        else -> CoverArt(bitmap = null, modifier = modifier)
    }
}

@Composable
internal fun StartupScanScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
        }
    }
}

/**
 * 歌曲列表骨架屏：每行的封面、歌名、音质标签、艺术家与真实条目同形同尺寸。
 * 所有骨架共用同一道斜向光带，每 [SKELETON_SHIMMER_PERIOD_MS] 从左往右扫过一轮。
 */
@Composable
private fun SongsSkeletonList(
    modifier: Modifier = Modifier,
    rowCount: Int = SKELETON_ROW_COUNT,
) {
    val shimmer = rememberSkeletonShimmer()
    val blockColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .skeletonBandOverlay(shimmer.value),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(rowCount) { index ->
                SongSkeletonRow(
                    index = index,
                    blockColor = blockColor,
                    dividerColor = dividerColor,
                )
            }
        }
    }
}

/** 骨架行：行高、内边距、分割线缩进都与 TrackRow 保持一致。 */
@Composable
private fun SongSkeletonRow(
    index: Int,
    blockColor: Color,
    dividerColor: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(start = 20.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(
                color = blockColor,
                modifier = Modifier.size(48.dp),
                corner = 8.dp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(
                        color = blockColor,
                        modifier = Modifier
                            .fillMaxWidth(
                                SKELETON_TITLE_FRACTIONS[index % SKELETON_TITLE_FRACTIONS.size],
                            )
                            .height(15.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    SkeletonBlock(
                        color = blockColor,
                        modifier = Modifier
                            .width(32.dp)
                            .height(15.dp),
                        corner = 3.dp,
                    )
                }
                SkeletonBlock(
                    color = blockColor,
                    modifier = Modifier
                        .fillMaxWidth(
                            SKELETON_ARTIST_FRACTIONS[index % SKELETON_ARTIST_FRACTIONS.size],
                        )
                        .height(12.dp),
                )
            }
            // 右侧"更多"按钮的位置也占住，保证和真实条目同宽。
            Spacer(modifier = Modifier.size(48.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 84.dp),
            color = dividerColor,
        )
    }
}

@Composable
private fun LibraryLoading(
    title: String,
    hint: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(44.dp),
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun EmptyLibrary(
    icon: ImageVector,
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class LibraryGroup(
    val title: String,
    val kind: LibraryGroupKind,
    val trackIds: List<String>,
    val coverTrack: Track?,
)

private data class LibraryGroupSelection(
    val title: String,
    val kind: LibraryGroupKind,
    val trackIds: List<String>,
    /** 歌单详情页用它读取自定义封面。 */
    val playlistId: Long? = null,
    /** true 表示这次是从播放页跳进来的，返回时应该回到播放页。 */
    val openedFromPlayer: Boolean = false,
)

private data class LibrarySearchResults(
    val songs: List<Track> = emptyList(),
    val artists: List<LibraryGroup> = emptyList(),
    val albums: List<LibraryGroup> = emptyList(),
    val playlists: List<UserPlaylist> = emptyList(),
) {
    val isEmpty: Boolean
        get() = songs.isEmpty() &&
            artists.isEmpty() &&
            albums.isEmpty() &&
            playlists.isEmpty()
}

private fun filterLibrarySearchResults(
    query: String,
    songs: List<Track>,
    artists: List<LibraryGroup>,
    albums: List<LibraryGroup>,
    playlists: List<UserPlaylist>,
): LibrarySearchResults {
    if (query.isBlank()) return LibrarySearchResults()
    return LibrarySearchResults(
        songs = songs.filter { track ->
            matchesLibrarySearch(query, listOf(track.title, track.artist, track.album))
        },
        artists = artists.filter { group ->
            matchesLibrarySearch(query, listOf(group.title))
        },
        albums = albums.filter { group ->
            matchesLibrarySearch(query, listOf(group.title))
        },
        playlists = playlists.filter { playlist ->
            matchesLibrarySearch(query, listOf(playlist.name))
        },
    )
}

private fun matchesLibrarySearch(
    query: String,
    values: List<String>,
): Boolean {
    val terms = normalizeLibrarySearchText(query)
        .split(SEARCH_TERM_SEPARATOR)
        .filter(String::isNotBlank)
    if (terms.isEmpty()) return false
    val haystack = values.joinToString(" ") { value ->
        buildString {
            append(normalizeLibrarySearchText(value))
            val pinyin = pinyinOf(value).lowercase(Locale.ROOT)
            if (pinyin.isNotBlank()) {
                append(' ')
                append(pinyin)
                append(' ')
                append(pinyin.filterNot(Char::isWhitespace))
            }
        }
    }
    return terms.all(haystack::contains)
}

private fun normalizeLibrarySearchText(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(SECTION_DIACRITICS, "")

private val SEARCH_TERM_SEPARATOR = Regex("\\s+")

private fun trackIdsForArtist(
    tracks: List<Track>,
    artistName: String,
    unknownArtist: String,
): List<String> = tracks
    .filter { it.artist.ifBlank { unknownArtist } == artistName }
    .map(Track::id)

/** 详情页头部的封面尺寸。 */
/** 列表封面与详情页头部封面共用的共享元素 key：三类集合各一套。 */
private fun playlistCoverKey(playlistId: Long): String = "playlist-cover-$playlistId"
private fun albumCoverKey(title: String): String = "album-cover-$title"
private fun artistCoverKey(title: String): String = "artist-cover-$title"

private fun LibraryGroupSelection.coverTransitionKey(): String? = when (kind) {
    LibraryGroupKind.PLAYLIST -> playlistId?.let(::playlistCoverKey)
    LibraryGroupKind.ALBUM -> albumCoverKey(title)
    LibraryGroupKind.ARTIST -> artistCoverKey(title)
}

private val DETAIL_COVER_SIZE = 96.dp
/** 点 tab 切换页面的补间时长：短一点，低端设备上整页重绘不容易看出卡顿。 */
private const val TAB_SWITCH_MS = 120
/** tab 按钮高度（选中底片与文字框共用，保证指示器高度一致）。 */
private val LIBRARY_TAB_HEIGHT = 40.dp
/** 等播放页浮层完全盖住主界面之后再收起「从播放页进来的详情页」。 */
private const val PLAYER_COVER_SETTLE_MS = 260L
private const val PAGE_FADE_MS = 200
/** 骨架屏行数：铺满一屏即可。 */
private const val SKELETON_ROW_COUNT = 9
/** 骨架条长度按行变化，避免整屏看起来是同一根。 */
private val SKELETON_TITLE_FRACTIONS = listOf(0.62f, 0.46f, 0.72f, 0.54f, 0.66f, 0.42f)
private val SKELETON_ARTIST_FRACTIONS = listOf(0.30f, 0.22f, 0.38f, 0.26f, 0.34f, 0.20f)
/** 列表 ↔ 详情页整屏推入 / 退回的时长：两页同时绘制的帧数更少，同时不会显得仓促。 */
private const val DETAIL_SLIDE_MS = 280
/** 改排序时 loading 停留的时长：够列表在它下面把新顺序组合、排版完就行。 */
private const val SORT_LOADING_MS = 260L

private const val LIBRARY_TAB_COUNT = 4
private val SECTION_DIACRITICS = Regex("\\p{Mn}+")
private val HAN_LATIN_TRANSLITERATOR: Transliterator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
    } else {
        null
    }
}

/** 索引分组：拉丁字母取首字母，中文转拼音后取首字母，其他归到 #。 */
internal fun sectionKeyOf(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "#"
    val first = trimmed[0]
    if (first in 'A'..'Z' || first in 'a'..'z') {
        return first.uppercaseChar().toString()
    }
    val latin = pinyinOf(trimmed)
    val letter = latin.firstOrNull()
    return if (letter != null && (letter in 'a'..'z' || letter in 'A'..'Z')) {
        letter.uppercaseChar().toString()
    } else {
        "#"
    }
}

/** 中文标题转无音调拼音；API 29 以下没有可用的转写器，返回空串。 */
private fun pinyinOf(text: String): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ""
    // 一次 ICU 转写要几毫秒，而排序 / A-Z 索引会把同一个名字问很多遍，缓存住。
    PINYIN_CACHE[text]?.let { return it }
    if (PINYIN_CACHE.size >= PINYIN_CACHE_LIMIT) PINYIN_CACHE.clear()
    val transliterated = runCatching {
        HAN_LATIN_TRANSLITERATOR?.transliterate(text)
    }.getOrNull() ?: return ""
    val result = Normalizer.normalize(transliterated, Normalizer.Form.NFD)
        .replace(SECTION_DIACRITICS, "")
        .trim()
    PINYIN_CACHE[text] = result
    return result
}

private const val PINYIN_CACHE_LIMIT = 1024
private val PINYIN_CACHE = java.util.concurrent.ConcurrentHashMap<String, String>()

/** 排序用的 Collator 只建一次，getInstance 本身也要花几毫秒。 */
private val SORT_COLLATOR: Collator by lazy { Collator.getInstance(Locale.getDefault()) }

private fun sectionRank(key: String): Int =
    if (key == "#") SECTION_LETTERS.lastIndex else key[0] - 'A'

private fun sortTracksBySection(tracks: List<Track>): List<Track> {
    return tracks.sortedWith(
        compareBy<Track> { sectionRank(sectionKeyOf(it.title)) }
            .thenComparator { first, second -> SORT_COLLATOR.compare(first.title, second.title) },
    )
}

/** 艺术家 / 专辑列表的排序方式。 */
private enum class GroupSortMode {
    ALPHABETICAL,
    ALPHABETICAL_DESC,
    COUNT_ASC,
    COUNT_DESC,
}

/** 按字母（中文按拼音）或曲目数量排序。 */
private fun sortGroups(
    groups: List<LibraryGroup>,
    mode: GroupSortMode,
    pinnedTitles: Set<String> = emptySet(),
): List<LibraryGroup> {
    if (groups.size < 2) return groups
    // 先把拼音排序键算好再排：转写放到比较器里会被调用 O(n log n) 次，改排序方式就会卡住。
    val keyed = groups.map { it to groupSortKey(it.title) }
    val byTitle = Comparator<Pair<LibraryGroup, String>> { first, second ->
        first.second.compareTo(second.second)
    }
    // 倒序时把空名/无拼音的 "#" 组仍放在最后。
    val byTitleDesc = Comparator<Pair<LibraryGroup, String>> { first, second ->
        val firstKey = first.second
        val secondKey = second.second
        when {
            firstKey == SORT_KEY_LAST && secondKey != SORT_KEY_LAST -> 1
            secondKey == SORT_KEY_LAST && firstKey != SORT_KEY_LAST -> -1
            else -> secondKey.compareTo(firstKey)
        }
    }
    val result = when (mode) {
        GroupSortMode.ALPHABETICAL -> keyed.sortedWith(byTitle)
        GroupSortMode.ALPHABETICAL_DESC -> keyed.sortedWith(byTitleDesc)
        GroupSortMode.COUNT_ASC -> keyed.sortedWith(
            compareBy<Pair<LibraryGroup, String>> { it.first.trackIds.size }.then(byTitle),
        )

        GroupSortMode.COUNT_DESC -> keyed.sortedWith(
            compareByDescending<Pair<LibraryGroup, String>> { it.first.trackIds.size }
                .then(byTitle),
        )
    }
    val sorted = result.map { it.first }
    if (pinnedTitles.isEmpty()) return sorted
    val (pinned, rest) = sorted.partition { it.title in pinnedTitles }
    return pinned.sortedBy { groupSortKey(it.title) } + rest
}

/** 排序键：中文取拼音、其它取原文，统一小写；空名排最后。 */
private fun groupSortKey(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return SORT_KEY_LAST
    val latin = pinyinOf(trimmed)
    return if (latin.isNotBlank()) {
        latin.lowercase(Locale.ROOT)
    } else {
        trimmed.lowercase(Locale.ROOT)
    }
}

/** 歌单排序：字母（名称）或数量（歌单内仍存在的曲目数）。 */
private fun sortPlaylists(
    playlists: List<UserPlaylist>,
    mode: GroupSortMode,
    tracksById: Map<String, Track>,
    pinnedIds: Set<Long> = emptySet(),
): List<UserPlaylist> {
    if (playlists.size < 2) return playlists
    // 同上：先算好名称键，再按数量 / 字母排序。
    val keyed = playlists.map { it to groupSortKey(it.name) }
    val byTitle = Comparator<Pair<UserPlaylist, String>> { first, second ->
        first.second.compareTo(second.second)
    }
    val countOf: (Pair<UserPlaylist, String>) -> Int = { entry ->
        entry.first.trackIds.count { tracksById.containsKey(it) }
    }
    val sorted = when (mode) {
        GroupSortMode.ALPHABETICAL -> keyed.sortedWith(byTitle)
        GroupSortMode.ALPHABETICAL_DESC -> keyed.sortedWith(byTitle.reversed())
        GroupSortMode.COUNT_ASC -> keyed.sortedWith(compareBy(countOf).then(byTitle))
        GroupSortMode.COUNT_DESC -> keyed.sortedWith(compareByDescending(countOf).then(byTitle))
    }.map { it.first }
    if (pinnedIds.isEmpty()) return sorted
    val (pinned, rest) = sorted.partition { it.id in pinnedIds }
    return pinned.sortedBy { groupSortKey(it.name) } + rest
}

/** 歌曲按艺术家 / 专辑分组排序：先算好（分组键, 标题键），避免在比较器里反复转写拼音。 */
private fun sortTracksByGroupKey(
    tracks: List<Track>,
    ascending: Boolean,
    groupNameOf: (Track) -> String,
): List<Track> {
    if (tracks.size < 2) return tracks
    val keyed = tracks.map { track ->
        Triple(track, groupSortKey(groupNameOf(track)), groupSortKey(track.title))
    }
    val comparator = compareBy<Triple<Track, String, String>> { it.second }
        .thenBy { it.third }
    val order = if (ascending) comparator else comparator.reversed()
    return keyed.sortedWith(order).map { it.first }
}

private const val SORT_KEY_LAST = "\uFFFF"

@Composable
private fun GroupSortMenuButton(
    mode: GroupSortMode,
    onModeChange: (GroupSortMode) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        // 排序只留一个图标按钮，当前方式在菜单里用勾选表示。
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = stringResource(
                    R.string.sort_button,
                    stringResource(mode.labelRes()),
                ),
                modifier = Modifier.size(22.dp),
                tint = if (mode.isAlphabetical && mode.isAscending) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            val byField = mode.isAlphabetical
            val ascending = mode.isAscending
            SortMenuLabel(text = stringResource(R.string.sort_field))
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.sort_field_alphabetical)) },
                trailingIcon = { if (byField) SortMenuCheck() },
                onClick = {
                    menuExpanded = false
                    onModeChange(
                        if (ascending) {
                            GroupSortMode.ALPHABETICAL
                        } else {
                            GroupSortMode.ALPHABETICAL_DESC
                        },
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.sort_field_count)) },
                trailingIcon = { if (!byField) SortMenuCheck() },
                onClick = {
                    menuExpanded = false
                    onModeChange(
                        if (ascending) GroupSortMode.COUNT_ASC else GroupSortMode.COUNT_DESC,
                    )
                },
            )
            HorizontalDivider()
            SortMenuLabel(text = stringResource(R.string.sort_order))
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.sort_order_asc)) },
                trailingIcon = { if (ascending) SortMenuCheck() },
                onClick = {
                    menuExpanded = false
                    onModeChange(
                        if (byField) GroupSortMode.ALPHABETICAL else GroupSortMode.COUNT_ASC,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.sort_order_desc)) },
                trailingIcon = { if (!ascending) SortMenuCheck() },
                onClick = {
                    menuExpanded = false
                    onModeChange(
                        if (byField) {
                            GroupSortMode.ALPHABETICAL_DESC
                        } else {
                            GroupSortMode.COUNT_DESC
                        },
                    )
                },
            )
        }
    }
}

/** 歌曲列表的排序按钮（标题 / 艺术家 / 专辑 + 正倒序）。 */
@Composable
private fun TrackSortMenuButton(
    field: TrackSortField,
    ascending: Boolean,
    onFieldChange: (TrackSortField) -> Unit,
    onOrderChange: (Boolean) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        // 排序只留一个图标按钮，当前方式在菜单里用勾选表示。
        IconButton(
            onClick = { menuExpanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = stringResource(
                    R.string.sort_button,
                    stringResource(field.labelRes()) + if (ascending) " ↑" else " ↓",
                ),
                modifier = Modifier.size(22.dp),
                tint = if (field == TrackSortField.TITLE && ascending) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            SortMenuLabel(text = stringResource(R.string.sort_field))
            TrackSortField.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = stringResource(option.labelRes())) },
                    trailingIcon = { if (option == field) SortMenuCheck() },
                    onClick = {
                        menuExpanded = false
                        onFieldChange(option)
                    },
                )
            }
            HorizontalDivider()
            SortMenuLabel(text = stringResource(R.string.sort_order))
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.sort_order_asc)) },
                trailingIcon = { if (ascending) SortMenuCheck() },
                onClick = {
                    menuExpanded = false
                    onOrderChange(true)
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.sort_order_desc)) },
                trailingIcon = { if (!ascending) SortMenuCheck() },
                onClick = {
                    menuExpanded = false
                    onOrderChange(false)
                },
            )
        }
    }
}

private fun TrackSortField.labelRes(): Int = when (this) {
    TrackSortField.TITLE -> R.string.sort_field_title
    TrackSortField.ARTIST -> R.string.sort_field_artist
    TrackSortField.ALBUM -> R.string.sort_field_album
}

@Composable
private fun LibrarySearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    results: LibrarySearchResults,
    /** 打开搜索时所在的 tab（0 歌曲 / 1 艺术家 / 2 专辑 / 3 歌单），对应分节排在最前。 */
    originTab: Int,
    unknownArtist: String,
    unknownAlbum: String,
    tracksById: Map<String, Track>,
    onSongClick: (Track) -> Unit,
    onArtistClick: (LibraryGroup) -> Unit,
    onAlbumClick: (LibraryGroup) -> Unit,
    onPlaylistClick: (UserPlaylist) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.search)) },
                    placeholder = { Text(text = stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboardController?.hide() },
                    ),
                )
            }

            when {
                query.isBlank() -> {
                    SearchMessage(
                        title = stringResource(R.string.search_prompt_title),
                        message = stringResource(R.string.search_prompt_message),
                    )
                }

                results.isEmpty -> {
                    SearchMessage(
                        title = stringResource(R.string.search_empty_title, query.trim()),
                        message = stringResource(R.string.search_empty_message),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        // 打开搜索时所在 tab 对应的一类排在最前：
                        // 歌曲页优先匹配歌名、艺术家页优先艺术家、专辑页优先专辑、歌单页优先歌单。
                        val sectionOrder = when (originTab) {
                            1 -> listOf(1, 0, 2, 3)
                            2 -> listOf(2, 0, 1, 3)
                            3 -> listOf(3, 0, 1, 2)
                            else -> listOf(0, 1, 2, 3)
                        }
                        sectionOrder.forEach { section ->
                            when (section) {
                                0 -> if (results.songs.isNotEmpty()) {
                                    item(key = "search-header-songs") {
                                        SearchSectionHeader(
                                            title = stringResource(R.string.songs_tab),
                                            count = results.songs.size,
                                        )
                                    }
                                    items(
                                        items = results.songs,
                                        key = { track -> "search-song-${track.id}" },
                                    ) { track ->
                                        val subtitle = listOf(
                                            track.artist.ifBlank { unknownArtist },
                                            track.album.ifBlank { unknownAlbum },
                                        ).distinct().joinToString(" · ")
                                        SearchResultRow(
                                            icon = Icons.Rounded.GraphicEq,
                                            title = track.title,
                                            subtitle = subtitle,
                                            trailingIcon = Icons.Rounded.PlayArrow,
                                            onClick = { onSongClick(track) },
                                        )
                                    }
                                }

                                1 -> if (results.artists.isNotEmpty()) {
                                    item(key = "search-header-artists") {
                                        SearchSectionHeader(
                                            title = stringResource(R.string.artists_tab),
                                            count = results.artists.size,
                                        )
                                    }
                                    items(
                                        items = results.artists,
                                        key = { group -> "search-artist-${group.title}" },
                                    ) { group ->
                                        SearchResultRow(
                                            icon = Icons.Rounded.Person,
                                            title = group.title,
                                            subtitle = stringResource(
                                                R.string.track_count,
                                                group.trackIds.size,
                                            ),
                                            trailingIcon = Icons.Rounded.ChevronRight,
                                            onClick = { onArtistClick(group) },
                                        )
                                    }
                                }

                                2 -> if (results.albums.isNotEmpty()) {
                                    item(key = "search-header-albums") {
                                        SearchSectionHeader(
                                            title = stringResource(R.string.albums_tab),
                                            count = results.albums.size,
                                        )
                                    }
                                    items(
                                        items = results.albums,
                                        key = { group -> "search-album-${group.title}" },
                                    ) { group ->
                                        SearchResultRow(
                                            icon = Icons.Rounded.Album,
                                            title = group.title,
                                            subtitle = stringResource(
                                                R.string.track_count,
                                                group.trackIds.size,
                                            ),
                                            trailingIcon = Icons.Rounded.ChevronRight,
                                            onClick = { onAlbumClick(group) },
                                        )
                                    }
                                }

                                else -> if (results.playlists.isNotEmpty()) {
                                    item(key = "search-header-playlists") {
                                        SearchSectionHeader(
                                            title = stringResource(R.string.playlists_tab),
                                            count = results.playlists.size,
                                        )
                                    }
                                    items(
                                        items = results.playlists,
                                        key = { playlist -> "search-playlist-${playlist.id}" },
                                    ) { playlist ->
                                        val trackCount =
                                            playlist.trackIds.count(tracksById::containsKey)
                                        SearchResultRow(
                                            icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                            title = playlist.name,
                                            subtitle = stringResource(
                                                R.string.track_count,
                                                trackCount,
                                            ),
                                            trailingIcon = Icons.Rounded.ChevronRight,
                                            onClick = { onPlaylistClick(playlist) },
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
private fun SearchSectionHeader(
    title: String,
    count: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = trailingIcon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
    )
}

@Composable
private fun SearchMessage(
    title: String,
    message: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/** tab 下面一行：左侧总数，右侧「播放 / 随机播放 / 视图切换 / 排序」。 */
@Composable
private fun LibraryTabToolbar(
    tab: Int,
    countText: String,
    groupSortMode: GroupSortMode,
    onGroupSortModeChange: (GroupSortMode) -> Unit,
    trackSortField: TrackSortField,
    trackSortAscending: Boolean,
    onTrackSortFieldChange: (TrackSortField) -> Unit,
    onTrackSortOrderChange: (Boolean) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** 集合页（艺术家 / 专辑 / 歌单）的展示形式；歌曲 tab 传 null 不显示切换按钮。 */
    viewMode: CollectionViewMode? = null,
    onToggleViewMode: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = countText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        IconButton(
            onClick = onPlayAll,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.library_play_all),
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(
            onClick = onShuffleAll,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.library_shuffle_all),
                modifier = Modifier.size(22.dp),
            )
        }
        if (viewMode != null && onToggleViewMode != null) {
            ViewModeToggleButton(
                viewMode = viewMode,
                onViewModeChange = { onToggleViewMode() },
            )
        }
        if (tab == 0) {
            TrackSortMenuButton(
                field = trackSortField,
                ascending = trackSortAscending,
                onFieldChange = onTrackSortFieldChange,
                onOrderChange = onTrackSortOrderChange,
            )
        } else {
            GroupSortMenuButton(mode = groupSortMode, onModeChange = onGroupSortModeChange)
        }
    }
}

@Composable
private fun SortMenuCheck() {
    Icon(
        imageVector = Icons.Rounded.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun SortMenuLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 当前排序依据是否为字母。 */
private val GroupSortMode.isAlphabetical: Boolean
    get() = this == GroupSortMode.ALPHABETICAL || this == GroupSortMode.ALPHABETICAL_DESC

/** 当前是否为正序。 */
private val GroupSortMode.isAscending: Boolean
    get() = this == GroupSortMode.ALPHABETICAL || this == GroupSortMode.COUNT_ASC

private fun GroupSortMode.labelRes(): Int = when (this) {
    GroupSortMode.ALPHABETICAL -> R.string.sort_alphabetical
    GroupSortMode.ALPHABETICAL_DESC -> R.string.sort_alphabetical_desc
    GroupSortMode.COUNT_ASC -> R.string.sort_count_asc
    GroupSortMode.COUNT_DESC -> R.string.sort_count_desc
}

/** 按字母排序（正序或倒序）时显示右侧字母导航。 */
private val GroupSortMode.showsAlphabetRail: Boolean
    get() = this == GroupSortMode.ALPHABETICAL || this == GroupSortMode.ALPHABETICAL_DESC

/** 歌曲列表的排序字段。 */
private enum class TrackSortField {
    TITLE,
    ARTIST,
    ALBUM,
}

private fun buildArtistGroups(
    tracks: List<Track>,
    unknownArtist: String,
): List<LibraryGroup> = tracks
    .groupBy { it.artist.ifBlank { unknownArtist } }
    .map { (name, groupTracks) ->
        LibraryGroup(
            title = name,
            kind = LibraryGroupKind.ARTIST,
            trackIds = groupTracks.map(Track::id),
            coverTrack = groupTracks.firstOrNull(),
        )
    }

private fun buildAlbumGroups(
    tracks: List<Track>,
    unknownAlbum: String,
): List<LibraryGroup> = tracks
    .groupBy { it.album.ifBlank { unknownAlbum } }
    .map { (name, groupTracks) ->
        LibraryGroup(
            title = name,
            kind = LibraryGroupKind.ALBUM,
            trackIds = groupTracks.map(Track::id),
            coverTrack = groupTracks.firstOrNull(),
        )
    }

/** 按标题（中文拼音）/ 艺术家 / 专辑排序歌曲列表。 */
private fun sortTracksFor(
    tracks: List<Track>,
    field: TrackSortField,
    ascending: Boolean,
): List<Track> = when (field) {
    // 标题排序保持和 A-Z 索引一致的分段顺序。
    TrackSortField.TITLE -> sortTracksBySection(tracks).let { if (ascending) it else it.reversed() }

    TrackSortField.ARTIST -> sortTracksByGroupKey(tracks, ascending) { it.artist }

    TrackSortField.ALBUM -> sortTracksByGroupKey(tracks, ascending) { it.album }
}

private fun sectionStartIndexes(sortedTracks: List<Track>): Map<String, Int> {
    val starts = linkedMapOf<String, Int>()
    sortedTracks.forEachIndexed { index, track ->
        starts.getOrPut(sectionKeyOf(track.title)) { index }
    }
    return starts
}
