package io.karpilabs.simplemp3.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.karpilabs.simplemp3.data.duplicates.DuplicateDetector
import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.playbackQueue
import io.karpilabs.simplemp3.data.playlist.M3uPlaylist
import io.karpilabs.simplemp3.data.search.LibrarySearch
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.prefs.BufferProfile
import io.karpilabs.simplemp3.data.prefs.ResumeSnapshot
import io.karpilabs.simplemp3.data.prefs.ThemeMode
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.scrobble.ScrobbleConfig
import io.karpilabs.simplemp3.data.scrobble.ScrobbleManager
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import io.karpilabs.simplemp3.player.PlayerConnection
import io.karpilabs.simplemp3.player.PlayerUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicViewModel
    @Inject
    constructor(
        private val repository: MusicRepository,
        private val playerConnection: PlayerConnection,
        private val appPreferences: AppPreferences,
        private val storageManager: LargeFileStorageManager,
        private val scrobbleManager: ScrobbleManager,
    ) : ViewModel() {
        val playerState: StateFlow<PlayerUiState> = playerConnection.state

        private val share = SharingStarted.WhileSubscribed(5_000)

        val playlists: StateFlow<List<PlaylistWithMeta>> =
            repository.playlists
                .stateIn(viewModelScope, share, emptyList())
        val recentlyAdded: StateFlow<List<TrackEntity>> =
            repository.recentlyAdded
                .stateIn(viewModelScope, share, emptyList())
        val continueListening: StateFlow<List<TrackEntity>> =
            repository.continueListening
                .stateIn(viewModelScope, share, emptyList())
        val trackCount: StateFlow<Int> =
            repository.trackCount
                .stateIn(viewModelScope, share, 0)
        val jellyfinCount: StateFlow<Int> =
            repository.jellyfinTrackCount
                .stateIn(viewModelScope, share, 0)
        val isScanning: StateFlow<Boolean> = repository.isScanning

        val tracks: StateFlow<List<TrackEntity>> =
            repository.tracks
                .stateIn(viewModelScope, share, emptyList())
        val albums: StateFlow<List<AlbumRow>> =
            repository.albums
                .stateIn(viewModelScope, share, emptyList())
        val artists: StateFlow<List<AlbumRow>> =
            repository.artists
                .stateIn(viewModelScope, share, emptyList())

        val genres: StateFlow<List<AlbumRow>> =
            repository.genres
                .stateIn(viewModelScope, share, emptyList())

        /** Top-level folders for the Library → Folders tab. */
        val rootFolders: StateFlow<List<FolderBrowser.FolderEntry>> =
            repository
                .childFolders("")
                .stateIn(viewModelScope, share, emptyList())

        val libraryFolderRoots: StateFlow<Set<String>> =
            repository.libraryFolderRoots
                .stateIn(viewModelScope, share, emptySet())

        val hiddenTracks: StateFlow<List<TrackEntity>> =
            repository.hiddenTracks
                .stateIn(viewModelScope, share, emptyList())

        private val _deviceFolders = MutableStateFlow<List<String>>(emptyList())
        val deviceFolders: StateFlow<List<String>> = _deviceFolders.asStateFlow()

        private val _deviceFoldersLoading = MutableStateFlow(false)
        val deviceFoldersLoading: StateFlow<Boolean> = _deviceFoldersLoading.asStateFlow()

        val driveMode: StateFlow<Boolean> =
            appPreferences.driveModeFlow
                .stateIn(viewModelScope, share, false)

        val autoDriveModeOnCar: StateFlow<Boolean> =
            appPreferences.autoDriveModeOnCarFlow
                .stateIn(viewModelScope, share, true)

        val autoResumeOnDrive: StateFlow<Boolean> =
            appPreferences.autoResumeOnDriveFlow
                .stateIn(viewModelScope, share, true)

        val pauseOnCarDisconnect: StateFlow<Boolean> =
            appPreferences.pauseOnCarDisconnectFlow
                .stateIn(viewModelScope, share, true)

        val largeFileOptimize: StateFlow<Boolean> =
            appPreferences.largeFileOptimizeFlow
                .stateIn(viewModelScope, share, true)

        val largeFileColdPack: StateFlow<Boolean> =
            appPreferences.largeFileColdPackFlow
                .stateIn(viewModelScope, share, true)

        val jellyfinEnabled: StateFlow<Boolean> =
            appPreferences.jellyfinEnabledFlow
                .stateIn(viewModelScope, share, false)

        val wifiOnlyDownloads: StateFlow<Boolean> =
            appPreferences.wifiOnlyDownloadsFlow
                .stateIn(viewModelScope, share, true)

        val resumeSnapshot: StateFlow<ResumeSnapshot?> =
            appPreferences.resumeFlow
                .stateIn(viewModelScope, share, null)

        val resumeEnabled: StateFlow<Boolean> =
            appPreferences.resumeEnabledFlow
                .stateIn(viewModelScope, share, true)

        val themeMode: StateFlow<ThemeMode> =
            appPreferences.themeModeFlow
                .stateIn(viewModelScope, share, ThemeMode.SYSTEM)

        val bufferProfile: StateFlow<BufferProfile> =
            appPreferences.bufferProfileFlow
                .stateIn(viewModelScope, share, BufferProfile.BALANCED)

        val normalizeVolume: StateFlow<Boolean> =
            appPreferences.normalizeVolumeFlow
                .stateIn(viewModelScope, share, false)

        val normalizePreampDb: StateFlow<Int> =
            appPreferences.normalizePreampDbFlow
                .stateIn(viewModelScope, share, 0)

        val scrobbleConfig: StateFlow<ScrobbleConfig> =
            appPreferences.scrobbleConfigFlow
                .stateIn(
                    viewModelScope,
                    share,
                    ScrobbleConfig(
                        provider = ScrobbleConfig.PROVIDER_NONE,
                        enabled = false,
                        listenBrainzToken = "",
                        lastfmSessionKey = "",
                        lastfmUsername = "",
                        lastfmApiKey = "",
                        lastfmApiSecret = "",
                    ),
                )

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        @OptIn(FlowPreview::class)
        val searchResults: StateFlow<LibrarySearch.Results> =
            combine(
                _searchQuery.debounce(180),
                tracks,
                albums,
                artists,
                playlists,
            ) { q, t, a, ar, p ->
                LibrarySearch.query(q, t, a, ar, p)
            }.stateIn(viewModelScope, share, LibrarySearch.Results())

        val safTreeUris: StateFlow<List<String>> =
            repository.safTreeUris
                .stateIn(viewModelScope, share, emptyList())

        private val _duplicateGroups = MutableStateFlow<List<DuplicateDetector.Group>>(emptyList())
        val duplicateGroups: StateFlow<List<DuplicateDetector.Group>> = _duplicateGroups.asStateFlow()

        private val _duplicatesLoading = MutableStateFlow(false)
        val duplicatesLoading: StateFlow<Boolean> = _duplicatesLoading.asStateFlow()

        private val _m3uImportMessage = MutableStateFlow<String?>(null)
        val m3uImportMessage: StateFlow<String?> = _m3uImportMessage.asStateFlow()

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

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch {
                appPreferences.setThemeMode(mode)
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

        fun setBufferProfile(profile: BufferProfile) {
            viewModelScope.launch {
                appPreferences.setBufferProfile(profile)
            }
        }

        fun setNormalizeVolume(enabled: Boolean) {
            viewModelScope.launch {
                appPreferences.setNormalizeVolume(enabled)
            }
        }

        fun setNormalizePreampDb(db: Int) {
            viewModelScope.launch {
                appPreferences.setNormalizePreampDb(db)
            }
        }

        // ── Scrobbling ─────────────────────────────────────────────
        fun setScrobbleEnabled(enabled: Boolean) {
            viewModelScope.launch { appPreferences.setScrobbleEnabled(enabled) }
        }

        fun setScrobbleProvider(provider: String) {
            viewModelScope.launch { appPreferences.setScrobbleProvider(provider) }
        }

        fun setListenBrainzToken(token: String) {
            viewModelScope.launch { appPreferences.setListenBrainzToken(token) }
        }

        fun logoutLastfm() {
            viewModelScope.launch { appPreferences.clearLastfmSession() }
        }

        /** @param onResult true on a successful Last.fm session. */
        fun loginLastfm(
            apiKey: String,
            apiSecret: String,
            username: String,
            password: String,
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch {
                val ok = scrobbleManager.loginLastfm(apiKey, apiSecret, username, password)
                onResult(ok)
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

        fun playTrack(
            track: TrackEntity,
            queue: List<TrackEntity> = tracks.value,
        ) {
            val source = if (queue.any { it.id == track.id }) queue else listOf(track)
            val (list, index) = source.playbackQueue(track)
            playerConnection.playTracks(list, index)
        }

        fun playAll(
            tracks: List<TrackEntity>,
            startIndex: Int = 0,
        ) {
            val (list, index) = tracks.playbackQueue(tracks.getOrNull(startIndex))
            playerConnection.playTracks(list, index)
        }

        fun playNext(track: TrackEntity) {
            if (track.isStream) {
                playerConnection.playTracks(listOf(track), 0)
                return
            }
            playerConnection.playNext(track)
        }

        fun playNext(tracks: List<TrackEntity>) {
            playerConnection.playNext(tracks)
        }

        fun addToQueue(track: TrackEntity) {
            if (track.isStream) {
                playerConnection.playTracks(listOf(track), 0)
                return
            }
            playerConnection.addToQueue(track)
        }

        fun addToQueue(tracks: List<TrackEntity>) {
            playerConnection.addToQueue(tracks)
        }

        fun resumeLastSession(autoPlay: Boolean = true) {
            playerConnection.resumeLastSession(autoPlay)
        }

        fun playPlaylist(playlistId: Long) {
            viewModelScope.launch {
                val list = repository.getPlaylistTracksOnce(playlistId)
                if (list.isEmpty()) return@launch
                val (queue, index) = list.playbackQueue(list.first())
                if (queue.isNotEmpty()) playerConnection.playTracks(queue, index)
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

        // ── Video streams ──────────────────────────────────────────
        fun attachVideoSurface(view: androidx.media3.ui.PlayerView) {
            playerConnection.attachVideoSurface(view)
        }

        fun detachVideoSurface(view: androidx.media3.ui.PlayerView) {
            playerConnection.detachVideoSurface(view)
        }

        fun setStreamAudioOnly(audioOnly: Boolean) {
            playerConnection.setAudioOnly(audioOnly)
        }

        fun createPlaylist(
            name: String,
            onCreated: (Long) -> Unit = {},
        ) {
            viewModelScope.launch {
                val id = repository.createPlaylist(name)
                onCreated(id)
            }
        }

        fun renamePlaylist(
            id: Long,
            name: String,
        ) {
            viewModelScope.launch { repository.renamePlaylist(id, name) }
        }

        fun deletePlaylist(id: Long) {
            viewModelScope.launch {
                repository.deletePlaylist(id)
            }
        }

        fun addToPlaylist(
            playlistId: Long,
            trackId: Long,
        ) {
            viewModelScope.launch {
                repository.addToPlaylist(playlistId, trackId)
            }
        }

        fun addToPlaylist(
            playlistId: Long,
            trackIds: List<Long>,
        ) {
            if (trackIds.isEmpty()) return
            viewModelScope.launch {
                repository.addToPlaylist(playlistId, trackIds)
            }
        }

        fun removeFromPlaylist(
            playlistId: Long,
            trackId: Long,
        ) {
            viewModelScope.launch {
                repository.removeFromPlaylist(playlistId, trackId)
            }
        }

        fun moveTrack(
            playlistId: Long,
            trackId: Long,
            toPosition: Int,
        ) {
            viewModelScope.launch {
                repository.moveTrackInPlaylist(playlistId, trackId, toPosition)
            }
        }

        fun reorderPlaylist(
            playlistId: Long,
            fromIndex: Int,
            toIndex: Int,
        ) {
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

        fun hideTrack(trackId: Long) {
            viewModelScope.launch {
                repository.setHidden(trackId, true)
            }
        }

        fun hideTracks(trackIds: List<Long>) {
            if (trackIds.isEmpty()) return
            viewModelScope.launch {
                repository.setHiddenMany(trackIds, true)
            }
        }

        fun unhideTrack(trackId: Long) {
            viewModelScope.launch {
                repository.setHidden(trackId, false)
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
            repository
                .getPlaylistTracks(playlistId)
                .stateIn(viewModelScope, share, emptyList())

        fun playlist(playlistId: Long): StateFlow<PlaylistEntity?> =
            repository
                .getPlaylist(playlistId)
                .stateIn(viewModelScope, share, null)

        fun albumTracks(album: String): StateFlow<List<TrackEntity>> =
            repository
                .getTracksByAlbum(album)
                .stateIn(viewModelScope, share, emptyList())

        fun artistTracks(artist: String): StateFlow<List<TrackEntity>> =
            repository
                .getTracksByArtist(artist)
                .stateIn(viewModelScope, share, emptyList())

        fun folderTracks(folderPath: String): StateFlow<List<TrackEntity>> =
            repository
                .getTracksByFolder(folderPath)
                .stateIn(viewModelScope, share, emptyList())

        fun smartTracks(smart: io.karpilabs.simplemp3.data.local.SmartPlaylist): StateFlow<List<TrackEntity>> =
            repository
                .getSmartTracks(smart)
                .stateIn(viewModelScope, share, emptyList())

        fun genreTracks(genre: String): StateFlow<List<TrackEntity>> =
            repository
                .getTracksByGenre(genre)
                .stateIn(viewModelScope, share, emptyList())

        fun childFolders(folderPath: String): StateFlow<List<FolderBrowser.FolderEntry>> =
            repository
                .childFolders(folderPath)
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
                val folders =
                    _deviceFolders.value.ifEmpty {
                        repository.listDeviceFolderPaths().also { _deviceFolders.value = it }
                    }
                val tops =
                    folders
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
                val cleaned =
                    paths
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

        fun addSafTree(uri: String) {
            viewModelScope.launch {
                repository.addSafTreeUri(uri)
            }
        }

        fun removeSafTree(uri: String) {
            viewModelScope.launch {
                repository.removeSafTreeUri(uri)
            }
        }

        fun safTreeDisplayName(uri: String): String = repository.safTreeDisplayName(uri)

        fun refreshDuplicates() {
            viewModelScope.launch {
                _duplicatesLoading.value = true
                try {
                    _duplicateGroups.value = repository.findDuplicateGroups()
                } finally {
                    _duplicatesLoading.value = false
                }
            }
        }

        fun hideDuplicateExtras(group: DuplicateDetector.Group) {
            viewModelScope.launch {
                repository.hideDuplicateExtras(group)
                _duplicateGroups.value = repository.findDuplicateGroups()
            }
        }

        fun mergeDuplicateGroup(group: DuplicateDetector.Group) {
            viewModelScope.launch {
                repository.mergeDuplicateGroup(group)
                _duplicateGroups.value = repository.findDuplicateGroups()
            }
        }

        fun importM3u(
            text: String,
            defaultName: String,
        ) {
            viewModelScope.launch {
                val result = repository.importM3u(text, defaultName)
                _m3uImportMessage.value = formatM3uImport(result)
            }
        }

        fun consumeM3uImportMessage() {
            _m3uImportMessage.value = null
        }

        fun exportM3u(
            name: String,
            tracks: List<TrackEntity>,
        ): String = repository.exportM3u(name, tracks)

        private fun formatM3uImport(result: M3uPlaylist.MatchResult): String {
            val matched = result.matched.size
            val missed = result.unmatched.size
            return when {
                matched == 0 -> "No songs in your library matched that playlist."
                missed == 0 -> "Imported “${result.playlistName}” · $matched song${if (matched == 1) "" else "s"}"
                else ->
                    "Imported “${result.playlistName}” · $matched matched, $missed skipped (not in library)"
            }
        }
    }
