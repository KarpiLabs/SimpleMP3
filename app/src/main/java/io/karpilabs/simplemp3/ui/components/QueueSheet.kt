package io.karpilabs.simplemp3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    state: PlayerUiState,
    onDismiss: () -> Unit,
    onPlayIndex: (Int) -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.card
    ) {
        Column(modifier = Modifier.padding(bottom = 28.dp)) {
            Text(
                text = "Up next",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Text(
                text = "${state.queueSize} in queue",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (state.queue.isEmpty()) {
                Text(
                    text = "Play something to build a queue.",
                    color = palette.textSecondary,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    itemsIndexed(state.queue, key = { _, item -> item.mediaId + item.title }) { _, item ->
                        // Approximate absolute index from windowed queue (window starts near current)
                        val absoluteIndex = state.queue.indexOf(item).let { relative ->
                            // Window is built around currentIndex-10 .. so relative 0 ≈ max(0, current-10)
                            val windowStart = (state.currentIndex - 10).coerceAtLeast(0)
                            windowStart + relative
                        }
                        val isCurrent = item.mediaId == state.currentMediaId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayIndex(absoluteIndex) }
                                .background(if (isCurrent) AccentTeal.copy(alpha = 0.12f) else palette.card)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArt(
                                artworkUri = item.artworkUri,
                                contentDescription = null,
                                size = 44.dp,
                                cornerRadius = 8.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) AccentTeal else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = item.artist.ifBlank { " " },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    "Playing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentTeal,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentTeal.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
