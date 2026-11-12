package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import com.qymusic.player.R
import com.qymusic.player.data.stripClipRangeSuffix
import com.qymusic.player.data.QUALITY_HI_RES
import com.qymusic.player.data.QUALITY_HQ
import com.qymusic.player.data.QUALITY_SQ
import com.qymusic.player.data.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * 按帧插值的实时播放进度：以 ViewModel 从 PlaybackState 采样到的
 * positionMs / positionAnchorElapsedMs / speed 为锚点，播放中每帧计算
 * position + speed * (elapsedRealtime - anchor)，暂停时直接用锚点值。
 *
 * 锚点由 ViewModel 在播放器事件与 200ms 轮询里不断校正（seek、缓冲、
 * 变速都会带来新锚点），这里只负责两帧之间的高频插值，不做任何 seekTo。
 */
@Composable
fun rememberLivePositionMs(playerState: PlayerUiState): Long {
    val state by rememberUpdatedState(playerState)
    var positionMs by remember(state.currentTrack?.id) {
        mutableLongStateOf(state.positionMs)
    }
    LaunchedEffect(state.currentTrack?.id) {
        while (isActive) {
            val snapshot = state
            positionMs = if (
                snapshot.isPlaying &&
                snapshot.positionAnchorElapsedMs > 0L
            ) {
                val wallMs = SystemClock.elapsedRealtime() - snapshot.positionAnchorElapsedMs
                val extrapolated = snapshot.positionMs + (snapshot.speed * wallMs).toLong()
                val anchored = extrapolated.coerceAtLeast(snapshot.positionMs)
                if (snapshot.durationMs > 0L) {
                    anchored.coerceAtMost(snapshot.durationMs)
                } else {
                    anchored
                }
            } else {
                snapshot.positionMs
            }
            // 播放中逐帧推进；暂停时降低频率等锚点更新即可（数值不变不会触发重组）
            if (snapshot.isPlaying) {
                withFrameNanos { }
            } else {
                delay(LIVE_POSITION_PAUSED_REFRESH_MS)
            }
        }
    }
    return positionMs
}

private const val LIVE_POSITION_PAUSED_REFRESH_MS = 200L

@Composable
fun CoverArt(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val side = if (maxWidth < maxHeight) maxWidth else maxHeight
                Canvas(modifier = Modifier.fillMaxSize().padding(side * 0.18f)) {
                    val stroke = size.minDimension * 0.035f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension * 0.38f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = stroke),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = radius * 0.68f,
                        center = center,
                        style = Stroke(width = stroke),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.04f),
                        radius = radius * 0.36f,
                        center = center,
                        style = Stroke(width = stroke),
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size((side * 0.42f).coerceAtLeast(12.dp)),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    artwork: Bitmap?,
    onOpen: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    phase: PlaybackPhase = PlaybackPhase.IDLE,
    bufferedPercent: Int? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        // 导航栏内边距放在背景里面，播放条底色一直铺到屏幕底部，
        // 否则底部会露出被阴影压暗的一条灰带。
        Column(modifier = Modifier.navigationBarsPadding()) {
            val progress = if (durationMs > 0L) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            val resolving = phase == PlaybackPhase.RESOLVING
            val buffering = phase == PlaybackPhase.BUFFERING
            if (resolving || buffering) {
                // 解析/缓冲用不确定态进度条：不拿播放进度冒充“加载进度”。
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onOpen)
                    .padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverArt(bitmap = artwork, modifier = Modifier.size(48.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stripClipRangeSuffix(track.title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val statusText = when {
                        resolving -> stringResource(R.string.playback_resolving)
                        buffering -> bufferedPercent?.let {
                            stringResource(R.string.playback_buffered_percent, it)
                        } ?: stringResource(R.string.playback_buffering)
                        else -> track.artist.ifBlank {
                            stringResource(R.string.unknown_artist)
                        }
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (resolving || buffering) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPrevious) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                    )
                }
                if (resolving && !isPlaying) {
                    // 试听地址解析中：播放键位置显示加载圈
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (isPlaying) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = stringResource(
                                if (isPlaying) R.string.pause else R.string.play,
                            ),
                        )
                    }
                }
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.next),
                    )
                }
            }
        }
    }
}

/**
 * 播放失败提示条：网络错误可原地重试，直链过期由 ViewModel 重新解析后续播。
 * 不踢回列表页，也不让错误只留在日志里。
 */
@Composable
fun PlaybackErrorBanner(
    error: PlaybackError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = error.message,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (error.retryable) {
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.playback_error_retry),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.playback_error_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun QualityBadge(
    quality: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (quality.isBlank()) return
    val scheme = MaterialTheme.colorScheme
    val containerColor = when (quality) {
        QUALITY_HI_RES -> scheme.primaryContainer
        QUALITY_SQ -> scheme.secondaryContainer
        QUALITY_HQ -> scheme.tertiaryContainer
        else -> scheme.surfaceVariant
    }
    val contentColor = when (quality) {
        QUALITY_HI_RES -> scheme.onPrimaryContainer
        QUALITY_SQ -> scheme.onSecondaryContainer
        QUALITY_HQ -> scheme.onTertiaryContainer
        else -> scheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(if (compact) 3.dp else 4.dp),
    ) {
        Text(
            text = quality,
            modifier = Modifier.padding(
                horizontal = if (compact) 4.dp else 6.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
            style = if (compact) {
                // 行高一起收窄，标签才会真的变小（只改字号行高仍是 16sp）。
                MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp)
            } else {
                MaterialTheme.typography.labelMedium
            },
        )
    }
}

/** 例如 “16bit / 44.1kHz / 2ch”。 */
fun formatAudioInfo(track: Track): String {
    val parts = mutableListOf<String>()
    when {
        track.bitDepth > 0 -> parts += "${track.bitDepth}bit"
        track.bitrateKbps > 0 -> parts += "${track.bitrateKbps}kbps"
    }
    if (track.sampleRateHz > 0) {
        val kHz = track.sampleRateHz / 1000f
        parts += if (kHz % 1f == 0f) {
            "${kHz.toInt()}kHz"
        } else {
            "%.1fkHz".format(Locale.US, kHz)
        }
    }
    if (track.channelCount > 0) {
        parts += "${track.channelCount}ch"
    }
    return parts.joinToString(" / ")
}

@Composable
fun TrackPropertiesDialog(
    track: Track,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.track_properties)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PropertyRow(
                    label = stringResource(R.string.property_title),
                    value = track.title,
                )
                PropertyRow(
                    label = stringResource(R.string.property_artist),
                    value = track.artist.ifBlank { stringResource(R.string.unknown_artist) },
                )
                PropertyRow(
                    label = stringResource(R.string.property_album),
                    value = track.album.ifBlank { stringResource(R.string.unknown_album) },
                )
                PropertyRow(
                    label = stringResource(R.string.property_duration),
                    value = formatDuration(track.durationMs),
                )
                PropertyRow(
                    label = stringResource(R.string.property_quality),
                    value = track.quality,
                )
                PropertyRow(
                    label = stringResource(R.string.property_audio),
                    value = formatAudioInfo(track),
                )
                PropertyRow(
                    label = stringResource(R.string.property_format),
                    value = track.extension.uppercase(),
                )
                PropertyRow(
                    label = stringResource(R.string.property_file),
                    value = track.relativePath,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.close))
            }
        },
    )
}

@Composable
private fun PropertyRow(
    label: String,
    value: String,
) {
    if (value.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(56.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * 胶囊变形开关：切换时圆点在移动过程中横向拉长成胶囊，到达终点后收回复圆，
 * 弹簧欠阻尼带轻微回弹。全程 Canvas 只改圆点的位置和宽度，无图切换、无透明度交叉。
 */
@Composable
fun MorphSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 欠阻尼弹簧：过冲即“到达终点后的轻微回弹”。
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "morph-switch",
    )
    val trackOn = MaterialTheme.colorScheme.primary
    val trackOff = MaterialTheme.colorScheme.surfaceVariant
    val thumbOn = MaterialTheme.colorScheme.onPrimary
    val thumbOff = MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .width(52.dp)
            .height(32.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onCheckedChange?.invoke(it) },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMorphSwitch(
                progress = progress,
                trackColor = lerp(trackOff, trackOn, progress.coerceIn(0f, 1f)),
                thumbColor = lerp(thumbOff, thumbOn, progress.coerceIn(0f, 1f)),
            )
        }
    }
}

private fun DrawScope.drawMorphSwitch(
    progress: Float,
    trackColor: Color,
    thumbColor: Color,
) {
    val h = size.height
    val w = size.width
    // 轨道：整颗胶囊。
    drawRoundRect(
        color = trackColor,
        cornerRadius = CornerRadius(h / 2f, h / 2f),
    )
    // 圆点静止直径；拉伸量在中点最大、两端归零（4p(1-p) 钳掉过冲段的负值），
    // 于是移动中是胶囊、到终点正好收回圆形。
    val dotD = h * 0.72f
    val margin = (h - dotD) / 2f
    val xStart = margin
    val xEnd = w - margin - dotD
    val stretch = (4f * progress * (1f - progress)).coerceAtLeast(0f)
    val dotW = dotD + dotD * 0.9f * stretch
    // 圆心沿行程移动；拉伸围绕圆心对称，不改变行进位置。
    val center = xStart + (xEnd - xStart) * progress + dotD / 2f
    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(center - dotW / 2f, (h - dotD) / 2f),
        size = androidx.compose.ui.geometry.Size(dotW, dotD),
        cornerRadius = CornerRadius(dotD / 2f, dotD / 2f),
    )
}
