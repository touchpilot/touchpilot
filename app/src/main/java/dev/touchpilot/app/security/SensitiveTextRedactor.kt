package dev.touchpilot.app.security

object SensitiveTextRedactor {
    private val assignmentPatterns = listOf(
        Regex("(?i)(api[_-]?key|access[_-]?token|refresh[_-]?token|password|passcode|secret)\\s*[:=]\\s*[^\\s,;]+"),
        Regex("(?i)(authorization:\\s*bearer\\s+)[^\\s,;]+")
    )
    private val valuePatterns = listOf(
        Regex("\\b\\d{13,19}\\b"),
        Regex("\\b\\d{6}\\b"),
        Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
    )
    private val sensitiveWords = Regex("(?i)\\b(password|passcode|otp|one-time code|api key|secret|token|credit card|card number)\\b")

    fun redact(text: String): String {
        var redacted = text
        assignmentPatterns.forEach { pattern ->
            redacted = pattern.replace(redacted) { match ->
                val prefix = match.value.substringBeforeAny(listOf(":", "="))
                "$prefix=[REDACTED]"
            }
        }
        valuePatterns.forEach { pattern ->
            redacted = pattern.replace(redacted, "[REDACTED]")
        }
        return redacted
    }

    fun redact(args: Map<String, String>): Map<String, String> {
        return args.mapValues { (key, value) ->
            if (isSensitiveKey(key) || containsSensitiveText(value)) {
                "[REDACTED]"
            } else {
                redact(value)
            }
        }
    }

    fun containsSensitiveText(text: String): Boolean {
        return sensitiveWords.containsMatchIn(text) ||
            assignmentPatterns.any { it.containsMatchIn(text) } ||
            valuePatterns.any { it.containsMatchIn(text) }
    }

    private fun isSensitiveKey(key: String): Boolean {
        return key.contains("password", ignoreCase = true) ||
            key.contains("token", ignoreCase = true) ||
            key.contains("secret", ignoreCase = true) ||
            key.contains("api_key", ignoreCase = true)
    }

    private fun String.substringBeforeAny(delimiters: List<String>): String {
        val index = delimiters.mapNotNull { delimiter ->
            indexOf(delimiter).takeIf { it >= 0 }
        }.minOrNull()
        return if (index == null) this else substring(0, index)
    }
}
