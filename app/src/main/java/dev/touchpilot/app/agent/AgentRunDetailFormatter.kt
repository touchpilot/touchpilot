package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AgentRunStepStatus(val label: String) {
    INFO("info"),
    PENDING("pending"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    WAITING("waiting"),
    BLOCKED("blocked"),
    COMPLETE("complete")
}

data class AgentRunDisplayStep(
    val index: Int,
    val status: AgentRunStepStatus,
    val title: String,
    val detail: String,
    val timestampMillis: Long
)

object AgentRunDetailFormatter {
    private val timestampFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun compactSummary(record: AgentRunRecord): String {
        if (record.errorMessage != null) {
            return "Run failed: ${SensitiveTextRedactor.redact(record.errorMessage)}"
        }
        val result = record.result
        if (result == null) {
            return "Run data unavailable."
        }
        val stepCount = result.events.size
        val toolCount = result.events.count {
            it.type == AgentEventType.TOOL_SUCCEEDED || it.type == AgentEventType.TOOL_FAILED
        }
        val stopReason = deriveStopReason(record)
        return buildString {
            append("$stepCount event(s)")
            if (toolCount > 0) append(" · $toolCount tool result(s)")
            appendLine()
            append("Stop: $stopReason")
            appendLine()
            append("Tap to inspect full run details.")
        }
    }

    fun deriveStopReason(record: AgentRunRecord): String {
        record.errorMessage?.let { error ->
            return "Error: ${SensitiveTextRedactor.redact(error)}"
        }
        val result = record.result ?: return "Run data unavailable"
        val events = result.events
        if (events.isEmpty()) return "No events recorded"

        val last = events.last()
        result.finalAnswer?.let { answer ->
            return "Completed: ${SensitiveTextRedactor.redact(answer)}"
        }
        return when (last) {
            is AgentEvent.PolicyBlocked -> "Blocked: ${SensitiveTextRedactor.redact(last.reason)}"
            is AgentEvent.ToolFailed -> "Tool failed: ${SensitiveTextRedactor.redact(last.message)}"
            is AgentEvent.AssistantMessage -> "Awaiting clarification"
            is AgentEvent.FinalAnswer -> "Completed: ${SensitiveTextRedactor.redact(last.text)}"
            else -> "Run ended without final answer"
        }
    }

    fun formatSteps(record: AgentRunRecord): List<AgentRunDisplayStep> {
        val events = record.result?.events.orEmpty()
        if (events.isEmpty()) return emptyList()
        return events.mapIndexed { index, event ->
            AgentRunDisplayStep(
                index = index + 1,
                status = statusFor(event),
                title = titleFor(event),
                detail = detailFor(event),
                timestampMillis = event.timestampMillis
            )
        }
    }

    fun formatTimestamp(timestampMillis: Long): String {
        return timestampFormat.format(Date(timestampMillis))
    }

    fun exportRedactedTrace(record: AgentRunRecord): String {
        return buildString {
            appendLine("TouchPilot agent run trace")
            appendLine("run_id=${record.id}")
            appendLine("task=${SensitiveTextRedactor.redact(record.task)}")
            appendLine("started_at=${formatTimestamp(record.startedAtMillis)}")
            appendLine("completed_at=${formatTimestamp(record.completedAtMillis)}")
            appendLine("stop_reason=${deriveStopReason(record)}")
            appendLine()
            record.errorMessage?.let { error ->
                appendLine("error=${SensitiveTextRedactor.redact(error)}")
                appendLine()
            }
            val events = record.result?.events.orEmpty()
            if (events.isEmpty()) {
                appendLine("No structured events recorded for this run.")
            } else {
                appendLine("events:")
                events.forEach { event ->
                    appendLine(event.toJson(redactSensitive = true).toString())
                }
            }
        }
    }

    private fun statusFor(event: AgentEvent): AgentRunStepStatus {
        return when (event) {
            is AgentEvent.UserMessage -> AgentRunStepStatus.INFO
            is AgentEvent.AssistantMessage -> AgentRunStepStatus.INFO
            is AgentEvent.ToolRequested -> AgentRunStepStatus.PENDING
            is AgentEvent.ToolRunning -> AgentRunStepStatus.RUNNING
            is AgentEvent.ToolSucceeded -> AgentRunStepStatus.SUCCESS
            is AgentEvent.ToolFailed -> AgentRunStepStatus.FAILED
            is AgentEvent.ApprovalRequired -> AgentRunStepStatus.WAITING
            is AgentEvent.PolicyBlocked -> AgentRunStepStatus.BLOCKED
            is AgentEvent.FinalAnswer -> AgentRunStepStatus.COMPLETE
        }
    }

    private fun titleFor(event: AgentEvent): String {
        return when (event) {
            is AgentEvent.UserMessage -> "User message"
            is AgentEvent.AssistantMessage -> "Assistant message"
            is AgentEvent.ToolRequested -> "Tool requested: ${event.tool}"
            is AgentEvent.ToolRunning -> "Tool running: ${event.tool}"
            is AgentEvent.ToolSucceeded -> "Tool succeeded: ${event.tool}"
            is AgentEvent.ToolFailed -> "Tool failed: ${event.tool}"
            is AgentEvent.ApprovalRequired -> "Approval required: ${event.tool}"
            is AgentEvent.PolicyBlocked -> {
                val tool = event.tool?.let { " ($it)" }.orEmpty()
                "Policy blocked$tool"
            }
            is AgentEvent.FinalAnswer -> "Final answer"
        }
    }

    private fun detailFor(event: AgentEvent): String {
        val payload = event.toJson(redactSensitive = true).getJSONObject("payload")
        return when (event) {
            is AgentEvent.UserMessage -> payload.getString("text")
            is AgentEvent.AssistantMessage -> buildString {
                append(payload.getString("text"))
                val detail = payload.optString("detail")
                if (detail.isNotBlank()) {
                    appendLine()
                    append(detail)
                }
            }
            is AgentEvent.ToolRequested,
            is AgentEvent.ToolRunning -> formatToolArgs(payload)
            is AgentEvent.ToolSucceeded,
            is AgentEvent.ToolFailed -> formatToolResult(payload)
            is AgentEvent.ApprovalRequired -> formatApproval(payload)
            is AgentEvent.PolicyBlocked -> formatPolicyBlock(payload)
            is AgentEvent.FinalAnswer -> payload.getString("text")
        }
    }

    private fun formatToolArgs(payload: JSONObject): String {
        val tool = payload.getString("tool")
        val args = payload.optJSONObject("args")
        val source = payload.optString("source")
        return buildString {
            appendLine("tool: $tool")
            if (source.isNotBlank()) appendLine("source: $source")
            append("args:")
            appendLine(formatJsonMap(args))
        }.trimEnd()
    }

    private fun formatToolResult(payload: JSONObject): String {
        val message = payload.getString("message")
        val data = payload.optJSONObject("data")
        return buildString {
            appendLine("message: $message")
            appendVerificationDetails(data)
            val remaining = remainingData(data)
            if (remaining.isNotEmpty()) {
                appendLine("data:")
                append(formatStringMap(remaining))
            }
        }.trimEnd()
    }

    private fun formatApproval(payload: JSONObject): String {
        return buildString {
            appendLine("reason: ${payload.getString("reason")}")
            appendLine("data affected: ${payload.getString("data_affected")}")
            appendLine("if approved: ${payload.getString("if_approved")}")
            append("args:")
            appendLine(formatJsonMap(payload.optJSONObject("args")))
        }.trimEnd()
    }

    private fun formatPolicyBlock(payload: JSONObject): String {
        return buildString {
            payload.optString("tool").takeIf { it.isNotBlank() }?.let { appendLine("tool: $it") }
            appendLine("reason: ${payload.getString("reason")}")
            append("user message: ${payload.getString("user_message")}")
        }
    }

    private fun StringBuilder.appendVerificationDetails(data: JSONObject?) {
        if (data == null) return
        val status = data.optString("verification_status")
        if (status.isBlank()) return
        appendLine("verification: $status")
        val reason = data.optString("verification_reason")
        if (reason.isNotBlank()) appendLine("verification reason: $reason")
        data.optString("screen_changed").takeIf { it.isNotBlank() }?.let {
            appendLine("screen changed: $it")
        }
    }

    private fun remainingData(data: JSONObject?): Map<String, String> {
        if (data == null) return emptyMap()
        val keys = data.keys()
        val remaining = linkedMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in verificationKeys) continue
            remaining[key] = data.optString(key)
        }
        return remaining
    }

    private fun formatJsonMap(json: JSONObject?): String {
        if (json == null || json.length() == 0) return " none"
        return buildString {
            appendLine()
            json.keys().asSequence().forEach { key ->
                append("  ")
                append(key)
                append(": ")
                appendLine(json.optString(key))
            }
        }.trimEnd()
    }

    private fun formatStringMap(values: Map<String, String>): String {
        if (values.isEmpty()) return " none"
        return buildString {
            appendLine()
            values.forEach { (key, value) ->
                append("  ")
                append(key)
                append(": ")
                appendLine(value)
            }
        }.trimEnd()
    }

    private val verificationKeys = setOf(
        "verification_status",
        "verification_reason",
        "screen_changed"
    )
}
