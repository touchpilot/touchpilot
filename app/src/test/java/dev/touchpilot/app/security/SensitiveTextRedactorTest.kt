package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveTextRedactorTest {
    @Test
    fun redactsSecretsAndEmails() {
        val redacted = SensitiveTextRedactor.redact(
            "email user@example.com password=hunter2 api_key=sk-test"
        )

        assertFalse("user@example.com" in redacted)
        assertFalse("hunter2" in redacted)
        assertFalse("sk-test" in redacted)
        assertTrue("[REDACTED]" in redacted)
    }

    @Test
    fun redactsSensitiveArgumentKeys() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("api_key" to "secret-value", "target" to "Settings")
        )

        assertEquals("[REDACTED]", redacted["api_key"])
        assertEquals("Settings", redacted["target"])
    }
}
