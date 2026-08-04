package io.karpilabs.simplemp3.data.jellyfin

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val moshi: Moshi
) {
    private val authAdapter by lazy { moshi.adapter(AuthenticationResult::class.java) }
    private val queryAdapter by lazy { moshi.adapter(QueryResult::class.java) }
    private val itemAdapter by lazy { moshi.adapter(JellyfinItem::class.java) }
    private val authRequestAdapter by lazy { moshi.adapter(AuthenticateByNameRequest::class.java) }

    fun normalizeServerUrl(raw: String): String {
        var url = raw.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        return url
    }

    private fun authHeader(deviceId: String, token: String? = null): String {
        val base = buildString {
            append("""MediaBrowser Client="SimpleMP3", Device="Android", DeviceId="$deviceId", Version="1.0.0"""")
            if (!token.isNullOrBlank()) {
                append(""", Token="$token"""")
            }
        }
        return base
    }

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
        deviceId: String
    ): Result<JellyfinSession> = withContext(Dispatchers.IO) {
        runCatching {
            val base = normalizeServerUrl(serverUrl)
            val bodyJson = authRequestAdapter.toJson(
                AuthenticateByNameRequest(username = username.trim(), password = password)
            )
            val request = Request.Builder()
                .url("$base/Users/AuthenticateByName")
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .header("X-Emby-Authorization", authHeader(deviceId))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            okHttp.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Login failed (${response.code}): ${body.take(200)}")
                }
                val parsed = authAdapter.fromJson(body)
                    ?: throw IOException("Invalid login response")
                val token = parsed.accessToken
                    ?: throw IOException("No access token returned")
                val user = parsed.user
                    ?: throw IOException("No user in response")
                JellyfinSession(
                    serverUrl = base,
                    accessToken = token,
                    userId = user.id,
                    userName = user.name.orEmpty().ifBlank { username },
                    serverId = parsed.serverId,
                    deviceId = deviceId
                )
            }
        }
    }

    suspend fun getAudioItems(
        session: JellyfinSession,
        startIndex: Int = 0,
        limit: Int = 200,
        parentId: String? = null,
        searchTerm: String? = null
    ): Result<QueryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val params = mutableListOf(
                "IncludeItemTypes=Audio",
                "Recursive=true",
                "Fields=BasicSyncInfo,PrimaryImageAspectRatio,Path,MediaSources",
                "SortBy=Album,IndexNumber,SortName",
                "SortOrder=Ascending",
                "StartIndex=$startIndex",
                "Limit=$limit",
                "EnableImageTypes=Primary"
            )
            if (!parentId.isNullOrBlank()) {
                params += "ParentId=${enc(parentId)}"
            }
            if (!searchTerm.isNullOrBlank()) {
                params += "SearchTerm=${enc(searchTerm)}"
            }
            val url = "${session.serverUrl}/Users/${session.userId}/Items?${params.joinToString("&")}"
            getJson(session, url, queryAdapter) ?: QueryResult()
        }
    }

    suspend fun getAlbums(
        session: JellyfinSession,
        startIndex: Int = 0,
        limit: Int = 100
    ): Result<QueryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(session.serverUrl)
                append("/Users/${session.userId}/Items?")
                append("IncludeItemTypes=MusicAlbum&Recursive=true")
                append("&SortBy=SortName&SortOrder=Ascending")
                append("&StartIndex=$startIndex&Limit=$limit")
                append("&EnableImageTypes=Primary")
                append("&Fields=ChildCount,PrimaryImageAspectRatio")
            }
            getJson(session, url, queryAdapter) ?: QueryResult()
        }
    }

    suspend fun getAlbumTracks(
        session: JellyfinSession,
        albumId: String
    ): Result<List<JellyfinItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(session.serverUrl)
                append("/Users/${session.userId}/Items?")
                append("ParentId=${enc(albumId)}")
                append("&IncludeItemTypes=Audio")
                append("&Recursive=true")
                append("&SortBy=IndexNumber,SortName")
                append("&SortOrder=Ascending")
                append("&Fields=BasicSyncInfo,PrimaryImageAspectRatio")
                append("&Limit=500")
            }
            getJson(session, url, queryAdapter)?.items.orEmpty()
        }
    }

    suspend fun getPlaylists(
        session: JellyfinSession,
        startIndex: Int = 0,
        limit: Int = 100
    ): Result<QueryResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(session.serverUrl)
                append("/Users/${session.userId}/Items?")
                append("IncludeItemTypes=Playlist&Recursive=true")
                append("&SortBy=SortName&SortOrder=Ascending")
                append("&StartIndex=$startIndex&Limit=$limit")
                append("&EnableImageTypes=Primary")
                append("&Fields=ChildCount,PrimaryImageAspectRatio")
            }
            getJson(session, url, queryAdapter) ?: QueryResult()
        }
    }

    suspend fun getPlaylistTracks(
        session: JellyfinSession,
        playlistId: String
    ): Result<List<JellyfinItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = buildString {
                append(session.serverUrl)
                append("/Playlists/${enc(playlistId)}/Items?")
                append("UserId=${enc(session.userId)}")
                append("&Fields=BasicSyncInfo,PrimaryImageAspectRatio,MediaSources")
                append("&EnableImageTypes=Primary")
                append("&Limit=500")
            }
            getJson(session, url, queryAdapter)?.items.orEmpty()
        }
    }

    suspend fun getItem(
        session: JellyfinSession,
        itemId: String
    ): Result<JellyfinItem> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${session.serverUrl}/Users/${session.userId}/Items/${enc(itemId)}" +
                "?Fields=BasicSyncInfo,PrimaryImageAspectRatio,MediaSources,Path"
            getJson(session, url, itemAdapter)
                ?: throw IOException("Item not found")
        }
    }

    fun imageUrl(session: JellyfinSession, item: JellyfinItem, maxWidth: Int = 400): String? {
        val imageItemId = when {
            item.imageTags?.containsKey("Primary") == true -> item.id
            !item.albumId.isNullOrBlank() && !item.albumPrimaryImageTag.isNullOrBlank() -> item.albumId
            else -> return null
        }
        return "${session.serverUrl}/Items/$imageItemId/Images/Primary?maxWidth=$maxWidth&quality=85&api_key=${session.accessToken}"
    }

    fun streamUrl(session: JellyfinSession, itemId: String): String {
        return "${session.serverUrl}/Audio/$itemId/stream?static=true&api_key=${session.accessToken}"
    }

    suspend fun downloadToFile(
        session: JellyfinSession,
        itemId: String,
        dest: File,
        onProgress: (bytesRead: Long, total: Long?) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            dest.parentFile?.mkdirs()
            val tmp = File(dest.parentFile, "${dest.name}.part")
            if (tmp.exists()) tmp.delete()

            val request = Request.Builder()
                .url(streamUrl(session, itemId))
                .header("X-Emby-Authorization", authHeader(session.deviceId, session.accessToken))
                .header("X-Emby-Token", session.accessToken)
                .get()
                .build()

            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Download failed (${response.code})")
                }
                val body = response.body ?: throw IOException("Empty body")
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            onProgress(readTotal, total)
                        }
                    }
                }
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            dest
        }
    }

    suspend fun downloadImage(
        session: JellyfinSession,
        item: JellyfinItem,
        dest: File
    ): Result<File?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = imageUrl(session, item, maxWidth = 600) ?: return@runCatching null
            dest.parentFile?.mkdirs()
            val request = Request.Builder()
                .url(url)
                .header("X-Emby-Authorization", authHeader(session.deviceId, session.accessToken))
                .get()
                .build()
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body ?: return@runCatching null
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            dest
        }
    }

    private fun <T> getJson(session: JellyfinSession, url: String, adapter: com.squareup.moshi.JsonAdapter<T>): T? {
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Authorization", authHeader(session.deviceId, session.accessToken))
            .header("X-Emby-Token", session.accessToken)
            .header("Accept", "application/json")
            .get()
            .build()
        okHttp.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Request failed (${response.code}): ${body.take(200)}")
            }
            return adapter.fromJson(body)
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
