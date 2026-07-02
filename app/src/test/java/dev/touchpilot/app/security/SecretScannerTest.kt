package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretScannerTest {

    private val jwt =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIn0." +
            "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c"
    private val githubToken = "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
    private val openaiKey = "sk-proj-abcdef1234567890ABCDEFGHijkl"

    @Test
    fun detectsBareJwt() {
        assertTrue(SecretScanner.containsSecret("token is $jwt right now"))
        assertEquals(listOf("jwt"), SecretScanner.detectedTypes("token is $jwt"))
    }

    @Test
    fun redactsSecretWhileKeepingSurroundingText() {
        val redacted = SecretScanner.redact("bearer $githubToken end")

        assertFalse(githubToken in redacted)
        assertTrue(redacted.startsWith("bearer "))
        assertTrue(redacted.endsWith(" end"))
        assertTrue("[REDACTED]" in redacted)
    }

    @Test
    fun reportsMultipleDistinctTypesSortedAndSafe() {
        val text = "gh=$githubToken jwt=$jwt again $githubToken"

        val types = SecretScanner.detectedTypes(text)
        assertEquals(listOf("github_token", "jwt"), types)

        // Findings carry names/ranges only — never the secret value.
        val findings = SecretScanner.scan(text)
        assertEquals(3, findings.size)
        assertTrue(findings.all { it.name in setOf("github_token", "jwt") })
    }

    @Test
    fun redactsEveryOccurrence() {
        val redacted = SecretScanner.redact("$openaiKey and again $openaiKey")

        assertFalse(openaiKey in redacted)
        assertEquals(2, Regex("\\[REDACTED]").findAll(redacted).count())
    }

    @Test
    fun ignoresBenignText() {
        val benign = "open Chrome and tap the Login button on version 1.2.3"

        assertFalse(SecretScanner.containsSecret(benign))
        assertEquals(benign, SecretScanner.redact(benign))
        assertTrue(SecretScanner.detectedTypes(benign).isEmpty())
    }

    @Test
    fun ignoresTooShortLookalikes() {
        // Real prefixes but below the minimum length for each format.
        assertFalse(SecretScanner.containsSecret("sk-short"))
        assertFalse(SecretScanner.containsSecret("ghp_tooshort"))
    }

    @Test
    fun emptyTextIsHandled() {
        assertFalse(SecretScanner.containsSecret(""))
        assertEquals("", SecretScanner.redact(""))
        assertTrue(SecretScanner.scan("").isEmpty())
    }
}
