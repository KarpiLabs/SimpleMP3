package io.karpilabs.simplemp3.data.youtube

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.externalItemIdToTrackId
import io.karpilabs.simplemp3.data.storage.LargeFileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class YoutubeDownloadProgress(
    val isActive: Boolean = false,
    val phase: String = "",
    val url: String = "",
    val title: String = "",
    val percent: Int = -1,
    val error: String? = null,
    val lastResult: String? = null,
)

/**
 * YouTube → local MP3, inspired by ~/Projects/yt-dl:
 *
 *   yt-dlp -x --audio-format mp3 --audio-quality 0 --embed-thumbnail --embed-metadata
 *
 * Pipeline:
 *  1. NewPipeExtractor → stream URL + title/uploader/thumbnail
 *  2. OkHttp download best audio
 *  3. FFmpegKit → MP3 (libmp3lame q0) + ID3 tags + embedded cover art
 *  4. Insert into Room library + YouTube Downloads playlist
 *
 * Note: re-encoding is lossy and uses more CPU than keeping m4a/opus; we do it
 * for the MP3 workflow you asked for (compatibility + yt-dl parity).
 */
@Singleton
class YoutubeDownloadManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val okHttpClient: OkHttpClient,
        private val trackDao: TrackDao,
        private val playlistDao: PlaylistDao,
        private val storageManager: LargeFileStorageManager,
    ) {
        private val mutex = Mutex()
        private val initOnce = AtomicBoolean(false)

        private val _progress = MutableStateFlow(YoutubeDownloadProgress())
        val progress: StateFlow<YoutubeDownloadProgress> = _progress.asStateFlow()

        private fun audioDir(): File = File(context.filesDir, "offline/youtube/audio").also { it.mkdirs() }

        private fun artDir(): File = File(context.filesDir, "offline/youtube/art").also { it.mkdirs() }

        private fun tempDir(): File = File(context.cacheDir, "youtube-tmp").also { it.mkdirs() }

        private fun ensureInit() {
            if (initOnce.compareAndSet(false, true)) {
                NewPipe.init(YoutubeDownloaderImpl(okHttpClient))
            }
        }

        /**
         * Download a single YouTube (or youtu.be) URL as an offline MP3 with metadata + art.
         */
        suspend fun download(url: String): Result<TrackEntity> =
            mutex.withLock {
                val cleaned = normalizeYoutubeUrl(url.trim())
                if (cleaned.isBlank()) {
                    return Result.failure(IllegalArgumentException("Paste a YouTube link first"))
                }

                _progress.value =
                    YoutubeDownloadProgress(
                        isActive = true,
                        phase = "Fetching info",
                        url = cleaned,
                    )

                val workDir = File(tempDir(), "job-${System.currentTimeMillis()}")
                workDir.mkdirs()

                return try {
                    ensureInit()
                    val info =
                        withContext(Dispatchers.IO) {
                            StreamInfo.getInfo(ServiceList.YouTube, cleaned)
                        }

                    val videoId =
                        info.id?.takeIf { it.isNotBlank() }
                            ?: extractVideoId(cleaned)
                            ?: info.originalUrl
                                .hashCode()
                                .toUInt()
                                .toString(16)

                    // Already downloaded as MP3?
                    trackDao.getByJellyfinId(videoId)?.let { existing ->
                        if (existing.source == TrackEntity.SOURCE_YOUTUBE && fileExists(existing.uri)) {
                            _progress.value =
                                YoutubeDownloadProgress(
                                    isActive = false,
                                    phase = "Done",
                                    title = existing.title,
                                    lastResult = "Already in library · ${existing.title}",
                                )
                            return Result.success(existing)
                        }
                    }

                    val title = sanitizeTitle(info.name?.takeIf { it.isNotBlank() } ?: "YouTube Audio")
                    val artist = info.uploaderName?.takeIf { it.isNotBlank() } ?: "YouTube"
                    val durationMs = (info.duration * 1000L).coerceAtLeast(0L)

                    _progress.value =
                        _progress.value.copy(
                            phase = "Downloading audio",
                            title = title,
                            percent = 0,
                        )

                    val audioStream =
                        pickBestAudio(info.audioStreams)
                            ?: return Result.failure(
                                IllegalStateException("No audio stream available for this video"),
                            )

                    val rawExt = extensionFor(audioStream)
                    val rawFile = File(workDir, "raw.$rawExt")
                    withContext(Dispatchers.IO) {
                        downloadToFile(audioStream.content, rawFile) { read, total ->
                            if (total != null && total > 0) {
                                val pct = ((read * 100) / total).toInt().coerceIn(0, 100)
                                _progress.value =
                                    _progress.value.copy(
                                        phase = "Downloading audio",
                                        title = title,
                                        percent = pct,
                                    )
                            }
                        }
                    }

                    _progress.value = _progress.value.copy(phase = "Album art", percent = -1)
                    val thumbUrl =
                        info.thumbnails
                            .maxByOrNull { it.height * it.width }
                            ?.url
                            ?: info.thumbnails.firstOrNull()?.url
                    val artFile = File(artDir(), "$videoId.jpg")
                    val artworkUri =
                        if (!thumbUrl.isNullOrBlank()) {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    downloadToFile(thumbUrl, artFile)
                                    Uri.fromFile(artFile).toString()
                                }.getOrNull()
                            }
                        } else {
                            null
                        }

                    _progress.value =
                        _progress.value.copy(
                            phase = "Converting to MP3",
                            title = title,
                            percent = -1,
                        )

                    val mp3File = File(audioDir(), "$videoId.mp3")
                    val convertResult =
                        withContext(Dispatchers.IO) {
                            AudioConverter.convertToMp3(
                                input = rawFile,
                                outputMp3 = mp3File,
                                metadata =
                                    AudioConverter.Metadata(
                                        title = title,
                                        artist = artist,
                                        album = "YouTube",
                                    ),
                                coverImage = artFile.takeIf { it.exists() },
                            )
                        }

                    if (convertResult.isFailure) {
                        // Keep the raw download so the user still gets playable audio.
                        val err = convertResult.exceptionOrNull()
                        _progress.value =
                            _progress.value.copy(
                                phase = "Convert failed — saving original",
                                title = title,
                            )
                        val fallback = File(audioDir(), "$videoId.$rawExt")
                        withContext(Dispatchers.IO) {
                            rawFile.copyTo(fallback, overwrite = true)
                        }
                        saveTrack(
                            videoId = videoId,
                            title = title,
                            artist = artist,
                            durationMs = durationMs,
                            audioFile = fallback,
                            artworkUri = artworkUri,
                            note = "Saved original audio (MP3 convert failed: ${err?.message})",
                        )
                    } else {
                        saveTrack(
                            videoId = videoId,
                            title = title,
                            artist = artist,
                            durationMs = durationMs,
                            audioFile = mp3File,
                            artworkUri = artworkUri,
                            note = "Saved MP3 · $title",
                        )
                    }
                } catch (e: Exception) {
                    _progress.value =
                        YoutubeDownloadProgress(
                            isActive = false,
                            phase = "Error",
                            url = cleaned,
                            error = e.message ?: "Download failed",
                            lastResult = e.message,
                        )
                    Result.failure(e)
                } finally {
                    workDir.deleteRecursively()
                }
            }

        private suspend fun saveTrack(
            videoId: String,
            title: String,
            artist: String,
            durationMs: Long,
            audioFile: File,
            artworkUri: String?,
            note: String,
        ): Result<TrackEntity> {
            val track =
                TrackEntity(
                    id = externalItemIdToTrackId("yt:$videoId"),
                    title = title,
                    artist = artist,
                    album = "YouTube",
                    albumId = "youtube".hashCode().toLong().and(0x7fffffff),
                    uri = Uri.fromFile(audioFile).toString(),
                    duration = durationMs,
                    artworkUri = artworkUri,
                    dateAdded = System.currentTimeMillis(),
                    year = 0,
                    trackNumber = 0,
                    size = audioFile.length(),
                    source = TrackEntity.SOURCE_YOUTUBE,
                    jellyfinId = videoId,
                    isOffline = true,
                )
            trackDao.insertTrack(track)
            ensureYoutubePlaylist()
            playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_YOUTUBE)?.id?.let { plId ->
                playlistDao.addTrackToEnd(plId, track.id)
            }

            // Movie-length / large downloads: one-time lean re-encode when enabled
            val finalTrack =
                if (storageManager.isLarge(track)) {
                    _progress.value = _progress.value.copy(phase = "Optimizing large file…", title = track.title)
                    runCatching { storageManager.optimizeIfNeeded(track) }.getOrDefault(track)
                } else {
                    track
                }

            _progress.value =
                YoutubeDownloadProgress(
                    isActive = false,
                    phase = "Done",
                    title = finalTrack.title,
                    lastResult =
                        if (finalTrack.isSizeOptimized && finalTrack.size < track.size) {
                            "Saved MP3 · optimized ${track.size / 1024 / 1024}→${finalTrack.size / 1024 / 1024} MB"
                        } else {
                            note
                        },
                )
            return Result.success(finalTrack)
        }

        suspend fun removeDownload(trackId: Long) {
            val track = trackDao.getTrackById(trackId) ?: return
            if (track.source != TrackEntity.SOURCE_YOUTUBE) return
            track.jellyfinId?.let { id ->
                audioDir().listFiles()?.filter { it.nameWithoutExtension == id }?.forEach { deleteFileSafely(it) }
                deleteFileSafely(File(artDir(), "$id.jpg"))
            }
            deleteFileUriSafely(track.uri)
            trackDao.deleteTrackById(trackId)
        }

        private fun deleteFileUriSafely(uri: String) {
            runCatching {
                val path = Uri.parse(uri).path ?: return
                deleteFileSafely(File(path))
            }
        }

        private fun deleteFileSafely(file: File) {
            runCatching {
                val targetFile = file.canonicalFile
                val allowedDir = File(context.filesDir, "offline/youtube").canonicalFile
                if (targetFile.canonicalPath.startsWith(allowedDir.canonicalPath + File.separator)) {
                    targetFile.delete()
                }
            }
        }

        suspend fun clearAll(): Int {
            val tracks = trackDao.getTracksBySourceOnce(TrackEntity.SOURCE_YOUTUBE)
            tracks.forEach { removeDownload(it.id) }
            audioDir().listFiles()?.forEach { it.delete() }
            artDir().listFiles()?.forEach { it.delete() }
            return tracks.size
        }

        private suspend fun ensureYoutubePlaylist() {
            if (playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_YOUTUBE) == null) {
                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        name = "YouTube Downloads",
                        description = "Imported from YouTube links as MP3",
                        isSystem = true,
                        systemType = PlaylistEntity.SYSTEM_YOUTUBE,
                    ),
                )
            }
        }

        private fun pickBestAudio(streams: List<AudioStream>): AudioStream? {
            if (streams.isEmpty()) return null
            // Prefer highest bitrate; m4a/aac first for clean decode → MP3.
            return streams
                .sortedWith(
                    compareByDescending<AudioStream> { preferFormatScore(it) }
                        .thenByDescending { it.averageBitrate },
                ).firstOrNull()
        }

        private fun preferFormatScore(stream: AudioStream): Int {
            val fmt =
                stream.format
                    ?.name
                    ?.lowercase()
                    .orEmpty()
            val mime =
                stream.format
                    ?.mimeType
                    ?.lowercase()
                    .orEmpty()
            return when {
                mime.contains("mp4") || fmt.contains("m4a") || mime.contains("aac") -> 3
                mime.contains("mpeg") || fmt.contains("mp3") -> 2
                mime.contains("webm") || mime.contains("opus") || mime.contains("ogg") -> 1
                else -> 0
            }
        }

        private fun extensionFor(stream: AudioStream): String {
            val mime =
                stream.format
                    ?.mimeType
                    ?.lowercase()
                    .orEmpty()
            val name =
                stream.format
                    ?.name
                    ?.lowercase()
                    .orEmpty()
            return when {
                mime.contains("mp4") || name.contains("m4a") || mime.contains("aac") -> "m4a"
                mime.contains("mpeg") || name.contains("mp3") -> "mp3"
                mime.contains("webm") -> "webm"
                mime.contains("ogg") || mime.contains("opus") -> "ogg"
                else -> "m4a"
            }
        }

        private fun downloadToFile(
            url: String,
            dest: File,
            onProgress: ((read: Long, total: Long?) -> Unit)? = null,
        ) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
                    ).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed (${response.code})")
                }
                val body = response.body ?: throw IllegalStateException("Empty response body")
                val total = body.contentLength().takeIf { it > 0 }
                dest.parentFile?.mkdirs()
                val tmp = File(dest.absolutePath + ".part")
                FileOutputStream(tmp).use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            readTotal += n
                            onProgress?.invoke(readTotal, total)
                        }
                    }
                }
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
        }

        private fun fileExists(uri: String): Boolean =
            runCatching {
                val path = Uri.parse(uri).path ?: return false
                File(path).exists()
            }.getOrDefault(false)

        private fun sanitizeTitle(title: String): String =
            title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .trim()
                .ifBlank { "YouTube Audio" }

        companion object {
            fun normalizeYoutubeUrl(raw: String): String {
                val s = raw.trim()
                if (s.isBlank()) return ""
                if (s.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                    return "https://www.youtube.com/watch?v=$s"
                }
                return s
            }

            fun extractVideoId(url: String): String? {
                val patterns =
                    listOf(
                        Regex("""(?:youtube\.com/watch\?v=|youtu\.be/|youtube\.com/embed/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""),
                        Regex("""^[a-zA-Z0-9_-]{11}$"""),
                    )
                for (p in patterns) {
                    val m = p.find(url)
                    if (m != null) {
                        return m.groupValues.lastOrNull { it.length == 11 } ?: m.groupValues.getOrNull(1)
                    }
                }
                return null
            }
        }
    }
