package io.karpilabs.simplemp3.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.prefs.ResumeSnapshot
import io.karpilabs.simplemp3.player.PlayerUiState
import io.karpilabs.simplemp3.ui.components.AlbumArt
import io.karpilabs.simplemp3.ui.components.PlaylistCard
import io.karpilabs.simplemp3.ui.components.SectionHeader
import io.karpilabs.simplemp3.ui.components.TrackRow
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.NightBlack
import io.karpilabs.simplemp3.ui.theme.NightCard
import io.karpilabs.simplemp3.ui.theme.NightElevated
import io.karpilabs.simplemp3.ui.theme.TextSecondary
import io.karpilabs.simplemp3.ui.util.formatTrackCount
import java.util.Calendar

@Composable
fun HomeScreen(
    playlists: List<PlaylistWithMeta>,
    recentlyAdded: List<TrackEntity>,
    continueListening: List<TrackEntity> = emptyList(),
    trackCount: Int,
    jellyfinCount: Int = 0,
    jellyfinEnabled: Boolean = false,
    isScanning: Boolean,
    playerState: PlayerUiState,
    driveMode: Boolean = false,
    resume: ResumeSnapshot? = null,
    onScan: () -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onPlayAll: (List<TrackEntity>) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit = {},
    onOpenJellyfin: () -> Unit = {},
    onOpenTools: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleDriveMode: () -> Unit = {},
    onResume: () -> Unit = {},
    onPlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {}
) {
    val greeting = rememberGreeting()
    val showJellyfin = jellyfinEnabled
    val jfCount = if (showJellyfin) jellyfinCount else 0

    if (driveMode) {
        DriveModeHome(
            playerState = playerState,
            resume = resume,
            playlists = playlists,
            continueListening = continueListening,
            jellyfinCount = jfCount,
            jellyfinEnabled = showJellyfin,
            onExitDriveMode = onToggleDriveMode,
            onResume = onResume,
            onPlayPause = onPlayPause,
            onSkipNext = onSkipNext,
            onSkipPrevious = onSkipPrevious,
            onOpenPlaylist = onOpenPlaylist,
            onPlayContinue = { onPlayAll(continueListening) },
            onOpenJellyfin = onOpenJellyfin
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentTeal
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Simple MP3",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row {
                        FilterChip(
                            selected = false,
                            onClick = onToggleDriveMode,
                            label = { Text("Drive") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DirectionsCar,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = NightCard,
                                labelColor = AccentTeal,
                                iconColor = AccentTeal
                            )
                        )
                        IconButton(onClick = onScan, enabled = !isScanning) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentTeal
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Scan library",
                                    tint = AccentTeal
                                )
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = AccentTeal
                            )
                        }
                    }
                }
                Text(
                    text = buildString {
                        if (trackCount > 0) {
                            append(formatTrackCount(trackCount))
                            if (jfCount > 0) {
                                append(" · ")
                                append(jfCount)
                                append(" from Jellyfin")
                            }
                        } else {
                            append(
                                if (showJellyfin) {
                                    "Scan your library or sync Jellyfin"
                                } else {
                                    "Scan your library to get started"
                                }
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        if (resume != null && resume.hasSession && playerState.queueSize == 0) {
            item {
                ResumeCard(resume = resume, onResume = onResume)
            }
        }

        if (showJellyfin) {
            item {
                FeatureCard(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Jellyfin Sync",
                    subtitle = if (jfCount > 0) {
                        "$jfCount tracks offline — tap to manage"
                    } else {
                        "Download server music for offline & Android Auto"
                    },
                    brush = Brush.horizontalGradient(
                        listOf(
                            AccentViolet.copy(alpha = 0.35f),
                            NightElevated,
                            AccentTeal.copy(alpha = 0.15f)
                        )
                    ),
                    onClick = onOpenJellyfin
                )
            }
        }

        item {
            FeatureCard(
                icon = Icons.Rounded.DirectionsCar,
                title = if (driveMode) "Drive mode · on" else "Drive mode",
                subtitle = "Big controls, resume, and playlists for the road",
                brush = Brush.horizontalGradient(
                    listOf(
                        AccentTeal.copy(alpha = 0.22f),
                        NightElevated,
                        NightCard
                    )
                ),
                onClick = onToggleDriveMode
            )
        }

        if (trackCount == 0 && !isScanning) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Your music. Your car. Offline.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (showJellyfin) {
                            "Scan local MP3s or connect Jellyfin to download tracks for the road."
                        } else {
                            "Scan local MP3s on this device, or open Tools for YouTube and LAN import."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onScan,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentTeal,
                                contentColor = NightBlack
                            )
                        ) {
                            Text("Scan library")
                        }
                        if (showJellyfin) {
                            Button(
                                onClick = onOpenJellyfin,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentViolet,
                                    contentColor = NightBlack
                                )
                            ) {
                                Text("Jellyfin")
                            }
                        } else {
                            Button(
                                onClick = onOpenTools,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NightCard,
                                    contentColor = AccentTeal
                                )
                            ) {
                                Text("Tools")
                            }
                        }
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item { SectionHeader(title = "Playlists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists, key = { it.id }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            modifier = Modifier.width(148.dp)
                        )
                    }
                }
            }
        }

        if (continueListening.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Continue listening",
                    actionLabel = "Play",
                    onAction = { onPlayAll(continueListening) }
                )
            }
            items(continueListening.take(10), key = { "cl_${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isPlaying = playerState.currentMediaId == "track:${track.id}",
                    onClick = { onPlayTrack(track, continueListening) },
                    onLongClick = { onAddToPlaylist(track) },
                    onFavoriteClick = { onToggleFavorite(track.id) }
                )
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently added",
                    actionLabel = "Play all",
                    onAction = { onPlayAll(recentlyAdded) }
                )
            }
            items(recentlyAdded.take(12), key = { "ra_${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isPlaying = playerState.currentMediaId == "track:${track.id}",
                    onClick = { onPlayTrack(track, recentlyAdded) },
                    onLongClick = { onAddToPlaylist(track) },
                    onFavoriteClick = { onToggleFavorite(track.id) }
                )
            }
        }

        if (showJellyfin && jfCount > 0) {
            item {
                SectionHeader(
                    title = "Jellyfin Offline",
                    actionLabel = "Manage",
                    onAction = onOpenJellyfin
                )
            }
        }
    }
}

@Composable
private fun DriveModeHome(
    playerState: PlayerUiState,
    resume: ResumeSnapshot?,
    playlists: List<PlaylistWithMeta>,
    continueListening: List<TrackEntity>,
    jellyfinCount: Int,
    jellyfinEnabled: Boolean,
    onExitDriveMode: () -> Unit,
    onResume: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onPlayContinue: () -> Unit,
    onOpenJellyfin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NightBlack, NightElevated, NightBlack)
                )
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Drive mode",
                style = MaterialTheme.typography.headlineMedium,
                color = AccentTeal
            )
            Text(
                "Exit",
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onExitDriveMode)
                    .padding(12.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        AlbumArt(
            artworkUri = playerState.artworkUri ?: resume?.artworkUri,
            contentDescription = null,
            size = 220.dp,
            cornerRadius = 20.dp
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = playerState.title.ifBlank { resume?.title ?: "Ready to roll" },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = playerState.artist.ifBlank { resume?.artist ?: "Tap Resume or a playlist" },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(28.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(64.dp)) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(AccentTeal)
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = NightBlack,
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = onSkipNext, modifier = Modifier.size(64.dp)) {
                Icon(
                    Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (resume != null && resume.hasSession) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = NightBlack
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Resume · ${resume.title}", maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (continueListening.isNotEmpty()) {
            Button(
                onClick = onPlayContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NightCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Play recently played", color = AccentTeal)
            }
            Spacer(Modifier.height(10.dp))
        }

        if (jellyfinEnabled && jellyfinCount > 0) {
            Button(
                onClick = onOpenJellyfin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NightCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Jellyfin Offline ($jellyfinCount)", color = AccentViolet)
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            "Playlists",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playlists.take(8), key = { it.id }) { pl ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NightCard)
                        .clickable { onOpenPlaylist(pl.id) }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AlbumArt(
                        artworkUri = pl.displayCover,
                        contentDescription = pl.name,
                        size = 72.dp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pl.name,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(resume: ResumeSnapshot, onResume: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AccentTeal.copy(alpha = 0.25f), NightCard)
                )
            )
            .clickable(onClick = onResume)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(artworkUri = resume.artworkUri, contentDescription = null, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Pick up where you left off", style = MaterialTheme.typography.labelMedium, color = AccentTeal)
            Text(
                resume.title.ifBlank { "Last session" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                resume.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Icon(Icons.Rounded.PlayArrow, null, tint = AccentTeal, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    brush: Brush,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentTeal, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun rememberGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Night drive mode"
    }
}
