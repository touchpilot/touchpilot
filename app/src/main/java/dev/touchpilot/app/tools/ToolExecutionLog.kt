package dev.touchpilot.app.tools

import dev.touchpilot.app.security.SensitiveTextRedactor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ToolLogEntry(
    val timestamp: String,
    val name: String,
    val args: String,
    val ok: Boolean,
    val message: String
)

object ToolExecutionLog {
    private const val MaxEntries = 100
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val entries = ArrayDeque<ToolLogEntry>()

    @Synchronized
    fun record(name: String, args: String, ok: Boolean, message: String) {
        if (entries.size >= MaxEntries) {
            entries.removeFirst()
        }
        entries.addLast(
            ToolLogEntry(
                timestamp = dateFormat.format(Date()),
                name = name,
                args = SensitiveTextRedactor.redact(args),
                ok = ok,
                message = SensitiveTextRedactor.redact(message)
            )
        )
    }

    @Synchronized
    fun render(): String {
        if (entries.isEmpty()) return "No tool executions yet."

        return entries.reversed().joinToString(separator = "\n") { entry ->
            val status = if (entry.ok) "ok" else "fail"
            "[${entry.timestamp}] ${entry.name}(${entry.args}) -> $status: ${entry.message}"
        }
    }

    @Synchronized
    fun renderChronological(): String {
        if (entries.isEmpty()) return "No tool executions yet."

        return entries.joinToString(separator = "\n") { entry ->
            val status = if (entry.ok) "ok" else "fail"
            "[${entry.timestamp}] ${entry.name}(${entry.args}) -> $status: ${entry.message}"
        }
    }
}
