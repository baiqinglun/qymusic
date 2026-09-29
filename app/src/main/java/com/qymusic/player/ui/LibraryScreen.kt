package com.qymusic.player.ui

import android.graphics.Bitmap
import android.icu.text.Transliterator
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.qymusic.player.R
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
    artwork: Bitmap?,
    artworkCache: Map<String, Bitmap?>,
    onRescan: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayNext: (Track) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onSetTrackInPlaylist: (Long, String, Boolean) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onToggleArtistPinned: (String, Boolean) -> Unit,
    onToggleAlbumPinned: (String, Boolean) -> Unit,
    onTogglePlaylistPinned: (Long, Boolean) -> Unit,
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

    // 主页四个 tab 的分页状态。提到最外层，让 tab 底片、统计栏和右下角按钮
    // 都读同一个来源，滑动 / 点按不会再出现「指示器停在这页、数量显示那页」。
    val tabPagerState = rememberPagerState(
        initialPage = selectedTab.coerceIn(0, LIBRARY_TAB_COUNT - 1),
        pageCount = { LIBRARY_TAB_COUNT },
    )
    val activeTab = tabPagerState.currentPage.coerceIn(0, LIBRARY_TAB_COUNT - 1)
    val tabScope = rememberCoroutineScope()

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
    // 进 / 出详情同样先在 loading 底下把新页面组合、排版好，再整体显示。
    var groupLoading by remember { mutableStateOf(false) }
    val groupSignature = activeGroup?.let { it.kind.name to it.title }
    var lastGroupSignature by remember { mutableStateOf(groupSignature) }
    LaunchedEffect(groupSignature) {
        if (lastGroupSignature != groupSignature) {
            lastGroupSignature = groupSignature
            // 只有「进详情」才走 loading（把详情页的组合 / 排版藏在它后面）；
            // 「返回列表」直接切回去，返回要跟手，多等 240ms 反而更卡。
            if (groupSignature != null) {
                groupLoading = true
                delay(GROUP_LOADING_MS)
                groupLoading = false
            }
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
        bottomBar = {
            AnimatedVisibility(
                visible = currentTrack != null,
                enter = slideInVertically(tween(PAGE_SLIDE_MS)) { it } +
                    fadeIn(tween(PAGE_FADE_MS)),
                exit = slideOutVertically(tween(PAGE_SLIDE_MS)) { it } +
                    fadeOut(tween(PAGE_FADE_MS)),
            ) {
                if (currentTrack != null) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = playerState.showPauseIcon,
                        positionMs = playerState.positionMs,
                        durationMs = playerState.durationMs,
                        artwork = artwork,
                        onOpen = onOpenPlayer,
                        onPrevious = onPrevious,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                    )
                }
            }
        },
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
            AnimatedContent(
                targetState = activeGroup,
                transitionSpec = {
                    if (targetState == null) {
                        // 返回列表：直接切，不播动画。列表内容重，滑动等于连续多帧重绘，
                        // 反而显得返回更卡；直接换页最跟手。
                        EnterTransition.None togetherWith ExitTransition.None
                    } else if (targetState?.openedFromPlayer == true && initialState == null) {
                        // 从播放页点进来：这一帧直接换页。动画本来也发生在播放页底下，
                        // 保留动画只会让下面先露出列表再切过去，看着像闪了一下。
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        // 进入详情页：从右侧推入。不做淡入淡出——两张页面都是不透明的，
                        // 一起位移正好铺满屏幕；一旦叠加淡出，中间帧就会透出底下的别的内容。
                        slideInHorizontally(tween(DETAIL_SLIDE_MS)) { it / 3 } togetherWith
                            slideOutHorizontally(tween(DETAIL_SLIDE_MS)) { -it / 6 }
                    }
                },
                label = "library-page",
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
            ) { group ->
                if (group != null) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val albumArtists = if (group.kind == LibraryGroupKind.ALBUM) {
                            selectedTracks
                                .map { it.artist.ifBlank { unknownArtist } }
                                .distinct()
                        } else {
                            emptyList()
                        }
                        val singleAlbumArtist = albumArtists.singleOrNull()
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
                                    singleAlbumArtist

                                group.kind == LibraryGroupKind.ALBUM ->
                                    stringResource(R.string.various_artists)

                                else -> stringResource(R.string.track_count, selectedTracks.size)
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
                        onBack = closeGroup,
                        // 播放按钮：用这个详情页里的歌曲整体替换播放列表。
                        onPlayAll = {
                            selectedTracks.firstOrNull()?.let { track ->
                                selectedGroup = selectedGroup?.copy(openedFromPlayer = false)
                                onPlayTrack(track, selectedTracks)
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
                            trackCount = tracks.size,
                            folderCount = folderCount,
                            onRescan = onRescan,
                            onOpenSettings = onOpenSettings,
                            onOpenStatistics = onOpenStatistics,
                        )
                        if (selectedGroup == null) {
                            LibraryTabs(
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
                        LibraryLoading(
                            title = stringResource(R.string.scanning),
                            hint = stringResource(R.string.scanning_hint),
                        )
                    }
                } else if (scanState is ScanUiState.Loading) {
                    // 普通启动读缓存时不显示 loading，只留空背景；列表数据到位后渐变推出。
                    Column(modifier = Modifier.fillMaxSize()) {
                        LibraryHeader(
                            trackCount = tracks.size,
                            folderCount = folderCount,
                            onRescan = onRescan,
                            onOpenSettings = onOpenSettings,
                            onOpenStatistics = onOpenStatistics,
                        )
                        if (selectedGroup == null) {
                            LibraryTabs(
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
                        Box(modifier = Modifier.fillMaxWidth().weight(1f))
                    }
                } else if (scanState is ScanUiState.Refreshing) {
                    // 启动页后扫描：歌曲列表区域保留 loading，扫描完成后逐条推出。
                    Column(modifier = Modifier.fillMaxSize()) {
                        LibraryHeader(
                            trackCount = tracks.size,
                            folderCount = folderCount,
                            onRescan = onRescan,
                            onOpenSettings = onOpenSettings,
                            onOpenStatistics = onOpenStatistics,
                        )
                        if (selectedGroup == null) {
                            LibraryTabs(
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
                        LibraryLoading(
                            title = stringResource(R.string.library_loading),
                            hint = null,
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LibraryHeader(
                            trackCount = tracks.size,
                            folderCount = folderCount,
                            onRescan = onRescan,
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
                    if (selectedGroup == null) {
                        LibraryTabs(
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
                            onOpenSearch = {
                                searchQuery = ""
                                searchOpen = true
                            },
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
                                ArtistListContent(
                                    groups = sortedArtistGroups,
                                    emptyTitle = stringResource(R.string.empty_library_title),
                                    artworkCache = artworkCache,
                                    onRequestArtwork = onRequestArtwork,
                                    onGroupClick = openGroup,
                                    pinnedTitles = pinnedArtists,
                                    showAlphabetRail = artistSortMode.showsAlphabetRail,
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }

                        page == 2 -> {
                            // 懒加载：第一次真正显示专辑页时才做分组。
                            LaunchedEffect(Unit) { albumGroupsEnabled = true }
                            if (albumGroupsEnabled) {
                                AlbumGridContent(
                                    groups = sortedAlbumGroups,
                                    emptyTitle = stringResource(R.string.empty_library_title),
                                    artworkCache = artworkCache,
                                    onRequestArtwork = onRequestArtwork,
                                    onGroupClick = openGroup,
                                    pinnedTitles = pinnedAlbums,
                                    showAlphabetRail = albumSortMode.showsAlphabetRail,
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize())
                            }
                        }

                        else -> PlaylistGridContent(
                            playlists = sortedPlaylists,
                            tracksById = tracksById,
                            artworkCache = artworkCache,
                            onRequestArtwork = onRequestArtwork,
                            playlistCovers = playlistCovers,
                            onRequestPlaylistCover = onRequestPlaylistCover,
                            onGroupClick = openGroup,
                            pinnedIds = pinnedPlaylistIds,
                            onCreatePlaylist = {
                                playlistSeedTrack = null
                                playlistName = ""
                                showCreatePlaylistDialog = true
                            },
                        )
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
                    }
                }
            }
            // 列表 ↔ 详情切换时，新页面在 loading 底下先组合、排版好再整体显示，
            // 进详情和返回列表那一帧的停顿就不会被看到。
            if (groupLoading) {
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
    onOpenSettings: () -> Unit,
    onOpenStatistics: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    onBack: () -> Unit,
    onPlayAll: (() -> Unit)? = null,
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
                CoverArt(bitmap = coverBitmap, modifier = Modifier.size(DETAIL_COVER_SIZE))
            } else if (coverTrack != null) {
                TrackCoverThumbnail(
                    track = coverTrack,
                    artworkCache = artworkCache,
                    onRequestArtwork = onRequestArtwork,
                    modifier = Modifier.size(DETAIL_COVER_SIZE),
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
                IconButton(
                    onClick = onPlayAll,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
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
private fun TrackCoverThumbnail(
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

/**
 * 网格卡片从底部滑入时由小变大：刚露头时最小，完整进入视口后恢复原大小。
 * 用画布缩放而不是 graphicsLayer，不额外创建图层，低端设备上开销小得多。
 */
private fun Modifier.gridScrollScale(
    gridState: LazyGridState,
    key: Any,
): Modifier = drawWithContent {
    // 按 key 找自己，而不是按下标：排序后下标会变，按 key 就不会因为下标变化而重组。
    val itemInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    val scaleValue = listEntryScale(
        viewportEnd = gridState.layoutInfo.viewportEndOffset,
        itemOffset = itemInfo?.offset?.y,
        itemSize = itemInfo?.size?.height ?: 0,
    )
    if (scaleValue < 1f) {
        scale(scaleValue, scaleValue) { this@drawWithContent.drawContent() }
    } else {
        this@drawWithContent.drawContent()
    }
}

/**
 * 列表行同样从底部滑入时由小变大；缩放原点放在左侧中点，保证封面与文字左对齐。
 */
private fun Modifier.listScrollScale(
    listState: LazyListState,
    key: Any,
): Modifier = drawWithContent {
    val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
    val scaleValue = listEntryScale(
        viewportEnd = listState.layoutInfo.viewportEndOffset,
        itemOffset = itemInfo?.offset,
        itemSize = itemInfo?.size ?: 0,
    )
    if (scaleValue < 1f) {
        scale(scaleValue, scaleValue, pivot = Offset(0f, size.height / 2f)) {
            this@drawWithContent.drawContent()
        }
    } else {
        this@drawWithContent.drawContent()
    }
}

/** 0 = 刚从视口底部露头，1 = 已经完整进入；对应 0.78 倍到 1 倍。 */
private fun listEntryScale(
    viewportEnd: Int,
    itemOffset: Int?,
    itemSize: Int,
): Float {
    if (itemOffset == null || itemSize <= 0) return 1f
    val progress = ((viewportEnd - itemOffset).toFloat() / itemSize).coerceIn(0f, 1f)
    return LIST_ITEM_MIN_SCALE + (1f - LIST_ITEM_MIN_SCALE) * progress
}

@Composable
private fun LibraryTabs(
    pagerPosition: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        onOpenAlbum = onOpenAlbum,
        onOpenArtist = onOpenArtist,
    )
}

@Composable
private fun TrackList(
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
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
            LaunchedEffect(track.id) { onRequestArtwork(track) }
            TrackEntranceRow(
                index = index,
                animate = animateEntrance,
            ) {
                TrackRow(
                    track = track,
                    listState = listState,
                    artwork = rememberCoverBitmap(track, artworkCache),
                    isCurrent = track.id == currentTrackId,
                    isPlaying = isPlaying && track.id == currentTrackId,
                    onClick = { onPlayTrack(track) },
                    onPlayNext = onPlayNext,
                    onAddToPlaylist = onAddToPlaylist,
                    onShowProperties = onShowProperties,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 84.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    listState: LazyListState,
    artwork: Bitmap?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onShowProperties: (Track) -> Unit,
    onOpenAlbum: (Track) -> Unit,
    onOpenArtist: (Track) -> Unit,
) {
    var menuExpanded by remember(track.id) { mutableStateOf(false) }

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
            modifier = Modifier.size(48.dp),
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
                    modifier = Modifier.weight(1f, fill = false),
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
                    modifier = Modifier.padding(start = 6.dp),
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
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.play_next)) },
                    onClick = {
                        menuExpanded = false
                        onPlayNext(track)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.add_to_playlist)) },
                    onClick = {
                        menuExpanded = false
                        onAddToPlaylist(track)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.properties)) },
                    onClick = {
                        menuExpanded = false
                        onShowProperties(track)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.view_album)) },
                    onClick = {
                        menuExpanded = false
                        onOpenAlbum(track)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.view_artist)) },
                    onClick = {
                        menuExpanded = false
                        onOpenArtist(track)
                    },
                )
            }
        }
    }
}

@Composable
private fun PinnedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PushPin,
            contentDescription = stringResource(R.string.pin),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TrackEntranceRow(
    index: Int,
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    // 只记住该行第一次组合时的状态。父级很快会把 animate 设为 false，
    // 但首屏已存在的行仍会完整播放；之后滚入的行则直接显示。
    val animateOnFirstComposition = remember { animate }
    val progress = remember {
        Animatable(if (animateOnFirstComposition) 0f else 1f)
    }
    val entranceOffsetPx = with(LocalDensity.current) {
        SONG_ENTRANCE_OFFSET.toPx()
    }
    LaunchedEffect(index, animateOnFirstComposition) {
        if (!animateOnFirstComposition) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(index * TRACK_ENTRANCE_STAGGER_MS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = TRACK_ENTRANCE_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress.value
                translationX = (1f - progress.value) * -entranceOffsetPx
            },
    ) {
        content()
    }
}

@Composable
private fun ArtistListContent(
    groups: List<LibraryGroup>,
    emptyTitle: String,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onGroupClick: (LibraryGroupSelection) -> Unit,
    pinnedTitles: Set<String>,
    showAlphabetRail: Boolean = false,
) {
    if (groups.isEmpty()) {
        EmptyLibrary(
            icon = Icons.Rounded.MusicOff,
            title = emptyTitle,
            message = stringResource(R.string.empty_library_message),
        )
        return
    }

    val listState = rememberLazyListState()
    // 排序变化后回到顶部，否则 key 锚定会让列表看起来没重新排。
    // 排序变化后回到顶部；已经在顶部时跳过，scrollToItem 会强制整列重新测量。
    LaunchedEffect(groups) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }
    val sectionStarts = remember(groups) { groupSectionStartIndexes(groups) }
    val scope = rememberCoroutineScope()
    val activeLetter by remember(groups, listState) {
        derivedStateOf {
            groups.getOrNull(listState.firstVisibleItemIndex)
                ?.let { sectionKeyOf(it.title) }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showAlphabetRail) {
                    Modifier.padding(end = ALPHABET_RAIL_WIDTH)
                } else {
                    Modifier
                },
            ),
    ) {
        items(items = groups, key = { it.title }) { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .listScrollScale(listState, group.title)
                    .heightIn(min = 72.dp)
                    .clickable(role = Role.Button) {
                        onGroupClick(
                            LibraryGroupSelection(
                                title = group.title,
                                kind = group.kind,
                                trackIds = group.trackIds,
                            ),
                        )
                    }
                    .padding(start = 20.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val coverTrack = group.coverTrack
                if (coverTrack != null) {
                    // 和专辑一样取该艺术家第一首歌的封面。
                    TrackCoverThumbnail(
                        track = coverTrack,
                        artworkCache = artworkCache,
                        onRequestArtwork = onRequestArtwork,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = group.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.track_count, group.trackIds.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (group.title in pinnedTitles) {
                    PinnedBadge()
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 84.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
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
    }
}

@Composable
private fun AlbumGridContent(
    groups: List<LibraryGroup>,
    emptyTitle: String,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onGroupClick: (LibraryGroupSelection) -> Unit,
    pinnedTitles: Set<String>,
    showAlphabetRail: Boolean = false,
) {
    if (groups.isEmpty()) {
        EmptyLibrary(
            icon = Icons.Rounded.MusicOff,
            title = emptyTitle,
            message = stringResource(R.string.empty_library_message),
        )
        return
    }

    val gridState = rememberLazyGridState()
    // 排序变化后列表会重新排布；Lazy 列表按 key 锚定滚动位置，
    // 不主动回到顶部的话画面会看起来"没变化"。
    LaunchedEffect(groups) {
        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.scrollToItem(0)
        }
    }
    val sectionStarts = remember(groups) { groupSectionStartIndexes(groups) }
    val scope = rememberCoroutineScope()
    val activeLetter by remember(groups, gridState) {
        derivedStateOf {
            groups.getOrNull(gridState.firstVisibleItemIndex)
                ?.let { sectionKeyOf(it.title) }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMN_COUNT),
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (showAlphabetRail) {
                    Modifier.padding(end = ALPHABET_RAIL_WIDTH)
                } else {
                    Modifier
                },
            ),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(items = groups, key = { it.title }) { group ->
            Column(
                modifier = Modifier
                    .gridScrollScale(gridState, group.title)
                    .clickable(role = Role.Button) {
                        onGroupClick(
                            LibraryGroupSelection(
                                title = group.title,
                                kind = group.kind,
                                trackIds = group.trackIds,
                            ),
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                ) {
                    val coverTrack = group.coverTrack
                    if (coverTrack != null) {
                        TrackCoverThumbnail(
                            track = coverTrack,
                            artworkCache = artworkCache,
                            onRequestArtwork = onRequestArtwork,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (group.title in pinnedTitles) {
                        PinnedBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.track_count, group.trackIds.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
        if (showAlphabetRail) {
            AlphabetRail(
                available = sectionStarts.keys,
                activeLetter = activeLetter,
                onLetterSelected = { letter ->
                    sectionStarts[letter]?.let { index ->
                        scope.launch { gridState.scrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
            )
        }
    }
}

@Composable
private fun PlaylistGridContent(
    playlists: List<UserPlaylist>,
    tracksById: Map<String, Track>,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    playlistCovers: Map<Long, Bitmap?>,
    onRequestPlaylistCover: (UserPlaylist) -> Unit,
    onGroupClick: (LibraryGroupSelection) -> Unit,
    pinnedIds: Set<Long>,
    onCreatePlaylist: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    // 排序变化后回到顶部，否则按 key 锚定会让顺序看起来没变。
    LaunchedEffect(playlists) {
        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.scrollToItem(0)
        }
    }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMN_COUNT),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.no_playlists),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        } else {
            gridItems(items = playlists, key = { it.id }) { playlist ->
                val existingTrackIds = playlist.trackIds.filter { tracksById.containsKey(it) }
                val firstTrack = existingTrackIds.firstOrNull()?.let(tracksById::get)

                LaunchedEffect(playlist.id, playlist.coverUri) {
                    if (playlist.coverUri != null) {
                        onRequestPlaylistCover(playlist)
                    }
                }

                Column(
                    modifier = Modifier
                        .gridScrollScale(gridState, playlist.id)
                        .clickable(role = Role.Button) {
                            onGroupClick(
                                LibraryGroupSelection(
                                    title = playlist.name,
                                    kind = LibraryGroupKind.PLAYLIST,
                                    trackIds = existingTrackIds,
                                    playlistId = playlist.id,
                                ),
                            )
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                    ) {
                        PlaylistCoverArt(
                            customCover = playlistCovers[playlist.id],
                            firstTrack = firstTrack,
                            artworkCache = artworkCache,
                            onRequestArtwork = onRequestArtwork,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (playlist.id in pinnedIds) {
                            PinnedBadge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.track_count, existingTrackIds.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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
private fun AlphabetRail(
    available: Set<String>,
    activeLetter: String?,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = remember { SECTION_LETTERS }
    val letterHeightPx = with(LocalDensity.current) { ALPHABET_LETTER_HEIGHT.toPx() }

    // 每个字母占固定高度，触摸位置可以直接换算成索引，拖到哪就跳到哪。
    fun letterAt(offsetY: Float): String =
        letters[(offsetY / letterHeightPx).toInt().coerceIn(0, letters.lastIndex)]

    Column(
        modifier = modifier
            .width(ALPHABET_RAIL_WIDTH)
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onLetterSelected(letterAt(offset.y))
                    },
                ) { change, _ ->
                    change.consume()
                    onLetterSelected(letterAt(change.position.y))
                }
            }
            .pointerInput(letters) {
                detectTapGestures { offset ->
                    onLetterSelected(letterAt(offset.y))
                }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val hasTracks = letter in available
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ALPHABET_LETTER_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        letter == activeLetter -> MaterialTheme.colorScheme.primary
                        hasTracks -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    },
                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
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
private fun EmptyLibrary(
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
    val icon: ImageVector,
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

private val SECTION_LETTERS: List<String> = ('A'..'Z').map(Char::toString) + "#"
private val ALPHABET_RAIL_WIDTH = 24.dp
private val ALPHABET_LETTER_HEIGHT = 17.dp
private const val GRID_COLUMN_COUNT = 3
/** 详情页头部的封面尺寸。 */
private val DETAIL_COVER_SIZE = 96.dp
/** 点 tab 切换页面的补间时长：短一点，低端设备上整页重绘不容易看出卡顿。 */
private const val TAB_SWITCH_MS = 120
/** tab 按钮高度（选中底片与文字框共用，保证指示器高度一致）。 */
private val LIBRARY_TAB_HEIGHT = 40.dp
/** 等播放页浮层完全盖住主界面之后再收起「从播放页进来的详情页」。 */
private const val PLAYER_COVER_SETTLE_MS = 260L
private const val PAGE_SLIDE_MS = 280
private const val PAGE_FADE_MS = 200
private const val TRACK_ENTRANCE_MS = 420
private const val TRACK_ENTRANCE_STAGGER_MS = 45L
private val SONG_ENTRANCE_OFFSET = 36.dp
/** 列表 ↔ 详情页的推入 / 退回时长：短一点，两张重页面同时绘制的帧数更少。 */
private const val DETAIL_SLIDE_MS = 200
/** 改排序时 loading 停留的时长：够列表在它下面把新顺序组合、排版完就行。 */
private const val SORT_LOADING_MS = 260L
/** 进 / 出详情时 loading 停留的时长：够详情页组合、排版完再亮出来。 */
private const val GROUP_LOADING_MS = 240L
/** 条目刚从底部滑入时的缩放值，越接近 1 动效越轻微。 */
private const val LIST_ITEM_MIN_SCALE = 0.78f
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
private fun sectionKeyOf(title: String): String {
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
                        if (results.songs.isNotEmpty()) {
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

                        if (results.artists.isNotEmpty()) {
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

                        if (results.albums.isNotEmpty()) {
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

                        if (results.playlists.isNotEmpty()) {
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
                                val trackCount = playlist.trackIds.count(tracksById::containsKey)
                                SearchResultRow(
                                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                    title = playlist.name,
                                    subtitle = stringResource(R.string.track_count, trackCount),
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

/** tab 下面一行：左侧总数，右侧「播放 / 随机播放 / 排序」。 */
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
    onOpenSearch: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
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
        IconButton(
            onClick = onOpenSearch,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search),
                modifier = Modifier.size(22.dp),
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

/** 按首字母（中文按拼音）给分组列表做导航索引。 */
private fun groupSectionStartIndexes(groups: List<LibraryGroup>): Map<String, Int> {
    val starts = linkedMapOf<String, Int>()
    groups.forEachIndexed { index, group ->
        starts.getOrPut(sectionKeyOf(group.title)) { index }
    }
    return starts
}

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
            icon = Icons.Rounded.Person,
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
            icon = Icons.Rounded.Album,
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
