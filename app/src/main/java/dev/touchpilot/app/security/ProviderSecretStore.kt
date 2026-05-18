package dev.touchpilot.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ProviderSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun hasApiKey(): Boolean {
        return preferences.contains(ApiKeyCiphertextKey) && preferences.contains(ApiKeyIvKey)
    }

    fun loadApiKey(): String? {
        val ciphertext = preferences.getString(ApiKeyCiphertextKey, null) ?: return null
        val iv = preferences.getString(ApiKeyIvKey, null) ?: return null
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GcmTagLengthBits, Base64.decode(iv, Base64.NO_WRAP))
        )
        val plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
        return plaintext.decodeToString()
    }

    fun saveApiKey(apiKey: String) {
        if (apiKey.isBlank()) return

        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(apiKey.encodeToByteArray())

        preferences.edit()
            .putString(ApiKeyCiphertextKey, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(ApiKeyIvKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clearApiKey() {
        preferences.edit()
            .remove(ApiKeyCiphertextKey)
            .remove(ApiKeyIvKey)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        keyStore.getKey(KeyAlias, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            AndroidKeyStore
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KeyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PreferencesName = "touchpilot_secrets"
        const val ApiKeyCiphertextKey = "provider_api_key_ciphertext"
        const val ApiKeyIvKey = "provider_api_key_iv"
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "touchpilot_provider_api_key"
        const val Transformation = "AES/GCM/NoPadding"
        const val GcmTagLengthBits = 128
    }
}
