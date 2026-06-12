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

    @Test
    fun redactsExpandedSensitiveArgumentKeys() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf(
                "otp_code" to "123456",
                "card_number" to "4111111111111111",
                "session_cookie" to "cookie-value",
                "target" to "Settings"
            )
        )

        assertEquals("[REDACTED]", redacted["otp_code"])
        assertEquals("[REDACTED]", redacted["card_number"])
        assertEquals("[REDACTED]", redacted["session_cookie"])
        assertEquals("Settings", redacted["target"])
    }

    @Test
    fun preservesAuthorizationBearerHeaderStructure() {
        val redacted = SensitiveTextRedactor.redact("Authorization: Bearer abc123xyz")

        assertEquals("Authorization: Bearer [REDACTED]", redacted)
    }

    @Test
    fun preservesAuthorizationBearerCaseAndWhitespace() {
        val redacted = SensitiveTextRedactor.redact("authorization:  bearer   abc123xyz")

        assertEquals("authorization:  bearer   [REDACTED]", redacted)
    }

    @Test
    fun doesNotEatClosingBracketsAfterBearerToken() {
        val input =
            "type_text({text=Authorization: Bearer abc123xyz}) -> false: typeIntoFocusedField"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals(
            "type_text({text=Authorization: Bearer [REDACTED]}) -> false: typeIntoFocusedField",
            redacted
        )
    }

    @Test
    fun doesNotEatClosingBracketsAfterAssignmentValue() {
        val input = "tool({password=hunter2})"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals("tool({password=[REDACTED]})", redacted)
    }

    @Test
    fun doesNotEatClosingSquareBracketAfterAssignmentValue() {
        val input = "[api_key=sk-test]"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals("[api_key=[REDACTED]]", redacted)
    }

    @Test
    fun doesNotEatClosingAngleBracketAfterAssignmentValue() {
        val input = "<secret=very-secret>"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals("<secret=[REDACTED]>", redacted)
    }

    @Test
    fun doesNotEatClosingDoubleQuoteAroundSecretValue() {
        val input = "config password=\"hunter2\" enabled=true"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals("config password=\"[REDACTED]\" enabled=true", redacted)
    }

    @Test
    fun doesNotEatClosingSingleQuoteAroundSecretValue() {
        val input = "config secret='topsecret' enabled=true"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals("config secret='[REDACTED]' enabled=true", redacted)
    }

    @Test
    fun stopsAtWhitespaceCommaAndSemicolonBoundariesAsBefore() {
        val input = "password=hunter2, api_key=sk-test; refresh_token=rt-xyz next=value"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals(
            "password=[REDACTED], api_key=[REDACTED]; refresh_token=[REDACTED] next=value",
            redacted
        )
    }

    @Test
    fun redactsMultipleAuthorizationHeadersInOneString() {
        val input = "first Authorization: Bearer aaa111 second Authorization: Bearer bbb222"

        val redacted = SensitiveTextRedactor.redact(input)

        assertEquals(
            "first Authorization: Bearer [REDACTED] second Authorization: Bearer [REDACTED]",
            redacted
        )
    }

    @Test
    fun containsSensitiveTextRecognisesAuthorizationHeader() {
        assertTrue(SensitiveTextRedactor.containsSensitiveText("Authorization: Bearer abc123xyz"))
    }

    @Test
    fun containsSensitiveTextRecognisesAssignment() {
        assertTrue(SensitiveTextRedactor.containsSensitiveText("password=hunter2"))
    }

    @Test
    fun containsSensitiveTextReturnsFalseForBenignString() {
        assertFalse(SensitiveTextRedactor.containsSensitiveText("user opened the Settings screen"))
    }
}
