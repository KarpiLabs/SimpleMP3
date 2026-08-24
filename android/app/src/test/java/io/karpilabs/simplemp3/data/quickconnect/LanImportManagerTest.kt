package io.karpilabs.simplemp3.data.quickconnect

import org.junit.Assert.assertEquals
import org.junit.Test

class LanImportManagerTest {

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
    }

    @Test
    fun testSanitizeFileName_specialCharactersAndLongNames() {
        assertEquals("song_name__.mp3", LanImportManager.sanitizeFileName("song<name>?.mp3"))
        assertEquals("test__file.mp3", LanImportManager.sanitizeFileName("test$#file.mp3"))
    }
}
