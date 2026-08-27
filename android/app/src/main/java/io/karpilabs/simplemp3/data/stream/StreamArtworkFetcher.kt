package io.karpilabs.simplemp3.data.stream

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort artwork for a live stream: embedded pictures, ICY station pages,
 * HLS playlist logos, then Open Graph images. Never throws — a miss just
 * returns null so saving the stream bookmark still succeeds.
 */
@Singleton
class StreamArtworkFetcher
    @Inject
    constructor(
        client: OkHttpClient,
    ) {
        private val probe =
            client
                .newBuilder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(6, TimeUnit.SECONDS)
                .writeTimeout(6, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

        data class Probe(
            val icyName: String? = null,
            val bytes: ByteArray? = null,
        )

        suspend fun capture(streamUrl: String): Probe =
            withContext(Dispatchers.IO) {
                val embedded = withTimeoutOrNull(3_500) { embeddedPicture(streamUrl) }
                if (embedded != null) return@withContext Probe(bytes = embedded)

                val headers = icyHeaders(streamUrl)
                headers.imageUrl?.let { url ->
                    downloadImage(url)?.let { return@withContext Probe(icyName = headers.name, bytes = it) }
                }
                headers.stationUrl?.let { page ->
                    imageFromHtmlPage(page)?.let {
                        return@withContext Probe(icyName = headers.name, bytes = it)
                    }
                }
                if (StreamArtworkParser.isHlsUrl(streamUrl)) {
                    hlsImage(streamUrl)?.let {
                        return@withContext Probe(icyName = headers.name, bytes = it)
                    }
                }
                imageFromHtmlPage(originOf(streamUrl))?.let {
                    return@withContext Probe(icyName = headers.name, bytes = it)
                }
                Probe(icyName = headers.name)
            }

        private fun embeddedPicture(url: String): ByteArray? =
            runCatching {
                MediaMetadataRetriever().use { mmr ->
                    mmr.setDataSource(url, hashMapOf("User-Agent" to USER_AGENT))
                    mmr.embeddedPicture?.takeIf { StreamArtworkParser.looksLikeImage(it) }
                }
            }.getOrNull()

        private data class IcyHeaders(
            val name: String?,
            val stationUrl: String?,
            val imageUrl: String?,
        )

        private fun icyHeaders(url: String): IcyHeaders {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Icy-MetaData", "1")
                    .get()
                    .build()
            return runCatching {
                probe.newCall(request).execute().use { response ->
                    fun header(name: String): String? =
                        response.header(name)?.trim()?.takeIf { it.isNotBlank() && it != "0" }
                    IcyHeaders(
                        name = header("icy-name") ?: header("ice-name"),
                        stationUrl = header("icy-url") ?: header("ice-url"),
                        imageUrl =
                            header("icy-logo")
                                ?: header("ice-logo")
                                ?: header("icy-image"),
                    )
                }
            }.getOrDefault(IcyHeaders(null, null, null))
        }

        private fun hlsImage(url: String): ByteArray? {
            val body = downloadText(url) ?: return null
            val imageUrl = StreamArtworkParser.imageUrlFromHls(body, url) ?: return null
            return downloadImage(imageUrl)
        }

        private fun imageFromHtmlPage(pageUrl: String?): ByteArray? {
            if (pageUrl.isNullOrBlank()) return null
            if (!pageUrl.startsWith("http://", true) && !pageUrl.startsWith("https://", true)) return null
            val html = downloadText(pageUrl) ?: return null
            val imageUrl = StreamArtworkParser.imageUrlFromHtml(html, pageUrl) ?: return null
            return downloadImage(imageUrl)
        }

        private fun downloadText(url: String): String? {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
            return runCatching {
                probe.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val type = response.header("Content-Type").orEmpty()
                    if (type.startsWith("audio/", ignoreCase = true) ||
                        type.startsWith("video/", ignoreCase = true)
                    ) {
                        return@use null
                    }
                    response.body?.string()?.take(MAX_HTML_CHARS)
                }
            }.getOrNull()
        }

        private fun downloadImage(url: String): ByteArray? {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
            return runCatching {
                probe.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val bytes = response.body?.bytes() ?: return@use null
                    if (bytes.size > MAX_IMAGE_BYTES) return@use null
                    bytes.takeIf { StreamArtworkParser.looksLikeImage(it) }
                }
            }.getOrNull()
        }

        private fun originOf(url: String): String? =
            runCatching {
                val uri = java.net.URI(url)
                "${uri.scheme}://${uri.authority}/"
            }.getOrNull()

        companion object {
            private const val USER_AGENT = "SimpleMP3/1.1"
            private const val MAX_HTML_CHARS = 256_000
            private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        }
    }
