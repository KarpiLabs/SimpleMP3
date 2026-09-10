package io.karpilabs.simplemp3.data.search

import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity

/**
 * In-memory library search across songs, albums, artists, and playlists.
 * Matching is case-insensitive substring — same rules as the Library tab filter.
 */
object LibrarySearch {
    data class Results(
        val tracks: List<TrackEntity> = emptyList(),
        val albums: List<AlbumRow> = emptyList(),
        val artists: List<AlbumRow> = emptyList(),
        val playlists: List<PlaylistWithMeta> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

        val totalCount: Int
            get() = tracks.size + albums.size + artists.size + playlists.size
    }

    fun query(
        raw: String,
        tracks: List<TrackEntity>,
        albums: List<AlbumRow>,
        artists: List<AlbumRow>,
        playlists: List<PlaylistWithMeta>,
    ): Results {
        val q = raw.trim()
        if (q.isEmpty()) return Results()
        return Results(
            tracks = tracks.filter { trackMatches(it, q) },
            albums = albums.filter { collectionMatches(it, q) },
            artists = artists.filter { collectionMatches(it, q) },
            playlists = playlists.filter { playlistMatches(it, q) },
        )
    }

    fun trackMatches(
        track: TrackEntity,
        query: String,
    ): Boolean =
        track.title.contains(query, ignoreCase = true) ||
            track.artist.contains(query, ignoreCase = true) ||
            track.album.contains(query, ignoreCase = true) ||
            (track.genre?.contains(query, ignoreCase = true) == true)

    fun collectionMatches(
        row: AlbumRow,
        query: String,
    ): Boolean =
        row.name.contains(query, ignoreCase = true) ||
            row.subtitle.contains(query, ignoreCase = true)

    fun playlistMatches(
        playlist: PlaylistWithMeta,
        query: String,
    ): Boolean =
        playlist.name.contains(query, ignoreCase = true) ||
            playlist.description.contains(query, ignoreCase = true)
}
