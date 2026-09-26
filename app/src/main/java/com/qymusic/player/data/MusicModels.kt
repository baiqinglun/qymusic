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
    val extension: String
        get() = id.substringAfterLast('.', "").lowercase()
}

/** 扫描缓存：文件没变时直接复用，不用再读一遍媒体元数据。 */
data class CachedTrack(
    val track: Track,
    val fileSize: Long,
    val modifiedAt: Long,
)
