package com.qymusic.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.playback.EqualizerUiState
import com.qymusic.player.playback.MAX_REVERB_LEVEL
import com.qymusic.player.playback.MIN_REVERB_LEVEL
import com.qymusic.player.playback.ReverbPreset
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun EqualizerControls(
    state: EqualizerUiState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetChange: (Int) -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onBassStrengthChange: (Int) -> Unit,
    onVirtualizerStrengthChange: (Int) -> Unit,
    onLoudnessGainChange: (Int) -> Unit,
    onReverbPresetChange: (ReverbPreset) -> Unit,
    onReverbLevelChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.enable_equalizer),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = state.enabled,
                onCheckedChange = onEnabledChange,
                enabled = state.available,
            )
        }

        if (!state.available) {
            Text(
                text = stringResource(R.string.equalizer_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        if (state.enabled) {
            Text(
                text = stringResource(R.string.equalizer_preset),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.presets) { preset ->
                    val index = state.presets.indexOf(preset)
                    FilterChip(
                        selected = state.selectedPreset == index,
                        onClick = { onPresetChange(index) },
                        label = { Text(text = preset) },
                    )
                }
            }

            // 各频段推子左右并排，竖向拖动，整条曲线一眼可见。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                state.bands.forEach { band ->
                    EqualizerFader(
                        label = formatFrequency(band.frequencyHz),
                        value = band.level.toFloat(),
                        valueRange = state.minLevel.toFloat()..state.maxLevel.toFloat(),
                        valueFormatter = ::formatBandLevel,
                        onValueChange = { level -> onBandChange(band.index, level.toInt()) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (state.bassAvailable) {
                QyRowSlider(
                    label = stringResource(R.string.bass_boost),
                    value = state.bassStrength.toFloat(),
                    valueRange = 0f..state.bassMaxStrength.toFloat(),
                    valueFormatter = { value ->
                        "${(value / state.bassMaxStrength * 100f).roundToInt()}%"
                    },
                    onValueChange = { value -> onBassStrengthChange(value.toInt()) },
                )
            }

            if (state.virtualizerAvailable) {
                QyRowSlider(
                    label = stringResource(R.string.virtualizer),
                    value = state.virtualizerStrength.toFloat(),
                    valueRange = 0f..state.virtualizerMaxStrength.toFloat(),
                    valueFormatter = { value ->
                        "${(value / state.virtualizerMaxStrength * 100f).roundToInt()}%"
                    },
                    onValueChange = { value -> onVirtualizerStrengthChange(value.toInt()) },
                )
            }

            if (state.loudnessAvailable) {
                // LoudnessEnhancer 以毫贝为单位，界面按 dB 展示。
                QyRowSlider(
                    label = stringResource(R.string.loudness_compensation),
                    value = state.loudnessGainMb.toFloat(),
                    valueRange = 0f..state.loudnessMaxGainMb.toFloat(),
                    valueFormatter = { value ->
                        val dB = value / 100f
                        "+%.1f dB".format(dB)
                    },
                    onValueChange = { value -> onLoudnessGainChange(value.toInt()) },
                )
            }

            if (state.reverbAvailable) {
                Text(
                    text = stringResource(R.string.reverb_environment),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ReverbPreset.entries.toList()) { preset ->
                        FilterChip(
                            selected = state.reverbPreset == preset,
                            onClick = { onReverbPresetChange(preset) },
                            label = { Text(text = reverbPresetLabel(preset)) },
                        )
                    }
                }
                if (state.reverbPreset != ReverbPreset.OFF) {
                    QyRowSlider(
                        label = stringResource(R.string.reverb_level),
                        value = state.reverbLevel.toFloat(),
                        valueRange = MIN_REVERB_LEVEL.toFloat()..MAX_REVERB_LEVEL.toFloat(),
                        valueFormatter = { value -> "${value.roundToInt()}%" },
                        onValueChange = { value -> onReverbLevelChange(value.toInt()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun reverbPresetLabel(preset: ReverbPreset): String = stringResource(
    when (preset) {
        ReverbPreset.OFF -> R.string.reverb_off
        ReverbPreset.SMALL_ROOM -> R.string.reverb_small_room
        ReverbPreset.MEDIUM_ROOM -> R.string.reverb_medium_room
        ReverbPreset.LARGE_ROOM -> R.string.reverb_large_room
        ReverbPreset.MEDIUM_HALL -> R.string.reverb_medium_hall
        ReverbPreset.LARGE_HALL -> R.string.reverb_large_hall
        ReverbPreset.PLATE -> R.string.reverb_plate
    },
)

/**
 * 自绘的竖向推子：细轨道 + 圆心压在轨道上的圆形滑钮，和播放页进度条同一套视觉。
 * 填充从 0dB 中性位置向当前值延伸，一眼能看出是增益还是衰减。
 */
@Composable
private fun EqualizerFader(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val neutralFraction = ((0f - valueRange.start) / range).coerceIn(0f, 1f)
    val thumbRadiusPx = with(LocalDensity.current) { FADER_THUMB_RADIUS.toPx() }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val activeColor = MaterialTheme.colorScheme.onSurface

    fun valueAt(y: Float, height: Float): Float {
        val travel = (height - thumbRadiusPx * 2f).coerceAtLeast(1f)
        val ratio = ((y - thumbRadiusPx) / travel).coerceIn(0f, 1f)
        // 最上面是最大值，最下面是可选范围的最小值。
        return valueRange.endInclusive - range * ratio
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = valueFormatter(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Canvas(
            modifier = Modifier
                .width(FADER_WIDTH)
                .height(FADER_HEIGHT)
                .pointerInput(valueRange) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            onValueChange(valueAt(offset.y, size.height.toFloat()))
                        },
                    ) { change, _ ->
                        change.consume()
                        onValueChange(valueAt(change.position.y, size.height.toFloat()))
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        onValueChange(valueAt(offset.y, size.height.toFloat()))
                    }
                }
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                    setProgress { target ->
                        onValueChange(target.coerceIn(valueRange))
                        true
                    }
                },
        ) {
            val centerX = size.width / 2f
            val thickness = FADER_TRACK_THICKNESS.toPx()
            val radius = FADER_THUMB_RADIUS.toPx()
            val travel = (size.height - radius * 2f).coerceAtLeast(1f)
            val thumbY = radius + travel * (1f - fraction)
            val neutralY = radius + travel * (1f - neutralFraction)
            val cornerRadius = CornerRadius(thickness / 2f)
            val tickHeight = 1.5.dp.toPx()

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - thickness / 2f, 0f),
                size = Size(thickness, size.height),
                cornerRadius = cornerRadius,
            )
            val fillHeight = abs(thumbY - neutralY)
            if (fillHeight > 0.5f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(centerX - thickness / 2f, minOf(neutralY, thumbY)),
                    size = Size(thickness, fillHeight),
                    cornerRadius = cornerRadius,
                )
            }
            // 0dB 中性刻度，方便快速判断提升或衰减。
            drawRoundRect(
                color = activeColor.copy(alpha = 0.5f),
                topLeft = Offset(centerX - radius, neutralY - tickHeight / 2f),
                size = Size(radius * 2f, tickHeight),
                cornerRadius = CornerRadius(tickHeight / 2f),
            )
            drawCircle(
                color = activeColor,
                radius = radius,
                center = Offset(centerX, thumbY),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatBandLevel(value: Float): String {
    val decibels = value / 100f
    val sign = if (decibels > 0f) "+" else ""
    return "$sign%.1f".format(decibels)
}

private fun formatFrequency(frequencyHz: Int): String =
    if (frequencyHz >= 1_000) {
        "%.1fk".format(frequencyHz / 1_000f)
    } else {
        "${frequencyHz}Hz"
    }

private val FADER_WIDTH = 34.dp
private val FADER_HEIGHT = 148.dp
private val FADER_THUMB_RADIUS = 7.dp
private val FADER_TRACK_THICKNESS = 4.dp
