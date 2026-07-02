package dev.touchpilot.app.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts short secrets (API keys, tokens) for storage at rest. */
interface SecretCipher {
    /** Encrypts [plaintext] and returns a serialized [EncryptedSecret] envelope string. */
    fun encrypt(plaintext: String): String

    /**
     * Decrypts a serialized envelope produced by [encrypt].
     *
     * @throws IllegalArgumentException if [stored] is not a well-formed envelope.
     * @throws javax.crypto.AEADBadTagException if the ciphertext fails authentication
     *   (wrong key or tampered data).
     */
    fun decrypt(stored: String): String
}

/**
 * AES-256-GCM implementation of [SecretCipher].
 *
 * The symmetric key is supplied by a [SecretKeyProvider] rather than held here,
 * so on device the key material can live inside the hardware-backed Android
 * Keystore while this crypto logic stays unit-testable with an in-memory key.
 *
 * GCM provides authenticated encryption: a tampered or truncated ciphertext, or
 * decryption with the wrong key, fails loudly instead of returning garbage. A
 * fresh random IV is generated per encryption, so encrypting the same plaintext
 * twice yields different envelopes.
 */
class AesGcmSecretCipher(
    private val keyProvider: SecretKeyProvider,
    private val random: SecureRandom = SecureRandom(),
) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, keyProvider.getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedSecret(EncryptedSecret.CURRENT_VERSION, iv, ciphertext).serialize()
    }

    override fun decrypt(stored: String): String {
        val envelope = EncryptedSecret.parse(stored)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, keyProvider.getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, envelope.iv))
        }
        return String(cipher.doFinal(envelope.ciphertext), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
