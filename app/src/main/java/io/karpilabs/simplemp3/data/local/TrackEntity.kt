package io.karpilabs.simplemp3.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["dateAdded"]),
        Index(value = ["source"]),
        Index(value = ["jellyfinId"], unique = true),
        Index(value = ["storageState"]),
        Index(value = ["size"])
    ]
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val artistId: Long = 0L,
    val uri: String,
    val duration: Long,
    val artworkUri: String? = null,
    val dateAdded: Long = 0L,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val genre: String? = null,
    val size: Long = 0L,
    /** local | jellyfin | youtube | lan */
    val source: String = SOURCE_LOCAL,
    /** Jellyfin item GUID or YouTube video id (source-discriminated). */
    val jellyfinId: String? = null,
    val isOffline: Boolean = true,
    /**
     * [STORAGE_HOT] playable file at [uri].
     * [STORAGE_COLD] gzip archive at [coldUri]; thaw to [uri] on play.
     */
    val storageState: String = STORAGE_HOT,
    /** file:// path to gzip archive when cold. */
    val coldUri: String? = null,
    /** True after one-time lossy re-encode to a leaner bitrate. */
    val isSizeOptimized: Boolean = false,
    /** Last time this track was prepared for playback (ms). */
    val lastPlayedAt: Long = 0L,
    /**
     * User starred “never compress” — skip size optimize + cold pack forever
     * (and keep thawed if currently cold).
     */
    val neverCompress: Boolean = false
) {
    companion object {
        const val SOURCE_LOCAL = "local"
        const val SOURCE_JELLYFIN = "jellyfin"
        const val SOURCE_YOUTUBE = "youtube"
        /** Uploaded via Quick Connect LAN portal */
        const val SOURCE_LAN = "lan"

        const val STORAGE_HOT = "hot"
        const val STORAGE_COLD = "cold"
    }

    val isJellyfin: Boolean get() = source == SOURCE_JELLYFIN
    val isYoutube: Boolean get() = source == SOURCE_YOUTUBE
    val isLan: Boolean get() = source == SOURCE_LAN
    val isCold: Boolean get() = storageState == STORAGE_COLD
    val isAppOwned: Boolean
        get() = source == SOURCE_JELLYFIN ||
            source == SOURCE_YOUTUBE ||
            source == SOURCE_LAN
}

/** Stable negative Long id from an external string id (never collides with MediaStore). */
fun externalItemIdToTrackId(itemId: String): Long {
    var h = 0xcbf29ce484222325UL
    for (c in itemId) {
        h = h xor c.code.toULong()
        h *= 0x100000001b3UL
    }
    val positive = (h and 0x7fffffffffffffffUL).toLong().coerceAtLeast(1L)
    return -positive
}

/** @deprecated Prefer [externalItemIdToTrackId]. */
fun jellyfinItemIdToTrackId(itemId: String): Long = externalItemIdToTrackId(itemId)
