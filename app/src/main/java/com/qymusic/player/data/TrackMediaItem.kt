package com.qymusic.player.data

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Track → MediaItem 的统一映射：应用内播放与桌面组件冷启动恢复共用，
 * 保证两边建出来的队列完全一致（缓存键、元数据都走同一份逻辑）。
 */
fun Track.toPlaybackMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist.takeIf { it.isNotBlank() })
                .setAlbumTitle(album.takeIf { it.isNotBlank() })
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
    // ProgressiveMediaPeriod 会把 customCacheKey 写进 DataSpec.key，
    // CacheKeyFactory 据此返回 songId|音质；不能让它回落到完整 URL。
    cacheKey?.takeIf(String::isNotBlank)?.let(builder::setCustomCacheKey)
    return builder.build()
}
