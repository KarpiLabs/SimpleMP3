package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@Composable
fun SearchScreen(
    query: String,
    results: List<TrackEntity>,
    playerState: PlayerUiState,
    onQueryChange: (String) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit = {}
) {
    val palette = LocalSimpleMP3Palette.current
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Songs, artists, albums") },
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
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentTeal,
                unfocusedBorderColor = palette.card,
                focusedContainerColor = palette.card,
                unfocusedContainerColor = palette.card,
                cursorColor = AccentTeal
            )
        )

        if (query.isBlank()) {
            Text(
                text = "Find anything in your local library — same search works on Android Auto.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(24.dp)
            )
        } else if (results.isEmpty()) {
            Text(
                text = "No matches for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
            ) {
                items(results, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = playerState.currentMediaId == "track:${track.id}",
                        onClick = { onPlayTrack(track, results) },
                        onLongClick = { onAddToPlaylist(track) },
                        onFavoriteClick = { onToggleFavorite(track.id) }
                    )
                }
            }
        }
    }
}
