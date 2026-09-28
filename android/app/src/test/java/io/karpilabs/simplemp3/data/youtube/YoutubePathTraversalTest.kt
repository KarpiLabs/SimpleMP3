package io.karpilabs.simplemp3.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YoutubePathTraversalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var allowedOfflineDir: File
    private lateinit var sensitiveDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        allowedOfflineDir = File(filesDir, "offline/youtube/audio").also { it.mkdirs() }
        sensitiveDir = File(filesDir, "databases").also { it.mkdirs() }
    }

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

    @Test
    fun testDeleteFileUriSafely_preventsPathTraversal() {
        val sensitiveFile = File(sensitiveDir, "app.db").also {
            it.writeText("sensitive data")
        }
        assertTrue("Sensitive file should exist before test", sensitiveFile.exists())

        val validTrackFile = File(allowedOfflineDir, "valid_track.mp3").also {
            it.writeText("audio data")
        }
        assertTrue("Valid track file should exist before test", validTrackFile.exists())

        val allowedBaseDir = File(filesDir, "offline/youtube").canonicalFile

        // Attempt deletion using path traversal URI pointing to sensitive file
        val maliciousUri = "file://${sensitiveFile.canonicalPath}"
        YoutubeDownloadManager.deleteFileUriSafely(maliciousUri, allowedBaseDir)

        assertTrue("Sensitive file should still exist after path traversal deletion attempt", sensitiveFile.exists())

        // Attempt deletion of valid track file via URI
        val validUri = "file://${validTrackFile.canonicalPath}"
        YoutubeDownloadManager.deleteFileUriSafely(validUri, allowedBaseDir)

        assertFalse("Valid track file should be deleted", validTrackFile.exists())
    }

    @Test
    fun testDeleteFileUriSafely_ignoresNonFileScheme() {
        val sensitiveFile = File(sensitiveDir, "secret.key").also {
            it.writeText("secret")
        }
        val allowedBaseDir = File(filesDir, "offline/youtube").canonicalFile

        // Attempt deletion with http scheme pointing to file path
        val httpUri = "https://example.com/${sensitiveFile.canonicalPath}"
        YoutubeDownloadManager.deleteFileUriSafely(httpUri, allowedBaseDir)

        assertTrue("Non-file URI must not delete sensitive file", sensitiveFile.exists())
    }
}
