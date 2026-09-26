package com.qymusic.player.ui

import com.qymusic.player.data.PlayHistoryEntry
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class StatsModelsTest {
    private val zone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun calendarAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 30,
    ): Calendar = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }

    private fun fields(timestampMs: Long): Calendar = Calendar.getInstance(zone).apply {
        timeInMillis = timestampMs
    }

    @Test
    fun dayRangeHasHourBucketsFromMidnight() {
        val calendar = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val starts = statsBucketStarts(StatsRange.DAY, calendar)

        assertEquals(24, starts.size)
        assertEquals(0, fields(starts.first()).get(Calendar.HOUR_OF_DAY))
        assertEquals(23, fields(starts.last()).get(Calendar.HOUR_OF_DAY))
        assertEquals(24, fields(starts.first()).get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun weekRangeStartsOnMonday() {
        val calendar = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val starts = statsBucketStarts(StatsRange.WEEK, calendar)

        assertEquals(7, starts.size)
        assertEquals(Calendar.MONDAY, fields(starts.first()).get(Calendar.DAY_OF_WEEK))
        assertEquals(21, fields(starts.first()).get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun monthRangeCoversWholeMonth() {
        val calendar = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val starts = statsBucketStarts(StatsRange.MONTH, calendar)

        assertEquals(30, starts.size)
        assertEquals(1, fields(starts.first()).get(Calendar.DAY_OF_MONTH))
        assertEquals(30, fields(starts.last()).get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun yearRangeHasTwelveMonths() {
        val calendar = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val starts = statsBucketStarts(StatsRange.YEAR, calendar)

        assertEquals(12, starts.size)
        assertEquals(Calendar.JANUARY, fields(starts.first()).get(Calendar.MONTH))
        assertEquals(Calendar.DECEMBER, fields(starts.last()).get(Calendar.MONTH))
    }

    @Test
    fun bucketStartTruncatesByRange() {
        val calendar = calendarAt(2026, Calendar.SEPTEMBER, 24, 15, 42)
        val timestamp = calendar.timeInMillis

        val hour = fields(statsBucketStartOf(StatsRange.DAY, timestamp, calendar))
        assertEquals(15, hour.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, hour.get(Calendar.MINUTE))

        val day = fields(statsBucketStartOf(StatsRange.WEEK, timestamp, calendar))
        assertEquals(0, day.get(Calendar.HOUR_OF_DAY))
        assertEquals(24, day.get(Calendar.DAY_OF_MONTH))

        val month = fields(statsBucketStartOf(StatsRange.YEAR, timestamp, calendar))
        assertEquals(1, month.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SEPTEMBER, month.get(Calendar.MONTH))
    }

    @Test
    fun aggregateSumsPlayHistoryIntoSameBucket() {
        val calendar = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val starts = statsBucketStarts(StatsRange.DAY, calendar)
        val first = calendarAt(2026, Calendar.SEPTEMBER, 24, 9, 5).timeInMillis
        val second = calendarAt(2026, Calendar.SEPTEMBER, 24, 9, 40).timeInMillis

        val buckets = aggregateStatsBuckets(
            range = StatsRange.DAY,
            starts = starts,
            entries = listOf(
                PlayHistoryEntry(startedAt = first, listenedMs = 60_000L),
                PlayHistoryEntry(startedAt = second, listenedMs = 30_000L),
            ),
            calendar = calendar,
        )

        val nine = buckets.first { fields(it.startAt).get(Calendar.HOUR_OF_DAY) == 9 }
        assertEquals(90_000L, nine.listenedMs)
        assertEquals(2L, nine.plays)
        assertEquals(0L, buckets.first().listenedMs)
    }

    @Test
    fun rangeEndFollowsTheSelectedDate() {
        val day = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val dayEnd = fields(statsRangeEnd(StatsRange.DAY, day))
        assertEquals(25, dayEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, dayEnd.get(Calendar.HOUR_OF_DAY))

        val week = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val weekEnd = fields(statsRangeEnd(StatsRange.WEEK, week))
        assertEquals(28, weekEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, weekEnd.get(Calendar.DAY_OF_WEEK))

        val month = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val monthEnd = fields(statsRangeEnd(StatsRange.MONTH, month))
        assertEquals(Calendar.OCTOBER, monthEnd.get(Calendar.MONTH))
        assertEquals(1, monthEnd.get(Calendar.DAY_OF_MONTH))

        val year = calendarAt(2026, Calendar.SEPTEMBER, 24, 15)
        val yearEnd = fields(statsRangeEnd(StatsRange.YEAR, year))
        assertEquals(2027, yearEnd.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, yearEnd.get(Calendar.MONTH))
    }

    @Test
    fun anchorShiftsByOneRangeUnit() {
        val anchor = calendarAt(2026, Calendar.SEPTEMBER, 24, 15).timeInMillis

        val previousDay = fields(
            shiftedStatsAnchor(StatsRange.DAY, anchor, -1, calendar = Calendar.getInstance(zone)),
        )
        assertEquals(23, previousDay.get(Calendar.DAY_OF_MONTH))

        val previousWeek = fields(
            shiftedStatsAnchor(StatsRange.WEEK, anchor, -1, calendar = Calendar.getInstance(zone)),
        )
        assertEquals(17, previousWeek.get(Calendar.DAY_OF_MONTH))

        val previousMonth = fields(
            shiftedStatsAnchor(StatsRange.MONTH, anchor, -1, calendar = Calendar.getInstance(zone)),
        )
        assertEquals(Calendar.AUGUST, previousMonth.get(Calendar.MONTH))

        val nextYear = fields(
            shiftedStatsAnchor(StatsRange.YEAR, anchor, 1, calendar = Calendar.getInstance(zone)),
        )
        assertEquals(2027, nextYear.get(Calendar.YEAR))
    }

    @Test
    fun currentAnchorDetectionMatchesTheRange() {
        val now = calendarAt(2026, Calendar.SEPTEMBER, 24, 15).timeInMillis
        val sameDay = calendarAt(2026, Calendar.SEPTEMBER, 24, 8).timeInMillis
        val lastMonth = calendarAt(2026, Calendar.AUGUST, 24, 8).timeInMillis
        val lastYear = calendarAt(2025, Calendar.AUGUST, 24, 8).timeInMillis

        assertEquals(
            true,
            isStatsAnchorCurrent(StatsRange.DAY, sameDay, now, Calendar.getInstance(zone)),
        )
        assertEquals(
            false,
            isStatsAnchorCurrent(StatsRange.DAY, lastMonth, now, Calendar.getInstance(zone)),
        )
        assertEquals(
            true,
            isStatsAnchorCurrent(StatsRange.MONTH, sameDay, now, Calendar.getInstance(zone)),
        )
        assertEquals(
            false,
            isStatsAnchorCurrent(StatsRange.MONTH, lastMonth, now, Calendar.getInstance(zone)),
        )
        assertEquals(
            true,
            isStatsAnchorCurrent(StatsRange.YEAR, sameDay, now, Calendar.getInstance(zone)),
        )
        assertEquals(
            true,
            isStatsAnchorCurrent(StatsRange.YEAR, lastMonth, now, Calendar.getInstance(zone)),
        )
        assertEquals(
            false,
            isStatsAnchorCurrent(StatsRange.YEAR, lastYear, now, Calendar.getInstance(zone)),
        )
    }

    @Test
    fun utcPickerDateRoundTripsToLocalMidnight() {
        val local = calendarAt(2026, Calendar.SEPTEMBER, 24, 0, 0).timeInMillis
        val utc = utcDateMillisOf(local, zone)
        val back = localDateFromUtcMillis(utc, zone)
        val restored = fields(back)

        assertEquals(2026, restored.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, restored.get(Calendar.MONTH))
        assertEquals(24, restored.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, restored.get(Calendar.HOUR_OF_DAY))
    }
}
