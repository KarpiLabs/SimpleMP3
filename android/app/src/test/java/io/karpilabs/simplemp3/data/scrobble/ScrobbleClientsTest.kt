package io.karpilabs.simplemp3.data.scrobble

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ScrobbleClientsTest {

    @Test
    fun testGetMobileSession_hashesPasswordWithMd5() {
        var capturedFormBody = ""

        val mockInterceptor = Interceptor { chain ->
            val request = chain.request()
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            capturedFormBody = buffer.readUtf8()

            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"session":{"key":"test_session_key"}}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val httpClient = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val client = LastFmClient(httpClient)

        val rawPassword = "MySecretPassword123"
        val expectedMd5Hash = MessageDigest.getInstance("MD5")
            .digest(rawPassword.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        // Execute getMobileSession
        client.getMobileSession(
            apiKey = "test_api_key",
            apiSecret = "test_api_secret",
            username = "test_user",
            password = rawPassword
        )

        // Verify that the HTTP request form body contained the MD5 hash instead of plaintext
        assertTrue("Captured form body should contain MD5 password hash", capturedFormBody.contains("password=$expectedMd5Hash"))
        assertTrue("Captured form body must not contain cleartext password", !capturedFormBody.contains(rawPassword))
    }
}
