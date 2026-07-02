package dev.touchpilot.app.security

/**
 * A secret located in text, identified by its [SecretPattern.name] and the
 * character [range] it occupies. The value itself is never carried on the
 * finding, so findings are always safe to log.
 */
data class SecretFinding(
    val name: String,
    val range: IntRange,
) {
    /** Number of characters the matched secret spans. */
    val length: Int get() = range.last - range.first + 1
}

/**
 * Scans free text for the credential patterns in [SecretPatterns] and can redact
 * them.
 *
 * Beyond redaction, the scanner reports *which* kinds of secrets were present
 * (by pattern name, never by value) via [scan] and [detectedTypes]. That lets
 * callers annotate a log or trace with e.g. "2 secrets redacted: github_token,
 * jwt" for auditability without ever re-exposing the secret.
 *
 * [SensitiveTextRedactor] delegates its bare-token detection and redaction to
 * this scanner, so the two never diverge.
 */
object SecretScanner {

    /** Returns every secret match in [text], ordered by start position. */
    fun scan(text: String): List<SecretFinding> {
        if (text.isEmpty()) return emptyList()

        val findings = mutableListOf<SecretFinding>()
        for (pattern in SecretPatterns.all) {
            for (match in pattern.regex.findAll(text)) {
                findings += SecretFinding(pattern.name, match.range)
            }
        }
        return findings.sortedBy { it.range.first }
    }

    /** Returns true if [text] contains at least one known secret pattern. */
    fun containsSecret(text: String): Boolean {
        if (text.isEmpty()) return false
        return SecretPatterns.all.any { it.regex.containsMatchIn(text) }
    }

    /** Replaces every matched secret with the shared redaction token. */
    fun redact(text: String): String {
        if (text.isEmpty()) return text

        var result = text
        for (pattern in SecretPatterns.all) {
            result = pattern.regex.replace(result, RedactionRule.RedactedToken)
        }
        return result
    }

    /** Distinct secret-pattern names found in [text], sorted; safe to log. */
    fun detectedTypes(text: String): List<String> =
        scan(text).map { it.name }.distinct().sorted()
}
