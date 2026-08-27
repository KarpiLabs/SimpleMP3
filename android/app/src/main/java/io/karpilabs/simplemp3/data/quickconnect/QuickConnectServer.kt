package io.karpilabs.simplemp3.data.quickconnect

import fi.iki.elonen.NanoHTTPD
import io.karpilabs.simplemp3.data.local.TrackEntity
import io.karpilabs.simplemp3.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class QuickConnectEvent(
    val timeMs: Long = System.currentTimeMillis(),
    val message: String,
)

data class QuickConnectSession(
    val running: Boolean = false,
    val port: Int = 0,
    val ip: String? = null,
    val accessCode: String = "",
    /** Base portal URL, e.g. http://192.168.1.20:8765 */
    val url: String? = null,
    /**
     * URL encoded in the on-screen QR — includes access code so a scan can unlock
     * without retyping (http://…/?code=123456).
     */
    val qrUrl: String? = null,
    val error: String? = null,
    val events: List<QuickConnectEvent> = emptyList(),
    /** True once repeated failed access codes have permanently disabled this portal instance. */
    val lockedOut: Boolean = false,
)

/**
 * Ephemeral LAN HTTP portal for desktop upload / playlist management.
 * Started only while the Quick Connect screen is visible.
 */
@Singleton
class QuickConnectServer
    @Inject
    constructor(
        private val musicRepository: MusicRepository,
        private val lanImportManager: LanImportManager,
    ) {
        private val lock = Any()
        private var http: HttpEngine? = null
        private val _session = MutableStateFlow(QuickConnectSession())
        val session: StateFlow<QuickConnectSession> = _session.asStateFlow()

        private val events = CopyOnWriteArrayList<QuickConnectEvent>()

        fun start(preferredPort: Int = DEFAULT_PORT): QuickConnectSession {
            synchronized(lock) {
                if (http?.isRunning == true) {
                    return _session.value
                }
                stopInternal()
                val ip = NetworkAddresses.localIpv4()
                val code = generateAccessCode()
                val token = UUID.randomUUID().toString().replace("-", "")
                var lastError: String? = null
                val ports = listOf(preferredPort) + (preferredPort + 1..preferredPort + 20)
                for (port in ports) {
                    try {
                        val engine =
                            HttpEngine(
                                port = port,
                                accessCode = code,
                                sessionToken = token,
                                musicRepository = musicRepository,
                                lanImportManager = lanImportManager,
                                onEvent = ::pushEvent,
                                onLockedOut = ::handleLockout,
                            )
                        engine.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                        http = engine
                        val url = ip?.let { "http://$it:$port" }
                        val qrUrl = url?.let { "$it/?code=$code" }
                        val state =
                            QuickConnectSession(
                                running = true,
                                port = port,
                                ip = ip,
                                accessCode = code,
                                url = url,
                                qrUrl = qrUrl,
                                error =
                                    if (ip == null) {
                                        "Connected to Wi‑Fi? Couldn’t find a LAN IP. Try hotspot or check Wi‑Fi."
                                    } else {
                                        null
                                    },
                                events = events.toList(),
                            )
                        _session.value = state
                        pushEvent("Portal listening on port $port")
                        return state
                    } catch (e: Exception) {
                        lastError = e.message
                    }
                }
                val failed =
                    QuickConnectSession(
                        running = false,
                        error = lastError ?: "Could not bind a local port",
                    )
                _session.value = failed
                return failed
            }
        }

        fun stop() {
            synchronized(lock) {
                stopInternal()
                events.clear()
                _session.value = QuickConnectSession()
            }
        }

        private fun stopInternal() {
            runCatching { http?.stop() }
            http = null
        }

        /**
         * Called from a request-handling thread once too many wrong access codes have been
         * entered. Doesn't tear down the listening socket (would race with writing this
         * request's response) — instead the running HttpEngine keeps rejecting every request
         * (see `permanentlyLocked` in HttpEngine.serve), and the session flips to a visibly
         * disabled state. The user must leave and reopen Quick Connect to get a fresh instance.
         */
        private fun handleLockout() {
            pushEvent("Portal locked after repeated failed access codes")
            synchronized(lock) {
                _session.value =
                    _session.value.copy(
                        running = false,
                        lockedOut = true,
                        error = "Portal locked — too many incorrect access codes. Reopen Quick Connect to try again.",
                    )
            }
        }

        private fun pushEvent(message: String) {
            events.add(0, QuickConnectEvent(message = message))
            while (events.size > 40) {
                events.removeAt(events.lastIndex)
            }
            val cur = _session.value
            if (cur.running) {
                _session.value = cur.copy(events = events.toList())
            }
        }

        private fun generateAccessCode(): String {
            val n = SecureRandom().nextInt(1_000_000)
            return n.toString().padStart(6, '0')
        }

        companion object {
            const val DEFAULT_PORT = 8765
            const val COOKIE_NAME = "sm3_qc"
            const val MAX_AUTH_ATTEMPTS = 5

            /** Above this many total failed codes, the portal disables itself until reopened. */
            const val MAX_PERMANENT_LOCKOUT_ATTEMPTS = 10
        }

        private class HttpEngine(
            port: Int,
            private val accessCode: String,
            private val sessionToken: String,
            private val musicRepository: MusicRepository,
            private val lanImportManager: LanImportManager,
            private val onEvent: (String) -> Unit,
            private val onLockedOut: () -> Unit,
        ) : NanoHTTPD(port) {
            private val started = AtomicBoolean(false)

            // Brute-force protection on /api/auth: the access code is only 6 digits
            // (1,000,000 combinations), so failed attempts must be throttled.
            private val failedAttempts = AtomicInteger(0)
            private val lockedUntilMs = AtomicLong(0)
            private val permanentlyLocked = AtomicBoolean(false)

            override fun start(
                timeout: Int,
                daemon: Boolean,
            ) {
                super.start(timeout, daemon)
                started.set(true)
            }

            val isRunning: Boolean get() = started.get() && wasStarted() && !permanentlyLocked.get()

            override fun serve(session: IHTTPSession): Response {
                if (permanentlyLocked.get()) {
                    return jsonError(
                        Response.Status.FORBIDDEN,
                        "Portal locked — too many incorrect access codes. Reopen Quick Connect to try again.",
                    )
                }
                val method = session.method
                val uri = session.uri.substringBefore('?')
                val files = HashMap<String, String>()
                if (method == Method.POST || method == Method.PUT) {
                    try {
                        session.parseBody(files)
                    } catch (e: ResponseException) {
                        return jsonError(e.status, e.message ?: "Bad request")
                    } catch (e: Exception) {
                        return jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Parse error")
                    }
                }

                return try {
                    when {
                        uri == "/" || uri == "/index.html" -> html(QuickConnectPortalHtml.PAGE)
                        uri == "/api/auth" && method == Method.POST -> handleAuth(session, files)
                        uri == "/api/status" && method == Method.GET ->
                            requireAuth(session) { handleStatus() }
                        uri == "/api/tracks" && method == Method.GET ->
                            requireAuth(session) { handleTracks() }
                        uri.startsWith("/api/tracks/") && method == Method.DELETE ->
                            requireAuth(session) {
                                val id =
                                    uri.removePrefix("/api/tracks/").toLongOrNull()
                                        ?: return@requireAuth jsonError(
                                            Response.Status.BAD_REQUEST,
                                            "Invalid track id",
                                        )
                                handleDeleteTrack(id)
                            }
                        uri == "/api/playlists" && method == Method.GET ->
                            requireAuth(session) { handlePlaylists() }
                        uri == "/api/playlists" && method == Method.POST ->
                            requireAuth(session) { handleCreatePlaylist(session, files) }
                        uri.startsWith("/api/playlists/") && method == Method.GET ->
                            requireAuth(session) {
                                val rest = uri.removePrefix("/api/playlists/")
                                if (rest.endsWith("/tracks")) {
                                    val id =
                                        rest.removeSuffix("/tracks").toLongOrNull()
                                            ?: return@requireAuth jsonError(
                                                Response.Status.BAD_REQUEST,
                                                "Invalid playlist id",
                                            )
                                    handlePlaylistTracks(id)
                                } else {
                                    jsonError(Response.Status.NOT_FOUND, "Not found")
                                }
                            }
                        uri.startsWith("/api/playlists/") && method == Method.POST ->
                            requireAuth(session) {
                                val rest = uri.removePrefix("/api/playlists/")
                                when {
                                    rest.endsWith("/tracks") -> {
                                        val id =
                                            rest.removeSuffix("/tracks").toLongOrNull()
                                                ?: return@requireAuth jsonError(
                                                    Response.Status.BAD_REQUEST,
                                                    "Invalid playlist id",
                                                )
                                        handleAddToPlaylist(id, session, files)
                                    }
                                    else -> jsonError(Response.Status.NOT_FOUND, "Not found")
                                }
                            }
                        uri.startsWith("/api/playlists/") && method == Method.DELETE ->
                            requireAuth(session) {
                                val rest = uri.removePrefix("/api/playlists/")
                                when {
                                    rest.contains("/tracks/") -> {
                                        val parts = rest.split("/tracks/")
                                        val plId = parts.getOrNull(0)?.toLongOrNull()
                                        val trackId = parts.getOrNull(1)?.toLongOrNull()
                                        if (plId == null || trackId == null) {
                                            return@requireAuth jsonError(
                                                Response.Status.BAD_REQUEST,
                                                "Invalid ids",
                                            )
                                        }
                                        handleRemoveFromPlaylist(plId, trackId)
                                    }
                                    else -> {
                                        val id =
                                            rest.toLongOrNull()
                                                ?: return@requireAuth jsonError(
                                                    Response.Status.BAD_REQUEST,
                                                    "Invalid playlist id",
                                                )
                                        handleDeletePlaylist(id)
                                    }
                                }
                            }
                        uri.startsWith("/api/playlists/") && method == Method.PUT ->
                            requireAuth(session) {
                                val id =
                                    uri.removePrefix("/api/playlists/").toLongOrNull()
                                        ?: return@requireAuth jsonError(
                                            Response.Status.BAD_REQUEST,
                                            "Invalid playlist id",
                                        )
                                handleRenamePlaylist(id, session, files)
                            }
                        uri == "/api/upload" && method == Method.POST ->
                            requireAuth(session) { handleUpload(session, files) }
                        else -> jsonError(Response.Status.NOT_FOUND, "Not found")
                    }
                } catch (e: Exception) {
                    jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Server error")
                }
            }

            private fun handleAuth(
                session: IHTTPSession,
                files: Map<String, String>,
            ): Response {
                val now = System.currentTimeMillis()
                val lockedUntil = lockedUntilMs.get()
                if (now < lockedUntil) {
                    val waitSec = ((lockedUntil - now) / 1000).coerceAtLeast(1)
                    return jsonError(
                        Response.Status.TOO_MANY_REQUESTS,
                        "Too many attempts — try again in ${waitSec}s",
                    )
                }

                val body =
                    session.parms["code"]
                        ?.takeIf { it.isNotBlank() }
                        ?: readJsonBody(files)?.optString("code")
                        ?: ""
                if (!constantTimeEquals(body.trim(), accessCode)) {
                    val attempts = failedAttempts.incrementAndGet()
                    onEvent("Failed access code attempt")
                    if (attempts >= MAX_PERMANENT_LOCKOUT_ATTEMPTS) {
                        permanentlyLocked.set(true)
                        onLockedOut()
                        return jsonError(
                            Response.Status.FORBIDDEN,
                            "Portal locked — too many incorrect access codes. Reopen Quick Connect to try again.",
                        )
                    }
                    if (attempts >= MAX_AUTH_ATTEMPTS) {
                        val lockMs = lockoutDurationMs(attempts)
                        lockedUntilMs.set(System.currentTimeMillis() + lockMs)
                    }
                    return jsonError(Response.Status.UNAUTHORIZED, "Invalid access code")
                }
                failedAttempts.set(0)
                lockedUntilMs.set(0)
                onEvent("Desktop unlocked the portal")
                val res = jsonOk(JSONObject().put("ok", true))
                res.addHeader(
                    "Set-Cookie",
                    "${COOKIE_NAME}=$sessionToken; Path=/; HttpOnly; SameSite=Strict",
                )
                return res
            }

            /** Exponential backoff beyond the free-attempt threshold, capped at 5 minutes. */
            private fun lockoutDurationMs(attempts: Int): Long {
                val overage = (attempts - MAX_AUTH_ATTEMPTS).coerceAtLeast(0)
                val seconds = (30L shl overage.coerceAtMost(4)) // 30s, 60s, 120s, 240s, then capped
                return seconds.coerceAtMost(300L) * 1000L
            }

            private fun handleStatus(): Response =
                runBlocking {
                    val tracks = musicRepository.getAllTracksOnce()
                    val playlists = musicRepository.getPlaylistsOnce()
                    jsonOk(
                        JSONObject()
                            .put("trackCount", tracks.size)
                            .put("playlistCount", playlists.size)
                            .put("app", "Simple MP3")
                            .put("mode", "Quick Connect"),
                    )
                }

            private fun handleTracks(): Response =
                runBlocking {
                    val tracks = musicRepository.getAllTracksOnce()
                    val arr = JSONArray()
                    tracks.forEach { t -> arr.put(trackJson(t)) }
                    jsonOk(JSONObject().put("tracks", arr))
                }

            private fun handleDeleteTrack(id: Long): Response =
                runBlocking {
                    val ok = lanImportManager.deleteLanTrack(id)
                    if (!ok) {
                        return@runBlocking jsonError(
                            Response.Status.BAD_REQUEST,
                            "Only LAN-uploaded tracks can be deleted from the portal",
                        )
                    }
                    onEvent("Deleted LAN track #$id")
                    jsonOk(JSONObject().put("ok", true))
                }

            private fun handlePlaylists(): Response =
                runBlocking {
                    val lists = musicRepository.getPlaylistsOnce()
                    val arr = JSONArray()
                    lists.forEach { p ->
                        arr.put(
                            JSONObject()
                                .put("id", p.id)
                                .put("name", p.name)
                                .put("description", p.description)
                                .put("trackCount", p.trackCount)
                                .put("isSystem", p.isSystem)
                                .put("systemType", p.systemType),
                        )
                    }
                    jsonOk(JSONObject().put("playlists", arr))
                }

            private fun handleCreatePlaylist(
                session: IHTTPSession,
                files: Map<String, String>,
            ): Response =
                runBlocking {
                    val json = readJsonBody(files)
                    val name =
                        json
                            ?.optString("name")
                            ?.trim()
                            .orEmpty()
                            .ifBlank { session.parms["name"]?.trim().orEmpty() }
                    if (name.isBlank()) {
                        return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Name required")
                    }
                    val id = musicRepository.createPlaylist(name)
                    onEvent("Created playlist “$name”")
                    jsonOk(JSONObject().put("id", id).put("name", name))
                }

            private fun handleRenamePlaylist(
                id: Long,
                session: IHTTPSession,
                files: Map<String, String>,
            ): Response =
                runBlocking {
                    val json = readJsonBody(files)
                    val name =
                        json
                            ?.optString("name")
                            ?.trim()
                            .orEmpty()
                            .ifBlank { session.parms["name"]?.trim().orEmpty() }
                    if (name.isBlank()) {
                        return@runBlocking jsonError(Response.Status.BAD_REQUEST, "Name required")
                    }
                    val existing = musicRepository.getPlaylistsOnce().firstOrNull { it.id == id }
                    if (existing == null) {
                        return@runBlocking jsonError(Response.Status.NOT_FOUND, "Playlist not found")
                    }
                    if (existing.isSystem) {
                        return@runBlocking jsonError(
                            Response.Status.BAD_REQUEST,
                            "System playlists can’t be renamed",
                        )
                    }
                    musicRepository.renamePlaylist(id, name)
                    onEvent("Renamed playlist to “$name”")
                    jsonOk(JSONObject().put("ok", true))
                }

            private fun handleDeletePlaylist(id: Long): Response =
                runBlocking {
                    val existing = musicRepository.getPlaylistsOnce().firstOrNull { it.id == id }
                    if (existing == null) {
                        return@runBlocking jsonError(Response.Status.NOT_FOUND, "Playlist not found")
                    }
                    if (existing.isSystem) {
                        return@runBlocking jsonError(
                            Response.Status.BAD_REQUEST,
                            "System playlists can’t be deleted",
                        )
                    }
                    musicRepository.deletePlaylist(id)
                    onEvent("Deleted playlist “${existing.name}”")
                    jsonOk(JSONObject().put("ok", true))
                }

            private fun handlePlaylistTracks(id: Long): Response =
                runBlocking {
                    val tracks = musicRepository.getPlaylistTracksOnce(id)
                    val arr = JSONArray()
                    tracks.forEach { t -> arr.put(trackJson(t)) }
                    jsonOk(JSONObject().put("tracks", arr))
                }

            private fun handleAddToPlaylist(
                playlistId: Long,
                session: IHTTPSession,
                files: Map<String, String>,
            ): Response =
                runBlocking {
                    val json = readJsonBody(files)
                    val trackId =
                        when {
                            json != null && json.has("trackId") -> json.optLong("trackId")
                            else -> session.parms["trackId"]?.toLongOrNull()
                        }
                    if (trackId == null || trackId == 0L) {
                        return@runBlocking jsonError(Response.Status.BAD_REQUEST, "trackId required")
                    }
                    musicRepository.addToPlaylist(playlistId, trackId)
                    onEvent("Added track to playlist #$playlistId")
                    jsonOk(JSONObject().put("ok", true))
                }

            private fun handleRemoveFromPlaylist(
                playlistId: Long,
                trackId: Long,
            ): Response =
                runBlocking {
                    musicRepository.removeFromPlaylist(playlistId, trackId)
                    onEvent("Removed track from playlist #$playlistId")
                    jsonOk(JSONObject().put("ok", true))
                }

            private fun handleUpload(
                session: IHTTPSession,
                files: Map<String, String>,
            ): Response =
                runBlocking {
                    val playlistId = session.parms["playlistId"]?.toLongOrNull()
                    val imported = mutableListOf<TrackEntity>()
                    val errors = mutableListOf<String>()

                    // NanoHTTPD: form field name → temp file path
                    val fileFields =
                        files.entries.filter { (key, path) ->
                            path.isNotBlank() &&
                                File(path).exists() &&
                                (key == "file" || key.startsWith("file") || key == "files")
                        }

                    if (fileFields.isEmpty()) {
                        // Fallback: any file path in map that isn't pure form text
                        files.forEach { (key, path) ->
                            val f = File(path)
                            if (f.isFile && f.length() > 0 && path.contains(File.separator)) {
                                tryImport(f, session.parms[key] ?: f.name, playlistId, imported, errors)
                            }
                        }
                    } else {
                        fileFields.forEach { (key, path) ->
                            val f = File(path)
                            val original = session.parms[key] ?: f.name
                            tryImport(f, original, playlistId, imported, errors)
                        }
                    }

                    // Also support multi-file with same field name patterns
                    if (imported.isEmpty() && errors.isEmpty()) {
                        return@runBlocking jsonError(
                            Response.Status.BAD_REQUEST,
                            "No audio files found in upload",
                        )
                    }

                    if (imported.isNotEmpty()) {
                        onEvent(
                            "Uploaded ${imported.size} file" +
                                if (imported.size == 1) ": ${imported.first().title}" else "s",
                        )
                    }

                    jsonOk(
                        JSONObject()
                            .put("ok", errors.isEmpty())
                            .put(
                                "imported",
                                JSONArray().also { arr ->
                                    imported.forEach { arr.put(trackJson(it)) }
                                },
                            ).put(
                                "errors",
                                JSONArray().also { arr ->
                                    errors.forEach { arr.put(it) }
                                },
                            ),
                    )
                }

            private suspend fun tryImport(
                tempFile: File,
                originalName: String,
                playlistId: Long?,
                imported: MutableList<TrackEntity>,
                errors: MutableList<String>,
            ) {
                val safeName = LanImportManager.sanitizeFileName(originalName)
                try {
                    FileInputStream(tempFile).use { stream ->
                        val track = lanImportManager.importStream(stream, safeName, playlistId)
                        imported += track
                    }
                } catch (e: Exception) {
                    errors += "$safeName: ${e.message ?: "failed"}"
                }
            }

            private fun trackJson(t: TrackEntity): JSONObject =
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("duration", t.duration)
                    .put("source", t.source)
                    .put("size", t.size)
                    .put("canDelete", t.source == TrackEntity.SOURCE_LAN)

            private fun requireAuth(
                session: IHTTPSession,
                block: () -> Response,
            ): Response {
                if (!isAuthorized(session)) {
                    return jsonError(Response.Status.UNAUTHORIZED, "Enter the access code first")
                }
                return block()
            }

            private fun isAuthorized(session: IHTTPSession): Boolean {
                val cookie = session.headers["cookie"].orEmpty()
                val fromCookie =
                    cookie
                        .split(';')
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("$COOKIE_NAME=") }
                        ?.substringAfter('=')
                if (fromCookie != null && constantTimeEquals(fromCookie, sessionToken)) return true
                val header = session.headers["x-access-code"]
                if (header != null && constantTimeEquals(header, accessCode)) return true
                val auth = session.headers["authorization"]
                if (auth != null && constantTimeEquals(auth.removePrefix("Bearer ").trim(), sessionToken)) {
                    return true
                }
                return false
            }

            /** Avoids leaking match-length via early-exit string comparison timing. */
            private fun constantTimeEquals(
                a: String,
                b: String,
            ): Boolean = MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

            /**
             * NanoHTTPD stores raw (non-multipart, non-form-urlencoded) POST bodies as the
             * literal content string under `postData` — not a temp file path.
             */
            private fun readJsonBody(files: Map<String, String>): JSONObject? {
                val text = files["postData"] ?: return null
                return runCatching { JSONObject(text) }.getOrNull()
            }

            private fun applySecurityHeaders(res: Response): Response =
                res.apply {
                    addHeader("X-Content-Type-Options", "nosniff")
                    addHeader("X-Frame-Options", "DENY")
                    addHeader("X-XSS-Protection", "1; mode=block")
                    addHeader(
                        "Content-Security-Policy",
                        "default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' data:; connect-src 'self'",
                    )
                }

            private fun html(body: String): Response =
                applySecurityHeaders(
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body),
                )

            private fun jsonOk(obj: JSONObject): Response =
                applySecurityHeaders(
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json; charset=utf-8",
                        obj.toString(),
                    ).also { it.addHeader("Cache-Control", "no-store") },
                )

            private fun jsonError(
                status: Response.Status,
                message: String,
            ): Response =
                applySecurityHeaders(
                    newFixedLengthResponse(
                        status,
                        "application/json; charset=utf-8",
                        JSONObject().put("error", message).toString(),
                    ),
                )
        }
    }
