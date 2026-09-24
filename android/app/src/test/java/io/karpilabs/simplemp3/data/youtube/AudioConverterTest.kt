package io.karpilabs.simplemp3.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AudioConverterTest {

    @Test
    fun testBuildRemuxArgs_keepsMetadataAsDiscreteArrayElements() {
        val metadata = AudioConverter.Metadata(
            title = "Track \"Title\" with 'Quotes' & \n Newline - --option",
            artist = "Artist; rm -rf /; $(whoami)",
            album = "Album with \"Double Quotes\" and --flags"
        )
        val outputFile = File("/tmp/output.m4a")

        val args = AudioConverter.buildRemuxArgs("https://example.com/stream.m3u8", outputFile, metadata)

        // Verify that metadata values are distinct array elements and sanitized
        val titleIndex = args.indexOf("-metadata")
        assertTrue("Expected -metadata flag in args", titleIndex >= 0)
        assertTrue("Expected -metadata title= argument in args", args.contains("title=${metadata.sanitizedTitle}"))
        assertTrue("Expected -metadata artist= argument in args", args.contains("artist=${metadata.sanitizedArtist}"))
        assertTrue("Expected -metadata album= argument in args", args.contains("album=${metadata.sanitizedAlbum}"))
        assertEquals("-y", args[0])
        assertEquals("-i", args[1])
        assertEquals("https://example.com/stream.m3u8", args[2])
    }

    @Test
    fun testMetadataSanitizesControlCharactersAndNewlines() {
        val metadata = AudioConverter.Metadata(
            title = "Line1\r\nLine2\t\u0000Injected",
            artist = "\r\n  New Artist \t",
            album = "\u0007"
        )
        assertEquals("Line1 Line2 Injected", metadata.sanitizedTitle)
        assertEquals("New Artist", metadata.sanitizedArtist)
        assertEquals("YouTube", metadata.sanitizedAlbum)
    }

    @Test
    fun testBuildMp3Args_keepsMetadataAsDiscreteArrayElements() {
        val metadata = AudioConverter.Metadata(
            title = "Track \"Title\" with 'Quotes'",
            artist = "Artist; rm -rf /",
            album = "Album --flag"
        )
        val inputFile = File("/tmp/input.wav")
        val outputFile = File("/tmp/output.mp3")

        val args = AudioConverter.buildMp3Args(
            input = inputFile,
            outputMp3 = outputFile,
            metadata = metadata,
            coverImage = null,
            audioArgs = listOf("-c:a", "libmp3lame", "-q:a", "0"),
            withCover = false
        )

        assertTrue("Expected -metadata title= argument in args", args.contains("title=${metadata.sanitizedTitle}"))
        assertTrue("Expected -metadata artist= argument in args", args.contains("artist=${metadata.sanitizedArtist}"))
        assertTrue("Expected -metadata album= argument in args", args.contains("album=${metadata.sanitizedAlbum}"))
        assertEquals(outputFile.absolutePath, args.last())
    }

    @Test
    fun testIsValidInputSource_acceptsSafeSchemesAndLocalPaths() {
        assertTrue(AudioConverter.isValidInputSource("http://example.com/stream.m3u8"))
        assertTrue(AudioConverter.isValidInputSource("https://example.com/stream.m3u8"))
        assertTrue(AudioConverter.isValidInputSource("file:///sdcard/Download/test.m4a"))
        assertTrue(AudioConverter.isValidInputSource("/storage/emulated/0/Music/song.mp3"))
        assertTrue(AudioConverter.isValidInputSource("relative/path/to/song.flac"))
    }

    @Test
    fun testIsValidInputSource_rejectsDangerousSchemes() {
        assertFalse(AudioConverter.isValidInputSource("concat:file1|file2"))
        assertFalse(AudioConverter.isValidInputSource("pipe:0"))
        assertFalse(AudioConverter.isValidInputSource("unix:/tmp/socket"))
        assertFalse(AudioConverter.isValidInputSource("subfile:0"))
        assertFalse(AudioConverter.isValidInputSource("fd:0"))
        assertFalse(AudioConverter.isValidInputSource(""))
    }

    @Test
    fun testRemuxToM4a_failsOnDisallowedScheme() {
        val metadata = AudioConverter.Metadata(title = "Test", artist = "Test")
        val outputFile = File("/tmp/output.m4a")

        val result = AudioConverter.remuxToM4a("concat:file1|file2", outputFile, metadata)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Invalid or unsupported input source scheme", result.exceptionOrNull()?.message)
    }
}
