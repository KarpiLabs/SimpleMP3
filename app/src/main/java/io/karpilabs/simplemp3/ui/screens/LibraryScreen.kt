package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.AlbumArt
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.NightCard
import io.karpilabs.simplemp3.ui.theme.NightElevated
import io.karpilabs.simplemp3.ui.theme.TextMuted
import io.karpilabs.simplemp3.ui.theme.TextSecondary
import io.karpilabs.simplemp3.ui.util.formatTrackCount

@Composable
fun LibraryScreen(
    tracks: List<TrackEntity>,
    albums: List<AlbumRow>,
    artists: List<AlbumRow>,
    rootFolders: List<FolderBrowser.FolderEntry>,
    filter: String,
    playerState: PlayerUiState,
    onFilterChange: (String) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Folders")
    val q = filter.trim()

    // In-memory filter is instant (no Room round-trip) once the list is warm.
    val filteredTracks = remember(tracks, q) {
        if (q.isEmpty()) tracks
        else tracks.filter {
            it.title.contains(q, true) ||
                it.artist.contains(q, true) ||
                it.album.contains(q, true)
        }
    }
    val filteredAlbums = remember(albums, q) {
        if (q.isEmpty()) albums
        else albums.filter {
            it.name.contains(q, true) || it.subtitle.contains(q, true)
        }
    }
    val filteredArtists = remember(artists, q) {
        if (q.isEmpty()) artists
        else artists.filter { it.name.contains(q, true) }
    }
    val filteredFolders = remember(rootFolders, q) {
        if (q.isEmpty()) rootFolders
        else rootFolders.filter {
            it.name.contains(q, true) || it.path.contains(q, true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Filter this tab…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = TextMuted) },
            trailingIcon = {
                if (filter.isNotEmpty()) {
                    IconButton(onClick = { onFilterChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = NightCard,
                focusedContainerColor = NightCard,
                unfocusedContainerColor = NightCard,
                cursorColor = AccentTeal
            )
        )

        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = AccentTeal,
            edgePadding = 16.dp,
            indicator = { positions ->
                SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[tab]),
                    color = AccentTeal
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            title,
                            color = if (tab == index) AccentTeal else TextSecondary
                        )
                    }
                )
            }
        }

        when (tab) {
            0 -> LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(filteredTracks, key = { it.id }, contentType = { "track" }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = playerState.currentMediaId == "track:${track.id}",
                        onClick = { onPlayTrack(track, filteredTracks) },
                        onLongClick = { onAddToPlaylist(track) },
                        onFavoriteClick = { onToggleFavorite(track.id) }
                    )
                }
            }
            1 -> LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(
                    filteredAlbums,
                    key = { "${it.name}|${it.subtitle}" },
                    contentType = { "album" }
                ) { album ->
                    MediaCollectionRow(
                        title = album.name,
                        subtitle = "${album.subtitle} · ${formatTrackCount(album.trackCount)}",
                        artworkUri = album.artworkUri,
                        onClick = { onOpenAlbum(album.name) }
                    )
                }
            }
            2 -> LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(filteredArtists, key = { it.name }, contentType = { "artist" }) { artist ->
                    MediaCollectionRow(
                        title = artist.name,
                        subtitle = formatTrackCount(artist.trackCount),
                        artworkUri = artist.artworkUri,
                        onClick = { onOpenArtist(artist.name) }
                    )
                }
            }
            3 -> LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                if (filteredFolders.isEmpty()) {
                    item {
                        Text(
                            text = if (q.isNotEmpty()) {
                                "No folders match “$q”"
                            } else {
                                "No folders yet — rescan the library from Home"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    items(filteredFolders, key = { it.path }, contentType = { "folder" }) { folder ->
                        FolderRow(
                            name = folder.name,
                            subtitle = buildRootFolderSubtitle(folder),
                            onClick = { onOpenFolder(folder.path) }
                        )
                    }
                }
            }
        }
    }
}

private fun buildRootFolderSubtitle(folder: FolderBrowser.FolderEntry): String {
    val parts = mutableListOf<String>()
    if (folder.totalTrackCount > 0) parts += formatTrackCount(folder.totalTrackCount)
    if (folder.hasSubfolders) parts += "subfolders"
    return parts.joinToString(" · ").ifBlank { folder.path }
}

@Composable
private fun MediaCollectionRow(
    title: String,
    subtitle: String,
    artworkUri: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NightElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AlbumArt(artworkUri = null, contentDescription = title, size = 56.dp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
