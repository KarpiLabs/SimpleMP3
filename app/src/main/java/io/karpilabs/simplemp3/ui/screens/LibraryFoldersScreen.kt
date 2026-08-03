package io.karpilabs.simplemp3.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

/**
 * Settings screen: optionally limit the MediaStore scan to selected folder roots.
 * Empty selection = scan all music on the device (default).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFoldersScreen(
    selectedRoots: Set<String>,
    deviceFolders: List<String>,
    isLoading: Boolean,
    isScanning: Boolean,
    onBack: () -> Unit,
    onRefreshFolders: () -> Unit,
    onLimitEnabledChange: (Boolean) -> Unit,
    onToggleRoot: (String) -> Unit,
    onSelectAllVisible: () -> Unit,
    onClearSelection: () -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    val limitEnabled = selectedRoots.isNotEmpty()
    // Prefer top-level roots for a clean picker; still include deeper unique roots if needed.
    val pickerEntries = remember(deviceFolders) {
        buildPickerEntries(deviceFolders)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text("Library folders") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onRefreshFolders, enabled = !isLoading) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh folder list")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Choose which folders appear in your local library. Jellyfin, YouTube, and LAN imports are never filtered out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.card)
                        .clickable { onLimitEnabledChange(!limitEnabled) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (limitEnabled) Icons.Rounded.Folder else Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Limit to selected folders",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (limitEnabled) {
                                "${selectedRoots.size} folder${if (selectedRoots.size == 1) "" else "s"} selected · rescan on change"
                            } else {
                                "Off · all music on this device is scanned"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary
                        )
                    }
                    Switch(
                        checked = limitEnabled,
                        onCheckedChange = onLimitEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.card,
                            checkedTrackColor = AccentTeal,
                            uncheckedThumbColor = palette.textMuted,
                            uncheckedTrackColor = palette.textMuted.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            if (limitEnabled) {
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Folders on device",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentTeal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Select all",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentTeal,
                            modifier = Modifier
                                .clickable(onClick = onSelectAllVisible)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.textMuted,
                            modifier = Modifier
                                .clickable(onClick = onClearSelection)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                if (isLoading && pickerEntries.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AccentTeal,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(Modifier.width(16.dp))
                            Text("Scanning folders…", color = palette.textSecondary)
                        }
                    }
                }

                items(pickerEntries, key = { it.path }) { entry ->
                    val checked = selectedRoots.any { root ->
                        FolderBrowser.normalize(root) == entry.path
                    }
                    FolderCheckRow(
                        path = entry.path,
                        label = entry.label,
                        checked = checked,
                        onToggle = { onToggleRoot(entry.path) }
                    )
                }

                if (!isLoading && pickerEntries.isEmpty()) {
                    item {
                        Text(
                            text = "No music folders found. Grant audio permission and pull to rescan the library first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textMuted,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }

            if (isScanning) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Updating library…",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentTeal,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCheckRow(
    path: String,
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.card)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = AccentTeal,
                uncheckedColor = palette.textMuted
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (label != path) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class PickerEntry(val path: String, val label: String)

/**
 * Build a picker list: all top-level segments (Music, Download, …) plus any
 * second-level paths that hold music so users can be more selective.
 */
private fun buildPickerEntries(allFolders: List<String>): List<PickerEntry> {
    val normalized = allFolders.map { FolderBrowser.normalize(it) }.filter { it.isNotEmpty() }
    val entries = linkedMapOf<String, PickerEntry>()

    // Top-level roots always
    for (path in normalized) {
        val top = path.substringBefore('/')
        entries.putIfAbsent(top, PickerEntry(top, top))
    }
    // Second-level when useful (Music/Podcasts, Download/Telegram, …)
    for (path in normalized) {
        val slash = path.indexOf('/')
        if (slash > 0) {
            val second = path.substringBeforeLast('/').takeIf { it.count { c -> c == '/' } == 0 }
                ?: path.split('/').take(2).joinToString("/")
            if (second.contains('/')) {
                entries.putIfAbsent(second, PickerEntry(second, second))
            }
        }
    }
    return entries.values.sortedBy { it.path.lowercase() }
}
