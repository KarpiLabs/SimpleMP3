package io.karpilabs.simplemp3.data.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LargeFileStorageManagerPathTraversalTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var baseAppDir: File
    private lateinit var filesDir: File
    private lateinit var sensitiveOutsideDir: File

    @Before
    fun setUp() {
        baseAppDir = tempFolder.newFolder("app_data")
        filesDir = File(baseAppDir, "files").also { it.mkdirs() }
        sensitiveOutsideDir = tempFolder.newFolder("system_sensitive")
    }

    @Test
    fun testIsPathWithinBaseDir_preventsTraversalOutsideAppData() {
        fun isWithinBaseDir(file: File, base: File): Boolean {
            val target = file.canonicalFile
            val allowed = base.canonicalFile
            return target.path.startsWith(allowed.path + File.separator)
        }

        val validHotFile = File(filesDir, "storage/warm/12345.mp3").also {
            it.parentFile?.mkdirs()
            it.writeText("audio content")
        }
        val maliciousFile = File(sensitiveOutsideDir, "etc_passwd").also {
            it.writeText("sensitive content")
        }

        assertTrue("File inside app_data is permitted", isWithinBaseDir(validHotFile, baseAppDir))
        assertFalse("File outside app_data is blocked", isWithinBaseDir(maliciousFile, baseAppDir))
    }

    @Test
    fun testIsPathWithinFilesDir_preventsRelativePathTraversalAndPrefixCollisions() {
        fun isWithinFilesDir(file: File, allowedBase: File): Boolean {
            val target = file.canonicalFile
            val allowed = allowedBase.canonicalFile
            return target.path.startsWith(allowed.path + File.separator)
        }

        val relativeTraversalFile = File(filesDir, "storage/warm/../../../../etc/passwd")
        val siblingDir = File(baseAppDir, "files_sibling").also { it.mkdirs() }
        val siblingFile = File(siblingDir, "secret.mp3").also { it.writeText("secret") }

        assertFalse("Relative traversal with .. is blocked", isWithinFilesDir(relativeTraversalFile, filesDir))
        assertFalse("Sibling directory matching prefix without separator is blocked", isWithinFilesDir(siblingFile, filesDir))
    }
}
