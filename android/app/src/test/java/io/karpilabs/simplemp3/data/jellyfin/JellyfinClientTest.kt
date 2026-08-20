package io.karpilabs.simplemp3.data.jellyfin

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinClientTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `normalizeServerUrl formats urls properly`() {
        val client = JellyfinClient(OkHttpClient(), moshi)
        assertEquals("http://192.168.1.50:8096", client.normalizeServerUrl("192.168.1.50:8096"))
        assertEquals("http://192.168.1.50:8096", client.normalizeServerUrl("http://192.168.1.50:8096/"))
        assertEquals("https://jellyfin.example.com", client.normalizeServerUrl("https://jellyfin.example.com/"))
    }

    @Test
    fun `authenticate failure does not leak sensitive response body details in exception message`() = runTest {
        val sensitiveBody = "SECRET_TOKEN_12345 Internal Stack trace: Exception at com.internal.Auth"
        val mockInterceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(401)
                .message("Unauthorized")
                .body(sensitiveBody.toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val client = JellyfinClient(okHttpClient, moshi)

        val result = client.authenticate("http://localhost", "user", "pass", "dev123")

        assertTrue(result.isFailure)
        val exceptionMessage = result.exceptionOrNull()?.message.orEmpty()
        assertEquals("Login failed (401)", exceptionMessage)
        assertFalse(exceptionMessage.contains(sensitiveBody))
    }

    @Test
    fun `getJson failure does not leak sensitive response body details in exception message`() = runTest {
        val sensitiveBody = "Database Connection Failed: postgresql://admin:secret@db.internal:5432"
        val mockInterceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body(sensitiveBody.toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val client = JellyfinClient(okHttpClient, moshi)

        val session = JellyfinSession(
            serverUrl = "http://localhost",
            accessToken = "token123",
            userId = "user123",
            userName = "test",
            serverId = "server123",
            deviceId = "dev123"
        )

        val result = client.getAlbums(session)

        assertTrue(result.isFailure)
        val exceptionMessage = result.exceptionOrNull()?.message.orEmpty()
        assertEquals("Request failed (500)", exceptionMessage)
        assertFalse(exceptionMessage.contains(sensitiveBody))
    }
}
