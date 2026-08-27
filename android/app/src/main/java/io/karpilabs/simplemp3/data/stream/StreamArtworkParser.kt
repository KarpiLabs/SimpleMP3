package io.karpilabs.simplemp3.data.stream

/**
 * Pulls a station / stream image URL out of HTML or an HLS playlist. Pure string
 * parsing so it can be unit-tested without Android or the network.
 */
object StreamArtworkParser {
    private val OG_IMAGE_PROP_FIRST =
        Regex(
            """<meta[^>]+property\s*=\s*["']og:image["'][^>]+content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
    private val OG_IMAGE_CONTENT_FIRST =
        Regex(
            """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]+property\s*=\s*["']og:image["']""",
            RegexOption.IGNORE_CASE,
        )
    private val TWITTER_IMAGE =
        Regex(
            """<meta[^>]+name\s*=\s*["']twitter:image["'][^>]+content\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
    private val APPLE_TOUCH =
        Regex(
            """<link[^>]+rel\s*=\s*["'][^"']*apple-touch-icon[^"']*["'][^>]+href\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
    private val TVG_LOGO =
        Regex(
            """tvg-logo\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
    private val IMAGE_URL_IN_TEXT =
        Regex(
            """https?://[^\s"'<>\\]+?\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )

    fun imageUrlFromHtml(
        html: String,
        baseUrl: String,
    ): String? {
        val raw =
            OG_IMAGE_PROP_FIRST.find(html)?.groupValues?.getOrNull(1)
                ?: OG_IMAGE_CONTENT_FIRST.find(html)?.groupValues?.getOrNull(1)
                ?: TWITTER_IMAGE.find(html)?.groupValues?.getOrNull(1)
                ?: APPLE_TOUCH.find(html)?.groupValues?.getOrNull(1)
                ?: return null
        return resolveUrl(raw.trim(), baseUrl)
    }

    fun imageUrlFromHls(
        playlist: String,
        baseUrl: String,
    ): String? {
        TVG_LOGO.find(playlist)?.groupValues?.getOrNull(1)?.let { logo ->
            return resolveUrl(logo.trim(), baseUrl)
        }
        IMAGE_URL_IN_TEXT.find(playlist)?.value?.let { return it }
        return null
    }

    fun resolveUrl(
        maybeRelative: String,
        baseUrl: String,
    ): String {
        val trimmed = maybeRelative.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val base = java.net.URI(baseUrl)
        return base.resolve(trimmed).toString()
    }

    fun isHlsUrl(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?')
        return path.endsWith(".m3u8", ignoreCase = true)
    }

    fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        // JPEG
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
        // PNG
        if (bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return true
        }
        // GIF
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        // WEBP: RIFF....WEBP
        if (bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return true
        }
        return false
    }

    fun imageExtension(bytes: ByteArray): String =
        when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
            bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
            bytes.size >= 3 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() -> "gif"
            bytes.size >= 12 && bytes[8] == 0x57.toByte() -> "webp"
            else -> "img"
        }
}
