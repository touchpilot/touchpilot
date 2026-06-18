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
    fun redactsHyphenSeparatedApiKey() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("api-key" to "sk-test", "target" to "Settings")
        )

        assertEquals("[REDACTED]", redacted["api-key"])
        assertEquals("Settings", redacted["target"])
    }

    @Test
    fun redactsApiKeyWithoutSeparator() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("apikey" to "sk-test")
        )

        assertEquals("[REDACTED]", redacted["apikey"])
    }

    @Test
    fun redactsHttpStyleApiKeyHeaderKey() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("X-API-Key" to "sk-test")
        )

        assertEquals("[REDACTED]", redacted["X-API-Key"])
    }

    @Test
    fun redactsUppercaseApiKeyVariants() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf(
                "API_KEY" to "v1",
                "API-KEY" to "v2",
                "APIKEY" to "v3"
            )
        )

        assertEquals("[REDACTED]", redacted["API_KEY"])
        assertEquals("[REDACTED]", redacted["API-KEY"])
        assertEquals("[REDACTED]", redacted["APIKEY"])
    }

    @Test
    fun redactsPasscodeKey() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("passcode" to "1234")
        )

        assertEquals("[REDACTED]", redacted["passcode"])
    }

    @Test
    fun redactsUppercasePasscodeKey() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("Passcode" to "1234")
        )

        assertEquals("[REDACTED]", redacted["Passcode"])
    }

    @Test
    fun redactsPrivateKeyArgumentKeys() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf(
                "private_key" to "MIIEvgIBADANBg",
                "private-key" to "MIIEvgIBADANBg",
                "PRIVATE_KEY" to "MIIEvgIBADANBg"
            )
        )

        assertEquals("[REDACTED]", redacted["private_key"])
        assertEquals("[REDACTED]", redacted["private-key"])
        assertEquals("[REDACTED]", redacted["PRIVATE_KEY"])
    }

    @Test
    fun redactsCredentialArgumentKeys() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf(
                "credential" to "json-secret",
                "credentials" to "json-secret",
                "user_credential" to "json-secret"
            )
        )

        assertEquals("[REDACTED]", redacted["credential"])
        assertEquals("[REDACTED]", redacted["credentials"])
        assertEquals("[REDACTED]", redacted["user_credential"])
    }

    @Test
    fun redactsAuthArgumentKeys() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf(
                "auth" to "Bearer sk-test",
                "authorization" to "Bearer sk-test",
                "Authorization" to "Bearer sk-test"
            )
        )

        assertEquals("[REDACTED]", redacted["auth"])
        assertEquals("[REDACTED]", redacted["authorization"])
        assertEquals("[REDACTED]", redacted["Authorization"])
    }

    @Test
    fun redactsPrivateKeyAndCredentialInTextAssignments() {
        val redacted = SensitiveTextRedactor.redact(
            "tool({private_key=MIIEvgIBADANBg, credential=json-secret})"
        )

        assertEquals(
            "tool({private_key=[REDACTED], credential=[REDACTED]})",
            redacted
        )
    }

    @Test
    fun doesNotRedactBenignKeyWithUnrelatedHyphen() {
        val redacted = SensitiveTextRedactor.redact(
            mapOf("user-language" to "en-US")
        )

        assertEquals("en-US", redacted["user-language"])
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
