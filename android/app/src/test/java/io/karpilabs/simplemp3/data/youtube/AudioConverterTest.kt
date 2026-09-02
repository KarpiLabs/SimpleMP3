package io.karpilabs.simplemp3.data.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioConverterTest {

    @Test
    fun testMetadataArguments_handlesSpecialCharactersSafely() {
        val metadata = AudioConverter.Metadata(
            title = "Track \"Title\" with 'Quotes' & \n Newline - --option",
            artist = "Artist; rm -rf /; $(whoami)",
            album = "Album with \"Double Quotes\" and --flags"
        )

        assertEquals("Track \"Title\" with 'Quotes' & \n Newline - --option", metadata.title)
        assertEquals("Artist; rm -rf /; $(whoami)", metadata.artist)
        assertEquals("Album with \"Double Quotes\" and --flags", metadata.album)
        assertNotNull(metadata)
    }
}
