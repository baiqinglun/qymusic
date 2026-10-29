package com.qymusic.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 一条试听缓存记录：流地址 + 歌曲信息（用于回查与一键下载）。 */
data class PreviewCacheEntry(
    val url: String,
    val songId: String,
    val sourceKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAtMs: Long,
)

/** 试听缓存注册表：记录流地址与歌曲信息，持久化在 SharedPreferences。 */
class PreviewCacheRegistry(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("preview_cache_registry", Context.MODE_PRIVATE)

    fun record(song: LxMusicSong, url: String) {
        val list = entries().toMutableList()
        list.removeAll { it.url == url }
        list.add(
            0,
            PreviewCacheEntry(
                url = url,
                songId = song.id,
                sourceKey = song.sourceKey,
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                addedAtMs = System.currentTimeMillis(),
            ),
        )
        save(list)
    }

    fun entries(): List<PreviewCacheEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ENTRIES, null).orEmpty())
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val url = entry.optString("url")
            if (url.isBlank()) return@mapNotNull null
            PreviewCacheEntry(
                url = url,
                songId = entry.optString("songId"),
                sourceKey = entry.optString("sourceKey"),
                title = entry.optString("title"),
                artist = entry.optString("artist"),
                album = entry.optString("album"),
                durationMs = entry.optLong("durationMs"),
                addedAtMs = entry.optLong("addedAtMs"),
            )
        }
    }.getOrDefault(emptyList())

    fun remove(url: String) {
        save(entries().filterNot { it.url == url })
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(list: List<PreviewCacheEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("url", entry.url)
                    .put("songId", entry.songId)
                    .put("sourceKey", entry.sourceKey)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    .put("durationMs", entry.durationMs)
                    .put("addedAtMs", entry.addedAtMs),
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
    }
}
