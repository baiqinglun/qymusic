package com.qymusic.player.data

import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class FetchedLyrics(
    val lrcText: String,
    val wordLevel: Boolean,
    val sourceName: String,
)

data class FetchedCover(
    val bytes: ByteArray,
    val mimeType: String,
    val sourceName: String,
)

/** 各字段聚合结果：同名字段来自最先成功的源（优先级高者优先）。 */
data class MetadataBundle(
    val lyrics: FetchedLyrics? = null,
    val cover: FetchedCover? = null,
)

data class MetadataFetchRequest(
    val wantLyrics: Boolean,
    val wantCover: Boolean,
    /** 歌曲自带的封面直链；视为封面字段的最高优先级源，仅补封面时不影响歌词抓取。 */
    val preferCoverUrl: String? = null,
)

/**
 * 插件调用入口抽象：便于对字段级聚合策略做单元测试。
 */
interface MetadataPluginInvoker {
    fun enabledPlugins(): List<MetadataPlugin>
    suspend fun invoke(
        plugin: MetadataPlugin,
        functionName: String,
        request: JSONObject,
    ): Any?
}

/**
 * 多源抓取的字段级补全策略：
 *
 * 1. 按插件优先级顺序依次请求各数据源；
 * 2. 字段级回退：某源已成功获取的字段予以保留并跳过（不再向后续源请求该字段），
 *    缺失的字段由后续源继续补充——例如某源返回了歌词但未返回封面，
 *    歌词采用该源结果，同时继续请求后续源补全封面；
 * 3. 终止条件：所有目标字段均已获取，或所有源均已遍历完毕；
 * 4. 合并规则：最终结果为各源成功字段的合并，同名字段以先成功返回的源为准。
 */
class MetadataFetchService(
    private val manager: MetadataPluginInvoker,
    private val imageFetcher: suspend (String) -> ByteArray? = ::downloadImage,
) {

    /**
     * 抓取歌词的类型偏好：true 优先逐字歌词（带单词级时间轴），
     * false 优先普通歌词（仅逐行时间轴）。优先的类型在当前源找不到时
     * 会继续问后续源，全部找不到才回退另一种。
     */
    var preferWordLevel: Boolean = true


    suspend fun fetchMetadata(
        song: LxMusicSong,
        request: MetadataFetchRequest,
    ): MetadataBundle {
        var bundle = MetadataBundle()
        if (!request.wantLyrics && !request.wantCover) return bundle

        // 封面直链：搜索结果自带封面地址时，作为封面字段的最高优先级源先行尝试
        if (request.wantCover && !request.preferCoverUrl.isNullOrBlank()) {
            imageFetcher(request.preferCoverUrl!!)?.let { bytes ->
                bundle = bundle.copy(
                    cover = FetchedCover(
                        bytes = bytes,
                        mimeType = detectImageMime(bytes),
                        sourceName = song.sourceName,
                    ),
                )
            }
        }

        var lyricFallback: FetchedLyrics? = null
        if (bundle.lyrics == null || bundle.cover == null) {
            val pluginRequest = buildSongRequest(song)
            for (plugin in manager.enabledPlugins()) {
                if (request.wantLyrics && bundle.lyrics == null && plugin.supportsLyrics) {
                    val raw = runCatching {
                        withTimeout(PLUGIN_TIMEOUT_MS) {
                            manager.invoke(plugin, "getLyrics", pluginRequest)
                        }
                    }.onFailure { error ->
                        android.util.Log.w(
                            LOG_TAG,
                            "歌词插件 ${plugin.name} 调用失败: ${error.message}",
                        )
                    }.getOrNull()
                    if (raw != null) {
                        val candidate = LyricResultParser.pickCandidate(raw, preferWordLevel)
                        val lrc = candidate?.let { LyricResultParser.toLrc(it) }
                        if (candidate != null && lrc != null) {
                            val wordLevel = LyricResultParser.isWordLevel(candidate)
                            val fetched = FetchedLyrics(
                                lrcText = lrc,
                                wordLevel = wordLevel,
                                sourceName = plugin.name,
                            )
                            // 命中偏好类型直接采用；不符合的先记作回退，
                            // 循环继续问后续源找符合偏好的
                            if (wordLevel == preferWordLevel) {
                                bundle = bundle.copy(lyrics = fetched)
                            } else if (lyricFallback == null) {
                                lyricFallback = fetched
                            }
                        }
                    }
                }
                if (request.wantCover && bundle.cover == null && plugin.supportsCovers) {
                    val raw = runCatching {
                        withTimeout(PLUGIN_TIMEOUT_MS) {
                            manager.invoke(plugin, "searchCovers", pluginRequest)
                        }
                    }.onFailure { error ->
                        android.util.Log.w(
                            LOG_TAG,
                            "封面插件 ${plugin.name} 调用失败: ${error.message}",
                        )
                    }.getOrNull()
                    val url = raw?.let { CoverResultParser.pickUrl(it) }
                    if (url != null) {
                        imageFetcher(url)?.let { bytes ->
                            bundle = bundle.copy(
                                cover = FetchedCover(
                                    bytes = bytes,
                                    mimeType = detectImageMime(bytes),
                                    sourceName = plugin.name,
                                ),
                            )
                        }
                    }
                }
                // 终止条件：目标字段全部就绪（歌词只拿到不符合偏好类型的回退时
                // 不算就绪，继续问后续源找符合偏好的）
                if ((!request.wantLyrics || bundle.lyrics != null) &&
                    (!request.wantCover || bundle.cover != null)
                ) {
                    break
                }
            }
        }
        // 所有源都没有符合偏好类型的歌词时，回退到先拿到的另一种
        if (request.wantLyrics && bundle.lyrics == null && lyricFallback != null) {
            bundle = bundle.copy(lyrics = lyricFallback)
        }
        android.util.Log.d(
            LOG_TAG,
            "聚合结果 歌词=${bundle.lyrics?.sourceName} 封面=${bundle.cover?.sourceName}",
        )
        return bundle
    }

    /**
     * 重新抓取源信息：按优先级调用各插件的歌曲搜索，取标题 / 歌手 / 时长
     * 最接近的匹配，返回其标准字段（标题、艺术家、专辑、年份、音轨号、封面直链）。
     */
    suspend fun fetchSongInfo(song: LxMusicSong): FetchedSongInfo? {
        val pluginRequest = buildSongRequest(song)
        for (plugin in manager.enabledPlugins()) {
            if (!plugin.supportsSearch) continue
            val raw = runCatching {
                withTimeout(PLUGIN_TIMEOUT_MS) {
                    manager.invoke(plugin, "searchSongs", pluginRequest)
                }
            }.onFailure { error ->
                android.util.Log.w(LOG_TAG, "歌曲搜索插件 ${plugin.name} 失败: ${error.message}")
            }.getOrNull() ?: continue
            val candidates = SongResultParser.parse(raw)
            val best = SongResultParser.bestMatch(
                candidates,
                song.title,
                song.artist,
                song.durationMs,
            ) ?: continue
            return FetchedSongInfo(
                sourceName = plugin.name,
                title = best.title,
                artist = best.artist,
                album = best.album,
                date = best.date,
                trackNumber = best.trackNumber,
                coverUrl = best.picUrl,
            )
        }
        return null
    }

    private fun buildSongRequest(song: LxMusicSong): JSONObject {
        val internal = runCatching { JSONObject(song.rawJson) }.getOrNull() ?: JSONObject()
        return JSONObject()
            .put(
                "song",
                JSONObject()
                    .put("id", song.id)
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("duration", song.durationMs)
                    .put("sourceId", song.sourceKey)
                    .put("internal", internal),
            )
            .put("page", 1)
            .put("pageSize", 20)
            .put("config", JSONObject())
    }

    private companion object {
        const val LOG_TAG = "QYMetadataFetch"
        const val PLUGIN_TIMEOUT_MS = 25_000L
    }
}

/** 各源歌曲搜索结果的归一化条目。 */
data class ParsedSong(
    val title: String,
    val artist: String,
    val album: String,
    val date: String,
    val trackNumber: String,
    val picUrl: String,
    val durationMs: Long,
)

/** 重新抓取源信息的结果。 */
data class FetchedSongInfo(
    val sourceName: String,
    val title: String,
    val artist: String,
    val album: String,
    val date: String,
    val trackNumber: String,
    val coverUrl: String,
)

/** searchSongs 结果的宽松解析（字段名映射与 Lyrico 规范一致）。 */
object SongResultParser {

    fun parse(raw: Any?): List<ParsedSong> {
        val array = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("items")
                ?: raw.optJSONArray("results")
                ?: raw.optJSONArray("songs")
                ?: raw.optJSONArray("data")
                ?: JSONArray()
            else -> return emptyList()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = firstText(item, "title", "name", "songName", "songname")
                val artist = when (val value = item.opt("artist") ?: item.opt("singer") ?: item.opt("artists")) {
                    is JSONArray -> buildList {
                        for (i in 0 until value.length()) {
                            when (val entry = value.opt(i)) {
                                is JSONObject -> add(entry.optString("name"))
                                else -> add(entry?.toString().orEmpty())
                            }
                        }
                    }.filter(String::isNotBlank).joinToString(" / ")

                    is JSONObject -> value.optString("name")
                    else -> value?.toString().orEmpty()
                }
                val duration = firstKey(item, "duration", "durationMs", "duration_ms")
                    ?.let { item.optDouble(it, 0.0) }
                    ?: 0.0
                add(
                    ParsedSong(
                        title = title,
                        artist = artist,
                        album = firstText(item, "album", "albumName", "album_name"),
                        date = firstText(item, "date", "releaseDate", "release_date"),
                        trackNumber = firstText(
                            item,
                            "trackNumber",
                            "trackerNumber",
                            "track_number",
                        ),
                        picUrl = firstText(item, "picUrl", "coverUrl", "cover_url", "artworkUrl"),
                        durationMs = when {
                            duration <= 0.0 -> 0L
                            duration > 10_000.0 -> duration.toLong()
                            else -> (duration * 1000.0).toLong()
                        },
                    ),
                )
            }
        }.filter { it.title.isNotBlank() }
    }

    fun bestMatch(
        candidates: List<ParsedSong>,
        title: String,
        artist: String,
        durationMs: Long,
    ): ParsedSong? {
        if (candidates.isEmpty()) return null
        val lowerTitle = title.lowercase()
        val lowerArtist = artist.lowercase()
        var best: ParsedSong? = null
        var bestScore = Int.MIN_VALUE
        candidates.forEachIndexed { index, item ->
            var score = 0
            val itemTitle = item.title.lowercase()
            val itemArtist = item.artist.lowercase()
            if (itemTitle.isNotEmpty() && lowerTitle.contains(itemTitle)) score += 2
            if (itemTitle.isNotEmpty() && itemTitle.contains(lowerTitle)) score += 2
            if (itemArtist.isNotEmpty() &&
                (lowerArtist.contains(itemArtist) || itemArtist.contains(lowerArtist))
            ) {
                score += 2
            }
            if (index == 0) score += 1
            if (durationMs > 0 && item.durationMs > 0) {
                val diff = Math.abs(item.durationMs - durationMs)
                when {
                    diff <= 3_000 -> score += 3
                    diff <= 8_000 -> score += 1
                    diff > 20_000 -> score -= 2
                }
            }
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return best
    }

    private fun firstText(item: JSONObject, vararg keys: String): String =
        keys.firstNotNullOfOrNull { key ->
            item.optString(key).takeIf(String::isNotBlank)
        }.orEmpty()

    private fun firstKey(item: JSONObject, vararg keys: String): String? =
        keys.firstOrNull { item.has(it) }
}


/** 下载封面图片字节（跟随最多 3 次重定向）。 */
internal suspend fun downloadImage(url: String): ByteArray? = runCatching {
    withContext(Dispatchers.IO) {
        var current = url.trim()
        repeat(3) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = IMAGE_CONNECT_TIMEOUT_MS
            connection.readTimeout = IMAGE_READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", IMAGE_USER_AGENT)
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                    if (location.isNullOrBlank()) return@withContext null
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) return@withContext null
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.size < 128) return@withContext null
                return@withContext bytes
            } finally {
                connection.disconnect()
            }
        }
        null
    }
}.onFailure { error ->
    android.util.Log.w(LOG_TAG, "封面下载失败: ${error.message}")
}.getOrNull()

internal fun detectImageMime(bytes: ByteArray): String = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 8 && bytes.copyOfRange(1, 4).decodeToString() == "PNG" -> "image/png"
    bytes.size >= 6 && bytes.decodeToString(0, 6) == "GIF87a" ||
        bytes.decodeToString(0, 6) == "GIF89a" -> "image/gif"
    else -> "image/jpeg"
}

private const val LOG_TAG = "QYMetadataFetch"
private const val IMAGE_CONNECT_TIMEOUT_MS = 10_000
private const val IMAGE_READ_TIMEOUT_MS = 15_000
private const val IMAGE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

/** 封面结果的宽松解析：数组或包装对象，取分辨率最高的 URL。 */
private object CoverResultParser {

    fun pickUrl(raw: Any?): String? {
        val array = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("items")
                ?: raw.optJSONArray("results")
                ?: raw.optJSONArray("covers")
                ?: raw.optJSONArray("data")
                ?: JSONArray().put(raw)
            else -> return null
        }
        var best: Pair<String, Int>? = null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val url = parseCoverObject(item) ?: continue
            val width = item.optInt("width", item.optInt("size", 0))
            if (best == null || width > best.second) {
                best = url to width
            }
        }
        return best?.first
    }

    private fun parseCoverObject(item: JSONObject): String? =
        item.optString("url").takeIf(String::isNotBlank)
            ?: item.optString("coverUrl").takeIf(String::isNotBlank)
            ?: item.optString("picUrl").takeIf(String::isNotBlank)
            ?: item.optString("img").takeIf(String::isNotBlank)
}

/** 歌词候选的解析与 LRC 生成（支持 rawPlainLrc / rawEnhancedLrc / structured）。 */
object LyricResultParser {

    /**
     * 从候选里挑一条。[preferWordLevel] 给定时优先挑符合偏好类型的候选
     * （逐字 / 普通），没有符合的再回退到第一条有效候选。
     */
    fun pickCandidate(raw: Any?, preferWordLevel: Boolean? = null): JSONObject? {
        val array = when (raw) {
            is JSONArray -> raw
            is JSONObject -> raw.optJSONArray("items")
                ?: raw.optJSONArray("results")
                ?: raw.optJSONArray("candidates")
                ?: JSONArray().put(raw)
            is String -> {
                if (raw.isBlank()) return null
                JSONArray().put(JSONObject().put("rawPlainLrc", raw))
            }
            else -> return null
        }
        var fallback: JSONObject? = null
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optBoolean("__rejected", false)) continue
            if (item.length() == 0) continue
            if (preferWordLevel == null || isWordLevel(item) == preferWordLevel) {
                return item
            }
            if (fallback == null) fallback = item
        }
        return fallback
    }

    fun isWordLevel(candidate: JSONObject): Boolean = when (candidate.optString("type")) {
        "rawEnhancedLrc", "structured" -> true
        else -> candidate.optString("rawEnhancedLrc").isNotBlank()
    }

    fun toLrc(candidate: JSONObject): String? {
        val header = buildHeader(candidate)
        when (candidate.optString("type")) {
            "structured" -> {
                val body = structuredToLrc(candidate) ?: return null
                return header + body
            }
            "rawEnhancedLrc" -> {
                val text = candidate.optString("rawEnhancedLrc")
                return header + text
            }
            "rawPlainLrc" -> {
                val text = candidate.optString("rawPlainLrc")
                return header + text
            }
            else -> {
                val enhanced = candidate.optString("rawEnhancedLrc")
                if (enhanced.isNotBlank()) return header + enhanced
                val plain = candidate.optString("rawPlainLrc").ifBlank {
                    candidate.optString("raw")
                }
                if (plain.isNotBlank()) return header + plain
                return null
            }
        }
    }

    private fun buildHeader(candidate: JSONObject): String {
        val tags = candidate.optJSONObject("tags") ?: return ""
        val lines = buildList {
            tags.optString("ti").takeIf(String::isNotBlank)?.let { add("[ti:$it]") }
            tags.optString("ar").takeIf(String::isNotBlank)?.let { add("[ar:$it]") }
            tags.optString("al").takeIf(String::isNotBlank)?.let { add("[al:$it]") }
            add("[by:QYMusic]")
        }
        return lines.joinToString("\n") + "\n"
    }

    private fun structuredToLrc(candidate: JSONObject): String? {
        val original = candidate.optJSONArray("original") ?: return null
        val translated = candidate.optJSONArray("translated")
        val builder = StringBuilder()
        for (index in 0 until original.length()) {
            val line = original.optJSONArray(index) ?: continue
            val start = line.optLong(0, -1L)
            if (start < 0) continue
            val end = line.optLong(1, start)
            builder.append('[').append(formatTime(start)).append(']')
            val payload = line.opt(2)
            when (payload) {
                is String -> builder.append(payload)
                is JSONArray -> appendWords(builder, payload, end)
            }
            builder.append('\n')
            translated?.opt(index)?.let { translation ->
                val text = when (translation) {
                    is String -> translation
                    is JSONArray -> translation.optString(2)
                    else -> null
                }
                if (!text.isNullOrBlank()) {
                    builder.append('[').append(formatTime(start)).append(']').append(text).append('\n')
                }
            }
        }
        return builder.toString().takeIf(String::isNotBlank)
    }

    private fun appendWords(builder: StringBuilder, words: JSONArray, lineEndMs: Long) {
        for (index in 0 until words.length()) {
            when (val word = words.opt(index)) {
                is String -> builder.append(word)
                is JSONArray -> {
                    val wordStart = word.optLong(0, 0L)
                    val wordEnd = word.optLong(1, wordStart).coerceAtLeast(wordStart)
                    val text = word.optString(2)
                    builder.append('<').append(formatTime(wordStart)).append('>')
                    builder.append(text)
                }
            }
        }
    }

    fun formatTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val minutes = total / 60_000
        val seconds = (total % 60_000) / 1000
        val centis = (total % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
    }
}
