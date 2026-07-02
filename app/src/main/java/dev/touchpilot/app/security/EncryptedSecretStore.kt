package dev.touchpilot.app.security

/**
 * Persists secrets (API keys, provider tokens) encrypted at rest.
 *
 * Behaviour:
 * - [write] encrypts with the injected [SecretCipher] before persisting; a blank
 *   value clears the entry.
 * - [read] transparently decrypts. Values written before this store existed are
 *   stored as plaintext; those are returned as-is and lazily re-encrypted on the
 *   first read, so existing installs migrate without any explicit step.
 * - Decryption failures fail closed: the unreadable value is dropped and `null`
 *   is returned rather than crashing the caller. This can happen legitimately
 *   after an app-data restore to a device whose Keystore no longer holds the key.
 *
 * The store is deliberately free of Android types (it depends only on
 * [SecretPreferences] and [SecretCipher]) so its read/write/migration logic is
 * fully unit-testable.
 */
class EncryptedSecretStore(
    private val preferences: SecretPreferences,
    private val cipher: SecretCipher,
    private val onError: (String, Throwable) -> Unit = { _, _ -> },
) {

    /** Returns true if a non-empty secret is stored (and readable) under [key]. */
    fun isConfigured(key: String): Boolean = !read(key).isNullOrEmpty()

    /** Returns the decrypted secret for [key], or `null` if unset or unreadable. */
    fun read(key: String): String? {
        val stored = preferences.getString(key)?.takeIf { it.isNotEmpty() } ?: return null

        if (!EncryptedSecret.looksEncrypted(stored)) {
            // Legacy plaintext written before encryption existed — migrate opportunistically.
            migratePlaintext(key, stored)
            return stored
        }

        return runCatching { cipher.decrypt(stored) }
            .getOrElse { error ->
                onError("Unable to decrypt stored secret for '$key'; clearing it", error)
                preferences.remove(key)
                null
            }
    }

    /** Encrypts and stores [value] under [key]. A blank value clears the entry. */
    fun write(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            preferences.remove(key)
            return
        }
        runCatching { cipher.encrypt(trimmed) }
            .onSuccess { envelope -> preferences.putString(key, envelope) }
            .onFailure { error -> onError("Unable to encrypt secret for '$key'; not stored", error) }
    }

    /** Removes any stored secret for [key]. */
    fun clear(key: String) {
        preferences.remove(key)
    }

    private fun migratePlaintext(key: String, plaintext: String) {
        runCatching { cipher.encrypt(plaintext) }
            .onSuccess { envelope -> preferences.putString(key, envelope) }
            .onFailure { error ->
                // Leave the plaintext in place if we cannot encrypt; it stays usable
                // and migration is retried on the next read.
                onError("Unable to migrate plaintext secret for '$key'", error)
            }
    }
}
