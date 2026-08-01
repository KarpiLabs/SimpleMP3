package io.karpilabs.simplemp3.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore by preferencesDataStore(name = "app_prefs")

data class ResumeSnapshot(
    val trackIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?
) {
    val hasSession: Boolean get() = trackIds.isNotEmpty()
}

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_LIBRARY_SCAN_MS = longPreferencesKey("last_library_scan_ms")
        val DRIVE_MODE = booleanPreferencesKey("drive_mode")
        /** When Android Auto / Automotive connects, turn Drive Mode on. */
        val AUTO_DRIVE_MODE_ON_CAR = booleanPreferencesKey("auto_drive_mode_on_car")
        /**
         * When the car connects or Drive mode is turned on, restore the last
         * queue + position and start playback (if nothing is already playing).
         */
        val AUTO_RESUME_ON_DRIVE = booleanPreferencesKey("auto_resume_on_drive")
        val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
        /** Re-encode very large/high-bitrate app-owned audio to save space. */
        val LARGE_FILE_OPTIMIZE = booleanPreferencesKey("large_file_optimize")
        /** Gzip-pack large idle files; thaw transparently on play. */
        val LARGE_FILE_COLD_PACK = booleanPreferencesKey("large_file_cold_pack")
        /**
         * Optional Jellyfin integration. Default off — not everyone runs a server.
         * When false, Jellyfin UI entry points are hidden.
         */
        val JELLYFIN_ENABLED = booleanPreferencesKey("jellyfin_enabled")
        val RESUME_IDS = stringPreferencesKey("resume_track_ids")
        val RESUME_INDEX = intPreferencesKey("resume_index")
        val RESUME_POSITION = longPreferencesKey("resume_position_ms")
        val RESUME_TITLE = stringPreferencesKey("resume_title")
        val RESUME_ARTIST = stringPreferencesKey("resume_artist")
        val RESUME_ARTWORK = stringPreferencesKey("resume_artwork")
        /** Google Drive: include app-owned audio files in backups (large). */
        val DRIVE_INCLUDE_MEDIA = booleanPreferencesKey("drive_include_media")
        /** Google Drive: require Wi‑Fi for backup/restore (esp. with media). */
        val DRIVE_WIFI_ONLY = booleanPreferencesKey("drive_wifi_only")
        val DRIVE_LAST_ACCOUNT = stringPreferencesKey("drive_last_account")
        val DRIVE_LAST_BACKUP_MS = longPreferencesKey("drive_last_backup_ms")
    }

    suspend fun getLastLibraryScanMs(): Long =
        context.appDataStore.data.map { it[Keys.LAST_LIBRARY_SCAN_MS] ?: 0L }.first()

    suspend fun setLastLibraryScanMs(ms: Long = System.currentTimeMillis()) {
        context.appDataStore.edit { it[Keys.LAST_LIBRARY_SCAN_MS] = ms }
    }

    suspend fun shouldSkipScan(maxAgeMs: Long = 6 * 60 * 60 * 1000L): Boolean {
        val last = getLastLibraryScanMs()
        return last > 0L && System.currentTimeMillis() - last < maxAgeMs
    }

    val driveModeFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.DRIVE_MODE] ?: false
    }

    suspend fun setDriveMode(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DRIVE_MODE] = enabled }
    }

    suspend fun isDriveMode(): Boolean = driveModeFlow.first()

    /** Default on: car connect enables Drive Mode for big on-phone controls. */
    val autoDriveModeOnCarFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.AUTO_DRIVE_MODE_ON_CAR] ?: true
    }

    suspend fun setAutoDriveModeOnCar(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.AUTO_DRIVE_MODE_ON_CAR] = enabled }
    }

    suspend fun isAutoDriveModeOnCar(): Boolean = autoDriveModeOnCarFlow.first()

    /** Default on: pick up the last session when driving starts. */
    val autoResumeOnDriveFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.AUTO_RESUME_ON_DRIVE] ?: true
    }

    suspend fun setAutoResumeOnDrive(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.AUTO_RESUME_ON_DRIVE] = enabled }
    }

    suspend fun isAutoResumeOnDrive(): Boolean = autoResumeOnDriveFlow.first()

    val wifiOnlyDownloadsFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.WIFI_ONLY_DOWNLOADS] ?: true
    }

    suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
    }

    suspend fun isWifiOnlyDownloads(): Boolean = wifiOnlyDownloadsFlow.first()

    /** Default on: one-time lower-bitrate re-encode for large movie-length files. */
    val largeFileOptimizeFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.LARGE_FILE_OPTIMIZE] ?: true
    }

    suspend fun setLargeFileOptimize(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.LARGE_FILE_OPTIMIZE] = enabled }
    }

    suspend fun isLargeFileOptimize(): Boolean = largeFileOptimizeFlow.first()

    /** Default on: gzip cold-pack large idle files when not playing. */
    val largeFileColdPackFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.LARGE_FILE_COLD_PACK] ?: true
    }

    suspend fun setLargeFileColdPack(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.LARGE_FILE_COLD_PACK] = enabled }
    }

    suspend fun isLargeFileColdPack(): Boolean = largeFileColdPackFlow.first()

    /** Default off: hide Jellyfin sync UI until the user opts in. */
    val jellyfinEnabledFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.JELLYFIN_ENABLED] ?: false
    }

    suspend fun setJellyfinEnabled(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.JELLYFIN_ENABLED] = enabled }
    }

    suspend fun isJellyfinEnabled(): Boolean = jellyfinEnabledFlow.first()

    val resumeFlow: Flow<ResumeSnapshot?> = context.appDataStore.data.map { prefs ->
        val idsRaw = prefs[Keys.RESUME_IDS].orEmpty()
        if (idsRaw.isBlank()) return@map null
        val ids = idsRaw.split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return@map null
        ResumeSnapshot(
            trackIds = ids,
            index = (prefs[Keys.RESUME_INDEX] ?: 0).coerceIn(0, ids.lastIndex),
            positionMs = prefs[Keys.RESUME_POSITION] ?: 0L,
            title = prefs[Keys.RESUME_TITLE].orEmpty(),
            artist = prefs[Keys.RESUME_ARTIST].orEmpty(),
            artworkUri = prefs[Keys.RESUME_ARTWORK]
        )
    }

    suspend fun getResume(): ResumeSnapshot? = resumeFlow.first()

    suspend fun saveResume(snapshot: ResumeSnapshot) {
        if (!snapshot.hasSession) {
            clearResume()
            return
        }
        // Cap queue persistence for speed / size
        val capped = snapshot.trackIds.take(200)
        val index = snapshot.index.coerceIn(0, capped.lastIndex.coerceAtLeast(0))
        context.appDataStore.edit { prefs ->
            prefs[Keys.RESUME_IDS] = capped.joinToString(",")
            prefs[Keys.RESUME_INDEX] = index
            prefs[Keys.RESUME_POSITION] = snapshot.positionMs.coerceAtLeast(0L)
            prefs[Keys.RESUME_TITLE] = snapshot.title
            prefs[Keys.RESUME_ARTIST] = snapshot.artist
            if (snapshot.artworkUri != null) {
                prefs[Keys.RESUME_ARTWORK] = snapshot.artworkUri
            } else {
                prefs.remove(Keys.RESUME_ARTWORK)
            }
        }
    }

    suspend fun clearResume() {
        context.appDataStore.edit { prefs ->
            prefs.remove(Keys.RESUME_IDS)
            prefs.remove(Keys.RESUME_INDEX)
            prefs.remove(Keys.RESUME_POSITION)
            prefs.remove(Keys.RESUME_TITLE)
            prefs.remove(Keys.RESUME_ARTIST)
            prefs.remove(Keys.RESUME_ARTWORK)
        }
    }

    // ── Google Drive backup ───────────────────────────────────

    /** Default off: metadata-only backups stay small. */
    val driveIncludeMediaFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.DRIVE_INCLUDE_MEDIA] ?: false
    }

    suspend fun setDriveIncludeMedia(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DRIVE_INCLUDE_MEDIA] = enabled }
    }

    suspend fun isDriveIncludeMedia(): Boolean = driveIncludeMediaFlow.first()

    /** Default on: avoid surprise cellular uploads. */
    val driveWifiOnlyFlow: Flow<Boolean> = context.appDataStore.data.map {
        it[Keys.DRIVE_WIFI_ONLY] ?: true
    }

    suspend fun setDriveWifiOnly(enabled: Boolean) {
        context.appDataStore.edit { it[Keys.DRIVE_WIFI_ONLY] = enabled }
    }

    suspend fun isDriveWifiOnly(): Boolean = driveWifiOnlyFlow.first()

    val driveLastAccountFlow: Flow<String?> = context.appDataStore.data.map {
        it[Keys.DRIVE_LAST_ACCOUNT]
    }

    suspend fun setDriveLastAccount(email: String?) {
        context.appDataStore.edit { prefs ->
            if (email.isNullOrBlank()) prefs.remove(Keys.DRIVE_LAST_ACCOUNT)
            else prefs[Keys.DRIVE_LAST_ACCOUNT] = email
        }
    }

    val driveLastBackupMsFlow: Flow<Long> = context.appDataStore.data.map {
        it[Keys.DRIVE_LAST_BACKUP_MS] ?: 0L
    }

    suspend fun setDriveLastBackupMs(ms: Long = System.currentTimeMillis()) {
        context.appDataStore.edit { it[Keys.DRIVE_LAST_BACKUP_MS] = ms }
    }
}
