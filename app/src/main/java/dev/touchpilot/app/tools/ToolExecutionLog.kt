package dev.touchpilot.app.tools

import android.content.Context
import dev.touchpilot.app.logging.DeveloperLogEntry
import dev.touchpilot.app.logging.DeveloperLogStore
import dev.touchpilot.app.security.SensitiveTextRedactor

data class ToolLogEntry(
    val timestamp: String,
    val name: String,
    val args: String,
    val ok: Boolean,
    val message: String,
    val source: String = "unknown"
)

object ToolExecutionLog {
    private const val MaxEntries = 100
    private val entries = ArrayDeque<ToolLogEntry>()
    @Volatile
    private var store: DeveloperLogStore? = null

    @Synchronized
    fun configure(context: Context) {
        store = DeveloperLogStore(context)
    }

    @Synchronized
    fun record(
        name: String,
        args: String,
        ok: Boolean,
        message: String,
        source: String = "unknown",
        actor: String = "TouchPilot"
    ) {
        val timestampMillis = System.currentTimeMillis()
        val redactedArgs = SensitiveTextRedactor.redact(args)
        val redactedMessage = SensitiveTextRedactor.redact(message)
        if (entries.size >= MaxEntries) {
            entries.removeFirst()
        }
        entries.addLast(
            ToolLogEntry(
                timestamp = DeveloperLogEntry.formatShortTimestamp(timestampMillis),
                name = name,
                args = redactedArgs,
                ok = ok,
                message = redactedMessage,
                source = source
            )
        )
        store?.insert(
            DeveloperLogEntry(
                timestampMillis = timestampMillis,
                type = "tool",
                actor = actor,
                name = name,
                status = if (ok) "ok" else "fail",
                source = source,
                result = redactedMessage,
                errorDetails = if (ok) "" else redactedMessage,
                payloadSummary = redactedArgs,
                details = "tool=$name\nsource=$source\nstatus=${if (ok) "ok" else "fail"}"
            )
        )
    }

    @Synchronized
    fun recordChat(name: String, actor: String, message: String, status: String = "info") {
        val timestampMillis = System.currentTimeMillis()
        val redactedMessage = SensitiveTextRedactor.redact(message)
        store?.insert(
            DeveloperLogEntry(
                timestampMillis = timestampMillis,
                type = "chat",
                actor = actor,
                name = name,
                status = status,
                source = "user_action",
                result = redactedMessage,
                payloadSummary = redactedMessage.take(240),
                details = "chat_activity=$name"
            )
        )
    }

    @Synchronized
    fun recordAction(
        name: String,
        actor: String = "TouchPilot",
        result: String,
        status: String,
        source: String = "local_router",
        details: String = ""
    ) {
        val redactedResult = SensitiveTextRedactor.redact(result)
        store?.insert(
            DeveloperLogEntry(
                type = "action",
                actor = actor,
                name = name,
                status = status,
                source = source,
                result = redactedResult,
                errorDetails = if (status == "fail") redactedResult else "",
                details = SensitiveTextRedactor.redact(details)
            )
        )
    }

    @Synchronized
    fun recentEntries(): List<DeveloperLogEntry> {
        return store?.recent() ?: entries.reversed().mapIndexed { index, entry ->
            DeveloperLogEntry(
                id = index.toLong(),
                type = "tool",
                actor = "TouchPilot",
                name = entry.name,
                status = if (entry.ok) "ok" else "fail",
                source = entry.source,
                result = entry.message,
                payloadSummary = entry.args
            )
        }
    }

    @Synchronized
    fun findEntry(id: Long): DeveloperLogEntry? {
        return store?.find(id) ?: recentEntries().firstOrNull { it.id == id }
    }

    @Synchronized
    fun render(): String {
        val persisted = store?.recent()
        if (persisted != null) {
            if (persisted.isEmpty()) return "No developer logs yet."
            return persisted.joinToString(separator = "\n") { entry ->
                "[${DeveloperLogEntry.formatShortTimestamp(entry.timestampMillis)}] ${entry.name} " +
                    "(${entry.payloadSummary}) -> ${entry.status}: ${entry.result}"
            }
        }

        if (entries.isEmpty()) return "No tool executions yet."

        return entries.reversed().joinToString(separator = "\n") { entry ->
            val status = if (entry.ok) "ok" else "fail"
            "[${entry.timestamp}] ${entry.name}(${entry.args}) -> $status: ${entry.message}"
        }
    }

    @Synchronized
    fun renderChronological(): String {
        val persisted = store?.recent()
        if (persisted != null) {
            if (persisted.isEmpty()) return "No developer logs yet."
            return persisted.asReversed().joinToString(separator = "\n") { entry ->
                "[${DeveloperLogEntry.formatShortTimestamp(entry.timestampMillis)}] ${entry.type} " +
                    "${entry.name} (${entry.source}) -> ${entry.status}: ${entry.result}"
            }
        }

        if (entries.isEmpty()) return "No tool executions yet."

        return entries.joinToString(separator = "\n") { entry ->
            val status = if (entry.ok) "ok" else "fail"
            "[${entry.timestamp}] ${entry.name}(${entry.args}) -> $status: ${entry.message}"
        }
    }
}
