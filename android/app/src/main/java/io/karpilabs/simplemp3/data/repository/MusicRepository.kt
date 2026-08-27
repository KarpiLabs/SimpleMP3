package io.karpilabs.simplemp3.data.repository

import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.FolderBrowser
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.scanner.MediaStoreScanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository
    @Inject
    constructor(
        private val trackDao: TrackDao,
        private val playlistDao: PlaylistDao,
        private val scanner: MediaStoreScanner,
        private val appPreferences: AppPreferences,
    ) {
        private val scanMutex = Mutex()
        private val _isScanning = MutableStateFlow(false)
        val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

        val tracks: Flow<List<TrackEntity>> = trackDao.getAllTracks()
        val recentlyAdded: Flow<List<TrackEntity>> = trackDao.getRecentlyAdded(40)
        val albums: Flow<List<AlbumRow>> = trackDao.getAlbums()
        val artists: Flow<List<AlbumRow>> = trackDao.getArtists()
        val playlists: Flow<List<PlaylistWithMeta>> = playlistDao.getPlaylistsWithMeta()
        val trackCount: Flow<Int> = trackDao.observeTrackCount()

        val jellyfinTrackCount: Flow<Int> =
            trackDao.observeCountBySource(TrackEntity.SOURCE_JELLYFIN)

        val jellyfinTracks: Flow<List<TrackEntity>> =
            trackDao.getTracksBySource(TrackEntity.SOURCE_JELLYFIN)

        val youtubeTrackCount: Flow<Int> =
            trackDao.observeCountBySource(TrackEntity.SOURCE_YOUTUBE)

        val youtubeTracks: Flow<List<TrackEntity>> =
            trackDao.getTracksBySource(TrackEntity.SOURCE_YOUTUBE)

        val streamTracks: Flow<List<TrackEntity>> =
            trackDao.getTracksBySource(TrackEntity.SOURCE_STREAM)

        val folderPaths: Flow<List<String>> = trackDao.getDistinctFolderPaths()

        val hiddenTracks: Flow<List<TrackEntity>> = trackDao.getHiddenTracks()

        suspend fun setHidden(
            trackId: Long,
            hidden: Boolean,
        ) {
            trackDao.setHidden(trackId, hidden)
        }

        val libraryFolderRoots: Flow<Set<String>> = appPreferences.libraryFolderRootsFlow

        /** Direct track counts keyed by folderPath (for browser badges). */
        val folderTrackCounts: Flow<Map<String, Int>> =
            tracks.map { list ->
                list
                    .groupingBy { it.folderPath }
                    .eachCount()
                    .filterKeys { it.isNotBlank() }
            }

        fun childFolders(parentPath: String): Flow<List<FolderBrowser.FolderEntry>> =
            combine(folderPaths, folderTrackCounts) { paths, counts ->
                FolderBrowser.childFolders(paths, parentPath, counts)
            }

        fun getTracksByFolder(folderPath: String): Flow<List<TrackEntity>> = trackDao.getTracksByFolder(FolderBrowser.normalize(folderPath))

        /** Home "Continue listening" — tracks from Recently Played playlist. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val continueListening: Flow<List<TrackEntity>> =
            playlistDao.getAllPlaylists().flatMapLatest { lists ->
                val recentId =
                    lists
                        .firstOrNull {
                            it.systemType == PlaylistEntity.SYSTEM_RECENTLY_PLAYED
                        }?.id
                if (recentId == null) {
                    flowOf(emptyList())
                } else {
                    playlistDao.getTracksForPlaylist(recentId).map { it.take(20) }
                }
            }

        suspend fun ensureSystemPlaylists() {
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_FAVORITES) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Liked Songs",
                        description = "Your favorite tracks",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_FAVORITES,
                    ),
                )
            }
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_RECENTLY_PLAYED) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Recently Played",
                        description = "Jump back in",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_RECENTLY_PLAYED,
                    ),
                )
            }
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_JELLYFIN) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Jellyfin Offline",
                        description = "Synced from your Jellyfin server",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_JELLYFIN,
                    ),
                )
            }
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_YOUTUBE) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "YouTube Downloads",
                        description = "Imported from YouTube links",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_YOUTUBE,
                    ),
                )
            }
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_LAN) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "LAN Imports",
                        description = "Uploaded via Quick Connect",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_LAN,
                    ),
                )
            }
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_STREAMS) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Saved Streams",
                        description = "Live streams saved to a playlist",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_STREAMS,
                    ),
                )
            }
        }

        /**
         * @param force when false, skip MediaStore work if library already has tracks and
         * a scan ran recently — keeps cold start instant.
         */
        suspend fun scanLibrary(force: Boolean = true): Int =
            scanMutex.withLock {
                ensureSystemPlaylists()
                val existing = trackDao.getTrackCount()
                if (!force && existing > 0 && appPreferences.shouldSkipScan()) {
                    return existing
                }
                // Empty library always scans once; force=true always scans.
                if (!force && existing > 0) {
                    // Soft refresh path still allowed if skip window expired — fall through
                }

                _isScanning.value = true
                try {
                    val roots = appPreferences.getLibraryFolderRoots()
                    val scanned =
                        scanner
                            .scan(allowedRoots = roots)
                            .map { it.copy(source = TrackEntity.SOURCE_LOCAL) }
                    // Only touch local MediaStore tracks — never wipe Jellyfin offline downloads.
                    if (scanned.isNotEmpty()) {
                        // Chunk inserts for large libraries so Room/SQLite stays responsive
                        scanned.chunked(300).forEach { chunk ->
                            trackDao.insertTracks(chunk)
                        }
                    }
                    val keepLocal = scanned.map { it.id }.toSet()
                    val staleLocal =
                        trackDao
                            .getTrackIdsBySource(TrackEntity.SOURCE_LOCAL)
                            .filter { it !in keepLocal }
                    staleLocal.chunked(400).forEach { chunk ->
                        if (chunk.isNotEmpty()) trackDao.deleteTracksByIds(chunk)
                    }
                    appPreferences.setLastLibraryScanMs()
                    trackDao.getTrackCount()
                } finally {
                    _isScanning.value = false
                }
            }

        suspend fun listDeviceFolderPaths(): List<String> = scanner.listAllFolderPaths()

        suspend fun setLibraryFolderRoots(roots: Set<String>) {
            appPreferences.setLibraryFolderRoots(roots)
            // Force rescan so the library matches the new roots.
            scanLibrary(force = true)
        }

        suspend fun getLibraryFolderRoots(): Set<String> = appPreferences.getLibraryFolderRoots()

        fun search(query: String): Flow<List<TrackEntity>> = trackDao.searchTracks(query)

        suspend fun searchOnce(query: String): List<TrackEntity> = trackDao.searchTracksOnce(query)

        suspend fun getTrack(id: Long): TrackEntity? = trackDao.getTrackById(id)

        suspend fun getTracksByIdsOrdered(ids: List<Long>): List<TrackEntity> {
            if (ids.isEmpty()) return emptyList()
            val map = trackDao.getTracksByIds(ids).associateBy { it.id }
            return ids.mapNotNull { map[it] }
        }

        suspend fun getAllTracksOnce(): List<TrackEntity> = trackDao.getAllTracksOnce()

        suspend fun getAlbumsOnce(): List<AlbumRow> = trackDao.getAlbumsOnce()

        suspend fun getArtistsOnce(): List<AlbumRow> = trackDao.getArtistsOnce()

        suspend fun getTracksByAlbumOnce(album: String): List<TrackEntity> = trackDao.getTracksByAlbumOnce(album)

        suspend fun getTracksByArtistOnce(artist: String): List<TrackEntity> = trackDao.getTracksByArtistOnce(artist)

        fun getTracksByAlbum(album: String): Flow<List<TrackEntity>> = trackDao.getTracksByAlbum(album)

        fun getTracksByArtist(artist: String): Flow<List<TrackEntity>> = trackDao.getTracksByArtist(artist)

        suspend fun getTracksByFolderOnce(folderPath: String): List<TrackEntity> =
            trackDao.getTracksByFolderOnce(FolderBrowser.normalize(folderPath))

        // ── Playlists ──────────────────────────────────────────────

        fun getPlaylist(id: Long): Flow<PlaylistEntity?> = playlistDao.getPlaylist(id)

        fun getPlaylistTracks(playlistId: Long): Flow<List<TrackEntity>> = playlistDao.getTracksForPlaylist(playlistId)

        suspend fun getPlaylistTracksOnce(playlistId: Long): List<TrackEntity> = playlistDao.getTracksForPlaylistOnce(playlistId)

        suspend fun getPlaylistsOnce(): List<PlaylistWithMeta> = playlistDao.getPlaylistsWithMetaOnce()

        suspend fun createPlaylist(
            name: String,
            description: String = "",
        ): Long {
            val id =
                playlistDao.insertPlaylist(
                    PlaylistEntity(name = name.trim(), description = description.trim()),
                )
            return id
        }

        suspend fun renamePlaylist(
            id: Long,
            name: String,
        ) {
            val existing = playlistDao.getPlaylistOnce(id) ?: return
            if (existing.isSystem) return
            playlistDao.updatePlaylist(
                existing.copy(name = name.trim(), updatedAt = System.currentTimeMillis()),
            )
        }

        suspend fun deletePlaylist(id: Long) {
            playlistDao.deletePlaylistById(id)
        }

        suspend fun addToPlaylist(
            playlistId: Long,
            trackId: Long,
        ) {
            playlistDao.addTrackToEnd(playlistId, trackId)
        }

        suspend fun removeFromPlaylist(
            playlistId: Long,
            trackId: Long,
        ) {
            playlistDao.removeTrackFromPlaylist(playlistId, trackId)
            playlistDao.touchPlaylist(playlistId)
        }

        suspend fun moveTrackInPlaylist(
            playlistId: Long,
            trackId: Long,
            toPosition: Int,
        ) {
            playlistDao.moveTrack(playlistId, trackId, toPosition)
        }

        suspend fun toggleFavorite(trackId: Long): Boolean {
            ensureSystemPlaylists()
            val fav = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_FAVORITES) ?: return false
            return if (playlistDao.containsTrack(fav.id, trackId)) {
                playlistDao.removeTrackFromPlaylist(fav.id, trackId)
                playlistDao.touchPlaylist(fav.id)
                false
            } else {
                playlistDao.addTrackToEnd(fav.id, trackId)
                true
            }
        }

        suspend fun isFavorite(trackId: Long): Boolean {
            val fav = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_FAVORITES) ?: return false
            return playlistDao.containsTrack(fav.id, trackId)
        }

        fun observeIsFavorite(trackId: Long): Flow<Boolean> =
            kotlinx.coroutines.flow.flow {
                val favId = getFavoritesPlaylistId()
                if (favId == null) {
                    emit(false)
                } else {
                    playlistDao.observeContainsTrack(favId, trackId).collect { emit(it) }
                }
            }

        suspend fun recordPlay(trackId: Long) {
            ensureSystemPlaylists()
            val recent = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_RECENTLY_PLAYED) ?: return
            // Move to front: remove if present, insert at position 0 by reordering
            if (playlistDao.containsTrack(recent.id, trackId)) {
                playlistDao.removeTrackFromPlaylist(recent.id, trackId)
            }
            val refs = playlistDao.getCrossRefs(recent.id)
            // Shift existing down
            refs.forEach { ref ->
                playlistDao.insertPlaylistTrack(ref.copy(position = ref.position + 1))
            }
            playlistDao.insertPlaylistTrack(
                io.karpilabs.simplemp3.data.local.PlaylistTrackCrossRef(
                    playlistId = recent.id,
                    trackId = trackId,
                    position = 0,
                ),
            )
            // Cap at 50
            val all = playlistDao.getCrossRefs(recent.id)
            if (all.size > 50) {
                all.filter { it.position >= 50 }.forEach {
                    playlistDao.removeTrackFromPlaylist(recent.id, it.trackId)
                }
            }
            playlistDao.touchPlaylist(recent.id)
        }

        suspend fun getFavoritesPlaylistId(): Long? = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_FAVORITES)?.id

        suspend fun getRecentlyPlayedPlaylistId(): Long? = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_RECENTLY_PLAYED)?.id

        suspend fun getYoutubePlaylistId(): Long? = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_YOUTUBE)?.id

        suspend fun getLikedTracksOnce(): List<TrackEntity> {
            val id = getFavoritesPlaylistId() ?: return emptyList()
            return getPlaylistTracksOnce(id)
        }

        suspend fun getRecentlyPlayedTracksOnce(limit: Int = 40): List<TrackEntity> {
            val id = getRecentlyPlayedPlaylistId() ?: return emptyList()
            return getPlaylistTracksOnce(id).take(limit)
        }

        suspend fun getYoutubeTracksOnce(): List<TrackEntity> = trackDao.getTracksBySourceOnce(TrackEntity.SOURCE_YOUTUBE)

        /** Resume snapshot tracks in order, or recently played if no session. */
        suspend fun getContinueTracksOnce(): List<TrackEntity> {
            val snap = appPreferences.getResume()
            if (snap != null && snap.hasSession) {
                val ordered = getTracksByIdsOrdered(snap.trackIds)
                if (ordered.isNotEmpty()) return ordered
            }
            return getRecentlyPlayedTracksOnce(40)
        }
    }
