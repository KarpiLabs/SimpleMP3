package io.karpilabs.simplemp3.data.youtube

import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Reproduction probe: hits the real YouTube extraction pipeline (network required) to
 * confirm at least one audio stream is returned for a known-good video.
 */
class YoutubeAudioStreamExtractionTest {

    @Test
    fun `extracts at least one audio stream for known video`() {
        NewPipe.init(YoutubeDownloaderImpl(OkHttpClient()))

        val url = "https://www.youtube.com/watch?v=-6Kxx19ejog"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)

        println("audioStreams=${info.audioStreams.size} videoStreams=${info.videoStreams.size} videoOnly=${info.videoOnlyStreams.size}")
        info.audioStreams.forEach {
            println("  audio: format=${it.format} bitrate=${it.averageBitrate} url=${it.content.take(80)}")
        }

        assertTrue(
            "Expected at least one audio stream, got ${info.audioStreams.size}",
            info.audioStreams.isNotEmpty()
        )
    }
}
