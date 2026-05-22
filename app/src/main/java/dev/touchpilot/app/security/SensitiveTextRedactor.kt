package dev.touchpilot.app.security

object SensitiveTextRedactor {
    private val redactionRules: List<RedactionRule> = listOf(
        RedactionRule.KeyAssignment(
            keyAlternation = "api[_-]?key|access[_-]?token|refresh[_-]?token|password|passcode|secret"
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
        return redacted
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
            redactionRules.any { rule -> rule.matches(text) }
    }

    private fun isSensitiveKey(key: String): Boolean {
        return key.contains("password", ignoreCase = true) ||
            key.contains("token", ignoreCase = true) ||
            key.contains("secret", ignoreCase = true) ||
            key.contains("api_key", ignoreCase = true)
    }
}
