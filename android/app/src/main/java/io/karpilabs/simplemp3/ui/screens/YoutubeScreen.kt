package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import io.karpilabs.simplemp3.data.youtube.YoutubeDownloadProgress
import io.karpilabs.simplemp3.ui.components.AlbumArt
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatDuration
import io.karpilabs.simplemp3.ui.viewmodel.YoutubeUiState

@Composable
fun YoutubeScreen(
    ui: YoutubeUiState,
    progress: YoutubeDownloadProgress,
    downloads: List<TrackEntity>,
    onBack: () -> Unit,
    onUrlChange: (String) -> Unit,
    onPaste: (String) -> Unit,
    onDownload: () -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onRemove: (Long) -> Unit,
    onToggleNeverCompress: (Long) -> Unit = {},
    onClearAll: () -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YouTube → MP3",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Paste a link · title + album art saved offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                AccentCoral.copy(alpha = 0.28f),
                                palette.elevated,
                                AccentViolet.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = AccentCoral,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Like yt-dl on your phone",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Downloads the best audio, converts to MP3 with FFmpeg (like yt-dl), embeds the thumbnail as cover art, and tags title/artist for Android Auto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary
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
                    label = { Text("YouTube link") },
                    placeholder = { Text("https://youtube.com/watch?v=…") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = palette.textMuted)
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.text?.let { onPaste(it) }
                            }
                        ) {
                            Icon(
                                Icons.Rounded.ContentPaste,
                                contentDescription = "Paste",
                                tint = AccentTeal
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(onGo = { onDownload() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = palette.card,
                        focusedLabelColor = AccentTeal,
                        cursorColor = AccentTeal
                    ),
                    enabled = !progress.isActive
                )

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onDownload,
                    enabled = !progress.isActive && ui.urlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCoral,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                        disabledContainerColor = palette.card,
                        disabledContentColor = palette.textMuted
                    )
                ) {
                    if (progress.isActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(progress.phase.ifBlank { "Working…" })
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Convert to MP3")
                    }
                }

                if (progress.isActive) {
                    Spacer(Modifier.height(10.dp))
                    if (progress.percent in 0..100) {
                        LinearProgressIndicator(
                            progress = { progress.percent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentTeal,
                            trackColor = palette.card
                        )
                        Text(
                            text = "${progress.percent}% · ${progress.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp)),
                            color = AccentTeal,
                            trackColor = palette.card
                        )
                        if (progress.title.isNotBlank()) {
                            Text(
                                text = progress.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                progress.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentCoral
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (downloads.isEmpty()) "No downloads yet"
                    else "${downloads.size} saved",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (downloads.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear all", color = AccentCoral)
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Paste any YouTube video or Shorts link above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary
                    )
                }
            }
        }

        items(downloads, key = { it.id }) { track ->
            YoutubeDownloadRow(
                track = track,
                onPlay = { onPlayTrack(track, downloads) },
                onToggleNeverCompress = { onToggleNeverCompress(track.id) },
                onRemove = { onRemove(track.id) }
            )
        }
    }
}

@Composable
private fun YoutubeDownloadRow(
    track: TrackEntity,
    onPlay: () -> Unit,
    onToggleNeverCompress: () -> Unit,
    onRemove: () -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.card)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            artworkUri = track.artworkUri,
            contentDescription = track.title,
            size = 52.dp,
            cornerRadius = 8.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.duration > 0) {
                        append(" · ")
                        append(formatDuration(track.duration))
                    }
                    if (track.neverCompress) append(" · Keep original")
                    else if (track.isSizeOptimized) append(" · Optimized")
                    else if (track.isCold) append(" · Cold")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (track.neverCompress) AccentTeal else palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleNeverCompress) {
            Icon(
                imageVector = if (track.neverCompress) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = if (track.neverCompress) {
                    "Allow compression"
                } else {
                    "Never compress"
                },
                tint = if (track.neverCompress) AccentTeal else palette.textMuted
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = AccentTeal)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = palette.textMuted)
        }
    }
}
