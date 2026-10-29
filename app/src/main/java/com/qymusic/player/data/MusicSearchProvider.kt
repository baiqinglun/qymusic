package com.qymusic.player.data

import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

enum class BuiltinMusicSource(
    val key: String,
    val displayName: String,
) {
    KUGOU("kg", "酷狗"),
    KUWO("kw", "酷我"),
    NETEASE("wy", "网易云"),
    QQ("tx", "QQ音乐"),
}

data class MusicSearchOption(
    val id: String,
    val name: String,
    val builtin: BuiltinMusicSource? = null,
    val capabilityKey: String? = null,
) {
    companion object {
        fun builtin(source: BuiltinMusicSource): MusicSearchOption =
            MusicSearchOption(
                id = "builtin:${source.key}",
                name = source.displayName,
                builtin = source,
            )

        fun lx(capability: LxSourceCapability): MusicSearchOption =
            MusicSearchOption(
                id = "lx:${capability.key}",
                name = capability.name,
                capabilityKey = capability.key,
            )
    }
}

class MusicSearchProvider {
    fun search(
        option: MusicSearchOption,
        keyword: String,
        page: Int = 1,
        pageSize: Int = DEFAULT_PAGE_SIZE,
    ): LxSearchPage {
        val source = option.builtin
            ?: throw IllegalStateException("当前搜索源不可用")
        android.util.Log.d("QYSearch", "search source=${source.key} kw=$keyword page=$page")
        return when (source) {
            BuiltinMusicSource.KUGOU -> searchKugou(keyword, page, pageSize)
            BuiltinMusicSource.KUWO -> searchKuwo(keyword, page, pageSize)
            BuiltinMusicSource.NETEASE -> searchNetease(keyword, page, pageSize)
            BuiltinMusicSource.QQ -> searchQq(keyword, page, pageSize)
        }
    }

    private fun searchKugou(
        keyword: String,
        page: Int,
        pageSize: Int,
    ): LxSearchPage {
        android.util.Log.d("QYSearch", "kugou requesting url=$KUGOU_SEARCH_URL kw=$keyword")
        val response = LxHttpClient.execute(
            url = buildUrl(
                KUGOU_SEARCH_URL,
                mapOf(
                    "format" to "json",
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "showtype" to "1",
                ),
            ),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", SEARCH_TIMEOUT_MS)
                .put("headers", headers(REFERER_KUGOU)),
        )
        android.util.Log.d(
            "QYSearch",
            "kugou status=${response.statusCode} len=${response.body.length} head=${response.body.take(160)}",
        )
        ensureSuccess(response, "酷狗搜索")
        val root = JSONObject(response.body)
        val data = root.optJSONObject("data")
        val list = data?.optJSONArray("info") ?: JSONArray()
        val songs = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val hash = item.optString("hash").takeIf(String::isNotBlank) ?: continue
                val title = cleanText(
                    item.optString("songname").ifBlank { item.optString("filename") },
                ).ifBlank { "未知歌曲" }
                val artist = cleanText(item.optString("singername"))
                    .ifBlank { "未知歌手" }
                val album = cleanText(item.optString("album_name"))
                val durationSeconds = item.optLong("duration")
                    .coerceAtLeast(0L)
                val raw = JSONObject(item.toString())
                    .put("songmid", hash)
                    .put("id", hash)
                    .put("rid", item.optString("audio_id"))
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", album)
                    .put("duration", durationSeconds)
                add(
                    LxMusicSong(
                        id = hash,
                        sourceKey = BuiltinMusicSource.KUGOU.key,
                        sourceName = BuiltinMusicSource.KUGOU.displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationSeconds * 1000L,
                        coverUrl = null,
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
        val total = data?.optInt("total")?.takeIf { it > 0 }
        return LxSearchPage(
            songs = songs,
            isEnd = songs.size < pageSize,
            total = total,
        )
    }

    private fun searchKuwo(
        keyword: String,
        page: Int,
        pageSize: Int,
    ): LxSearchPage {
        val response = LxHttpClient.execute(
            url = buildUrl(
                KUWO_SEARCH_URL,
                mapOf(
                    "all" to keyword,
                    "ft" to "music",
                    "itemset" to "web_2013",
                    "client" to "kt",
                    "pn" to ((page - 1).coerceAtLeast(0)).toString(),
                    "rn" to pageSize.toString(),
                    "rformat" to "json",
                    "encoding" to "utf8",
                    "vipver" to "1",
                ),
            ),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", SEARCH_TIMEOUT_MS)
                .put("headers", headers(REFERER_KUWO)),
        )
        ensureSuccess(response, "酷我搜索")
        val root = JSONObject(response.body.trim().replace('\'', '"'))
        val list = root.optJSONArray("abslist") ?: JSONArray()
        val songs = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val rid = item.optString("MUSICRID")
                    .substringAfter('_')
                    .ifBlank { item.optString("DC_TARGETID") }
                    .takeIf(String::isNotBlank)
                    ?: continue
                val title = cleanText(item.optString("NAME"))
                    .ifBlank { "未知歌曲" }
                val artist = cleanText(item.optString("ARTIST"))
                    .ifBlank { "未知歌手" }
                val album = cleanText(item.optString("ALBUM"))
                val durationSeconds = item.optString("DURATION").toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                val coverUrl = cleanText(item.optString("web_albumpic_short"))
                    .takeIf(String::isNotBlank)
                    ?.let { raw ->
                        // 相对路径（如 120/s3s94/93/x.jpg）需拼 img4 域名，并升到 500px
                        val absolute = when {
                            raw.startsWith("http") -> raw
                            Regex("^\\d+([xX]\\d+)?/").containsMatchIn(raw) ->
                                "http://img4.kuwo.cn/star/albumcover/$raw"
                            else -> "http://$raw"
                        }
                        absolute.replace("/120x120/", "/500x500/").replace("/120/", "/500/")
                    }
                val raw = JSONObject(item.toString())
                    .put("songmid", rid)
                    .put("id", rid)
                    .put("rid", rid)
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", album)
                    .put("duration", durationSeconds)
                add(
                    LxMusicSong(
                        id = rid,
                        sourceKey = BuiltinMusicSource.KUWO.key,
                        sourceName = BuiltinMusicSource.KUWO.displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationSeconds * 1000L,
                        coverUrl = coverUrl,
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
        val total = root.optString("TOTAL").toIntOrNull()?.takeIf { it > 0 }
        return LxSearchPage(
            songs = songs,
            isEnd = songs.size < pageSize,
            total = total,
        )
    }

    private fun searchNetease(
        keyword: String,
        page: Int,
        pageSize: Int,
    ): LxSearchPage {
        val offset = ((page - 1).coerceAtLeast(0)) * pageSize
        val response = LxHttpClient.execute(
            url = buildUrl(
                NETEASE_SEARCH_URL,
                mapOf(
                    "s" to keyword,
                    "type" to "1",
                    "offset" to offset.toString(),
                    "limit" to pageSize.toString(),
                ),
            ),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", SEARCH_TIMEOUT_MS)
                .put("headers", headers(REFERER_NETEASE)),
        )
        ensureSuccess(response, "网易云搜索")
        val root = JSONObject(response.body)
        val result = root.optJSONObject("result")
        val list = result?.optJSONArray("songs") ?: JSONArray()
        val songs = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optLong("id").takeIf { it > 0L }?.toString() ?: continue
                val title = cleanText(item.optString("name")).ifBlank { "未知歌曲" }
                val artist = cleanText(
                    item.optJSONArray("artists").joinNames(),
                ).ifBlank { "未知歌手" }
                val album = cleanText(
                    item.optJSONObject("album")?.optString("name").orEmpty(),
                )
                val durationMs = item.optLong("duration").coerceAtLeast(0L)
                val raw = JSONObject(item.toString())
                    .put("songmid", id)
                    .put("id", id)
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", album)
                    .put("duration", durationMs / 1000L)
                add(
                    LxMusicSong(
                        id = id,
                        sourceKey = BuiltinMusicSource.NETEASE.key,
                        sourceName = BuiltinMusicSource.NETEASE.displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationMs,
                        coverUrl = null,
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
        val total = result?.optInt("songCount")?.takeIf { it > 0 }
        return LxSearchPage(
            songs = songs,
            isEnd = songs.size < pageSize,
            total = total,
        )
    }

    private fun searchQq(
        keyword: String,
        page: Int,
        pageSize: Int,
    ): LxSearchPage {
        val offset = ((page - 1).coerceAtLeast(0)) * pageSize
        val response = LxHttpClient.execute(
            url = buildUrl(
                QQ_SEARCH_URL,
                mapOf(
                    "ct" to "24",
                    "format" to "json",
                    "inCharset" to "utf8",
                    "outCharset" to "utf-8",
                    "platform" to "yqq.json",
                    "w" to keyword,
                    "n" to pageSize.toString(),
                    "p" to ((offset / pageSize) + 1).toString(),
                ),
            ),
            options = JSONObject()
                .put("method", "GET")
                .put("timeout", SEARCH_TIMEOUT_MS)
                .put("headers", headers(REFERER_QQ)),
        )
        android.util.Log.d(
            "QYSearch",
            "qq status=${response.statusCode} len=${response.body.length} head=${response.body.take(160)}",
        )
        ensureSuccess(response, "QQ音乐搜索")
        val root = JSONObject(response.body)
        val list = root.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: JSONArray()
        val songs = buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val songmid = item.optString("songmid").takeIf(String::isNotBlank) ?: continue
                val title = cleanText(item.optString("songname")).ifBlank { "未知歌曲" }
                val artist = cleanText(
                    item.optJSONArray("singer").joinNames(),
                ).ifBlank { "未知歌手" }
                val album = cleanText(item.optString("albumname"))
                val durationSeconds = item.optLong("interval").coerceAtLeast(0L)
                val raw = JSONObject(item.toString())
                    .put("songmid", songmid)
                    .put("id", songmid)
                    .put("name", title)
                    .put("singer", artist)
                    .put("albumName", album)
                    .put("duration", durationSeconds)
                add(
                    LxMusicSong(
                        id = songmid,
                        sourceKey = BuiltinMusicSource.QQ.key,
                        sourceName = BuiltinMusicSource.QQ.displayName,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = durationSeconds * 1000L,
                        coverUrl = null,
                        rawJson = raw.toString(),
                    ),
                )
            }
        }
        val total = root.optJSONObject("data")?.optJSONObject("song")
            ?.optInt("total")?.takeIf { it > 0 }
        return LxSearchPage(
            songs = songs,
            isEnd = songs.size < pageSize,
            total = total,
        )
    }

    private fun JSONArray?.joinNames(): String {
        if (this == null) return ""
        return buildList {
            for (index in 0 until length()) {
                when (val value = opt(index)) {
                    is JSONObject -> add(value.optString("name"))
                    else -> add(value?.toString().orEmpty())
                }
            }
        }.filter(String::isNotBlank).joinToString(" / ")
    }

    private fun headers(referer: String): JSONObject = JSONObject()
        .put("User-Agent", USER_AGENT)
        .put("Referer", referer)
        .put("Accept", "application/json,text/plain,*/*")

    private fun ensureSuccess(response: LxHttpResponse, operation: String) {
        if (response.statusCode !in 200..299) {
            throw IllegalStateException("$operation 失败：HTTP ${response.statusCode}")
        }
        if (response.body.isBlank()) {
            throw IllegalStateException("$operation 返回为空")
        }
    }

    private fun cleanText(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("\\\\u0026", "&")
        .replace("\\u0026", "&")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun buildUrl(base: String, params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "$base?$query"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val DEFAULT_PAGE_SIZE = 30
        const val SEARCH_TIMEOUT_MS = 20_000
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
        const val KUGOU_SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song"
        const val KUWO_SEARCH_URL = "https://search.kuwo.cn/r.s"
        const val NETEASE_SEARCH_URL = "https://music.163.com/api/search/get/web"
        const val QQ_SEARCH_URL =
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
        const val REFERER_KUGOU = "https://www.kugou.com/"
        const val REFERER_KUWO = "https://www.kuwo.cn/"
        const val REFERER_NETEASE = "https://music.163.com/"
        const val REFERER_QQ = "https://y.qq.com/"
    }
}
