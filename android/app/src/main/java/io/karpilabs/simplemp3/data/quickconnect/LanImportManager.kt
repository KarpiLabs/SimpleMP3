package io.karpilabs.simplemp3.data.quickconnect

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.externalItemIdToTrackId
import io.karpilabs.simplemp3.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saves audio files uploaded through Quick Connect into app-private storage
 * and indexes them in the Room library (source = [TrackEntity.SOURCE_LAN]).
 */
@Singleton
class LanImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val musicRepository: MusicRepository
) {
    private fun audioDir(): File =
        File(context.filesDir, "offline/lan/audio").also { it.mkdirs() }

    suspend fun importStream(
        input: InputStream,
        originalName: String,
        playlistId: Long? = null
    ): TrackEntity = withContext(Dispatchers.IO) {
        musicRepository.ensureSystemPlaylists()

        val safeName = sanitizeFileName(originalName.ifBlank { "upload.mp3" })
        val ext = extensionOf(safeName)
        require(ext in ALLOWED_EXTENSIONS) {
            "Unsupported file type .$ext — use mp3, m4a, aac, flac, ogg, opus, or wav"
        }

        val temp = File(audioDir(), "tmp-${UUID.randomUUID()}.$ext")
        try {
            temp.outputStream().use { out -> input.copyTo(out) }
            require(temp.length() > 0L) { "Empty file" }
            require(temp.length() <= MAX_BYTES) {
                "File too large (max ${MAX_BYTES / (1024 * 1024)} MB)"
            }

            val digest = sha256Hex(temp)
            val trackId = externalItemIdToTrackId("lan:$digest")

            trackDao.getTrackById(trackId)?.let { existing ->
                if (fileExists(existing.uri)) {
                    if (playlistId != null) {
                        playlistDao.addTrackToEnd(playlistId, existing.id)
                    }
                    return@withContext existing
                }
            }

            val dest = File(audioDir(), "$digest.$ext")
            if (!dest.exists()) {
                temp.copyTo(dest, overwrite = true)
            }

            val meta = readMetadata(dest, safeName)
            val track = TrackEntity(
                id = trackId,
                title = meta.title,
                artist = meta.artist,
                album = meta.album,
                albumId = meta.album.hashCode().toLong().and(0x7fffffff),
                artistId = meta.artist.hashCode().toLong().and(0x7fffffff),
                uri = Uri.fromFile(dest).toString(),
                duration = meta.durationMs,
                artworkUri = null,
                dateAdded = System.currentTimeMillis(),
                year = meta.year,
                trackNumber = meta.trackNumber,
                genre = meta.genre,
                size = dest.length(),
                source = TrackEntity.SOURCE_LAN,
                jellyfinId = digest,
                isOffline = true
            )
            trackDao.insertTrack(track)

            playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_LAN)?.id?.let { lanPl ->
                playlistDao.addTrackToEnd(lanPl, track.id)
            }
            if (playlistId != null) {
                playlistDao.addTrackToEnd(playlistId, track.id)
            }
            track
        } finally {
            temp.delete()
        }
    }

    suspend fun deleteLanTrack(trackId: Long): Boolean = withContext(Dispatchers.IO) {
        val track = trackDao.getTrackById(trackId) ?: return@withContext false
        if (track.source != TrackEntity.SOURCE_LAN) return@withContext false
        deleteFileUri(track.uri)
        trackDao.deleteTrackById(trackId)
        true
    }

    private fun readMetadata(file: File, fallbackName: String): Meta {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName.substringBeforeLast('.').ifBlank { "Untitled" }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: "Unknown artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "LAN Imports"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.take(4)
                ?.toIntOrNull()
                ?: 0
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')
                ?.toIntOrNull()
                ?: 0
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            Meta(title, artist, album, durationMs, year, trackNumber, genre)
        } catch (_: Exception) {
            Meta(
                title = fallbackName.substringBeforeLast('.').ifBlank { "Untitled" },
                artist = "Unknown artist",
                album = "LAN Imports",
                durationMs = 0L,
                year = 0,
                trackNumber = 0,
                genre = null
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class Meta(
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val year: Int,
        val trackNumber: Int,
        val genre: String?
    )

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fileExists(uri: String): Boolean = try {
        val path = Uri.parse(uri).path ?: return false
        File(path).exists()
    } catch (_: Exception) {
        false
    }

    private fun deleteFileUri(uri: String) {
        runCatching {
            val path = Uri.parse(uri).path ?: return
            File(path).delete()
        }
    }

    private fun sanitizeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return base.replace(Regex("[^A-Za-z0-9._\\- ]"), "_").take(180).ifBlank { "upload.mp3" }
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', missingDelimiterValue = "mp3")
            .lowercase(Locale.US)
            .ifBlank { "mp3" }

    companion object {
        private const val MAX_BYTES = 200L * 1024L * 1024L // 200 MB
        val ALLOWED_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")
    }
}
