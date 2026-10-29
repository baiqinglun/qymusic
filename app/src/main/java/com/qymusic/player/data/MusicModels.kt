package com.qymusic.player.data

import android.net.Uri

const val QUALITY_HI_RES = "Hi-Res"
const val QUALITY_SQ = "SQ"
const val QUALITY_HQ = "HQ"
const val QUALITY_STANDARD = "标准"

data class MusicFolder(
    val uri: Uri,
    val name: String,
)

data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val folderName: String,
    val relativePath: String,
    val lyricUri: Uri?,
    /** 品质等级：Hi-Res / SQ / HQ / 标准 */
    val quality: String,
    val bitrateKbps: Int = 0,
    val bitDepth: Int = 0,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
) {
    /** 扩展名从 uri 取：片段轨道的 id 是 clip:uuid（无扩展名），按 id 推导会得到空串，导致内嵌歌词等按扩展名分支的逻辑失效。 */
    val extension: String
        get() = (uri.lastPathSegment ?: id)
            .substringAfterLast('.', "")
            .substringBefore('?')
            .lowercase()
}

/** 扫描缓存：文件没变时直接复用，不用再读一遍媒体元数据。 */
data class CachedTrack(
    val track: Track,
    val fileSize: Long,
    val modifiedAt: Long,
)
