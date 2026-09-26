package com.qymusic.player.ui

import com.qymusic.player.data.PlayHistoryEntry
import com.qymusic.player.data.TrackPlaybackStats
import java.util.Calendar
import java.util.TimeZone

/** 统计页的时间范围：天 / 周 / 月 / 年。 */
enum class StatsRange {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

/** 热力图的一个格子。 */
data class StatsBucket(
    val startAt: Long,
    val listenedMs: Long,
    val plays: Long,
)

/** 当前范围下的统计数据。 */
data class RangeStats(
    val range: StatsRange = StatsRange.DAY,
    /** 当前查看的日期锚点，用来翻前后天/周/月/年。 */
    val anchorMs: Long = 0L,
    val rangeStartMs: Long = 0L,
    val rangeEndMs: Long = 0L,
    val plays: Long = 0L,
    val listenedMs: Long = 0L,
    val buckets: List<StatsBucket> = emptyList(),
    val topTracks: List<TrackPlaybackStats> = emptyList(),
)

/**
 * 把时间戳截断到该范围的格子边界：
 * 天 → 整点，周 / 月 → 当天零点，年 → 当月一号零点。
 */
fun statsBucketStartOf(
    range: StatsRange,
    timestampMs: Long,
    calendar: Calendar = Calendar.getInstance(),
): Long {
    calendar.timeInMillis = timestampMs
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    if (range != StatsRange.DAY) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
    }
    if (range == StatsRange.YEAR) {
        calendar.set(Calendar.DAY_OF_MONTH, 1)
    }
    return calendar.timeInMillis
}

/** 当前范围内热力图的全部格子起点（升序）。 */
fun statsBucketStarts(
    range: StatsRange,
    calendar: Calendar = Calendar.getInstance(),
): List<Long> {
    val cursor = calendar.clone() as Calendar
    val starts = mutableListOf<Long>()
    when (range) {
        StatsRange.DAY -> {
            // 今天的 24 个小时
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
            cursor.set(Calendar.SECOND, 0)
            cursor.set(Calendar.MILLISECOND, 0)
            repeat(24) {
                starts += cursor.timeInMillis
                cursor.add(Calendar.HOUR_OF_DAY, 1)
            }
        }

        StatsRange.WEEK -> {
            // 本周 7 天（周一到周日）
            // Calendar 里周日=1 … 周六=7，换算成距离本周一的天数。
            val daysFromMonday = (cursor.get(Calendar.DAY_OF_WEEK) + 5) % 7
            cursor.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
            cursor.set(Calendar.SECOND, 0)
            cursor.set(Calendar.MILLISECOND, 0)
            repeat(7) {
                starts += cursor.timeInMillis
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        StatsRange.MONTH -> {
            // 本月每天都列出，方便按星期排成日历格子
            cursor.set(Calendar.DAY_OF_MONTH, 1)
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
            cursor.set(Calendar.SECOND, 0)
            cursor.set(Calendar.MILLISECOND, 0)
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
            repeat(daysInMonth) {
                starts += cursor.timeInMillis
                cursor.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        StatsRange.YEAR -> {
            // 今年 12 个月
            cursor.set(Calendar.DAY_OF_MONTH, 1)
            cursor.set(Calendar.MONTH, Calendar.JANUARY)
            cursor.set(Calendar.HOUR_OF_DAY, 0)
            cursor.set(Calendar.MINUTE, 0)
            cursor.set(Calendar.SECOND, 0)
            cursor.set(Calendar.MILLISECOND, 0)
            repeat(12) {
                starts += cursor.timeInMillis
                cursor.add(Calendar.MONTH, 1)
            }
        }
    }
    return starts
}

/** 当前范围的起点（第一个格子的开始时间）。 */
fun statsRangeStart(
    range: StatsRange,
    calendar: Calendar = Calendar.getInstance(),
): Long = statsBucketStarts(range, calendar).firstOrNull() ?: 0L

/** 当前范围的结束时间（不含），即最后一个格子之后的边界。 */
fun statsRangeEnd(
    range: StatsRange,
    calendar: Calendar = Calendar.getInstance(),
): Long {
    val last = statsBucketStarts(range, calendar).lastOrNull() ?: return 0L
    val cursor = calendar.clone() as Calendar
    cursor.timeInMillis = last
    when (range) {
        StatsRange.DAY -> cursor.add(Calendar.HOUR_OF_DAY, 1)
        StatsRange.WEEK, StatsRange.MONTH -> cursor.add(Calendar.DAY_OF_MONTH, 1)
        StatsRange.YEAR -> cursor.add(Calendar.MONTH, 1)
    }
    return cursor.timeInMillis
}

/** 把日期锚点按当前范围前后移动若干段（天 / 周 / 月 / 年），用于翻页看别的日期。 */
fun shiftedStatsAnchor(
    range: StatsRange,
    anchorMs: Long,
    steps: Int,
    calendar: Calendar = Calendar.getInstance(),
): Long {
    if (steps == 0) return anchorMs
    calendar.timeInMillis = anchorMs
    when (range) {
        StatsRange.DAY -> calendar.add(Calendar.DAY_OF_MONTH, steps)
        StatsRange.WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, steps)
        StatsRange.MONTH -> calendar.add(Calendar.MONTH, steps)
        StatsRange.YEAR -> calendar.add(Calendar.YEAR, steps)
    }
    return calendar.timeInMillis
}

/** 锚点是否落在今天 / 本周 / 本月 / 今年，用来判断还能不能往后翻。 */
fun isStatsAnchorCurrent(
    range: StatsRange,
    anchorMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    calendar: Calendar = Calendar.getInstance(),
): Boolean {
    val anchor = calendar.clone() as Calendar
    anchor.timeInMillis = anchorMs
    val now = calendar.clone() as Calendar
    now.timeInMillis = nowMs
    return when (range) {
        StatsRange.DAY ->
            anchor.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                anchor.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        StatsRange.WEEK ->
            anchor.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                anchor.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)

        StatsRange.MONTH ->
            anchor.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                anchor.get(Calendar.MONTH) == now.get(Calendar.MONTH)

        StatsRange.YEAR -> anchor.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    }
}

/**
 * 日期选择器返回的是「所选日期的 UTC 零点」，这里换算成本地时区同一天的零点，
 * 否则东八区会整体往前挪一天。
 */
fun localDateFromUtcMillis(
    utcMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    return Calendar.getInstance(timeZone).apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
        )
    }.timeInMillis
}

/** 反过来：把本地某天的零点换算成日期选择器要的 UTC 零点。 */
fun utcDateMillisOf(
    localMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long {
    val local = Calendar.getInstance(timeZone).apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
    }.timeInMillis
}

/** 把播放历史按格子聚合，缺失的格子补 0。 */
fun aggregateStatsBuckets(
    range: StatsRange,
    starts: List<Long>,
    entries: List<PlayHistoryEntry>,
    calendar: Calendar = Calendar.getInstance(),
): List<StatsBucket> {
    if (starts.isEmpty()) return emptyList()
    val listenedByStart = HashMap<Long, Long>()
    val playsByStart = HashMap<Long, Long>()
    entries.forEach { entry ->
        val start = statsBucketStartOf(range, entry.startedAt, calendar)
        listenedByStart[start] = (listenedByStart[start] ?: 0L) + entry.listenedMs
        playsByStart[start] = (playsByStart[start] ?: 0L) + 1L
    }
    return starts.map { start ->
        StatsBucket(
            startAt = start,
            listenedMs = listenedByStart[start] ?: 0L,
            plays = playsByStart[start] ?: 0L,
        )
    }
}
