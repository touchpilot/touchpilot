package dev.touchpilot.app.logging

import dev.touchpilot.app.security.SensitiveTextRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DeveloperLogEntry(
    val id: Long = 0L,
    val timestampMillis: Long = System.currentTimeMillis(),
    val type: String,
    val actor: String,
    val name: String,
    val status: String,
    val source: String,
    val result: String,
    val errorDetails: String = "",
    val payloadSummary: String = "",
    val details: String = ""
) {
    fun redacted(): DeveloperLogEntry {
        return copy(
            actor = SensitiveTextRedactor.redact(actor),
            name = SensitiveTextRedactor.redact(name),
            result = SensitiveTextRedactor.redact(result),
            errorDetails = SensitiveTextRedactor.redact(errorDetails),
            payloadSummary = SensitiveTextRedactor.redact(payloadSummary),
            details = SensitiveTextRedactor.redact(details)
        )
    }

    fun compactSummary(): String {
        val pieces = listOf(
            type.ifBlank { "log" },
            source.ifBlank { "unknown source" },
            status.ifBlank { "unknown" }
        )
        return pieces.joinToString(separator = " / ")
    }

    fun detailText(): String {
        return buildString {
            appendLine("Time: ${formatTimestamp(timestampMillis)}")
            appendLine("Type: ${type.ifBlank { "log" }}")
            appendLine("Actor: ${actor.ifBlank { "unknown" }}")
            appendLine("Name: ${name.ifBlank { "untitled" }}")
            appendLine("Status: ${status.ifBlank { "unknown" }}")
            appendLine("Source: ${source.ifBlank { "unknown" }}")
            if (payloadSummary.isNotBlank()) {
                appendLine()
                appendLine("Payload")
                appendLine(payloadSummary)
            }
            if (result.isNotBlank()) {
                appendLine()
                appendLine("Result")
                appendLine(result)
            }
            if (errorDetails.isNotBlank()) {
                appendLine()
                appendLine("Error details")
                appendLine(errorDetails)
            }
            if (details.isNotBlank()) {
                appendLine()
                appendLine("Details")
                appendLine(details)
            }
        }.trim()
    }

    companion object {
        private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        private val shortDateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        fun formatTimestamp(timestampMillis: Long): String {
            return displayDateFormat.format(Date(timestampMillis))
        }

        fun formatShortTimestamp(timestampMillis: Long): String {
            return shortDateFormat.format(Date(timestampMillis))
        }
    }
}
