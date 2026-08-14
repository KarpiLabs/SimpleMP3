package io.karpilabs.simplemp3.data.jellyfin

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.jellyfinItemIdToTrackId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinSyncManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val client: JellyfinClient,
        private val discovery: JellyfinDiscovery,
        private val preferences: JellyfinPreferences,
        private val trackDao: TrackDao,
        private val playlistDao: PlaylistDao,
    ) {
        private val mutex = Mutex()
        private val _progress = MutableStateFlow(SyncProgress())
        val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

        val sessionFlow = preferences.sessionFlow

        private fun audioDir(): File = File(context.filesDir, "offline/jellyfin/audio").also { it.mkdirs() }

        private fun artDir(): File = File(context.filesDir, "offline/jellyfin/art").also { it.mkdirs() }

        suspend fun login(
            serverUrl: String,
            username: String,
            password: String,
        ): Result<JellyfinSession> {
            val deviceId = preferences.getOrCreateDeviceId()
            return client.authenticate(serverUrl, username, password, deviceId).onSuccess {
                preferences.saveSession(it)
            }
        }

        /** Scan LAN for Jellyfin servers (UDP 7359 + light HTTP probes). */
        suspend fun discoverServers(): Result<List<DiscoveredJellyfinServer>> =
            runCatching {
                discovery.discover()
            }

        suspend fun logout() {
            preferences.clearSession()
        }

        suspend fun getSession(): JellyfinSession? = preferences.getSession()

        suspend fun fetchRemoteAlbums(): Result<List<JellyfinItem>> {
            val session =
                preferences.getSession()
                    ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))
            return client.getAlbums(session).map { it.items }
        }

        suspend fun fetchRemoteTracks(
            search: String? = null,
            limit: Int = 200,
        ): Result<List<JellyfinItem>> {
            val session =
                preferences.getSession()
                    ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))
            return client
                .getAudioItems(session, startIndex = 0, limit = limit, searchTerm = search)
                .map { it.items }
        }

        suspend fun fetchAlbumTracks(albumId: String): Result<List<JellyfinItem>> {
            val session =
                preferences.getSession()
                    ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))
            return client.getAlbumTracks(session, albumId)
        }

        /**
         * Download selected remote audio items for offline playback.
         * Adds them to the library and a "Jellyfin Offline" playlist.
         */
        suspend fun syncItems(items: List<JellyfinItem>): Result<Int> =
            mutex.withLock {
                if (items.isEmpty()) return Result.success(0)
                val session =
                    preferences.getSession()
                        ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))

                _progress.value =
                    SyncProgress(
                        phase = "Preparing",
                        current = 0,
                        total = items.size,
                        isActive = true,
                    )

                var successCount = 0
                try {
                    ensureJellyfinPlaylist()
                    val playlistId = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_JELLYFIN)?.id

                    items.forEachIndexed { index, item ->
                        _progress.value =
                            SyncProgress(
                                phase = "Downloading",
                                current = index,
                                total = items.size,
                                currentTitle = item.title,
                                isActive = true,
                            )
                        val ok = downloadOne(session, item)
                        if (ok != null) {
                            successCount++
                            if (playlistId != null) {
                                playlistDao.addTrackToEnd(playlistId, ok.id)
                            }
                        }
                    }

                    _progress.value =
                        SyncProgress(
                            phase = "Done",
                            current = items.size,
                            total = items.size,
                            isActive = false,
                            lastResult = "Synced $successCount of ${items.size} tracks offline",
                        )
                    Result.success(successCount)
                } catch (e: Exception) {
                    _progress.value =
                        SyncProgress(
                            phase = "Error",
                            isActive = false,
                            error = e.message ?: "Sync failed",
                            lastResult = e.message,
                        )
                    Result.failure(e)
                }
            }

        suspend fun syncAllMusic(maxTracks: Int = 500): Result<Int> {
            val session =
                preferences.getSession()
                    ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))

            _progress.value = SyncProgress(phase = "Fetching library", isActive = true)
            val all = mutableListOf<JellyfinItem>()
            var start = 0
            val pageSize = 100
            while (all.size < maxTracks) {
                val page =
                    client
                        .getAudioItems(session, startIndex = start, limit = pageSize)
                        .getOrElse {
                            _progress.value =
                                SyncProgress(
                                    phase = "Error",
                                    isActive = false,
                                    error = it.message,
                                )
                            return Result.failure(it)
                        }
                if (page.items.isEmpty()) break
                all += page.items
                start += page.items.size
                if (start >= page.totalRecordCount) break
            }
            return syncItems(all.take(maxTracks))
        }

        suspend fun syncAlbum(albumId: String): Result<Int> {
            val tracks = fetchAlbumTracks(albumId).getOrElse { return Result.failure(it) }
            return syncItems(tracks)
        }

        suspend fun fetchRemotePlaylists(): Result<List<JellyfinItem>> {
            val session =
                preferences.getSession()
                    ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))
            return client.getPlaylists(session).map { it.items }
        }

        suspend fun fetchPlaylistTracks(playlistId: String): Result<List<JellyfinItem>> {
            val session =
                preferences.getSession()
                    ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))
            return client.getPlaylistTracks(session, playlistId)
        }

        suspend fun resolveItemsByIds(ids: List<String>): List<JellyfinItem> {
            val session = preferences.getSession() ?: return emptyList()
            return ids.mapNotNull { id ->
                client.getItem(session, id).getOrNull()
            }
        }

        /**
         * Import a Jellyfin playlist: download tracks offline and create a matching local playlist.
         */
        suspend fun importPlaylist(remote: JellyfinItem): Result<Long> =
            mutex.withLock {
                val session =
                    preferences.getSession()
                        ?: return Result.failure(IllegalStateException("Not connected to Jellyfin"))
                val items = client.getPlaylistTracks(session, remote.id).getOrElse { return Result.failure(it) }
                if (items.isEmpty()) {
                    return Result.failure(IllegalStateException("Playlist is empty on the server"))
                }

                _progress.value =
                    SyncProgress(
                        phase = "Importing playlist",
                        current = 0,
                        total = items.size,
                        currentTitle = remote.name.orEmpty(),
                        isActive = true,
                    )

                return try {
                    val localId =
                        playlistDao.insertPlaylist(
                            PlaylistEntity(
                                name = remote.name?.takeIf { it.isNotBlank() } ?: "Jellyfin Playlist",
                                description = "Imported from Jellyfin",
                                isSystem = false,
                            ),
                        )
                    var ok = 0
                    items.forEachIndexed { index, item ->
                        _progress.value =
                            SyncProgress(
                                phase = "Importing ${remote.name.orEmpty()}",
                                current = index,
                                total = items.size,
                                currentTitle = item.title,
                                isActive = true,
                            )
                        val track = downloadOne(session, item)
                        if (track != null) {
                            playlistDao.addTrackToEnd(localId, track.id)
                            ok++
                            // also keep in Jellyfin Offline collection
                            ensureJellyfinPlaylist()
                            playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_JELLYFIN)?.id?.let { jfPl ->
                                playlistDao.addTrackToEnd(jfPl, track.id)
                            }
                        }
                    }
                    _progress.value =
                        SyncProgress(
                            phase = "Done",
                            current = items.size,
                            total = items.size,
                            isActive = false,
                            lastResult = "Imported \"$ok\" tracks into ${remote.name}",
                        )
                    Result.success(localId)
                } catch (e: Exception) {
                    _progress.value =
                        SyncProgress(
                            phase = "Error",
                            isActive = false,
                            error = e.message,
                            lastResult = e.message,
                        )
                    Result.failure(e)
                }
            }

        suspend fun removeOfflineTrack(trackId: Long) {
            val track = trackDao.getTrackById(trackId) ?: return
            if (track.source != TrackEntity.SOURCE_JELLYFIN) return
            track.jellyfinId?.let { jfId ->
                File(audioDir(), fileNameFor(jfId, track.uri)).delete()
                File(artDir(), "$jfId.jpg").delete()
            }
            runCatching {
                val path = Uri.parse(track.uri).path
                if (path != null) File(path).delete()
            }
            trackDao.deleteTrackById(trackId)
        }

        suspend fun clearAllOffline(): Int {
            val tracks = trackDao.getTracksBySourceOnce(TrackEntity.SOURCE_JELLYFIN)
            tracks.forEach { removeOfflineTrack(it.id) }
            audioDir().listFiles()?.forEach { it.delete() }
            artDir().listFiles()?.forEach { it.delete() }
            return tracks.size
        }

        fun isDownloaded(itemId: String): Boolean {
            val id = jellyfinItemIdToTrackId(itemId)
            // cheap filesystem check
            return audioDir().listFiles()?.any {
                it.nameWithoutExtension ==
                    itemId.replace(
                        "-",
                        "",
                    ) ||
                    it.name.startsWith(itemId)
            } == true ||
                File(audioDir(), "$itemId.mp3").exists() ||
                File(audioDir(), "${itemId.replace("-", "")}.mp3").exists()
        }

        private suspend fun downloadOne(
            session: JellyfinSession,
            item: JellyfinItem,
        ): TrackEntity? {
            val existing = trackDao.getByJellyfinId(item.id)
            if (existing != null && existing.isOffline && fileExists(existing.uri)) {
                return existing
            }

            val container =
                item.container
                    ?.lowercase()
                    ?.substringBefore(',')
                    ?.trim()
                    .orEmpty()
            val ext =
                when {
                    container.contains("flac") -> "flac"
                    container.contains("m4a") || container.contains("mp4") || container.contains("aac") -> "m4a"
                    container.contains("ogg") || container.contains("opus") -> "ogg"
                    container.contains("wav") -> "wav"
                    else -> "mp3"
                }
            val audioFile = File(audioDir(), "${item.id}.$ext")
            val download =
                client.downloadToFile(session, item.id, audioFile) { read, total ->
                    // keep UI responsive with soft progress on current item
                    val base = _progress.value
                    if (total != null && total > 0) {
                        _progress.value =
                            base.copy(
                                currentTitle = "${item.title} (${(read * 100 / total).toInt()}%)",
                            )
                    }
                }
            if (download.isFailure) return null

            val artFile = File(artDir(), "${item.id}.jpg")
            val art = client.downloadImage(session, item, artFile).getOrNull()
            val artworkUri =
                art?.let { Uri.fromFile(it).toString() }
                    ?: client.imageUrl(session, item)

            val track =
                TrackEntity(
                    id = jellyfinItemIdToTrackId(item.id),
                    title = item.title,
                    artist = item.artistName,
                    album = item.albumName,
                    albumId =
                        item.albumId
                            ?.hashCode()
                            ?.toLong()
                            ?.and(0x7fffffff) ?: 0L,
                    uri = Uri.fromFile(audioFile).toString(),
                    duration = item.durationMs,
                    artworkUri = artworkUri,
                    dateAdded = System.currentTimeMillis(),
                    year = item.productionYear ?: 0,
                    trackNumber = item.indexNumber ?: 0,
                    size = audioFile.length(),
                    source = TrackEntity.SOURCE_JELLYFIN,
                    jellyfinId = item.id,
                    isOffline = true,
                )
            trackDao.insertTrack(track)
            return track
        }

        private fun fileExists(uri: String): Boolean {
            return runCatching {
                val path = Uri.parse(uri).path ?: return false
                File(path).exists()
            }.getOrDefault(false)
        }

        private fun fileNameFor(
            jellyfinId: String,
            uri: String,
        ): String = Uri.parse(uri).lastPathSegment ?: "$jellyfinId.mp3"

        private suspend fun ensureJellyfinPlaylist() {
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
        }
    }
