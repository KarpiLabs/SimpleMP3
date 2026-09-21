package io.karpilabs.simplemp3.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class YoutubePathTraversalTest {

    @Test
    fun testSanitizeVideoId_normalId() {
        assertEquals("dQw4w9WgXcQ", YoutubeDownloadManager.sanitizeVideoId("dQw4w9WgXcQ"))
        assertEquals("abc_123-XYZ", YoutubeDownloadManager.sanitizeVideoId("abc_123-XYZ"))
    }

    @Test
    fun testSanitizeVideoId_pathTraversal() {
        assertEquals("passwd", YoutubeDownloadManager.sanitizeVideoId("../../etc/passwd"))
        assertEquals("secret", YoutubeDownloadManager.sanitizeVideoId("..\\..\\secret"))
        assertEquals("test", YoutubeDownloadManager.sanitizeVideoId("....//test"))
    }

    @Test
    fun testSanitizeVideoId_specialCharacters() {
        assertEquals("video_id_123", YoutubeDownloadManager.sanitizeVideoId("video?id=123"))
        assertEquals("video_123_", YoutubeDownloadManager.sanitizeVideoId("video#123!"))
    }

    @Test
    fun testSanitizeVideoId_blankFallback() {
        assertEquals("unknown_id", YoutubeDownloadManager.sanitizeVideoId("../.."))
        assertEquals("unknown_id", YoutubeDownloadManager.sanitizeVideoId("   "))
        assertEquals("unknown_id", YoutubeDownloadManager.sanitizeVideoId("..."))
    }
}
