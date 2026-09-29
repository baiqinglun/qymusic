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

/** 对已解析歌词统一应用每首歌自己的时间偏移。 */
fun Lyrics.withOffset(offsetMs: Long): Lyrics {
    if (!isSynchronized || offsetMs == 0L) return this
    return copy(
        lines = lines.map { line ->
            line.copy(
                timeMs = (line.timeMs + offsetMs).coerceAtLeast(0L),
                segments = line.segments.map { segment ->
                    segment.copy(
                        timeMs = (segment.timeMs + offsetMs).coerceAtLeast(0L),
                    )
                },
            )
        },
    )
}
