package com.qymusic.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qymusic.player.data.BuiltinMusicSource
import com.qymusic.player.data.LxMusicSong
import com.qymusic.player.data.PlatformAccount
import com.qymusic.player.data.PlatformAccountStore
import com.qymusic.player.data.PlatformPlaylist
import com.qymusic.player.data.PlatformPlaylistClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** 单个平台的页面状态：未登录 / 加载中 / 已加载 / 出错（含账号信息区分重试与登录）。 */
data class PlatformTabUiState(
    val account: PlatformAccount? = null,
    val playlists: List<PlatformPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** 拉到过一次结果后不再自动刷新，交给手动刷新。 */
    val loaded: Boolean = false,
)

data class KugouQrUiState(
    val qrImageBase64: String = "",
    /** -1 未知，0 过期，1 等待扫码，2 已扫码待确认，4 成功。 */
    val status: Int = 1,
    val error: String? = null,
)

data class NeteaseQrUiState(
    /** 本地渲染二维码的内容；空串表示尚未取到。 */
    val qrContent: String = "",
    /** -1 出错，0 过期，1 等待扫码，2 已扫码待确认。 */
    val status: Int = 1,
    val error: String? = null,
)

data class PlatformPlaylistsUiState(
    val tabs: Map<BuiltinMusicSource, PlatformTabUiState> =
        BuiltinMusicSource.entries.associateWith { PlatformTabUiState() },
    /** 当前展示的 tab。 */
    val selected: BuiltinMusicSource = BuiltinMusicSource.KUGOU,
    /** WebView 登录的目标平台；null 表示登录页关闭。 */
    val webLoginPlatform: BuiltinMusicSource? = null,
    /** 网易云手机号登录弹窗是否打开。 */
    val neteaseLoginOpen: Boolean = false,
    /** 网易云扫码登录状态；非 null 表示弹窗当前在扫码模式（也是扫码/验证码模式的唯一事实源）。 */
    val neteaseQr: NeteaseQrUiState? = null,
    /** 酷狗扫码登录状态；null 表示扫码页关闭。 */
    val qrLogin: KugouQrUiState? = null,
    /** 正在打开的歌单（详情加载中或已加载）。 */
    val detailPlaylist: PlatformPlaylist? = null,
    val detailLoading: Boolean = false,
    val detailTracks: List<LxMusicSong> = emptyList(),
    val detailError: String? = null,
) {
    val loginOpen: Boolean
        get() = webLoginPlatform != null || qrLogin != null || neteaseLoginOpen
}

class PlatformPlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val accountStore = PlatformAccountStore(application)
    private val client = PlatformPlaylistClient(accountStore.loadDeviceGuid())

    private val _state = MutableStateFlow(
        PlatformPlaylistsUiState(
            tabs = BuiltinMusicSource.entries.associateWith { source ->
                PlatformTabUiState(account = accountStore.loadAccounts()[source])
            },
        ),
    )
    val state: StateFlow<PlatformPlaylistsUiState> = _state.asStateFlow()

    private val fetchJobs = mutableMapOf<BuiltinMusicSource, Job>()
    private var qrPollJob: Job? = null
    private var neteaseQrPollJob: Job? = null
    private var detailJob: Job? = null

    /** tab 顺序：酷狗、QQ音乐、酷我、网易云。 */
    val tabOrder: List<BuiltinMusicSource> = listOf(
        BuiltinMusicSource.KUGOU,
        BuiltinMusicSource.QQ,
        BuiltinMusicSource.KUWO,
        BuiltinMusicSource.NETEASE,
    )

    fun selectTab(platform: BuiltinMusicSource) {
        _state.update { it.copy(selected = platform) }
        val tab = _state.value.tabs[platform] ?: return
        if (tab.account != null && !tab.loaded && !tab.isLoading) {
            refresh(platform)
        }
    }

    /** 刷新按钮：强制重新拉取该平台歌单。 */
    fun refresh(platform: BuiltinMusicSource) {
        val account = _state.value.tabs[platform]?.account ?: return
        if (fetchJobs[platform]?.isActive == true) return
        _state.update {
            it.copy(
                tabs = it.tabs + (
                    platform to (it.tabs[platform] ?: PlatformTabUiState()).copy(
                        isLoading = true,
                        error = null,
                        account = account,
                    )
                    ),
            )
        }
        fetchJobs[platform] = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { client.fetchPlaylists(account) }
            }
            _state.update { current ->
                val tab = current.tabs[platform] ?: PlatformTabUiState()
                result.fold(
                    onSuccess = { playlists ->
                        current.copy(
                            tabs = current.tabs + (
                                platform to tab.copy(
                                    isLoading = false,
                                    playlists = playlists,
                                    error = null,
                                    loaded = true,
                                    // 登录失效的接口可能成功返回空列表，这里不区分
                                    account = account,
                                )
                                ),
                        )
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        current.copy(
                            tabs = current.tabs + (
                                platform to tab.copy(
                                    isLoading = false,
                                    error = error.message ?: "拉取失败",
                                    loaded = true,
                                    account = account,
                                )
                                ),
                        )
                    },
                )
            }
        }
    }

    // ---- 登录 ----

    /** 开始登录：酷狗/网易云走扫码，其余平台打开 WebView 登录页。 */
    fun startLogin(platform: BuiltinMusicSource) {
        when (platform) {
            BuiltinMusicSource.KUGOU -> startQrLogin()
            BuiltinMusicSource.NETEASE -> {
                _state.update {
                    it.copy(neteaseLoginOpen = true, qrLogin = null, webLoginPlatform = null)
                }
                startNeteaseQrPolling()
            }
            else -> {
                _state.update { it.copy(webLoginPlatform = platform, qrLogin = null) }
            }
        }
    }

    fun cancelLogin() {
        qrPollJob?.cancel()
        qrPollJob = null
        neteaseQrPollJob?.cancel()
        neteaseQrPollJob = null
        _state.update {
            it.copy(webLoginPlatform = null, qrLogin = null, neteaseLoginOpen = false, neteaseQr = null)
        }
    }

    /** 网易云弹窗切换扫码/验证码模式；扫码模式重新起会话，验证码模式停掉轮询。 */
    fun setNeteaseLoginMode(qrMode: Boolean) {
        if (qrMode) {
            if (_state.value.neteaseQr == null) startNeteaseQrPolling()
        } else {
            neteaseQrPollJob?.cancel()
            neteaseQrPollJob = null
            _state.update { it.copy(neteaseQr = null) }
        }
    }

    /** 手动刷新网易云二维码（过期后）。 */
    fun refreshNeteaseQr() {
        if (_state.value.neteaseLoginOpen) startNeteaseQrPolling()
    }

    /** 发送酷狗短信验证码；结果通过回调返回（成功 / 错误消息），供登录弹窗展示。 */
    fun sendKugouSmsCode(mobile: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { client.sendKugouSmsCode(mobile) }
            }
            result.fold(
                onSuccess = { onResult(true, null) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    onResult(false, error.message ?: "验证码发送失败")
                },
            )
        }
    }

    /** 酷狗手机号 + 验证码登录；成功直接走登录完成流程，失败回调错误消息。 */
    fun loginKugouBySms(mobile: String, code: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.loginKugouBySms(mobile, code) }
            }.fold(
                onSuccess = { account -> onLoginSuccess(account) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    onError(error.message ?: "登录失败")
                },
            )
        }
    }

    /** 网易云短信被风控时的兜底：改用网页登录（页内可交互过滑块验证）。 */
    fun startNeteaseWebLogin() {
        neteaseQrPollJob?.cancel()
        neteaseQrPollJob = null
        _state.update {
            it.copy(neteaseLoginOpen = false, neteaseQr = null, webLoginPlatform = BuiltinMusicSource.NETEASE)
        }
    }

    /** 网易云扫码登录：取 unikey 生成二维码后轮询，状态写入 [NeteaseQrUiState]。 */
    private fun startNeteaseQrPolling() {
        neteaseQrPollJob?.cancel()
        _state.update {
            it.copy(
                neteaseQr = NeteaseQrUiState(status = 1, qrContent = ""),
                neteaseLoginOpen = true,
                webLoginPlatform = null,
            )
        }
        neteaseQrPollJob = viewModelScope.launch {
            val session = runCatching {
                withContext(Dispatchers.IO) { client.fetchNeteaseQrSession() }
            }.getOrElse { error ->
                _state.update {
                    it.copy(neteaseQr = NeteaseQrUiState(status = -1, error = error.message ?: "二维码获取失败"))
                }
                return@launch
            }
            _state.update {
                it.copy(neteaseQr = NeteaseQrUiState(status = 1, qrContent = session.qrContent))
            }
            // 与酷狗一致：约 3 分钟，每 2 秒轮询一次
            repeat(QR_POLL_MAX_TRIES) {
                if (!isActive) return@launch
                delay(QR_POLL_INTERVAL_MS)
                val poll = runCatching {
                    withContext(Dispatchers.IO) { client.pollNeteaseQr(session) }
                }.getOrElse { error ->
                    _state.update {
                        it.copy(
                            neteaseQr = (it.neteaseQr ?: NeteaseQrUiState())
                                .copy(error = error.message ?: "检查扫码状态失败"),
                        )
                    }
                    return@launch
                }
                if (poll.status == 3) {
                    val account = poll.account ?: return@launch
                    onLoginSuccess(account)
                    return@launch
                }
                _state.update {
                    it.copy(neteaseQr = (it.neteaseQr ?: NeteaseQrUiState()).copy(status = poll.status))
                }
            }
            _state.update {
                it.copy(neteaseQr = (it.neteaseQr ?: NeteaseQrUiState()).copy(status = 0))
            }
        }
    }

    /** 发送网易云短信验证码。 */
    fun sendNeteaseCaptcha(mobile: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.sendNeteaseCaptcha(mobile) }
            }.fold(
                onSuccess = { onResult(true, null) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    onResult(false, error.message ?: "验证码发送失败")
                },
            )
        }
    }

    /** 网易云手机号 + 验证码登录；成功直接走登录完成流程，失败回调错误消息。 */
    fun loginNeteaseBySms(mobile: String, code: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.loginNeteaseBySms(mobile, code) }
            }.fold(
                onSuccess = { account -> onLoginSuccess(account) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    onError(error.message ?: "登录失败")
                },
            )
        }
    }

    private fun startQrLogin() {
        qrPollJob?.cancel()
        _state.update {
            it.copy(
                qrLogin = KugouQrUiState(status = 1, qrImageBase64 = ""),
                webLoginPlatform = null,
            )
        }
        qrPollJob = viewModelScope.launch {
            val key = runCatching {
                withContext(Dispatchers.IO) { client.fetchKugouQr(accountStore.loadDeviceGuid()) }
            }.getOrElse { error ->
                _state.update {
                    it.copy(qrLogin = KugouQrUiState(status = -1, error = error.message ?: "二维码获取失败"))
                }
                return@launch
            }
            _state.update {
                it.copy(qrLogin = KugouQrUiState(status = 1, qrImageBase64 = key.qrImageBase64))
            }
            // 最长约 3 分钟，期间每 2 秒轮询一次扫码状态
            repeat(QR_POLL_MAX_TRIES) {
                if (!isActive) return@launch
                delay(QR_POLL_INTERVAL_MS)
                val poll = runCatching {
                    withContext(Dispatchers.IO) {
                        client.pollKugouQr(key.key, accountStore.loadDeviceGuid())
                    }
                }.getOrElse { error ->
                    _state.update {
                        it.copy(
                            qrLogin = (it.qrLogin ?: KugouQrUiState())
                                .copy(error = error.message ?: "检查扫码状态失败"),
                        )
                    }
                    return@launch
                }
                when (poll.status) {
                    4 -> {
                        val account = poll.account ?: return@launch
                        onLoginSuccess(account)
                        return@launch
                    }
                    0 -> {
                        _state.update {
                            it.copy(qrLogin = (it.qrLogin ?: KugouQrUiState()).copy(status = 0))
                        }
                        return@launch
                    }
                    else -> {
                        _state.update {
                            it.copy(qrLogin = (it.qrLogin ?: KugouQrUiState()).copy(status = poll.status))
                        }
                    }
                }
            }
            _state.update {
                it.copy(qrLogin = (it.qrLogin ?: KugouQrUiState()).copy(status = 0))
            }
        }
    }

    /**
     * WebView 登录完成回调：宿主从登录页 cookie / 页面变量提取好账号信息后调用。
     */
    fun completeWebLogin(
        platform: BuiltinMusicSource,
        userId: String,
        nickname: String,
        avatarUrl: String?,
        cookies: Map<String, String>,
    ) {
        if (userId.isBlank()) return
        onLoginSuccess(
            PlatformAccount(
                platform = platform,
                userId = userId,
                nickname = nickname.ifBlank { platform.displayName },
                avatarUrl = avatarUrl,
                cookies = cookies,
                loggedAtMs = System.currentTimeMillis(),
            ),
        )
        _state.update { it.copy(webLoginPlatform = null) }
    }

    private fun onLoginSuccess(account: PlatformAccount) {
        accountStore.saveAccount(account)
        qrPollJob?.cancel()
        qrPollJob = null
        neteaseQrPollJob?.cancel()
        neteaseQrPollJob = null
        _state.update {
            it.copy(
                webLoginPlatform = null,
                qrLogin = null,
                neteaseLoginOpen = false,
                neteaseQr = null,
                tabs = it.tabs + (
                    account.platform to (it.tabs[account.platform] ?: PlatformTabUiState())
                        .copy(account = account, playlists = emptyList(), loaded = false, error = null)
                    ),
                selected = account.platform,
            )
        }
        refresh(account.platform)
    }

    fun logout(platform: BuiltinMusicSource) {
        accountStore.removeAccount(platform)
        fetchJobs[platform]?.cancel()
        _state.update {
            it.copy(
                tabs = it.tabs + (platform to PlatformTabUiState()),
            )
        }
    }

    // ---- 歌单详情 ----

    fun openPlaylist(playlist: PlatformPlaylist) {
        val platform = playlist.platform
        val account = _state.value.tabs[platform]?.account ?: return
        detailJob?.cancel()
        _state.update {
            it.copy(
                detailPlaylist = playlist,
                detailLoading = true,
                detailTracks = emptyList(),
                detailError = null,
            )
        }
        detailJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { client.fetchPlaylistTracks(account, playlist) }
            }
            _state.update { current ->
                if (current.detailPlaylist?.id != playlist.id) return@update current
                result.fold(
                    onSuccess = { tracks ->
                        current.copy(detailLoading = false, detailTracks = tracks, detailError = null)
                    },
                    onFailure = { error ->
                        if (error is CancellationException) throw error
                        current.copy(
                            detailLoading = false,
                            detailTracks = emptyList(),
                            detailError = error.message ?: "拉取歌曲失败",
                        )
                    },
                )
            }
        }
    }

    fun closePlaylist() {
        detailJob?.cancel()
        detailJob = null
        _state.update {
            it.copy(
                detailPlaylist = null,
                detailLoading = false,
                detailTracks = emptyList(),
                detailError = null,
            )
        }
    }

    override fun onCleared() {
        qrPollJob?.cancel()
        neteaseQrPollJob?.cancel()
        detailJob?.cancel()
        fetchJobs.values.forEach { it.cancel() }
        super.onCleared()
    }

    private companion object {
        const val QR_POLL_INTERVAL_MS = 2_000L
        const val QR_POLL_MAX_TRIES = 90
    }
}
