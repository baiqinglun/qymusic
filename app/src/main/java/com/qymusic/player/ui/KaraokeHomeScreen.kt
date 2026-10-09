package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Drafts
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.Track

@Composable
internal fun KaraokeHomeScreen(
    tracks: List<Track>,
    artworkCache: Map<String, Bitmap?>,
    lyricsAvailability: Map<String, Boolean>,
    onRequestArtwork: (Track) -> Unit,
    onRequestLyricsAvailability: (Track) -> Unit,
    onBack: () -> Unit,
    onOpenDrafts: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartKaraoke: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredTracks = remember(tracks, query) {
        val keyword = query.trim()
        if (keyword.isEmpty()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.contains(keyword, ignoreCase = true) ||
                    track.artist.contains(keyword, ignoreCase = true) ||
                    track.album.contains(keyword, ignoreCase = true)
            }
        }
    }
    BackHandler(onBack = onBack)

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
                    text = stringResource(R.string.karaoke),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onOpenDrafts) {
                    Icon(
                        imageVector = Icons.Rounded.Drafts,
                        contentDescription = stringResource(R.string.karaoke_drafts),
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.settings),
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.karaoke_search_hint)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(
                    items = filteredTracks,
                    key = Track::id,
                ) { track ->
                    LaunchedEffect(track.id) {
                        onRequestArtwork(track)
                        onRequestLyricsAvailability(track)
                    }
                    KaraokeTrackRow(
                        track = track,
                        artwork = artworkCache[track.id],
                        hasLyrics = lyricsAvailability[track.id] ?: (track.lyricUri != null),
                        onKaraoke = { onStartKaraoke(track) },
                    )
                }
            }
        }
        }
}

@Composable
private fun KaraokeTrackRow(
    track: Track,
    artwork: Bitmap?,
    hasLyrics: Boolean,
    onKaraoke: () -> Unit,
) {
    val karaokeDescription = stringResource(R.string.karaoke)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onKaraoke)
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
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
        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Icon(
            imageVector = if (hasLyrics) {
                Icons.Rounded.Lyrics
            } else {
                Icons.Rounded.SubtitlesOff
            },
            contentDescription = stringResource(
                if (hasLyrics) R.string.karaoke_has_lyrics else R.string.karaoke_no_lyrics,
            ),
            modifier = Modifier.size(20.dp),
            tint = if (hasLyrics) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            },
        )
        IconButton(
            onClick = onKaraoke,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = karaokeDescription },
        ) {
            KaraokeBadgeIcon()
        }
    }
}

@Composable
private fun KaraokeBadgeIcon() {
    Box(modifier = Modifier.size(26.dp)) {
        Icon(
            imageVector = Icons.Rounded.RecordVoiceOver,
            contentDescription = null,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.TopStart),
            tint = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier
                .size(13.dp)
                .align(Alignment.BottomEnd),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
