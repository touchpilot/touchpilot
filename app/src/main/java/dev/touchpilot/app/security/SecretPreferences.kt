package dev.touchpilot.app.security

import android.content.SharedPreferences

/**
 * Minimal string key/value abstraction over the persistence layer so
 * [EncryptedSecretStore] can be unit tested with an in-memory map instead of a
 * real Android [SharedPreferences].
 */
interface SecretPreferences {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

/** [SecretPreferences] backed by Android [SharedPreferences]. */
class SharedPreferencesSecretPreferences(
    private val preferences: SharedPreferences,
) : SecretPreferences {

    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }
}
