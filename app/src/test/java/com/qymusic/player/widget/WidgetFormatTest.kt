package com.qymusic.player.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetFormatTest {

    @Test
    fun `zero and negative clamp to 0_00`() {
        assertEquals("0:00", NowPlayingWidget.formatTimeMs(0L))
        assertEquals("0:00", NowPlayingWidget.formatTimeMs(-1L))
        // ExoPlayer 的 TIME_UNSET = Long.MIN_VALUE + 1，组件侧把它当"未知时长"处理
        assertEquals("0:00", NowPlayingWidget.formatTimeMs(Long.MIN_VALUE + 1))
    }

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("0:59", NowPlayingWidget.formatTimeMs(59_999L))
        assertEquals("1:00", NowPlayingWidget.formatTimeMs(60_000L))
        assertEquals("3:21", NowPlayingWidget.formatTimeMs(201_000L))
    }

    @Test
    fun `over an hour keeps accumulating minutes`() {
        assertEquals("75:32", NowPlayingWidget.formatTimeMs(4_532_000L))
    }
}
