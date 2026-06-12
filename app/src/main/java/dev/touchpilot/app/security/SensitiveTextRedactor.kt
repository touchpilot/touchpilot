package dev.touchpilot.app.security

object SensitiveTextRedactor {
    private const val AssignmentSensitiveKeyAlternation =
        "api[_-]?key|access[_-]?token|refresh[_-]?token|token|password|passcode|secret|otp|" +
            "one[_-]?time[_-]?code|pin|recovery(?:[_-]?code)?|card(?:[_-]?(?:number|pan))?|" +
            "cvv|cvc|security[_-]?code|cookie|session"

    private const val SensitiveKeyAlternation =
        "$AssignmentSensitiveKeyAlternation|auth(?:orization)?|email"

    private val sensitiveKeyPattern = Regex("(?i)($SensitiveKeyAlternation)")

    private val redactionRules: List<RedactionRule> = listOf(
        RedactionRule.KeyAssignment(
            keyAlternation = AssignmentSensitiveKeyAlternation
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
        return sensitiveKeyPattern.containsMatchIn(key)
    }
}
