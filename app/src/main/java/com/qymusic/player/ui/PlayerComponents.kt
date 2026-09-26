package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qymusic.player.R
import com.qymusic.player.data.QUALITY_HI_RES
import com.qymusic.player.data.QUALITY_HQ
import com.qymusic.player.data.QUALITY_SQ
import com.qymusic.player.data.Track
import java.util.Locale

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
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            )
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
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.artist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                IconButton(onClick = onTogglePlayPause) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.pause else R.string.play,
                        ),
                    )
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
