package io.karpilabs.simplemp3.data.drive

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.local.PlaylistDao
import io.karpilabs.simplemp3.data.local.PlaylistEntity
import io.karpilabs.simplemp3.data.local.TrackDao
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.local.externalItemIdToTrackId
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DriveBackupProgress(
    val active: Boolean = false,
    val phase: String = "",
    val percent: Int = -1,
    val error: String? = null,
    val message: String? = null
)

/**
 * Builds ZIP backups (library.json + optional app-owned media) and uploads to Google Drive.
 * Restore merges playlists and re-imports included media into app storage.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gateway: GoogleDriveGateway,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val musicRepository: MusicRepository,
    private val appPreferences: AppPreferences,
    private val moshi: Moshi
) {
    private val _progress = MutableStateFlow(DriveBackupProgress())
    val progress: StateFlow<DriveBackupProgress> = _progress.asStateFlow()

    private val payloadAdapter = moshi.adapter(BackupPayload::class.java)
    private val manifestAdapter = moshi.adapter(BackupManifest::class.java)

    private fun workDir(): File =
        File(context.cacheDir, "drive-backup").also { it.mkdirs() }

    suspend fun estimateAppOwnedMedia(): MediaBackupEstimate = withContext(Dispatchers.IO) {
        val tracks = trackDao.getAllTracksOnce().filter { it.isAppOwned && !it.isCold }
        var bytes = 0L
        var count = 0
        for (t in tracks) {
            val f = fileFromUri(t.uri) ?: continue
            if (!f.isFile) continue
            bytes += f.length()
            count++
        }
        MediaBackupEstimate(count, bytes)
    }

    suspend fun listRemoteBackups(): Result<List<DriveBackupRemote>> = withContext(Dispatchers.IO) {
        runCatching {
            val account = gateway.lastAccount()
                ?: error("Sign in to Google first")
            if (!gateway.hasDriveScope(account)) error("Drive permission missing — sign in again")
            val drive = gateway.driveFor(account)
            val folderId = gateway.ensureBackupFolder(drive)
            gateway.listBackups(drive, folderId)
        }
    }

    suspend fun createAndUploadBackup(includeMedia: Boolean): Result<DriveBackupRemote> =
        withContext(Dispatchers.IO) {
            try {
                if (appPreferences.isDriveWifiOnly() &&
                    !NetworkWifi.isUnmeteredOrWifi(context)
                ) {
                    error("Connect to Wi‑Fi (or turn off Wi‑Fi only in Drive settings)")
                }
                val account = gateway.lastAccount()
                    ?: error("Sign in to Google first")
                if (!gateway.hasDriveScope(account)) {
                    error("Drive permission missing — sign in again")
                }

                _progress.value = DriveBackupProgress(active = true, phase = "Building backup", percent = 5)
                val zip = buildLocalZip(includeMedia)
                try {
                    _progress.value = _progress.value.copy(phase = "Uploading to Drive", percent = 70)
                    val drive = gateway.driveFor(account)
                    val folderId = gateway.ensureBackupFolder(drive)
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    val remoteName = "simplemp3-backup-$stamp.zip"
                    val remote = gateway.uploadZip(drive, folderId, zip, remoteName)
                    appPreferences.setDriveLastBackupMs()
                    appPreferences.setDriveLastAccount(account.email)
                    _progress.value = DriveBackupProgress(
                        active = false,
                        phase = "Done",
                        percent = 100,
                        message = "Uploaded ${remote.name}"
                    )
                    Result.success(remote)
                } finally {
                    zip.delete()
                }
            } catch (e: Exception) {
                _progress.value = DriveBackupProgress(
                    active = false,
                    phase = "Error",
                    error = e.message ?: "Backup failed"
                )
                Result.failure(e)
            }
        }

    suspend fun restoreFromRemote(fileId: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (appPreferences.isDriveWifiOnly() &&
                    !NetworkWifi.isUnmeteredOrWifi(context)
                ) {
                    error("Connect to Wi‑Fi (or turn off Wi‑Fi only in Drive settings)")
                }
                val account = gateway.lastAccount()
                    ?: error("Sign in to Google first")
                val drive = gateway.driveFor(account)
                val dest = File(workDir(), "restore-${System.currentTimeMillis()}.zip")
                try {
                    _progress.value = DriveBackupProgress(
                        active = true,
                        phase = "Downloading $fileName",
                        percent = 10
                    )
                    gateway.downloadToFile(drive, fileId, dest)
                    _progress.value = _progress.value.copy(phase = "Restoring library", percent = 50)
                    val summary = restoreFromZip(dest)
                    _progress.value = DriveBackupProgress(
                        active = false,
                        phase = "Done",
                        percent = 100,
                        message = summary
                    )
                    Result.success(summary)
                } finally {
                    dest.delete()
                }
            } catch (e: Exception) {
                _progress.value = DriveBackupProgress(
                    active = false,
                    phase = "Error",
                    error = e.message ?: "Restore failed"
                )
                Result.failure(e)
            }
        }

    suspend fun deleteRemote(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val account = gateway.lastAccount() ?: error("Sign in first")
            gateway.deleteFile(gateway.driveFor(account), fileId)
            Unit
        }
    }

    private suspend fun buildLocalZip(includeMedia: Boolean): File {
        musicRepository.ensureSystemPlaylists()
        val allTracks = trackDao.getAllTracksOnce()
        val playlists = playlistDao.getAllPlaylistsOnce()

        val exportTracks = mutableListOf<BackupTrack>()
        val mediaFiles = mutableListOf<Pair<String, File>>() // zip path → file
        var mediaBytes = 0L

        for (t in allTracks) {
            val exportId = exportIdFor(t)
            var mediaPath: String? = null
            var mediaExt: String? = null
            if (includeMedia && t.isAppOwned && !t.isCold) {
                val f = fileFromUri(t.uri)
                if (f != null && f.isFile && f.length() > 0) {
                    val ext = f.extension.ifBlank { "mp3" }
                    val safe = exportId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
                    mediaPath = "media/$safe.$ext"
                    mediaExt = ext
                    mediaFiles += mediaPath to f
                    mediaBytes += f.length()
                }
            }
            exportTracks += BackupTrack(
                exportId = exportId,
                originalId = t.id,
                title = t.title,
                artist = t.artist,
                album = t.album,
                duration = t.duration,
                year = t.year,
                trackNumber = t.trackNumber,
                genre = t.genre,
                size = t.size,
                source = t.source,
                jellyfinId = t.jellyfinId,
                neverCompress = t.neverCompress,
                mediaPath = mediaPath,
                mediaExt = mediaExt
            )
        }

        val exportPlaylists = playlists.map { pl ->
            val trackIds = playlistDao.getTracksForPlaylistOnce(pl.id).map { exportIdFor(it) }
            BackupPlaylist(
                name = pl.name,
                description = pl.description,
                isSystem = pl.isSystem,
                systemType = pl.systemType,
                trackExportIds = trackIds
            )
        }

        val prefs = BackupPreferences(
            autoDriveModeOnCar = appPreferences.isAutoDriveModeOnCar(),
            autoResumeOnDrive = appPreferences.isAutoResumeOnDrive(),
            wifiOnlyDownloads = appPreferences.isWifiOnlyDownloads(),
            largeFileOptimize = appPreferences.isLargeFileOptimize(),
            largeFileColdPack = appPreferences.isLargeFileColdPack(),
            jellyfinEnabled = appPreferences.isJellyfinEnabled()
        )

        val payload = BackupPayload(
            createdAt = System.currentTimeMillis(),
            appVersionName = appVersionName(),
            includeMedia = includeMedia,
            preferences = prefs,
            tracks = exportTracks,
            playlists = exportPlaylists
        )

        val manifest = BackupManifest(
            createdAt = payload.createdAt,
            includeMedia = includeMedia,
            trackCount = exportTracks.size,
            playlistCount = exportPlaylists.size,
            mediaFileCount = mediaFiles.size,
            mediaBytes = mediaBytes,
            label = if (includeMedia) "metadata+media" else "metadata"
        )

        val zipFile = File(workDir(), "upload-${System.currentTimeMillis()}.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestAdapter.toJson(manifest).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("library.json"))
            zos.write(payloadAdapter.toJson(payload).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val totalMedia = mediaFiles.size.coerceAtLeast(1)
            mediaFiles.forEachIndexed { index, (path, file) ->
                _progress.value = _progress.value.copy(
                    phase = "Packing media ${index + 1}/$totalMedia",
                    percent = 10 + ((index * 50) / totalMedia)
                )
                zos.putNextEntry(ZipEntry(path))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zipFile
    }

    private suspend fun restoreFromZip(zipFile: File): String {
        musicRepository.ensureSystemPlaylists()

        var payload: BackupPayload? = null
        val mediaTemp = File(workDir(), "media-extract-${System.currentTimeMillis()}").also {
            it.mkdirs()
        }

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == "library.json" -> {
                            val text = zis.readBytes().toString(Charsets.UTF_8)
                            payload = payloadAdapter.fromJson(text)
                        }
                        name == "manifest.json" -> {
                            // optional; ignore for restore logic
                            zis.readBytes()
                        }
                        name.startsWith("media/") && !entry.isDirectory -> {
                            val out = File(mediaTemp, name.removePrefix("media/"))
                            out.parentFile?.mkdirs()
                            FileOutputStream(out).use { zis.copyTo(it) }
                        }
                        else -> zis.readBytes()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val data = payload ?: error("Invalid backup: missing library.json")
            if (data.version > BackupPayload.SCHEMA_VERSION) {
                error("Backup is from a newer app version (v${data.version})")
            }

            // Preferences (non-destructive toggles)
            appPreferences.setAutoDriveModeOnCar(data.preferences.autoDriveModeOnCar)
            appPreferences.setAutoResumeOnDrive(data.preferences.autoResumeOnDrive)
            appPreferences.setWifiOnlyDownloads(data.preferences.wifiOnlyDownloads)
            appPreferences.setLargeFileOptimize(data.preferences.largeFileOptimize)
            appPreferences.setLargeFileColdPack(data.preferences.largeFileColdPack)
            appPreferences.setJellyfinEnabled(data.preferences.jellyfinEnabled)

            val existing = trackDao.getAllTracksOnce()
            val byJellyfin = existing.mapNotNull { t -> t.jellyfinId?.let { it to t } }.toMap()
            val byOriginalId = existing.associateBy { it.id }
            val byTitleKey = existing.associateBy { matchKey(it.title, it.artist, it.duration) }

            val exportToLocalId = mutableMapOf<String, Long>()
            var mediaRestored = 0
            var tracksLinked = 0

            for ((index, bt) in data.tracks.withIndex()) {
                if (index % 25 == 0) {
                    _progress.value = _progress.value.copy(
                        phase = "Importing tracks ${index + 1}/${data.tracks.size}",
                        percent = 50 + (index * 35 / data.tracks.size.coerceAtLeast(1))
                    )
                }

                // Prefer existing match (avoid originalId for MediaStore — ids differ per device)
                val matched = bt.jellyfinId?.let { byJellyfin[it] }
                    ?: byOriginalId[bt.originalId]?.takeIf {
                        bt.source != TrackEntity.SOURCE_LOCAL && it.source == bt.source
                    }
                    ?: byTitleKey[matchKey(bt.title, bt.artist, bt.duration)]

                if (matched != null) {
                    exportToLocalId[bt.exportId] = matched.id
                    tracksLinked++
                    continue
                }

                // Restore media for app-owned sources
                val mediaFile = bt.mediaPath?.let { path ->
                    File(mediaTemp, path.removePrefix("media/")).takeIf { it.isFile }
                }
                if (mediaFile != null) {
                    val restored = importMediaFile(bt, mediaFile)
                    exportToLocalId[bt.exportId] = restored.id
                    mediaRestored++
                    tracksLinked++
                } else {
                    // Metadata-only: can't invent MediaStore files; skip orphan locals
                    if (bt.source == TrackEntity.SOURCE_LOCAL) {
                        // leave unmapped — playlist entry skipped
                    } else if (bt.jellyfinId != null) {
                        // recreate shell? without file it's not playable — skip
                    }
                }
            }

            // Playlists
            var playlistsTouched = 0
            for (bp in data.playlists) {
                val playlistId = if (bp.isSystem && bp.systemType != null) {
                    playlistDao.getSystemPlaylist(bp.systemType)?.id
                        ?: continue
                } else {
                    // Find by name or create
                    val existingPl = playlistDao.getAllPlaylistsOnce()
                        .firstOrNull { !it.isSystem && it.name.equals(bp.name, ignoreCase = true) }
                    existingPl?.id ?: musicRepository.createPlaylist(bp.name, bp.description)
                }
                playlistsTouched++
                for (exportId in bp.trackExportIds) {
                    val localId = exportToLocalId[exportId] ?: continue
                    playlistDao.addTrackToEnd(playlistId, localId)
                }
            }

            return buildString {
                append("Restored ")
                append(playlistsTouched)
                append(" playlist")
                if (playlistsTouched != 1) append("s")
                append(" · linked ")
                append(tracksLinked)
                append(" track")
                if (tracksLinked != 1) append("s")
                if (mediaRestored > 0) {
                    append(" · imported ")
                    append(mediaRestored)
                    append(" file")
                    if (mediaRestored != 1) append("s")
                }
            }
        } finally {
            mediaTemp.deleteRecursively()
        }
    }

    private suspend fun importMediaFile(bt: BackupTrack, file: File): TrackEntity {
        val source = when (bt.source) {
            TrackEntity.SOURCE_YOUTUBE -> TrackEntity.SOURCE_YOUTUBE
            TrackEntity.SOURCE_JELLYFIN -> TrackEntity.SOURCE_JELLYFIN
            else -> TrackEntity.SOURCE_LAN
        }
        val key = bt.jellyfinId?.takeIf { it.isNotBlank() }
            ?: bt.exportId.removePrefix("lan:").removePrefix("yt:").removePrefix("jf:")
        val id = when (source) {
            TrackEntity.SOURCE_YOUTUBE -> externalItemIdToTrackId("yt:$key")
            TrackEntity.SOURCE_JELLYFIN -> externalItemIdToTrackId(key)
            else -> externalItemIdToTrackId("lan:$key")
        }
        val dir = when (source) {
            TrackEntity.SOURCE_YOUTUBE -> File(context.filesDir, "offline/youtube/audio")
            TrackEntity.SOURCE_JELLYFIN -> File(context.filesDir, "offline/jellyfin/audio")
            else -> File(context.filesDir, "offline/lan/audio")
        }.also { it.mkdirs() }

        val ext = bt.mediaExt ?: file.extension.ifBlank { "mp3" }
        val dest = File(dir, "${key.take(64)}.$ext")
        file.copyTo(dest, overwrite = true)

        val track = TrackEntity(
            id = id,
            title = bt.title,
            artist = bt.artist,
            album = bt.album,
            albumId = bt.album.hashCode().toLong().and(0x7fffffff),
            artistId = bt.artist.hashCode().toLong().and(0x7fffffff),
            uri = Uri.fromFile(dest).toString(),
            duration = bt.duration,
            dateAdded = System.currentTimeMillis(),
            year = bt.year,
            trackNumber = bt.trackNumber,
            genre = bt.genre,
            size = dest.length(),
            source = source,
            jellyfinId = key,
            isOffline = true,
            neverCompress = bt.neverCompress
        )
        trackDao.insertTrack(track)

        // System playlists for source
        when (source) {
            TrackEntity.SOURCE_YOUTUBE ->
                playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_YOUTUBE)?.id?.let {
                    playlistDao.addTrackToEnd(it, track.id)
                }
            TrackEntity.SOURCE_JELLYFIN ->
                playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_JELLYFIN)?.id?.let {
                    playlistDao.addTrackToEnd(it, track.id)
                }
            TrackEntity.SOURCE_LAN ->
                playlistDao.getSystemPlaylist(PlaylistEntity.SYSTEM_LAN)?.id?.let {
                    playlistDao.addTrackToEnd(it, track.id)
                }
        }
        return track
    }

    private fun exportIdFor(t: TrackEntity): String {
        t.jellyfinId?.takeIf { it.isNotBlank() }?.let { jid ->
            return when (t.source) {
                TrackEntity.SOURCE_YOUTUBE -> "yt:$jid"
                TrackEntity.SOURCE_JELLYFIN -> "jf:$jid"
                TrackEntity.SOURCE_LAN -> "lan:$jid"
                else -> "ext:$jid"
            }
        }
        return "id:${t.id}:${t.title}|${t.artist}|${t.duration}"
    }

    private fun matchKey(title: String, artist: String, duration: Long): String =
        "${title.trim().lowercase()}|${artist.trim().lowercase()}|${duration / 1000}"

    private fun fileFromUri(uri: String): File? {
        return try {
            val path = Uri.parse(uri).path ?: return null
            File(path).takeIf { it.exists() }
        } catch (_: Exception) {
            null
        }
    }

    private fun appVersionName(): String? = runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).versionName
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0).versionName
        }
    }.getOrNull()
}
