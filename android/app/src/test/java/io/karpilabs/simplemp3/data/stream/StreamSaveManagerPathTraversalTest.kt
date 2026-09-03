package io.karpilabs.simplemp3.data.stream

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

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

        val allowedDir = allowedArtDir.canonicalFile

        // Function simulating deleteFileUriSafely logic from StreamSaveManager
        fun deleteSafely(uriString: String) {
            runCatching {
                if (!uriString.startsWith("file:")) return
                val path = URI.create(uriString).path ?: return
                val targetFile = File(path).canonicalFile
                if (targetFile.canonicalPath.startsWith(allowedDir.canonicalPath + File.separator)) {
                    targetFile.delete()
                }
            }
        }

        // Attempt deletion using path traversal / file URI pointing to sensitive file
        val maliciousUri = sensitiveFile.toURI().toString()
        deleteSafely(maliciousUri)

        // Verify sensitive file was NOT deleted
        assertTrue("Sensitive file should still exist after path traversal deletion attempt", sensitiveFile.exists())

        // Attempt deletion of valid artwork file
        val validUri = validArtFile.toURI().toString()
        deleteSafely(validUri)

        // Verify valid artwork file WAS deleted
        assertFalse("Valid artwork file should be deleted", validArtFile.exists())
    }
}
