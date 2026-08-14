package io.karpilabs.simplemp3.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSongsToPlaylistSheet(
    tracks: List<TrackEntity>,
    alreadyInPlaylist: Set<Long>,
    onDismiss: () -> Unit,
    onAdd: (List<Long>) -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<Long>()) }

    val candidates =
        remember(tracks, alreadyInPlaylist, query) {
            val q = query.trim()
            tracks.filter { track ->
                track.id !in alreadyInPlaylist &&
                    (
                        q.isEmpty() ||
                            track.title.contains(q, ignoreCase = true) ||
                            track.artist.contains(q, ignoreCase = true) ||
                            track.album.contains(q, ignoreCase = true)
                    )
            }
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.card,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Add songs",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                text =
                    if (selected.isEmpty()) {
                        "Pick tracks from your library"
                    } else {
                        "${selected.size} selected"
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Filter songs") },
                singleLine = true,
            )

            if (candidates.isEmpty()) {
                Text(
                    text =
                        if (query.isBlank()) {
                            "Every song in your library is already in this playlist."
                        } else {
                            "No matching songs."
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(candidates, key = { it.id }) { track ->
                        val isOn = track.id in selected
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (isOn) selected - track.id else selected + track.id
                                    }.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AlbumArt(
                                artworkUri = track.artworkUri,
                                contentDescription = track.album,
                                size = 48.dp,
                                cornerRadius = 8.dp,
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                            ) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                )
                            }
                            Icon(
                                imageVector = if (isOn) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (isOn) "Selected" else "Not selected",
                                tint = if (isOn) palette.accent else palette.textMuted,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { onAdd(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                ) {
                    Text(if (selected.isEmpty()) "Add" else "Add ${selected.size}")
                }
            }
        }
    }
}
