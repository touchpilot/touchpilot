package dev.touchpilot.app.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device coverage for the Android Keystore-backed secret path.
 *
 * The JVM unit tests exercise [AesGcmSecretCipher] and [EncryptedSecretStore]
 * with an in-memory key. This live test exercises the real
 * [AndroidKeystoreSecretKeyProvider] end to end: a key is generated inside the
 * AndroidKeyStore, used to encrypt through real [android.content.SharedPreferences],
 * and decrypted back by a fresh store instance.
 *
 * It uses a dedicated key alias and prefs file so it never touches the real
 * `agent_api_key` entry, and clears both before and after each test.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretStoreLiveTest {
    private val alias = "touchpilot.secret.livetest"
    private val prefsName = "touchpilot-secret-livetest"
    private val key = "test_api_key"

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        clearState()
    }

    @After
    fun tearDown() {
        clearState()
    }

    @Test
    fun encryptsWithKeystoreAndDecryptsBack() {
        newStore().write(key, SECRET)

        val raw = rawStored()
        assertNotNull("expected a stored value", raw)
        assertTrue("stored value should be an encrypted envelope", EncryptedSecret.looksEncrypted(raw!!))
        assertFalse("plaintext must not be persisted", raw.contains(SECRET))

        // A fresh store instance must decrypt using the persisted Keystore key.
        assertEquals(SECRET, newStore().read(key))
    }

    @Test
    fun migratesLegacyPlaintextOnRead() {
        // Simulate a value written by a build that stored the key in plaintext.
        prefs().edit().putString(key, LEGACY_PLAINTEXT).commit()

        val store = newStore()
        assertEquals(LEGACY_PLAINTEXT, store.read(key))

        val raw = rawStored()
        assertNotNull("value should still be present after migration", raw)
        assertTrue("value should be encrypted after migration", EncryptedSecret.looksEncrypted(raw!!))
        assertEquals(LEGACY_PLAINTEXT, newStore().read(key))
    }

    @Test
    fun blankWriteClearsEntry() {
        val store = newStore()
        store.write(key, "sk-temp")
        store.write(key, "   ")

        assertNull(rawStored())
        assertNull(store.read(key))
    }

    private fun newStore(): EncryptedSecretStore =
        EncryptedSecretStore(
            SharedPreferencesSecretPreferences(prefs()),
            AesGcmSecretCipher(AndroidKeystoreSecretKeyProvider(alias)),
        )

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun rawStored(): String? = prefs().getString(key, null)

    private fun clearState() {
        prefs().edit().clear().commit()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    private companion object {
        const val SECRET = "sk-live-keystore-secret"
        const val LEGACY_PLAINTEXT = "sk-legacy-plaintext"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}
