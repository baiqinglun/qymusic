package com.qymusic.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 自绘的横向滑杆：左侧标签、中间细轨道、右侧数值，滑钮圆心压在轨道上，
 * 和播放页进度条、均衡器推子保持同一套视觉。
 *
 * [neutralValue] 不为空时，填充从中性位置向当前值延伸并画出中性刻度，
 * 适合「音调」这类有正负方向的调节。
 */
@Composable
internal fun QyRowSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    neutralValue: Float? = null,
    labelWidth: androidx.compose.ui.unit.Dp = QY_SLIDER_LABEL_WIDTH,
    valueWidth: androidx.compose.ui.unit.Dp = QY_SLIDER_VALUE_WIDTH,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        QySliderTrack(
            value = value,
            valueRange = valueRange,
            neutralValue = neutralValue,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueFormatter(value),
            modifier = Modifier.width(valueWidth),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

/**
 * 带左右步进按钮的滑杆，供需要精细调节的偏移量使用。
 */
@Composable
internal fun QyStepperSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    neutralValue: Float? = null,
) {
    fun quantized(rawValue: Float): Float = quantizeToStep(
        value = rawValue,
        valueRange = valueRange,
        step = step,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { onValueChange(quantized(value - step)) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.lyric_offset_decrease),
                )
            }
            QySliderTrack(
                value = value,
                valueRange = valueRange,
                neutralValue = neutralValue,
                onValueChange = { onValueChange(quantized(it)) },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onValueChange(quantized(value + step)) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.lyric_offset_increase),
                )
            }
        }
    }
}

@Composable
internal fun QySliderTrack(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    neutralValue: Float?,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color? = null,
    activeColor: Color? = null,
) {
    val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / range).coerceIn(0f, 1f)
    val neutralFraction = neutralValue
        ?.let { ((it - valueRange.start) / range).coerceIn(0f, 1f) }
    val thumbRadiusPx = with(LocalDensity.current) { QY_SLIDER_THUMB_RADIUS.toPx() }
    val resolvedTrackColor = trackColor
        ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val resolvedActiveColor = activeColor ?: MaterialTheme.colorScheme.onSurface

    fun valueAt(x: Float, width: Float): Float {
        val travel = (width - thumbRadiusPx * 2f).coerceAtLeast(1f)
        val ratio = ((x - thumbRadiusPx) / travel).coerceIn(0f, 1f)
        return valueRange.start + range * ratio
    }

    Canvas(
        modifier = modifier
            // 触摸区域比轨道高，手指更容易按住拖动。
            .height(32.dp)
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onValueChange(valueAt(offset.x, size.width.toFloat()))
                    },
                ) { change, _ ->
                    change.consume()
                    onValueChange(valueAt(change.position.x, size.width.toFloat()))
                }
            }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    onValueChange(valueAt(offset.x, size.width.toFloat()))
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
        val centerY = size.height / 2f
        val thickness = QY_SLIDER_TRACK_THICKNESS.toPx()
        val radius = QY_SLIDER_THUMB_RADIUS.toPx()
        val travel = (size.width - radius * 2f).coerceAtLeast(1f)
        val thumbX = radius + travel * fraction
        val cornerRadius = CornerRadius(thickness / 2f)
        val tickHeight = 1.5.dp.toPx()

        drawRoundRect(
            color = resolvedTrackColor,
            topLeft = Offset(0f, centerY - thickness / 2f),
            size = Size(size.width, thickness),
            cornerRadius = cornerRadius,
        )
        val fillStart = when (neutralFraction) {
            null -> 0f
            else -> radius + travel * neutralFraction
        }
        val fillWidth = abs(thumbX - fillStart).coerceAtLeast(thickness)
        drawRoundRect(
            color = resolvedActiveColor,
            topLeft = Offset(minOf(fillStart, thumbX), centerY - thickness / 2f),
            size = Size(fillWidth, thickness),
            cornerRadius = cornerRadius,
        )
        if (neutralFraction != null) {
            val tickX = radius + travel * neutralFraction
            drawRoundRect(
                color = resolvedActiveColor.copy(alpha = 0.5f),
                topLeft = Offset(tickX - tickHeight / 2f, centerY - radius),
                size = Size(tickHeight, radius * 2f),
                cornerRadius = CornerRadius(tickHeight / 2f),
            )
        }
        drawCircle(
            color = resolvedActiveColor,
            radius = radius,
            center = Offset(thumbX, centerY),
        )
    }
}

private fun quantizeToStep(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
): Float {
    if (step <= 0f) return value.coerceIn(valueRange)
    val stepCount = ((value - valueRange.start) / step).roundToInt()
    return (valueRange.start + stepCount * step).coerceIn(valueRange)
}

private val QY_SLIDER_LABEL_WIDTH = 72.dp
private val QY_SLIDER_VALUE_WIDTH = 58.dp
private val QY_SLIDER_THUMB_RADIUS = 7.dp
private val QY_SLIDER_TRACK_THICKNESS = 3.dp
