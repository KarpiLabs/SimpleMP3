package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.AddSongsToPlaylistSheet
import io.karpilabs.simplemp3.ui.components.TrackActionsMenu
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatDuration
import io.karpilabs.simplemp3.ui.util.formatTrackCount
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity?,
    tracks: List<TrackEntity>,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (TrackEntity) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onDeletePlaylist: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onToggleNeverCompress: (Long) -> Unit = {},
    onReorder: (from: Int, to: Int) -> Unit,
    onPlayNext: (TrackEntity) -> Unit = {},
    onAddToQueue: (TrackEntity) -> Unit = {},
    onAddToPlaylist: (TrackEntity) -> Unit = {},
    libraryTracks: List<TrackEntity> = emptyList(),
    onAddTracks: (List<Long>) -> Unit = {},
) {
    val palette = LocalSimpleMP3Palette.current
    var menuOpen by remember { mutableStateOf(false) }
    var actionTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var showAddSongs by remember { mutableStateOf(false) }
    var localTracks by remember { mutableStateOf(tracks) }
    LaunchedEffect(tracks) { localTracks = tracks }
    val canAddSongs = playlist.canAddSongs()

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()
    val itemHeightPx = 72f // approximate row height for reordering thresholds

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canAddSongs) {
                        IconButton(onClick = { showAddSongs = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add songs")
                        }
                    }
                    if (playlist != null && !playlist.isSystem) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete playlist") },
                                    onClick = {
                                        menuOpen = false
                                        onDeletePlaylist()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Delete, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
            )

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val cover = localTracks.firstOrNull()?.artworkUri
                        Box(
                            modifier =
                                Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(AccentTeal.copy(alpha = 0.4f), palette.elevated),
                                        ),
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!cover.isNullOrBlank()) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = playlist?.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = playlist?.name ?: "Playlist",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                buildString {
                                    append(formatTrackCount(localTracks.size))
                                    val total = localTracks.sumOf { it.duration }
                                    if (total > 0) {
                                        append(" · ")
                                        append(formatDuration(total))
                                    }
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                        if (!playlist?.description.isNullOrBlank()) {
                            Text(
                                text = playlist!!.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Long-press ☰ to drag-reorder",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onPlayAll,
                                enabled = localTracks.isNotEmpty(),
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = AccentTeal,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Play")
                            }
                            OutlinedButton(
                                onClick = onShuffle,
                                enabled = localTracks.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = AccentTeal)
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle", color = AccentTeal)
                            }
                        }
                        if (canAddSongs) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showAddSongs = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = palette.accent)
                                Spacer(Modifier.width(6.dp))
                                Text("Add songs", color = palette.accent)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                itemsIndexed(localTracks, key = { _, t -> t.id }) { index, track ->
                    val isDragging = dragIndex == index
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .offset {
                                    IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0)
                                }.then(
                                    if (isDragging) Modifier.shadow(8.dp) else Modifier,
                                ).background(
                                    if (isDragging) {
                                        palette.elevated
                                    } else {
                                        MaterialTheme.colorScheme.background
                                    },
                                ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = palette.textMuted,
                            modifier =
                                Modifier
                                    .padding(start = 8.dp)
                                    .size(28.dp)
                                    .pointerInput(localTracks, index) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                dragIndex = index
                                                dragOffset = 0f
                                            },
                                            onDragCancel = {
                                                dragIndex = -1
                                                dragOffset = 0f
                                            },
                                            onDragEnd = {
                                                dragIndex = -1
                                                dragOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffset += dragAmount.y
                                                val from = dragIndex
                                                if (from < 0) return@detectDragGesturesAfterLongPress
                                                val shift = (dragOffset / itemHeightPx).toInt()
                                                if (shift != 0) {
                                                    val to = (from + shift).coerceIn(0, localTracks.lastIndex)
                                                    if (to != from) {
                                                        val mutable = localTracks.toMutableList()
                                                        val item = mutable.removeAt(from)
                                                        mutable.add(to, item)
                                                        localTracks = mutable
                                                        onReorder(from, to)
                                                        dragIndex = to
                                                        dragOffset -= shift * itemHeightPx
                                                    }
                                                }
                                            },
                                        )
                                    },
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                track = track,
                                isPlaying = playerState.currentMediaId == "track:${track.id}",
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onToggleFavorite(track.id) },
                                onMoreClick = { actionTrack = track },
                                onLongClick = { actionTrack = track },
                            )
                            TrackActionsMenu(
                                expanded = actionTrack?.id == track.id,
                                track = actionTrack,
                                onDismiss = { actionTrack = null },
                                onPlayNext = onPlayNext,
                                onAddToQueue = onAddToQueue,
                                onAddToPlaylist = onAddToPlaylist,
                                onToggleFavorite = { onToggleFavorite(it.id) },
                                onToggleNeverCompress = { onToggleNeverCompress(it.id) },
                            )
                        }
                        IconButton(onClick = { onRemoveTrack(track.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = palette.textMuted)
                        }
                    }
                }

                if (localTracks.isEmpty()) {
                    item {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text =
                                    if (canAddSongs) {
                                        "This playlist is empty. Add songs from your library."
                                    } else {
                                        "This playlist fills automatically."
                                    },
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                            )
                            if (canAddSongs) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { showAddSongs = true },
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = palette.accent,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add songs")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSongs) {
            AddSongsToPlaylistSheet(
                tracks = libraryTracks,
                alreadyInPlaylist = localTracks.map { it.id }.toSet(),
                onDismiss = { showAddSongs = false },
                onAdd = { ids ->
                    onAddTracks(ids)
                    showAddSongs = false
                },
            )
        }
    }
}

private fun PlaylistEntity?.canAddSongs(): Boolean {
    if (this == null) return false
    if (!isSystem) return true
    return systemType == PlaylistEntity.SYSTEM_FAVORITES
}
