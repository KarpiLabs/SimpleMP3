package io.karpilabs.simplemp3.data.stream

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.externalItemIdToTrackId
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
 * Bookmarks a live network stream (progressive URL or HLS `.m3u8`) as a
 * [TrackEntity.SOURCE_STREAM] library item and adds it to the "Saved Streams"
 * playlist. The original URL is kept so playback is live — nothing is downloaded.
 *
 * Artwork is either a user-chosen image or a best-effort capture from the stream
 * (embedded picture, ICY station page, HLS logo, Open Graph).
 */
@Singleton
class StreamSaveManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val trackDao: TrackDao,
        private val playlistDao: PlaylistDao,
        private val artworkFetcher: StreamArtworkFetcher,
    ) {
        private val mutex = Mutex()

        private val _progress = MutableStateFlow(StreamSaveProgress())
        val progress: StateFlow<StreamSaveProgress> = _progress.asStateFlow()

        private fun artDir(): File = File(context.filesDir, "stream_art").also { it.mkdirs() }

        /**
         * Bookmark [url] into Saved Streams. [title] falls back to ICY name / URL slug.
         * [customArtworkUri] is a content or file URI the user picked; when set we skip
         * auto-capture.
         */
        suspend fun save(
            url: String,
            title: String,
            customArtworkUri: String? = null,
        ): Result<TrackEntity> =
            mutex.withLock {
                val cleaned = url.trim()
                if (cleaned.isBlank()) {
                    return Result.failure(IllegalArgumentException("Paste a stream URL first"))
                }
                if (!cleaned.startsWith("http://", true) && !cleaned.startsWith("https://", true)) {
                    return Result.failure(IllegalArgumentException("Stream URL must start with http(s)://"))
                }

                val streamKey = streamKeyFor(cleaned)
                _progress.value =
                    StreamSaveProgress(
                        isActive = true,
                        phase = "Saving stream",
                        url = cleaned,
                        title = title,
                    )

                return try {
                    val probe =
                        if (customArtworkUri.isNullOrBlank()) {
                            artworkFetcher.capture(cleaned)
                        } else {
                            StreamArtworkFetcher.Probe()
                        }
                    val name =
                        sanitizeTitle(
                            title.ifBlank { probe.icyName.orEmpty() }.ifBlank { defaultTitleFrom(cleaned) },
                        )
                    val artworkUri =
                        withContext(Dispatchers.IO) {
                            persistCustomArtwork(customArtworkUri, streamKey)
                                ?: persistCapturedArtwork(probe.bytes, streamKey)
                        }

                    val existing = trackDao.getByJellyfinId(streamKey)
                    val track =
                        (existing?.takeIf { it.source == TrackEntity.SOURCE_STREAM } ?: TrackEntity(
                            id = externalItemIdToTrackId(streamKey),
                            title = name,
                            artist = "Stream",
                            album = "Saved Streams",
                            albumId = "saved_streams".hashCode().toLong().and(0x7fffffff),
                            uri = cleaned,
                            duration = 0L,
                            artworkUri = artworkUri,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            trackNumber = 0,
                            size = 0L,
                            source = TrackEntity.SOURCE_STREAM,
                            jellyfinId = streamKey,
                            isOffline = false,
                        )).copy(
                            title = name,
                            uri = cleaned,
                            artist = existing?.artist?.takeIf { it.isNotBlank() } ?: "Stream",
                            album = "Saved Streams",
                            artworkUri = artworkUri ?: existing?.artworkUri,
                            isOffline = false,
                            size = 0L,
                            source = TrackEntity.SOURCE_STREAM,
                            jellyfinId = streamKey,
                        )

                    if (existing != null && existing.uri != cleaned) {
                        deleteFileUriSafely(existing.uri)
                    }

                    trackDao.insertTrack(track)
                    ensureStreamsPlaylist()
                    playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_STREAMS)?.id?.let { plId ->
                        playlistDao.addTrackToEnd(plId, track.id)
                    }
                    _progress.value =
                        StreamSaveProgress(
                            isActive = false,
                            phase = "Done",
                            title = name,
                            lastResult = "Added to Saved Streams · $name",
                        )
                    Result.success(track)
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

        suspend fun setCustomArtwork(
            trackId: Long,
            source: Uri,
        ): Result<TrackEntity> =
            mutex.withLock {
                val track = trackDao.getTrackById(trackId)
                    ?: return Result.failure(IllegalArgumentException("Stream not found"))
                if (track.source != TrackEntity.SOURCE_STREAM) {
                    return Result.failure(IllegalArgumentException("Not a saved stream"))
                }
                val key = track.jellyfinId ?: streamKeyFor(track.uri)
                val artwork =
                    withContext(Dispatchers.IO) { persistArtworkFromUri(source, key) }
                        ?: return Result.failure(IllegalArgumentException("Couldn't read that image"))
                val updated = track.copy(artworkUri = artwork)
                trackDao.updateArtworkUri(trackId, artwork)
                Result.success(updated)
            }

        /** Copy a user-picked image into app storage and return its file URI, or null. */
        suspend fun importPendingArtwork(source: Uri): String? =
            withContext(Dispatchers.IO) {
                persistArtworkFromUri(source, "pending_icon")
            }

        suspend fun removeSaved(trackId: Long) {
            val track = trackDao.getTrackById(trackId) ?: return
            if (track.source != TrackEntity.SOURCE_STREAM) return
            deleteFileUriSafely(track.uri)
            track.artworkUri?.let { uri -> deleteFileUriSafely(uri) }
            trackDao.deleteTrackById(trackId)
        }

        private fun deleteFileUriSafely(uri: String) {
            deleteFileUriSafely(uri, artDir())
        }

        companion object {
            internal fun deleteFileUriSafely(
                uri: String,
                allowedDir: File,
            ) {
                runCatching {
                    if (!uri.startsWith("file:")) return
                    val path = runCatching { Uri.parse(uri).path }.getOrNull()
                        ?: runCatching { java.net.URI.create(uri).path }.getOrNull()
                        ?: return
                    deleteFileSafely(File(path), allowedDir)
                }
            }

            internal fun deleteFileSafely(
                file: File,
                allowedDir: File,
            ) {
                runCatching {
                    val targetFile = file.canonicalFile
                    val baseDir = allowedDir.canonicalFile
                    if (targetFile.canonicalPath.startsWith(baseDir.canonicalPath + File.separator)) {
                        targetFile.delete()
                    }
                }
            }
        }

        private suspend fun ensureStreamsPlaylist() {
            val existing = playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_STREAMS)
            val description = "Live streams saved to a playlist"
            if (existing == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "Saved Streams",
                        description = description,
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_STREAMS,
                    ),
                )
            } else if (existing.description != description) {
                playlistDao.updatePlaylist(existing.copy(description = description))
            }
        }

        private fun persistCustomArtwork(
            uriString: String?,
            streamKey: String,
        ): String? {
            if (uriString.isNullOrBlank()) return null
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
            return persistArtworkFromUri(uri, streamKey)
        }

        private fun persistCapturedArtwork(
            bytes: ByteArray?,
            streamKey: String,
        ): String? {
            if (bytes == null || !StreamArtworkParser.looksLikeImage(bytes)) return null
            val ext = StreamArtworkParser.imageExtension(bytes)
            val dest = File(artDir(), "${safeFileName(streamKey)}.$ext")
            return runCatching {
                dest.writeBytes(bytes)
                Uri.fromFile(dest).toString()
            }.getOrNull()
        }

        private fun persistArtworkFromUri(
            source: Uri,
            key: String,
        ): String? {
            val dest = File(artDir(), "${safeFileName(key)}.img")
            return runCatching {
                val copied =
                    when {
                        source.scheme == "file" -> {
                            val path = source.path ?: return@runCatching null
                            val from = File(path)
                            if (!from.exists()) return@runCatching null
                            from.copyTo(dest, overwrite = true)
                            true
                        }
                        else -> {
                            context.contentResolver.openInputStream(source)?.use { input ->
                                dest.outputStream().use { output -> input.copyTo(output) }
                                true
                            } ?: false
                        }
                    }
                if (!copied || !dest.exists() || dest.length() < 16L) {
                    dest.delete()
                    return@runCatching null
                }
                Uri.fromFile(dest).toString()
            }.getOrNull()
        }

        private fun defaultTitleFrom(url: String): String {
            val last = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
            return last.takeIf { it.isNotBlank() } ?: "Saved stream"
        }

        private fun sanitizeTitle(title: String): String =
            title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Saved stream" }

        private fun streamKeyFor(url: String): String = "stream:${url.hashCode().toUInt().toString(16)}"

        private fun safeFileName(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
