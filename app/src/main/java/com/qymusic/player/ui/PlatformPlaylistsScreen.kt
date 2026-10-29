package com.qymusic.player.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qymusic.player.R
import com.qymusic.player.data.BuiltinMusicSource
import com.qymusic.player.data.LxMusicSong
import com.qymusic.player.data.PlatformPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 平台歌单：顶部 tab 切换 酷狗 / QQ音乐 / 酷我 / 网易云，
 * 每个平台各自拉取登录账号下的歌单，未登录时提示需要登录。
 */
@Composable
fun PlatformPlaylistsScreen(
    viewModel: PlatformPlaylistsViewModel,
    downloadViewModel: MusicDownloadViewModel,
    downloadDirTreeUri: String?,
    currentTrackId: String?,
    onOpenDrawer: () -> Unit,
    onPreviewStart: (LxMusicSong) -> Unit,
    onPlayOnline: (LxMusicSong, String) -> Unit,
    onPreviewFailed: (LxMusicSong) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // 播放 / 下载走音源解析链路，确保音源脚本已加载
        downloadViewModel.ensureSourceLoaded()
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            message = null
        }
    }
    // 进入页面 / 切 tab：已登录且还没加载过就自动拉取
    LaunchedEffect(state.selected) {
        val tab = state.tabs[state.selected]
        if (tab?.account != null && !tab.loaded && !tab.isLoading) {
            viewModel.refresh(state.selected)
        }
    }

    BackHandler(enabled = state.detailPlaylist != null) {
        viewModel.closePlaylist()
    }
    BackHandler(enabled = state.loginOpen) {
        viewModel.cancelLogin()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.navigation_menu),
                    )
                }
                Text(
                    text = stringResource(R.string.platform_playlists_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            val selectedIndex = viewModel.tabOrder.indexOf(state.selected).coerceAtLeast(0)
            TabRow(selectedTabIndex = selectedIndex) {
                viewModel.tabOrder.forEach { platform ->
                    Tab(
                        selected = state.selected == platform,
                        onClick = { viewModel.selectTab(platform) },
                        text = {
                            Text(
                                text = platform.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                val tabState = state.tabs[state.selected] ?: PlatformTabUiState()
                when {
                    tabState.account == null -> PlatformLoggedOutContent(
                        platform = state.selected,
                        onLogin = { viewModel.startLogin(state.selected) },
                    )

                    tabState.isLoading && tabState.playlists.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    else -> PlatformPlaylistGrid(
                        platform = state.selected,
                        tabState = tabState,
                        onRefresh = { viewModel.refresh(state.selected) },
                        onLogout = { viewModel.logout(state.selected) },
                        onOpenPlaylist = { viewModel.openPlaylist(it) },
                        onLogin = { viewModel.startLogin(state.selected) },
                    )
                }

                // 歌单详情浮层：盖住 tab 内容
                state.detailPlaylist?.let { playlist ->
                    PlatformPlaylistDetail(
                        playlist = playlist,
                        tracks = state.detailTracks,
                        isLoading = state.detailLoading,
                        error = state.detailError,
                        currentTrackId = currentTrackId,
                        onBack = { viewModel.closePlaylist() },
                        onPlayTrack = { song ->
                            downloadViewModel.previewSong(
                                song = song,
                                onPreviewStart = onPreviewStart,
                                onResolved = onPlayOnline,
                                onResolveFailed = { failed ->
                                    onPreviewFailed(failed)
                                    message = "暂时无法播放这首歌，请检查音源或网络"
                                },
                            )
                        },
                        onDownloadTrack = { song ->
                            downloadViewModel.download(song, downloadDirTreeUri)
                            message = "开始下载 ${song.title}"
                        },
                    )
                }
            }
        }
    }

    // 登录浮层：酷狗扫码 / 其他平台 WebView
    state.qrLogin?.let { qrState ->
        KugouQrLoginSheet(
            qrState = qrState,
            onRefresh = { viewModel.startLogin(BuiltinMusicSource.KUGOU) },
            onDismiss = { viewModel.cancelLogin() },
            onSendSmsCode = { mobile, onResult ->
                viewModel.sendKugouSmsCode(mobile, onResult)
            },
            onSmsLogin = { mobile, code, onError ->
                viewModel.loginKugouBySms(mobile, code, onError)
            },
        )
    }
    state.webLoginPlatform?.let { platform ->
        PlatformWebLoginSheet(
            platform = platform,
            onComplete = { userId, nickname, avatar, cookies ->
                viewModel.completeWebLogin(platform, userId, nickname, avatar, cookies)
            },
            onDismiss = { viewModel.cancelLogin() },
        )
    }
    if (state.neteaseLoginOpen) {
        NeteaseLoginSheet(
            qrState = state.neteaseQr,
            onModeChange = { qrMode -> viewModel.setNeteaseLoginMode(qrMode) },
            onRefreshQr = { viewModel.refreshNeteaseQr() },
            onSendSmsCode = { mobile, onResult ->
                viewModel.sendNeteaseCaptcha(mobile, onResult)
            },
            onSmsLogin = { mobile, code, onError ->
                viewModel.loginNeteaseBySms(mobile, code, onError)
            },
            onWebLogin = { viewModel.startNeteaseWebLogin() },
            onDismiss = { viewModel.cancelLogin() },
        )
    }
}

@Composable
private fun PlatformLoggedOutContent(
    platform: BuiltinMusicSource,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.platform_playlists_need_login),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(
                R.string.platform_playlists_login_hint,
                platform.displayName,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onLogin) {
            Text(text = stringResource(R.string.platform_playlists_login))
        }
    }
}

@Composable
private fun PlatformPlaylistGrid(
    platform: BuiltinMusicSource,
    tabState: PlatformTabUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenPlaylist: (PlatformPlaylist) -> Unit,
    onLogin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 账号行：昵称 + 退出登录 + 刷新
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.platform_playlists_account_label,
                    tabState.account?.nickname.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = stringResource(R.string.platform_playlists_logout),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tabState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(22.dp)
                        .padding(end = 15.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.platform_playlists_refresh),
                    )
                }
            }
        }

        tabState.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onLogin) {
                    Text(text = stringResource(R.string.platform_playlists_relogin))
                }
            }
        }

        if (tabState.playlists.isEmpty() && !tabState.isLoading && tabState.error == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.platform_playlists_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tabState.playlists, key = { it.platform.key + "_" + it.id }) { playlist ->
                PlatformPlaylistCell(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun PlatformPlaylistCell(
    playlist: PlatformPlaylist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            RemoteCover(
                url = playlist.coverUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (playlist.trackCount > 0) {
            Text(
                text = stringResource(
                    R.string.platform_playlists_track_count,
                    playlist.trackCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 歌单详情：全屏浮层列出歌单内歌曲，点击播放（走音源试听链路）。 */
@Composable
private fun PlatformPlaylistDetail(
    playlist: PlatformPlaylist,
    tracks: List<LxMusicSong>,
    isLoading: Boolean,
    error: String?,
    currentTrackId: String?,
    onBack: () -> Unit,
    onPlayTrack: (LxMusicSong) -> Unit,
    onDownloadTrack: (LxMusicSong) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (playlist.trackCount > 0) {
                        Text(
                            text = stringResource(
                                R.string.platform_playlists_track_count,
                                playlist.trackCount,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    error != null -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    tracks.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.platform_playlists_tracks_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(tracks) { index, song ->
                            val playing = currentTrackId ==
                                "online:${song.sourceKey}:${song.id}"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayTrack(song) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (playing) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.width(28.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (playing) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        },
                                        color = if (playing) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = song.artist +
                                            if (song.album.isNotBlank()) " · ${song.album}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { onDownloadTrack(song) }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = stringResource(R.string.download_entry),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** 酷狗登录弹窗：扫码 / 手机号验证码两种方式。 */
@Composable
private fun KugouQrLoginSheet(
    qrState: KugouQrUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSendSmsCode: (mobile: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSmsLogin: (mobile: String, code: String, onError: (String) -> Unit) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("qr") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.platform_playlists_kugou_qr_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 方式切换
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    TextButton(onClick = { mode = "qr" }) {
                        Text(
                            text = stringResource(R.string.platform_playlists_mode_qr),
                            color = if (mode == "qr") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = { mode = "sms" }) {
                        Text(
                            text = stringResource(R.string.platform_playlists_mode_sms),
                            color = if (mode == "sms") {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (mode == "qr") {
                    KugouQrContent(
                        qrState = qrState,
                        onRefresh = onRefresh,
                    )
                } else {
                    PhoneSmsLoginContent(
                        onSendSmsCode = onSendSmsCode,
                        onSmsLogin = onSmsLogin,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun KugouQrContent(
    qrState: KugouQrUiState,
    onRefresh: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = remember(qrState.qrImageBase64) {
            decodeBase64Qr(qrState.qrImageBase64)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.platform_playlists_kugou_qr_title),
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (qrState.error == null) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val statusText = when {
            qrState.error != null -> qrState.error
            qrState.status == 0 -> stringResource(R.string.platform_playlists_qr_expired)
            qrState.status == 2 -> stringResource(R.string.platform_playlists_qr_scanned)
            else -> stringResource(R.string.platform_playlists_qr_wait)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.platform_playlists_qr_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh) {
            Text(text = stringResource(R.string.platform_playlists_qr_refresh))
        }
    }
}

/** 网易云登录弹窗：扫码（默认，短信接口被风控）/ 手机号验证码两种方式。 */
@Composable
private fun NeteaseLoginSheet(
    qrState: NeteaseQrUiState?,
    onModeChange: (qrMode: Boolean) -> Unit,
    onRefreshQr: () -> Unit,
    onSendSmsCode: (mobile: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSmsLogin: (mobile: String, code: String, onError: (String) -> Unit) -> Unit,
    onWebLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 模式以 VM 的 neteaseQr 是否为 null 为唯一事实源，避免弹窗重开后 UI 与轮询状态不一致
    val qrMode = qrState != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.platform_playlists_netease_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    TextButton(onClick = { if (!qrMode) onModeChange(true) }) {
                        Text(
                            text = stringResource(R.string.platform_playlists_mode_qr),
                            color = if (qrMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = { if (qrMode) onModeChange(false) }) {
                        Text(
                            text = stringResource(R.string.platform_playlists_mode_sms),
                            color = if (!qrMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (qrMode) {
                    NeteaseQrContent(
                        qrState = qrState ?: NeteaseQrUiState(),
                        onRefresh = onRefreshQr,
                    )
                } else {
                    PhoneSmsLoginContent(
                        onSendSmsCode = onSendSmsCode,
                        onSmsLogin = onSmsLogin,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onWebLogin) {
                Text(text = stringResource(R.string.platform_playlists_netease_web_fallback))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun NeteaseQrContent(
    qrState: NeteaseQrUiState,
    onRefresh: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val bitmap = remember(qrState.qrContent) {
            qrState.qrContent.takeIf(String::isNotBlank)?.let(::renderQrBitmap)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.platform_playlists_netease_title),
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (qrState.error == null) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val statusText = when {
            qrState.error != null -> qrState.error
            qrState.status == 0 -> stringResource(R.string.platform_playlists_qr_expired)
            qrState.status == 2 -> stringResource(R.string.platform_playlists_qr_scanned)
            else -> stringResource(R.string.platform_playlists_qr_wait)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.platform_playlists_netease_qr_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh) {
            Text(text = stringResource(R.string.platform_playlists_qr_refresh))
        }
    }
}

/** 用 zxing 本地渲染二维码（网易云扫码协议不提供服务端图片）；黑码白底保证对比度。 */
private fun renderQrBitmap(content: String, size: Int = 640): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { index ->
        if (matrix.get(index % size, index / size)) android.graphics.Color.BLACK
        else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}.getOrNull()

@Composable
private fun PhoneSmsLoginContent(
    onSendSmsCode: (mobile: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSmsLogin: (mobile: String, code: String, onError: (String) -> Unit) -> Unit,
) {
    var mobile by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var loggingIn by remember { mutableStateOf(false) }

    LaunchedEffect(countdown) {
        while (countdown > 0) {
            delay(1_000)
            countdown -= 1
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = mobile,
            onValueChange = { value -> mobile = value.filter(Char::isDigit).take(11) },
            label = { Text(text = stringResource(R.string.platform_playlists_sms_phone)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            enabled = !loggingIn,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
                label = { Text(text = stringResource(R.string.platform_playlists_sms_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !loggingIn,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = {
                    if (mobile.length == 11 && !sending && countdown == 0) {
                        sending = true
                        message = null
                        onSendSmsCode(mobile) { ok, error ->
                            sending = false
                            if (ok) {
                                countdown = 60
                                message = null
                            } else {
                                message = error
                            }
                        }
                    }
                },
                enabled = mobile.length == 11 && !sending && countdown == 0,
            ) {
                Text(
                    text = when {
                        sending -> stringResource(R.string.platform_playlists_sms_sending)
                        countdown > 0 -> stringResource(
                            R.string.platform_playlists_sms_resend_in,
                            countdown,
                        )
                        else -> stringResource(R.string.platform_playlists_sms_send)
                    },
                )
            }
        }
        message?.let { text ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                if (mobile.length == 11 && code.length >= 4 && !loggingIn) {
                    loggingIn = true
                    message = null
                    onSmsLogin(mobile, code) { error ->
                        loggingIn = false
                        message = error
                    }
                }
            },
            enabled = mobile.length == 11 && code.length >= 4 && !loggingIn,
        ) {
            if (loggingIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.platform_playlists_login))
        }
    }
}

/** WebView 登录：加载平台登录页，轮询 cookie；也可手动点"我已登录完成"收凭据。 */
@Composable
private fun PlatformWebLoginSheet(
    platform: BuiltinMusicSource,
    onComplete: (userId: String, nickname: String, avatarUrl: String?, cookies: Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun completeFromCookies(): Boolean {
        val cookies = readCookies(platform) ?: return false
        if (platform == BuiltinMusicSource.NETEASE) {
            // 网易云：MUSIC_U 就位后从页面注入的 GUser 读取 userId/昵称
            if (!cookies.containsKey("MUSIC_U")) return false
            val info = readNeteaseUser(webView) ?: return false
            val userId = info.optString("id")
            if (userId.isBlank() || userId == "0" || userId == "null") return false
            finishLogin(
                PlatformWebLoginCreds(
                    userId = userId,
                    nickname = info.optString("n"),
                    avatarUrl = info.optString("a").takeIf(String::isNotBlank),
                    cookies = cookies,
                ),
                onComplete,
            )
            return true
        }
        val creds = detectWebLoginCreds(platform, cookies) ?: return false
        finishLogin(creds, onComplete)
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.cancel),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        R.string.platform_playlists_web_login_title,
                        platform.displayName,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.platform_playlists_web_login_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        hint?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = false
                    }
                    webView = this
                    loadUrl(loginUrl(platform))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        // 手动完成：自动检测偶尔漏掉（cookie 域名不同 / 写入延迟）时的兜底入口。
        // 酷我会先回到 www 域让页面写入 uid/kw_token cookie 再读取。
        Button(
            onClick = {
                if (checking) return@Button
                checking = true
                hint = null
                scope.launch {
                    if (platform == BuiltinMusicSource.KUWO) {
                        webView?.loadUrl("https://www.kuwo.cn/")
                        delay(WEB_LOGIN_DONE_WAIT_MS)
                    } else if (platform == BuiltinMusicSource.NETEASE) {
                        webView?.loadUrl("https://music.163.com/")
                        delay(WEB_LOGIN_DONE_WAIT_MS)
                    }
                    if (!completeFromCookies()) {
                        hint = if (platform == BuiltinMusicSource.KUWO) {
                            "未检测到登录信息，请确认已在页面登录成功；若刚登录请等几秒再点一次"
                        } else {
                            "未检测到登录信息，请确认已在页面登录成功后再点一次"
                        }
                    }
                    checking = false
                }
            },
            enabled = !checking,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = stringResource(R.string.platform_playlists_web_login_done))
        }
    }

    LaunchedEffect(platform) {
        var neteaseHomeLoaded = false
        var attempts = 0
        while (isActive && attempts < WEB_LOGIN_MAX_ATTEMPTS) {
            delay(WEB_LOGIN_POLL_MS)
            attempts += 1
            if (platform == BuiltinMusicSource.NETEASE) {
                val cookies = readCookies(platform)
                if (cookies?.containsKey("MUSIC_U") == true && !neteaseHomeLoaded) {
                    // MUSIC_U 已就位：回首页读取服务端注入的 GUser（含 userId / 昵称）
                    neteaseHomeLoaded = true
                    webView?.loadUrl("https://music.163.com/")
                }
            }
            if (completeFromCookies()) return@LaunchedEffect
        }
    }
}

/** 从页面 cookie 里识别登录凭据；未登录 / 不完整时返回 null。 */
private fun detectWebLoginCreds(
    platform: BuiltinMusicSource,
    cookies: Map<String, String>,
): PlatformWebLoginCreds? = when (platform) {
    BuiltinMusicSource.QQ -> {
        val keyst = cookies["qm_keyst"] ?: cookies["qqmusic_key"]
        val uin = cookies["uin"]?.trim()?.removePrefix("o")
            ?.takeIf(String::isNotBlank)
        if (keyst != null && uin != null) {
            PlatformWebLoginCreds(uin, "", null, cookies)
        } else {
            null
        }
    }

    BuiltinMusicSource.KUWO -> {
        val token = cookies["kw_token"]
        val uid = cookies["uid"]?.trim()?.filter(Char::isDigit)
            ?.takeIf(String::isNotBlank)
        if (token != null && uid != null) {
            PlatformWebLoginCreds(uid, "", null, cookies)
        } else {
            null
        }
    }

    else -> null
}

private data class PlatformWebLoginCreds(
    val userId: String,
    val nickname: String,
    val avatarUrl: String?,
    val cookies: Map<String, String>,
)

/**
 * 平台相关多个域的 cookie 快照（合并）。
 * QQ 的登录凭据散在 *.qq.com 多个子域，酷我可能在 www / m / passport 任一域写入。
 */
private fun readCookies(platform: BuiltinMusicSource): Map<String, String>? {
    val domains = when (platform) {
        BuiltinMusicSource.QQ -> listOf(
            "https://y.qq.com/",
            "https://u.y.qq.com/",
            "https://c.y.qq.com/",
            "https://graph.qq.com/",
            "https://ssl.ptlogin2.qq.com/",
        )
        BuiltinMusicSource.KUWO -> listOf(
            "https://www.kuwo.cn/",
            "https://kuwo.cn/",
            "https://m.kuwo.cn/",
            "https://passport.kuwo.cn/",
        )
        BuiltinMusicSource.NETEASE -> listOf("https://music.163.com/")
        BuiltinMusicSource.KUGOU -> return null
    }
    val manager = CookieManager.getInstance()
    val merged = mutableMapOf<String, String>()
    for (domain in domains) {
        val header = runCatching { manager.getCookie(domain) }.getOrNull() ?: continue
        header.split(";")
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                part.substring(0, index).trim() to part.substring(index + 1).trim()
            }
            .forEach { (name, value) -> merged.putIfAbsent(name, value) }
    }
    return merged.ifEmpty { null }
}

private fun loginUrl(platform: BuiltinMusicSource): String = when (platform) {
    BuiltinMusicSource.QQ -> "https://y.qq.com/portal/profile.html"
    BuiltinMusicSource.KUWO -> "https://www.kuwo.cn/"
    BuiltinMusicSource.NETEASE -> "https://music.163.com/m/login"
    BuiltinMusicSource.KUGOU -> "https://www.kugou.com/"
}

private suspend fun readNeteaseUser(webView: WebView?): JSONObject? {
    val view = webView ?: return null
    val result = suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(
            "(window.GUser && window.GUser.userId) ? JSON.stringify(" +
                "{id:window.GUser.userId,n:window.GUser.nickname||'',a:window.GUser.avatarUrl||''}" +
                ") : '{}'",
        ) { value ->
            continuation.resumeWith(Result.success(value))
        }
    }
    return runCatching {
        val unwrapped = JSONTokener(result).nextValue()
        val text = when (unwrapped) {
            is String -> unwrapped
            else -> result
        }
        JSONObject(text)
    }.getOrNull()
}

private fun finishLogin(
    creds: PlatformWebLoginCreds,
    onComplete: (String, String, String?, Map<String, String>) -> Unit,
) {
    runCatching { CookieManager.getInstance().flush() }
    onComplete(creds.userId, creds.nickname, creds.avatarUrl, creds.cookies)
}

private fun decodeBase64Qr(dataUrl: String): Bitmap? = runCatching {
    val base64 = dataUrl.substringAfter("base64,", "").trim()
    if (base64.isBlank()) return@runCatching null
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/** 网络封面：进程级 LRU 内存缓存 + 简单下载，失败占位。 */
private val remoteCoverCache = LruCache<String, Bitmap>(64)

@Composable
private fun RemoteCover(
    url: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(url) { mutableStateOf(url?.let { remoteCoverCache.get(it) }) }
    LaunchedEffect(url) {
        if (url == null || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        loaded?.let {
            remoteCoverCache.put(url, it)
            bitmap = it
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudQueue,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

private const val WEB_LOGIN_POLL_MS = 1_000L
private const val WEB_LOGIN_MAX_ATTEMPTS = 120
private const val WEB_LOGIN_DONE_WAIT_MS = 4_000L
