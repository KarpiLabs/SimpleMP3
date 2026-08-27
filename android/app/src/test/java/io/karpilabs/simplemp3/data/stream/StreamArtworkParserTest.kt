package io.karpilabs.simplemp3.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamArtworkParserTest {
    @Test
    fun `og image property first`() {
        val html =
            """<html><head><meta property="og:image" content="https://station.example/cover.jpg"></head></html>"""
        assertEquals(
            "https://station.example/cover.jpg",
            StreamArtworkParser.imageUrlFromHtml(html, "https://station.example/"),
        )
    }

    @Test
    fun `og image content first`() {
        val html =
            """<meta content="https://cdn.example/art.png" property="og:image">"""
        assertEquals(
            "https://cdn.example/art.png",
            StreamArtworkParser.imageUrlFromHtml(html, "https://station.example/"),
        )
    }

    @Test
    fun `resolves relative apple touch icon`() {
        val html = """<link rel="apple-touch-icon" href="/icon.png">"""
        assertEquals(
            "https://radio.example/icon.png",
            StreamArtworkParser.imageUrlFromHtml(html, "https://radio.example/listen"),
        )
    }

    @Test
    fun `hls tvg-logo`() {
        val playlist = """#EXTM3U
#EXTINF:-1 tvg-logo="https://img.example/logo.png",Station
http://radio.example/stream
"""
        assertEquals(
            "https://img.example/logo.png",
            StreamArtworkParser.imageUrlFromHls(playlist, "https://radio.example/playlist.m3u8"),
        )
    }

    @Test
    fun `hls image url in playlist`() {
        val playlist = "#EXTM3U\n#ARTWORK:https://cdn.example/show.webp?w=200\n"
        assertEquals(
            "https://cdn.example/show.webp?w=200",
            StreamArtworkParser.imageUrlFromHls(playlist, "https://radio.example/live.m3u8"),
        )
    }

    @Test
    fun `missing image returns null`() {
        assertNull(StreamArtworkParser.imageUrlFromHtml("<html></html>", "https://x.example/"))
        assertNull(StreamArtworkParser.imageUrlFromHls("#EXTM3U\n#EXTINF:10,a\n", "https://x.example/a.m3u8"))
    }

    @Test
    fun `hls url detection ignores query`() {
        assertTrue(StreamArtworkParser.isHlsUrl("https://radio.example/live/playlist.m3u8?token=1"))
        assertFalse(StreamArtworkParser.isHlsUrl("https://radio.example/live.mp3"))
    }

    @Test
    fun `jpeg magic`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(16)
        assertTrue(StreamArtworkParser.looksLikeImage(jpeg))
        assertEquals("jpg", StreamArtworkParser.imageExtension(jpeg))
        assertFalse(StreamArtworkParser.looksLikeImage(byteArrayOf(1, 2, 3, 4)))
    }
}
