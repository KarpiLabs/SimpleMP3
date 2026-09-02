package io.karpilabs.simplemp3.data.youtube

import org.junit.Assert.assertEquals
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

        // Verify that metadata values are distinct array elements (not string concatenated into commands)
        val titleIndex = args.indexOf("-metadata")
        assertTrue("Expected -metadata flag in args", titleIndex >= 0)
        assertTrue("Expected -metadata title= argument in args", args.contains("title=${metadata.title}"))
        assertTrue("Expected -metadata artist= argument in args", args.contains("artist=${metadata.artist}"))
        assertTrue("Expected -metadata album= argument in args", args.contains("album=${metadata.album}"))
        assertEquals("-y", args[0])
        assertEquals("-i", args[1])
        assertEquals("https://example.com/stream.m3u8", args[2])
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

        assertTrue("Expected -metadata title= argument in args", args.contains("title=${metadata.title}"))
        assertTrue("Expected -metadata artist= argument in args", args.contains("artist=${metadata.artist}"))
        assertTrue("Expected -metadata album= argument in args", args.contains("album=${metadata.album}"))
        assertEquals(outputFile.absolutePath, args.last())
    }
}
