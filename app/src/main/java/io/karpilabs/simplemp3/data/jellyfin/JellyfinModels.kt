package io.karpilabs.simplemp3.data.jellyfin

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AuthenticateByNameRequest(
    @Json(name = "Username") val username: String,
    @Json(name = "Pw") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthenticationResult(
    @Json(name = "AccessToken") val accessToken: String?,
    @Json(name = "User") val user: JellyfinUser?,
    @Json(name = "ServerId") val serverId: String? = null
)

@JsonClass(generateAdapter = true)
data class JellyfinUser(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class QueryResult(
    @Json(name = "Items") val items: List<JellyfinItem> = emptyList(),
    @Json(name = "TotalRecordCount") val totalRecordCount: Int = 0,
    @Json(name = "StartIndex") val startIndex: Int = 0
)

@JsonClass(generateAdapter = true)
data class JellyfinItem(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "Album") val album: String? = null,
    @Json(name = "AlbumId") val albumId: String? = null,
    @Json(name = "AlbumArtist") val albumArtist: String? = null,
    @Json(name = "Artists") val artists: List<String>? = null,
    @Json(name = "RunTimeTicks") val runTimeTicks: Long? = null,
    @Json(name = "IndexNumber") val indexNumber: Int? = null,
    @Json(name = "ProductionYear") val productionYear: Int? = null,
    @Json(name = "ImageTags") val imageTags: Map<String, String>? = null,
    @Json(name = "AlbumPrimaryImageTag") val albumPrimaryImageTag: String? = null,
    @Json(name = "ParentId") val parentId: String? = null,
    @Json(name = "ChildCount") val childCount: Int? = null,
    @Json(name = "Size") val size: Long? = null,
    @Json(name = "Container") val container: String? = null
) {
    val durationMs: Long
        get() = (runTimeTicks ?: 0L) / 10_000L

    val artistName: String
        get() = artists?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: albumArtist?.takeIf { it.isNotBlank() }
            ?: "Unknown Artist"

    val albumName: String
        get() = album?.takeIf { it.isNotBlank() } ?: "Unknown Album"

    val title: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Unknown Title"

    val hasPrimaryImage: Boolean
        get() = imageTags?.containsKey("Primary") == true || !albumPrimaryImageTag.isNullOrBlank()
}

data class JellyfinSession(
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String,
    val serverId: String? = null,
    val deviceId: String
)

data class SyncProgress(
    val phase: String = "Idle",
    val current: Int = 0,
    val total: Int = 0,
    val currentTitle: String = "",
    val isActive: Boolean = false,
    val error: String? = null,
    val lastResult: String? = null
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total).coerceIn(0f, 1f)
}
