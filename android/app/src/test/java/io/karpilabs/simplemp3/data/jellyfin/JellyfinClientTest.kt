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

    @Test
    fun `streamUrl and imageUrl encode special characters in parameters`() {
        val client = JellyfinClient(OkHttpClient(), moshi)
        val session = JellyfinSession(
            serverUrl = "http://localhost:8096",
            accessToken = "token/with+special=chars&more",
            userId = "user@domain/123",
            userName = "test",
            serverId = "server123",
            deviceId = "dev123"
        )
        val item = JellyfinItem(id = "item/id#123", imageTags = mapOf("Primary" to "tag1"))

        val streamUrl = client.streamUrl(session, "item/id#123")
        assertEquals(
            "http://localhost:8096/Audio/item%2Fid%23123/stream?static=true&api_key=token%2Fwith%2Bspecial%3Dchars%26more",
            streamUrl
        )

        val streamUrlWithSpace = client.streamUrl(session, "item id")
        assertEquals(
            "http://localhost:8096/Audio/item%20id/stream?static=true&api_key=token%2Fwith%2Bspecial%3Dchars%26more",
            streamUrlWithSpace
        )

        val imageUrl = client.imageUrl(session, item)
        assertEquals(
            "http://localhost:8096/Items/item%2Fid%23123/Images/Primary?maxWidth=400&quality=85&api_key=token%2Fwith%2Bspecial%3Dchars%26more",
            imageUrl
        )
    }

    @Test
    fun `getAlbums encodes userId in request url`() = runTest {
        var requestedUrl = ""
        val mockInterceptor = Interceptor { chain ->
            requestedUrl = chain.request().url.toString()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"Items":[],"TotalRecordCount":0}""".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
        val client = JellyfinClient(okHttpClient, moshi)

        val session = JellyfinSession(
            serverUrl = "http://localhost:8096",
            accessToken = "token123",
            userId = "user@domain/special",
            userName = "test",
            serverId = "server123",
            deviceId = "dev123"
        )

        val result = client.getAlbums(session)
        assertTrue(result.isSuccess)
        assertTrue(requestedUrl.contains("/Users/user%40domain%2Fspecial/Items"))
    }
}
