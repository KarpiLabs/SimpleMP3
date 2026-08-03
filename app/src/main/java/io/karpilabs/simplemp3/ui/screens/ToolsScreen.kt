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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentGold
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@Composable
fun ToolsScreen(
    onOpenYoutube: () -> Unit,
    onOpenQuickConnect: () -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Import and transfer music",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
            }
        }

        item {
            ToolsFeatureCard(
                icon = Icons.Rounded.VideoLibrary,
                title = "YouTube → MP3",
                subtitle = "Paste a link · title, art, and offline audio",
                brush = Brush.horizontalGradient(
                    listOf(
                        AccentCoral.copy(alpha = 0.32f),
                        palette.elevated,
                        AccentViolet.copy(alpha = 0.18f)
                    )
                ),
                onClick = onOpenYoutube
            )
        }

        item {
            ToolsFeatureCard(
                icon = Icons.Rounded.WifiTethering,
                title = "Quick Connect",
                subtitle = "LAN portal · drag MP3s from your computer",
                brush = Brush.horizontalGradient(
                    listOf(
                        AccentTeal.copy(alpha = 0.3f),
                        palette.elevated,
                        AccentGold.copy(alpha = 0.18f)
                    )
                ),
                onClick = onOpenQuickConnect
            )
        }
    }
}

@Composable
private fun ToolsFeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    brush: Brush,
    onClick: () -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
        }
    }
}
