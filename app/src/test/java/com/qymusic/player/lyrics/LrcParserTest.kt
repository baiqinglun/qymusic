package com.qymusic.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesAndSortsTimedLyrics() {
        val lyrics = LrcParser.parse(
            """
            [ti:Demo]
            [00:05.50]Second
            [00:01.00]First
            """.trimIndent(),
        )

        assertTrue(lyrics.isSynchronized)
        assertEquals(listOf("First", "Second"), lyrics.lines.map { it.text })
        assertEquals(listOf(1_000L, 5_500L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun appliesOffsetAndMergesSameTimestamp() {
        val lyrics = LrcParser.parse(
            """
            [offset:+200]
            [00:10.00]Original
            [00:10.00]Translation
            """.trimIndent(),
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals(10_200L, lyrics.lines.single().timeMs)
        assertEquals("Original\nTranslation", lyrics.lines.single().text)
    }

    @Test
    fun supportsMultipleTimestampTagsAndPlainLyrics() {
        val timed = LrcParser.parse("[00:01.00][00:03.000]Repeat")
        assertEquals(listOf(1_000L, 3_000L), timed.lines.map { it.timeMs })

        val plain = LrcParser.parse("第一行\n第二行")
        assertFalse(plain.isSynchronized)
        assertEquals(emptyList<LyricLine>(), plain.lines)
        assertEquals("第一行\n第二行", plain.rawText)
    }

    @Test
    fun supportsEnhancedInlineTimestamps() {
        val lyrics = LrcParser.parse("<00:27.183>在<00:28.100>心<00:28.405>底")

        assertTrue(lyrics.isSynchronized)
        assertEquals("在心底", lyrics.lines.single().text)
        assertEquals(27_183L, lyrics.lines.single().timeMs)
        assertEquals(listOf("在", "心", "底"), lyrics.lines.single().segments.map { it.text })
        assertEquals(
            listOf(27_183L, 28_100L, 28_405L),
            lyrics.lines.single().segments.map { it.timeMs },
        )
    }

    @Test
    fun findsActiveLineAtPlaybackBoundaries() {
        val lines = listOf(
            LyricLine(1_000L, "A"),
            LyricLine(2_000L, "B"),
            LyricLine(3_000L, "C"),
        )

        assertEquals(-1, LrcParser.findActiveLine(lines, 999L))
        assertEquals(0, LrcParser.findActiveLine(lines, 1_000L))
        assertEquals(1, LrcParser.findActiveLine(lines, 2_999L))
        assertEquals(2, LrcParser.findActiveLine(lines, 8_000L))
    }

    @Test
    fun appliesPerTrackOffsetToLinesAndSegments() {
        val lyrics = LrcParser.parse(
            "[00:01.00]<00:01.00>逐<00:01.50>字",
        ).withOffset(500L)

        assertEquals(1_500L, lyrics.lines.single().timeMs)
        assertEquals(listOf(1_500L, 2_000L), lyrics.lines.single().segments.map { it.timeMs })
    }
}
