package dev.touchpilot.app.security

import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedSecretStoreTest {

    private class InMemoryPreferences : SecretPreferences {
        val map = mutableMapOf<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private class FixedKeyProvider : SecretKeyProvider {
        private val key: SecretKey = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        override fun getOrCreateKey(): SecretKey = key
    }

    private fun newStore(prefs: SecretPreferences): EncryptedSecretStore =
        EncryptedSecretStore(prefs, AesGcmSecretCipher(FixedKeyProvider()))

    private val KEY = "agent_api_key"

    @Test
    fun writeThenReadReturnsOriginalValue() {
        val prefs = InMemoryPreferences()
        val store = newStore(prefs)

        store.write(KEY, "sk-live-secret")

        assertEquals("sk-live-secret", store.read(KEY))
    }

    @Test
    fun storedValueIsEncryptedNotPlaintext() {
        val prefs = InMemoryPreferences()
        newStore(prefs).write(KEY, "sk-live-secret")

        val raw = prefs.getString(KEY)!!
        assertFalse("sk-live-secret" in raw)
        assertTrue(EncryptedSecret.looksEncrypted(raw))
    }

    @Test
    fun writeTrimsSurroundingWhitespace() {
        val prefs = InMemoryPreferences()
        val store = newStore(prefs)

        store.write(KEY, "  sk-live-secret  ")

        assertEquals("sk-live-secret", store.read(KEY))
    }

    @Test
    fun blankWriteClearsEntry() {
        val prefs = InMemoryPreferences()
        val store = newStore(prefs)
        store.write(KEY, "sk-live-secret")

        store.write(KEY, "   ")

        assertNull(store.read(KEY))
        assertFalse(prefs.map.containsKey(KEY))
    }

    @Test
    fun readMissingKeyReturnsNull() {
        assertNull(newStore(InMemoryPreferences()).read(KEY))
    }

    @Test
    fun legacyPlaintextIsReturnedAndMigratedOnRead() {
        val prefs = InMemoryPreferences()
        // Simulate a key stored before encryption existed.
        prefs.map[KEY] = "sk-legacy-plaintext"
        val store = newStore(prefs)

        // First read returns the plaintext transparently...
        assertEquals("sk-legacy-plaintext", store.read(KEY))
        // ...and rewrites it encrypted at rest.
        assertTrue(EncryptedSecret.looksEncrypted(prefs.map[KEY]!!))
        // Subsequent reads still return the same value via decryption.
        assertEquals("sk-legacy-plaintext", store.read(KEY))
    }

    @Test
    fun unreadableValueFailsClosedAndIsCleared() {
        val prefs = InMemoryPreferences()
        // A well-formed envelope that this store's key cannot authenticate.
        val foreign = AesGcmSecretCipher(object : SecretKeyProvider {
            override fun getOrCreateKey(): SecretKey = SecretKeySpec(ByteArray(32) { 99 }, "AES")
        }).encrypt("secret")
        prefs.map[KEY] = foreign

        val store = newStore(prefs)

        assertNull(store.read(KEY))
        assertFalse(prefs.map.containsKey(KEY))
    }

    @Test
    fun isConfiguredReflectsStoredState() {
        val prefs = InMemoryPreferences()
        val store = newStore(prefs)

        assertFalse(store.isConfigured(KEY))
        store.write(KEY, "sk-live-secret")
        assertTrue(store.isConfigured(KEY))
        store.clear(KEY)
        assertFalse(store.isConfigured(KEY))
    }
}
