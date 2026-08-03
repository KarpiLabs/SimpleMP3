package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.prefs.ResumeSnapshot
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import io.karpilabs.simplemp3.player.PlayerConnection
import io.karpilabs.simplemp3.player.PlayerUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val playerConnection: PlayerConnection,
    private val appPreferences: AppPreferences,
    private val storageManager: LargeFileStorageManager
) : ViewModel() {

    val playerState: StateFlow<PlayerUiState> = playerConnection.state

    private val share = SharingStarted.WhileSubscribed(5_000)

    val playlists: StateFlow<List<PlaylistWithMeta>> = repository.playlists
        .stateIn(viewModelScope, share, emptyList())
    val recentlyAdded: StateFlow<List<TrackEntity>> = repository.recentlyAdded
        .stateIn(viewModelScope, share, emptyList())
    val continueListening: StateFlow<List<TrackEntity>> = repository.continueListening
        .stateIn(viewModelScope, share, emptyList())
    val trackCount: StateFlow<Int> = repository.trackCount
        .stateIn(viewModelScope, share, 0)
    val jellyfinCount: StateFlow<Int> = repository.jellyfinTrackCount
        .stateIn(viewModelScope, share, 0)
    val isScanning: StateFlow<Boolean> = repository.isScanning

    val tracks: StateFlow<List<TrackEntity>> = repository.tracks
        .stateIn(viewModelScope, share, emptyList())
    val albums: StateFlow<List<AlbumRow>> = repository.albums
        .stateIn(viewModelScope, share, emptyList())
    val artists: StateFlow<List<AlbumRow>> = repository.artists
        .stateIn(viewModelScope, share, emptyList())

    /** Top-level folders for the Library → Folders tab. */
    val rootFolders: StateFlow<List<FolderBrowser.FolderEntry>> =
        repository.childFolders("")
            .stateIn(viewModelScope, share, emptyList())

    val libraryFolderRoots: StateFlow<Set<String>> = repository.libraryFolderRoots
        .stateIn(viewModelScope, share, emptySet())

    private val _deviceFolders = MutableStateFlow<List<String>>(emptyList())
    val deviceFolders: StateFlow<List<String>> = _deviceFolders.asStateFlow()

    private val _deviceFoldersLoading = MutableStateFlow(false)
    val deviceFoldersLoading: StateFlow<Boolean> = _deviceFoldersLoading.asStateFlow()

    val driveMode: StateFlow<Boolean> = appPreferences.driveModeFlow
        .stateIn(viewModelScope, share, false)

    val autoDriveModeOnCar: StateFlow<Boolean> = appPreferences.autoDriveModeOnCarFlow
        .stateIn(viewModelScope, share, true)

    val autoResumeOnDrive: StateFlow<Boolean> = appPreferences.autoResumeOnDriveFlow
        .stateIn(viewModelScope, share, true)

    val pauseOnCarDisconnect: StateFlow<Boolean> = appPreferences.pauseOnCarDisconnectFlow
        .stateIn(viewModelScope, share, true)

    val largeFileOptimize: StateFlow<Boolean> = appPreferences.largeFileOptimizeFlow
        .stateIn(viewModelScope, share, true)

    val largeFileColdPack: StateFlow<Boolean> = appPreferences.largeFileColdPackFlow
        .stateIn(viewModelScope, share, true)

    val jellyfinEnabled: StateFlow<Boolean> = appPreferences.jellyfinEnabledFlow
        .stateIn(viewModelScope, share, false)

    val wifiOnlyDownloads: StateFlow<Boolean> = appPreferences.wifiOnlyDownloadsFlow
        .stateIn(viewModelScope, share, true)

    val resumeSnapshot: StateFlow<ResumeSnapshot?> = appPreferences.resumeFlow
        .stateIn(viewModelScope, share, null)

    val resumeEnabled: StateFlow<Boolean> = appPreferences.resumeEnabledFlow
        .stateIn(viewModelScope, share, true)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TrackEntity>> = _searchQuery
        .debounce(180)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.search(q)
        }
        .stateIn(viewModelScope, share, emptyList())

    private val _libraryFilter = MutableStateFlow("")
    val libraryFilter: StateFlow<String> = _libraryFilter.asStateFlow()

    init {
        playerConnection.connect()
        viewModelScope.launch {
            repository.ensureSystemPlaylists()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setLibraryFilter(query: String) {
        _libraryFilter.value = query
    }

    fun setDriveMode(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setDriveMode(enabled)
            if (enabled && appPreferences.isAutoResumeOnDrive()) {
                // Manual Drive mode: pick up last session if nothing is playing.
                if (!playerConnection.state.value.isPlaying) {
                    playerConnection.resumeLastSession(autoPlay = true)
                }
            }
        }
    }

    fun setAutoDriveModeOnCar(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAutoDriveModeOnCar(enabled)
        }
    }

    fun setAutoResumeOnDrive(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAutoResumeOnDrive(enabled)
        }
    }

    fun setPauseOnCarDisconnect(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setPauseOnCarDisconnect(enabled)
        }
    }

    fun setResumeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setResumeEnabled(enabled)
        }
    }

    fun setLargeFileOptimize(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setLargeFileOptimize(enabled)
        }
    }

    fun setLargeFileColdPack(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setLargeFileColdPack(enabled)
        }
    }

    fun setJellyfinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setJellyfinEnabled(enabled)
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setWifiOnlyDownloads(enabled)
        }
    }

    fun ensureLibraryReady() {
        viewModelScope.launch {
            repository.scanLibrary(force = false)
        }
    }

    fun scanLibrary(force: Boolean = true) {
        viewModelScope.launch {
            repository.scanLibrary(force = force)
        }
    }

    fun playTrack(track: TrackEntity, queue: List<TrackEntity> = tracks.value) {
        val list = if (queue.any { it.id == track.id }) queue else listOf(track)
        playerConnection.playTrackInQueue(list, track.id)
    }

    fun playAll(tracks: List<TrackEntity>, startIndex: Int = 0) {
        playerConnection.playTracks(tracks, startIndex)
    }

    fun playNext(track: TrackEntity) {
        playerConnection.playNext(track)
    }

    fun addToQueue(track: TrackEntity) {
        playerConnection.addToQueue(track)
    }

    fun resumeLastSession(autoPlay: Boolean = true) {
        playerConnection.resumeLastSession(autoPlay)
    }

    fun playPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val list = repository.getPlaylistTracksOnce(playlistId)
            if (list.isNotEmpty()) playerConnection.playTracks(list, 0)
        }
    }

    fun togglePlayPause() = playerConnection.togglePlayPause()
    fun skipNext() = playerConnection.skipNext()
    fun skipPrevious() = playerConnection.skipPrevious()
    fun seekTo(ms: Long) = playerConnection.seekTo(ms)
    fun toggleShuffle() = playerConnection.toggleShuffle()
    fun cycleRepeat() = playerConnection.cycleRepeatMode()
    fun refreshPosition() = playerConnection.refreshPosition()
    fun seekToQueueIndex(index: Int) = playerConnection.seekToQueueIndex(index)
    fun setSleepTimer(minutes: Int) {
        playerConnection.setSleepTimer(minutes)
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            onCreated(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, trackId)
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeFromPlaylist(playlistId, trackId)
        }
    }

    fun moveTrack(playlistId: Long, trackId: Long, toPosition: Int) {
        viewModelScope.launch {
            repository.moveTrackInPlaylist(playlistId, trackId, toPosition)
        }
    }

    fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            val list = repository.getPlaylistTracksOnce(playlistId)
            if (fromIndex !in list.indices || toIndex !in list.indices) return@launch
            val trackId = list[fromIndex].id
            repository.moveTrackInPlaylist(playlistId, trackId, toIndex)
        }
    }

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            repository.toggleFavorite(trackId)
        }
    }

    /** Star “never compress” on Jellyfin / YouTube offline tracks. */
    fun toggleNeverCompress(trackId: Long) {
        viewModelScope.launch {
            val current = repository.getTrack(trackId) ?: return@launch
            if (!current.isAppOwned) return@launch
            storageManager.setNeverCompress(trackId, !current.neverCompress)
        }
    }

    fun playlistTracks(playlistId: Long): StateFlow<List<TrackEntity>> =
        repository.getPlaylistTracks(playlistId)
            .stateIn(viewModelScope, share, emptyList())

    fun playlist(playlistId: Long): StateFlow<PlaylistEntity?> =
        repository.getPlaylist(playlistId)
            .stateIn(viewModelScope, share, null)

    fun albumTracks(album: String): StateFlow<List<TrackEntity>> =
        repository.getTracksByAlbum(album)
            .stateIn(viewModelScope, share, emptyList())

    fun artistTracks(artist: String): StateFlow<List<TrackEntity>> =
        repository.getTracksByArtist(artist)
            .stateIn(viewModelScope, share, emptyList())

    fun folderTracks(folderPath: String): StateFlow<List<TrackEntity>> =
        repository.getTracksByFolder(folderPath)
            .stateIn(viewModelScope, share, emptyList())

    fun childFolders(folderPath: String): StateFlow<List<FolderBrowser.FolderEntry>> =
        repository.childFolders(folderPath)
            .stateIn(viewModelScope, share, emptyList())

    fun refreshDeviceFolders() {
        viewModelScope.launch {
            _deviceFoldersLoading.value = true
            try {
                _deviceFolders.value = repository.listDeviceFolderPaths()
            } finally {
                _deviceFoldersLoading.value = false
            }
        }
    }

    fun setLibraryFolderLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                repository.setLibraryFolderRoots(emptySet())
                return@launch
            }
            // Turning the filter on: default to every top-level music root found on device.
            val folders = _deviceFolders.value.ifEmpty {
                repository.listDeviceFolderPaths().also { _deviceFolders.value = it }
            }
            val tops = folders
                .map { FolderBrowser.normalize(it).substringBefore('/') }
                .filter { it.isNotBlank() }
                .toSet()
            repository.setLibraryFolderRoots(tops)
        }
    }

    fun toggleLibraryFolderRoot(path: String) {
        viewModelScope.launch {
            val normalized = FolderBrowser.normalize(path)
            if (normalized.isEmpty()) return@launch
            val current = repository.getLibraryFolderRoots().toMutableSet()
            if (current.any { FolderBrowser.normalize(it) == normalized }) {
                current.removeAll { FolderBrowser.normalize(it) == normalized }
            } else {
                current += normalized
            }
            repository.setLibraryFolderRoots(current)
        }
    }

    fun selectAllVisibleLibraryFolderRoots(paths: Collection<String>) {
        viewModelScope.launch {
            val cleaned = paths
                .map { FolderBrowser.normalize(it) }
                .filter { it.isNotBlank() }
                .toSet()
            repository.setLibraryFolderRoots(cleaned)
        }
    }

    fun clearLibraryFolderRoots() {
        viewModelScope.launch {
            repository.setLibraryFolderRoots(emptySet())
        }
    }
}
