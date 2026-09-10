package io.karpilabs.simplemp3.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.karpilabs.simplemp3.data.scrobble.ScrobbleConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appDataStore by preferencesDataStore(name = "app_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Playback buffer size. Bigger buffers rebuffer less on flaky car networks / slow
 * storage at the cost of RAM and a longer initial load. Values feed ExoPlayer's
 * DefaultLoadControl (Android) and AVPlayerItem.preferredForwardBufferDuration (iOS).
 */
enum class BufferProfile(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val forPlaybackMs: Int,
    val forPlaybackAfterRebufferMs: Int,
) {
    SMALL(15_000, 60_000, 1_500, 3_000),
    BALANCED(30_000, 180_000, 2_000, 5_000),
    LARGE(60_000, 600_000, 2_500, 8_000),
}

data class ResumeSnapshot(
    val trackIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
) {
    val hasSession: Boolean get() = trackIds.isNotEmpty()
}

@Singleton
class AppPreferences
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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

            /**
             * When Android Auto / Automotive disconnects, pause playback so audio
             * does not keep running on the phone.
             */
            val PAUSE_ON_CAR_DISCONNECT = booleanPreferencesKey("pause_on_car_disconnect")
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

            /** "Pick up where you left off" — show/use the resume snapshot outside Drive mode. */
            val RESUME_ENABLED = booleanPreferencesKey("resume_enabled")
            val RESUME_IDS = stringPreferencesKey("resume_track_ids")
            val RESUME_INDEX = intPreferencesKey("resume_index")
            val RESUME_POSITION = longPreferencesKey("resume_position_ms")
            val RESUME_TITLE = stringPreferencesKey("resume_title")
            val RESUME_ARTIST = stringPreferencesKey("resume_artist")
            val RESUME_ARTWORK = stringPreferencesKey("resume_artwork")

            /**
             * Comma-separated library folder roots (relative paths). Empty = scan all music folders.
             * Example: "Music,Download/Audio"
             */
            val LIBRARY_FOLDER_ROOTS = stringPreferencesKey("library_folder_roots")

            /**
             * Persistable SAF tree URIs (SD card / USB / extra folders MediaStore missed).
             * Unit-separator joined.
             */
            val SAF_TREE_URIS = stringPreferencesKey("saf_tree_uris")

            /** SYSTEM / LIGHT / DARK — see [ThemeMode]. */
            val THEME_MODE = stringPreferencesKey("theme_mode")

            /** SMALL / BALANCED / LARGE playback buffer — see [BufferProfile]. */
            val BUFFER_PROFILE = stringPreferencesKey("buffer_profile")

            /** Even out loudness across tracks using ReplayGain tags. */
            val NORMALIZE_VOLUME = booleanPreferencesKey("normalize_volume")

            /** Extra gain (dB) applied on top of ReplayGain when normalizing. */
            val NORMALIZE_PREAMP_DB = intPreferencesKey("normalize_preamp_db")

            // ── Scrobbling ──────────────────────────────────────────
            /** none | listenbrainz | lastfm */
            val SCROBBLE_PROVIDER = stringPreferencesKey("scrobble_provider")
            val SCROBBLE_ENABLED = booleanPreferencesKey("scrobble_enabled")
            val LISTENBRAINZ_TOKEN = stringPreferencesKey("listenbrainz_token")
            val LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
            val LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
            val LASTFM_API_KEY = stringPreferencesKey("lastfm_api_key")
            val LASTFM_API_SECRET = stringPreferencesKey("lastfm_api_secret")

            /** JSON-encoded queue of scrobbles awaiting network. */
            val SCROBBLE_QUEUE = stringPreferencesKey("scrobble_queue")
        }

        val themeModeFlow: Flow<ThemeMode> =
            context.appDataStore.data.map { prefs ->
                ThemeMode.entries.firstOrNull { it.name == prefs[Keys.THEME_MODE] } ?: ThemeMode.SYSTEM
            }

        suspend fun getThemeMode(): ThemeMode = themeModeFlow.first()

        suspend fun setThemeMode(mode: ThemeMode) {
            context.appDataStore.edit { it[Keys.THEME_MODE] = mode.name }
        }

        val bufferProfileFlow: Flow<BufferProfile> =
            context.appDataStore.data.map { prefs ->
                BufferProfile.entries.firstOrNull { it.name == prefs[Keys.BUFFER_PROFILE] }
                    ?: BufferProfile.BALANCED
            }

        suspend fun getBufferProfile(): BufferProfile = bufferProfileFlow.first()

        suspend fun setBufferProfile(profile: BufferProfile) {
            context.appDataStore.edit { it[Keys.BUFFER_PROFILE] = profile.name }
        }

        /** Default off: even out loudness across tracks using ReplayGain tags. */
        val normalizeVolumeFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.NORMALIZE_VOLUME] ?: false
            }

        suspend fun setNormalizeVolume(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.NORMALIZE_VOLUME] = enabled }
        }

        suspend fun isNormalizeVolume(): Boolean = normalizeVolumeFlow.first()

        /** Extra gain (dB) applied on top of ReplayGain; clamped to [-12, 12]. Default 0. */
        val normalizePreampDbFlow: Flow<Int> =
            context.appDataStore.data.map {
                (it[Keys.NORMALIZE_PREAMP_DB] ?: 0).coerceIn(-12, 12)
            }

        suspend fun setNormalizePreampDb(db: Int) {
            context.appDataStore.edit { it[Keys.NORMALIZE_PREAMP_DB] = db.coerceIn(-12, 12) }
        }

        suspend fun getNormalizePreampDb(): Int = normalizePreampDbFlow.first()

        // ── Scrobbling ─────────────────────────────────────────────

        val scrobbleConfigFlow: Flow<ScrobbleConfig> =
            context.appDataStore.data.map { prefs ->
                ScrobbleConfig(
                    provider = prefs[Keys.SCROBBLE_PROVIDER] ?: ScrobbleConfig.PROVIDER_NONE,
                    enabled = prefs[Keys.SCROBBLE_ENABLED] ?: false,
                    listenBrainzToken = SecretCipher.decrypt(prefs[Keys.LISTENBRAINZ_TOKEN].orEmpty()),
                    lastfmSessionKey = SecretCipher.decrypt(prefs[Keys.LASTFM_SESSION_KEY].orEmpty()),
                    lastfmUsername = prefs[Keys.LASTFM_USERNAME].orEmpty(),
                    lastfmApiKey = prefs[Keys.LASTFM_API_KEY].orEmpty(),
                    lastfmApiSecret = SecretCipher.decrypt(prefs[Keys.LASTFM_API_SECRET].orEmpty()),
                )
            }

        suspend fun getScrobbleConfig(): ScrobbleConfig = scrobbleConfigFlow.first()

        suspend fun setScrobbleProvider(provider: String) {
            context.appDataStore.edit { it[Keys.SCROBBLE_PROVIDER] = provider }
        }

        suspend fun setScrobbleEnabled(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.SCROBBLE_ENABLED] = enabled }
        }

        suspend fun setListenBrainzToken(token: String) {
            context.appDataStore.edit { it[Keys.LISTENBRAINZ_TOKEN] = SecretCipher.encrypt(token.trim()) }
        }

        suspend fun setLastfmSession(
            username: String,
            sessionKey: String,
        ) {
            context.appDataStore.edit {
                it[Keys.LASTFM_USERNAME] = username
                it[Keys.LASTFM_SESSION_KEY] = SecretCipher.encrypt(sessionKey)
            }
        }

        suspend fun setLastfmCredentials(
            apiKey: String,
            apiSecret: String,
        ) {
            context.appDataStore.edit {
                it[Keys.LASTFM_API_KEY] = apiKey.trim()
                it[Keys.LASTFM_API_SECRET] = SecretCipher.encrypt(apiSecret.trim())
            }
        }

        suspend fun clearLastfmSession() {
            context.appDataStore.edit {
                it.remove(Keys.LASTFM_SESSION_KEY)
                it.remove(Keys.LASTFM_USERNAME)
            }
        }

        suspend fun getScrobbleQueue(): String = context.appDataStore.data.map { it[Keys.SCROBBLE_QUEUE].orEmpty() }.first()

        suspend fun setScrobbleQueue(json: String) {
            context.appDataStore.edit {
                if (json.isBlank()) it.remove(Keys.SCROBBLE_QUEUE) else it[Keys.SCROBBLE_QUEUE] = json
            }
        }

        /**
         * Synchronous read for constructing the ExoPlayer singleton at DI time (before
         * any coroutine scope exists). Changes to the profile apply on next app start.
         */
        fun getBufferProfileBlocking(): BufferProfile =
            kotlinx.coroutines.runBlocking { getBufferProfile() }

        suspend fun getLastLibraryScanMs(): Long =
            context.appDataStore.data
                .map { it[Keys.LAST_LIBRARY_SCAN_MS] ?: 0L }
                .first()

        suspend fun setLastLibraryScanMs(ms: Long = System.currentTimeMillis()) {
            context.appDataStore.edit { it[Keys.LAST_LIBRARY_SCAN_MS] = ms }
        }

        suspend fun shouldSkipScan(maxAgeMs: Long = 6 * 60 * 60 * 1000L): Boolean {
            val last = getLastLibraryScanMs()
            return last > 0L && System.currentTimeMillis() - last < maxAgeMs
        }

        val driveModeFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.DRIVE_MODE] ?: false
            }

        suspend fun setDriveMode(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.DRIVE_MODE] = enabled }
        }

        suspend fun isDriveMode(): Boolean = driveModeFlow.first()

        /** Default on: car connect enables Drive Mode for big on-phone controls. */
        val autoDriveModeOnCarFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.AUTO_DRIVE_MODE_ON_CAR] ?: true
            }

        suspend fun setAutoDriveModeOnCar(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.AUTO_DRIVE_MODE_ON_CAR] = enabled }
        }

        suspend fun isAutoDriveModeOnCar(): Boolean = autoDriveModeOnCarFlow.first()

        /** Default on: pick up the last session when driving starts. */
        val autoResumeOnDriveFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.AUTO_RESUME_ON_DRIVE] ?: true
            }

        suspend fun setAutoResumeOnDrive(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.AUTO_RESUME_ON_DRIVE] = enabled }
        }

        suspend fun isAutoResumeOnDrive(): Boolean = autoResumeOnDriveFlow.first()

        /** Default on: pause when Android Auto / the car disconnects. */
        val pauseOnCarDisconnectFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.PAUSE_ON_CAR_DISCONNECT] ?: true
            }

        suspend fun setPauseOnCarDisconnect(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.PAUSE_ON_CAR_DISCONNECT] = enabled }
        }

        suspend fun isPauseOnCarDisconnect(): Boolean = pauseOnCarDisconnectFlow.first()

        val wifiOnlyDownloadsFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.WIFI_ONLY_DOWNLOADS] ?: true
            }

        suspend fun setWifiOnlyDownloads(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
        }

        suspend fun isWifiOnlyDownloads(): Boolean = wifiOnlyDownloadsFlow.first()

        /** Default on: one-time lower-bitrate re-encode for large movie-length files. */
        val largeFileOptimizeFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.LARGE_FILE_OPTIMIZE] ?: true
            }

        suspend fun setLargeFileOptimize(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.LARGE_FILE_OPTIMIZE] = enabled }
        }

        suspend fun isLargeFileOptimize(): Boolean = largeFileOptimizeFlow.first()

        /** Default on: gzip cold-pack large idle files when not playing. */
        val largeFileColdPackFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.LARGE_FILE_COLD_PACK] ?: true
            }

        suspend fun setLargeFileColdPack(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.LARGE_FILE_COLD_PACK] = enabled }
        }

        suspend fun isLargeFileColdPack(): Boolean = largeFileColdPackFlow.first()

        /** Default off: hide Jellyfin sync UI until the user opts in. */
        val jellyfinEnabledFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.JELLYFIN_ENABLED] ?: false
            }

        suspend fun setJellyfinEnabled(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.JELLYFIN_ENABLED] = enabled }
        }

        suspend fun isJellyfinEnabled(): Boolean = jellyfinEnabledFlow.first()

        /** Default on: offer "Pick up where you left off" on Home. */
        val resumeEnabledFlow: Flow<Boolean> =
            context.appDataStore.data.map {
                it[Keys.RESUME_ENABLED] ?: true
            }

        suspend fun setResumeEnabled(enabled: Boolean) {
            context.appDataStore.edit { it[Keys.RESUME_ENABLED] = enabled }
        }

        suspend fun isResumeEnabled(): Boolean = resumeEnabledFlow.first()

        val resumeFlow: Flow<ResumeSnapshot?> =
            context.appDataStore.data.map { prefs ->
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
                    artworkUri = prefs[Keys.RESUME_ARTWORK],
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

        // ── Library folder roots (empty = all folders) ─────────────────

        val libraryFolderRootsFlow: Flow<Set<String>> =
            context.appDataStore.data.map { prefs ->
                parseFolderRoots(prefs[Keys.LIBRARY_FOLDER_ROOTS])
            }

        suspend fun getLibraryFolderRoots(): Set<String> = libraryFolderRootsFlow.first()

        suspend fun setLibraryFolderRoots(roots: Set<String>) {
            val cleaned =
                roots
                    .map { it.trim().trim('/').replace('\\', '/') }
                    .filter { it.isNotBlank() }
                    .toSortedSet()
            context.appDataStore.edit { prefs ->
                if (cleaned.isEmpty()) {
                    prefs.remove(Keys.LIBRARY_FOLDER_ROOTS)
                } else {
                    prefs[Keys.LIBRARY_FOLDER_ROOTS] = cleaned.joinToString("\u001e")
                }
            }
        }

        suspend fun clearLibraryFolderRoots() = setLibraryFolderRoots(emptySet())

        val safTreeUrisFlow: Flow<List<String>> =
            context.appDataStore.data.map { prefs ->
                parseSafTreeUris(prefs[Keys.SAF_TREE_URIS])
            }

        suspend fun getSafTreeUris(): List<String> = safTreeUrisFlow.first()

        suspend fun setSafTreeUris(uris: List<String>) {
            val cleaned =
                uris
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
            context.appDataStore.edit { prefs ->
                if (cleaned.isEmpty()) {
                    prefs.remove(Keys.SAF_TREE_URIS)
                } else {
                    prefs[Keys.SAF_TREE_URIS] = cleaned.joinToString("\u001e")
                }
            }
        }

        suspend fun addSafTreeUri(uri: String) {
            val trimmed = uri.trim()
            if (trimmed.isEmpty()) return
            val current = getSafTreeUris()
            if (current.any { it == trimmed }) return
            setSafTreeUris(current + trimmed)
        }

        suspend fun removeSafTreeUri(uri: String) {
            setSafTreeUris(getSafTreeUris().filter { it != uri })
        }

        private fun parseSafTreeUris(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val parts =
                if (raw.contains('\u001e')) {
                    raw.split('\u001e')
                } else {
                    raw.split(',')
                }
            return parts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        }

        private fun parseFolderRoots(raw: String?): Set<String> {
            if (raw.isNullOrBlank()) return emptySet()
            // Prefer unit-separator (paths can contain commas); fall back to comma for older prefs.
            val parts =
                if (raw.contains('\u001e')) {
                    raw.split('\u001e')
                } else {
                    raw.split(',')
                }
            return parts
                .map { it.trim().trim('/').replace('\\', '/') }
                .filter { it.isNotBlank() }
                .toSet()
        }
    }
