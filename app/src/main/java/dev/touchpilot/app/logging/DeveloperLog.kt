package dev.touchpilot.app.logging

import dev.touchpilot.app.security.SensitiveTextRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogDetailSection(
    val title: String,
    val body: String
)

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

    fun detailSections(): List<LogDetailSection> {
        val message = result.ifBlank { errorDetails.ifBlank { payloadSummary } }
        val sections = mutableListOf<LogDetailSection>()
        if (message.isNotBlank()) {
            sections += LogDetailSection("Message", message)
        }
        if (payloadSummary.isNotBlank() && payloadSummary != message) {
            sections += LogDetailSection("Payload", payloadSummary)
        }
        if (errorDetails.isNotBlank() && errorDetails != message) {
            sections += LogDetailSection("Error details", errorDetails)
        }
        if (details.isNotBlank()) {
            sections += LogDetailSection("Log details", details)
        }
        return sections
    }

    fun detailText(): String {
        return detailSections().joinToString(separator = "\n\n") { section ->
            buildString {
                appendLine(section.title)
                append(section.body)
            }
        }.trim()
    }

    fun fullLogText(): String {
        val message = result.ifBlank { errorDetails.ifBlank { payloadSummary } }
        return buildString {
            appendLine(
                "[${formatTimestamp(timestampMillis)}] ${type.ifBlank { "log" }} " +
                    "${name.ifBlank { "untitled" }} (${source.ifBlank { "unknown" }}) -> " +
                    "${status.ifBlank { "unknown" }}"
            )
            if (message.isNotBlank()) {
                appendLine()
                appendLine("Message")
                appendLine(message)
            }
            if (payloadSummary.isNotBlank() && payloadSummary != message) {
                appendLine()
                appendLine("Payload")
                appendLine(payloadSummary)
            }
            if (errorDetails.isNotBlank() && errorDetails != message) {
                appendLine()
                appendLine("Error details")
                appendLine(errorDetails)
            }
            if (details.isNotBlank()) {
                appendLine()
                appendLine("Log details")
                appendLine(details)
            }
            appendLine()
            appendLine("Metadata")
            appendLine("Actor: ${actor.ifBlank { "unknown" }}")
            appendLine("Type: ${type.ifBlank { "log" }}")
            appendLine("Source: ${source.ifBlank { "unknown" }}")
            appendLine("Status: ${status.ifBlank { "unknown" }}")
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
