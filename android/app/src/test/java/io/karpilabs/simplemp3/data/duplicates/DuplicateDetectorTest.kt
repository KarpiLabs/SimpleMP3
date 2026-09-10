package io.karpilabs.simplemp3.data.duplicates

import io.karpilabs.simplemp3.data.local.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {
    @Test
    fun groupsSameTitleArtistAndDuration() {
        val music =
            track(1, "Highway", "Juniper", duration = 180_000, folder = "Music/Juniper", size = 4_000_000)
        val download =
            track(2, "Highway", "Juniper", duration = 181_500, folder = "Download", size = 3_000_000)
        val other = track(3, "Night", "Juniper", duration = 180_000, folder = "Music")

        val groups = DuplicateDetector.findGroups(listOf(music, download, other))
        assertEquals(1, groups.size)
        assertEquals(1L, groups[0].keepId)
        assertEquals(listOf(2L), groups[0].extras.map { it.id })
    }

    @Test
    fun prefersPlayCountThenMusicFolderThenSize() {
        val download =
            track(10, "Clip", "A", duration = 120_000, folder = "Download", size = 9_000_000, plays = 0)
        val music =
            track(11, "Clip", "A", duration = 120_000, folder = "Music", size = 2_000_000, plays = 0)
        val loved =
            track(12, "Clip", "A", duration = 121_000, folder = "Download", size = 1_000, plays = 8)

        val group = DuplicateDetector.findGroups(listOf(download, music, loved)).single()
        assertEquals(12L, group.keepId)
    }

    @Test
    fun ignoresStreamsAndUnknownTitles() {
        val stream =
            TrackEntity(
                id = 9,
                title = "Radio",
                artist = "Live",
                album = "S",
                uri = "https://x",
                duration = 0,
                source = TrackEntity.SOURCE_STREAM,
            )
        val unknown = track(1, "Unknown Title", "Unknown Artist", duration = 180_000)
        val unknown2 = track(2, "Unknown Title", "Unknown Artist", duration = 180_000)
        assertTrue(DuplicateDetector.findGroups(listOf(stream, unknown, unknown2)).isEmpty())
    }

    @Test
    fun normalizeCollapsesPunctuation() {
        assertEquals("hello world", DuplicateDetector.normalize("Hello, World!"))
        assertEquals("highway", DuplicateDetector.normalize("  Highway  "))
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        duration: Long,
        folder: String = "",
        size: Long = 0,
        plays: Int = 0,
    ) = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        uri = "content://media/$id",
        duration = duration,
        folderPath = folder,
        size = size,
        playCount = plays,
    )
}
