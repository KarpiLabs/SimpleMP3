package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.jellyfin.DiscoveredJellyfinServer
import io.karpilabs.simplemp3.data.jellyfin.JellyfinDownloadScheduler
import io.karpilabs.simplemp3.data.jellyfin.JellyfinItem
import io.karpilabs.simplemp3.data.jellyfin.JellyfinSession
import io.karpilabs.simplemp3.data.jellyfin.JellyfinSyncManager
import io.karpilabs.simplemp3.data.jellyfin.SyncProgress
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JellyfinUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val remoteAlbums: List<JellyfinItem> = emptyList(),
    val remoteTracks: List<JellyfinItem> = emptyList(),
    val remotePlaylists: List<JellyfinItem> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val browseMode: BrowseMode = BrowseMode.ALBUMS,
    val albumTracks: List<JellyfinItem> = emptyList(),
    val openAlbum: JellyfinItem? = null,
    val isDiscovering: Boolean = false,
    val discoveredServers: List<DiscoveredJellyfinServer> = emptyList(),
    val discoveryAttempted: Boolean = false,
    val wifiOnly: Boolean = true
)

enum class BrowseMode { ALBUMS, TRACKS, PLAYLISTS, OFFLINE }

@HiltViewModel
class JellyfinViewModel @Inject constructor(
    private val syncManager: JellyfinSyncManager,
    private val musicRepository: MusicRepository,
    private val downloadScheduler: JellyfinDownloadScheduler,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val session: StateFlow<JellyfinSession?> = syncManager.sessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncProgress: StateFlow<SyncProgress> = syncManager.progress

    val offlineTracks: StateFlow<List<TrackEntity>> = musicRepository.jellyfinTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val offlineCount: StateFlow<Int> = musicRepository.jellyfinTrackCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val backgroundWorkRunning: StateFlow<Boolean> = downloadScheduler.isRunningFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _ui = MutableStateFlow(JellyfinUiState())
    val ui: StateFlow<JellyfinUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            if (syncManager.getSession() == null) {
                discoverServers()
            }
            _ui.value = _ui.value.copy(wifiOnly = appPreferences.isWifiOnlyDownloads())
        }
        viewModelScope.launch {
            appPreferences.wifiOnlyDownloadsFlow.collect { enabled ->
                _ui.value = _ui.value.copy(wifiOnly = enabled)
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setWifiOnlyDownloads(enabled)
            _ui.value = _ui.value.copy(wifiOnly = enabled)
        }
    }

    fun discoverServers() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isDiscovering = true, error = null)
            val result = syncManager.discoverServers()
            val servers = result.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(
                isDiscovering = false,
                discoveryAttempted = true,
                discoveredServers = servers,
                error = when {
                    result.isFailure -> result.exceptionOrNull()?.message ?: "Discovery failed"
                    servers.isEmpty() -> "No servers found on this Wi‑Fi. Check that Jellyfin is running, or type the URL."
                    else -> null
                }
            )
        }
    }

    fun login(serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            val result = syncManager.login(serverUrl, username, password)
            result.fold(
                onSuccess = {
                    _ui.value = _ui.value.copy(isLoading = false)
                    refreshRemote()
                },
                onFailure = {
                    _ui.value = _ui.value.copy(
                        isLoading = false,
                        error = it.message ?: "Login failed"
                    )
                }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            syncManager.logout()
            _ui.value = JellyfinUiState(
                wifiOnly = appPreferences.isWifiOnlyDownloads()
            )
        }
    }

    fun setBrowseMode(mode: BrowseMode) {
        _ui.value = _ui.value.copy(browseMode = mode, openAlbum = null, albumTracks = emptyList())
        if (mode != BrowseMode.OFFLINE) refreshRemote()
    }

    fun refreshRemote() {
        viewModelScope.launch {
            if (syncManager.getSession() == null) return@launch
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            val albums = syncManager.fetchRemoteAlbums()
            val tracks = syncManager.fetchRemoteTracks(limit = 150)
            val playlists = syncManager.fetchRemotePlaylists()
            _ui.value = _ui.value.copy(
                isLoading = false,
                remoteAlbums = albums.getOrDefault(emptyList()),
                remoteTracks = tracks.getOrDefault(emptyList()),
                remotePlaylists = playlists.getOrDefault(emptyList()),
                error = albums.exceptionOrNull()?.message
                    ?: tracks.exceptionOrNull()?.message
                    ?: playlists.exceptionOrNull()?.message
            )
        }
    }

    fun openAlbum(album: JellyfinItem) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, openAlbum = album, error = null)
            val result = syncManager.fetchAlbumTracks(album.id)
            _ui.value = _ui.value.copy(
                isLoading = false,
                albumTracks = result.getOrDefault(emptyList()),
                browseMode = BrowseMode.ALBUMS,
                error = result.exceptionOrNull()?.message
            )
        }
    }

    fun closeAlbum() {
        _ui.value = _ui.value.copy(openAlbum = null, albumTracks = emptyList())
    }

    fun toggleSelect(itemId: String) {
        val current = _ui.value.selectedIds
        _ui.value = _ui.value.copy(
            selectedIds = if (itemId in current) current - itemId else current + itemId
        )
    }

    fun selectAllVisible() {
        val ids = when {
            _ui.value.openAlbum != null -> _ui.value.albumTracks.map { it.id }
            _ui.value.browseMode == BrowseMode.TRACKS -> _ui.value.remoteTracks.map { it.id }
            else -> emptyList()
        }
        _ui.value = _ui.value.copy(selectedIds = ids.toSet())
    }

    fun clearSelection() {
        _ui.value = _ui.value.copy(selectedIds = emptySet())
    }

    fun syncSelected() {
        viewModelScope.launch {
            val ui = _ui.value
            val items = when {
                ui.openAlbum != null -> {
                    val selected = ui.albumTracks.filter { it.id in ui.selectedIds }
                    selected.ifEmpty { ui.albumTracks }
                }
                ui.browseMode == BrowseMode.TRACKS -> {
                    val selected = ui.remoteTracks.filter { it.id in ui.selectedIds }
                    selected.ifEmpty { ui.remoteTracks.take(50) }
                }
                else -> emptyList()
            }
            if (items.isEmpty()) return@launch
            // Background + Wi‑Fi constraint when enabled
            downloadScheduler.enqueueItemIds(items.map { it.id })
            _ui.value = _ui.value.copy(selectedIds = emptySet())
        }
    }

    fun syncAlbum(albumId: String) {
        viewModelScope.launch {
            // Prefer immediate for single album when user taps download icon
            syncManager.syncAlbum(albumId)
        }
    }

    fun syncAll(max: Int = 200) {
        viewModelScope.launch {
            syncManager.syncAllMusic(max)
        }
    }

    fun importPlaylist(playlist: JellyfinItem) {
        viewModelScope.launch {
            downloadScheduler.enqueuePlaylistImport(
                playlistId = playlist.id,
                playlistName = playlist.name.orEmpty().ifBlank { "Playlist" }
            )
        }
    }

    fun importPlaylistNow(playlist: JellyfinItem) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true)
            syncManager.importPlaylist(playlist)
            _ui.value = _ui.value.copy(isLoading = false)
        }
    }

    fun removeOffline(trackId: Long) {
        viewModelScope.launch {
            syncManager.removeOfflineTrack(trackId)
        }
    }

    fun clearOffline() {
        viewModelScope.launch {
            syncManager.clearAllOffline()
        }
    }
}
