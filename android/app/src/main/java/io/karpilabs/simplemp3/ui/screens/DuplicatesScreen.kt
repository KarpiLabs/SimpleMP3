package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.duplicates.DuplicateDetector
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    groups: List<DuplicateDetector.Group>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onHideExtras: (DuplicateDetector.Group) -> Unit,
    onMerge: (DuplicateDetector.Group) -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Duplicates") },
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

        when {
            isLoading -> {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(color = AccentTeal)
                    Spacer(Modifier.width(16.dp))
                    Text("Looking for matching rips…", color = palette.textSecondary)
                }
            }
            groups.isEmpty() -> {
                Text(
                    text = "No duplicate songs found. Matches use title, artist, and duration — extras can be hidden or merged into playlists. Files are never deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
            }
            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                    item {
                        Text(
                            text = "${groups.size} group${if (groups.size == 1) "" else "s"} · hide extras or merge playlist membership. Nothing is deleted from disk.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(groups, key = { it.key + it.keepId }) { group ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = "${group.keeper.title} · keep ${group.keeper.folderPath.ifBlank { "library" }}",
                                style = MaterialTheme.typography.titleSmall,
                                color = AccentTeal,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            group.tracks.forEach { track ->
                                val isKeep = track.id == group.keepId
                                TrackRow(
                                    track = track,
                                    isPlaying = false,
                                    onClick = {},
                                    showArtwork = true,
                                )
                                Text(
                                    text =
                                        buildString {
                                            append(if (isKeep) "Keep · " else "Extra · ")
                                            append(track.folderPath.ifBlank { "unknown folder" })
                                            if (track.playCount > 0) append(" · ${track.playCount} plays")
                                        },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isKeep) AccentTeal else palette.textMuted,
                                    modifier = Modifier.padding(start = 82.dp, end = 16.dp, bottom = 4.dp),
                                )
                            }
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { onHideExtras(group) }) {
                                    Icon(
                                        Icons.Rounded.VisibilityOff,
                                        contentDescription = null,
                                        tint = AccentCoral,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    Text("Hide extras", color = AccentCoral)
                                }
                                OutlinedButton(onClick = { onMerge(group) }) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.MergeType,
                                        contentDescription = null,
                                        tint = AccentTeal,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                    Text("Merge into keep", color = AccentTeal)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}
