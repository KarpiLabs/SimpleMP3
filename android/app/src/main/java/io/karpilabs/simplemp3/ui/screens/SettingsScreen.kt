package io.karpilabs.simplemp3.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Speed
import io.karpilabs.simplemp3.data.prefs.BufferProfile
import io.karpilabs.simplemp3.data.prefs.ThemeMode
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.LocalSimpleMP3Palette

@Composable
fun SettingsScreen(
    jellyfinEnabled: Boolean,
    autoDriveModeOnCar: Boolean,
    autoResumeOnDrive: Boolean,
    pauseOnCarDisconnect: Boolean,
    resumeEnabled: Boolean,
    wifiOnlyDownloads: Boolean,
    largeFileOptimize: Boolean,
    largeFileColdPack: Boolean,
    themeMode: ThemeMode,
    bufferProfile: BufferProfile,
    onBack: () -> Unit,
    onJellyfinEnabledChange: (Boolean) -> Unit,
    onAutoDriveModeOnCarChange: (Boolean) -> Unit,
    onAutoResumeOnDriveChange: (Boolean) -> Unit,
    onPauseOnCarDisconnectChange: (Boolean) -> Unit,
    onResumeEnabledChange: (Boolean) -> Unit,
    onWifiOnlyDownloadsChange: (Boolean) -> Unit,
    onLargeFileOptimizeChange: (Boolean) -> Unit,
    onLargeFileColdPackChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBufferProfileChange: (BufferProfile) -> Unit,
    onOpenQuickConnect: () -> Unit = {},
    onOpenLibraryFolders: () -> Unit = {},
    onOpenHiddenSongs: () -> Unit = {},
) {
    val context = LocalContext.current
    val versionLabel =
        remember {
            runCatching {
                val pm = context.packageManager
                val pkg = context.packageName
                val info =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(pkg, 0)
                    }
                val name = info.versionName ?: "?"
                val code =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        info.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode.toLong()
                    }
                "$name ($code)"
            }.getOrDefault("—")
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                SettingsSectionHeader("Integrations")
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.CloudDownload,
                    iconTint = AccentViolet,
                    title = "Jellyfin",
                    subtitle =
                        if (jellyfinEnabled) {
                            "On · sync & offline from your server"
                        } else {
                            "Off · hidden until you enable it"
                        },
                    checked = jellyfinEnabled,
                    onCheckedChange = onJellyfinEnabledChange,
                )
            }
            if (jellyfinEnabled) {
                item {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Wifi,
                        title = "Wi‑Fi only downloads",
                        subtitle =
                            if (wifiOnlyDownloads) {
                                "Jellyfin downloads wait for Wi‑Fi"
                            } else {
                                "Downloads may use mobile data"
                            },
                        checked = wifiOnlyDownloads,
                        onCheckedChange = onWifiOnlyDownloadsChange,
                    )
                }
            }

            item {
                SettingsSectionHeader("Library")
            }
            item {
                SettingsNavRow(
                    icon = Icons.Rounded.Folder,
                    title = "Library folders",
                    subtitle = "Browse by path · optionally limit which folders are scanned",
                    onClick = onOpenLibraryFolders,
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Rounded.WifiTethering,
                    title = "Quick Connect",
                    subtitle = "Host a temporary LAN portal to upload & manage playlists",
                    onClick = onOpenQuickConnect,
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Hidden songs",
                    subtitle = "Manage songs long-press-hidden from your library",
                    onClick = onOpenHiddenSongs,
                )
            }

            item {
                SettingsSectionHeader("Display")
            }
            item {
                SettingsNavRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Theme",
                    subtitle =
                        when (themeMode) {
                            ThemeMode.SYSTEM -> "Follows system light/dark setting"
                            ThemeMode.LIGHT -> "Light — always on"
                            ThemeMode.DARK -> "Dark — always on"
                        },
                    onClick = {
                        val next =
                            when (themeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.SYSTEM
                            }
                        onThemeModeChange(next)
                    },
                )
            }

            item {
                SettingsSectionHeader("Playback")
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Pick up where you left off",
                    subtitle =
                        if (resumeEnabled) {
                            "Show a resume prompt for your last session on Home"
                        } else {
                            "Home always starts fresh"
                        },
                    checked = resumeEnabled,
                    onCheckedChange = onResumeEnabledChange,
                )
            }
            item {
                SettingsNavRow(
                    icon = Icons.Rounded.Speed,
                    title = "Playback buffer",
                    subtitle =
                        when (bufferProfile) {
                            BufferProfile.SMALL -> "Small · faster start, more rebuffering · applies after restart"
                            BufferProfile.BALANCED -> "Balanced · default · applies after restart"
                            BufferProfile.LARGE -> "Large · smoothest on flaky streams, more memory · applies after restart"
                        },
                    onClick = {
                        val next =
                            when (bufferProfile) {
                                BufferProfile.SMALL -> BufferProfile.BALANCED
                                BufferProfile.BALANCED -> BufferProfile.LARGE
                                BufferProfile.LARGE -> BufferProfile.SMALL
                            }
                        onBufferProfileChange(next)
                    },
                )
            }

            item {
                SettingsSectionHeader("Driving")
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.DirectionsCar,
                    title = "Auto → Drive mode",
                    subtitle =
                        if (autoDriveModeOnCar) {
                            "Turn Drive mode on when Android Auto connects"
                        } else {
                            "Car connect won’t change Drive mode"
                        },
                    checked = autoDriveModeOnCar,
                    onCheckedChange = onAutoDriveModeOnCarChange,
                )
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = "Auto-resume when driving",
                    subtitle =
                        if (autoResumeOnDrive) {
                            "Restore last queue & play when the car connects or Drive mode turns on"
                        } else {
                            "You’ll start playback manually"
                        },
                    checked = autoResumeOnDrive,
                    onCheckedChange = onAutoResumeOnDriveChange,
                )
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.PauseCircle,
                    title = "Pause when car disconnects",
                    subtitle =
                        if (pauseOnCarDisconnect) {
                            "Stop audio when Android Auto / the car disconnects"
                        } else {
                            "Keep playing on the phone after disconnect"
                        },
                    checked = pauseOnCarDisconnect,
                    onCheckedChange = onPauseOnCarDisconnectChange,
                )
            }

            item {
                SettingsSectionHeader("Storage")
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Compress,
                    title = "Large file optimize",
                    subtitle =
                        if (largeFileOptimize) {
                            "Re-encode huge offline tracks leaner (96–128 kbps)"
                        } else {
                            "Keep original bitrate for offline downloads"
                        },
                    checked = largeFileOptimize,
                    onCheckedChange = onLargeFileOptimizeChange,
                )
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Rounded.Storage,
                    title = "Cold storage (large only)",
                    subtitle =
                        if (largeFileColdPack) {
                            "Gzip idle 25+ MB files; thaw automatically on play"
                        } else {
                            "Keep large offline files fully expanded"
                        },
                    checked = largeFileColdPack,
                    onCheckedChange = onLargeFileColdPackChange,
                )
            }

            item {
                SettingsSectionHeader("About")
            }
            item {
                AboutCard(versionLabel = versionLabel)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = AccentTeal,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = AccentTeal,
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = AccentTeal,
) {
    val palette = LocalSimpleMP3Palette.current
    Row(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = palette.card,
                    checkedTrackColor = AccentTeal,
                    uncheckedThumbColor = palette.textMuted,
                    uncheckedTrackColor = palette.textMuted.copy(alpha = 0.3f),
                ),
        )
    }
}

@Composable
private fun AboutCard(versionLabel: String) {
    val palette = LocalSimpleMP3Palette.current
    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.card)
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = AccentTeal,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Simple MP3",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Version $versionLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = palette.textMuted.copy(alpha = 0.25f))
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Local music player with playlists, optional Jellyfin offline sync, YouTube → MP3, Quick Connect LAN portal, and Android Auto.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your music stays on your device. Jellyfin only talks to the server you configure. Quick Connect is local Wi‑Fi only and stops when you leave that screen.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
        )
    }
}
