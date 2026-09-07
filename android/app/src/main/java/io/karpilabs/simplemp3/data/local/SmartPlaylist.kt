package io.karpilabs.simplemp3.data.local

/**
 * Computed ("auto") playlists that are derived from track metadata rather than
 * stored [PlaylistTrackCrossRef] rows. They are surfaced alongside real playlists
 * but their contents are queried on demand, so they always stay fresh.
 */
enum class SmartPlaylist(
    val key: String,
    val displayName: String,
    val description: String,
) {
    MOST_PLAYED("most_played", "Most Played", "Your top tracks by play count"),
    RECENTLY_ADDED("recently_added", "Recently Added", "The latest additions to your library"),
    ;

    companion object {
        fun fromKey(key: String?): SmartPlaylist? = entries.firstOrNull { it.key == key }
    }
}
