package com.qymusic.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.TrackPlaybackStats
import java.util.Calendar

@Composable
fun StatisticsScreen(
    rangeStats: RangeStats,
    statsRange: StatsRange,
    statsAnchorMs: Long,
    trackStats: List<TrackPlaybackStats>,
    onSelectRange: (StatsRange) -> Unit,
    onShiftAnchor: (Int) -> Unit,
    onSelectAnchor: (Long) -> Unit,
    onResetAnchor: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) {
        onRefresh()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item { StatisticsTopBar(onBack = onBack) }
                item {
                    StatsPeriodBar(
                        range = statsRange,
                        anchorMs = statsAnchorMs,
                        onShift = onShiftAnchor,
                        onPickDate = onSelectAnchor,
                        onReset = onResetAnchor,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                    )
                }
                item {
                    StatsRangeTabs(
                        range = statsRange,
                        onSelect = onSelectRange,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 12.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SummaryTile(
                            label = stringResource(R.string.total_plays),
                            value = rangeStats.plays.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        SummaryTile(
                            label = stringResource(R.string.total_duration),
                            value = formatDuration(rangeStats.listenedMs),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item {
                    StatisticsSectionTitle(
                        text = stringResource(R.string.stats_heatmap),
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                }
                item {
                    StatsHeatmap(
                        range = statsRange,
                        buckets = rangeStats.buckets,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                item {
                    StatisticsSectionTitle(
                        text = stringResource(R.string.stats_top_tracks),
                        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
                    )
                }
                val maxPlays = rangeStats.topTracks.maxOfOrNull { it.playCount } ?: 1L
                if (rangeStats.topTracks.isEmpty()) {
                    item { StatisticsEmptyHint() }
                } else {
                    itemsIndexed(
                        items = rangeStats.topTracks,
                        key = { _, stat -> "top-${stat.trackId}" },
                    ) { index, stat ->
                        TopTrackRow(rank = index + 1, stat = stat, maxPlays = maxPlays)
                        StatisticsDivider()
                    }
                }

                item {
                    StatisticsSectionTitle(
                        text = stringResource(R.string.stats_all_tracks),
                        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
                    )
                }
                if (trackStats.isEmpty()) {
                    item { StatisticsEmptyHint() }
                } else {
                    items(items = trackStats, key = { it.trackId }) { stat ->
                        StatisticsRow(stat = stat)
                        StatisticsDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }
        Text(
            text = stringResource(R.string.playback_statistics),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 日期导航：◀ 上一段 · 点中间选日期 · 下一段 ▶（已经是最近一段时禁用）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsPeriodBar(
    range: StatsRange,
    anchorMs: Long,
    onShift: (Int) -> Unit,
    onPickDate: (Long) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val isCurrent = remember(range, anchorMs) { isStatsAnchorCurrent(range, anchorMs) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onShift(-1) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronLeft,
                    contentDescription = stringResource(R.string.stats_prev_period),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        showDatePicker = true
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = statsPeriodLabel(range = range, anchorMs = anchorMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!isCurrent) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.stats_back_to_now),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = onReset,
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(
                onClick = { onShift(1) },
                enabled = !isCurrent,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.stats_next_period),
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember(anchorMs) { utcDateMillisOf(anchorMs) },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { utcMillis ->
                            onPickDate(localDateFromUtcMillis(utcMillis))
                        }
                        showDatePicker = false
                    },
                ) {
                    Text(text = stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun statsPeriodLabel(range: StatsRange, anchorMs: Long): String {
    val calendar = remember(anchorMs) {
        Calendar.getInstance().apply { timeInMillis = anchorMs }
    }
    return when (range) {
        StatsRange.DAY -> stringResource(
            R.string.stats_period_day,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        )

        StatsRange.WEEK -> {
            val starts = remember(range, anchorMs) {
                statsBucketStarts(range, Calendar.getInstance().apply { timeInMillis = anchorMs })
            }
            val start = remember(starts) { Calendar.getInstance().apply { timeInMillis = starts.first() } }
            val end = remember(starts) { Calendar.getInstance().apply { timeInMillis = starts.last() } }
            stringResource(
                R.string.stats_period_week,
                start.get(Calendar.MONTH) + 1,
                start.get(Calendar.DAY_OF_MONTH),
                end.get(Calendar.MONTH) + 1,
                end.get(Calendar.DAY_OF_MONTH),
            )
        }

        StatsRange.MONTH -> stringResource(
            R.string.stats_period_month,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
        )

        StatsRange.YEAR -> stringResource(
            R.string.stats_period_year,
            calendar.get(Calendar.YEAR),
        )
    }
}

@Composable
private fun StatisticsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 20.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatisticsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
    )
}

@Composable
private fun StatisticsEmptyHint() {
    Text(
        text = stringResource(R.string.no_statistics),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 天 / 周 / 月 / 年 切换。 */
@Composable
private fun StatsRangeTabs(
    range: StatsRange,
    onSelect: (StatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { option ->
            val selected = option == range
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Tab) {
                        onSelect(option)
                    },
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(option.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private fun StatsRange.labelRes(): Int = when (this) {
    StatsRange.DAY -> R.string.stats_range_day
    StatsRange.WEEK -> R.string.stats_range_week
    StatsRange.MONTH -> R.string.stats_range_month
    StatsRange.YEAR -> R.string.stats_range_year
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------- 热力图 ----------------

@Composable
private fun StatsHeatmap(
    range: StatsRange,
    buckets: List<StatsBucket>,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) {
        StatisticsEmptyHint()
        return
    }
    val maxListened = buckets.maxOf { it.listenedMs }.coerceAtLeast(1L)
    Column(modifier = modifier.fillMaxWidth()) {
        when (range) {
            StatsRange.DAY -> HourHeatmap(buckets, maxListened)
            StatsRange.WEEK -> WeekHeatmap(buckets, maxListened)
            StatsRange.MONTH -> MonthHeatmap(buckets, maxListened)
            StatsRange.YEAR -> YearHeatmap(buckets, maxListened)
        }
        Spacer(modifier = Modifier.height(10.dp))
        HeatmapLegend()
    }
}

/** 今天 24 个小时：一条 24 格的横条。 */
@Composable
private fun HourHeatmap(buckets: List<StatsBucket>, maxListened: Long) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            buckets.forEach { bucket ->
                HeatCell(
                    level = heatLevel(bucket.listenedMs, maxListened),
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "23").forEach { hour ->
                Text(
                    text = hour,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 本周 7 天：格子下面标星期。 */
@Composable
private fun WeekHeatmap(buckets: List<StatsBucket>, maxListened: Long) {
    val weekdays = stringArrayResource(R.array.stats_weekdays)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        buckets.forEachIndexed { index, bucket ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeatCell(
                    level = heatLevel(bucket.listenedMs, maxListened),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = weekdays.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 本月按星期排成日历格子，格子里写日期。 */
@Composable
private fun MonthHeatmap(buckets: List<StatsBucket>, maxListened: Long) {
    val calendar = remember { Calendar.getInstance() }
    val leadingBlanks = remember(buckets) {
        val first = buckets.firstOrNull() ?: return@remember 0
        calendar.timeInMillis = first.startAt
        // Calendar 里周日=1，这里换算成周一=0
        (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
    val cells = remember(buckets, leadingBlanks) {
        buildList<StatsBucket?> {
            repeat(leadingBlanks) { add(null) }
            addAll(buckets)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { bucket ->
                    if (bucket == null) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp),
                        )
                    } else {
                        HeatCell(
                            level = heatLevel(bucket.listenedMs, maxListened),
                            label = dayOfMonth(bucket.startAt, calendar),
                            modifier = Modifier
                                .weight(1f)
                                .height(30.dp),
                        )
                    }
                }
                repeat(7 - week.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp),
                    )
                }
            }
        }
    }
}

/** 今年 12 个月：两行六列，格子里写月份。 */
@Composable
private fun YearHeatmap(buckets: List<StatsBucket>, maxListened: Long) {
    val calendar = remember { Calendar.getInstance() }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        buckets.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { bucket ->
                    HeatCell(
                        level = heatLevel(bucket.listenedMs, maxListened),
                        label = monthOf(bucket.startAt, calendar),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                    )
                }
                repeat(6 - row.size) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    level: Int,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val color = if (level <= 0) {
        scheme.surfaceVariant
    } else {
        scheme.primary.copy(alpha = HEAT_LEVEL_ALPHAS[level.coerceIn(1, 4)])
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (level >= 3) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeatmapLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(R.string.stats_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        (0..4).forEach { level ->
            HeatCell(
                level = level,
                modifier = Modifier
                    .padding(end = 3.dp)
                    .width(14.dp)
                    .height(14.dp),
            )
        }
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.stats_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun heatLevel(listenedMs: Long, maxListened: Long): Int {
    if (listenedMs <= 0L || maxListened <= 0L) return 0
    val ratio = listenedMs.toFloat() / maxListened.toFloat()
    return (ratio * 4f).toInt().coerceIn(1, 4)
}

private fun dayOfMonth(timestampMs: Long, calendar: Calendar): String {
    calendar.timeInMillis = timestampMs
    return calendar.get(Calendar.DAY_OF_MONTH).toString()
}

private fun monthOf(timestampMs: Long, calendar: Calendar): String {
    calendar.timeInMillis = timestampMs
    return (calendar.get(Calendar.MONTH) + 1).toString()
}

private val HEAT_LEVEL_ALPHAS = listOf(0f, 0.25f, 0.45f, 0.7f, 0.95f)

// ---------------- 列表 ----------------

@Composable
private fun TopTrackRow(
    rank: Int,
    stat: TrackPlaybackStats,
    maxPlays: Long,
) {
    val fraction = (stat.playCount.toFloat() / maxPlays.coerceAtLeast(1L).toFloat())
        .coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.toString(),
            modifier = Modifier.width(26.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (rank <= 3) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stat.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.stats_plays_count, stat.playCount),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatDuration(stat.listenedMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatisticsRow(stat: TrackPlaybackStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stat.artist.ifBlank { stringResource(R.string.unknown_artist) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(R.string.stats_plays_count, stat.playCount),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatDuration(stat.listenedMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
