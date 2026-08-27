package io.karpilabs.simplemp3.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamPlaybackTest {
    @Test
    fun `formats kbps`() {
        assertEquals("128 kbps", StreamPlayback.formatDataRate(128_000))
        assertEquals("96 kbps", StreamPlayback.formatDataRate(96_400))
        assertEquals("1 kbps", StreamPlayback.formatDataRate(1_000))
    }

    @Test
    fun `formats mbps`() {
        assertEquals("1.5 Mbps", StreamPlayback.formatDataRate(1_500_000))
        assertEquals("10 Mbps", StreamPlayback.formatDataRate(10_000_000))
    }

    @Test
    fun `live label prefers encoded bitrate`() {
        assertEquals(
            "Live · 128 kbps",
            StreamPlayback.dataRateLabel(isLive = true, bitrateBps = 128_000, throughputBps = 400_000),
        )
    }

    @Test
    fun `live label falls back to throughput`() {
        assertEquals(
            "Live · 160 kbps",
            StreamPlayback.dataRateLabel(isLive = true, bitrateBps = 0, throughputBps = 160_000),
        )
    }

    @Test
    fun `live without rate still says Live`() {
        assertEquals("Live", StreamPlayback.dataRateLabel(isLive = true, bitrateBps = 0, throughputBps = 0))
    }

    @Test
    fun `non-live has no label`() {
        assertNull(StreamPlayback.dataRateLabel(isLive = false, bitrateBps = 128_000, throughputBps = 0))
    }
}
