package com.qymusic.player.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.Track
import com.qymusic.player.data.TrackPlaybackStats

@Composable
fun RecommendationScreen(
    tracks: List<Track>,
    trackStats: List<TrackPlaybackStats>,
    artworkCache: Map<String, Bitmap?>,
    playerState: PlayerUiState,
    onRequestArtwork: (Track) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val tracksById = remember(tracks) { tracks.associateBy(Track::id) }
    val recentTracks = remember(trackStats, tracksById) {
        trackStats
            .sortedWith(
                compareByDescending<TrackPlaybackStats> { it.lastPlayedAt }
                    .thenByDescending { it.playCount },
            )
            .mapNotNull { tracksById[it.trackId] }
            .distinctBy(Track::id)
            .take(8)
    }
    val recommendationQueue = remember(tracks, recentTracks) {
        val recentIds = recentTracks.mapTo(mutableSetOf(), Track::id)
        tracks.filterNot { it.id in recentIds }.take(30)
            .ifEmpty { recentTracks }
            .ifEmpty { tracks.take(30) }
    }
    val currentTrack = playerState.currentTrack

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.padding(
                    start = 20.dp,
                    top = 20.dp,
                    end = 20.dp,
                    bottom = 12.dp,
                ),
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.navigation_menu),
                    )
                }
                Text(
                    text = stringResource(R.string.recommend_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.recommend_summary, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (currentTrack != null) {
            item(key = "continue") {
                RecommendationSectionTitle(
                    text = stringResource(R.string.recommend_continue),
                )
                RecommendationTrackRow(
                    track = currentTrack,
                    artwork = artworkCache[currentTrack.id],
                    isPlaying = playerState.showPauseIcon,
                    onClick = onOpenPlayer,
                    onRequestArtwork = onRequestArtwork,
                    trailing = {
                        IconButton(onClick = onTogglePlayPause) {
                            Icon(
                                imageVector = if (playerState.showPauseIcon) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (playerState.showPauseIcon) {
                                        R.string.pause
                                    } else {
                                        R.string.play
                                    },
                                ),
                            )
                        }
                    },
                )
            }
        }

        if (recentTracks.isNotEmpty()) {
            item(key = "recent-header") {
                RecommendationSectionTitle(
                    text = stringResource(R.string.recommend_recent),
                )
            }
            items(
                items = recentTracks,
                key = { track -> "recent-${track.id}" },
            ) { track ->
                RecommendationTrackRow(
                    track = track,
                    artwork = artworkCache[track.id],
                    isPlaying = currentTrack?.id == track.id &&
                        playerState.showPauseIcon,
                    onClick = { onPlayTrack(track, recentTracks) },
                    onRequestArtwork = onRequestArtwork,
                )
            }
        }

        item(key = "recommend-header") {
            RecommendationSectionTitle(
                text = stringResource(R.string.recommend_for_you),
            )
        }
        if (recommendationQueue.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.recommend_empty),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(
                items = recommendationQueue,
                key = { track -> "recommend-${track.id}" },
            ) { track ->
                RecommendationTrackRow(
                    track = track,
                    artwork = artworkCache[track.id],
                    isPlaying = currentTrack?.id == track.id &&
                        playerState.showPauseIcon,
                    onClick = { onPlayTrack(track, recommendationQueue) },
                    onRequestArtwork = onRequestArtwork,
                )
            }
        }
    }
}

@Composable
private fun RecommendationSectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 6.dp,
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun RecommendationTrackRow(
    track: Track,
    artwork: Bitmap?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onRequestArtwork: (Track) -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (artwork == null) onRequestArtwork(track)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPlaying) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(track.artist, track.album)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
