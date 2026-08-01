package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.local.AlbumRow
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

    val driveMode: StateFlow<Boolean> = appPreferences.driveModeFlow
        .stateIn(viewModelScope, share, false)

    val autoDriveModeOnCar: StateFlow<Boolean> = appPreferences.autoDriveModeOnCarFlow
        .stateIn(viewModelScope, share, true)

    val autoResumeOnDrive: StateFlow<Boolean> = appPreferences.autoResumeOnDriveFlow
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<TrackEntity>> = _searchQuery
        .debounce(180)
        .flatMapLatest { q ->
            if (q.isBlank()) flowOf(emptyList()) else repository.search(q)
        }
        .stateIn(viewModelScope, share, emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar.asStateFlow()

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
            _snackbar.value = if (enabled) {
                if (appPreferences.isAutoResumeOnDrive()) {
                    "Drive mode on · resuming last session"
                } else {
                    "Drive mode on"
                }
            } else {
                "Drive mode off"
            }
        }
    }

    fun setAutoDriveModeOnCar(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAutoDriveModeOnCar(enabled)
            _snackbar.value = if (enabled) {
                "Drive mode will turn on when the car connects"
            } else {
                "Car connect won’t change Drive mode"
            }
        }
    }

    fun setAutoResumeOnDrive(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setAutoResumeOnDrive(enabled)
            _snackbar.value = if (enabled) {
                "Last session resumes when you start driving"
            } else {
                "Won’t auto-play when Drive mode starts"
            }
        }
    }

    fun setLargeFileOptimize(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setLargeFileOptimize(enabled)
            _snackbar.value = if (enabled) {
                "Large files will re-encode leaner when idle"
            } else {
                "Large-file re-encode off"
            }
        }
    }

    fun setLargeFileColdPack(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setLargeFileColdPack(enabled)
            _snackbar.value = if (enabled) {
                "Large idle files will gzip until next play"
            } else {
                "Cold storage packing off"
            }
        }
    }

    fun setJellyfinEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setJellyfinEnabled(enabled)
            _snackbar.value = if (enabled) {
                "Jellyfin enabled — open it from Home"
            } else {
                "Jellyfin hidden"
            }
        }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setWifiOnlyDownloads(enabled)
            _snackbar.value = if (enabled) {
                "Downloads wait for Wi‑Fi"
            } else {
                "Downloads may use mobile data"
            }
        }
    }

    fun ensureLibraryReady() {
        viewModelScope.launch {
            repository.scanLibrary(force = false)
        }
    }

    fun scanLibrary(force: Boolean = true) {
        viewModelScope.launch {
            val count = repository.scanLibrary(force = force)
            val jf = if (jellyfinEnabled.value) jellyfinCount.value else 0
            _snackbar.value = when {
                count == 0 -> if (jellyfinEnabled.value) {
                    "No music found — try Jellyfin sync"
                } else {
                    "No music found — scan local files or add downloads"
                }
                jf > 0 -> "Library ready · $count tracks ($jf offline from Jellyfin)"
                else -> "Library ready · $count tracks"
            }
        }
    }

    fun consumeSnackbar() {
        _snackbar.value = null
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
        _snackbar.value = "Playing next · ${track.title}"
    }

    fun addToQueue(track: TrackEntity) {
        playerConnection.addToQueue(track)
        _snackbar.value = "Queued · ${track.title}"
    }

    fun resumeLastSession(autoPlay: Boolean = true) {
        playerConnection.resumeLastSession(autoPlay)
        _snackbar.value = if (autoPlay) "Resuming where you left off" else "Session restored"
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
        _snackbar.value = if (minutes > 0) "Sleep timer · $minutes min" else "Sleep timer off"
    }

    fun createPlaylist(name: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.createPlaylist(name)
            _snackbar.value = "Playlist created"
            onCreated(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { repository.renamePlaylist(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
            _snackbar.value = "Playlist deleted"
        }
    }

    fun addToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.addToPlaylist(playlistId, trackId)
            _snackbar.value = "Added to playlist"
        }
    }

    fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            repository.removeFromPlaylist(playlistId, trackId)
            _snackbar.value = "Removed from playlist"
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
            val liked = repository.toggleFavorite(trackId)
            _snackbar.value = if (liked) "Added to Liked Songs" else "Removed from Liked Songs"
        }
    }

    /** Star “never compress” on Jellyfin / YouTube offline tracks. */
    fun toggleNeverCompress(trackId: Long) {
        viewModelScope.launch {
            val current = repository.getTrack(trackId) ?: return@launch
            if (!current.isAppOwned) {
                _snackbar.value = "Never compress only applies to offline downloads"
                return@launch
            }
            val updated = storageManager.setNeverCompress(trackId, !current.neverCompress)
            _snackbar.value = when {
                updated == null -> "Couldn’t update"
                updated.neverCompress -> "Starred · never compress “${updated.title}”"
                else -> "Unstarred · compression allowed again"
            }
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
}
