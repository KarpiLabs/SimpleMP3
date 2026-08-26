package io.karpilabs.simplemp3.data.stream

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.externalItemIdToTrackId
import io.karpilabs.simplemp3.data.youtube.AudioConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StreamSaveProgress(
    val isActive: Boolean = false,
    val phase: String = "",
    val url: String = "",
    val title: String = "",
    val error: String? = null,
    val lastResult: String? = null,
)

/**
 * Saves a live network stream (progressive URL or HLS `.m3u8`) to a local `.m4a`
 * via FFmpeg stream-copy — no re-encode, so it's fast and lossless. The saved file
 * is registered as a [TrackEntity.SOURCE_STREAM] track and added to the
 * "Saved Streams" system playlist so it plays offline / in Android Auto.
 *
 * FFmpeg must have network (https/tls) protocol support to fetch remote inputs; if
 * not, the save fails with the FFmpeg error surfaced to the user.
 */
@Singleton
class StreamSaveManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackDao: TrackDao,
        private val playlistDao: PlaylistDao,
    ) {
        private val mutex = Mutex()

        private val _progress = MutableStateFlow(StreamSaveProgress())
        val progress: StateFlow<StreamSaveProgress> = _progress.asStateFlow()

        private fun audioDir(): File = File(context.filesDir, "offline/streams").also { it.mkdirs() }

        /** Save [url] to an offline `.m4a` track titled [title] (falls back to a generated name). */
        suspend fun save(
            url: String,
            title: String,
        ): Result<TrackEntity> =
            mutex.withLock {
                val cleaned = url.trim()
                if (cleaned.isBlank()) {
                    return Result.failure(IllegalArgumentException("Paste a stream URL first"))
                }
                if (!cleaned.startsWith("http://", true) && !cleaned.startsWith("https://", true)) {
                    return Result.failure(IllegalArgumentException("Stream URL must start with http(s)://"))
                }

                val name = sanitizeTitle(title.ifBlank { defaultTitleFrom(cleaned) })
                val streamKey = "stream:${cleaned.hashCode().toUInt().toString(16)}"

                _progress.value =
                    StreamSaveProgress(isActive = true, phase = "Saving stream", url = cleaned, title = name)

                // Already saved?
                trackDao.getByJellyfinId(streamKey)?.let { existing ->
                    if (existing.source == TrackEntity.SOURCE_STREAM && fileExists(existing.uri)) {
                        _progress.value =
                            StreamSaveProgress(
                                isActive = false,
                                phase = "Done",
                                title = existing.title,
                                lastResult = "Already saved · ${existing.title}",
                            )
                        return Result.success(existing)
                    }
                }

                return try {
                    val outFile = File(audioDir(), "$streamKey.m4a")
                    val result =
                        withContext(Dispatchers.IO) {
                            AudioConverter.remuxToM4a(
                                input = cleaned,
                                outputM4a = outFile,
                                metadata = AudioConverter.Metadata(title = name, artist = "Stream", album = "Saved Streams"),
                            )
                        }
                    val saved = result.getOrThrow()
                    val durationMs = withContext(Dispatchers.IO) { probeDurationMs(saved) }
                    saveTrack(streamKey, name, durationMs, saved)
                } catch (e: Exception) {
                    _progress.value =
                        StreamSaveProgress(
                            isActive = false,
                            phase = "Error",
                            url = cleaned,
                            error = e.message ?: "Save failed",
                            lastResult = e.message,
                        )
                    Result.failure(e)
                }
            }

        private suspend fun saveTrack(
            streamKey: String,
            title: String,
            durationMs: Long,
            audioFile: File,
        ): Result<TrackEntity> {
            val track =
                TrackEntity(
                    id = externalItemIdToTrackId(streamKey),
                    title = title,
                    artist = "Stream",
                    album = "Saved Streams",
                    albumId = "saved_streams".hashCode().toLong().and(0x7fffffff),
                    uri = Uri.fromFile(audioFile).toString(),
                    duration = durationMs,
                    artworkUri = null,
                    dateAdded = System.currentTimeMillis(),
                    year = 0,
                    trackNumber = 0,
                    size = audioFile.length(),
                    source = TrackEntity.SOURCE_STREAM,
                    jellyfinId = streamKey,
                    isOffline = true,
                )
            trackDao.insertTrack(track)
            ensureStreamsPlaylist()
            playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_STREAMS)?.id?.let { plId ->
                playlistDao.addTrackToEnd(plId, track.id)
            }
            _progress.value =
                StreamSaveProgress(
                    isActive = false,
                    phase = "Done",
                    title = title,
                    lastResult = "Saved · $title",
                )
            return Result.success(track)
        }

        suspend fun removeSaved(trackId: Long) {
            val track = trackDao.getTrackById(trackId) ?: return
            if (track.source != TrackEntity.SOURCE_STREAM) return
            runCatching { Uri.parse(track.uri).path?.let { File(it).delete() } }
            trackDao.deleteTrackById(trackId)
        }

        private suspend fun ensureStreamsPlaylist() {
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_STREAMS) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Saved Streams",
                        description = "Network streams saved offline",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_STREAMS,
                    ),
                )
            }
        }

        private fun probeDurationMs(file: File): Long =
            runCatching {
                MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(file.absolutePath)
                    mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
            }.getOrDefault(0L)

        private fun fileExists(uri: String): Boolean =
            runCatching { Uri.parse(uri).path?.let { File(it).exists() } ?: false }.getOrDefault(false)

        private fun defaultTitleFrom(url: String): String {
            val last = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
            return last.takeIf { it.isNotBlank() } ?: "Saved stream"
        }

        private fun sanitizeTitle(title: String): String =
            title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Saved stream" }
    }
