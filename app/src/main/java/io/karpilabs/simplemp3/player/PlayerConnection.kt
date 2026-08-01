package io.karpilabs.simplemp3.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.prefs.ResumeSnapshot
import io.karpilabs.simplemp3.data.repository.MusicRepository
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import io.karpilabs.simplemp3.service.MediaIds
import io.karpilabs.simplemp3.service.MediaItemFactory
import io.karpilabs.simplemp3.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class QueueItemUi(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String? = null
)

data class PlayerUiState(
    val isConnected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentMediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueSize: Int = 0,
    val currentIndex: Int = 0,
    val queue: List<QueueItemUi> = emptyList(),
    val sleepTimerRemainingMs: Long = 0L
)

@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val musicRepository: MusicRepository,
    private val storageManager: LargeFileStorageManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var sleepJob: Job? = null
    private var sleepEndsAt: Long = 0L
    private var persistJob: Job? = null
    private var maintenanceJob: Job? = null

    /** Last full queue of track IDs we loaded (for resume). */
    private var lastQueueIds: List<Long> = emptyList()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
            ) {
                schedulePersist()
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) &&
                !player.isPlaying
            ) {
                // When we leave a track, allow storage maintenance for large files
                scheduleStorageMaintenance()
            }
            if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                player.playbackState == Player.STATE_ENDED
            ) {
                scheduleStorageMaintenance()
            }
        }
    }

    fun connect() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                publish(c)
                _state.update { it.copy(isConnected = true) }
                startPersistLoop()
            } catch (_: Exception) {
                controllerFuture = null
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        cancelSleepTimer()
        persistJob?.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
        _state.value = PlayerUiState()
    }

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        val c = controller ?: return
        lastQueueIds = tracks.map { it.id }
        scope.launch {
            // Thaw cold large files before handing URIs to ExoPlayer
            val ready = withContext(Dispatchers.IO) {
                storageManager.ensurePlayable(tracks)
            }
            lastQueueIds = ready.map { it.id }
            val items = withContext(Dispatchers.Default) {
                MediaItemFactory.fromTracks(ready)
            }
            if (items.isEmpty()) return@launch
            val idx = startIndex.coerceIn(0, items.lastIndex)
            c.setMediaItems(items, idx, startPositionMs.coerceAtLeast(0L))
            c.prepare()
            c.play()
            schedulePersist()
        }
    }

    fun playTrackInQueue(tracks: List<TrackEntity>, trackId: Long) {
        val index = tracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
        playTracks(tracks, index)
    }

    /** Insert track to play immediately after the current song. */
    fun playNext(track: TrackEntity) {
        val c = controller ?: run {
            playTracks(listOf(track), 0)
            return
        }
        if (c.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
            return
        }
        scope.launch {
            val ready = withContext(Dispatchers.IO) { storageManager.ensurePlayable(track) }
            val item = MediaItemFactory.fromTrack(ready)
            val insertAt = (c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount)
            c.addMediaItem(insertAt, item)
            if (lastQueueIds.isNotEmpty()) {
                val mutable = lastQueueIds.toMutableList()
                val at = insertAt.coerceIn(0, mutable.size)
                mutable.add(at, ready.id)
                lastQueueIds = mutable
            }
            schedulePersist()
        }
    }

    fun addToQueue(track: TrackEntity) {
        val c = controller
        if (c == null || c.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
            return
        }
        scope.launch {
            val ready = withContext(Dispatchers.IO) { storageManager.ensurePlayable(track) }
            c.addMediaItem(MediaItemFactory.fromTrack(ready))
            if (lastQueueIds.isNotEmpty()) {
                lastQueueIds = lastQueueIds + ready.id
            }
            schedulePersist()
        }
    }

    private fun scheduleStorageMaintenance() {
        maintenanceJob?.cancel()
        maintenanceJob = scope.launch {
            delay(8_000) // let gap between tracks settle
            val exclude = lastQueueIds.toSet()
            withContext(Dispatchers.IO) {
                storageManager.runMaintenance(excludeTrackIds = exclude)
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        schedulePersist()
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        val c = controller ?: return
        if (c.currentPosition > 3000) {
            c.seekTo(0)
        } else {
            c.seekToPreviousMediaItem()
        }
    }

    fun seekToQueueIndex(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun refreshPosition() {
        controller?.let { publish(it) }
        if (sleepEndsAt > 0L) {
            val remaining = (sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
            _state.update { it.copy(sleepTimerRemainingMs = remaining) }
            if (remaining == 0L) sleepEndsAt = 0L
        }
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            sleepEndsAt = 0L
            _state.update { it.copy(sleepTimerRemainingMs = 0L) }
            return
        }
        val durationMs = minutes * 60_000L
        sleepEndsAt = System.currentTimeMillis() + durationMs
        _state.update { it.copy(sleepTimerRemainingMs = durationMs) }
        sleepJob = scope.launch {
            delay(durationMs)
            controller?.pause()
            sleepEndsAt = 0L
            _state.update { it.copy(sleepTimerRemainingMs = 0L, isPlaying = false) }
            persistNow()
        }
    }

    fun cancelSleepTimer() = setSleepTimer(0)

    /**
     * Restore last session (queue + position) and optionally auto-play.
     * If a queue is already loaded and nothing is playing, [autoPlay] continues it
     * without reloading (handy for Drive mode / car connect).
     */
    fun resumeLastSession(autoPlay: Boolean = true) {
        scope.launch {
            val c = controller ?: return@launch
            // Already have a queue (paused mid-trip) — just continue.
            if (c.mediaItemCount > 0) {
                if (autoPlay && !c.isPlaying) c.play()
                return@launch
            }
            val snap = appPreferences.getResume() ?: return@launch
            if (!snap.hasSession) return@launch
            val tracks = musicRepository.getTracksByIdsOrdered(snap.trackIds)
            if (tracks.isEmpty()) return@launch
            val ready = withContext(Dispatchers.IO) {
                storageManager.ensurePlayable(tracks)
            }
            if (ready.isEmpty()) return@launch
            val idx = snap.index.coerceIn(0, ready.lastIndex)
            lastQueueIds = ready.map { it.id }
            val items = withContext(Dispatchers.Default) {
                MediaItemFactory.fromTracks(ready)
            }
            c.setMediaItems(items, idx, snap.positionMs.coerceAtLeast(0L))
            c.prepare()
            if (autoPlay) c.play() else c.pause()
        }
    }

    private fun startPersistLoop() {
        persistJob?.cancel()
        persistJob = scope.launch {
            while (isActive) {
                delay(8_000)
                val c = controller
                if (c != null && (c.isPlaying || c.playbackState == Player.STATE_READY)) {
                    persistNow()
                }
            }
        }
    }

    private fun schedulePersist() {
        scope.launch {
            delay(400)
            persistNow()
        }
    }

    private suspend fun persistNow() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = if (lastQueueIds.size == c.mediaItemCount) {
            lastQueueIds
        } else {
            // Fallback: parse media IDs
            (0 until c.mediaItemCount).mapNotNull { i ->
                MediaIds.parseTrackId(c.getMediaItemAt(i).mediaId)
            }
        }
        if (ids.isEmpty()) return
        val meta = c.mediaMetadata
        appPreferences.saveResume(
            ResumeSnapshot(
                trackIds = ids,
                index = c.currentMediaItemIndex.coerceAtLeast(0),
                positionMs = c.currentPosition.coerceAtLeast(0L),
                title = meta.title?.toString().orEmpty(),
                artist = meta.artist?.toString().orEmpty(),
                artworkUri = meta.artworkUri?.toString()
            )
        )
    }

    private fun publish(player: Player) {
        val meta = player.mediaMetadata
        val queue = buildQueueSnapshot(player)
        val remaining = if (sleepEndsAt > 0L) {
            (sleepEndsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        } else 0L
        _state.update {
            it.copy(
                isConnected = true,
                isPlaying = player.isPlaying,
                currentMediaId = player.currentMediaItem?.mediaId,
                title = meta.title?.toString().orEmpty().ifBlank { "Nothing playing" },
                artist = meta.artist?.toString().orEmpty(),
                album = meta.albumTitle?.toString().orEmpty(),
                artworkUri = meta.artworkUri?.toString(),
                durationMs = player.duration.coerceAtLeast(0L),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                shuffleModeEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                queueSize = player.mediaItemCount,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                queue = queue,
                sleepTimerRemainingMs = remaining
            )
        }
    }

    private fun buildQueueSnapshot(player: Player): List<QueueItemUi> {
        val count = player.mediaItemCount
        if (count == 0) return emptyList()
        val start = (player.currentMediaItemIndex - 10).coerceAtLeast(0)
        val end = (player.currentMediaItemIndex + 40).coerceAtMost(count)
        return (start until end).map { i ->
            val item = player.getMediaItemAt(i)
            val m = item.mediaMetadata
            QueueItemUi(
                mediaId = item.mediaId,
                title = m.title?.toString().orEmpty().ifBlank { "Track ${i + 1}" },
                artist = m.artist?.toString().orEmpty(),
                artworkUri = m.artworkUri?.toString()
            )
        }
    }
}
