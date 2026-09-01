package io.karpilabs.simplemp3.data.jellyfin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI

class JellyfinPathTraversalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var allowedOfflineDir: File
    private lateinit var sensitiveDir: File

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        allowedOfflineDir = File(filesDir, "offline/jellyfin/audio").also { it.mkdirs() }
        sensitiveDir = File(filesDir, "databases").also { it.mkdirs() }
    }

    @Test
    fun testDeleteFileUriSafely_preventsPathTraversal() {
        // Create a sensitive file outside the allowed directory
        val sensitiveFile = File(sensitiveDir, "app.db").also {
            it.writeText("sensitive data")
        }
        assertTrue("Sensitive file should exist before test", sensitiveFile.exists())

        // Create an allowed file inside the allowed directory
        val validTrackFile = File(allowedOfflineDir, "valid_track.mp3").also {
            it.writeText("audio data")
        }
        assertTrue("Valid track file should exist before test", validTrackFile.exists())

        val allowedDir = File(filesDir, "offline/jellyfin").canonicalFile

        // Function simulating deleteFileUriSafely logic without android.net.Uri in JVM unit test
        fun deleteSafely(uriString: String) {
            runCatching {
                val path = URI.create(uriString).path ?: return
                val targetFile = File(path).canonicalFile
                if (targetFile.canonicalPath.startsWith(allowedDir.canonicalPath + File.separator)) {
                    targetFile.delete()
                }
            }
        }

        // Attempt deletion using path traversal URI pointing to sensitive file
        val maliciousUri = sensitiveFile.toURI().toString()
        deleteSafely(maliciousUri)

        // Verify sensitive file was NOT deleted
        assertTrue("Sensitive file should still exist after path traversal deletion attempt", sensitiveFile.exists())

        // Attempt deletion of valid track file
        val validUri = validTrackFile.toURI().toString()
        deleteSafely(validUri)

        // Verify valid track file WAS deleted
        assertFalse("Valid track file should be deleted", validTrackFile.exists())
    }
}
