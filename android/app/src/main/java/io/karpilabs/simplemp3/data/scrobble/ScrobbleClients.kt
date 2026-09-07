package io.karpilabs.simplemp3.data.scrobble

import android.util.Log
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private const val TAG = "Scrobble"

/**
 * ListenBrainz listen submission. Token auth only — the user pastes their token from
 * https://listenbrainz.org/settings/ so there is no OAuth dance to persist.
 */
class ListenBrainzClient(
    private val http: OkHttpClient,
) {
    private val endpoint = "https://api.listenbrainz.org/1/submit-listens"
    private val jsonMedia = "application/json".toMediaType()

    fun submit(
        token: String,
        scrobbles: List<PendingScrobble>,
        listenType: String,
    ): Boolean {
        if (scrobbles.isEmpty()) return true
        val payload = JSONArray()
        scrobbles.forEach { s ->
            val meta =
                JSONObject()
                    .put("artist_name", s.artist)
                    .put("track_name", s.track)
            if (s.album.isNotBlank()) meta.put("release_name", s.album)
            meta.put(
                "additional_info",
                JSONObject()
                    .put("duration_ms", s.durationSec * 1000)
                    .put("submission_client", "Simple MP3"),
            )
            val listen = JSONObject().put("track_metadata", meta)
            if (listenType != "playing_now") listen.put("listened_at", s.timestamp)
            payload.put(listen)
        }
        val body =
            JSONObject()
                .put("listen_type", listenType)
                .put("payload", payload)
                .toString()

        return runCatching {
            val request =
                Request
                    .Builder()
                    .url(endpoint)
                    .header("Authorization", "Token $token")
                    .post(body.toRequestBody(jsonMedia))
                    .build()
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse {
            Log.w(TAG, "ListenBrainz submit failed", it)
            false
        }
    }
}

/**
 * Last.fm scrobbling. Requires an app API key + secret (registered by the user at
 * https://www.last.fm/api/account/create) plus a session key obtained via
 * [getMobileSession] with the account's username + password.
 */
class LastFmClient(
    private val http: OkHttpClient,
) {
    private val endpoint = "https://ws.audioscrobbler.com/2.0/"

    /** @return the session key, or null on failure. */
    fun getMobileSession(
        apiKey: String,
        apiSecret: String,
        username: String,
        password: String,
    ): String? {
        val params =
            sortedMapOf(
                "method" to "auth.getMobileSession",
                "api_key" to apiKey,
                "username" to username,
                "password" to password,
            )
        val signed = signed(params, apiSecret)
        return runCatching {
            val form = FormBody.Builder().apply { signed.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder().url(endpoint).post(form).build()
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use null
                JSONObject(text).optJSONObject("session")?.optString("key").takeIf { !it.isNullOrBlank() }
            }
        }.getOrElse {
            Log.w(TAG, "Last.fm getMobileSession failed", it)
            null
        }
    }

    fun updateNowPlaying(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        scrobble: PendingScrobble,
    ): Boolean =
        post(
            base =
                sortedMapOf(
                    "method" to "track.updateNowPlaying",
                    "api_key" to apiKey,
                    "sk" to sessionKey,
                    "artist" to scrobble.artist,
                    "track" to scrobble.track,
                    "album" to scrobble.album,
                    "duration" to scrobble.durationSec.toString(),
                ),
            apiSecret = apiSecret,
        )

    fun scrobble(
        apiKey: String,
        apiSecret: String,
        sessionKey: String,
        scrobbles: List<PendingScrobble>,
    ): Boolean {
        if (scrobbles.isEmpty()) return true
        val params =
            sortedMapOf(
                "method" to "track.scrobble",
                "api_key" to apiKey,
                "sk" to sessionKey,
            )
        scrobbles.forEachIndexed { i, s ->
            params["artist[$i]"] = s.artist
            params["track[$i]"] = s.track
            params["timestamp[$i]"] = s.timestamp.toString()
            if (s.album.isNotBlank()) params["album[$i]"] = s.album
            if (s.durationSec > 0) params["duration[$i]"] = s.durationSec.toString()
        }
        return post(params, apiSecret)
    }

    private fun post(
        base: Map<String, String>,
        apiSecret: String,
    ): Boolean {
        val cleaned = base.filterValues { it.isNotBlank() }.toSortedMap()
        val signed = signed(cleaned, apiSecret)
        return runCatching {
            val form = FormBody.Builder().apply { signed.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder().url(endpoint).post(form).build()
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse {
            Log.w(TAG, "Last.fm POST failed", it)
            false
        }
    }

    /** Add api_sig (md5 of sorted key+value pairs + secret) and format=json. */
    private fun signed(
        params: Map<String, String>,
        apiSecret: String,
    ): Map<String, String> {
        val sig =
            buildString {
                params.toSortedMap().forEach { (k, v) ->
                    append(k)
                    append(v)
                }
                append(apiSecret)
            }.let(::md5)
        return params.toMutableMap().apply {
            put("api_sig", sig)
            put("format", "json")
        }
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
