package com.qymusic.player.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qymusic.player.data.AudioTagWriter
import com.qymusic.player.data.BuiltinMusicSource
import com.qymusic.player.data.DownloadContentException
import com.qymusic.player.data.DownloadEmbedOptions
import com.qymusic.player.data.DownloadHttpException
import com.qymusic.player.data.DownloadNamingFormat
import com.qymusic.player.data.EmbeddedTags
import com.qymusic.player.data.FetchedCover
import com.qymusic.player.data.FetchedLyrics
import com.qymusic.player.data.LxMusicSource
import com.qymusic.player.data.LxMusicSourceRuntime
import com.qymusic.player.data.LxMusicSourceStore
import com.qymusic.player.data.LxMusicSong
import com.qymusic.player.data.LxSearchPage
import com.qymusic.player.data.LxSourceCapability
import com.qymusic.player.data.MetadataFetchService
import com.qymusic.player.data.MetadataFetchRequest
import com.qymusic.player.data.MetadataPlugin
import com.qymusic.player.data.MetadataPluginManager
import com.qymusic.player.data.MetadataPluginParser
import com.qymusic.player.data.MetadataPluginStore
import com.qymusic.player.data.MusicDownloadRepository
import com.qymusic.player.data.MusicSearchOption
import com.qymusic.player.data.MusicSearchProvider
import com.qymusic.player.data.PendingAudioDownload
import com.qymusic.player.data.PluginHttpClient
import com.qymusic.player.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_DOWNLOAD_QUALITY = "320k"

enum class MusicDownloadPhase {
    RESOLVING,
    DOWNLOADING,
    EMBEDDING,
    COMPLETED,
    FAILED,
}

data class MusicDownloadTaskState(
    val taskId: String,
    val song: LxMusicSong,
    val quality: String,
    val phase: MusicDownloadPhase,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val message: String? = null,
    val fileName: String? = null,
    val displayPath: String? = null,
    val embedNote: String? = null,
) {
    val progress: Float?
        get() = totalBytes.takeIf { it > 0L }?.let { total ->
            (downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val isActive: Boolean
        get() = phase == MusicDownloadPhase.RESOLVING ||
            phase == MusicDownloadPhase.DOWNLOADING ||
            phase == MusicDownloadPhase.EMBEDDING
}

/** 元数据插件管理相关的界面状态。 */
data class MetadataPluginsUiState(
    val plugins: List<MetadataPlugin> = emptyList(),
    val disabled: Set<String> = emptySet(),
    val isImporting: Boolean = false,
    val importMessage: String? = null,
    val importError: String? = null,
) {
    fun isEnabled(pluginId: String): Boolean = pluginId !in disabled
}

data class MusicDownloadUiState(
    val sources: List<LxMusicSource> = emptyList(),
    val activeSourceId: String? = null,
    val activeSourceName: String = "",
    val searchSources: List<MusicSearchOption> = emptyList(),
    val selectedSearchSourceKey: String? = null,
    val selectedSearchSourceName: String = "",
    val qualities: List<String> = emptyList(),
    val selectedQuality: String = DEFAULT_DOWNLOAD_QUALITY,
    val query: String = "",
    val results: List<LxMusicSong> = emptyList(),
    val isSourceLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMoreResults: Boolean = false,
    val searchError: String? = null,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val importSuccess: String? = null,
    val downloads: Map<String, MusicDownloadTaskState> = emptyMap(),
    val notice: String? = null,
    val embedOptions: DownloadEmbedOptions = DownloadEmbedOptions(),
    val metadataPlugins: MetadataPluginsUiState = MetadataPluginsUiState(),
    /** 音质 → 文件大小（字节）探测结果；key 同任务 id，-1 表示探测失败。 */
    val qualitySizes: Map<String, Long> = emptyMap(),
    val namingFormat: DownloadNamingFormat = DownloadNamingFormat.TITLE_ARTIST,
    /** 抓取歌词偏好：true 优先逐字，false 优先普通。 */
    val lyricsPreferWordLevel: Boolean = true,
)

class MusicDownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val sourceStore = LxMusicSourceStore(application)
    private val repository = MusicDownloadRepository(application)
    private val runtime = LxMusicSourceRuntime(application)
    private val searchProvider = MusicSearchProvider()
    private val settingsStore = SettingsStore(application)
    private val pluginStore = MetadataPluginStore(application)
    private val pluginManager = MetadataPluginManager(application, pluginStore)
    private val fetchService = MetadataFetchService(pluginManager)

    /** 供标签编辑页复用多源抓取（字段级聚合）。 */
    internal val metadataFetchService: MetadataFetchService get() = fetchService

    private val _state = MutableStateFlow(MusicDownloadUiState())
    val state: StateFlow<MusicDownloadUiState> = _state.asStateFlow()

    private var previewJob: Job? = null

    private var searchJob: Job? = null
    private var searchPage = 1
    private var searchHasMore = false

    init {
        val saved = sourceStore.load()
        val activeSource = saved.sources.firstOrNull { it.id == saved.activeSourceId }
            ?: saved.sources.firstOrNull()
        val settings = settingsStore.settings.value
        _state.update {
            it.copy(
                sources = saved.sources,
                activeSourceId = activeSource?.id,
                activeSourceName = activeSource?.name.orEmpty(),
                embedOptions = DownloadEmbedOptions(
                    writeMetadata = settings.downloadWriteMetadata,
                    embedLyrics = settings.downloadEmbedLyrics,
                    embedCover = settings.downloadEmbedCover,
                ),
                namingFormat = settings.downloadNamingFormat,
                lyricsPreferWordLevel = settings.lyricsFetchPreferWordLevel,
            )
        }
        fetchService.preferWordLevel = settings.lyricsFetchPreferWordLevel
        refreshMetadataPlugins()
        activeSource?.let { source ->
            viewModelScope.launch { loadSourceInternal(source) }
        }
    }

    fun ensureSourceLoaded() {
        val current = _state.value
        val source = current.sources.firstOrNull { it.id == current.activeSourceId }
            ?: current.sources.firstOrNull()
            ?: return
        if (
            runtime.loadedSourceId != source.id &&
            !current.isSourceLoading
        ) {
            viewModelScope.launch { loadSourceInternal(source) }
        }
    }

    fun importSource(url: String) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            _state.update { it.copy(importError = "请输入音源地址") }
            return
        }
        if (!normalizedUrl.startsWith("http://") &&
            !normalizedUrl.startsWith("https://")
        ) {
            _state.update { it.copy(importError = "音源地址必须以 http:// 或 https:// 开头") }
            return
        }
        _state.update {
            it.copy(
                isImporting = true,
                importError = null,
                importSuccess = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                val script = withContext(Dispatchers.IO) {
                    repository.fetchText(normalizedUrl)
                }
                LxMusicSourceStore.buildSource(script, normalizedUrl)
            }.onSuccess { source ->
                val saved = sourceStore.load()
                val updatedSources = saved.sources
                    .filterNot { it.id == source.id } + source
                sourceStore.save(
                    saved.copy(
                        sources = updatedSources,
                        activeSourceId = source.id,
                    ),
                )
                _state.update {
                    it.copy(
                        sources = updatedSources,
                        activeSourceId = source.id,
                        activeSourceName = source.name,
                        isImporting = false,
                        importError = null,
                        importSuccess = "已导入 ${source.name}",
                        results = emptyList(),
                        searchError = null,
                    )
                }
                loadSourceInternal(source)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isImporting = false,
                        importError = error.message ?: "音源导入失败",
                    )
                }
            }
        }
    }

    fun importLocalSource(uri: Uri) {
        _state.update {
            it.copy(
                isImporting = true,
                importError = null,
                importSuccess = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                val displayName = withContext(Dispatchers.IO) {
                    queryDisplayName(uri) ?: uri.lastPathSegment ?: "local-source.js"
                }
                val script = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { input -> input.readBytes().toString(Charsets.UTF_8) }
                        ?: throw IllegalStateException("无法读取本地音源文件")
                }
                LxMusicSourceStore.buildSource(
                    script = script,
                    scriptUrl = "local:$displayName",
                )
            }.onSuccess { source ->
                val saved = sourceStore.load()
                val updatedSources = saved.sources
                    .filterNot { it.id == source.id } + source
                sourceStore.save(
                    saved.copy(
                        sources = updatedSources,
                        activeSourceId = source.id,
                    ),
                )
                _state.update {
                    it.copy(
                        sources = updatedSources,
                        activeSourceId = source.id,
                        activeSourceName = source.name,
                        isImporting = false,
                        importError = null,
                        importSuccess = "已导入 ${source.name}",
                        results = emptyList(),
                        searchError = null,
                    )
                }
                loadSourceInternal(source)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isImporting = false,
                        importError = error.message ?: "本地音源导入失败",
                    )
                }
            }
        }
    }


    fun activateSource(sourceId: String) {
        val source = _state.value.sources.firstOrNull { it.id == sourceId } ?: return
        val saved = sourceStore.load()
        sourceStore.save(saved.copy(activeSourceId = sourceId))
        _state.update {
            it.copy(
                activeSourceId = sourceId,
                activeSourceName = source.name,
                results = emptyList(),
                searchError = null,
            )
        }
        viewModelScope.launch { loadSourceInternal(source) }
    }

    fun deleteSource(sourceId: String) {
        val saved = sourceStore.load()
        val remaining = saved.sources.filterNot { it.id == sourceId }
        val nextActive = saved.activeSourceId
            ?.takeIf { active -> active != sourceId }
            ?.takeIf { active -> remaining.any { it.id == active } }
            ?: remaining.firstOrNull()?.id
        sourceStore.save(
            saved.copy(
                sources = remaining,
                activeSourceId = nextActive,
            ),
        )
        val nextSource = remaining.firstOrNull { it.id == nextActive }
        _state.update {
            it.copy(
                sources = remaining,
                activeSourceId = nextSource?.id,
                activeSourceName = nextSource?.name.orEmpty(),
                searchSources = if (nextSource == null) {
                    emptyList()
                } else {
                    it.searchSources
                },
                selectedSearchSourceKey = if (nextSource == null) {
                    null
                } else {
                    it.selectedSearchSourceKey
                },
                selectedSearchSourceName = if (nextSource == null) {
                    ""
                } else {
                    it.selectedSearchSourceName
                },
                qualities = if (nextSource == null) emptyList() else it.qualities,
                results = emptyList(),
                searchError = null,
            )
        }
        if (nextSource != null) {
            viewModelScope.launch { loadSourceInternal(nextSource) }
        }
    }

    fun setSearchSource(optionId: String) {
        val option = _state.value.searchSources.firstOrNull { it.id == optionId }
            ?: return
        val qualities = qualitiesForOption(
            option = option,
            capabilities = runtime.loadedCapabilities,
        )
        _state.update {
            it.copy(
                selectedSearchSourceKey = option.id,
                selectedSearchSourceName = option.name,
                qualities = qualities,
                selectedQuality = chooseDefaultQuality(
                    current = it.selectedQuality,
                    available = qualities,
                ),
                results = emptyList(),
                searchError = null,
            )
        }
    }

    fun setQuality(quality: String) {
        if (quality.isBlank()) return
        _state.update { it.copy(selectedQuality = quality) }
    }

    /** 并行探测各音质的文件大小，供音质选择框展示。 */
    fun queryQualitySizes(song: LxMusicSong, qualities: List<String>) {
        val pending = qualities.distinct().filter { quality ->
            _state.value.qualitySizes[qualitySizeKey(song, quality)] == null
        }
        if (pending.isEmpty()) return
        pending.forEach { quality ->
            viewModelScope.launch {
                val size = runCatching {
                    val url = runtime.resolveUrl(song, quality)
                    withContext(Dispatchers.IO) {
                        repository.probeSize(url, song.sourceKey)
                    }
                }.getOrElse { -1L }
                _state.update {
                    it.copy(
                        qualitySizes = it.qualitySizes +
                            (qualitySizeKey(song, quality) to size),
                    )
                }
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = getApplication<Application>().contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }


    fun updateQuery(query: String) {
        _state.update {
            it.copy(
                query = query,
                searchError = null,
            )
        }
    }

    fun search() {
        val keyword = _state.value.query.trim()
        if (keyword.isBlank()) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    searchError = null,
                    isSearching = false,
                    hasMoreResults = false,
                    isLoadingMore = false,
                )
            }
            return
        }
        searchJob?.cancel()
        searchPage = 1
        _state.update {
            it.copy(
                isSearching = true,
                searchError = null,
                isLoadingMore = false,
                hasMoreResults = false,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val current = _state.value
                val option = currentSearchOption(current)
                val page = fetchSearchPage(option, keyword, searchPage)
                searchHasMore = !page.isEnd
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = page.songs,
                        hasMoreResults = searchHasMore,
                        searchError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isSearching = false,
                        results = emptyList(),
                        hasMoreResults = false,
                        searchError = error.message ?: "搜索失败",
                    )
                }
            }
        }
    }

    /** 滚动到底部后加载下一页搜索结果（按源的分页参数继续请求）。 */
    fun loadMoreResults() {
        val current = _state.value
        if (current.isLoadingMore || current.isSearching ||
            !current.hasMoreResults || current.query.isBlank()
        ) {
            return
        }
        val option = currentSearchOption(current)
        val keyword = current.query
        val nextPage = searchPage + 1
        _state.update { it.copy(isLoadingMore = true) }
        searchJob = viewModelScope.launch {
            try {
                val page = fetchSearchPage(option, keyword, nextPage)
                searchPage = nextPage
                searchHasMore = !page.isEnd
                _state.update { current ->
                    // 去重后再拼接，避免多源返回重复条目
                    val merged = current.results + page.songs.filter { new ->
                        current.results.none {
                            it.sourceKey == new.sourceKey && it.id == new.id
                        }
                    }
                    current.copy(
                        isLoadingMore = false,
                        results = merged,
                        hasMoreResults = searchHasMore,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 失败后允许再次触发加载
                searchPage = nextPage - 1
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun currentSearchOption(current: MusicDownloadUiState): MusicSearchOption =
        current.searchSources.firstOrNull {
            it.id == current.selectedSearchSourceKey
        } ?: current.searchSources.firstOrNull()
            ?: throw IllegalStateException("当前没有可用的搜索源")

    private suspend fun fetchSearchPage(
        option: MusicSearchOption,
        keyword: String,
        page: Int,
    ): LxSearchPage {
        android.util.Log.d(
            "QYSearch",
            "fetch option=" + option.id + " builtin=" + option.builtin + " kw=" + keyword,
        )
        return if (option.builtin != null) {
            withContext(Dispatchers.IO) {
                searchProvider.search(option, keyword, page)
            }
        } else {
            runtime.search(
                keyword = keyword,
                sourceKey = option.capabilityKey,
                page = page,
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                results = emptyList(),
                searchError = null,
                isSearching = false,
            )
        }
    }

    /**
     * 试听：点击后立即回调 [onPreviewStart]（宿主让播放条显示歌曲），
     * 随后解析流地址并回调 [onResolved] 交给主播放器流式播放；
     * 全部候选音质都解析失败时回调 [onResolveFailed]（宿主撤掉解析加载圈）。
     */
    fun previewSong(
        song: LxMusicSong,
        onPreviewStart: (LxMusicSong) -> Unit,
        onResolved: (LxMusicSong, String) -> Unit,
        onResolveFailed: (LxMusicSong) -> Unit = {},
    ) {
        onPreviewStart(song)
        viewModelScope.launch {
            var lastError: Exception? = null
            val candidates = downloadQualityCandidates(
                current = _state.value.selectedQuality,
                available = _state.value.qualities,
            )
            for (quality in candidates) {
                try {
                    val url = runtime.resolveUrl(song, quality)
                    onResolved(song, url)
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    lastError = error
                }
            }
            onResolveFailed(song)
            _state.update {
                it.copy(notice = lastError?.message ?: "暂时无法播放这首歌")
            }
        }
    }

    /** 下载完成后抓取歌词 / 封面并写入音频标签，返回用于展示的结果说明。 */
    fun download(
        song: LxMusicSong,
        destinationTreeUri: String?,
        quality: String = _state.value.selectedQuality.ifBlank {
            DEFAULT_DOWNLOAD_QUALITY
        },
    ) {
        val taskId = "${song.sourceKey}|${song.id}|$quality"
        val existing = _state.value.downloads[taskId]
        if (existing?.isActive == true) return
        val embedOptions = _state.value.embedOptions
        _state.update { current ->
            current.copy(
                downloads = current.downloads + (
                    taskId to MusicDownloadTaskState(
                        taskId = taskId,
                        song = song,
                        quality = quality,
                        phase = MusicDownloadPhase.RESOLVING,
                    )
                    ),
                notice = "开始下载 ${song.title}",
            )
        }
        viewModelScope.launch {
            var lastError: Exception? = null
            val requestedQuality = quality
            val candidates = downloadQualityCandidates(
                current = requestedQuality,
                available = _state.value.qualities,
            )
            for (candidateQuality in candidates) {
                try {
                    val url = runtime.resolveUrl(song, candidateQuality)
                    updateTask(taskId) {
                        it.copy(
                            quality = candidateQuality,
                            phase = MusicDownloadPhase.DOWNLOADING,
                            message = null,
                        )
                    }
                    val pending = repository.download(
                        url = url,
                        song = song,
                        namingFormat = _state.value.namingFormat,
                    ) { downloaded, total ->
                        updateTask(taskId) {
                            it.copy(
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                phase = MusicDownloadPhase.DOWNLOADING,
                            )
                        }
                    }
                    updateTask(taskId) {
                        it.copy(
                            phase = MusicDownloadPhase.EMBEDDING,
                            message = null,
                        )
                    }
                    val embedNote = runCatching {
                        embedMetadata(pending, song, embedOptions)
                    }.onFailure { error ->
                        Log.w(EMBED_LOG_TAG, "标签嵌入失败: ${error.message}")
                    }.getOrNull()
                    val result = repository.commit(
                        pending = pending,
                        destinationTreeUri = destinationTreeUri,
                    )
                    updateTask(taskId) {
                        it.copy(
                            quality = candidateQuality,
                            phase = MusicDownloadPhase.COMPLETED,
                            downloadedBytes = result.sizeBytes,
                            totalBytes = result.sizeBytes,
                            fileName = result.fileName,
                            displayPath = result.displayPath,
                            embedNote = embedNote,
                            message = null,
                        )
                    }
                    _state.update {
                        it.copy(notice = "${song.title} 下载成功")
                    }
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (downloadError: DownloadHttpException) {
                    lastError = downloadError
                    if (!downloadError.isRetryable) break
                    updateTask(taskId) {
                        it.copy(
                            phase = MusicDownloadPhase.RESOLVING,
                            message = "HTTP ${downloadError.statusCode}，尝试其他音质",
                        )
                    }
                } catch (downloadError: DownloadContentException) {
                    lastError = downloadError
                    updateTask(taskId) {
                        it.copy(
                            phase = MusicDownloadPhase.RESOLVING,
                            message = "音源返回错误，尝试其他音质",
                        )
                    }
                } catch (error: Exception) {
                    lastError = error
                    break
                }
            }
            val error = lastError ?: IllegalStateException("下载失败")
            _state.update {
                it.copy(notice = "${song.title} 下载失败")
            }
            updateTask(taskId) {
                it.copy(
                    phase = MusicDownloadPhase.FAILED,
                    message = error.message ?: "下载失败",
                )
            }
        }
    }

    private suspend fun embedMetadata(
        pending: PendingAudioDownload,
        song: LxMusicSong,
        options: DownloadEmbedOptions,
    ): String? {
        if (!options.anyEnabled) return null
        // 多源字段级聚合：单趟按优先级遍历插件，歌词与封面缺失字段由后续源补全
        val bundle = fetchService.fetchMetadata(
            song = song,
            request = MetadataFetchRequest(
                wantLyrics = options.embedLyrics,
                wantCover = options.embedCover,
                preferCoverUrl = song.coverUrl,
            ),
        )
        val lyrics = bundle.lyrics
        val cover = bundle.cover
        val failures = mutableListOf<String>()
        if (options.embedLyrics && lyrics == null) failures.add("歌词未找到")
        if (options.embedCover && cover == null) failures.add("封面未找到")
        if (lyrics == null && cover == null && !options.writeMetadata) {
            return failures.joinToString(" · ").ifBlank { null }
        }
        val report = withContext(Dispatchers.IO) {
            AudioTagWriter.embed(
                file = pending.tempFile,
                tags = EmbeddedTags(
                    title = if (options.writeMetadata) song.title else "",
                    artist = if (options.writeMetadata) song.artist else "",
                    album = if (options.writeMetadata) song.album else "",
                    coverBytes = cover?.bytes,
                    coverMime = cover?.mimeType,
                    lyrics = lyrics?.lrcText,
                ),
            )
        }
        if (report.unsupportedFormat) {
            failures.add("该格式不支持嵌入标签")
        } else {
            if (cover != null && !report.wroteCover) failures.add("封面写入失败")
            if (lyrics != null && !report.wroteLyrics) failures.add("歌词写入失败")
        }
        val notes = buildList {
            lyrics?.takeIf { report.wroteLyrics }?.let {
                add(
                    "歌词·${it.sourceName}" + if (it.wordLevel) "（逐字）" else "",
                )
            }
            cover?.takeIf { report.wroteCover }?.let { add("封面·${it.sourceName}") }
        }
        val parts = notes + failures
        return parts.joinToString(" · ").ifBlank { null }
    }


    fun dismissImportMessage() {
        _state.update {
            it.copy(
                importError = null,
                importSuccess = null,
            )
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearFinishedDownloads() {
        _state.update { current ->
            current.copy(
                downloads = current.downloads.filterValues { it.isActive },
            )
        }
    }

    /* ---- 歌词 / 封面元数据插件管理 ---- */

    fun refreshMetadataPlugins() {
        val state = pluginStore.load()
        _state.update {
            it.copy(
                metadataPlugins = it.metadataPlugins.copy(
                    plugins = state.plugins,
                    disabled = state.disabled,
                ),
            )
        }
    }

    fun setEmbedOptions(options: DownloadEmbedOptions) {
        settingsStore.setDownloadWriteMetadata(options.writeMetadata)
        settingsStore.setDownloadEmbedLyrics(options.embedLyrics)
        settingsStore.setDownloadEmbedCover(options.embedCover)
        _state.update { it.copy(embedOptions = options) }
    }

    fun setNamingFormat(format: DownloadNamingFormat) {
        settingsStore.setDownloadNamingFormat(format)
        _state.update { it.copy(namingFormat = format) }
    }

    /** 设置抓取歌词的类型偏好（影响下载嵌入与标签编辑抓取）。 */
    fun setLyricsPreferWordLevel(preferWordLevel: Boolean) {
        fetchService.preferWordLevel = preferWordLevel
        settingsStore.setLyricsFetchPreferWordLevel(preferWordLevel)
        _state.update { it.copy(lyricsPreferWordLevel = preferWordLevel) }
    }

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        pluginStore.setEnabled(pluginId, enabled)
        refreshMetadataPlugins()
    }

    fun movePluginUp(pluginId: String) {
        pluginStore.moveUp(pluginId)
        refreshMetadataPlugins()
    }

    fun movePluginDown(pluginId: String) {
        pluginStore.moveDown(pluginId)
        refreshMetadataPlugins()
    }

    fun deleteMetadataPlugin(pluginId: String) {
        pluginStore.delete(pluginId)
        pluginManager.release(pluginId)
        refreshMetadataPlugins()
    }

    fun dismissPluginImportMessage() {
        _state.update {
            it.copy(
                metadataPlugins = it.metadataPlugins.copy(
                    importMessage = null,
                    importError = null,
                ),
            )
        }
    }

    fun importMetadataPluginUrl(url: String) {
        val normalizedUrl = url.trim()
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            _state.update {
                it.copy(
                    metadataPlugins = it.metadataPlugins.copy(
                        importError = "插件地址必须以 http:// 或 https:// 开头",
                    ),
                )
            }
            return
        }
        setPluginImporting(true)
        viewModelScope.launch {
            runCatching {
                val request = org.json.JSONObject()
                    .put("url", normalizedUrl)
                    .put("method", "GET")
                val response = withContext(Dispatchers.IO) {
                    PluginHttpClient.execute(normalizedUrl, request)
                }
                if (response.code !in 200..299) {
                    throw IllegalStateException("插件下载失败：HTTP ${response.code}")
                }
                val bytes = java.util.Base64.getDecoder().decode(response.bodyBase64)
                importPackageBytes(bytes, defaultName = normalizedUrl.substringAfterLast('/'))
            }.onSuccess { plugin ->
                setPluginImporting(false)
                _state.update {
                    it.copy(
                        metadataPlugins = it.metadataPlugins.copy(
                            importMessage = "已导入插件 ${plugin.name}",
                            importError = null,
                        ),
                    )
                }
                refreshMetadataPlugins()
            }.onFailure { error ->
                setPluginImporting(false)
                _state.update {
                    it.copy(
                        metadataPlugins = it.metadataPlugins.copy(
                            importError = error.message ?: "插件导入失败",
                        ),
                    )
                }
            }
        }
    }

    fun importMetadataPluginLocal(uri: Uri) {
        setPluginImporting(true)
        viewModelScope.launch {
            runCatching {
                val (displayName, bytes) = withContext(Dispatchers.IO) {
                    val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "plugin.zip"
                    val data = getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: throw IllegalStateException("无法读取插件文件")
                    name to data
                }
                importPackageBytes(bytes, defaultName = displayName)
            }.onSuccess { plugin ->
                setPluginImporting(false)
                _state.update {
                    it.copy(
                        metadataPlugins = it.metadataPlugins.copy(
                            importMessage = "已导入插件 ${plugin.name}",
                            importError = null,
                        ),
                    )
                }
                refreshMetadataPlugins()
            }.onFailure { error ->
                setPluginImporting(false)
                _state.update {
                    it.copy(
                        metadataPlugins = it.metadataPlugins.copy(
                            importError = error.message ?: "插件导入失败",
                        ),
                    )
                }
            }
        }
    }

    /** zip（Lyrico 插件包）与单文件 JS 均可导入。 */
    private fun importPackageBytes(
        bytes: ByteArray,
        defaultName: String,
    ): MetadataPlugin {
        val packageData = if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            MetadataPluginParser.parseZip(bytes)
        } else {
            MetadataPluginParser.parseSingleScript(
                fileName = defaultName,
                script = String(bytes, Charsets.UTF_8),
            )
        }
        return pluginStore.importPackage(packageData)
    }

    private fun setPluginImporting(importing: Boolean) {
        _state.update {
            it.copy(
                metadataPlugins = it.metadataPlugins.copy(
                    isImporting = importing,
                    importError = if (importing) null else it.metadataPlugins.importError,
                    importMessage = if (importing) null else it.metadataPlugins.importMessage,
                ),
            )
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        runtime.release()
        pluginManager.releaseAll()
        super.onCleared()
    }

    private suspend fun loadSourceInternal(source: LxMusicSource) {
        _state.update {
            it.copy(
                isSourceLoading = true,
                activeSourceId = source.id,
                activeSourceName = source.name,
                searchError = null,
            )
        }
        runCatching {
            runtime.load(source)
        }.onSuccess { capabilities ->
            val searchSources = buildSearchOptions(capabilities)
            val searchSource = searchSources.firstOrNull {
                it.id == _state.value.selectedSearchSourceKey
            } ?: defaultSearchOption(searchSources)
            val qualities = searchSource?.let {
                qualitiesForOption(it, capabilities)
            }.orEmpty().ifEmpty { DEFAULT_QUALITIES }
            _state.update {
                it.copy(
                    isSourceLoading = false,
                    searchSources = searchSources,
                    selectedSearchSourceKey = searchSource?.id,
                    selectedSearchSourceName = searchSource?.name.orEmpty(),
                    qualities = qualities,
                    selectedQuality = chooseDefaultQuality(
                        current = it.selectedQuality,
                        available = qualities,
                    ),
                    searchError = null,
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    isSourceLoading = false,
                    searchError = error.message ?: "音源加载失败",
                    results = emptyList(),
                )
            }
        }
    }

    private fun updateTask(
        taskId: String,
        transform: (MusicDownloadTaskState) -> MusicDownloadTaskState,
    ) {
        _state.update { current ->
            val task = current.downloads[taskId] ?: return@update current
            current.copy(
                downloads = current.downloads + (taskId to transform(task)),
            )
        }
    }

    private fun buildSearchOptions(
        capabilities: List<LxSourceCapability>,
    ): List<MusicSearchOption> {
        val byKey = capabilities.associateBy(LxSourceCapability::key)
        val builtins = BuiltinMusicSource.entries
            .filter { source -> byKey[source.key]?.supportsUrl == true }
            .map(MusicSearchOption::builtin)
        val lxSources = capabilities
            .filter(LxSourceCapability::supportsSearch)
            .map(MusicSearchOption::lx)
        return builtins + lxSources
    }

    private fun defaultSearchOption(
        options: List<MusicSearchOption>,
    ): MusicSearchOption? =
        options.firstOrNull { it.builtin == BuiltinMusicSource.KUGOU }
            ?: options.firstOrNull { it.builtin == BuiltinMusicSource.KUWO }
            ?: options.firstOrNull { it.builtin == BuiltinMusicSource.QQ }
            ?: options.firstOrNull { it.builtin == BuiltinMusicSource.NETEASE }
            ?: options.firstOrNull { it.capabilityKey == PREFERRED_LX_SEARCH_SOURCE }
            ?: options.firstOrNull()

    private fun qualitiesForOption(
        option: MusicSearchOption,
        capabilities: List<LxSourceCapability>,
    ): List<String> {
        val capabilityKey = option.builtin?.key ?: option.capabilityKey ?: return emptyList()
        return capabilities.firstOrNull { it.key == capabilityKey }
            ?.qualities
            .orEmpty()
            .ifEmpty { DEFAULT_QUALITIES }
    }

    private val DownloadHttpException.isRetryable: Boolean
        get() = statusCode in setOf(403, 404, 410, 429, 500, 502, 503, 504)

    private fun downloadQualityCandidates(
        current: String,
        available: List<String>,
    ): List<String> {
        val preferred = listOf(current, "128k", "320k", "flac", "flac24bit", "24bit")
        val allowed = available.ifEmpty { preferred }
        return preferred.filter(allowed::contains).distinct().ifEmpty { listOf(current) }
    }


    private companion object {
        const val PREFERRED_LX_SEARCH_SOURCE = "qsvip"
        const val EMBED_LOG_TAG = "QYMusicEmbed"
        val DEFAULT_QUALITIES = listOf("128k", "320k", "flac", "flac24bit")

        /** 与下载任务 id 同构：音质大小探测结果按此缓存。 */
        fun qualitySizeKey(song: LxMusicSong, quality: String): String =
            "${song.sourceKey}|${song.id}|$quality"

        fun chooseDefaultQuality(
            current: String,
            available: List<String>,
        ): String = current.takeIf(available::contains)
            ?: DEFAULT_DOWNLOAD_QUALITY.takeIf(available::contains)
            ?: available.firstOrNull()
            ?: DEFAULT_DOWNLOAD_QUALITY
    }
}
