package io.karpilabs.simplemp3.data.scrobble

import io.karpilabs.simplemp3.data.prefs.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates "now playing" updates and threshold-based scrobbles for the configured
 * provider, with an offline queue that survives restarts and flushes on the next play.
 *
 * Scrobble rules follow Last.fm / ListenBrainz guidance: a track counts once it has
 * played for at least 4 minutes or half its length (whichever is shorter), and only if
 * it is longer than 30 seconds.
 */
@Singleton
class ScrobbleManager
    @Inject
    constructor(
        private val http: OkHttpClient,
        private val appPreferences: AppPreferences,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val listenBrainz = ListenBrainzClient(http)
        private val lastfm = LastFmClient(http)
        private val queueMutex = Mutex()

        // Per-track state, reset on each track start.
        @Volatile private var current: PendingScrobble? = null

        @Volatile private var scrobbledCurrent = false

        /** Call when a new track begins playing. Sends "now playing" and arms the scrobble. */
        fun onTrackStarted(
            artist: String,
            track: String,
            album: String,
            durationMs: Long,
        ) {
            if (track.isBlank() || artist.isBlank()) {
                current = null
                return
            }
            val pending =
                PendingScrobble(
                    artist = artist,
                    track = track,
                    album = album,
                    durationSec = (durationMs / 1000L).toInt(),
                    timestamp = System.currentTimeMillis() / 1000L,
                )
            current = pending
            scrobbledCurrent = false
            scope.launch {
                val config = appPreferences.getScrobbleConfig()
                if (!config.isReady) return@launch
                sendNowPlaying(config, pending)
                // Opportunistically flush anything stuck from a previous session.
                flushQueue(config)
            }
        }

        /** Call periodically with the current playback position. */
        fun onProgress(positionMs: Long) {
            val pending = current ?: return
            if (scrobbledCurrent) return
            val durationMs = pending.durationSec * 1000L
            if (durationMs < 30_000L) return
            val threshold = minOf(durationMs / 2, 240_000L)
            if (positionMs < threshold) return
            scrobbledCurrent = true
            scope.launch {
                val config = appPreferences.getScrobbleConfig()
                if (!config.isReady) return@launch
                enqueue(pending)
                flushQueue(config)
            }
        }

        /** Validate + persist a Last.fm session from username/password. */
        suspend fun loginLastfm(
            apiKey: String,
            apiSecret: String,
            username: String,
            password: String,
        ): Boolean {
            appPreferences.setLastfmCredentials(apiKey, apiSecret)
            val sk = lastfm.getMobileSession(apiKey, apiSecret, username, password) ?: return false
            appPreferences.setLastfmSession(username, sk)
            return true
        }

        private fun sendNowPlaying(
            config: ScrobbleConfig,
            pending: PendingScrobble,
        ) {
            when {
                config.isListenBrainz ->
                    listenBrainz.submit(config.listenBrainzToken, listOf(pending), "playing_now")
                config.isLastfm ->
                    lastfm.updateNowPlaying(
                        config.lastfmApiKey,
                        config.lastfmApiSecret,
                        config.lastfmSessionKey,
                        pending,
                    )
            }
        }

        private suspend fun enqueue(pending: PendingScrobble) {
            queueMutex.withLock {
                val queue = decode(appPreferences.getScrobbleQueue()).toMutableList()
                queue += pending
                // Cap so a long offline stretch can't grow unbounded.
                val capped = if (queue.size > 200) queue.takeLast(200) else queue
                appPreferences.setScrobbleQueue(encode(capped))
            }
        }

        private suspend fun flushQueue(config: ScrobbleConfig) {
            queueMutex.withLock {
                val queue = decode(appPreferences.getScrobbleQueue())
                if (queue.isEmpty()) return
                val ok =
                    when {
                        config.isListenBrainz ->
                            listenBrainz.submit(config.listenBrainzToken, queue, "single")
                        config.isLastfm ->
                            lastfm.scrobble(
                                config.lastfmApiKey,
                                config.lastfmApiSecret,
                                config.lastfmSessionKey,
                                queue,
                            )
                        else -> false
                    }
                if (ok) appPreferences.setScrobbleQueue("")
            }
        }

        private fun encode(list: List<PendingScrobble>): String {
            val arr = JSONArray()
            list.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("artist", s.artist)
                        .put("track", s.track)
                        .put("album", s.album)
                        .put("durationSec", s.durationSec)
                        .put("timestamp", s.timestamp),
                )
            }
            return arr.toString()
        }

        private fun decode(json: String): List<PendingScrobble> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    PendingScrobble(
                        artist = o.getString("artist"),
                        track = o.getString("track"),
                        album = o.optString("album"),
                        durationSec = o.optInt("durationSec"),
                        timestamp = o.optLong("timestamp"),
                    )
                }
            }.getOrDefault(emptyList())
        }
    }
