package com.qymusic.player.playback

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.CacheDataSource
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/** 试听缓存的进程级单例与操作入口：同一目录全应用只允许一个 SimpleCache 实例。 */
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

    /** 某个缓存键（流地址）已缓存的字节数。 */
    fun cachedBytes(context: Context, key: String): Long = runCatching {
        get(context).getCachedSpans(key).sumOf { it.length }
    }.getOrDefault(0L)

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

/**
 * 按 scheme 分流的数据源：http(s) 走缓存（CacheDataSource，边下边播并落盘），
 * 其它（本地 content / file）直连，不进缓存。
 */
class PreviewCacheDataSourceFactory(
    private val context: Context,
    private val cache: SimpleCache,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        SplitDataSource(context, cache)

    private class SplitDataSource(
        private val context: Context,
        private val cache: SimpleCache,
    ) : DataSource {
        private val listeners = CopyOnWriteArraySet<TransferListener>()
        private var active: DataSource? = null
        private var httpSource: DataSource? = null
        private var otherSource: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            listeners.add(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = sourceFor(dataSpec.uri)
            listeners.forEach(source::addTransferListener)
            active = source
            return source.open(dataSpec)
        }

        private fun sourceFor(uri: Uri): DataSource {
            val scheme = uri.scheme ?: ""
            if (scheme == "http" || scheme == "https") {
                httpSource?.let { return it }
                val http = CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(
                        DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .setUserAgent("QYMusic"),
                    )
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    .createDataSource()
                httpSource = http
                return http
            }
            otherSource?.let { return it }
            val local = DefaultDataSource.Factory(context).createDataSource()
            otherSource = local
            return local
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val source = active ?: throw IOException("数据源未打开")
            return source.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders ?: emptyMap()

        override fun close() {
            active?.close()
            active = null
        }
    }
}
