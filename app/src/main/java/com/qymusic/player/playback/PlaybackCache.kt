package com.qymusic.player.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** 在线音频缓存的进程级单例与操作入口：同一目录全应用只允许一个 SimpleCache 实例。 */
@androidx.annotation.OptIn(UnstableApi::class)
object PlaybackCache {
    private const val DIR = "preview_audio_cache"
    private const val MAX_BYTES = 256L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, DIR),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context),
        ).also { cache = it }
    }

    /**
     * 播放器的数据源工厂。缓存 key 来自 MediaItem.customCacheKey（songId|音质），
     * 不是完整 URL；这样平台重新签名、换直链后仍能命中同一份缓存。
     */
    fun dataSourceFactory(context: Context): DataSource.Factory =
        OnlineDataSourceFactory(context.applicationContext, get(context))

    /**
     * 某个缓存 key 已写入的字节数。
     * contentLength 未知时传默认的 C.LENGTH_UNSET，API 会统计到第一个空洞为止。
     */
    fun cachedBytes(
        context: Context,
        key: String,
        contentLength: Long = -1L,
    ): Long = runCatching {
        get(context).getCachedBytes(key, 0, contentLength)
    }.getOrDefault(0L)

    /** 缓存目录当前总占用，用于缓存页汇总；比把各条 DB 记录相加更准。 */
    fun cacheSpace(context: Context): Long = runCatching {
        get(context).getCacheSpace()
    }.getOrDefault(0L)

    fun keys(context: Context): Set<String> = runCatching {
        get(context).keys
    }.getOrDefault(emptySet())

    fun remove(context: Context, key: String) {
        runCatching { get(context).removeResource(key) }
    }

    fun clear(context: Context) {
        runCatching {
            val simpleCache = get(context)
            simpleCache.keys.forEach { key -> simpleCache.removeResource(key) }
        }
    }
}
