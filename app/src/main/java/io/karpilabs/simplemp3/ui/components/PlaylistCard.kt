package io.karpilabs.simplemp3.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatTrackCount

@Composable
fun PlaylistCard(
    playlist: PlaylistWithMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSimpleMP3Palette.current
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(playlistGradient(playlist.systemType, palette.elevated)),
            contentAlignment = Alignment.Center
        ) {
            val cover = playlist.displayCover
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = when (playlist.systemType) {
                        PlaylistEntity.SYSTEM_FAVORITES -> Icons.Rounded.Favorite
                        PlaylistEntity.SYSTEM_RECENTLY_PLAYED -> Icons.Rounded.History
                        else -> Icons.Rounded.QueueMusic
                    },
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = formatTrackCount(playlist.trackCount),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1
        )
    }
}

@Composable
fun PlaylistListRow(
    playlist: PlaylistWithMeta,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalSimpleMP3Palette.current
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(playlistGradient(playlist.systemType, palette.elevated)),
            contentAlignment = Alignment.Center
        ) {
            val cover = playlist.displayCover
            if (!cover.isNullOrBlank()) {
                AsyncImage(
                    model = cover,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = when (playlist.systemType) {
                        PlaylistEntity.SYSTEM_FAVORITES -> Icons.Rounded.Favorite
                        PlaylistEntity.SYSTEM_RECENTLY_PLAYED -> Icons.Rounded.History
                        else -> Icons.Rounded.QueueMusic
                    },
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(formatTrackCount(playlist.trackCount))
                    if (playlist.description.isNotBlank()) {
                        append(" · ")
                        append(playlist.description)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun playlistGradient(systemType: String?, elevated: androidx.compose.ui.graphics.Color) = when (systemType) {
    PlaylistEntity.SYSTEM_FAVORITES -> Brush.linearGradient(
        listOf(AccentCoral.copy(alpha = 0.55f), elevated)
    )
    PlaylistEntity.SYSTEM_RECENTLY_PLAYED -> Brush.linearGradient(
        listOf(AccentViolet.copy(alpha = 0.5f), elevated)
    )
    PlaylistEntity.SYSTEM_JELLYFIN -> Brush.linearGradient(
        listOf(AccentViolet.copy(alpha = 0.55f), AccentTeal.copy(alpha = 0.25f), elevated)
    )
    else -> Brush.linearGradient(
        listOf(AccentTeal.copy(alpha = 0.35f), elevated)
    )
}
