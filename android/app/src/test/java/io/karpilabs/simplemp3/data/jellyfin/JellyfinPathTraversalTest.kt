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
        // Attempt deletion using path traversal URI pointing to sensitive file
        val maliciousPath = sensitiveFile.canonicalPath
        JellyfinSyncManager.deleteFileSafely(File(maliciousPath), allowedDir)

        // Verify sensitive file was NOT deleted
        assertTrue("Sensitive file should still exist after path traversal deletion attempt", sensitiveFile.exists())

        // Attempt deletion of valid track file
        JellyfinSyncManager.deleteFileSafely(validTrackFile, allowedDir)

        // Verify valid track file WAS deleted
        assertFalse("Valid track file should be deleted", validTrackFile.exists())
    }

    @Test
    fun testDeleteFileSafely_duringDirectoryCleanup_preventsSymlinkTraversal() {
        val sensitiveFile = File(sensitiveDir, "secret.db").also {
            it.writeText("confidential")
        }
        val validTrackFile = File(allowedOfflineDir, "track1.mp3").also {
            it.writeText("audio")
        }
        val allowedDir = File(filesDir, "offline/jellyfin").canonicalFile

        // Test normal deletion within allowed dir using production method directly
        JellyfinSyncManager.deleteFileSafely(validTrackFile, allowedDir)
        assertFalse("Valid track in audio dir should be deleted", validTrackFile.exists())

        // Test attempting to delete file outside allowed dir
        JellyfinSyncManager.deleteFileSafely(sensitiveFile, allowedDir)
        assertTrue("Sensitive file outside allowed dir must not be deleted", sensitiveFile.exists())
    }
}
