package io.karpilabs.simplemp3.data.playlist

import io.karpilabs.simplemp3.data.local.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uPlaylistTest {
    @Test
    fun parseExtm3uAndPlaylistName() {
        val text =
            """
            #EXTM3U
            #PLAYLIST:Road trip
            #EXTINF:180,Juniper - Highway
            content://media/external/audio/media/1
            #EXTINF:200,Night
            /storage/emulated/0/Music/night.mp3
            """.trimIndent()
        val (name, entries) = M3uPlaylist.parse(text, "Fallback")
        assertEquals("Road trip", name)
        assertEquals(2, entries.size)
        assertEquals("Juniper", entries[0].artist)
        assertEquals("Highway", entries[0].title)
        assertEquals(180, entries[0].durationSec)
        assertEquals("night.mp3", entries[1].fileName)
    }

    @Test
    fun writeThenParseRoundTrip() {
        val tracks =
            listOf(
                track(1, "Highway", "Juniper", "content://media/1"),
                track(2, "Night", "Other", "file:///Music/night.mp3"),
            )
        val text = M3uPlaylist.write("Mix", tracks)
        val (name, entries) = M3uPlaylist.parse(text)
        assertEquals("Mix", name)
        assertEquals(2, entries.size)
        assertEquals("content://media/1", entries[0].location)
    }

    @Test
    fun matchByUriAndFileNameAndTitle() {
        val library =
            listOf(
                track(1, "Highway", "Juniper", "content://media/1"),
                track(2, "Night", "Other", "file:///storage/emulated/0/Music/night.mp3"),
                track(3, "Paper Planes", "Juniper", "content://media/3"),
            )
        val entries =
            listOf(
                M3uPlaylist.Entry("content://media/1"),
                M3uPlaylist.Entry("/Music/night.mp3"),
                M3uPlaylist.Entry("missing.mp3", title = "Paper Planes", artist = "Juniper"),
                M3uPlaylist.Entry("nope.mp3", title = "Ghost"),
            )
        val result = M3uPlaylist.match(entries, library, "Imported")
        assertEquals(listOf(1L, 2L, 3L), result.matched.map { it.id })
        assertEquals(1, result.unmatched.size)
        assertEquals("Ghost", result.unmatched[0].title)
    }

    @Test
    fun splitArtistTitle() {
        assertEquals("Juniper" to "Highway", M3uPlaylist.splitArtistTitle("Juniper - Highway"))
        assertEquals(null to "Highway", M3uPlaylist.splitArtistTitle("Highway"))
        assertEquals(null to null, M3uPlaylist.splitArtistTitle("  "))
    }

    @Test
    fun skipsLiveStreams() {
        val stream =
            TrackEntity(
                id = 9,
                title = "Radio",
                artist = "Live",
                album = "Streams",
                uri = "https://radio.example/live",
                duration = 0,
                source = TrackEntity.SOURCE_STREAM,
            )
        val song = track(1, "Highway", "Juniper", "content://media/1")
        val result =
            M3uPlaylist.match(
                listOf(M3uPlaylist.Entry("content://media/1"), M3uPlaylist.Entry("https://radio.example/live")),
                listOf(song, stream),
                "X",
            )
        assertEquals(listOf(1L), result.matched.map { it.id })
        assertTrue(result.unmatched.any { it.location.contains("radio") })
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        uri: String,
    ) = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        uri = uri,
        duration = 180_000,
    )
}
