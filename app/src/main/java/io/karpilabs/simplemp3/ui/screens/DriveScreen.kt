package io.karpilabs.simplemp3.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.drive.DriveBackupProgress
import io.karpilabs.simplemp3.data.drive.DriveBackupRemote
import io.karpilabs.simplemp3.data.drive.MediaBackupEstimate
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.NightBlack
import io.karpilabs.simplemp3.ui.theme.NightCard
import io.karpilabs.simplemp3.ui.theme.NightElevated
import io.karpilabs.simplemp3.ui.theme.TextMuted
import io.karpilabs.simplemp3.ui.theme.TextSecondary
import io.karpilabs.simplemp3.ui.viewmodel.DriveUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun DriveScreen(
    ui: DriveUiState,
    progress: DriveBackupProgress,
    includeMedia: Boolean,
    wifiOnly: Boolean,
    lastBackupMs: Long,
    signInIntent: () -> Intent,
    onBack: () -> Unit,
    onSignInResult: (Intent?) -> Unit,
    onSignOut: () -> Unit,
    onIncludeMediaChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: (DriveBackupRemote) -> Unit,
    onDelete: (DriveBackupRemote) -> Unit,
    onRefresh: () -> Unit
) {
    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Always pass data — Google Sign-In may still return an account with RESULT_OK
        onSignInResult(result.data)
    }

    val signedIn = !ui.signedInEmail.isNullOrBlank()
    val busy = ui.busy || progress.active

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Drive",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Backup playlists & optional offline music",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.Rounded.Cloud,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.padding(end = 16.dp)
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                AccountCard(
                    email = ui.signedInEmail,
                    signedIn = signedIn,
                    busy = busy,
                    onSignIn = { signInLauncher.launch(signInIntent()) },
                    onSignOut = onSignOut
                )
            }

            item {
                OptionsCard(
                    includeMedia = includeMedia,
                    wifiOnly = wifiOnly,
                    mediaEstimate = ui.mediaEstimate,
                    lastBackupMs = lastBackupMs,
                    onIncludeMediaChange = onIncludeMediaChange,
                    onWifiOnlyChange = onWifiOnlyChange
                )
            }

            item {
                BackupActionsCard(
                    signedIn = signedIn,
                    busy = busy,
                    progress = progress,
                    includeMedia = includeMedia,
                    onBackup = onBackup,
                    onRefresh = onRefresh
                )
            }

            item {
                Text(
                    text = "Cloud backups",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentTeal,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            if (!signedIn) {
                item {
                    Text(
                        text = "Sign in to see backups stored in “Simple MP3 Backups” on your Drive.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            } else if (ui.backups.isEmpty() && !busy) {
                item {
                    Text(
                        text = "No backups yet. Create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            items(ui.backups, key = { it.fileId }) { backup ->
                BackupRow(
                    backup = backup,
                    enabled = !busy,
                    onRestore = { onRestore(backup) },
                    onDelete = { onDelete(backup) }
                )
            }

            item {
                AboutCard()
            }
        }
    }
}

@Composable
private fun AccountCard(
    email: String?,
    signedIn: Boolean,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightCard)
            .padding(16.dp)
    ) {
        Text(
            text = if (signedIn) "Signed in" else "Not signed in",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = email ?: "Connect a Google account with Drive access",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (signedIn) {
                TextButton(onClick = onSignOut, enabled = !busy) {
                    Icon(Icons.Rounded.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign out")
                }
            } else {
                Button(
                    onClick = onSignIn,
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentViolet,
                        contentColor = NightBlack
                    )
                ) {
                    Icon(Icons.Rounded.Login, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
            }
        }
    }
}

@Composable
private fun OptionsCard(
    includeMedia: Boolean,
    wifiOnly: Boolean,
    mediaEstimate: MediaBackupEstimate,
    lastBackupMs: Long,
    onIncludeMediaChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightCard)
            .padding(16.dp)
    ) {
        ToggleRow(
            icon = Icons.Rounded.CloudUpload,
            title = "Include offline music files",
            subtitle = if (mediaEstimate.trackCount > 0) {
                "${mediaEstimate.trackCount} app-owned tracks · ${formatBytes(mediaEstimate.totalBytes)}"
            } else {
                "YouTube, LAN, Jellyfin offline files (can be large)"
            },
            checked = includeMedia,
            onCheckedChange = onIncludeMediaChange
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            icon = Icons.Rounded.Wifi,
            title = "Wi‑Fi only",
            subtitle = "Require unmetered network for backup & restore",
            checked = wifiOnly,
            onCheckedChange = onWifiOnlyChange
        )
        if (lastBackupMs > 0L) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Last backup · ${
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(lastBackupMs))
                }",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AccentTeal, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NightCard,
                checkedTrackColor = AccentTeal,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = TextMuted.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun BackupActionsCard(
    signedIn: Boolean,
    busy: Boolean,
    progress: DriveBackupProgress,
    includeMedia: Boolean,
    onBackup: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightCard)
            .padding(16.dp)
    ) {
        Button(
            onClick = onBackup,
            enabled = signedIn && !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentTeal,
                contentColor = NightBlack
            )
        ) {
            if (busy && progress.active) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = NightBlack
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (includeMedia) "Back up library + media" else "Back up playlists & settings"
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onRefresh,
            enabled = signedIn && !busy,
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Refresh list")
        }
        if (progress.active) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = progress.phase,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (progress.percent in 0..100) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AccentTeal,
                    trackColor = NightElevated
                )
            }
        }
    }
}

@Composable
private fun BackupRow(
    backup: DriveBackupRemote,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NightCard)
            .padding(14.dp)
    ) {
        Text(
            text = backup.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                if (backup.modifiedTimeMs > 0) {
                    append(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(backup.modifiedTimeMs))
                    )
                    append(" · ")
                }
                append(formatBytes(backup.sizeBytes))
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRestore, enabled = enabled) {
                Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(18.dp), tint = AccentTeal)
                Spacer(Modifier.width(4.dp))
                Text("Restore", color = AccentTeal)
            }
            TextButton(onClick = onDelete, enabled = enabled) {
                Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp), tint = AccentCoral)
                Spacer(Modifier.width(4.dp))
                Text("Delete", color = AccentCoral)
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NightElevated)
            .padding(14.dp)
    ) {
        Text(
            text = "How backups work",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Metadata backups store playlists, likes, and settings. " +
                "Optional media includes YouTube, LAN, and Jellyfin offline files the app owns — " +
                "not your entire phone MediaStore library. " +
                "Files go to a “Simple MP3 Backups” folder (app-created only). " +
                "Requires a Google Cloud OAuth client for this package + SHA‑1.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format("%.1f GB", bytes / gb)
        bytes >= mb -> String.format("%.1f MB", bytes / mb)
        bytes >= kb -> String.format("%.0f KB", bytes / kb)
        else -> "$bytes B"
    }
}
