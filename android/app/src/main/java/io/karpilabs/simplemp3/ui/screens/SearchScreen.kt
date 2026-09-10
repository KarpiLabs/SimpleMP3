package io.karpilabs.simplemp3.ui.screens

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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.search.LibrarySearch
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.AlbumArt
import io.karpilabs.simplemp3.ui.components.SectionHeader
import io.karpilabs.simplemp3.ui.components.SelectionBar
import io.karpilabs.simplemp3.ui.components.TrackActionsMenu
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.components.rememberTrackSelection
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatTrackCount

@Composable
fun SearchScreen(
    query: String,
    results: LibrarySearch.Results,
    playerState: PlayerUiState,
    onQueryChange: (String) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit = {},
    onPlayNext: (TrackEntity) -> Unit = {},
    onAddToQueue: (TrackEntity) -> Unit = {},
    onHide: (TrackEntity) -> Unit = {},
    onOpenAlbum: (String) -> Unit = {},
    onOpenArtist: (String) -> Unit = {},
    onOpenPlaylist: (Long) -> Unit = {},
    onQueueTracks: (List<TrackEntity>) -> Unit = {},
    onPlayNextTracks: (List<TrackEntity>) -> Unit = {},
    onAddTracksToPlaylist: (List<TrackEntity>) -> Unit = {},
    onHideTracks: (List<TrackEntity>) -> Unit = {},
) {
    val palette = LocalSimpleMP3Palette.current
    var actionTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val selection = rememberTrackSelection()
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            placeholder = { Text("Songs, artists, albums, playlists") },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textMuted)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = palette.card,
                    focusedContainerColor = palette.card,
                    unfocusedContainerColor = palette.card,
                    cursorColor = AccentTeal,
                ),
        )

        if (query.isBlank()) {
            Text(
                text = "Find songs, albums, artists, and playlists — same search works on Android Auto.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(24.dp),
            )
        } else if (results.isEmpty) {
            Text(
                text = "No matches for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = 12.dp,
                            bottom = if (selection.isSelecting) 180.dp else 120.dp,
                        ),
                ) {
                    if (results.playlists.isNotEmpty()) {
                        item { SectionHeader("Playlists") }
                        items(results.playlists, key = { "pl-${it.id}" }) { playlist ->
                            SearchHitRow(
                                title = playlist.name,
                                subtitle = formatTrackCount(playlist.trackCount),
                                artworkUri = playlist.displayCover,
                                fallbackIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                    )
                                },
                                onClick = { onOpenPlaylist(playlist.id) },
                            )
                        }
                    }
                    if (results.albums.isNotEmpty()) {
                        item { SectionHeader("Albums") }
                        items(results.albums, key = { "al-${it.name}|${it.subtitle}" }) { album ->
                            SearchHitRow(
                                title = album.name,
                                subtitle = "${album.subtitle} · ${formatTrackCount(album.trackCount)}",
                                artworkUri = album.artworkUri,
                                fallbackIcon = {
                                    Icon(Icons.Rounded.Album, contentDescription = null, tint = AccentTeal)
                                },
                                onClick = { onOpenAlbum(album.name) },
                            )
                        }
                    }
                    if (results.artists.isNotEmpty()) {
                        item { SectionHeader("Artists") }
                        items(results.artists, key = { "ar-${it.name}" }) { artist ->
                            SearchHitRow(
                                title = artist.name,
                                subtitle = formatTrackCount(artist.trackCount),
                                artworkUri = artist.artworkUri,
                                fallbackIcon = {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = AccentTeal)
                                },
                                onClick = { onOpenArtist(artist.name) },
                            )
                        }
                    }
                    if (results.tracks.isNotEmpty()) {
                        item { SectionHeader("Songs") }
                        items(results.tracks, key = { it.id }) { track ->
                            Box {
                                TrackRow(
                                    track = track,
                                    isPlaying = playerState.currentMediaId == "track:${track.id}",
                                    selected = selection.selected(track.id),
                                    selectionMode = selection.isSelecting,
                                    onClick = {
                                        if (selection.isSelecting) {
                                            selection.toggle(track.id)
                                        } else {
                                            onPlayTrack(track, results.tracks)
                                        }
                                    },
                                    onLongClick = { selection.enter(track.id) },
                                    onFavoriteClick = { onToggleFavorite(track.id) },
                                    onMoreClick = { actionTrack = track },
                                )
                                TrackActionsMenu(
                                    expanded = actionTrack?.id == track.id,
                                    track = actionTrack,
                                    onDismiss = { actionTrack = null },
                                    onPlayNext = onPlayNext,
                                    onAddToQueue = onAddToQueue,
                                    onAddToPlaylist = onAddToPlaylist,
                                    onToggleFavorite = { onToggleFavorite(it.id) },
                                    onHide = onHide,
                                    showNeverCompress = false,
                                )
                            }
                        }
                    }
                }
                if (selection.isSelecting) {
                    SelectionBar(
                        count = selection.count,
                        onQueue = {
                            onQueueTracks(selection.selectedTracks(results.tracks))
                            selection.clear()
                        },
                        onPlayNext = {
                            onPlayNextTracks(selection.selectedTracks(results.tracks))
                            selection.clear()
                        },
                        onAddToPlaylist = {
                            onAddTracksToPlaylist(selection.selectedTracks(results.tracks))
                            selection.clear()
                        },
                        onHide = {
                            onHideTracks(selection.selectedTracks(results.tracks))
                            selection.clear()
                        },
                        onSelectAll = { selection.selectAll(results.tracks) },
                        onClear = { selection.clear() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchHitRow(
    title: String,
    subtitle: String,
    artworkUri: String?,
    fallbackIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!artworkUri.isNullOrBlank()) {
                AlbumArt(artworkUri = artworkUri, contentDescription = title, size = 52.dp)
            } else {
                fallbackIcon()
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
