package io.karpilabs.simplemp3.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SecretCipherTest {

    @Test
    fun testEncrypt_emptyStringReturnsEmpty() {
        assertEquals("", SecretCipher.encrypt(""))
    }

    @Test
    fun testEncrypt_whenKeyStoreFails_failsClosedAndReturnsEmpty() {
        // Without an Android KeyStore environment in local unit tests,
        // encrypt should fail closed and return empty string, NEVER plaintext.
        val secret = "my_super_secret_token_123"
        val encrypted = SecretCipher.encrypt(secret)
        assertEquals("", encrypted)
    }

    @Test
    fun testDecrypt_legacyUnprefixedStringReturnedAsIs() {
        val legacySecret = "legacy_plain_token_abc"
        assertEquals(legacySecret, SecretCipher.decrypt(legacySecret))
    }

    @Test
    fun testDecrypt_invalidCiphertextFailsClosedAndReturnsEmpty() {
        val invalidEncrypted = "ENC1:invalid_base64_data_xyz!!!"
        assertEquals("", SecretCipher.decrypt(invalidEncrypted))
    }
}
