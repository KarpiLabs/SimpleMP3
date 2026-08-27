package io.karpilabs.simplemp3.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.stream.StreamSaveProgress
import io.karpilabs.simplemp3.ui.components.AlbumArt
import io.karpilabs.simplemp3.ui.theme.AccentGold
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatDuration
import io.karpilabs.simplemp3.ui.viewmodel.StreamUiState

@Composable
fun StreamScreen(
    ui: StreamUiState,
    progress: StreamSaveProgress,
    saved: List<TrackEntity>,
    onBack: () -> Unit,
    onUrlChange: (String) -> Unit,
    onPasteUrl: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onPlayLive: () -> Unit,
    onSave: () -> Unit,
    onPickArtwork: (Uri) -> Unit,
    onClearArtwork: () -> Unit,
    onSetTrackArtwork: (Long, Uri) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onRemove: (Long) -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    val clipboard = LocalClipboardManager.current
    var iconTargetId by remember { mutableStateOf<Long?>(null) }
    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val target = iconTargetId
            iconTargetId = null
            if (target != null) {
                onSetTrackArtwork(target, uri)
            } else {
                onPickArtwork(uri)
            }
        }

    fun pickIcon(trackId: Long? = null) {
        iconTargetId = trackId
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        item {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Streams",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Play a live stream, or save it to a playlist",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
        }

        item {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    AccentTeal.copy(alpha = 0.28f),
                                    palette.elevated,
                                    AccentGold.copy(alpha = 0.2f),
                                ),
                            ),
                        ).padding(16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "HLS & direct audio streams",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Paste an .m3u8 (HLS) or direct audio URL. Play it live, or save the stream to the Saved Streams playlist — the live URL is kept, nothing is downloaded. We'll grab a thumbnail when we can, or you can set a custom icon.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = ui.urlInput,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Stream URL") },
                    placeholder = { Text("https://…/playlist.m3u8") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = palette.textMuted)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { clipboard.getText()?.text?.let { onPasteUrl(it) } },
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste", tint = AccentTeal)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = palette.card,
                            focusedLabelColor = AccentTeal,
                            cursorColor = AccentTeal,
                        ),
                    enabled = !progress.isActive,
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = ui.titleInput,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title (optional)") },
                    placeholder = { Text("My radio station") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Title, contentDescription = null, tint = palette.textMuted)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onPlayLive() }),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = palette.card,
                            focusedLabelColor = AccentTeal,
                            cursorColor = AccentTeal,
                        ),
                    enabled = !progress.isActive,
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.card)
                                .clickable(enabled = !progress.isActive) { pickIcon(null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!ui.pendingArtworkUri.isNullOrBlank()) {
                            AlbumArt(
                                artworkUri = ui.pendingArtworkUri,
                                contentDescription = "Stream icon",
                                size = 56.dp,
                                cornerRadius = 10.dp,
                            )
                        } else {
                            Icon(
                                Icons.Rounded.AddPhotoAlternate,
                                contentDescription = "Set icon",
                                tint = AccentTeal,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (ui.pendingArtworkUri.isNullOrBlank()) "Icon (optional)" else "Custom icon set",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                if (ui.pendingArtworkUri.isNullOrBlank()) {
                                    "Tap to pick one, or we'll try the stream's thumbnail"
                                } else {
                                    "Tap to change"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                    if (!ui.pendingArtworkUri.isNullOrBlank()) {
                        IconButton(onClick = onClearArtwork, enabled = !progress.isActive) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Clear icon", tint = palette.textMuted)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onPlayLive,
                        enabled = !progress.isActive && ui.urlInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AccentTeal,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = palette.card,
                                disabledContentColor = palette.textMuted,
                            ),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Play live")
                    }
                    OutlinedButton(
                        onClick = onSave,
                        enabled = !progress.isActive && ui.urlInput.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (progress.isActive) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentTeal,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(progress.phase.ifBlank { "Saving…" })
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save stream")
                        }
                    }
                }

                if (progress.isActive) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                        color = AccentTeal,
                        trackColor = palette.card,
                    )
                    if (progress.title.isNotBlank()) {
                        Text(
                            text = progress.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                progress.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(text = err, style = MaterialTheme.typography.bodySmall, color = AccentGold)
                }
            }
        }

        item {
            Text(
                text = if (saved.isEmpty()) "No saved streams yet" else "${saved.size} saved",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (saved.isEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Saved streams stay in the Saved Streams playlist and play live in Android Auto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
            }
        }

        items(saved, key = { it.id }) { track ->
            SavedStreamRow(
                track = track,
                onPlay = { onPlayTrack(track, saved) },
                onSetIcon = { pickIcon(track.id) },
                onRemove = { onRemove(track.id) },
            )
        }
    }
}

@Composable
private fun SavedStreamRow(
    track: TrackEntity,
    onPlay: () -> Unit,
    onSetIcon: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = LocalSimpleMP3Palette.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.card)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.clickable(onClick = onSetIcon)) {
            if (!track.artworkUri.isNullOrBlank()) {
                AlbumArt(
                    artworkUri = track.artworkUri,
                    contentDescription = "Change icon",
                    size = 52.dp,
                    cornerRadius = 8.dp,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentViolet.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Podcasts, contentDescription = "Set icon", tint = AccentTeal)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    buildString {
                        append(if (track.isRemoteStream) "Live stream" else "Saved stream")
                        if (track.duration > 0) {
                            append(" · ")
                            append(formatDuration(track.duration))
                        }
                    },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = AccentTeal)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = palette.textMuted)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Set icon") },
                    onClick = {
                        menuOpen = false
                        onSetIcon()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                )
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                )
            }
        }
    }
}
