package com.qymusic.player.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import com.qymusic.player.data.OnlineRequestHeaders
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 在线音频的数据源链：
 *
 *   DefaultHttpDataSource（UA / 超时 / 跨协议重定向）
 *     → ResolvingDataSource（按 cacheKey 注入 Referer）
 *     → CacheDataSource（边播边写 SimpleCache，512KB 分片）
 *
 * CacheDataSource 把网络字节同时交给播放器和缓存文件，播放器只等 LoadControl 的
 * 起播缓冲，不会等整首；缓存 key 用 MediaItem 的 customCacheKey（songId|音质），
 * 不用带签名的完整 URL，否则直链一换就永远命中不了。
 */
@androidx.annotation.OptIn(UnstableApi::class)
class OnlineDataSourceFactory(
    private val context: Context,
    private val cache: SimpleCache,
) : DataSource.Factory {

    override fun createDataSource(): DataSource = SplitDataSource(context, cache)

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
            return try {
                val length = source.open(dataSpec)
                recordContentLength(dataSpec, source)
                length
            } catch (error: IOException) {
                active = null
                runCatching { source.close() }
                throw error
            }
        }

        private fun sourceFor(uri: Uri): DataSource {
            val scheme = uri.scheme.orEmpty().lowercase()
            if (scheme == "http" || scheme == "https") {
                httpSource?.let { return it }
                return createHttpSource().also { httpSource = it }
            }
            otherSource?.let { return it }
            return DefaultDataSource.Factory(context).createDataSource().also {
                otherSource = it
            }
        }

        private fun createHttpSource(): DataSource {
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(OnlineRequestHeaders.USER_AGENT)
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(OnlineRequestHeaders.defaultHeaders())

            // DataSpec.key 来自 MediaItem.customCacheKey；按它查来源并补 Referer。
            // 放在 ResolvingDataSource 里而不是改 DefaultHttpDataSource 的全局头，
            // 是因为同一播放器里不同平台的歌需要不同的 Referer。
            val resolver = ResolvingDataSource.Resolver { dataSpec ->
                val headers = OnlinePlaybackRegistry.headersFor(dataSpec.key)
                if (headers.isEmpty()) {
                    dataSpec
                } else {
                    dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
                }
            }
            val resolvingFactory = ResolvingDataSource.Factory(httpFactory, resolver)

            return CacheDataSource.Factory()
                .setCache(cache)
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory()
                        .setCache(cache)
                        // 默认分片是 5MB：播放中 getCachedBytes 会长时间为 0，
                        // 缓存列表就像“没有大小”。512KB 让进度接近实时可见，
                        // 也保证出声瞬间的 cachedBytes 远小于 1MB。
                        .setFragmentSize(CACHE_FRAGMENT_SIZE_BYTES),
                )
                .setCacheKeyFactory(
                    CacheKeyFactory { dataSpec ->
                        dataSpec.key?.takeIf(String::isNotBlank)
                            ?: dataSpec.uri.toString()
                    },
                )
                .setUpstreamDataSourceFactory(resolvingFactory)
                // 磁盘满 / 缓存写失败时继续走网络，保证“能播”优先。
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .createDataSource()
        }

        private fun recordContentLength(dataSpec: DataSpec, source: DataSource) {
            val key = dataSpec.key ?: return
            val total = totalBytesOf(source.responseHeaders) ?: return
            OnlinePlaybackRegistry.recordContentLength(key, total, source.uri?.toString())
            Log.i(CACHE_LOG_TAG, "contentLength key=$key total=${total}B")
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val source = active ?: throw IOException("数据源未打开")
            return source.read(buffer, offset, length)
        }

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            active?.responseHeaders.orEmpty()

        override fun close() {
            active?.close()
            active = null
        }
    }
}

private const val CONNECT_TIMEOUT_MS = 8_000
private const val READ_TIMEOUT_MS = 15_000
private const val CACHE_FRAGMENT_SIZE_BYTES = 512L * 1024L
private const val CACHE_LOG_TAG = "QYCache"

/**
 * 总长度优先取 Content-Range 的 total：拖动进度时会发 Range 请求，
 * 这时 Content-Length 只是本次区间长度，拿它当总长度会把缓存判成“快满了”。
 */
private fun totalBytesOf(headers: Map<String, List<String>>): Long? {
    val contentRange = headerValue(headers, "Content-Range")
    val totalFromRange = contentRange
        ?.substringAfterLast('/', "")
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
    if (totalFromRange != null) return totalFromRange
    return headerValue(headers, "Content-Length")
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0L }
}

private fun headerValue(headers: Map<String, List<String>>, name: String): String? =
    headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()
