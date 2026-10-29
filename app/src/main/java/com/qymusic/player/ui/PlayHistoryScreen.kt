package com.qymusic.player.ui

import androidx.compose.foundation.background
import android.graphics.Bitmap
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qymusic.player.R
import com.qymusic.player.data.Track
import com.qymusic.player.ui.MusicViewModel.RecentPlayedTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val historyTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

/** 播放历史：最近播放的歌曲，去重、最新的在最上，排版与歌曲列表一致。 */
@Composable
fun PlayHistoryScreen(
    viewModel: MusicViewModel,
    tracks: List<Track>,
    artworkCache: Map<String, Bitmap?>,
    onRequestArtwork: (Track) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    var items by remember { mutableStateOf(viewModel.recentPlayedTracks()) }
    LaunchedEffect(Unit) {
        items = viewModel.recentPlayedTracks()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.navigation_menu),
                    )
                }
                Text(
                    text = stringResource(R.string.history_page_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 8.dp,
                    ),
                ) {
                    itemsIndexed(
                        items,
                        key = { _, item -> item.track.id + item.playedAtMs },
                    ) { _, item ->
                        val track = tracks.firstOrNull { it.id == item.track.id }
                        HistoryRow(
                            item = item,
                            cover = {
                                if (track != null) {
                                    TrackCoverThumbnail(
                                        track = track,
                                        artworkCache = artworkCache,
                                        onRequestArtwork = onRequestArtwork,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                track?.let {
                                    viewModel.playTrack(it, listOf(it))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: RecentPlayedTrack,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            cover()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = item.track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    item.track.artist.takeIf { it.isNotBlank() },
                    formatHistoryTime(item.playedAtMs),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = formatHistoryDuration(item.listenedMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatHistoryDuration(listenedMs: Long): String {
    if (listenedMs <= 0L) return "0:00"
    val totalSeconds = listenedMs / 1000
    return "%d:%02d".format(java.util.Locale.US, totalSeconds / 60, totalSeconds % 60)
}

private fun formatHistoryTime(playedAtMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(playedAtMs))
