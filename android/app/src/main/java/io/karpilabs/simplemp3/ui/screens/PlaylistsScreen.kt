package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.SmartPlaylist
import io.karpilabs.simplemp3.ui.components.PlaylistListRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatTrackCount

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithMeta>,
    genres: List<AlbumRow>,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (SmartPlaylist) -> Unit,
    onOpenGenre: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    var showCreate by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Manage collections that sync to Android Auto",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "smart-header") {
                    SectionHeader("Smart playlists")
                }
                items(SmartPlaylist.entries.toList(), key = { "smart-${it.key}" }) { smart ->
                    SmartRow(
                        title = smart.displayName,
                        subtitle = smart.description,
                        icon =
                            when (smart) {
                                SmartPlaylist.MOST_PLAYED -> Icons.Rounded.TrendingUp
                                SmartPlaylist.RECENTLY_ADDED -> Icons.Rounded.History
                            },
                        onClick = { onOpenSmart(smart) },
                    )
                }

                if (genres.isNotEmpty()) {
                    item(key = "genre-header") {
                        SectionHeader("Genres")
                    }
                    items(genres, key = { "genre-${it.name}" }) { genre ->
                        SmartRow(
                            title = genre.name,
                            subtitle = formatTrackCount(genre.trackCount),
                            icon = Icons.Rounded.Category,
                            onClick = { onOpenGenre(genre.name) },
                        )
                    }
                }

                item(key = "playlists-header") {
                    SectionHeader("Your playlists")
                }
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistListRow(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 100.dp),
            containerColor = AccentTeal,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New playlist")
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreate = false
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalSimpleMP3Palette.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = palette.textSecondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SmartRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.elevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(28.dp),
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initialName: String = "",
) {
    val palette = LocalSimpleMP3Palette.current
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.card,
        title = {
            Text("New playlist", color = MaterialTheme.colorScheme.onSurface)
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Playlist name") },
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        cursorColor = AccentTeal,
                        focusedLabelColor = AccentTeal,
                    ),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentTeal,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.textSecondary)
            }
        },
    )
}
