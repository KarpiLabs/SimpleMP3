package io.karpilabs.simplemp3.data.drive

import com.squareup.moshi.JsonClass

/** On-disk / in-zip schema for Simple MP3 backups. */
@JsonClass(generateAdapter = true)
data class BackupPayload(
    val version: Int = SCHEMA_VERSION,
    val createdAt: Long = System.currentTimeMillis(),
    val appVersionName: String? = null,
    val includeMedia: Boolean = false,
    val preferences: BackupPreferences = BackupPreferences(),
    val tracks: List<BackupTrack> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList()
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@JsonClass(generateAdapter = true)
data class BackupPreferences(
    val autoDriveModeOnCar: Boolean = true,
    val autoResumeOnDrive: Boolean = true,
    val wifiOnlyDownloads: Boolean = true,
    val largeFileOptimize: Boolean = true,
    val largeFileColdPack: Boolean = true,
    val jellyfinEnabled: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BackupTrack(
    /** Stable id across devices: source-specific key. */
    val exportId: String,
    val originalId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long = 0L,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val genre: String? = null,
    val size: Long = 0L,
    val source: String,
    val jellyfinId: String? = null,
    val neverCompress: Boolean = false,
    /** Relative zip path when media was included, e.g. media/lan_abc.mp3 */
    val mediaPath: String? = null,
    val mediaExt: String? = null
)

@JsonClass(generateAdapter = true)
data class BackupPlaylist(
    val name: String,
    val description: String = "",
    val isSystem: Boolean = false,
    val systemType: String? = null,
    val trackExportIds: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BackupManifest(
    val version: Int = BackupPayload.SCHEMA_VERSION,
    val createdAt: Long,
    val includeMedia: Boolean,
    val trackCount: Int,
    val playlistCount: Int,
    val mediaFileCount: Int = 0,
    val mediaBytes: Long = 0L,
    val label: String = ""
)

data class DriveBackupRemote(
    val fileId: String,
    val name: String,
    val modifiedTimeMs: Long,
    val sizeBytes: Long
)

data class MediaBackupEstimate(
    val trackCount: Int,
    val totalBytes: Long
)
