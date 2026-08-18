package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenSongsScreen(
    tracks: List<TrackEntity>,
    onBack: () -> Unit,
    onUnhide: (TrackEntity) -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Hidden songs") },
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

        if (tracks.isEmpty()) {
            Text(
                text = "Songs you hide from long-press → Hide from library show up here so you can bring them back.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(24.dp),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                track = track,
                                isPlaying = false,
                                onClick = {},
                                onLongClick = {},
                            )
                        }
                        TextButton(onClick = { onUnhide(track) }) {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = AccentTeal,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text("Unhide", color = AccentTeal)
                        }
                    }
                }
            }
        }
    }
}
