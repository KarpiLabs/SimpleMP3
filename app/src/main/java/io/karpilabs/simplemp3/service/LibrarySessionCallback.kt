package io.karpilabs.simplemp3.service

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Android Auto / media browser tree:
 *
 * Root → Continue · Liked · YouTube · Up Next · Playlists · Offline ·
 *        Albums · Artists · Songs · Recently Played
 *
 * - Single-track plays expand into a full queue
 * - Search is fully wired (notify + results)
 * - Shuffle play actions on folders
 * - Optional Drive Mode + auto-resume when Auto connects
 */
@OptIn(UnstableApi::class)
class LibrarySessionCallback(
    private val repository: MusicRepository,
    private val player: Player,
    private val appPreferences: AppPreferences,
    private val storageManager: LargeFileStorageManager
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Avoid restarting playback when Auto reconnects several controllers at once. */
    @Volatile
    private var lastAutoResumeAtMs: Long = 0L

    /** Last browsable folder the Auto client opened — used to expand single-track plays. */
    @Volatile
    private var lastBrowseParentId: String? = null

    /** Cached search hits so [onGetSearchResult] matches [onSearch]. */
    private val searchCache = ConcurrentHashMap<String, List<MediaItem>>()

    /**
     * Pending pause after a car controller leaves. Cancelled if Auto reconnects
     * within [CAR_DISCONNECT_PAUSE_DELAY_MS] (common during brief USB/wireless glitches).
     */
    private var carDisconnectPauseJob: Job? = null

    companion object {
        const val ACTION_TOGGLE_SHUFFLE = "io.karpilabs.simplemp3.TOGGLE_SHUFFLE"
        const val ACTION_CYCLE_REPEAT = "io.karpilabs.simplemp3.CYCLE_REPEAT"

        val CUSTOM_SHUFFLE = SessionCommand(ACTION_TOGGLE_SHUFFLE, android.os.Bundle.EMPTY)
        val CUSTOM_REPEAT = SessionCommand(ACTION_CYCLE_REPEAT, android.os.Bundle.EMPTY)

        /** Ignore repeated Auto controller connects within this window. */
        private const val AUTO_RESUME_COOLDOWN_MS = 45_000L

        /**
         * Wait before pausing on car disconnect so short reconnects (common with
         * wireless Auto / head-unit handshakes) do not interrupt a trip mid-song.
         */
        private const val CAR_DISCONNECT_PAUSE_DELAY_MS = 2_000L

        /** Packages that mean “the car is connected”. */
        private val CAR_PACKAGES = setOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.autosimulator",
            "com.google.android.car.templates.places",
            "com.android.car.media",
            "com.android.bluetooth" // some stacks browse via BT AVRCP media
        )
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        maybeEnableDriveModeForCar(controller)

        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            .buildUpon()
            .add(CUSTOM_SHUFFLE)
            .add(CUSTOM_REPEAT)
            .build()

        val playerCommands = Player.Commands.Builder()
            .addAllCommands()
            .build()

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    /**
     * When Android Auto / Automotive drops, stop audio so it does not keep
     * playing on the phone (or through a dead route). Debounced so brief
     * controller churn during reconnect does not pause mid-drive.
     */
    override fun onDisconnected(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ) {
        super.onDisconnected(session, controller)
        if (!isCarController(controller)) return

        carDisconnectPauseJob?.cancel()
        carDisconnectPauseJob = scope.launch {
            delay(CAR_DISCONNECT_PAUSE_DELAY_MS)
            if (hasConnectedCarController(session)) return@launch
            handleCarDisconnect()
        }
    }

    private fun isCarController(controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName.orEmpty()
        return pkg in CAR_PACKAGES ||
            pkg.contains("gearhead", ignoreCase = true) ||
            pkg.contains("android.car", ignoreCase = true) ||
            pkg.contains("projection", ignoreCase = true)
    }

    private fun hasConnectedCarController(session: MediaSession): Boolean =
        session.connectedControllers.any { isCarController(it) }

    private suspend fun handleCarDisconnect() {
        val shouldPause = withContext(Dispatchers.IO) {
            appPreferences.isPauseOnCarDisconnect()
        }
        if (shouldPause && (player.playWhenReady || player.isPlaying)) {
            player.pause()
        }
        // Clear Drive Mode if we auto-enabled it for the car session.
        val autoDrive = withContext(Dispatchers.IO) {
            appPreferences.isAutoDriveModeOnCar()
        }
        if (autoDrive) {
            withContext(Dispatchers.IO) { appPreferences.setDriveMode(false) }
        }
    }

    private fun maybeEnableDriveModeForCar(controller: MediaSession.ControllerInfo) {
        if (!isCarController(controller)) return
        // Car came back — do not pause from a prior disconnect race.
        carDisconnectPauseJob?.cancel()
        carDisconnectPauseJob = null
        scope.launch {
            val enableDrive = withContext(Dispatchers.IO) {
                appPreferences.isAutoDriveModeOnCar()
            }
            if (enableDrive) {
                withContext(Dispatchers.IO) { appPreferences.setDriveMode(true) }
            }
            val shouldResume = withContext(Dispatchers.IO) {
                appPreferences.isAutoResumeOnDrive()
            }
            if (shouldResume) {
                autoResumeForDriving(fromCarConnect = true)
            }
        }
    }

    /**
     * Restore last session and play when driving starts.
     * Skips if already playing, or if a car auto-resume ran recently (controller spam).
     */
    private suspend fun autoResumeForDriving(fromCarConnect: Boolean) {
        val now = System.currentTimeMillis()
        if (fromCarConnect && now - lastAutoResumeAtMs < AUTO_RESUME_COOLDOWN_MS) {
            return
        }
        // Never yank control if music is already going.
        if (player.isPlaying) return

        // Queue already loaded (e.g. paused mid-trip) — just continue.
        if (player.mediaItemCount > 0) {
            lastAutoResumeAtMs = now
            player.play()
            return
        }

        val snap = withContext(Dispatchers.IO) { appPreferences.getResume() } ?: return
        if (!snap.hasSession) return
        val tracks = withContext(Dispatchers.IO) {
            repository.getTracksByIdsOrdered(snap.trackIds)
        }
        if (tracks.isEmpty()) return
        val ready = withContext(Dispatchers.IO) {
            storageManager.ensurePlayable(tracks)
        }
        if (ready.isEmpty()) return

        lastAutoResumeAtMs = now
        val idx = snap.index.coerceIn(0, ready.lastIndex)
        val items = withContext(Dispatchers.Default) {
            MediaItemFactory.fromTracks(ready)
        }
        player.setMediaItems(items, idx, snap.positionMs.coerceAtLeast(0L))
        player.prepare()
        player.play()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: android.os.Bundle
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ACTION_TOGGLE_SHUFFLE -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            ACTION_CYCLE_REPEAT -> {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(
            LibraryResult.ofItem(MediaItemFactory.root(), params)
        )
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        lastBrowseParentId = parentId
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        ioScope.launch {
            try {
                val children = loadChildren(parentId)
                val from = (page * pageSize).coerceAtMost(children.size)
                val to = (from + pageSize).coerceAtMost(children.size)
                future.set(LibraryResult.ofItemList(children.subList(from, to), params))
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val future = SettableFuture.create<LibraryResult<MediaItem>>()
        ioScope.launch {
            val item = resolveItem(mediaId)
            future.set(
                if (item != null) LibraryResult.ofItem(item, null)
                else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            )
        }
        return future
    }

    override fun onSubscribe(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    /**
     * Android Auto search: run query, cache results, notify the browser of the count.
     * Without [MediaLibrarySession.notifySearchResultChanged], Auto never shows hits.
     */
    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val future = SettableFuture.create<LibraryResult<Void>>()
        ioScope.launch {
            try {
                val q = query.trim()
                val items = if (q.isBlank()) emptyList() else buildSearchResults(q)
                searchCache[q.lowercase()] = items
                // Also key by exact query for clients that re-request with same string
                searchCache[q] = items
                withContext(Dispatchers.Main) {
                    session.notifySearchResultChanged(browser, query, items.size, params)
                }
                future.set(LibraryResult.ofVoid())
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
        ioScope.launch {
            try {
                val q = query.trim()
                val items = searchCache[q]
                    ?: searchCache[q.lowercase()]
                    ?: if (q.isBlank()) emptyList() else buildSearchResults(q).also {
                        searchCache[q] = it
                        searchCache[q.lowercase()] = it
                    }
                val from = (page * pageSize).coerceAtMost(items.size)
                val to = (from + pageSize).coerceAtMost(items.size)
                future.set(LibraryResult.ofItemList(items.subList(from, to), params))
            } catch (_: Exception) {
                future.set(LibraryResult.ofError(SessionError.ERROR_UNKNOWN))
            }
        }
        return future
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        val future = SettableFuture.create<List<MediaItem>>()
        ioScope.launch {
            try {
                val queue = resolvePlaybackQueue(mediaItems)
                applyShuffleFlag(queue)
                future.set(queue.items)
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        ioScope.launch {
            try {
                val queue = resolvePlaybackQueue(
                    mediaItems,
                    preferredStartMediaId = mediaItems.getOrNull(startIndex)?.mediaId
                )
                applyShuffleFlag(queue)
                val safeIndex = if (queue.items.isEmpty()) 0
                else queue.startIndex.coerceIn(0, queue.items.lastIndex)

                // Continue / resume: restore saved position when playing CONTINUE
                var position = startPositionMs
                if (mediaItems.singleOrNull()?.mediaId == MediaIds.CONTINUE ||
                    mediaItems.singleOrNull()?.mediaId == MediaIds.shuffleOf(MediaIds.CONTINUE)
                ) {
                    val snap = appPreferences.getResume()
                    if (snap != null && snap.hasSession) {
                        position = snap.positionMs.coerceAtLeast(0L)
                    }
                }

                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        queue.items,
                        safeIndex,
                        position
                    )
                )
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        ioScope.launch {
            try {
                val snap = appPreferences.getResume()
                if (snap == null || !snap.hasSession) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                    return@launch
                }
                val tracks = repository.getTracksByIdsOrdered(snap.trackIds)
                val items = MediaItemFactory.fromTracks(tracks)
                if (items.isEmpty()) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                    return@launch
                }
                val idx = snap.index.coerceIn(0, items.lastIndex)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        items,
                        idx,
                        snap.positionMs.coerceAtLeast(0L)
                    )
                )
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private suspend fun applyShuffleFlag(queue: ResolvedQueue) {
        if (!queue.shuffle) return
        withContext(Dispatchers.Main) {
            player.shuffleModeEnabled = true
        }
    }

    /**
     * Resolve requested media into a full playable queue.
     * Single-track requests (typical from Android Auto) expand to the browsed
     * parent list / album / full library so the next song keeps playing.
     */
    private suspend fun resolvePlaybackQueue(
        mediaItems: List<MediaItem>,
        preferredStartMediaId: String? = null
    ): ResolvedQueue {
        if (mediaItems.isEmpty()) return ResolvedQueue(emptyList(), 0)

        if (mediaItems.size > 1) {
            val resolved = mediaItems.flatMap { item ->
                resolvePlayable(item.mediaId)
                    ?: listOfNotNull(item.takeIf { it.localConfiguration != null })
            }.distinctBy { it.mediaId }
            val startId = preferredStartMediaId ?: mediaItems.first().mediaId
            val start = resolved.indexOfFirst { it.mediaId == startId }.coerceAtLeast(0)
            return ResolvedQueue(resolved, start)
        }

        val only = mediaItems.first()
        val mediaId = only.mediaId

        // Shuffle play action
        if (MediaIds.isShuffle(mediaId)) {
            val target = MediaIds.unwrapShuffle(mediaId) ?: return ResolvedQueue(emptyList(), 0)
            val list = resolvePlayable(target).orEmpty().shuffled()
            return ResolvedQueue(list, 0, shuffle = true)
        }

        // Folder / collection play
        if (!mediaId.startsWith(MediaIds.TRACK_PREFIX)) {
            val list = resolvePlayable(mediaId)
                ?: listOfNotNull(only.takeIf { it.localConfiguration != null })
            val start = if (mediaId == MediaIds.CONTINUE) {
                val snap = appPreferences.getResume()
                snap?.index?.coerceIn(0, (list.size - 1).coerceAtLeast(0)) ?: 0
            } else 0
            return ResolvedQueue(list, start)
        }

        // Single track — expand into a continuous queue.
        val expanded = expandSingleTrack(mediaId)
        val start = expanded.indexOfFirst { it.mediaId == mediaId }.coerceAtLeast(0)
        return ResolvedQueue(expanded, start)
    }

    private suspend fun expandSingleTrack(trackMediaId: String): List<MediaItem> {
        val trackId = MediaIds.parseTrackId(trackMediaId) ?: return emptyList()
        val track = repository.getTrack(trackId) ?: return emptyList()
        val single = MediaItemFactory.fromTracks(
            storageManager.ensurePlayable(listOf(track))
        )

        val parent = lastBrowseParentId
        if (parent != null) {
            val siblings = playableTracksFromParent(parent)
            if (siblings.size > 1 && siblings.any { it.mediaId == trackMediaId }) {
                return siblings
            }
        }

        // Prefer the search result list as the queue when the track came from search
        run {
            val fromSearch = searchCache.values.firstOrNull { list ->
                list.any { it.mediaId == trackMediaId }
            }?.filter { it.mediaId.startsWith(MediaIds.TRACK_PREFIX) }
            if (fromSearch != null && fromSearch.size > 1) {
                // Re-resolve via track ids so cold files thaw
                val ids = fromSearch.mapNotNull { MediaIds.parseTrackId(it.mediaId) }
                val tracks = repository.getTracksByIdsOrdered(ids)
                return MediaItemFactory.fromTracks(storageManager.ensurePlayable(tracks))
            }
        }

        if (track.album.isNotBlank() &&
            !track.album.equals("Unknown Album", ignoreCase = true)
        ) {
            val albumTracks = repository.getTracksByAlbumOnce(track.album)
            if (albumTracks.size > 1 && albumTracks.any { it.id == track.id }) {
                return MediaItemFactory.fromTracks(storageManager.ensurePlayable(albumTracks))
            }
        }

        if (track.artist.isNotBlank() &&
            !track.artist.equals("Unknown Artist", ignoreCase = true)
        ) {
            val artistTracks = repository.getTracksByArtistOnce(track.artist)
            if (artistTracks.size > 1 && artistTracks.any { it.id == track.id }) {
                return MediaItemFactory.fromTracks(storageManager.ensurePlayable(artistTracks))
            }
        }

        val allTracks = repository.getAllTracksOnce()
        if (allTracks.size > 1 && allTracks.any { it.id == track.id }) {
            // Don't thaw the entire library — only the selected track for the single case;
            // for full-library queue, thaw in batches is expensive; resolve hot URIs as-is
            // and thaw individually when needed via ensurePlayable on the full list only
            // if it's reasonably small.
            val ready = if (allTracks.size <= 200) {
                storageManager.ensurePlayable(allTracks)
            } else {
                allTracks.map { t ->
                    if (t.id == track.id || t.isCold) storageManager.ensurePlayable(t) else t
                }
            }
            return MediaItemFactory.fromTracks(ready)
        }

        return single
    }

    private suspend fun playableTracksFromParent(parentId: String): List<MediaItem> {
        val tracks = when {
            parentId == MediaIds.SONGS -> repository.getAllTracksOnce()

            parentId == MediaIds.RECENT || parentId == MediaIds.CONTINUE ->
                repository.getContinueTracksOnce()

            parentId == MediaIds.LIKED -> repository.getLikedTracksOnce()

            parentId == MediaIds.YOUTUBE -> repository.getYoutubeTracksOnce()

            parentId == MediaIds.QUEUE -> return currentQueueItems()

            parentId == MediaIds.OFFLINE ->
                repository.getAllTracksOnce().filter { it.isJellyfin || it.isYoutube }

            parentId.startsWith(MediaIds.PLAYLIST_PREFIX) -> {
                val playlistId = MediaIds.parsePlaylistId(parentId) ?: return emptyList()
                repository.getPlaylistTracksOnce(playlistId)
            }

            parentId.startsWith(MediaIds.ALBUM_PREFIX) -> {
                val album = MediaIds.parseAlbum(parentId) ?: return emptyList()
                repository.getTracksByAlbumOnce(album)
            }

            parentId.startsWith(MediaIds.ARTIST_PREFIX) -> {
                val artist = MediaIds.parseArtist(parentId) ?: return emptyList()
                repository.getTracksByArtistOnce(artist)
            }

            else -> emptyList()
        }
        return MediaItemFactory.fromTracks(storageManager.ensurePlayable(tracks))
    }

    private suspend fun currentQueueItems(): List<MediaItem> = withContext(Dispatchers.Main) {
        val count = player.mediaItemCount
        if (count == 0) return@withContext emptyList()
        (0 until count).map { player.getMediaItemAt(it) }
    }

    private suspend fun loadChildren(parentId: String): List<MediaItem> {
        return when {
            parentId == MediaIds.ROOT -> buildRootChildren()

            parentId == MediaIds.PLAYLISTS ->
                visiblePlaylists().map { MediaItemFactory.fromPlaylist(it) }

            parentId == MediaIds.ALBUMS ->
                repository.getAlbumsOnce().map { MediaItemFactory.fromAlbum(it) }

            parentId == MediaIds.ARTISTS ->
                repository.getArtistsOnce().map { MediaItemFactory.fromArtist(it) }

            parentId == MediaIds.SONGS -> {
                val tracks = MediaItemFactory.fromTracks(repository.getAllTracksOnce())
                if (tracks.size > 1) {
                    listOf(MediaItemFactory.shufflePlayAction(MediaIds.SONGS)) + tracks
                } else tracks
            }

            parentId == MediaIds.RECENT -> {
                val id = repository.getRecentlyPlayedPlaylistId()
                val tracks = if (id != null) {
                    MediaItemFactory.fromTracks(repository.getPlaylistTracksOnce(id))
                } else emptyList()
                withShuffleHeader(MediaIds.RECENT, tracks)
            }

            parentId == MediaIds.CONTINUE -> {
                val tracks = MediaItemFactory.fromTracks(repository.getContinueTracksOnce())
                withShuffleHeader(MediaIds.CONTINUE, tracks)
            }

            parentId == MediaIds.LIKED -> {
                val tracks = MediaItemFactory.fromTracks(repository.getLikedTracksOnce())
                withShuffleHeader(MediaIds.LIKED, tracks)
            }

            parentId == MediaIds.YOUTUBE -> {
                val tracks = MediaItemFactory.fromTracks(repository.getYoutubeTracksOnce())
                withShuffleHeader(MediaIds.YOUTUBE, tracks)
            }

            parentId == MediaIds.QUEUE -> {
                val items = currentQueueItems()
                if (items.isEmpty()) {
                    listOf(
                        MediaItemFactory.category(
                            mediaId = "queue_empty",
                            title = "Nothing in queue",
                            subtitle = "Play something to fill Up Next",
                            isPlayable = false
                        )
                    )
                } else items
            }

            parentId == MediaIds.OFFLINE -> {
                val tracks = MediaItemFactory.fromTracks(
                    repository.getAllTracksOnce().filter { it.isJellyfin || it.isYoutube }
                )
                withShuffleHeader(MediaIds.OFFLINE, tracks)
            }

            parentId.startsWith(MediaIds.PLAYLIST_PREFIX) -> {
                val playlistId = MediaIds.parsePlaylistId(parentId) ?: return emptyList()
                val tracks = MediaItemFactory.fromTracks(repository.getPlaylistTracksOnce(playlistId))
                withShuffleHeader(parentId, tracks)
            }

            parentId.startsWith(MediaIds.ALBUM_PREFIX) -> {
                val album = MediaIds.parseAlbum(parentId) ?: return emptyList()
                val tracks = MediaItemFactory.fromTracks(repository.getTracksByAlbumOnce(album))
                withShuffleHeader(parentId, tracks)
            }

            parentId.startsWith(MediaIds.ARTIST_PREFIX) -> {
                val artist = MediaIds.parseArtist(parentId) ?: return emptyList()
                val tracks = MediaItemFactory.fromTracks(repository.getTracksByArtistOnce(artist))
                withShuffleHeader(parentId, tracks)
            }

            else -> emptyList()
        }
    }

    private suspend fun buildRootChildren(): List<MediaItem> {
        val continueTracks = repository.getContinueTracksOnce()
        val liked = repository.getLikedTracksOnce()
        val youtube = repository.getYoutubeTracksOnce()
        val queueCount = withContext(Dispatchers.Main) { player.mediaItemCount }

        return buildList {
            add(
                MediaItemFactory.category(
                    mediaId = MediaIds.CONTINUE,
                    title = "Continue",
                    subtitle = when {
                        continueTracks.isEmpty() -> "No recent session"
                        else -> "Resume · ${continueTracks.size} in queue"
                    },
                    isPlayable = continueTracks.isNotEmpty(),
                    artworkUri = continueTracks.firstOrNull()?.artworkUri
                )
            )
            add(
                MediaItemFactory.category(
                    mediaId = MediaIds.LIKED,
                    title = "Liked Songs",
                    subtitle = when (liked.size) {
                        0 -> "Heart tracks on your phone"
                        1 -> "1 song"
                        else -> "${liked.size} songs"
                    },
                    isPlayable = liked.isNotEmpty(),
                    artworkUri = liked.firstOrNull()?.artworkUri
                )
            )
            add(
                MediaItemFactory.category(
                    mediaId = MediaIds.YOUTUBE,
                    title = "YouTube Downloads",
                    subtitle = when (youtube.size) {
                        0 -> "Import links on your phone"
                        1 -> "1 track"
                        else -> "${youtube.size} tracks"
                    },
                    isPlayable = youtube.isNotEmpty(),
                    artworkUri = youtube.firstOrNull()?.artworkUri
                )
            )
            add(
                MediaItemFactory.category(
                    mediaId = MediaIds.QUEUE,
                    title = "Up Next",
                    subtitle = when (queueCount) {
                        0 -> "Queue is empty"
                        1 -> "1 in queue"
                        else -> "$queueCount in queue"
                    },
                    isPlayable = queueCount > 0
                )
            )
            add(MediaItemFactory.category(MediaIds.PLAYLISTS, "Playlists", "Your collections"))
            add(
                MediaItemFactory.category(
                    MediaIds.OFFLINE,
                    "Offline",
                    if (appPreferences.isJellyfinEnabled()) {
                        "Jellyfin & YouTube downloads"
                    } else {
                        "YouTube & offline downloads"
                    }
                )
            )
            add(MediaItemFactory.category(MediaIds.ALBUMS, "Albums", "Browse by album"))
            add(MediaItemFactory.category(MediaIds.ARTISTS, "Artists", "Browse by artist"))
            add(MediaItemFactory.category(MediaIds.SONGS, "Songs", "All tracks", isPlayable = true))
            add(
                MediaItemFactory.category(
                    MediaIds.RECENT,
                    "Recently Played",
                    "Jump back in",
                    isPlayable = true
                )
            )
        }
    }

    private fun withShuffleHeader(targetId: String, tracks: List<MediaItem>): List<MediaItem> {
        if (tracks.size <= 1) return tracks
        return listOf(MediaItemFactory.shufflePlayAction(targetId)) + tracks
    }

    /** Hide Jellyfin system playlist in Auto when integration is disabled. */
    private suspend fun visiblePlaylists() =
        repository.getPlaylistsOnce().filter { pl ->
            appPreferences.isJellyfinEnabled() ||
                pl.systemType != PlaylistEntity.SYSTEM_JELLYFIN
        }

    private suspend fun buildSearchResults(query: String): List<MediaItem> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()

        val tracks = repository.searchOnce(q)
        val trackItems = MediaItemFactory.fromTracks(tracks)

        val albumItems = repository.getAlbumsOnce()
            .filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.subtitle.contains(q, ignoreCase = true)
            }
            .take(10)
            .map { MediaItemFactory.fromAlbum(it) }

        val artistItems = repository.getArtistsOnce()
            .filter { it.name.contains(q, ignoreCase = true) }
            .take(10)
            .map { MediaItemFactory.fromArtist(it) }

        val playlistItems = visiblePlaylists()
            .filter { it.name.contains(q, ignoreCase = true) }
            .take(10)
            .map { MediaItemFactory.fromPlaylist(it) }

        // Folders first (albums/artists/playlists), then tracks — better for voice/Auto.
        return (playlistItems + albumItems + artistItems + trackItems).distinctBy { it.mediaId }
    }

    private suspend fun resolveItem(mediaId: String): MediaItem? {
        if (MediaIds.isShuffle(mediaId)) {
            val target = MediaIds.unwrapShuffle(mediaId) ?: return null
            return MediaItemFactory.shufflePlayAction(target)
        }
        return when {
            mediaId == MediaIds.ROOT -> MediaItemFactory.root()
            mediaId == MediaIds.PLAYLISTS -> MediaItemFactory.category(MediaIds.PLAYLISTS, "Playlists")
            mediaId == MediaIds.ALBUMS -> MediaItemFactory.category(MediaIds.ALBUMS, "Albums")
            mediaId == MediaIds.ARTISTS -> MediaItemFactory.category(MediaIds.ARTISTS, "Artists")
            mediaId == MediaIds.SONGS -> MediaItemFactory.category(MediaIds.SONGS, "Songs", isPlayable = true)
            mediaId == MediaIds.RECENT -> MediaItemFactory.category(MediaIds.RECENT, "Recently Played", isPlayable = true)
            mediaId == MediaIds.OFFLINE -> MediaItemFactory.category(MediaIds.OFFLINE, "Offline")
            mediaId == MediaIds.CONTINUE -> MediaItemFactory.category(
                MediaIds.CONTINUE, "Continue", isPlayable = true
            )
            mediaId == MediaIds.LIKED -> MediaItemFactory.category(
                MediaIds.LIKED, "Liked Songs", isPlayable = true
            )
            mediaId == MediaIds.YOUTUBE -> MediaItemFactory.category(
                MediaIds.YOUTUBE, "YouTube Downloads", isPlayable = true
            )
            mediaId == MediaIds.QUEUE -> MediaItemFactory.category(
                MediaIds.QUEUE, "Up Next", isPlayable = true
            )
            mediaId.startsWith(MediaIds.TRACK_PREFIX) -> {
                val id = MediaIds.parseTrackId(mediaId) ?: return null
                repository.getTrack(id)?.let { MediaItemFactory.fromTrack(it) }
            }
            mediaId.startsWith(MediaIds.PLAYLIST_PREFIX) -> {
                val id = MediaIds.parsePlaylistId(mediaId) ?: return null
                repository.getPlaylistsOnce().firstOrNull { it.id == id }
                    ?.let { MediaItemFactory.fromPlaylist(it) }
            }
            mediaId.startsWith(MediaIds.ALBUM_PREFIX) -> {
                val name = MediaIds.parseAlbum(mediaId) ?: return null
                repository.getAlbumsOnce().firstOrNull { it.name == name }
                    ?.let { MediaItemFactory.fromAlbum(it) }
            }
            mediaId.startsWith(MediaIds.ARTIST_PREFIX) -> {
                val name = MediaIds.parseArtist(mediaId) ?: return null
                repository.getArtistsOnce().firstOrNull { it.name == name }
                    ?.let { MediaItemFactory.fromArtist(it) }
            }
            else -> null
        }
    }

    private suspend fun resolvePlayable(mediaId: String): List<MediaItem>? {
        if (MediaIds.isShuffle(mediaId)) {
            val target = MediaIds.unwrapShuffle(mediaId) ?: return null
            return resolvePlayable(target)?.shuffled()
        }
        val tracks = when {
            mediaId.startsWith(MediaIds.TRACK_PREFIX) -> {
                val id = MediaIds.parseTrackId(mediaId) ?: return null
                repository.getTrack(id)?.let { listOf(it) }
            }
            mediaId.startsWith(MediaIds.PLAYLIST_PREFIX) -> {
                val id = MediaIds.parsePlaylistId(mediaId) ?: return null
                repository.getPlaylistTracksOnce(id)
            }
            mediaId.startsWith(MediaIds.ALBUM_PREFIX) -> {
                val album = MediaIds.parseAlbum(mediaId) ?: return null
                repository.getTracksByAlbumOnce(album)
            }
            mediaId.startsWith(MediaIds.ARTIST_PREFIX) -> {
                val artist = MediaIds.parseArtist(mediaId) ?: return null
                repository.getTracksByArtistOnce(artist)
            }
            mediaId == MediaIds.SONGS -> repository.getAllTracksOnce()
            mediaId == MediaIds.RECENT -> {
                val id = repository.getRecentlyPlayedPlaylistId() ?: return emptyList()
                repository.getPlaylistTracksOnce(id)
            }
            mediaId == MediaIds.CONTINUE -> repository.getContinueTracksOnce()
            mediaId == MediaIds.LIKED -> repository.getLikedTracksOnce()
            mediaId == MediaIds.YOUTUBE -> repository.getYoutubeTracksOnce()
            mediaId == MediaIds.QUEUE -> return currentQueueItems()
            mediaId == MediaIds.OFFLINE -> {
                repository.getAllTracksOnce().filter { it.isJellyfin || it.isYoutube }
            }
            mediaId == MediaIds.PLAYLISTS -> {
                val favId = repository.getFavoritesPlaylistId() ?: return emptyList()
                repository.getPlaylistTracksOnce(favId)
            }
            else -> null
        } ?: return null

        // Thaw cold archives so Auto/ExoPlayer get real file URIs
        val ready = storageManager.ensurePlayable(tracks)
        return MediaItemFactory.fromTracks(ready)
    }

    fun recordPlayForCurrent() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val trackId = MediaIds.parseTrackId(mediaId) ?: return
        scope.launch(Dispatchers.IO) {
            repository.recordPlay(trackId)
        }
    }

    private data class ResolvedQueue(
        val items: List<MediaItem>,
        val startIndex: Int,
        val shuffle: Boolean = false
    )
}
