package com.qymusic.player.ui

import android.graphics.Bitmap
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.KaraokeEditProject
import com.qymusic.player.data.KaraokeExportFormat
import com.qymusic.player.data.KaraokePublishSelection
import com.qymusic.player.data.KaraokePublishState
import com.qymusic.player.data.KaraokeVoiceEffect
import com.qymusic.player.lyrics.LrcParser
import com.qymusic.player.lyrics.Lyrics
import com.qymusic.player.playback.KaraokePreviewPlaybackState
import com.qymusic.player.playback.KaraokeRecordingPhase
import com.qymusic.player.playback.KaraokeRecordingState
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun KaraokeEditorScreen(
    recordingState: KaraokeRecordingState,
    draft: KaraokeEditProject?,
    playbackState: KaraokePreviewPlaybackState,
    publishState: KaraokePublishState,
    trackTitle: String,
    artist: String,
    artwork: Bitmap?,
    blurredCover: Bitmap,
    defaultLyrics: String,
    onRerecord: () -> Unit,
    onDelete: () -> Unit,
    onDraftChange: (KaraokeEditProject) -> Unit,
    onUpdatePreview: (KaraokeEditProject, Boolean) -> Unit,
    onTogglePreviewPlayback: (KaraokeEditProject) -> Unit,
    onSeekPreview: (KaraokeEditProject, Long) -> Unit,
    onSaveDraft: (KaraokeEditProject) -> Unit,
    onPublish: (KaraokeEditProject) -> Unit,
) {
    val context = LocalContext.current
    var localDraft by remember(draft?.voicePath) { mutableStateOf(draft) }
    var activeSectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    fun update(
        updated: KaraokeEditProject,
        restart: Boolean = false,
    ) {
        localDraft = updated
        onDraftChange(updated)
        onUpdatePreview(updated, restart)
    }
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            localDraft?.let { project ->
                update(project.copy(coverUri = uri.toString()))
            }
        }
    }
    LaunchedEffect(draft) {
        if (draft != null) localDraft = draft
    }

    val scheme = KaraokeEditorColorScheme.copy(
        primary = MaterialTheme.colorScheme.primary,
        onPrimary = MaterialTheme.colorScheme.onPrimary,
        primaryContainer = MaterialTheme.colorScheme.primaryContainer,
        onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    MaterialTheme(colorScheme = scheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            KaraokeBackdrop(
                blurredCover = blurredCover,
                scrimAlpha = 0.58f,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                KaraokeEditorTopBar(
                    onRerecord = onRerecord,
                )
                when {
                    recordingState.phase == KaraokeRecordingPhase.PROCESSING &&
                        localDraft == null -> KaraokeEditorLoading()

                    localDraft == null -> KaraokeEditorLoading()

                    else -> {
                        val project = localDraft ?: return@Column
                        val lyricsText = project.exportLyrics.ifBlank { defaultLyrics }
                        val lyrics = remember(lyricsText) {
                            LrcParser.parse(lyricsText)
                        }
                        val activeSection = KaraokeEditorSection.entries.firstOrNull {
                            it.name == activeSectionName
                        }
                        LaunchedEffect(defaultLyrics, project.trackId) {
                            if (project.exportLyrics.isBlank() && defaultLyrics.isNotBlank()) {
                                update(project.copy(exportLyrics = defaultLyrics))
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            KaraokeEditorTrackSummary(
                                trackTitle = trackTitle,
                                artist = artist,
                                artwork = artwork,
                                score = recordingState.score,
                                pitchScore = recordingState.pitchScore,
                                stabilityScore = recordingState.stabilityScore,
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            )
                            KaraokePitchLadder(
                                points = recordingState.points,
                                positionMs = playbackState.positionMs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.75f),
                            )
                            KaraokePublishRangeEditor(
                                project = project,
                                playbackState = playbackState,
                                onProjectChange = { updated ->
                                    update(updated, restart = true)
                                },
                                onTogglePlayback = {
                                    onTogglePreviewPlayback(project)
                                },
                                onSeek = { positionMs ->
                                    onSeekPreview(project, positionMs)
                                },
                            )
                            KaraokeLyricsPreview(
                                lyrics = lyrics,
                                positionMs = playbackState.positionMs,
                                onSeek = { positionMs ->
                                    onSeekPreview(project, positionMs)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1.25f),
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            )
                            KaraokeGenerateButton(
                                project = project,
                                publishState = publishState,
                                onPublish = onPublish,
                            )
                            KaraokeEditorSettingsBar(
                                activeSection = activeSection,
                                onSectionClick = { section ->
                                    activeSectionName = section.name
                                },
                            )
                            publishState.message?.let { message ->
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (publishState.isError) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        activeSection?.let { section ->
                            KaraokeSettingsBottomSheet(
                                section = section,
                                project = project,
                                defaultLyrics = defaultLyrics,
                                onDismiss = { activeSectionName = null },
                                onProjectChange = { updated, restart ->
                                    update(updated, restart)
                                },
                                onChooseCover = {
                                    coverPicker.launch(arrayOf("image/*"))
                                },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !publishState.isPublishing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(text = stringResource(R.string.delete))
                            }
                            OutlinedButton(
                                onClick = { onSaveDraft(project) },
                                enabled = !publishState.isPublishing,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 52.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = stringResource(R.string.karaoke_save_draft),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(text = stringResource(R.string.karaoke_delete_recording_title))
            },
            text = {
                Text(text = stringResource(R.string.karaoke_delete_recording_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun KaraokeLyricsPreview(
    lyrics: Lyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val synchronized = lyrics.isSynchronized && lyrics.lines.isNotEmpty()
    val activeIndex = if (synchronized) {
        LrcParser.findActiveLine(lyrics.lines, positionMs)
    } else {
        -1
    }
    val displayLines = remember(lyrics) {
        if (synchronized) {
            lyrics.lines.map { line ->
                KaraokeLyricDisplayLine(
                    text = line.text,
                    timeMs = line.timeMs,
                )
            }
        } else {
            lyrics.rawText
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { text ->
                    KaraokeLyricDisplayLine(
                        text = text,
                        timeMs = null,
                    )
                }
                .toList()
        }
    }
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
            if (displayLines.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_lyrics),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = displayLines,
                        key = { index, _ -> index },
                    ) { index, line ->
                        val active = index == activeIndex
                        Text(
                            text = line.text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) {
                                        primary.copy(alpha = 0.16f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable(
                                    enabled = line.timeMs != null,
                                    role = androidx.compose.ui.semantics.Role.Button,
                                    onClick = {
                                        line.timeMs?.let(onSeek)
                                    },
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            style = if (active) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = if (active) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (active) primary else onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
    }
}

private data class KaraokeLyricDisplayLine(
    val text: String,
    val timeMs: Long?,
)

@Composable
private fun KaraokeGenerateButton(
    project: KaraokeEditProject,
    publishState: KaraokePublishState,
    onPublish: (KaraokeEditProject) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = { onPublish(project) },
            enabled = !publishState.isPublishing,
            modifier = Modifier
                .width(148.dp)
                .heightIn(min = 42.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (publishState.isPublishing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(text = stringResource(R.string.karaoke_generate))
            }
        }
    }
}

private enum class KaraokeEditorSection(
    val labelRes: Int,
    val icon: ImageVector,
) {
    VOLUME(R.string.karaoke_tab_volume, Icons.AutoMirrored.Rounded.VolumeUp),
    EFFECT(R.string.karaoke_tab_effect, Icons.Rounded.AutoAwesome),
    FORMAT(R.string.karaoke_tab_format, Icons.Rounded.AudioFile),
    TAGS(R.string.karaoke_tab_tags, Icons.AutoMirrored.Rounded.Label),
}

@Composable
private fun karaokeFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun KaraokeEditorSettingsBar(
    activeSection: KaraokeEditorSection?,
    onSectionClick: (KaraokeEditorSection) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KaraokeEditorSection.entries.forEach { section ->
            val selected = section == activeSection
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = { onSectionClick(section) },
                    )
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = section.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(section.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun KaraokeSettingsBottomSheet(
    section: KaraokeEditorSection,
    project: KaraokeEditProject,
    defaultLyrics: String,
    onDismiss: () -> Unit,
    onProjectChange: (KaraokeEditProject, Boolean) -> Unit,
    onChooseCover: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outline,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(section.labelRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (section) {
                KaraokeEditorSection.VOLUME -> KaraokeVolumeSettings(
                    project = project,
                    onProjectChange = onProjectChange,
                )

                KaraokeEditorSection.EFFECT -> KaraokeEffectSettings(
                    project = project,
                    onProjectChange = onProjectChange,
                )

                KaraokeEditorSection.FORMAT -> KaraokeFormatSettings(
                    project = project,
                    onProjectChange = { updated ->
                        onProjectChange(updated, false)
                    },
                )

                KaraokeEditorSection.TAGS -> KaraokeTagSettings(
                    project = project,
                    defaultLyrics = defaultLyrics,
                    onProjectChange = { updated ->
                        onProjectChange(updated, false)
                    },
                    onChooseCover = onChooseCover,
                )
            }
        }
    }
}

@Composable
private fun KaraokeVolumeSettings(
    project: KaraokeEditProject,
    onProjectChange: (KaraokeEditProject, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KaraokeEditorSlider(
            label = stringResource(R.string.karaoke_vocal_volume),
            value = project.vocalVolume,
            valueRange = 0f..1.5f,
            valueFormatter = { "${(it * 100).roundToInt()}%" },
            onValueChange = {
                onProjectChange(project.copy(vocalVolume = it), false)
            },
        )
        KaraokeEditorSlider(
            label = stringResource(R.string.karaoke_music_volume),
            value = project.musicVolume,
            valueRange = 0f..1.5f,
            valueFormatter = { "${(it * 100).roundToInt()}%" },
            onValueChange = {
                onProjectChange(project.copy(musicVolume = it), false)
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = androidx.compose.ui.semantics.Role.Switch,
                    onClick = {
                        val enabled = !project.vocalOffsetEnabled
                        onProjectChange(
                            project.copy(
                                vocalOffsetEnabled = enabled,
                                vocalOffsetMs = if (enabled) {
                                    project.vocalOffsetMs
                                } else {
                                    0L
                                },
                            ),
                            true,
                        )
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.karaoke_vocal_offset),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = project.vocalOffsetEnabled,
                onCheckedChange = { enabled ->
                    onProjectChange(
                        project.copy(
                            vocalOffsetEnabled = enabled,
                            vocalOffsetMs = if (enabled) {
                                project.vocalOffsetMs
                            } else {
                                0L
                            },
                        ),
                        true,
                    )
                },
            )
        }
        if (project.vocalOffsetEnabled) {
            KaraokeEditorSlider(
                label = stringResource(R.string.karaoke_vocal_offset_time),
                value = project.vocalOffsetMs.toFloat(),
                valueRange = -2_000f..2_000f,
                valueFormatter = ::formatOffset,
                onValueChange = {
                    onProjectChange(
                        project.copy(
                            vocalOffsetMs = it.roundToInt().toLong(),
                        ),
                        true,
                    )
                },
            )
        }
    }
}

@Composable
private fun KaraokeEffectSettings(
    project: KaraokeEditProject,
    onProjectChange: (KaraokeEditProject, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.karaoke_voice_effect),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KaraokeVoiceEffect.entries.forEach { effect ->
                FilterChip(
                    selected = project.effect == effect,
                    onClick = {
                        onProjectChange(
                            project.copy(effect = effect),
                            true,
                        )
                    },
                    label = {
                        Text(text = effect.label())
                    },
                    colors = karaokeFilterChipColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KaraokeFormatSettings(
    project: KaraokeEditProject,
    onProjectChange: (KaraokeEditProject) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.karaoke_export_format),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KaraokeExportFormat.entries.forEach { format ->
                FilterChip(
                    selected = project.exportFormat == format,
                    onClick = {
                        onProjectChange(project.copy(exportFormat = format))
                    },
                    label = {
                        Text(text = format.name)
                    },
                    colors = karaokeFilterChipColors(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun KaraokeTagSettings(
    project: KaraokeEditProject,
    defaultLyrics: String,
    onProjectChange: (KaraokeEditProject) -> Unit,
    onChooseCover: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.karaoke_metadata),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        KaraokeTagField(
            label = stringResource(R.string.karaoke_tag_title),
            value = project.exportTitle,
            singleLine = true,
            onValueChange = {
                onProjectChange(project.copy(exportTitle = it))
            },
        )
        KaraokeTagField(
            label = stringResource(R.string.karaoke_tag_artist),
            value = project.exportArtist,
            singleLine = true,
            onValueChange = {
                onProjectChange(project.copy(exportArtist = it))
            },
        )
        KaraokeTagField(
            label = stringResource(R.string.karaoke_tag_original_singer),
            value = project.exportOriginalSinger,
            singleLine = true,
            onValueChange = {
                onProjectChange(project.copy(exportOriginalSinger = it))
            },
        )
        KaraokeTagField(
            label = stringResource(R.string.karaoke_tag_lyrics),
            value = project.exportLyrics.ifBlank { defaultLyrics },
            singleLine = false,
            onValueChange = {
                onProjectChange(project.copy(exportLyrics = it))
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (project.coverUri == null) {
                    stringResource(R.string.karaoke_cover_default)
                } else {
                    stringResource(R.string.karaoke_cover_selected)
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onChooseCover) {
                Text(
                    text = stringResource(R.string.karaoke_choose_cover),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (project.coverUri != null) {
                Spacer(modifier = Modifier.size(8.dp))
                OutlinedButton(
                    onClick = {
                        onProjectChange(project.copy(coverUri = null))
                    },
                ) {
                    Text(
                        text = stringResource(R.string.karaoke_reset_cover),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokeEditorTopBar(
    onRerecord: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.karaoke_editor),
            modifier = Modifier.padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onRerecord,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.karaoke_rerecord),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun KaraokeEditorLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun KaraokeEditorTrackSummary(
    trackTitle: String,
    artist: String,
    artwork: Bitmap?,
    score: Int?,
    pitchScore: Int?,
    stabilityScore: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(bitmap = artwork, modifier = Modifier.size(64.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = score?.toString() ?: "--",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${pitchScore ?: "--"} / ${stabilityScore ?: "--"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KaraokeEditorSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueFormatter(safeValue),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        QySliderTrack(
            value = safeValue,
            valueRange = valueRange,
            neutralValue = null,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KaraokeTagField(
    label: String,
    value: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        maxLines = if (singleLine) 1 else 6,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

private enum class KaraokeRangeTarget {
    START,
    END,
    PLAYBACK,
}

@Composable
private fun KaraokePublishRangeEditor(
    project: KaraokeEditProject,
    playbackState: KaraokePreviewPlaybackState,
    onProjectChange: (KaraokeEditProject) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val currentProject = rememberUpdatedState(project)
    val currentPlaybackState = rememberUpdatedState(playbackState)
    val currentOnProjectChange = rememberUpdatedState(onProjectChange)
    val currentOnSeek = rememberUpdatedState(onSeek)
    val edgePaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        14.dp.toPx()
    }
    val handleHitSlopPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        30.dp.toPx()
    }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val rangeDescription = stringResource(R.string.karaoke_publish_progress)
    val displayDurationMs = project.durationMs.coerceAtLeast(0L)
    val displayPositionMs = playbackState.positionMs.coerceIn(
        0L,
        displayDurationMs.coerceAtLeast(1L),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) {
                        Icons.Rounded.Pause
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = stringResource(
                        if (playbackState.isPlaying) {
                            R.string.pause
                        } else {
                            R.string.play
                        },
                    ),
                    tint = onSurfaceColor,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${formatDuration(displayPositionMs)} / " +
                        formatDuration(displayDurationMs),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryColor,
                    textAlign = TextAlign.Center,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .semantics { contentDescription = rangeDescription }
                        .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val width = size.width.toFloat()
                            val travel = (width - edgePaddingPx * 2f).coerceAtLeast(1f)
                            val duration = currentProject.value.durationMs.coerceAtLeast(1L)
                            val startMs = currentProject.value.effectiveTrimStartMs()
                            val endMs = currentProject.value.effectiveTrimEndMs()
                            val progressMs = currentPlaybackState.value.positionMs
                                .coerceIn(0L, duration)

                            fun xFor(positionMs: Long): Float =
                                edgePaddingPx +
                                    travel * positionMs.toFloat() / duration.toFloat()

                            fun millisecondsAt(x: Float): Long =
                                (
                                    duration.toFloat() *
                                        (
                                            (x - edgePaddingPx) / travel
                                            ).coerceIn(0f, 1f)
                                    ).roundToInt().toLong()

                            val startX = xFor(startMs)
                            val endX = xFor(endMs)
                            val progressX = xFor(progressMs)
                            val startDistance = abs(down.position.x - startX)
                            val endDistance = abs(down.position.x - endX)
                            val target = when {
                                startDistance <= handleHitSlopPx &&
                                    startDistance <= endDistance -> KaraokeRangeTarget.START

                                endDistance <= handleHitSlopPx ->
                                    KaraokeRangeTarget.END

                                else -> KaraokeRangeTarget.PLAYBACK
                            }
                            var dragged = false

                            fun applyPosition(x: Float) {
                                val current = currentProject.value
                                val currentDuration = current.durationMs
                                    .coerceAtLeast(1L)
                                val positionMs = millisecondsAt(x)
                                    .coerceIn(0L, currentDuration)
                                when (target) {
                                    KaraokeRangeTarget.START -> {
                                        val end = current.effectiveTrimEndMs()
                                        val maxStart = (end - MIN_KARAOKE_RANGE_MS)
                                            .coerceAtLeast(0L)
                                        currentOnProjectChange.value(
                                            current.copy(
                                                publishSelection =
                                                    KaraokePublishSelection.SELECTED,
                                                trimStartMs = positionMs.coerceAtMost(maxStart),
                                                trimEndMs = end,
                                            ),
                                        )
                                    }

                                    KaraokeRangeTarget.END -> {
                                        val start = current.effectiveTrimStartMs()
                                        val minEnd = (start + MIN_KARAOKE_RANGE_MS)
                                            .coerceAtMost(currentDuration)
                                        currentOnProjectChange.value(
                                            current.copy(
                                                publishSelection =
                                                    KaraokePublishSelection.SELECTED,
                                                trimStartMs = start,
                                                trimEndMs = positionMs.coerceAtLeast(minEnd),
                                            ),
                                        )
                                    }

                                    KaraokeRangeTarget.PLAYBACK ->
                                        currentOnSeek.value(positionMs)
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull {
                                    it.id == down.id
                                } ?: break
                                if (!change.pressed) break
                                val delta = change.position - down.position
                                val horizontalDrag = abs(delta.x) > viewConfiguration.touchSlop &&
                                    abs(delta.x) > abs(delta.y)
                                if (horizontalDrag) {
                                    dragged = true
                                    change.consume()
                                    applyPosition(change.position.x)
                                }
                            }
                            if (!dragged) {
                                applyPosition(down.position.x)
                            }
                        }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                    val centerY = size.height / 2f
                    val duration = project.durationMs.coerceAtLeast(1L)
                    val startFraction = (
                        project.effectiveTrimStartMs().toFloat() / duration.toFloat()
                        ).coerceIn(0f, 1f)
                    val endFraction = (
                        project.effectiveTrimEndMs().toFloat() / duration.toFloat()
                        ).coerceIn(0f, 1f)
                    val progressFraction = (
                        playbackState.positionMs.toFloat() / duration.toFloat()
                        ).coerceIn(0f, 1f)
                    val travel = (size.width - edgePaddingPx * 2f).coerceAtLeast(1f)

                    fun xFor(fraction: Float): Float =
                        edgePaddingPx + travel * fraction

                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.26f),
                        start = Offset(edgePaddingPx, centerY),
                        end = Offset(size.width - edgePaddingPx, centerY),
                        strokeWidth = 3.dp.toPx(),
                    )
                    drawLine(
                        color = onSurfaceColor,
                        start = Offset(xFor(startFraction), centerY),
                        end = Offset(xFor(endFraction), centerY),
                        strokeWidth = 5.dp.toPx(),
                    )
                    drawRangeHandle(
                        x = xFor(startFraction),
                        centerY = centerY,
                        color = onSurfaceColor,
                    )
                    drawRangeHandle(
                        x = xFor(endFraction),
                        centerY = centerY,
                        color = onSurfaceColor,
                    )
                    val progressX = xFor(progressFraction)
                    drawCircle(
                        color = onSurfaceColor,
                        radius = 6.dp.toPx(),
                        center = Offset(progressX, centerY),
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.68f),
                        radius = 8.dp.toPx(),
                        center = Offset(progressX, centerY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(),
                        ),
                    )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatDuration(0L),
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceVariantColor,
                    )
                    Text(
                        text = formatDuration(displayDurationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = onSurfaceVariantColor,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRangeHandle(
    x: Float,
    centerY: Float,
    color: Color,
) {
    val path = Path().apply {
        moveTo(x, centerY - 14.dp.toPx())
        lineTo(x - 7.dp.toPx(), centerY + 2.dp.toPx())
        lineTo(x + 7.dp.toPx(), centerY + 2.dp.toPx())
        close()
    }
    drawPath(path = path, color = color)
}

private fun KaraokeEditProject.effectiveTrimStartMs(): Long =
    if (publishSelection == KaraokePublishSelection.SELECTED) {
        trimStartMs.coerceIn(0L, durationMs.coerceAtLeast(1L))
    } else {
        0L
    }

private fun KaraokeEditProject.effectiveTrimEndMs(): Long {
    val duration = durationMs.coerceAtLeast(1L)
    return if (publishSelection == KaraokePublishSelection.SELECTED) {
        trimEndMs.coerceIn(effectiveTrimStartMs(), duration)
    } else {
        duration
    }
}

private const val MIN_KARAOKE_RANGE_MS = 500L

@Composable
private fun KaraokeVoiceEffect.label(): String = stringResource(
    when (this) {
        KaraokeVoiceEffect.STUDIO -> R.string.karaoke_effect_studio
        KaraokeVoiceEffect.HALL -> R.string.karaoke_effect_hall
        KaraokeVoiceEffect.DISTANT -> R.string.karaoke_effect_distant
        KaraokeVoiceEffect.ELECTRONIC -> R.string.karaoke_effect_electronic
    },
)

private fun formatOffset(value: Float): String {
    val milliseconds = value.roundToInt()
    val sign = if (milliseconds > 0) "+" else ""
    return "$sign${milliseconds}ms"
}
