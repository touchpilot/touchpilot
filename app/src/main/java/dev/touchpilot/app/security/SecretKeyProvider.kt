package dev.touchpilot.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Supplies the symmetric key used by [AesGcmSecretCipher]. */
interface SecretKeyProvider {
    /** Returns the existing key, creating and persisting it on first use. */
    fun getOrCreateKey(): SecretKey
}

/**
 * Supplies an AES key that lives in the Android Keystore.
 *
 * The key is generated in the keystore on first use and never leaves it — only
 * encrypt/decrypt operations cross the boundary, so the raw key bytes are never
 * exposed to the app process or written to SharedPreferences. On devices with a
 * secure element the key is hardware-backed.
 *
 * This is a thin platform adapter: it is exercised on device (the keystore is
 * unavailable in local JVM tests), while the crypto and envelope logic it feeds
 * are covered by unit tests through [SecretKeyProvider].
 */
class AndroidKeystoreSecretKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) : SecretKeyProvider {

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "touchpilot.secret.v1"
        const val KEY_SIZE_BITS = 256
    }
}
