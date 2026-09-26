package com.qymusic.player.lyrics

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val segments: List<LyricSegment> = emptyList(),
)

data class LyricSegment(
    val timeMs: Long,
    val text: String,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val rawText: String,
    val isSynchronized: Boolean,
)
