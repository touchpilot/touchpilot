package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretPatternsTest {

    private fun assertMatches(pattern: SecretPattern, value: String) {
        assertTrue(pattern.regex.containsMatchIn(value), "expected ${pattern.name} to match '$value'")
    }

    private fun assertNoMatch(pattern: SecretPattern, value: String) {
        assertFalse(pattern.regex.containsMatchIn(value), "expected ${pattern.name} not to match '$value'")
    }

    @Test
    fun jwtMatchesThreeSegmentTokenOnly() {
        assertMatches(SecretPatterns.JWT, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.SflKxwRJSMeKKF2QT4fw")
        // Two valid-length segments but no third — not a JWT.
        assertNoMatch(SecretPatterns.JWT, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ")
    }

    @Test
    fun awsAccessKeyMatchesExactShape() {
        assertMatches(SecretPatterns.AWS_ACCESS_KEY, "AKIA" + "A".repeat(16))
        assertMatches(SecretPatterns.AWS_ACCESS_KEY, "ASIA" + "0".repeat(16))
        assertNoMatch(SecretPatterns.AWS_ACCESS_KEY, "AKIA" + "A".repeat(8))
    }

    @Test
    fun githubTokenRequiresPrefixAndLength() {
        assertMatches(SecretPatterns.GITHUB_TOKEN, "ghp_" + "a".repeat(36))
        assertMatches(SecretPatterns.GITHUB_TOKEN, "gho_" + "b".repeat(40))
        assertNoMatch(SecretPatterns.GITHUB_TOKEN, "ghp_tooshort")
        assertNoMatch(SecretPatterns.GITHUB_TOKEN, "ghz_" + "a".repeat(36))
    }

    @Test
    fun openAiKeyRequiresMinimumLength() {
        assertMatches(SecretPatterns.OPENAI_KEY, "sk-" + "a".repeat(20))
        assertMatches(SecretPatterns.OPENAI_KEY, "sk-proj-" + "a".repeat(24))
        assertNoMatch(SecretPatterns.OPENAI_KEY, "sk-short")
    }

    @Test
    fun slackTokenMatchesKnownPrefixes() {
        assertMatches(SecretPatterns.SLACK_TOKEN, "xoxb-" + "a".repeat(12))
        assertNoMatch(SecretPatterns.SLACK_TOKEN, "xoxz-" + "a".repeat(12))
    }

    @Test
    fun googleApiKeyMatchesFixedLength() {
        assertMatches(SecretPatterns.GOOGLE_API_KEY, "AIza" + "b".repeat(35))
        assertNoMatch(SecretPatterns.GOOGLE_API_KEY, "AIza" + "b".repeat(10))
    }

    @Test
    fun stripeKeyMatchesLiveAndTest() {
        assertMatches(SecretPatterns.STRIPE_KEY, "sk_live_" + "a".repeat(16))
        assertMatches(SecretPatterns.STRIPE_KEY, "rk_test_" + "a".repeat(20))
        assertNoMatch(SecretPatterns.STRIPE_KEY, "sk_live_short")
    }

    @Test
    fun privateKeyBlockMatchesFullPemAcrossNewlines() {
        val pem = buildString {
            appendLine("-----BEGIN RSA PRIVATE KEY-----")
            appendLine("MIIBVwIBADANBgkqhkiG9w0BAQEFAASCAT0wggE5AgEA")
            appendLine("QoGBAKj34GkxFhD90vcNLYLInFEX")
            append("-----END RSA PRIVATE KEY-----")
        }
        assertMatches(SecretPatterns.PRIVATE_KEY_BLOCK, pem)
        assertNoMatch(SecretPatterns.PRIVATE_KEY_BLOCK, "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----")
    }

    @Test
    fun catalogListsEveryPatternWithUniqueNames() {
        val names = SecretPatterns.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(SecretPatterns.PRIVATE_KEY_BLOCK in SecretPatterns.all)
        // The multi-line PEM block is redacted as a unit before narrower patterns.
        assertEquals(SecretPatterns.PRIVATE_KEY_BLOCK, SecretPatterns.all.first())
    }
}
