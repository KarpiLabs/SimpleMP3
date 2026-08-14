package io.karpilabs.simplemp3.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure unit tests for YouTube URL / video-id parsing (no network).
 */
class YoutubeUrlParseTest {
    @Test
    fun `extracts id from watch URL`() {
        assertEquals(
            "-6Kxx19ejog",
            YoutubeDownloadManager.extractVideoId("https://www.youtube.com/watch?v=-6Kxx19ejog"),
        )
    }

    @Test
    fun `extracts id from short youtu be URL`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeDownloadManager.extractVideoId("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `extracts id from shorts URL`() {
        assertEquals(
            "abc123XYZ00",
            YoutubeDownloadManager.extractVideoId("https://www.youtube.com/shorts/abc123XYZ00"),
        )
    }

    @Test
    fun `extracts id from embed URL`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeDownloadManager.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `returns null for non youtube URL`() {
        assertNull(YoutubeDownloadManager.extractVideoId("https://example.com/watch?v=nope"))
    }

    @Test
    fun `returns null for blank`() {
        assertNull(YoutubeDownloadManager.extractVideoId(""))
        assertNull(YoutubeDownloadManager.extractVideoId("   "))
    }
}
