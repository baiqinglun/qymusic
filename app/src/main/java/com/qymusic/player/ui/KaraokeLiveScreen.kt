package com.qymusic.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.qymusic.player.data.AppSettings
import com.qymusic.player.data.Track
import com.qymusic.player.playback.KaraokePlayerState
import com.qymusic.player.playback.KaraokeRecordingState
import com.qymusic.player.playback.VocalSplitMode

@Composable
internal fun KaraokeLiveScreen(
    track: Track,
    playerState: KaraokePlayerState,
    lyricsState: LyricsUiState,
    settings: AppSettings,
    artwork: Bitmap,
    blurredCover: Bitmap,
    recordingState: KaraokeRecordingState,
    onStartRecording: () -> Unit,
    onFinishRecording: () -> Unit,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVocalSplitModeChange: (VocalSplitMode) -> Unit,
    onPitchSemitonesChange: (Float) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    var permissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            onStartRecording()
        }
    }

    LaunchedEffect(Unit) {
        if (permissionGranted) {
            onStartRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    KaraokeScreen(
        playerState = playerState,
        lyricsState = lyricsState,
        settings = settings,
        track = track,
        artwork = artwork,
        blurredCover = blurredCover,
        vocalSplitMode = playerState.vocalSplitMode,
        recordingState = recordingState,
        recordingPermissionGranted = permissionGranted,
        onRequestRecordingPermission = {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onBack = onBack,
        onTogglePlayPause = onTogglePlayPause,
        onSeek = onSeek,
        onVocalSplitModeChange = onVocalSplitModeChange,
        onPitchSemitonesChange = onPitchSemitonesChange,
        onPlaybackSpeedChange = onPlaybackSpeedChange,
        onFinishRecording = onFinish,
    )
}
