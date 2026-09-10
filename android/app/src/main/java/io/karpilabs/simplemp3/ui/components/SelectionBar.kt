package io.karpilabs.simplemp3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@Composable
fun rememberTrackSelection(): TrackSelectionState = remember { TrackSelectionState() }

class TrackSelectionState(
    val selectedIds: SnapshotStateList<Long> = mutableStateListOf(),
) {
    val isSelecting: Boolean get() = selectedIds.isNotEmpty()
    val count: Int get() = selectedIds.size

    fun selected(id: Long): Boolean = selectedIds.contains(id)

    fun toggle(id: Long) {
        if (!selectedIds.remove(id)) selectedIds.add(id)
    }

    fun enter(id: Long) {
        if (!selectedIds.contains(id)) selectedIds.add(id)
    }

    fun selectAll(tracks: List<TrackEntity>) {
        selectedIds.clear()
        selectedIds.addAll(tracks.map { it.id })
    }

    fun clear() {
        selectedIds.clear()
    }

    fun selectedTracks(from: List<TrackEntity>): List<TrackEntity> {
        val set = selectedIds.toHashSet()
        return from.filter { it.id in set }
    }
}

@Composable
fun SelectionBar(
    count: Int,
    onQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onHide: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showHide: Boolean = true,
) {
    val palette = LocalSimpleMP3Palette.current
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.elevated)
                .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onClear) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = palette.textSecondary)
                Text(
                    "  $count selected",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            TextButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = null, tint = AccentTeal)
                Text(" All", color = AccentTeal)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlayNext, enabled = count > 0) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Play next", tint = AccentTeal)
            }
            IconButton(onClick = onQueue, enabled = count > 0) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add to queue", tint = AccentTeal)
            }
            IconButton(onClick = onAddToPlaylist, enabled = count > 0) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist", tint = AccentTeal)
            }
            if (showHide) {
                IconButton(onClick = onHide, enabled = count > 0) {
                    Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide", tint = AccentCoral)
                }
            }
        }
    }
}
