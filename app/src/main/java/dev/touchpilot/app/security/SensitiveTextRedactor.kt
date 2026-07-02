package dev.touchpilot.app.security

object SensitiveTextRedactor {
    private val redactionRules: List<RedactionRule> = listOf(
        RedactionRule.KeyAssignment(
            keyAlternation =
                "api[_-]?key|access[_-]?token|refresh[_-]?token|password|passcode|secret|" +
                    "private[_-]?key|credential"
        ),
        RedactionRule.KeyAssignment(
            keyAlternation = "auth|authorization",
            delimiterAlternation = "="
        ),
        RedactionRule.BearerHeader(),
        RedactionRule.LiteralValue(Regex("\\b\\d{13,19}\\b")),
        RedactionRule.LiteralValue(Regex("\\b\\d{6}\\b")),
        RedactionRule.LiteralValue(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"))
    )

    private val sensitiveWords = Regex(
        "(?i)\\b(password|passcode|otp|one-time code|api key|secret|token|credit card|card number)\\b"
    )

    fun redact(text: String): String {
        var redacted = text
        for (rule in redactionRules) {
            redacted = rule.apply(redacted)
        }
        // Catch bare credential tokens (JWTs, cloud keys, PEM blocks) that have
        // no adjacent key/header to trip the rules above.
        return SecretScanner.redact(redacted)
    }

    fun redact(args: Map<String, String>): Map<String, String> {
        return args.mapValues { (key, value) ->
            if (isSensitiveKey(key) || containsSensitiveText(value)) {
                RedactionRule.RedactedToken
            } else {
                redact(value)
            }
        }
    }

    fun containsSensitiveText(text: String): Boolean {
        return sensitiveWords.containsMatchIn(text) ||
            redactionRules.any { rule -> rule.matches(text) } ||
            SecretScanner.containsSecret(text)
    }

    private val apiKeyVariantPattern = Regex("(?i)api[_-]?key")
    private val privateKeyPattern = Regex("(?i)private[_-]?key")
    private val credentialPattern = Regex("(?i)credential")
    private val authKeyPattern = Regex("(?i)^(auth|authorization)$")

    private fun isSensitiveKey(key: String): Boolean {
        return key.contains("password", ignoreCase = true) ||
            key.contains("passcode", ignoreCase = true) ||
            key.contains("token", ignoreCase = true) ||
            key.contains("secret", ignoreCase = true) ||
            apiKeyVariantPattern.containsMatchIn(key) ||
            privateKeyPattern.containsMatchIn(key) ||
            credentialPattern.containsMatchIn(key) ||
            authKeyPattern.matches(key)
    }
}
