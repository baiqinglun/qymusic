package com.qymusic.player.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 片段类型预设：类型从预设里单选，标签是自由输入的自定义字段。 */
val CLIP_TYPES = listOf("前奏", "主歌", "副歌", "间奏", "尾奏")

/** 一条音乐片段：某首歌的 [startMs, endMs] 区间，带类型、名称与标签。 */
data class MusicClip(
    val id: String,
    val trackId: String,
    val name: String,
    val type: String = "",
    val tag: String = "",
    val startMs: Long,
    val endMs: Long,
    val createdAtMs: Long,
)

/** 一条片段播放规则：按歌曲名关键字 + 类型集合 + 标签集合筛选并连播。 */
data class ClipPlaybackRule(
    val id: String,
    val name: String,
    val query: String = "",
    val types: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    /** true = 类型与标签同时满足（与）；false = 满足其一即可（或）。 */
    val requireAll: Boolean = true,
    val createdAtMs: Long = 0L,
)

/**
 * 片段筛选：歌曲名关键字（包含、忽略大小写）+ 类型集合 + 标签集合。
 * 类型与标签都有值时按 [requireAll] 取「与 / 或」，只有其一时按该条件单独筛选。
 */
fun clipMatchesFilter(
    clip: MusicClip,
    trackTitle: String,
    query: String,
    types: List<String>,
    tags: List<String>,
    requireAll: Boolean,
): Boolean {
    val queryOk = query.isBlank() ||
        trackTitle.lowercase().contains(query.trim().lowercase())
    val typeOk = types.isEmpty() || clip.type in types
    val tagOk = tags.isEmpty() || clip.tag in tags
    val combined = when {
        types.isNotEmpty() && tags.isNotEmpty() ->
            if (requireAll) typeOk && tagOk else typeOk || tagOk
        types.isNotEmpty() -> typeOk
        tags.isNotEmpty() -> tagOk
        else -> true
    }
    return queryOk && combined
}

/** 片段播放时，播放页 / 播放条标题去掉尾部的起止位置后缀（队列里仍显示全名）。 */
fun stripClipRangeSuffix(title: String): String =
    title.replace(Regex(""" \d{1,2}:\d{2}\.\d~\d{1,2}:\d{2}\.\d$"""), "")

fun musicClipDisplayName(trackTitle: String, startMs: Long, endMs: Long): String {
    fun fmt(ms: Long): String {
        val total = ms / 1000
        val cents = (ms % 1000) / 100
        return String.format(java.util.Locale.US, "%d:%02d.%d", total / 60, total % 60, cents)
    }
    return trackTitle + " " + fmt(startMs) + "~" + fmt(endMs)
}

/**
 * 音乐片段仓库：持久化在 SharedPreferences（JSON 数组），
 * 新片段插到最前，列表按创建时间倒序展示。播放规则也在这里持久化。
 */
class MusicClipStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun all(): List<MusicClip> = runCatching {
        val array = JSONArray(prefs.getString(KEY_CLIPS, null).orEmpty())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val trackId = item.optString("trackId")
            if (id.isBlank() || trackId.isBlank()) return@mapNotNull null
            val tag = item.optString("tag")
            // 旧数据没有 type 字段：tag 命中类型预设的迁移为类型（tag 保留不丢数据）
            val storedType = item.optString("type")
            MusicClip(
                id = id,
                trackId = trackId,
                name = item.optString("name"),
                type = storedType.ifBlank {
                    CLIP_TYPES.firstOrNull { it == tag }.orEmpty()
                },
                tag = tag,
                startMs = item.optLong("startMs"),
                endMs = item.optLong("endMs"),
                createdAtMs = item.optLong("createdAtMs"),
            )
        }
    }.getOrDefault(emptyList())

    fun forTrack(trackId: String): List<MusicClip> =
        all().filter { it.trackId == trackId }

    fun upsert(clip: MusicClip) {
        val list = all().toMutableList()
        list.removeAll { it.id == clip.id }
        list.add(0, clip)
        save(list)
    }

    fun remove(clipId: String) {
        save(all().filterNot { it.id == clipId })
    }

    private fun save(list: List<MusicClip>) {
        val array = JSONArray()
        list.forEach { clip ->
            array.put(
                JSONObject()
                    .put("id", clip.id)
                    .put("trackId", clip.trackId)
                    .put("name", clip.name)
                    .put("type", clip.type)
                    .put("tag", clip.tag)
                    .put("startMs", clip.startMs)
                    .put("endMs", clip.endMs)
                    .put("createdAtMs", clip.createdAtMs),
            )
        }
        prefs.edit().putString(KEY_CLIPS, array.toString()).apply()
    }

    fun allRules(): List<ClipPlaybackRule> = runCatching {
        val array = JSONArray(prefs.getString(KEY_RULES, null).orEmpty())
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            ClipPlaybackRule(
                id = id,
                name = item.optString("name"),
                query = item.optString("query"),
                types = item.optString("types").split('|').filter { it.isNotBlank() },
                tags = item.optString("tags").split('|').filter { it.isNotBlank() },
                requireAll = item.optBoolean("requireAll", true),
                createdAtMs = item.optLong("createdAtMs"),
            )
        }
    }.getOrDefault(emptyList())

    fun upsertRule(rule: ClipPlaybackRule) {
        val list = allRules().toMutableList()
        list.removeAll { it.id == rule.id }
        list.add(0, rule)
        saveRules(list)
    }

    fun removeRule(ruleId: String) {
        saveRules(allRules().filterNot { it.id == ruleId })
    }

    private fun saveRules(list: List<ClipPlaybackRule>) {
        val array = JSONArray()
        list.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("query", rule.query)
                    .put("types", rule.types.joinToString("|"))
                    .put("tags", rule.tags.joinToString("|"))
                    .put("requireAll", rule.requireAll)
                    .put("createdAtMs", rule.createdAtMs),
            )
        }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "music_clip_store"
        const val KEY_CLIPS = "clips"
        const val KEY_RULES = "rules"
    }
}
