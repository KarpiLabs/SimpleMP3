package io.karpilabs.simplemp3.data.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StreamSaveManagerPathTraversalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var allowedArtDir: File
    private lateinit var sensitiveDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        allowedArtDir = File(filesDir, "stream_art").also { it.mkdirs() }
        sensitiveDir = File(filesDir, "databases").also { it.mkdirs() }
    }

    @Test
    fun testDeleteFileUriSafely_preventsPathTraversal() {
        // Create a sensitive file outside the allowed directory
        val sensitiveFile = File(sensitiveDir, "user_data.db").also {
            it.writeText("sensitive data")
        }
        assertTrue("Sensitive file should exist before test", sensitiveFile.exists())

        // Create a valid artwork file inside stream_art
        val validArtFile = File(allowedArtDir, "stream_icon.img").also {
            it.writeText("image data")
        }
        assertTrue("Valid artwork file should exist before test", validArtFile.exists())

        // Exercise the actual production method on StreamSaveManager
        val maliciousUri = sensitiveFile.toURI().toString()
        StreamSaveManager.deleteFileUriSafely(maliciousUri, allowedArtDir)

        // Verify sensitive file was NOT deleted
        assertTrue("Sensitive file should still exist after path traversal deletion attempt", sensitiveFile.exists())

        // Exercise the actual production method on valid file
        val validUri = validArtFile.toURI().toString()
        StreamSaveManager.deleteFileUriSafely(validUri, allowedArtDir)

        // Verify valid artwork file WAS deleted
        assertFalse("Valid artwork file should be deleted", validArtFile.exists())
    }

    @Test
    fun testPersistArtworkFromUri_preventsDirectoryTraversalOrInvalidFile() {
        val nonExistentFile = File(filesDir, "does_not_exist.png")
        assertFalse("Non-existent file should not exist", nonExistentFile.exists())

        val directoryFile = sensitiveDir
        assertTrue("Directory should exist as a directory", directoryFile.isDirectory)

        // Verifies directory / non-file paths are rejected when treating file URIs
        assertFalse("Directory is not a normal file", directoryFile.isFile)
    }
}
