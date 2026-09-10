package io.karpilabs.simplemp3.data.search

import io.karpilabs.simplemp3.data.local.AlbumRow
import io.karpilabs.simplemp3.data.local.PlaylistWithMeta
import io.karpilabs.simplemp3.data.local.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    @Test
    fun blankQueryReturnsEmpty() {
        val results =
            LibrarySearch.query(
                "  ",
                tracks = listOf(track(1, "Highway", "Juniper", "Folded Sky")),
                albums = listOf(album("Folded Sky", "Juniper")),
                artists = listOf(album("Juniper", "")),
                playlists = listOf(playlist(9, "Road trip")),
            )
        assertTrue(results.isEmpty)
    }

    @Test
    fun matchesSongsAlbumsArtistsAndPlaylists() {
        val results =
            LibrarySearch.query(
                "sky",
                tracks =
                    listOf(
                        track(1, "Highway", "Juniper", "Folded Sky"),
                        track(2, "Night", "Other", "Blue"),
                    ),
                albums =
                    listOf(
                        album("Folded Sky", "Juniper"),
                        album("Blue", "Other"),
                    ),
                artists = listOf(album("Juniper", ""), album("Skyline", "")),
                playlists =
                    listOf(
                        playlist(9, "Sky mix"),
                        playlist(10, "Road trip"),
                    ),
            )
        assertEquals(listOf(1L), results.tracks.map { it.id })
        assertEquals(listOf("Folded Sky"), results.albums.map { it.name })
        assertEquals(listOf("Skyline"), results.artists.map { it.name })
        assertEquals(listOf("Sky mix"), results.playlists.map { it.name })
        assertEquals(4, results.totalCount)
    }

    @Test
    fun genreIsSearchableOnTracks() {
        val results =
            LibrarySearch.query(
                "jazz",
                tracks = listOf(track(3, "Soft", "A", "B").copy(genre = "Cool Jazz")),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
            )
        assertEquals(listOf(3L), results.tracks.map { it.id })
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String,
    ) = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        uri = "content://media/$id",
        duration = 180_000,
    )

    private fun album(
        name: String,
        subtitle: String,
    ) = AlbumRow(name, subtitle, 1, 0, null, 0)

    private fun playlist(
        id: Long,
        name: String,
    ) = PlaylistWithMeta(
        id = id,
        name = name,
        description = "",
        coverUri = null,
        createdAt = 0,
        updatedAt = 0,
        isSystem = false,
        systemType = null,
        trackCount = 1,
        firstArtworkUri = null,
    )
}
