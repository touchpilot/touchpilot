package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * End-to-end coverage that the expanded secret patterns flow through the public
 * [SensitiveTextRedactor] entry points used across logging and traces, and that
 * the existing key/value redaction behaviour is unchanged.
 */
class SensitiveTextRedactorSecretPatternsTest {

    private val jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIn0." +
            "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c"
    private val githubToken = "ghp_1234567890abcdefghijklmnopqrstuvwxyz"

    @Test
    fun redactsBareTokenWithNoAdjacentKey() {
        val redacted = SensitiveTextRedactor.redact("observed screen text: $jwt shown to user")

        assertFalse(jwt in redacted)
        assertTrue("[REDACTED]" in redacted)
        assertTrue(redacted.startsWith("observed screen text: "))
    }

    @Test
    fun detectsBareTokenAsSensitive() {
        assertTrue(SensitiveTextRedactor.containsSensitiveText("value $githubToken"))
    }

    @Test
    fun redactsSecretValueUnderNonSensitiveArgumentKey() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("note" to "auth uses $jwt", "target" to "Settings")
        )

        assertEquals("[REDACTED]", redacted["note"])
        assertEquals("Settings", redacted["target"])
    }

    @Test
    fun preservesExistingKeyValueRedaction() {
        // Regression guard: key/value and email rules still behave as before.
        val redacted = SensitiveTextRedactor.redact("password=hunter2 email user@example.com")

        assertFalse("hunter2" in redacted)
        assertFalse("user@example.com" in redacted)
        assertTrue("[REDACTED]" in redacted)
    }

    @Test
    fun leavesBenignTextUntouched() {
        val benign = "open Settings and tap Save"
        assertEquals(benign, SensitiveTextRedactor.redact(benign))
        assertFalse(SensitiveTextRedactor.containsSensitiveText(benign))
    }
}
