package com.qymusic.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 管理全部元数据插件的运行时：按需为每个插件创建 WebView 运行时，
 * 提供按用户排序的歌词 / 封面插件队列。
 */
class MetadataPluginManager(
    context: Context,
    private val store: MetadataPluginStore,
) : MetadataPluginInvoker {
    private val appContext = context.applicationContext
    private val runtimes = LinkedHashMap<String, MetadataPluginRuntime>()
    private val lock = Any()

    private fun currentState(): MetadataPluginState = store.load()

    fun allPlugins(): List<MetadataPlugin> = currentState().plugins

    override fun enabledPlugins(): List<MetadataPlugin> {
        val state = currentState()
        return state.plugins.filter { store.isEnabled(state, it.id) }
    }

    override suspend fun invoke(
        plugin: MetadataPlugin,
        functionName: String,
        request: JSONObject,
    ): Any? {
        val state = currentState()
        if (!store.isEnabled(state, plugin.id)) {
            throw IllegalStateException("插件 ${plugin.name} 已停用")
        }
        val runtime = obtainRuntime(plugin)
        return runtime.invoke(functionName, request)
    }

    private suspend fun obtainRuntime(plugin: MetadataPlugin): MetadataPluginRuntime {
        val existing = synchronized(lock) { runtimes[plugin.id] }
        if (existing != null) return existing
        val runtime = MetadataPluginRuntime(appContext, plugin.id)
        withContext(Dispatchers.Main.immediate) {
            runtime.load(plugin)
        }
        synchronized(lock) {
            runtimes[plugin.id]?.release()
            runtimes[plugin.id] = runtime
        }
        return runtime
    }

    fun release(pluginId: String) {
        synchronized(lock) {
            runtimes.remove(pluginId)?.release()
        }
    }

    fun releaseAll() {
        synchronized(lock) {
            runtimes.values.forEach(MetadataPluginRuntime::release)
            runtimes.clear()
        }
    }
}
