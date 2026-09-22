package io.karpilabs.simplemp3.data.quickconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LanImportManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testSanitizeFileName_normalFilename() {
        assertEquals("song.mp3", LanImportManager.sanitizeFileName("song.mp3"))
        assertEquals("My Song - Track 01.flac", LanImportManager.sanitizeFileName("My Song - Track 01.flac"))
    }

    @Test
    fun testSanitizeFileName_pathTraversal() {
        assertEquals("passwd.mp3", LanImportManager.sanitizeFileName("../../etc/passwd.mp3"))
        assertEquals("secret.mp3", LanImportManager.sanitizeFileName("..\\..\\secret.mp3"))
        assertEquals("test.mp3", LanImportManager.sanitizeFileName("....//test.mp3"))
        assertEquals("file.mp3", LanImportManager.sanitizeFileName("..\\path..\\..\\file.mp3"))
    }

    @Test
    fun testSanitizeFileName_hiddenFilesAndLeadingDots() {
        assertEquals("bashrc.mp3", LanImportManager.sanitizeFileName(".bashrc.mp3"))
        assertEquals("hidden_song.mp3", LanImportManager.sanitizeFileName("...hidden_song.mp3"))
    }

    @Test
    fun testSanitizeFileName_blankOrUnsafeFallback() {
        assertEquals("upload.mp3", LanImportManager.sanitizeFileName("../.."))
        assertEquals("upload.mp3", LanImportManager.sanitizeFileName("   "))
        assertEquals("upload.mp3", LanImportManager.sanitizeFileName("..."))
    }

    @Test
    fun testSanitizeFileName_additionalPathTraversalCases() {
        assertEquals("passwd.mp3", LanImportManager.sanitizeFileName("/etc/passwd.mp3"))
        assertEquals("config.txt", LanImportManager.sanitizeFileName("../../../sys/config.txt"))
        assertEquals("malicious.mp3", LanImportManager.sanitizeFileName("..\\..\\..\\sdcard\\Download\\malicious.mp3"))
    }

    @Test
    fun testSanitizeFileName_specialCharactersAndLongNames() {
        assertEquals("song_name__.mp3", LanImportManager.sanitizeFileName("song<name>?.mp3"))
        assertEquals("test__file.mp3", LanImportManager.sanitizeFileName("test$#file.mp3"))
    }

    @Test
    fun testDeleteFileUriSafely_preventsPathTraversal() {
        val filesDir = tempFolder.newFolder("files")
        val allowedAudioDir = File(filesDir, "offline/lan/audio").also { it.mkdirs() }
        val sensitiveDir = File(filesDir, "databases").also { it.mkdirs() }

        val sensitiveFile = File(sensitiveDir, "user_data.db").also {
            it.writeText("sensitive data")
        }
        assertTrue("Sensitive file should exist before deletion attempt", sensitiveFile.exists())

        val validTrackFile = File(allowedAudioDir, "valid_track.mp3").also {
            it.writeText("audio content")
        }
        assertTrue("Valid track file should exist before deletion attempt", validTrackFile.exists())

        // Attempt path traversal deletion outside allowed directory
        val maliciousUri = sensitiveFile.toURI().toString()
        LanImportManager.deleteFileUriSafely(maliciousUri, allowedAudioDir)

        assertTrue("Sensitive file outside allowed directory should NOT be deleted", sensitiveFile.exists())

        // Delete valid track file inside allowed directory
        val validUri = validTrackFile.toURI().toString()
        LanImportManager.deleteFileUriSafely(validUri, allowedAudioDir)

        assertFalse("Valid track file inside allowed directory SHOULD be deleted", validTrackFile.exists())
    }
}
