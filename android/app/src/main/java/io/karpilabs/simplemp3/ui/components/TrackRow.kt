package io.karpilabs.simplemp3.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette
import io.karpilabs.simplemp3.ui.util.formatDuration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: TrackEntity,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    showArtwork: Boolean = true,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArtwork) {
            AlbumArt(
                artworkUri = track.artworkUri,
                contentDescription = track.album,
                size = 52.dp,
                cornerRadius = 8.dp,
            )
            Spacer(Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) palette.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.neverCompress) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = "Never compress",
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text =
                        buildString {
                            append(track.artist)
                            append(" · ")
                            append(formatDuration(track.duration))
                            when {
                                track.isYoutube -> append(" · YouTube")
                                track.isJellyfin -> append(" · Offline")
                            }
                            if (track.neverCompress) append(" · Keep original")
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        when {
                            track.neverCompress -> palette.accent
                            track.isJellyfin || track.isYoutube ->
                                io.karpilabs.simplemp3.ui.theme.AccentViolet
                            else -> palette.textSecondary
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (onFavoriteClick != null) {
            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isFavorite) "Unlike" else "Like",
                    tint = if (isFavorite) palette.accent else palette.textMuted,
                )
            }
        }

        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = palette.textMuted,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier =
                    Modifier
                        .clickable(onClick = onAction)
                        .padding(8.dp),
            )
        }
    }
}
