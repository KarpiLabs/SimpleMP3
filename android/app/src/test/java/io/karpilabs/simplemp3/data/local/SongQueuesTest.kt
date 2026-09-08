package io.karpilabs.simplemp3.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongQueuesTest {
    @Test
    fun excludingLiveStreams_dropsStreamSourceTracks() {
        val song = track(id = 1, title = "Highway", source = TrackEntity.SOURCE_LOCAL)
        val jellyfin = track(id = 2, title = "Offline", source = TrackEntity.SOURCE_JELLYFIN)
        val stream = track(id = 3, title = "Radio", source = TrackEntity.SOURCE_STREAM)

        val result = listOf(song, jellyfin, stream).excludingLiveStreams()

        assertEquals(listOf(1L, 2L), result.map { it.id })
        assertTrue(result.none { it.isStream })
    }

    @Test
    fun excludingLiveStreams_keepsEmptyAndNonStreamLists() {
        assertTrue(emptyList<TrackEntity>().excludingLiveStreams().isEmpty())
        val onlySongs = listOf(track(id = 9, title = "Clip", source = TrackEntity.SOURCE_YOUTUBE))
        assertEquals(onlySongs, onlySongs.excludingLiveStreams())
    }

    @Test
    fun playbackQueue_playsALiveStreamAlone() {
        val song = track(id = 1, title = "Highway", source = TrackEntity.SOURCE_LOCAL)
        val stream = track(id = 3, title = "Radio", source = TrackEntity.SOURCE_STREAM)
        val (queue, index) = listOf(song, stream).playbackQueue(stream)
        assertEquals(listOf(3L), queue.map { it.id })
        assertEquals(0, index)
    }

    @Test
    fun playbackQueue_dropsStreamsWhenStartingOnASong() {
        val song = track(id = 1, title = "Highway", source = TrackEntity.SOURCE_LOCAL)
        val stream = track(id = 3, title = "Radio", source = TrackEntity.SOURCE_STREAM)
        val later = track(id = 4, title = "Night", source = TrackEntity.SOURCE_LOCAL)
        val (queue, index) = listOf(song, stream, later).playbackQueue(later)
        assertEquals(listOf(1L, 4L), queue.map { it.id })
        assertEquals(1, index)
    }

    private fun track(
        id: Long,
        title: String,
        source: String,
    ) = TrackEntity(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        uri = if (source == TrackEntity.SOURCE_STREAM) "https://radio.example/live" else "file:///music/$id.mp3",
        duration = if (source == TrackEntity.SOURCE_STREAM) 0L else 180_000L,
        source = source,
    )
}
