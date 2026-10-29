package com.qymusic.player.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MetadataPluginState(
    val plugins: List<MetadataPlugin>,
    /** 歌词 / 封面查找的优先顺序，未列出的内置插件排在其后。 */
    val order: List<String>,
    val disabled: Set<String>,
)

/**
 * 元数据插件仓库：内置插件读 assets，用户导入的插件持久化在
 * filesDir/metadata_plugins.json；启用与排序保存在 SharedPreferences。
 */
class MetadataPluginStore(context: Context) {
    private val appContext = context.applicationContext
    private val pluginFile = File(appContext.filesDir, STORE_FILE_NAME)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    fun load(): MetadataPluginState = synchronized(lock) {
        val imported = loadImported()
        val builtins = loadBuiltins()
        val byId = LinkedHashMap<String, MetadataPlugin>()
        (builtins + imported).forEach { plugin ->
            byId[plugin.id] = plugin
        }
        val savedOrder = preferences.getString(KEY_ORDER, null)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        val order = (savedOrder + byId.keys).distinct().filter(byId::containsKey)
        val disabled = preferences.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()
        MetadataPluginState(
            plugins = order.mapNotNull(byId::get),
            order = order,
            disabled = disabled,
        )
    }

    fun orderedPlugins(state: MetadataPluginState): List<MetadataPlugin> {
        val byId = state.plugins.associateBy(MetadataPlugin::id)
        return (state.order + state.plugins.map(MetadataPlugin::id))
            .distinct()
            .mapNotNull(byId::get)
    }

    fun isEnabled(state: MetadataPluginState, pluginId: String): Boolean =
        pluginId !in state.disabled

    fun setEnabled(pluginId: String, enabled: Boolean) = synchronized(lock) {
        preferences.edit {
            val current = preferences.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()
            putStringSet(
                KEY_DISABLED,
                if (enabled) current - pluginId else current + pluginId,
            )
        }
    }

    fun moveUp(pluginId: String) = synchronized(lock) {
        val order = currentOrder().toMutableList()
        val index = order.indexOf(pluginId)
        if (index > 0) {
            order.removeAt(index)
            order.add(index - 1, pluginId)
            saveOrder(order)
        }
    }

    fun moveDown(pluginId: String) = synchronized(lock) {
        val order = currentOrder().toMutableList()
        val index = order.indexOf(pluginId)
        if (index in 0 until order.lastIndex) {
            order.removeAt(index)
            order.add(index + 1, pluginId)
            saveOrder(order)
        }
    }

    fun import(plugin: MetadataPlugin): MetadataPlugin = synchronized(lock) {
        val saved = loadImported()
        val updated = saved.filterNot { it.id == plugin.id } + plugin
        saveImported(updated)
        val order = currentOrder().toMutableList()
        if (!order.contains(plugin.id)) {
            order.add(plugin.id)
            saveOrder(order)
        }
        plugin
    }

    /** 同 id 且 versionCode 不低于现有版本时覆盖导入，返回导入结果。 */
    fun importPackage(pkg: MetadataPluginPackage, builtin: Boolean = false): MetadataPlugin {
        val plugin = MetadataPlugin(
            manifest = pkg.manifest,
            script = pkg.script,
            builtin = builtin,
            importedAtMs = System.currentTimeMillis(),
        )
        if (builtin) {
            val order = currentOrder().toMutableList()
            if (!order.contains(plugin.id)) {
                order.add(plugin.id)
                saveOrder(order)
            }
            return plugin
        }
        return import(plugin)
    }

    fun delete(pluginId: String) = synchronized(lock) {
        val saved = loadImported()
        saveImported(saved.filterNot { it.id == pluginId })
        preferences.edit {
            val current = preferences.getStringSet(KEY_DISABLED, emptySet()).orEmpty().toSet()
            putStringSet(KEY_DISABLED, current - pluginId)
        }
        val order = currentOrder().filterNot { it == pluginId }
        saveOrder(order)
    }

    private fun currentOrder(): List<String> =
        preferences.getString(KEY_ORDER, null)
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()

    private fun saveOrder(order: List<String>) {
        preferences.edit { putString(KEY_ORDER, order.joinToString(",")) }
    }

    private fun loadBuiltins(): List<MetadataPlugin> {
        val assets = runCatching { appContext.assets.list(ASSET_ROOT) }.getOrNull()
            ?: return emptyList()
        return assets.mapNotNull { dirName ->
            runCatching {
                val manifestText = String(
                    appContext.assets.open("$ASSET_ROOT/$dirName/manifest.json").use { it.readBytes() },
                    Charsets.UTF_8,
                )
                val script = String(
                    appContext.assets.open("$ASSET_ROOT/$dirName/source.js").use { it.readBytes() },
                    Charsets.UTF_8,
                )
                val manifest = MetadataPluginParser.fromText(manifestText)
                MetadataPlugin(
                    manifest = manifest,
                    script = script,
                    builtin = true,
                    importedAtMs = 0L,
                )
            }.onFailure { error ->
                android.util.Log.w(LOG_TAG, "内置插件 $dirName 加载失败: ${error.message}")
            }.getOrNull()
        }.sortedBy { dirPriority(it.manifest.id) }
    }

    /** 默认优先级：国内源可靠在前，苹果与 LRCLIB 兜底在后。 */
    private fun dirPriority(pluginId: String): Int =
        BUILTIN_PRIORITY.indexOf(pluginId).let { if (it >= 0) it else BUILTIN_PRIORITY.size }

    private fun loadImported(): List<MetadataPlugin> {
        if (!pluginFile.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(pluginFile.readText(Charsets.UTF_8))
            val array = root.optJSONArray(KEY_PLUGINS) ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val manifestJson = item.optJSONObject(KEY_MANIFEST) ?: continue
                    val script = item.optString(KEY_SCRIPT).takeIf(String::isNotBlank) ?: continue
                    runCatching {
                        add(
                            MetadataPlugin(
                                manifest = MetadataPluginManifest.parse(manifestJson),
                                script = script,
                                builtin = false,
                                importedAtMs = item.optLong(KEY_IMPORTED_AT_MS),
                            ),
                        )
                    }
                }
            }
        }.onFailure { error ->
            android.util.Log.w(LOG_TAG, "导入插件读取失败: ${error.message}")
        }.getOrDefault(emptyList())
    }

    private fun saveImported(plugins: List<MetadataPlugin>) {
        val array = JSONArray()
        plugins.forEach { plugin ->
            val manifest = JSONObject()
                .put("id", plugin.manifest.id)
                .put("name", plugin.manifest.name)
                .put("versionCode", plugin.manifest.versionCode)
                .put("versionName", plugin.manifest.versionName)
                .put("apiVersion", plugin.manifest.apiVersion)
                .put("minHostApiVersion", plugin.manifest.minHostApiVersion)
                .put("author", plugin.manifest.author)
                .put("description", plugin.manifest.description)
                .put("entry", plugin.manifest.entry)
                .put("includeDirs", JSONArray(plugin.manifest.includeDirs))
                .put("capabilities", JSONArray(plugin.manifest.capabilities.toList()))
            array.put(
                JSONObject()
                    .put(KEY_MANIFEST, manifest)
                    .put(KEY_SCRIPT, plugin.script)
                    .put(KEY_IMPORTED_AT_MS, plugin.importedAtMs),
            )
        }
        val root = JSONObject().put(KEY_PLUGINS, array)
        val temp = File(pluginFile.parentFile, "$STORE_FILE_NAME.tmp")
        temp.writeText(root.toString(), Charsets.UTF_8)
        temp.copyTo(pluginFile, overwrite = true)
        temp.delete()
    }

    private companion object {
        const val LOG_TAG = "QYMetadataPlugin"
        const val STORE_FILE_NAME = "metadata_plugins.json"
        const val PREFERENCES_NAME = "metadata_plugins"
        const val KEY_ORDER = "order"
        const val KEY_DISABLED = "disabled"
        const val ASSET_ROOT = "metadata-plugins"
        const val KEY_PLUGINS = "plugins"
        const val KEY_MANIFEST = "manifest"
        const val KEY_SCRIPT = "script"
        const val KEY_IMPORTED_AT_MS = "importedAtMs"

        val BUILTIN_PRIORITY = listOf(
            "com.qymusic.kugou",
            "com.qymusic.netease",
            "com.qymusic.kuwo",
            "com.qymusic.qq",
            "com.qymusic.apple",
            "com.qymusic.lrclib",
        )
    }
}
