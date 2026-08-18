package io.karpilabs.simplemp3.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.theme.AccentCoral

@Composable
fun TrackActionsMenu(
    expanded: Boolean,
    track: TrackEntity?,
    onDismiss: () -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit = {},
    onToggleNeverCompress: (TrackEntity) -> Unit = {},
    onHide: (TrackEntity) -> Unit = {},
    showFavorite: Boolean = true,
    showNeverCompress: Boolean = true,
    showHide: Boolean = true,
) {
    DropdownMenu(expanded = expanded && track != null, onDismissRequest = onDismiss) {
        val t = track ?: return@DropdownMenu
        DropdownMenuItem(
            text = { Text("Play next") },
            onClick = {
                onDismiss()
                onPlayNext(t)
            },
        )
        DropdownMenuItem(
            text = { Text("Add to queue") },
            onClick = {
                onDismiss()
                onAddToQueue(t)
            },
        )
        DropdownMenuItem(
            text = { Text("Add to playlist") },
            onClick = {
                onDismiss()
                onAddToPlaylist(t)
            },
        )
        if (showFavorite) {
            DropdownMenuItem(
                text = { Text("Toggle liked") },
                onClick = {
                    onDismiss()
                    onToggleFavorite(t)
                },
            )
        }
        if (showNeverCompress && t.isAppOwned) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (t.neverCompress) {
                            "Allow compression"
                        } else {
                            "★ Never compress"
                        },
                    )
                },
                onClick = {
                    onDismiss()
                    onToggleNeverCompress(t)
                },
            )
        }
        if (showHide) {
            DropdownMenuItem(
                text = { Text("Hide from library", color = AccentCoral) },
                onClick = {
                    onDismiss()
                    onHide(t)
                },
            )
        }
    }
}
