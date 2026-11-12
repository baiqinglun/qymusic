package com.qymusic.player.playback

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 一条在线播放记录。
 *
 * 数据源层在 open 时只有 DataSpec.key，拿不到 LxMusicSong；把解析阶段已知的信息
 * 按 cacheKey 登记在这里，播放线程就能补 Referer、记录 Content-Length，后续 S3/S4
 * 还能用它做完整缓存命中判断和 403 重解析。
 */
data class OnlinePlaybackRecord(
    val cacheKey: String,
    val trackId: String,
    val songId: String,
    val sourceKey: String,
    val quality: String,
    val url: String,
    val title: String,
    val artist: String,
    val album: String,
    val declaredDurationMs: Long,
    val coverUrl: String?,
    val rawJson: String,
    val headers: Map<String, String>,
    /** 响应头拿到的真实总字节；-1 表示未知。 */
    val contentLengthBytes: Long = -1L,
    val resolvedAtMs: Long = System.currentTimeMillis(),
)

/**
 * 在线播放的进程级注册表 + 起播打点。
 *
 * 同一进程内 MediaSessionService 与 ViewModel 共用这一份，Service 侧不需要再持有
 * 歌曲对象就能把 cacheKey 对应回来源和请求头。所有方法都可在任意线程调用。
 */
object OnlinePlaybackRegistry {
    private const val LOG_TAG = "QYStart"

    private val records = ConcurrentHashMap<String, OnlinePlaybackRecord>()
    private val traces = ConcurrentHashMap<String, StartupTrace>()

    @Volatile
    private var currentCacheKey: String? = null

    /** 在线曲目 id：与 MusicViewModel.ONLINE_TRACK_ID_PREFIX 的拼接方式保持一致。 */
    fun trackId(sourceKey: String, songId: String): String = "online:$sourceKey:$songId"

    /**
     * 缓存 key 固定为 songId|音质，绝不能回落成完整 URL：
     * 平台直链带签名、几十分钟就过期，用 URL 做 key 会导致永远命中不了。
     */
    fun cacheKey(sourceKey: String, songId: String, quality: String): String =
        "${trackId(sourceKey, songId)}|$quality"

    fun register(record: OnlinePlaybackRecord) {
        // 同一首重播时新的解析结果没有 Content-Length（缓存命中不会打开上游），
        // 但上一次播放已经探测到了；保留它，避免日志/缓存页的总大小又变回未知。
        val previous = records[record.cacheKey]
        val merged = if (
            previous != null &&
            previous.contentLengthBytes > 0L &&
            record.contentLengthBytes <= 0L
        ) {
            record.copy(contentLengthBytes = previous.contentLengthBytes)
        } else {
            record
        }
        records[record.cacheKey] = merged
        currentCacheKey = record.cacheKey
    }

    fun find(cacheKey: String?): OnlinePlaybackRecord? =
        cacheKey?.let(records::get)

    fun headersFor(cacheKey: String?): Map<String, String> =
        find(cacheKey)?.headers.orEmpty()

    /**
     * 同一进程内复用刚解析过的直链：第二次点同一首不必再等 1–2 秒的 JS 解析。
     * 平台直链有有效期，所以只复用 maxAgeMs 内的；过期后由 S4 的自愈重解析兜底。
     */
    fun findFresh(cacheKey: String, maxAgeMs: Long): OnlinePlaybackRecord? {
        val record = records[cacheKey] ?: return null
        if (record.url.isBlank()) return null
        if (System.currentTimeMillis() - record.resolvedAtMs > maxAgeMs) return null
        return record
    }

    /** 播放失败（典型是直链过期 403）时丢掉复用记录，下次点击强制重新解析。 */
    fun invalidate(cacheKey: String?) {
        if (cacheKey.isNullOrBlank()) return
        records.remove(cacheKey)
        if (currentCacheKey == cacheKey) {
            currentCacheKey = null
        }
    }

    /**
     * 只让直链失效，保留歌曲/音质/总长度信息：
     * findFresh 不会再复用空 URL，但错误 banner 的重试仍能拿回歌曲重新解析。
     */
    fun invalidateUrl(cacheKey: String?) {
        if (cacheKey.isNullOrBlank()) return
        records.computeIfPresent(cacheKey) { _, old ->
            old.copy(url = "", resolvedAtMs = 0L)
        }
    }

    fun currentCacheKey(): String? = currentCacheKey

    /** 记录解析到的真实总长度；CacheDataSource open 之后由数据源层调用。 */
    fun recordContentLength(cacheKey: String?, totalBytes: Long, url: String?) {
        if (cacheKey.isNullOrBlank() || totalBytes <= 0L) return
        records.computeIfPresent(cacheKey) { _, old ->
            old.copy(
                contentLengthBytes = totalBytes,
                url = url?.takeIf(String::isNotBlank) ?: old.url,
            )
        }
    }

    /**
     * 点击时打点。这里只记时间，真正的 READY / 首帧日志由 PlaybackService 的
     * AnalyticsListener 读取；两个类都在进程内，不需要跨进程传参。
     */
    fun markClick(trackId: String, cacheKey: String) {
        traces[trackId] = StartupTrace(clickAtMs = SystemClock.elapsedRealtime())
        Log.i(LOG_TAG, "CLICK track=$trackId key=$cacheKey")
    }

    /**
     * READY 和真正出声是两个时刻，各打一次；重复事件不会重复刷日志。
     * cachedBytes 在出声时应当远小于总长度，用来证明“没有等整首下完”。
     */
    fun logStage(trackId: String?, stage: String, cachedBytes: Long, totalBytes: Long) {
        val id = trackId ?: return
        val trace = traces[id] ?: return
        if (!trace.markLogged(stage)) return
        val elapsedMs = SystemClock.elapsedRealtime() - trace.clickAtMs
        val totalText = if (totalBytes > 0L) "${totalBytes}B" else "unknown"
        Log.i(LOG_TAG, "$stage +${elapsedMs}ms cached=${cachedBytes}B total=$totalText track=$id")
    }

    fun clearTrace(trackId: String) {
        traces.remove(trackId)
    }

    private class StartupTrace(val clickAtMs: Long) {
        private val loggedStages = ConcurrentHashMap.newKeySet<String>()

        fun markLogged(stage: String): Boolean = loggedStages.add(stage)
    }
}
