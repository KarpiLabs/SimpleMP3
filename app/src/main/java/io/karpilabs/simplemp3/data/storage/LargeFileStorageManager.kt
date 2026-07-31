package io.karpilabs.simplemp3.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.youtube.AudioConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage strategy for **large** app-owned audio (Jellyfin / YouTube offline),
 * e.g. full-movie MP3s:
 *
 * 1. **Size optimize (lossy, once)** — if the file is large *and* high bitrate,
 *    re-encode with FFmpeg to ~96–128 kbps. Biggest real savings (MP3 is already
 *    lossy; gzip alone only saves ~5–15%).
 *
 * 2. **Cold pack (lossless gzip)** — when a large file has been idle, store it
 *    as `.gz` and free the hot bytes. On play, thaw transparently to [TrackEntity.uri].
 *
 * Only app-owned files under our sandbox are touched — never MediaStore library tracks.
 */
@Singleton
class LargeFileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val appPreferences: AppPreferences
) {
    private val mutex = Mutex()

    private fun coldDir(): File =
        File(context.filesDir, "storage/cold").also { it.mkdirs() }

    companion object {
        private const val TAG = "LargeFileStorage"

        /** Treat as large if either size or duration crosses these. */
        const val LARGE_MIN_BYTES = 30L * 1024 * 1024 // 30 MB
        const val LARGE_MIN_DURATION_MS = 25L * 60 * 1000 // 25 min

        /** Only re-encode when average bitrate is still fat. */
        const val OPTIMIZE_MIN_BITRATE = 150_000 // bits/sec

        /** Idle this long before cold-packing (default 2 hours). */
        const val COLD_IDLE_MS = 2L * 60 * 60 * 1000

        /** Cold-pack only when still at least this big after optimize. */
        const val COLD_MIN_BYTES = 25L * 1024 * 1024
    }

    fun isLarge(track: TrackEntity): Boolean =
        track.size >= LARGE_MIN_BYTES || track.duration >= LARGE_MIN_DURATION_MS

    fun estimatedBitrate(track: TrackEntity): Long {
        val secs = (track.duration / 1000L).coerceAtLeast(1L)
        return (track.size * 8L) / secs
    }

    /**
     * Ensure every track has a playable hot file at [TrackEntity.uri].
     * Thaws cold archives; no-ops for already-hot / MediaStore tracks.
     */
    suspend fun ensurePlayable(tracks: List<TrackEntity>): List<TrackEntity> {
        if (tracks.isEmpty()) return tracks
        return tracks.map { ensurePlayable(it) }
    }

    suspend fun ensurePlayable(track: TrackEntity): TrackEntity = mutex.withLock {
        if (!track.isAppOwned) return track
        val now = System.currentTimeMillis()
        if (!track.isCold) {
            // Touch lastPlayed so we don't cold-pack mid-session (throttle DB writes)
            if (now - track.lastPlayedAt > 60_000L) {
                val touched = track.copy(lastPlayedAt = now)
                trackDao.insertTrack(touched)
                return touched
            }
            return track
        }
        return withContext(Dispatchers.IO) { thaw(track, now) }
    }

    /**
     * One-time lossy re-encode for large high-bitrate files.
     * @return updated track, or original if skipped/failed
     */
    suspend fun optimizeIfNeeded(track: TrackEntity): TrackEntity = mutex.withLock {
        if (!appPreferences.isLargeFileOptimize()) return track
        if (!track.isAppOwned || track.isSizeOptimized || track.neverCompress) return track
        if (!isLarge(track)) return track
        if (estimatedBitrate(track) < OPTIMIZE_MIN_BITRATE) {
            // Already lean — mark optimized so we don't keep checking
            val marked = track.copy(isSizeOptimized = true)
            trackDao.insertTrack(marked)
            return marked
        }
        // Must be hot before re-encode
        val hot = if (track.isCold) {
            withContext(Dispatchers.IO) { thaw(track, System.currentTimeMillis()) }
        } else track

        return withContext(Dispatchers.IO) { reencodeLean(hot) }
    }

    /**
     * Star / unstar “never compress”. When starring, thaw if cold so the full
     * file stays expanded and stays out of optimize + cold-pack paths.
     */
    suspend fun setNeverCompress(trackId: Long, never: Boolean): TrackEntity? = mutex.withLock {
        val track = trackDao.getTrackById(trackId) ?: return null
        if (!track.isAppOwned) return track
        var updated = track.copy(neverCompress = never)
        if (never && updated.isCold) {
            updated = withContext(Dispatchers.IO) {
                thaw(updated, System.currentTimeMillis())
            }.copy(neverCompress = true)
        }
        trackDao.insertTrack(updated)
        updated
    }

    /**
     * Background pass: optimize unoptimized large tracks, then cold-pack idle ones.
     * Safe to call from WorkManager / after playback.
     */
    suspend fun runMaintenance(excludeTrackIds: Set<Long> = emptySet()) {
        mutex.withLock {
            if (appPreferences.isLargeFileOptimize()) {
                val candidates = trackDao.getUnoptimizedLargeTracks(
                    minSize = LARGE_MIN_BYTES / 2,
                    minDurationMs = LARGE_MIN_DURATION_MS,
                    limit = 3
                )
                for (t in candidates) {
                    if (t.id in excludeTrackIds) continue
                    runCatching {
                        val hot = if (t.isCold) thaw(t, System.currentTimeMillis()) else t
                        reencodeLean(hot)
                    }.onFailure { Log.w(TAG, "optimize failed ${t.id}: ${it.message}") }
                }
            }

            if (appPreferences.isLargeFileColdPack()) {
                val idleBefore = System.currentTimeMillis() - COLD_IDLE_MS
                val idle = trackDao.getHotIdleLargeTracks(
                    minSize = COLD_MIN_BYTES,
                    idleBeforeMs = idleBefore,
                    limit = 5
                )
                for (t in idle) {
                    if (t.id in excludeTrackIds) continue
                    if (!isLarge(t)) continue
                    runCatching { coldPack(t) }
                        .onFailure { Log.w(TAG, "cold pack failed ${t.id}: ${it.message}") }
                }
            }
        }
    }

    /** Immediately cold-pack a large track if eligible (e.g. after it finishes playing). */
    suspend fun maybeColdPackAfterPlay(trackId: Long, activeIds: Set<Long>) {
        if (!appPreferences.isLargeFileColdPack()) return
        mutex.withLock {
            val track = trackDao.getTrackById(trackId) ?: return
            if (track.id in activeIds) return
            if (!track.isAppOwned || track.isCold) return
            if (track.size < COLD_MIN_BYTES && track.duration < LARGE_MIN_DURATION_MS) return
            // Don't pack right after play — require idle window via lastPlayedAt
            val idleMs = System.currentTimeMillis() - track.lastPlayedAt
            if (idleMs < COLD_IDLE_MS) return
            runCatching { coldPack(track) }
        }
    }

    // ── internals ──────────────────────────────────────────────────

    private suspend fun thaw(track: TrackEntity, now: Long): TrackEntity {
        val coldPath = track.coldUri?.let { uriPath(it) }
            ?: File(coldDir(), "${track.id}.mp3.gz").absolutePath
        val coldFile = File(coldPath)
        val hotFile = uriPath(track.uri)?.let { File(it) }
            ?: File(context.filesDir, "storage/warm/${track.id}.mp3")

        if (hotFile.exists() && hotFile.length() > 0L) {
            val updated = track.copy(
                storageState = TrackEntity.STORAGE_HOT,
                lastPlayedAt = now,
                size = hotFile.length()
            )
            trackDao.insertTrack(updated)
            return updated
        }

        if (!coldFile.exists()) {
            Log.e(TAG, "Cold archive missing for track ${track.id}")
            return track
        }

        hotFile.parentFile?.mkdirs()
        val tmp = File(hotFile.absolutePath + ".part")
        GZIPInputStream(FileInputStream(coldFile)).use { gz ->
            FileOutputStream(tmp).use { out -> gz.copyTo(out, DEFAULT_BUFFER_SIZE) }
        }
        if (!tmp.renameTo(hotFile)) {
            tmp.copyTo(hotFile, overwrite = true)
            tmp.delete()
        }
        // Drop cold archive after successful thaw (we re-pack when idle again)
        coldFile.delete()

        val updated = track.copy(
            uri = Uri.fromFile(hotFile).toString(),
            storageState = TrackEntity.STORAGE_HOT,
            coldUri = null,
            size = hotFile.length(),
            lastPlayedAt = now
        )
        trackDao.insertTrack(updated)
        Log.i(TAG, "Thawed track ${track.id} → ${hotFile.length()} bytes")
        return updated
    }

    private suspend fun coldPack(track: TrackEntity): TrackEntity {
        if (track.isCold || track.neverCompress) return track
        val hotPath = uriPath(track.uri) ?: return track
        val hotFile = File(hotPath)
        if (!hotFile.exists() || hotFile.length() < COLD_MIN_BYTES) return track
        // Only pack files we own under app storage
        if (!hotFile.absolutePath.startsWith(context.filesDir.absolutePath)) return track

        val coldFile = File(coldDir(), "${track.id}.mp3.gz")
        val tmp = File(coldFile.absolutePath + ".part")
        GZIPOutputStream(FileOutputStream(tmp)).use { gz ->
            FileInputStream(hotFile).use { input -> input.copyTo(gz, DEFAULT_BUFFER_SIZE) }
        }
        if (!tmp.renameTo(coldFile)) {
            tmp.copyTo(coldFile, overwrite = true)
            tmp.delete()
        }

        val hotSize = hotFile.length()
        val coldSize = coldFile.length()
        // If gzip barely helped (< 3%), keep hot — not worth thaw latency
        if (coldSize >= hotSize * 97 / 100) {
            coldFile.delete()
            Log.i(TAG, "Skip cold pack ${track.id}: gzip saved <3%")
            return track
        }

        val warmPath = hotFile.absolutePath
        hotFile.delete()
        val updated = track.copy(
            storageState = TrackEntity.STORAGE_COLD,
            coldUri = Uri.fromFile(coldFile).toString(),
            // uri keeps the preferred warm path for next thaw
            uri = Uri.fromFile(File(warmPath)).toString(),
            size = coldSize
        )
        trackDao.insertTrack(updated)
        Log.i(
            TAG,
            "Cold-packed ${track.id}: $hotSize → $coldSize " +
                "(saved ${100 - coldSize * 100 / hotSize.coerceAtLeast(1)}%)"
        )
        return updated
    }

    private suspend fun reencodeLean(track: TrackEntity): TrackEntity {
        if (track.neverCompress) return track
        val path = uriPath(track.uri) ?: return track
        val input = File(path)
        if (!input.exists() || input.length() == 0L) return track
        if (!input.absolutePath.startsWith(context.filesDir.absolutePath)) return track

        // Movies / long form → leaner; album-length large files stay a bit richer
        val targetKbps = when {
            track.duration >= 90L * 60 * 1000 -> 96
            track.duration >= 45L * 60 * 1000 -> 112
            else -> 128
        }

        val out = File(input.parentFile, "${input.nameWithoutExtension}.lean.mp3")
        val result = AudioConverter.convertToMp3AtBitrate(
            input = input,
            outputMp3 = out,
            metadata = AudioConverter.Metadata(
                title = track.title,
                artist = track.artist,
                album = track.album
            ),
            bitrateKbps = targetKbps,
            coverImage = track.artworkUri?.let { uriPath(it)?.let { p -> File(p) } }
                ?.takeIf { it.exists() }
        )

        val lean = result.getOrElse {
            Log.w(TAG, "Re-encode failed ${track.id}: ${it.message}")
            out.delete()
            return track.copy(isSizeOptimized = true).also { trackDao.insertTrack(it) }
        }

        val before = input.length()
        // Only keep if we actually saved ≥12%
        if (lean.length() >= before * 88 / 100) {
            lean.delete()
            val marked = track.copy(isSizeOptimized = true)
            trackDao.insertTrack(marked)
            Log.i(TAG, "Re-encode skip ${track.id}: savings <12%")
            return marked
        }

        val finalFile = File(input.parentFile, "${input.nameWithoutExtension}.mp3")
        if (finalFile.absolutePath != lean.absolutePath) {
            lean.copyTo(finalFile, overwrite = true)
            lean.delete()
        }
        if (input.absolutePath != finalFile.absolutePath) {
            input.delete()
        }

        val updated = track.copy(
            uri = Uri.fromFile(finalFile).toString(),
            size = finalFile.length(),
            isSizeOptimized = true,
            storageState = TrackEntity.STORAGE_HOT,
            coldUri = null
        )
        trackDao.insertTrack(updated)
        Log.i(
            TAG,
            "Optimized ${track.id}: $before → ${finalFile.length()} @ ${targetKbps}k"
        )
        return updated
    }

    private fun uriPath(uri: String): String? =
        runCatching {
            val u = Uri.parse(uri)
            when (u.scheme) {
                "file", null -> u.path
                else -> u.path // content: not used for app-owned
            }
        }.getOrNull()
}
