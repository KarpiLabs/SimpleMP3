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
    NEVER_PLAYED("never_played", "Never Played", "Tracks you haven't started yet"),
    NOT_RECENTLY("not_recently", "Not in a while", "Played before, but not in the last 90 days"),
    ;

    companion object {
        fun fromKey(key: String?): SmartPlaylist? = entries.firstOrNull { it.key == key }

        /** Cutoff for [NOT_RECENTLY] — 90 days. */
        const val NOT_RECENTLY_WINDOW_MS: Long = 90L * 24 * 60 * 60 * 1000
    }
}
