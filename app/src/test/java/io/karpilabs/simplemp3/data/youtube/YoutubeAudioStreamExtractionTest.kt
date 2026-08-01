package io.karpilabs.simplemp3.data.youtube

import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Optional live probe against YouTube via NewPipeExtractor.
 *
 * Not run on CI: GitHub-hosted runners are frequently challenged as bots
 * ([SignInConfirmNotBotException]), which is unrelated to app correctness.
 * Run locally when debugging extraction regressions:
 * `./gradlew testDebugUnitTest --tests '*.YoutubeAudioStreamExtractionTest'`
 */
class YoutubeAudioStreamExtractionTest {

    @Test
    fun `extracts at least one audio stream for known video`() {
        assumeFalse(
            "Skip live YouTube probe on CI (bot challenges / no stable network contract)",
            isCi()
        )

        NewPipe.init(YoutubeDownloaderImpl(OkHttpClient()))

        val url = "https://www.youtube.com/watch?v=-6Kxx19ejog"
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: SignInConfirmNotBotException) {
            assumeNoException("YouTube bot check blocked extraction; not an app regression", e)
            return
        } catch (e: ExtractionException) {
            assumeNoException("YouTube extraction unavailable in this environment", e)
            return
        }

        println(
            "audioStreams=${info.audioStreams.size} " +
                "videoStreams=${info.videoStreams.size} " +
                "videoOnly=${info.videoOnlyStreams.size}"
        )
        info.audioStreams.forEach {
            println(
                "  audio: format=${it.format} bitrate=${it.averageBitrate} " +
                    "url=${it.content.take(80)}"
            )
        }

        assertTrue(
            "Expected at least one audio stream, got ${info.audioStreams.size}",
            info.audioStreams.isNotEmpty()
        )
    }

    private fun isCi(): Boolean {
        val keys = listOf("CI", "GITHUB_ACTIONS", "GITLAB_CI", "CIRCLECI", "TF_BUILD")
        return keys.any { key ->
            System.getenv(key)?.equals("true", ignoreCase = true) == true
        }
    }
}
