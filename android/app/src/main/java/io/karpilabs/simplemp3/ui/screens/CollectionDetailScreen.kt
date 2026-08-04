package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatTrackCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    tracks: List<TrackEntity>,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onToggleFavorite: (Long) -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1)
                    Text(
                        text = subtitle.ifBlank { formatTrackCount(tracks.size) },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = onPlayAll,
                enabled = tracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Play")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onShuffle,
                enabled = tracks.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = AccentTeal)
                Spacer(Modifier.width(6.dp))
                Text("Shuffle", color = AccentTeal)
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    isPlaying = playerState.currentMediaId == "track:${track.id}",
                    onClick = { onPlayTrack(track) },
                    onFavoriteClick = { onToggleFavorite(track.id) }
                )
            }
        }
    }
}
