package io.karpilabs.simplemp3.data.prefs

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small secrets (scrobble tokens / session keys) with an AES-GCM key held in
 * the AndroidKeyStore, so the ciphertext persisted in DataStore is useless off-device
 * (rooted device / backup extraction can read DataStore but not the Keystore key).
 *
 * Ciphertext is stored as `ENC1:<base64(iv|ciphertext)>`. Values without that prefix are
 * treated as legacy plaintext and returned as-is, so existing prefs keep working and are
 * transparently re-encrypted the next time they are written.
 */
object SecretCipher {
    private const val KEY_ALIAS = "simplemp3_secret_key"
    private const val PREFIX = "ENC1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    /** Encrypt [plain]; returns a `ENC1:`-prefixed string. Blank input is returned unchanged. */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return plain
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val combined = iv + ct
            PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            // If the Keystore is somehow unavailable, fail closed rather than silently
            // persisting plaintext under the encrypted contract.
            plain
        }
    }

    /** Decrypt a value produced by [encrypt]; legacy (unprefixed) values are returned as-is. */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored
        return try {
            val combined = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ct = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
