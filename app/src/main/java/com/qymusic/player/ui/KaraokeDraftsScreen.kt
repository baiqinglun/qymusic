package com.qymusic.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.KaraokeDraftStore
import com.qymusic.player.data.KaraokeEditProject

private enum class KaraokeDraftViewMode {
    BY_SONG,
    FLAT,
}

@Composable
internal fun KaraokeDraftsScreen(
    drafts: List<KaraokeEditProject>,
    onBack: () -> Unit,
    onOpenDraft: (KaraokeEditProject) -> Unit,
    onDeleteDrafts: (Set<String>) -> Unit,
) {
    var viewMode by rememberSaveable { mutableStateOf(KaraokeDraftViewMode.BY_SONG) }
    var selectedSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showViewModeMenu by remember { mutableStateOf(false) }

    val songGroups = remember(drafts) { drafts.groupBy(KaraokeEditProject::trackId) }
    val selectedSongDrafts = selectedSongId?.let(songGroups::get).orEmpty()
    val groupedRoot = viewMode == KaraokeDraftViewMode.BY_SONG && selectedSongId == null
    val visibleDrafts = when {
        selectedSongId != null -> selectedSongDrafts
        viewMode == KaraokeDraftViewMode.FLAT -> drafts
        else -> emptyList()
    }
    val visibleDraftIds = when {
        selectedSongId != null -> visibleDrafts.mapTo(mutableSetOf()) {
            it.draftId
        }
        viewMode == KaraokeDraftViewMode.FLAT -> drafts.mapTo(mutableSetOf()) {
            it.draftId
        }
        else -> songGroups.values.flatten().mapTo(mutableSetOf()) {
            it.draftId
        }
    }
    val allVisibleSelected = visibleDraftIds.isNotEmpty() &&
        selectedIds.containsAll(visibleDraftIds)
    val selectedSong = selectedSongId?.let { songId ->
        selectedSongDrafts.firstOrNull() ?: drafts.firstOrNull {
            it.trackId == songId
        }
    }

    LaunchedEffect(drafts, selectedSongId) {
        val currentIds = drafts.mapTo(mutableSetOf(), KaraokeEditProject::draftId)
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in currentIds }
        if (selectedIds.isEmpty()) selectionMode = false
        if (selectedSongId != null && selectedSongDrafts.isEmpty()) {
            selectedSongId = null
        }
    }
    BackHandler(enabled = selectionMode || selectedSongId != null) {
        when {
            selectionMode -> {
                selectionMode = false
                selectedIds = emptySet()
            }
            selectedSongId != null -> selectedSongId = null
        }
    }
    val scheme = MaterialTheme.colorScheme

    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        when {
                            selectionMode -> {
                                selectionMode = false
                                selectedIds = emptySet()
                            }
                            selectedSongId != null -> selectedSongId = null
                            else -> onBack()
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedSong?.trackTitle?.ifBlank { null }
                            ?: stringResource(R.string.karaoke_drafts),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (selectionMode) {
                            stringResource(
                                R.string.karaoke_draft_selected_count,
                                selectedIds.size,
                            )
                        } else if (selectedSongId != null) {
                            stringResource(
                                R.string.karaoke_draft_count,
                                selectedSongDrafts.size,
                            )
                        } else {
                            stringResource(R.string.karaoke_draft_count, drafts.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selectionMode) {
                    if (visibleDraftIds.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                selectedIds = if (allVisibleSelected) {
                                    selectedIds - visibleDraftIds
                                } else {
                                    selectedIds + visibleDraftIds
                                }
                                selectionMode = selectedIds.isNotEmpty()
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    if (allVisibleSelected) {
                                        R.string.karaoke_draft_clear_selection
                                    } else {
                                        R.string.karaoke_draft_select_all
                                    },
                                ),
                            )
                        }
                    }
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { showDeleteConfirmation = true }) {
                            Icon(
                                imageVector = Icons.Rounded.Delete,
                                contentDescription = stringResource(
                                    R.string.karaoke_draft_delete_selected,
                                ),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    KaraokeDraftViewModeMenu(
                        viewMode = viewMode,
                        expanded = showViewModeMenu,
                        onExpandedChange = { showViewModeMenu = it },
                        onViewModeChange = {
                            showViewModeMenu = false
                            viewMode = it
                            selectedSongId = null
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                    )
                }
            }

            when {
                drafts.isEmpty() -> KaraokeDraftsEmptyState(
                    modifier = Modifier.weight(1f),
                )
                groupedRoot -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = songGroups.values.toList(),
                        key = { group -> group.first().trackId },
                    ) { group ->
                        val first = group.first()
                        val draftIds = group.mapTo(mutableSetOf()) {
                            it.draftId
                        }
                        KaraokeSongGroupRow(
                            title = first.trackTitle.ifBlank {
                                stringResource(R.string.karaoke_unknown_song)
                            },
                            artist = first.artist.ifBlank {
                                stringResource(R.string.unknown_artist)
                            },
                            draftCount = group.size,
                            latestRecordedAtMs = group.maxOf { it.recordedAtMs },
                            selected = draftIds.all { it in selectedIds },
                            selectionMode = selectionMode,
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + draftIds
                            },
                            onClick = {
                                if (selectionMode) {
                                    val allSelected = draftIds.all { it in selectedIds }
                                    selectedIds = if (allSelected) {
                                        selectedIds - draftIds
                                    } else {
                                        selectedIds + draftIds
                                    }
                                    selectionMode = selectedIds.isNotEmpty()
                                } else {
                                    selectedSongId = first.trackId
                                }
                            },
                            onSelectedChange = { selected ->
                                selectionMode = true
                                selectedIds = if (selected) {
                                    selectedIds + draftIds
                                } else {
                                    selectedIds - draftIds
                                }
                                selectionMode = selectedIds.isNotEmpty()
                            },
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(
                        items = visibleDrafts,
                        key = KaraokeEditProject::draftId,
                    ) { draft ->
                        KaraokeDraftRow(
                            draft = draft,
                            selected = draft.draftId in selectedIds,
                            selectionMode = selectionMode,
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + draft.draftId
                            },
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = selectedIds.toggle(draft.draftId)
                                    selectionMode = selectedIds.isNotEmpty()
                                } else {
                                    onOpenDraft(draft)
                                }
                            },
                            onSelectedChange = { selected ->
                                selectionMode = true
                                selectedIds = selectedIds.withSelected(
                                    draft.draftId,
                                    selected,
                                )
                                selectionMode = selectedIds.isNotEmpty()
                            },
                        )
                    }
                }
            }
        }
        }

        if (showDeleteConfirmation) {
            AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = stringResource(R.string.karaoke_draft_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.karaoke_draft_delete_message,
                        selectedIds.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDrafts(selectedIds)
                        selectedIds = emptySet()
                        selectionMode = false
                        showDeleteConfirmation = false
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
}

@Composable
private fun KaraokeDraftViewModeMenu(
    viewMode: KaraokeDraftViewMode,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onViewModeChange: (KaraokeDraftViewMode) -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                imageVector = if (viewMode == KaraokeDraftViewMode.BY_SONG) {
                    Icons.Rounded.LibraryMusic
                } else {
                    Icons.AutoMirrored.Rounded.ViewList
                },
                contentDescription = stringResource(
                    R.string.karaoke_draft_switch_view,
                ),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.karaoke_draft_view_by_song)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (viewMode == KaraokeDraftViewMode.BY_SONG) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
                onClick = { onViewModeChange(KaraokeDraftViewMode.BY_SONG) },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.karaoke_draft_view_flat)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ViewList,
                        contentDescription = null,
                    )
                },
                trailingIcon = if (viewMode == KaraokeDraftViewMode.FLAT) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                        )
                    }
                } else {
                    null
                },
                onClick = { onViewModeChange(KaraokeDraftViewMode.FLAT) },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun KaraokeSongGroupRow(
    title: String,
    artist: String,
    draftCount: Int,
    latestRecordedAtMs: Long,
    selected: Boolean,
    selectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    val selectLabel = stringResource(R.string.karaoke_draft_select)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = selectLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = title },
            )
        }
        Surface(
            modifier = Modifier.size(52.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
            Text(
                text = stringResource(
                    R.string.karaoke_draft_group_summary,
                    draftCount,
                    KaraokeDraftStore.formatRecordedAt(latestRecordedAtMs),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun KaraokeDraftRow(
    draft: KaraokeEditProject,
    selected: Boolean,
    selectionMode: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    val selectLabel = stringResource(R.string.karaoke_draft_select)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = selectLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = draft.draftName.ifBlank {
                            draft.trackTitle
                        }
                    },
            )
        }
        Surface(
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = draft.draftName.ifBlank {
                    KaraokeDraftStore.buildDraftName(
                        draft.trackTitle,
                        draft.recordedAtMs,
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.karaoke_draft_source,
                    draft.trackTitle.ifBlank {
                        stringResource(R.string.karaoke_unknown_song)
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.karaoke_draft_recorded_at,
                    KaraokeDraftStore.formatRecordedAt(draft.recordedAtMs),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (draft.artist.isNotBlank()) {
                Text(
                    text = draft.artist,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatDuration(draft.durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun KaraokeDraftsEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Drafts,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.karaoke_no_drafts),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.karaoke_draft_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun Set<String>.withSelected(value: String, selected: Boolean): Set<String> =
    if (selected) this + value else this - value
