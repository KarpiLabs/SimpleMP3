package io.karpilabs.simplemp3.data.scrobble

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrobbleClientsTest {

    @Test
    fun testGetMobileSessionFailureDoesNotThrowOrExposeSecret() {
        var requestedUrl = ""
        val mockInterceptor = Interceptor { chain ->
            requestedUrl = chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("Internal Server Error".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val client = LastFmClient(okHttpClient)

        val result = client.getMobileSession(
            apiKey = "test_key",
            apiSecret = "super_secret_password_123",
            username = "test_user",
            password = "secret_password"
        )

        assertNull(result)
        assertTrue(requestedUrl.contains("ws.audioscrobbler.com"))
    }

    @Test
    fun testScrobbleEmptyListReturnsTrue() {
        val client = LastFmClient(OkHttpClient())
        val result = client.scrobble(
            apiKey = "key",
            apiSecret = "secret",
            sessionKey = "session",
            scrobbles = emptyList()
        )
        assertTrue(result)
    }
}
