package io.karpilabs.simplemp3.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.karpilabs.simplemp3.data.jellyfin.DiscoveredJellyfinServer
import io.karpilabs.simplemp3.data.jellyfin.JellyfinItem
import io.karpilabs.simplemp3.data.jellyfin.JellyfinSession
import io.karpilabs.simplemp3.data.jellyfin.SyncProgress
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.ui.components.AlbumArt
import io.karpilabs.simplemp3.ui.theme.AccentCoral
import io.karpilabs.simplemp3.ui.theme.AccentTeal
import io.karpilabs.simplemp3.ui.theme.AccentViolet
import io.karpilabs.simplemp3.ui.theme.NightBlack
import io.karpilabs.simplemp3.ui.theme.NightCard
import io.karpilabs.simplemp3.ui.theme.NightElevated
import io.karpilabs.simplemp3.ui.theme.TextMuted
import io.karpilabs.simplemp3.ui.theme.TextSecondary
import io.karpilabs.simplemp3.ui.util.formatDuration
import io.karpilabs.simplemp3.ui.util.formatTrackCount
import io.karpilabs.simplemp3.ui.viewmodel.BrowseMode
import io.karpilabs.simplemp3.ui.viewmodel.JellyfinUiState

@Composable
fun JellyfinScreen(
    session: JellyfinSession?,
    ui: JellyfinUiState,
    progress: SyncProgress,
    offlineTracks: List<TrackEntity>,
    offlineCount: Int,
    onBack: () -> Unit,
    onLogin: (server: String, user: String, pass: String) -> Unit,
    onDiscover: () -> Unit,
    onLogout: () -> Unit,
    onRefresh: () -> Unit,
    onBrowseMode: (BrowseMode) -> Unit,
    onOpenAlbum: (JellyfinItem) -> Unit,
    onCloseAlbum: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSyncSelected: () -> Unit,
    onSyncAlbum: (String) -> Unit,
    onSyncAll: () -> Unit,
    onImportPlaylist: (JellyfinItem) -> Unit,
    onImportPlaylistNow: (JellyfinItem) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onRemoveOffline: (Long) -> Unit,
    onClearOffline: () -> Unit,
    onPlayOfflineTrack: (TrackEntity) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Jellyfin Sync",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (session != null) {
                        "${session.userName} · ${session.serverUrl.removePrefix("http://").removePrefix("https://")}"
                    } else {
                        "Pull music offline for the car"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session != null) {
                IconButton(onClick = onRefresh, enabled = !ui.isLoading && !progress.isActive) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = AccentTeal)
                }
            }
        }

        AnimatedVisibility(visible = progress.isActive || progress.phase == "Done" || progress.error != null) {
            SyncBanner(progress)
        }

        if (session == null) {
            LoginCard(
                isLoading = ui.isLoading,
                isDiscovering = ui.isDiscovering,
                discoveredServers = ui.discoveredServers,
                discoveryAttempted = ui.discoveryAttempted,
                error = ui.error,
                onLogin = onLogin,
                onDiscover = onDiscover
            )
        } else {
            ConnectedContent(
                ui = ui,
                progress = progress,
                offlineTracks = offlineTracks,
                offlineCount = offlineCount,
                onLogout = onLogout,
                onBrowseMode = onBrowseMode,
                onOpenAlbum = onOpenAlbum,
                onCloseAlbum = onCloseAlbum,
                onToggleSelect = onToggleSelect,
                onSelectAll = onSelectAll,
                onSyncSelected = onSyncSelected,
                onSyncAlbum = onSyncAlbum,
                onSyncAll = onSyncAll,
                onImportPlaylist = onImportPlaylist,
                onImportPlaylistNow = onImportPlaylistNow,
                onWifiOnlyChange = onWifiOnlyChange,
                onRemoveOffline = onRemoveOffline,
                onClearOffline = onClearOffline,
                onPlayOfflineTrack = onPlayOfflineTrack
            )
        }
    }
}

@Composable
private fun SyncBanner(progress: SyncProgress) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AccentTeal.copy(alpha = 0.18f), AccentViolet.copy(alpha = 0.12f))
                )
            )
            .padding(14.dp)
    ) {
        Text(
            text = progress.phase,
            style = MaterialTheme.typography.titleSmall,
            color = AccentTeal
        )
        if (progress.currentTitle.isNotBlank()) {
            Text(
                text = progress.currentTitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (progress.total > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = AccentTeal,
                trackColor = NightElevated
            )
            Text(
                text = "${progress.current}/${progress.total}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        progress.error?.let {
            Text(text = it, color = AccentCoral, style = MaterialTheme.typography.bodySmall)
        }
        progress.lastResult?.takeIf { !progress.isActive }?.let {
            Text(text = it, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoginCard(
    isLoading: Boolean,
    isDiscovering: Boolean,
    discoveredServers: List<DiscoveredJellyfinServer>,
    discoveryAttempted: Boolean,
    error: String?,
    onLogin: (String, String, String) -> Unit,
    onDiscover: () -> Unit
) {
    var server by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AccentViolet.copy(alpha = 0.35f),
                                NightCard,
                                AccentTeal.copy(alpha = 0.2f)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Icon(
                        Icons.Rounded.CloudDownload,
                        contentDescription = null,
                        tint = AccentTeal,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Offline from Jellyfin",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "We’ll scan your Wi‑Fi for Jellyfin, or you can paste a server URL. Then download music for offline & Android Auto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Local servers",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onDiscover,
                    enabled = !isDiscovering
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = AccentTeal
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scanning…", color = AccentTeal)
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = AccentTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Find servers", color = AccentTeal)
                    }
                }
            }
        }

        if (isDiscovering && discoveredServers.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NightCard)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = AccentTeal
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Looking for Jellyfin on your Wi‑Fi…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        items(discoveredServers, key = { it.address }) { found ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NightCard)
                    .border(
                        width = if (server.trimEnd('/') == found.address.trimEnd('/')) 1.5.dp else 0.dp,
                        color = AccentTeal,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { server = found.address }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Storage, null, tint = AccentTeal)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = found.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = found.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Use",
                    style = MaterialTheme.typography.labelLarge,
                    color = AccentTeal
                )
            }
        }

        if (discoveryAttempted && !isDiscovering && discoveredServers.isEmpty()) {
            item {
                Text(
                    text = "Nothing found on LAN. Make sure your phone is on the same Wi‑Fi as the server (not guest/AP isolation), then try again — or enter the URL below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        item {
            FieldLabel("Server URL")
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("http://192.168.1.10:8096") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Link, null, tint = TextMuted) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = fieldColors(),
                shape = RoundedCornerShape(14.dp)
            )
        }
        item {
            FieldLabel("Username")
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = fieldColors(),
                shape = RoundedCornerShape(14.dp)
            )
        }
        item {
            FieldLabel("Password")
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                colors = fieldColors(),
                shape = RoundedCornerShape(14.dp)
            )
        }
        if (!error.isNullOrBlank()) {
            item {
                Text(text = error, color = AccentCoral, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Button(
                onClick = { onLogin(server, user, pass) },
                enabled = !isLoading && server.isNotBlank() && user.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = NightBlack
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = NightBlack
                    )
                } else {
                    Text("Connect", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        item {
            Text(
                text = "Tip: Use your LAN IP for car/offline downloads at home. HTTPS reverse proxies work too.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    ui: JellyfinUiState,
    progress: SyncProgress,
    offlineTracks: List<TrackEntity>,
    offlineCount: Int,
    onLogout: () -> Unit,
    onBrowseMode: (BrowseMode) -> Unit,
    onOpenAlbum: (JellyfinItem) -> Unit,
    onCloseAlbum: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSyncSelected: () -> Unit,
    onSyncAlbum: (String) -> Unit,
    onSyncAll: () -> Unit,
    onImportPlaylist: (JellyfinItem) -> Unit,
    onImportPlaylistNow: (JellyfinItem) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onRemoveOffline: (Long) -> Unit,
    onClearOffline: () -> Unit,
    onPlayOfflineTrack: (TrackEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ModeChip("Albums", ui.browseMode == BrowseMode.ALBUMS && ui.openAlbum == null) {
                onBrowseMode(BrowseMode.ALBUMS)
            }
            ModeChip("Tracks", ui.browseMode == BrowseMode.TRACKS) {
                onBrowseMode(BrowseMode.TRACKS)
            }
            ModeChip("Playlists", ui.browseMode == BrowseMode.PLAYLISTS) {
                onBrowseMode(BrowseMode.PLAYLISTS)
            }
            ModeChip("Offline ($offlineCount)", ui.browseMode == BrowseMode.OFFLINE) {
                onBrowseMode(BrowseMode.OFFLINE)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterChip(
                selected = ui.wifiOnly,
                onClick = { onWifiOnlyChange(!ui.wifiOnly) },
                label = { Text(if (ui.wifiOnly) "Wi‑Fi only" else "Any network") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentTeal.copy(alpha = 0.2f),
                    selectedLabelColor = AccentTeal,
                    containerColor = NightCard,
                    labelColor = TextSecondary
                )
            )
            TextButton(onClick = onLogout) {
                Text("Log out", color = TextMuted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onSyncAll,
                enabled = !progress.isActive,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.CloudDownload, null, tint = AccentTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sync library", color = AccentTeal)
            }
        }

        when {
            ui.openAlbum != null -> AlbumDetailPane(
                album = ui.openAlbum,
                tracks = ui.albumTracks,
                selected = ui.selectedIds,
                loading = ui.isLoading,
                syncing = progress.isActive,
                onBack = onCloseAlbum,
                onToggle = onToggleSelect,
                onSelectAll = onSelectAll,
                onSyncSelected = onSyncSelected,
                onSyncAlbum = { onSyncAlbum(ui.openAlbum.id) }
            )

            ui.browseMode == BrowseMode.OFFLINE -> OfflineList(
                tracks = offlineTracks,
                onPlay = onPlayOfflineTrack,
                onRemove = onRemoveOffline,
                onClear = onClearOffline
            )

            ui.browseMode == BrowseMode.TRACKS -> RemoteTrackList(
                tracks = ui.remoteTracks,
                selected = ui.selectedIds,
                loading = ui.isLoading,
                syncing = progress.isActive,
                onToggle = onToggleSelect,
                onSelectAll = onSelectAll,
                onSyncSelected = onSyncSelected
            )

            ui.browseMode == BrowseMode.PLAYLISTS -> RemotePlaylistList(
                playlists = ui.remotePlaylists,
                loading = ui.isLoading,
                wifiOnly = ui.wifiOnly,
                onImportWifi = onImportPlaylist,
                onImportNow = onImportPlaylistNow
            )

            else -> RemoteAlbumList(
                albums = ui.remoteAlbums,
                loading = ui.isLoading,
                syncing = progress.isActive,
                onOpen = onOpenAlbum,
                onSyncAlbum = onSyncAlbum
            )
        }
    }
}

@Composable
private fun RemotePlaylistList(
    playlists: List<JellyfinItem>,
    loading: Boolean,
    wifiOnly: Boolean,
    onImportWifi: (JellyfinItem) -> Unit,
    onImportNow: (JellyfinItem) -> Unit
) {
    if (loading && playlists.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentTeal)
        }
        return
    }
    if (playlists.isEmpty()) {
        Text(
            "No server playlists found.",
            color = TextSecondary,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)) {
        item {
            Text(
                text = if (wifiOnly) {
                    "Import queues a Wi‑Fi download + creates a local playlist for Auto."
                } else {
                    "Import downloads in the background and creates a local playlist."
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        items(playlists, key = { it.id }) { pl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NightElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Storage, null, tint = AccentViolet)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        pl.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            pl.childCount?.let {
                                append(formatTrackCount(it))
                                append(" · ")
                            }
                            append("Import offline")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { onImportWifi(pl) }) {
                        Text(if (wifiOnly) "Wi‑Fi" else "Queue", color = AccentTeal)
                    }
                    TextButton(onClick = { onImportNow(pl) }) {
                        Text("Now", color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentTeal.copy(alpha = 0.2f),
            selectedLabelColor = AccentTeal,
            containerColor = NightCard,
            labelColor = TextSecondary
        )
    )
}

@Composable
private fun RemoteAlbumList(
    albums: List<JellyfinItem>,
    loading: Boolean,
    syncing: Boolean,
    onOpen: (JellyfinItem) -> Unit,
    onSyncAlbum: (String) -> Unit
) {
    if (loading && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentTeal)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp)) {
        items(albums, key = { it.id }) { album ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(album) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(NightElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, null, tint = AccentTeal)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        album.name.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(album.albumArtist ?: album.artistName)
                            album.childCount?.let { append(" · "); append(formatTrackCount(it)) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onSyncAlbum(album.id) },
                    enabled = !syncing
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = "Sync album", tint = AccentTeal)
                }
            }
        }
    }
}

@Composable
private fun RemoteTrackList(
    tracks: List<JellyfinItem>,
    selected: Set<String>,
    loading: Boolean,
    syncing: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSyncSelected: () -> Unit
) {
    Column {
        ActionRow(
            selectedCount = selected.size,
            syncing = syncing,
            onSelectAll = onSelectAll,
            onSync = onSyncSelected
        )
        if (loading && tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentTeal)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(tracks, key = { it.id }) { track ->
                    SelectableRemoteTrack(
                        track = track,
                        selected = track.id in selected,
                        onToggle = { onToggle(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailPane(
    album: JellyfinItem,
    tracks: List<JellyfinItem>,
    selected: Set<String>,
    loading: Boolean,
    syncing: Boolean,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onSyncSelected: () -> Unit,
    onSyncAlbum: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("← Albums", color = AccentTeal)
            }
            Text(
                text = album.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSyncAlbum, enabled = !syncing) {
                Icon(Icons.Rounded.Download, null, tint = AccentTeal)
            }
        }
        ActionRow(
            selectedCount = selected.size,
            syncing = syncing,
            onSelectAll = onSelectAll,
            onSync = onSyncSelected,
            syncLabel = if (selected.isEmpty()) "Sync album" else "Sync selected"
        )
        if (loading && tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentTeal)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(tracks, key = { it.id }) { track ->
                    SelectableRemoteTrack(
                        track = track,
                        selected = track.id in selected,
                        onToggle = { onToggle(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    selectedCount: Int,
    syncing: Boolean,
    onSelectAll: () -> Unit,
    onSync: () -> Unit,
    syncLabel: String = "Download selected"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onSelectAll) {
            Text("Select all", color = TextSecondary)
        }
        Button(
            onClick = onSync,
            enabled = !syncing,
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentTeal,
                contentColor = NightBlack
            )
        ) {
            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (selectedCount > 0) "$syncLabel ($selectedCount)" else syncLabel
            )
        }
    }
}

@Composable
private fun SelectableRemoteTrack(
    track: JellyfinItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) AccentTeal else TextMuted,
                    shape = CircleShape
                )
                .background(if (selected) AccentTeal.copy(alpha = 0.25f) else NightCard),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = AccentTeal,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artistName} · ${formatDuration(track.durationMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OfflineList(
    tracks: List<TrackEntity>,
    onPlay: (TrackEntity) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit
) {
    if (tracks.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.CloudOff, null, tint = TextMuted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Nothing offline yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Download albums or tracks from Jellyfin. They'll appear here and in Android Auto.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Storage, null, tint = AccentTeal)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatTrackCount(tracks.size) + " ready offline",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(onClick = onClear) {
                    Text("Clear all", color = AccentCoral)
                }
            }
        }
        items(tracks, key = { it.id }) { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(track) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(artworkUri = track.artworkUri, contentDescription = track.album, size = 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${track.artist} · Jellyfin",
                        style = MaterialTheme.typography.bodySmall,
                        color = AccentViolet,
                        maxLines = 1
                    )
                }
                IconButton(onClick = { onRemove(track.id) }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 6.dp, top = 4.dp)
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentTeal,
    unfocusedBorderColor = NightElevated,
    focusedContainerColor = NightCard,
    unfocusedContainerColor = NightCard,
    cursorColor = AccentTeal
)
