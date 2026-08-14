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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatTrackCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderPath: String,
    subfolders: List<FolderBrowser.FolderEntry>,
    tracks: List<TrackEntity>,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit = {},
) {
    val palette = LocalSimpleMP3Palette.current
    val title = FolderBrowser.displayName(folderPath).ifBlank { "Folders" }
    val subtitle = folderPath.ifBlank { "Browse by path" }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank() && subtitle != title) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
        )

        if (tracks.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(
                    onClick = onPlayAll,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Play all")
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            if (subfolders.isNotEmpty()) {
                item {
                    Text(
                        text = "Folders",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(subfolders, key = { it.path }) { folder ->
                    FolderRow(
                        name = folder.name,
                        subtitle = buildFolderSubtitle(folder),
                        onClick = { onOpenFolder(folder.path) },
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Songs · ${formatTrackCount(tracks.size)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = playerState.currentMediaId == "track:${track.id}",
                        onClick = { onPlayTrack(track) },
                        onLongClick = { onAddToPlaylist(track) },
                        onFavoriteClick = { onToggleFavorite(track.id) },
                    )
                }
            }

            if (subfolders.isEmpty() && tracks.isEmpty()) {
                item {
                    Text(
                        text = "No songs in this folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textMuted,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FolderRow(
    name: String,
    subtitle: String,
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
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.elevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
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

private fun buildFolderSubtitle(folder: FolderBrowser.FolderEntry): String {
    val parts = mutableListOf<String>()
    if (folder.totalTrackCount > 0) {
        parts += formatTrackCount(folder.totalTrackCount)
    }
    if (folder.hasSubfolders) {
        parts += "subfolders"
    }
    return parts.joinToString(" · ").ifBlank { "Empty" }
}
