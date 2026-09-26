package com.qymusic.player.lyrics

object LrcParser {
    private val bracketTimeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val inlineTimeTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val metadataTag = Regex("""^\[[a-zA-Z]+:.*]$""")

    fun parse(source: String): Lyrics {
        val normalized = source
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) {
            return Lyrics(emptyList(), "", false)
        }

        val offsetMs = offsetTag.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val timedLines = mutableListOf<LyricLine>()
        val plainLines = mutableListOf<String>()

        normalized.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            val lineWithoutBracketTags = bracketTimeTag.replace(line, "")
            val bracketTimestamps = bracketTimeTag.findAll(line).toList()
            val inlineTimestamps = inlineTimeTag.findAll(lineWithoutBracketTags).toList()
            val text = inlineTimeTag.replace(lineWithoutBracketTags, "").trim()
            if (bracketTimestamps.isEmpty() && inlineTimestamps.isEmpty()) {
                if (!metadataTag.matches(line)) {
                    plainLines += line
                }
                return@forEach
            }
            if (text.isEmpty()) return@forEach

            val timestamps = bracketTimestamps.ifEmpty { listOf(inlineTimestamps.first()) }
            val segments = buildSegments(lineWithoutBracketTags, inlineTimestamps, offsetMs)
            timestamps.forEach { match ->
                timedLines += LyricLine(
                    timeMs = (parseTimestamp(match) + offsetMs).coerceAtLeast(0L),
                    text = text,
                    segments = segments,
                )
            }
        }

        if (timedLines.isEmpty()) {
            return Lyrics(
                lines = emptyList(),
                rawText = normalized,
                isSynchronized = false,
            )
        }

        timedLines.sortBy { it.timeMs }
        val merged = linkedMapOf<Long, MutableList<LyricLine>>()
        timedLines.forEach { line ->
            val lines = merged.getOrPut(line.timeMs) { mutableListOf() }
            if (lines.none { it.text == line.text }) {
                lines += line
            }
        }
        return Lyrics(
            lines = merged.map { (timeMs, lines) ->
                LyricLine(
                    timeMs = timeMs,
                    text = lines.joinToString("\n") { it.text },
                    segments = lines.singleOrNull()?.segments.orEmpty(),
                )
            },
            rawText = normalized,
            isSynchronized = true,
        )
    }

    fun findActiveLine(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines.first().timeMs) return -1

        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].timeMs <= positionMs) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return result
    }

    fun containsTimestamp(source: String): Boolean =
        bracketTimeTag.containsMatchIn(source) || inlineTimeTag.containsMatchIn(source)

    private fun buildSegments(
        source: String,
        timestamps: List<MatchResult>,
        offsetMs: Long,
    ): List<LyricSegment> {
        if (timestamps.isEmpty()) return emptyList()
        return buildList {
            timestamps.forEachIndexed { index, timestamp ->
                val start = timestamp.range.last + 1
                val end = timestamps.getOrNull(index + 1)?.range?.first ?: source.length
                if (start < end) {
                    add(
                        LyricSegment(
                            timeMs = (parseTimestamp(timestamp) + offsetMs).coerceAtLeast(0L),
                            text = source.substring(start, end),
                        ),
                    )
                }
            }
        }
    }

    private fun parseTimestamp(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3]
        val milliseconds = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).padEnd(3, '0').toLong()
        }
        return minutes * 60_000L + seconds * 1_000L + milliseconds
    }
}
