package dev.touchpilot.app.security

import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AesGcmSecretCipherTest {

    /** Deterministic in-memory AES-256 key, standing in for the Android Keystore key. */
    private class FixedKeyProvider(seed: Int = 7) : SecretKeyProvider {
        private val key: SecretKey = SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES")
        override fun getOrCreateKey(): SecretKey = key
    }

    private fun cipher(seed: Int = 7) = AesGcmSecretCipher(FixedKeyProvider(seed))

    @Test
    fun encryptThenDecryptReturnsOriginal() {
        val cipher = cipher()
        val secret = "sk-live-01234567890-ABCDEF"

        assertEquals(secret, cipher.decrypt(cipher.encrypt(secret)))
    }

    @Test
    fun handlesUnicodeAndEmptyString() {
        val cipher = cipher()

        assertEquals("", cipher.decrypt(cipher.encrypt("")))
        assertEquals("clé-🔐-秘密", cipher.decrypt(cipher.encrypt("clé-🔐-秘密")))
    }

    @Test
    fun ciphertextDoesNotContainPlaintext() {
        val envelope = cipher().encrypt("super-secret-value")

        assertFalse("super-secret-value" in envelope)
        assertTrue(EncryptedSecret.looksEncrypted(envelope))
    }

    @Test
    fun encryptingTwiceProducesDifferentEnvelopes() {
        val cipher = cipher()

        // Fresh random IV per call, so identical plaintext yields distinct ciphertext.
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun decryptWithWrongKeyFails() {
        val envelope = cipher(seed = 1).encrypt("secret")

        assertFailsWith<AEADBadTagException> { cipher(seed = 2).decrypt(envelope) }
    }

    @Test
    fun decryptTamperedCiphertextFails() {
        val cipher = cipher()
        val envelope = EncryptedSecret.parse(cipher.encrypt("secret"))
        val tampered = EncryptedSecret(
            version = envelope.version,
            iv = envelope.iv,
            ciphertext = envelope.ciphertext.copyOf().also { it[0] = (it[0] + 1).toByte() },
        ).serialize()

        assertFailsWith<AEADBadTagException> { cipher.decrypt(tampered) }
    }
}
