package com.qymusic.player.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class LxMusicSource(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val scriptUrl: String,
    val script: String,
    val importedAtMs: Long,
)

data class LxSourceStoreState(
    val sources: List<LxMusicSource> = emptyList(),
    val activeSourceId: String? = null,
)

data class LxSourceCapability(
    val key: String,
    val name: String,
    val type: String,
    val actions: Set<String>,
    val qualities: List<String>,
) {
    val supportsSearch: Boolean get() = actions.contains(ACTION_SEARCH)
    val supportsUrl: Boolean get() = actions.contains(ACTION_URL)

    companion object {
        const val ACTION_SEARCH = "musicSearch"
        const val ACTION_URL = "musicUrl"
    }
}

data class LxMusicSong(
    val id: String,
    val sourceKey: String,
    val sourceName: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String?,
    val rawJson: String,
)

data class LxSearchPage(
    val songs: List<LxMusicSong>,
    val isEnd: Boolean,
    val total: Int?,
)

class LxMusicSourceStore(context: Context) {
    private val sourceFile = File(context.filesDir, SOURCE_FILE_NAME)
    private val lock = Any()

    fun load(): LxSourceStoreState = synchronized(lock) {
        if (!sourceFile.isFile) return@synchronized LxSourceStoreState()
        runCatching {
            val root = JSONObject(sourceFile.readText(Charsets.UTF_8))
            val sourceArray = root.optJSONArray(KEY_SOURCES) ?: JSONArray()
            val sources = buildList {
                for (index in 0 until sourceArray.length()) {
                    val item = sourceArray.optJSONObject(index) ?: continue
                    val id = item.optString(KEY_ID).takeIf(String::isNotBlank) ?: continue
                    val url = item.optString(KEY_URL).takeIf(String::isNotBlank) ?: continue
                    val script = item.optString(KEY_SCRIPT).takeIf(String::isNotBlank) ?: continue
                    add(
                        LxMusicSource(
                            id = id,
                            name = item.optString(KEY_NAME).ifBlank { UNKNOWN_SOURCE_NAME },
                            description = item.optString(KEY_DESCRIPTION),
                            version = item.optString(KEY_VERSION),
                            scriptUrl = url,
                            script = script,
                            importedAtMs = item.optLong(KEY_IMPORTED_AT_MS),
                        ),
                    )
                }
            }
            val activeSourceId = root.optString(KEY_ACTIVE_SOURCE_ID)
                .takeIf { candidate -> sources.any { it.id == candidate } }
            LxSourceStoreState(sources = sources, activeSourceId = activeSourceId)
        }.getOrDefault(LxSourceStoreState())
    }

    fun save(state: LxSourceStoreState) = synchronized(lock) {
        val sourceArray = JSONArray()
        state.sources.forEach { source ->
            sourceArray.put(
                JSONObject()
                    .put(KEY_ID, source.id)
                    .put(KEY_NAME, source.name)
                    .put(KEY_DESCRIPTION, source.description)
                    .put(KEY_VERSION, source.version)
                    .put(KEY_URL, source.scriptUrl)
                    .put(KEY_SCRIPT, source.script)
                    .put(KEY_IMPORTED_AT_MS, source.importedAtMs),
            )
        }
        val root = JSONObject()
            .put(KEY_ACTIVE_SOURCE_ID, state.activeSourceId ?: JSONObject.NULL)
            .put(KEY_SOURCES, sourceArray)
        val tempFile = File(sourceFile.parentFile, "$SOURCE_FILE_NAME.tmp")
        tempFile.writeText(root.toString(), Charsets.UTF_8)
        tempFile.copyTo(sourceFile, overwrite = true)
        tempFile.delete()
    }

    companion object {
        fun buildSource(script: String, scriptUrl: String): LxMusicSource {
            val normalizedScript = script.trim()
            require(normalizedScript.isNotBlank()) { "音源脚本为空" }
            require(
                normalizedScript.contains("globalThis.lx") ||
                    normalizedScript.contains("lx.send") ||
                    normalizedScript.contains("EVENT_NAMES.request"),
            ) {
                "这不是有效的洛雪音乐源脚本"
            }
            return LxMusicSource(
                id = sha256(scriptUrl.trim()),
                name = readCommentTag(normalizedScript, "name")
                    ?: UNKNOWN_SOURCE_NAME,
                description = readCommentTag(normalizedScript, "description").orEmpty(),
                version = readCommentTag(normalizedScript, "version").orEmpty(),
                scriptUrl = scriptUrl.trim(),
                script = normalizedScript,
                importedAtMs = System.currentTimeMillis(),
            )
        }

        private fun readCommentTag(script: String, tag: String): String? =
            Regex("""@${Regex.escape(tag)}\s+([^\r\n*]+)""")
                .find(script)
                ?.groupValues
                ?.getOrNull(1)
                ?.trim()
                ?.trimEnd('/')
                ?.takeIf(String::isNotBlank)

        private fun sha256(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { byte -> "%02x".format(byte) }
        }

        const val UNKNOWN_SOURCE_NAME = "未命名音源"

        private const val SOURCE_FILE_NAME = "lx_music_sources.json"
        private const val KEY_SOURCES = "sources"
        private const val KEY_ACTIVE_SOURCE_ID = "activeSourceId"
        private const val KEY_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_VERSION = "version"
        private const val KEY_URL = "url"
        private const val KEY_SCRIPT = "script"
        private const val KEY_IMPORTED_AT_MS = "importedAtMs"
    }
}
