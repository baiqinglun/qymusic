package com.qymusic.player.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.AppSettings
import com.qymusic.player.data.Track
import com.qymusic.player.playback.KaraokePitchPoint
import com.qymusic.player.playback.KaraokePlayerState
import com.qymusic.player.playback.KaraokeRecordingPhase
import com.qymusic.player.playback.KaraokeRecordingState
import com.qymusic.player.playback.MAX_PITCH_SEMITONES
import com.qymusic.player.playback.MAX_PLAYBACK_SPEED
import com.qymusic.player.playback.MIN_PITCH_SEMITONES
import com.qymusic.player.playback.MIN_PLAYBACK_SPEED
import com.qymusic.player.playback.PLAYBACK_SPEED_STEP
import com.qymusic.player.playback.VocalSplitMode
import com.qymusic.player.playback.snapPlaybackSpeed
import com.qymusic.player.ui.theme.Night
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun KaraokeScreen(
    playerState: KaraokePlayerState,
    lyricsState: LyricsUiState,
    settings: AppSettings,
    track: Track,
    artwork: Bitmap?,
    blurredCover: Bitmap,
    vocalSplitMode: VocalSplitMode,
    recordingState: KaraokeRecordingState,
    recordingPermissionGranted: Boolean,
    onRequestRecordingPermission: () -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onFinishRecording: () -> Unit,
) {
    val baseScheme = MaterialTheme.colorScheme
    val karaokeScheme = baseScheme
    val karaokeLyricsSettings = remember(settings) {
        settings.copy(lyricCenterStartEnd = true)
    }
    val semitones = playerState.pitchSemitones.roundToInt()
    val speed = snapPlaybackSpeed(playerState.speed)
    val showPitchLadder = shouldShowPitchLadder(
        lyricsState = lyricsState,
        positionMs = playerState.positionMs,
    )
    val backgroundMotion = rememberMusicBackgroundMotion(
        enabled = settings.musicReactiveBackground && playerState.isPlaying,
        positionMs = playerState.positionMs,
    )

    MaterialTheme(colorScheme = karaokeScheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val landscapeTrackHeaderWidth = (maxWidth * 0.32f).coerceIn(180.dp, 280.dp)

            KaraokeBackdrop(
                blurredCover = blurredCover,
                backgroundMotion = backgroundMotion,
                backgroundMotionEnabled = settings.musicReactiveBackground,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                KaraokeTopBar(
                    onBack = onBack,
                    compact = landscape,
                    recordingState = recordingState,
                    recordingPermissionGranted = recordingPermissionGranted,
                    onFinishRecording = onFinishRecording,
                )

                if (landscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .width(landscapeTrackHeaderWidth)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            KaraokeTrackHeader(
                                track = track,
                                artwork = artwork,
                                vertical = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            KaraokePitchPanel(
                                recordingState = recordingState,
                                positionMs = playerState.positionMs,
                                showPitchLadder = showPitchLadder,
                                recordingPermissionGranted = recordingPermissionGranted,
                                onRequestRecordingPermission = onRequestRecordingPermission,
                                compact = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                KaraokeLyrics(
                                    lyricsState = lyricsState,
                                    positionMs = playerState.positionMs,
                                    settings = karaokeLyricsSettings,
                                    onSeek = onSeek,
                                    onTogglePlayPause = onTogglePlayPause,
                                    showPauseIcon = playerState.showPauseIcon,
                                )
                            }
                        }
                    }
                } else {
                    KaraokeTrackHeader(
                        track = track,
                        artwork = artwork,
                        vertical = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    KaraokePitchPanel(
                        recordingState = recordingState,
                        positionMs = playerState.positionMs,
                        showPitchLadder = showPitchLadder,
                        recordingPermissionGranted = recordingPermissionGranted,
                        onRequestRecordingPermission = onRequestRecordingPermission,
                        compact = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        KaraokeLyrics(
                            lyricsState = lyricsState,
                            positionMs = playerState.positionMs,
                            settings = karaokeLyricsSettings,
                            onSeek = onSeek,
                            onTogglePlayPause = onTogglePlayPause,
                            showPauseIcon = playerState.showPauseIcon,
                        )
                    }
                }

                KaraokeCompactControls(
                    trackId = track.id,
                    playerState = playerState,
                    vocalSplitMode = vocalSplitMode,
                    semitones = semitones,
                    speed = speed,
                    landscape = landscape,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeek = onSeek,
                    onVocalSplitModeChange = onVocalSplitModeChange,
                    onPitchSemitonesChange = onPitchSemitonesChange,
                    onPlaybackSpeedChange = onPlaybackSpeedChange,
                )
            }
        }
    }
}

@Composable
internal fun KaraokePreviewScreen(
    recordingState: KaraokeRecordingState,
    track: Track,
    artwork: Bitmap?,
    blurredCover: Bitmap,
    onBack: () -> Unit,
    onRerecord: () -> Unit,
    onDone: () -> Unit,
) {
    val baseScheme = MaterialTheme.colorScheme
    val previewScheme = baseScheme
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(recordingState.outputPath, recordingState.phase) {
        val path = recordingState.outputPath
        if (
            recordingState.phase == KaraokeRecordingPhase.READY &&
            path != null &&
            File(path).exists()
        ) {
            mediaPlayer = runCatching {
                MediaPlayer().apply {
                    setDataSource(path)
                    setOnCompletionListener { isPlaying = false }
                    prepare()
                }
            }.getOrNull()
        }
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
            isPlaying = false
        }
    }

    MaterialTheme(colorScheme = previewScheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            val previewSummaryWidth = (maxWidth * 0.34f).coerceIn(220.dp, 320.dp)
            KaraokeBackdrop(blurredCover = blurredCover)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                KaraokePreviewTopBar(
                    compact = landscape,
                    onBack = onBack,
                    onDone = onDone,
                )
                if (recordingState.phase == KaraokeRecordingPhase.PROCESSING) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                strokeWidth = 2.5.dp,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.karaoke_scoring),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.82f),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (landscape) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                            ) {
                                KaraokePreviewSummary(
                                    recordingState = recordingState,
                                    track = track,
                                    artwork = artwork,
                                    compact = true,
                                    modifier = Modifier
                                        .width(previewSummaryWidth)
                                        .fillMaxHeight(),
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                ) {
                                    KaraokePitchLadder(
                                        points = recordingState.points,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                    )
                                    KaraokeScoreMetricsRow(
                                        pitchScore = recordingState.pitchScore,
                                        stabilityScore = recordingState.stabilityScore,
                                    )
                                    KaraokeRecordingPlaybackControl(
                                        mediaPlayer = mediaPlayer,
                                        isPlaying = isPlaying,
                                        onPlayingChange = { isPlaying = it },
                                    )
                                }
                            }
                        } else {
                            KaraokePreviewSummary(
                                recordingState = recordingState,
                                track = track,
                                artwork = artwork,
                                compact = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            KaraokePitchLadder(
                                points = recordingState.points,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                            KaraokeRecordingPlaybackControl(
                                mediaPlayer = mediaPlayer,
                                isPlaying = isPlaying,
                                onPlayingChange = { isPlaying = it },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                mediaPlayer?.release()
                                mediaPlayer = null
                                isPlaying = false
                                onRerecord()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.karaoke_rerecord),
                                color = Color.White,
                            )
                        }
                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Night,
                            ),
                        ) {
                            Text(text = stringResource(R.string.done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun KaraokeBackdrop(
    blurredCover: Bitmap,
    scrimAlpha: Float = 0f,
    backgroundMotion: Float = 0f,
    backgroundMotionEnabled: Boolean = false,
) {
    CoverBackdrop(
        bitmap = blurredCover,
        scrimAlpha = scrimAlpha,
        musicMotion = backgroundMotion,
        musicMotionEnabled = backgroundMotionEnabled,
    )
}

@Composable
private fun KaraokeTopBar(
    onBack: () -> Unit,
    compact: Boolean,
    recordingState: KaraokeRecordingState,
    recordingPermissionGranted: Boolean,
    onFinishRecording: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.karaoke_local_recording),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
        Surface(
            onClick = onFinishRecording,
            enabled = recordingPermissionGranted && recordingState.isRecording,
            shape = RoundedCornerShape(18.dp),
            color = if (recordingState.isRecording) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (recordingState.isRecording) {
                Color.White
            } else {
                Color.White.copy(alpha = 0.45f)
            },
            modifier = Modifier.heightIn(min = 40.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.StopCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.karaoke_finish_recording),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun KaraokePreviewTopBar(
    compact: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.karaoke_preview),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onDone) {
            Text(
                text = stringResource(R.string.done),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun KaraokeTrackHeader(
    track: Track,
    artwork: Bitmap?,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    val artistName = track.artist.ifBlank { stringResource(R.string.unknown_artist) }

    if (vertical) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CoverArt(bitmap = artwork, modifier = Modifier.size(136.dp))
            Spacer(modifier = Modifier.height(16.dp))
            KaraokeTrackText(
                title = track.title,
                artist = artistName,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(bitmap = artwork, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.width(14.dp))
            KaraokeTrackText(
                title = track.title,
                artist = artistName,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KaraokeTrackText(
    title: String,
    artist: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.74f),
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun KaraokeLyrics(
    lyricsState: LyricsUiState,
    positionMs: Long,
    settings: AppSettings,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    showPauseIcon: Boolean,
) {
    LyricsPanel(
        lyricsState = lyricsState,
        positionMs = positionMs,
        settings = settings,
        animate = true,
        karaokeMode = true,
        onPlayFrom = { positionMs ->
            onSeek(positionMs)
            if (!showPauseIcon) {
                onTogglePlayPause()
            }
        },
    )
}

@Composable
private fun KaraokePitchPanel(
    recordingState: KaraokeRecordingState,
    positionMs: Long,
    showPitchLadder: Boolean,
    recordingPermissionGranted: Boolean,
    onRequestRecordingPermission: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val vocalTrackColor = darkTrackAccent(MaterialTheme.colorScheme.primary)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = KaraokeSurface,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KaraokePitchLegend(
                    label = stringResource(R.string.karaoke_music_pitch),
                    note = midiToNoteName(recordingState.musicMidi),
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f),
                )
                KaraokePitchLegend(
                    label = stringResource(R.string.karaoke_vocal_pitch),
                    note = midiToNoteName(recordingState.vocalMidi),
                    color = vocalTrackColor,
                    modifier = Modifier.weight(1f),
                )
            }
            val visiblePoints = recordingState.points.takeLast(
                if (compact) COMPACT_PITCH_POINT_WINDOW else LIVE_PITCH_POINT_WINDOW,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 82.dp else 108.dp),
            ) {
                KaraokePitchLadder(
                    points = visiblePoints,
                    positionMs = positionMs,
                    showDynamicLadder = showPitchLadder,
                    darkTrack = true,
                    modifier = Modifier.fillMaxSize(),
                )
                val hasPitchSignal = visiblePoints.any { point ->
                    point.musicMidi != null || point.vocalMidi != null
                }
                if (!showPitchLadder) {
                    Text(
                        text = stringResource(R.string.karaoke_instrumental_intro),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.66f),
                    )
                } else if (!hasPitchSignal) {
                    Text(
                        text = stringResource(
                            if (recordingPermissionGranted) {
                                R.string.karaoke_waiting_pitch
                            } else {
                                R.string.karaoke_microphone_required
                            },
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.66f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (
                recordingState.phase == KaraokeRecordingPhase.ERROR &&
                !recordingState.errorMessage.isNullOrBlank()
            ) {
                Text(
                    text = recordingState.errorMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!recordingPermissionGranted) {
                TextButton(
                    onClick = onRequestRecordingPermission,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.karaoke_allow_microphone),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokePitchLegend(
    label: String,
    note: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.74f),
            maxLines = 1,
        )
        Text(
            text = note,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (note == "--") Color.White.copy(alpha = 0.45f) else color,
            maxLines = 1,
        )
    }
}

@Composable
internal fun KaraokePitchLadder(
    points: List<KaraokePitchPoint>,
    positionMs: Long? = null,
    showDynamicLadder: Boolean = true,
    darkTrack: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val midiValues = remember(points) {
        points.flatMap { point ->
            listOfNotNull(point.musicMidi, point.vocalMidi)
        }
    }
    val minMidi = remember(midiValues) {
        floor((midiValues.minOrNull() ?: DEFAULT_MIN_MIDI) - 1f)
            .coerceAtLeast(MIN_VISIBLE_MIDI)
    }
    val maxMidi = remember(midiValues) {
        ceil((midiValues.maxOrNull() ?: DEFAULT_MAX_MIDI) + 1f)
            .coerceAtMost(MAX_VISIBLE_MIDI)
    }
    val visibleRange = max(MIN_VISIBLE_RANGE, maxMidi - minMidi)
    val effectiveMax = minMidi + visibleRange
    val timelineStartMs = remember(points, positionMs) {
        val currentMs = positionMs ?: points.lastOrNull()?.timeMs
        if (currentMs == null || points.isEmpty()) {
            null
        } else {
            val firstPointMs = points.first().timeMs
            val lastPointMs = points.last().timeMs
            val windowEnd = max(
                lastPointMs,
                (currentMs + PITCH_SCROLL_WINDOW_MS / 2L)
                    .coerceAtLeast(firstPointMs + PITCH_SCROLL_WINDOW_MS),
            )
            (windowEnd - PITCH_SCROLL_WINDOW_MS).coerceAtLeast(firstPointMs)
        }
    }
    val timelineEndMs = timelineStartMs?.plus(PITCH_SCROLL_WINDOW_MS)
    val ladderSurface = if (darkTrack) {
        KaraokeBackground
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primary = if (darkTrack) {
        darkTrackAccent(MaterialTheme.colorScheme.primary)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val gridColor = if (darkTrack) {
        Color.White.copy(alpha = 0.08f)
    } else {
        onSurface.copy(alpha = 0.08f)
    }
    val guideColor = if (darkTrack) {
        Color.White.copy(alpha = 0.16f)
    } else {
        onSurface.copy(alpha = 0.14f)
    }
    val musicLineColor = if (darkTrack) {
        Color.White.copy(alpha = 0.52f)
    } else {
        onSurface.copy(alpha = 0.52f)
    }
    val playheadColor = if (darkTrack) {
        Color.White.copy(alpha = 0.62f)
    } else {
        onSurface.copy(alpha = 0.58f)
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ladderSurface),
    ) {
        val rowCount = visibleRange.toInt().coerceAtLeast(1)
        repeat(rowCount + 1) { index ->
            val y = size.height * index / rowCount
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        if (showDynamicLadder) {
            drawPitchGuideLadder(
                color = guideColor,
                strokeWidth = 2.dp.toPx(),
            )
            drawStepPitchLine(
                points = points,
                minMidi = minMidi,
                maxMidi = effectiveMax,
                timelineStartMs = timelineStartMs,
                timelineEndMs = timelineEndMs,
                color = musicLineColor,
                strokeWidth = 3.dp.toPx(),
                midiOf = { it.musicMidi },
            )
            drawStepPitchLine(
                points = points,
                minMidi = minMidi,
                maxMidi = effectiveMax,
                timelineStartMs = timelineStartMs,
                timelineEndMs = timelineEndMs,
                color = primary,
                strokeWidth = 3.5.dp.toPx(),
                midiOf = { it.vocalMidi },
            )
            if (
                positionMs != null &&
                timelineStartMs != null &&
                timelineEndMs != null &&
                timelineEndMs > timelineStartMs
            ) {
                val playheadX = size.width * (
                    (positionMs - timelineStartMs).toFloat() /
                        (timelineEndMs - timelineStartMs).toFloat()
                    ).coerceIn(0f, 1f)
                drawLine(
                    color = playheadColor,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
    }
}

private fun darkTrackAccent(color: Color): Color = lerp(color, Color.White, 0.35f)

private fun DrawScope.drawPitchGuideLadder(
    color: Color,
    strokeWidth: Float,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val stepCount = 5
    val stepWidth = size.width / stepCount.toFloat()
    val levels = listOf(0.68f, 0.52f, 0.40f, 0.60f, 0.47f, 0.64f)
    var level = levels.first()

    repeat(stepCount) { index ->
        val nextLevel = levels[(index + 1).coerceAtMost(levels.lastIndex)]
        val startX = stepWidth * index
        val endX = stepWidth * (index + 1)
        val startY = size.height * level
        val endY = size.height * nextLevel
        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, startY),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(endX, startY),
            end = Offset(endX, endY),
            strokeWidth = strokeWidth,
        )
        level = nextLevel
    }
}

private fun DrawScope.drawStepPitchLine(
    points: List<KaraokePitchPoint>,
    minMidi: Float,
    maxMidi: Float,
    timelineStartMs: Long?,
    timelineEndMs: Long?,
    color: Color,
    strokeWidth: Float,
    midiOf: (KaraokePitchPoint) -> Float?,
) {
    if (points.size < 2 || maxMidi <= minMidi) return
    val width = size.width
    val height = size.height
    var previous: Offset? = null

    val timelineRange = if (
        timelineStartMs != null &&
        timelineEndMs != null &&
        timelineEndMs > timelineStartMs
    ) {
        timelineStartMs.toFloat()..timelineEndMs.toFloat()
    } else {
        null
    }

    points.forEachIndexed { index, point ->
        val midi = midiOf(point) ?: run {
            previous = null
            return@forEachIndexed
        }
        val x = timelineRange?.let { range ->
            if (point.timeMs < range.start || point.timeMs > range.endInclusive) {
                previous = null
                return@forEachIndexed
            }
            width * ((point.timeMs - range.start) /
                (range.endInclusive - range.start))
        } ?: (width * index.toFloat() / (points.size - 1).toFloat())
        val y = height * (
            1f - ((midi - minMidi) / (maxMidi - minMidi)).coerceIn(0f, 1f)
            )
        val current = Offset(x, y)
        previous?.let { previousOffset ->
            drawLine(
                color = color,
                start = previousOffset,
                end = Offset(current.x, previousOffset.y),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = color,
                start = Offset(current.x, previousOffset.y),
                end = current,
                strokeWidth = strokeWidth,
            )
        }
        previous = current
    }
}

private enum class KaraokeExpandedTool {
    NONE,
    PITCH,
    SPEED,
}

@Composable
private fun KaraokeProgressBar(
    playerState: KaraokePlayerState,
    onSeek: (Long) -> Unit,
) {
    var draggingValue by remember(playerState.trackId) {
        mutableStateOf<Float?>(null)
    }
    val duration = playerState.durationMs.coerceAtLeast(0L)
    val maxValue = duration.toFloat().coerceAtLeast(1f)
    val value = (draggingValue ?: playerState.positionMs.toFloat())
        .coerceIn(0f, maxValue)
    val progress = (value / maxValue).coerceIn(0f, 1f)
    val currentOnSeek = rememberUpdatedState(onSeek)
    val thumbRadiusPx = with(LocalDensity.current) { KARAOKE_PROGRESS_THUMB_RADIUS.toPx() }
    val trackColor = Color.White.copy(alpha = 0.25f)
    val activeTrackColor = Color.White
    val thumbColor = Color.White

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(KARAOKE_PROGRESS_TOUCH_HEIGHT)
                .semantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value,
                        range = 0f..maxValue,
                    )
                    setProgress { target ->
                        currentOnSeek.value(target.toLong())
                        true
                    }
                }
                .pointerInput(duration, thumbRadiusPx) {
                    if (duration <= 0L) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val usableWidth = (size.width - thumbRadiusPx * 2f)
                            .coerceAtLeast(1f)

                        fun valueAt(x: Float): Float =
                            ((x - thumbRadiusPx) / usableWidth)
                                .coerceIn(0f, 1f) * maxValue

                        draggingValue = valueAt(down.position.x)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull {
                                it.id == down.id
                            } ?: break
                            if (!change.pressed) break
                            draggingValue = valueAt(change.position.x)
                            change.consume()
                        }
                        draggingValue?.let { currentOnSeek.value(it.toLong()) }
                        draggingValue = null
                    }
                },
        ) {
            val centerY = size.height / 2f
            val thickness = KARAOKE_PROGRESS_TRACK_THICKNESS.toPx()
            val radius = KARAOKE_PROGRESS_THUMB_RADIUS.toPx()
            val travel = (size.width - radius * 2f).coerceAtLeast(1f)
            val thumbX = radius + travel * progress
            val cornerRadius = CornerRadius(thickness / 2f)

            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, centerY - thickness / 2f),
                size = Size(size.width, thickness),
                cornerRadius = cornerRadius,
            )
            drawRoundRect(
                color = activeTrackColor,
                topLeft = Offset(0f, centerY - thickness / 2f),
                size = Size(thumbX.coerceAtLeast(thickness), thickness),
                cornerRadius = cornerRadius,
            )
            drawCircle(
                color = thumbColor,
                radius = radius,
                center = Offset(thumbX, centerY),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(playerState.positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.62f),
            )
        }
    }
}

private val KARAOKE_PROGRESS_TOUCH_HEIGHT = 22.dp
private val KARAOKE_PROGRESS_THUMB_RADIUS = 7.dp
private val KARAOKE_PROGRESS_TRACK_THICKNESS = 3.dp

@Composable
private fun KaraokeCompactControls(
    trackId: String,
    playerState: KaraokePlayerState,
    vocalSplitMode: VocalSplitMode,
    semitones: Int,
    speed: Float,
    landscape: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
) {
    var expandedToolName by rememberSaveable(trackId) {
        mutableStateOf(KaraokeExpandedTool.NONE.name)
    }
    val expandedTool = runCatching {
        KaraokeExpandedTool.valueOf(expandedToolName)
    }.getOrDefault(KaraokeExpandedTool.NONE)

    fun toggleTool(tool: KaraokeExpandedTool) {
        expandedToolName = if (expandedTool == tool) {
            KaraokeExpandedTool.NONE.name
        } else {
            tool.name
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (landscape) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KaraokeProgressBar(
            playerState = playerState,
            onSeek = onSeek,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = if (playerState.showPauseIcon) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = stringResource(
                        if (playerState.showPauseIcon) R.string.pause else R.string.play,
                    ),
                    modifier = Modifier.size(32.dp),
                )
            }
            KaraokeCompactButton(
                icon = Icons.Rounded.GraphicEq,
                label = if (vocalSplitMode == VocalSplitMode.INSTRUMENTAL) {
                    stringResource(R.string.karaoke_accompaniment)
                } else {
                    stringResource(R.string.vocal_split_both)
                },
                selected = vocalSplitMode == VocalSplitMode.INSTRUMENTAL,
                onClick = {
                    onVocalSplitModeChange(
                        if (vocalSplitMode == VocalSplitMode.INSTRUMENTAL) {
                            VocalSplitMode.BOTH
                        } else {
                            VocalSplitMode.INSTRUMENTAL
                        },
                    )
                },
            )
            KaraokeCompactButton(
                    icon = Icons.Rounded.MusicNote,
                    label = stringResource(R.string.karaoke_pitch),
                    value = formatKaraokePitch(semitones),
                    selected = semitones != 0 ||
                        expandedTool == KaraokeExpandedTool.PITCH,
                    onClick = { toggleTool(KaraokeExpandedTool.PITCH) },
                )
            KaraokeCompactButton(
                    icon = Icons.Rounded.Speed,
                    label = stringResource(R.string.playback_speed),
                    value = formatKaraokeSpeed(speed),
                    selected = abs(speed - 1f) > 0.001f ||
                        expandedTool == KaraokeExpandedTool.SPEED,
                    onClick = { toggleTool(KaraokeExpandedTool.SPEED) },
                )
        }

        if (expandedTool != KaraokeExpandedTool.NONE) {
            KaraokeTuningBottomSheet(
                tool = expandedTool,
                semitones = semitones,
                speed = speed,
                onDismiss = { expandedToolName = KaraokeExpandedTool.NONE.name },
                onPitchSemitonesChange = onPitchSemitonesChange,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
            )
        }
    }
}

@Composable
private fun KaraokeCompactButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    selected: Boolean = false,
) {
    val isSelected = selected
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(56.dp)
            .semantics { this.selected = isSelected },
        shape = CircleShape,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = contentColor,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = value
                    ?.takeIf(String::isNotBlank)
                    ?.let { "$label $it" }
                    ?: label,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun KaraokeTuningBottomSheet(
    tool: KaraokeExpandedTool,
    semitones: Int,
    speed: Float,
    onDismiss: () -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Night,
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.42f),
            )
        },
    ) {
        when (tool) {
            KaraokeExpandedTool.PITCH -> KaraokeTuningContent(
                title = stringResource(R.string.karaoke_pitch),
                value = semitones.toFloat(),
                valueRange = MIN_PITCH_SEMITONES..MAX_PITCH_SEMITONES,
                step = PITCH_STEP_SEMITONES,
                neutralValue = 0f,
                valueFormatter = { formatKaraokePitch(it.roundToInt()) },
                reset = { onPitchSemitonesChange(0f) },
                onValueChange = onPitchSemitonesChange,
            )

            KaraokeExpandedTool.SPEED -> KaraokeTuningContent(
                title = stringResource(R.string.playback_speed),
                value = speed,
                valueRange = MIN_PLAYBACK_SPEED..MAX_PLAYBACK_SPEED,
                step = PLAYBACK_SPEED_STEP,
                neutralValue = 1f,
                valueFormatter = ::formatKaraokeSpeed,
                reset = { onPlaybackSpeedChange(1f) },
                onValueChange = onPlaybackSpeedChange,
            )

            KaraokeExpandedTool.NONE -> Unit
        }
    }
}

@Composable
private fun KaraokeTuningContent(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    neutralValue: Float,
    valueFormatter: (Float) -> String,
    reset: () -> Unit,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
        QySliderTrack(
            value = value,
            valueRange = valueRange,
            neutralValue = neutralValue,
            onValueChange = { sliderValue ->
                onValueChange(
                    snapKaraokeTuningValue(
                        value = sliderValue,
                        valueRange = valueRange,
                        step = step,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
            trackColor = Color.White.copy(alpha = 0.24f),
            activeColor = Color.White,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledIconButton(
                onClick = {
                    onValueChange((value - step).coerceIn(valueRange))
                },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.karaoke_decrease),
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = valueFormatter(value),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            FilledIconButton(
                onClick = {
                    onValueChange((value + step).coerceIn(valueRange))
                },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.karaoke_increase),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        TextButton(
            onClick = reset,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.reset),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun KaraokePreviewSummary(
    recordingState: KaraokeRecordingState,
    track: Track,
    artwork: Bitmap?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CoverArt(bitmap = artwork, modifier = Modifier.size(if (compact) 64.dp else 96.dp))
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist.ifBlank { stringResource(R.string.unknown_artist) },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
        Text(
            text = recordingState.score?.toString() ?: "--",
            style = if (compact) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.displayLarge
            },
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.karaoke_score),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.72f),
        )
        if (recordingState.phase == KaraokeRecordingPhase.ERROR) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.karaoke_recording_failed),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            recordingState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.64f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!compact) {
            Spacer(modifier = Modifier.height(12.dp))
            KaraokeScoreMetricsRow(
                pitchScore = recordingState.pitchScore,
                stabilityScore = recordingState.stabilityScore,
            )
        }
        if (!compact && !recordingState.musicPitchAvailable) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.karaoke_music_pitch_fallback),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun KaraokeScoreMetricsRow(
    pitchScore: Int?,
    stabilityScore: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KaraokeScoreMetric(
            label = stringResource(R.string.karaoke_pitch_accuracy),
            value = pitchScore,
            modifier = Modifier.weight(1f),
        )
        KaraokeScoreMetric(
            label = stringResource(R.string.karaoke_stability),
            value = stabilityScore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun KaraokeScoreMetric(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value?.toString() ?: "--",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.66f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KaraokeRecordingPlaybackControl(
    mediaPlayer: MediaPlayer?,
    isPlaying: Boolean,
    onPlayingChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = {
                val player = mediaPlayer ?: return@Surface
                when {
                    player.isPlaying -> {
                        player.pause()
                        onPlayingChange(false)
                    }

                    isPlaying -> {
                        player.start()
                        onPlayingChange(true)
                    }

                    else -> {
                        player.seekTo(0)
                        player.start()
                        onPlayingChange(true)
                    }
                }
            },
            enabled = mediaPlayer != null,
            shape = RoundedCornerShape(20.dp),
            color = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (mediaPlayer?.isPlaying == true) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.karaoke_play_recording),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun snapKaraokeTuningValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
): Float {
    if (step <= 0f) return value.coerceIn(valueRange)
    val stepCount = ((value - valueRange.start) / step).roundToInt()
    return (valueRange.start + stepCount * step).coerceIn(valueRange)
}

private fun formatKaraokePitch(semitones: Int): String =
    when {
        semitones > 0 -> "+$semitones"
        semitones < 0 -> semitones.toString()
        else -> "0"
    }

private fun formatKaraokeSpeed(speed: Float): String =
    String.format(Locale.US, "%.2fx", snapPlaybackSpeed(speed))

private fun midiToNoteName(midi: Float?): String {
    if (midi == null || !midi.isFinite()) return "--"
    val rounded = midi.roundToInt().coerceIn(0, 127)
    val note = NOTE_NAMES[rounded % 12]
    val octave = rounded / 12 - 1
    return "$note$octave"
}

private fun shouldShowPitchLadder(
    lyricsState: LyricsUiState,
    positionMs: Long,
): Boolean {
    val lyrics = when (lyricsState) {
        is LyricsUiState.Ready -> lyricsState.lyrics
        else -> return true
    }
    if (!lyrics.isSynchronized || lyrics.lines.isEmpty()) return true
    return positionMs >= lyrics.lines.first().timeMs
}

private const val COMPACT_PITCH_POINT_WINDOW = 80
private const val LIVE_PITCH_POINT_WINDOW = 120
private const val PITCH_SCROLL_WINDOW_MS = 10_000L
private const val DEFAULT_MIN_MIDI = 48f
private const val DEFAULT_MAX_MIDI = 72f
private const val MIN_VISIBLE_MIDI = 36f
private const val MAX_VISIBLE_MIDI = 96f
private const val MIN_VISIBLE_RANGE = 12f
private const val PITCH_STEP_SEMITONES = 1f
private val NOTE_NAMES = arrayOf(
    "C",
    "C#",
    "D",
    "D#",
    "E",
    "F",
    "F#",
    "G",
    "G#",
    "A",
    "A#",
    "B",
)
