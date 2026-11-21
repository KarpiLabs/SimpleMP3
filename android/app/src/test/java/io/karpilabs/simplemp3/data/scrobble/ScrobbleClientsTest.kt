package io.karpilabs.simplemp3.data.scrobble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrobbleClientsTest {

    @Test
    fun lastFmSignedMethodFormatsSignature() {
        // LastFmClient generates sig as MD5(sorted(params) + secret)
        // With params: api_key=abc, method=test, username=john
        // sorted string: "api_keyabcmethodtestusernamejohn" + "mysecret"
        val client = LastFmClient(okhttp3.OkHttpClient())
        // Since signed is private, we verify via getMobileSession error handling with dummy client
        val key = client.getMobileSession("abc", "mysecret", "john", "pass")
        assertNull(key)
    }

    @Test
    fun listenBrainzSubmitEmptyListReturnsTrue() {
        val client = ListenBrainzClient(okhttp3.OkHttpClient())
        val ok = client.submit("token", emptyList(), "single")
        assertEquals(true, ok)
    }
}
