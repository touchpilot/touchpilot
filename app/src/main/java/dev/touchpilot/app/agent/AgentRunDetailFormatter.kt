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

/**
 * Visual severity of a run step, used so run review can highlight each outcome
 * distinctly. Kept separate from any color/UI value so the classification is
 * pure and unit-testable; the renderer maps each severity to a theme color.
 *
 * Notably [FAILED] (a tool error) and [BLOCKED] (a policy/safety stop) are
 * different severities, so they are no longer rendered with the same neutral
 * color.
 */
enum class AgentRunStepSeverity {
    POSITIVE,
    IN_PROGRESS,
    CAUTION,
    NEGATIVE,
    NEUTRAL
}

val AgentRunStepStatus.severity: AgentRunStepSeverity
    get() = when (this) {
        AgentRunStepStatus.SUCCESS,
        AgentRunStepStatus.COMPLETE -> AgentRunStepSeverity.POSITIVE
        AgentRunStepStatus.RUNNING,
        AgentRunStepStatus.WAITING,
        AgentRunStepStatus.PENDING -> AgentRunStepSeverity.IN_PROGRESS
        AgentRunStepStatus.BLOCKED -> AgentRunStepSeverity.CAUTION
        AgentRunStepStatus.FAILED -> AgentRunStepSeverity.NEGATIVE
        AgentRunStepStatus.INFO -> AgentRunStepSeverity.NEUTRAL
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

    fun buildCompletionSummary(
        record: AgentRunRecord,
        displaySteps: List<AgentStep> = emptyList(),
    ): AgentRunCompletionSummary {
        val steps = resolveDisplaySteps(record, displaySteps)
        val stopReason = record.result?.stopReason
        val status = deriveCompletionStatus(record, steps)
        return AgentRunCompletionSummary(
            status = status,
            stopReason = deriveStopReason(record),
            stepCount = steps.size,
            lastVerificationOutcome = deriveLastVerificationOutcome(record, steps),
            nextAction = deriveNextAction(status, stopReason, steps),
        )
    }

    fun compactSummary(record: AgentRunRecord): String {
        if (record.errorMessage != null) {
            return "Run failed: ${SensitiveTextRedactor.redact(record.errorMessage)}"
        }
        val result = record.result
        if (result == null) {
            return "Run data unavailable."
        }
        val stopReason = deriveStopReason(record)
        return if (result.steps.isNotEmpty()) {
            val toolResults = result.steps.count {
                it.type == AgentStepType.ACT && it.toolCall?.result != null
            }
            buildString {
                append("${result.steps.size} step(s)")
                if (toolResults > 0) append(" · $toolResults tool result(s)")
                appendLine()
                append("Stop: $stopReason")
                appendLine()
                append("Tap to inspect full run details.")
            }
        } else {
            val toolCount = result.events.count {
                it.type == AgentEventType.TOOL_SUCCEEDED || it.type == AgentEventType.TOOL_FAILED
            }
            buildString {
                append("${result.events.size} event(s)")
                if (toolCount > 0) append(" · $toolCount tool result(s)")
                appendLine()
                append("Stop: $stopReason")
                appendLine()
                append("Tap to inspect full run details.")
            }
        }
    }

    fun deriveStopReason(record: AgentRunRecord): String {
        record.errorMessage?.let { error ->
            return "Error: ${SensitiveTextRedactor.redact(error)}"
        }
        val result = record.result ?: return "Run data unavailable"
        deriveStopReasonFromResult(result)?.let { return it }
        return deriveStopReasonFromEvents(result)
    }

    fun formatSteps(record: AgentRunRecord): List<AgentRunDisplayStep> {
        val result = record.result ?: return emptyList()
        if (result.steps.isNotEmpty()) {
            return formatStepsFromStructured(result.steps)
        }
        return formatStepsFromEvents(result.events)
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
            appendLine("derived_stop_reason=${deriveStopReason(record)}")
            appendLine()
            record.errorMessage?.let { error ->
                appendLine("error=${SensitiveTextRedactor.redact(error)}")
                appendLine()
            }
            val result = record.result
            if (result == null) {
                appendLine("No run result recorded.")
                return@buildString
            }
            appendLine("canonical_stop_reason=${result.stopReason?.wireName ?: "none"}")
            appendLine(
                "canonical_stop_message=${
                    SensitiveTextRedactor.redact(
                        result.stopMessage.ifBlank {
                            result.stopReason?.userMessage.orEmpty()
                        }
                    )
                }"
            )
            appendLine()
            if (result.steps.isEmpty()) {
                appendLine("steps: none")
            } else {
                appendLine("steps:")
                result.steps.forEach { step ->
                    appendLine(step.toJson(redactSensitive = true).toString())
                }
            }
            appendLine()
            if (result.events.isEmpty()) {
                appendLine("events: none")
            } else {
                appendLine("events:")
                result.events.forEach { event ->
                    appendLine(event.toJson(redactSensitive = true).toString())
                }
            }
            appendLine()
            if (record.screenRecords.isEmpty()) {
                appendLine("screen_records: none")
            } else {
                appendLine("screen_records:")
                record.screenRecords.forEach { screenRecord ->
                    appendLine(screenRecord.toJson().toString())
                }
            }
        }
    }

    private fun resolveDisplaySteps(
        record: AgentRunRecord,
        displaySteps: List<AgentStep>,
    ): List<AgentStep> {
        val structured = record.result?.steps.orEmpty()
        return if (structured.isNotEmpty()) structured else displaySteps
    }

    private fun deriveCompletionStatus(
        record: AgentRunRecord,
        steps: List<AgentStep>,
    ): AgentRunCompletionStatus {
        if (record.errorMessage != null) {
            return AgentRunCompletionStatus.FAILED
        }
        record.result?.stopReason?.let { return it.toCompletionStatus() }
        steps.lastOrNull { it.type == AgentStepType.STOP || it.type == AgentStepType.CLARIFY }
            ?.let { terminal ->
                if (terminal.type == AgentStepType.CLARIFY) {
                    return AgentRunCompletionStatus.NEEDS_CLARIFICATION
                }
                terminal.stopReason?.let { return it.toCompletionStatus() }
            }
        if (!record.result?.finalAnswer.isNullOrBlank()) {
            return AgentRunCompletionStatus.COMPLETED
        }
        return when (record.result?.events?.lastOrNull()) {
            is AgentEvent.FinalAnswer -> AgentRunCompletionStatus.COMPLETED
            is AgentEvent.PolicyBlocked -> AgentRunCompletionStatus.BLOCKED
            is AgentEvent.Clarification -> AgentRunCompletionStatus.NEEDS_CLARIFICATION
            else -> AgentRunCompletionStatus.STOPPED
        }
    }

    private fun AgentStepStopReason.toCompletionStatus(): AgentRunCompletionStatus {
        return when (this) {
            AgentStepStopReason.COMPLETED -> AgentRunCompletionStatus.COMPLETED
            AgentStepStopReason.POLICY_BLOCKED,
            AgentStepStopReason.APPROVAL_DENIED -> AgentRunCompletionStatus.BLOCKED
            AgentStepStopReason.CLARIFICATION_NEEDED -> AgentRunCompletionStatus.NEEDS_CLARIFICATION
            AgentStepStopReason.USER_CANCELLED -> AgentRunCompletionStatus.CANCELLED
            AgentStepStopReason.MAX_STEPS,
            AgentStepStopReason.REPEATED_TOOL_FAILURE,
            AgentStepStopReason.PARSER_ERROR,
            AgentStepStopReason.EXECUTOR_ERROR,
            AgentStepStopReason.NO_VALID_ACTION -> AgentRunCompletionStatus.STOPPED
        }
    }

    private fun deriveLastVerificationOutcome(
        record: AgentRunRecord,
        steps: List<AgentStep>,
    ): String? {
        steps.asReversed().firstNotNullOfOrNull { step ->
            step.verification?.let(::formatVerificationOutcome)
                ?: step.takeIf { it.type == AgentStepType.VERIFY && it.outputSummary.isNotBlank() }
                    ?.let { verifyStep ->
                        formatVerificationOutcome(
                            verifyStep.status.wireName,
                            verifyStep.outputSummary,
                        )
                    }
        }?.let { return it }

        record.result?.events?.asReversed()?.firstNotNullOfOrNull { event ->
            if (event !is AgentEvent.ToolSucceeded) return@firstNotNullOfOrNull null
            val status = event.data["verification_status"].orEmpty()
            if (status.isBlank()) return@firstNotNullOfOrNull null
            formatVerificationOutcome(status, event.data["verification_reason"].orEmpty())
        }?.let { return it }

        return null
    }

    private fun formatVerificationOutcome(verification: AgentStepVerification): String {
        return formatVerificationOutcome(verification.status, verification.reason)
    }

    private fun formatVerificationOutcome(status: String, reason: String): String {
        val label = status.replace('_', ' ').replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
        val redactedReason = SensitiveTextRedactor.redact(reason)
        return if (redactedReason.isBlank()) label else "$label: $redactedReason"
    }

    private fun deriveNextAction(
        status: AgentRunCompletionStatus,
        stopReason: AgentStepStopReason?,
        steps: List<AgentStep>,
    ): String? {
        return when (status) {
            AgentRunCompletionStatus.COMPLETED -> null
            AgentRunCompletionStatus.NEEDS_CLARIFICATION -> {
                steps.lastOrNull { it.type == AgentStepType.CLARIFY }
                    ?.clarification
                    ?.question
                    ?.takeIf { it.isNotBlank() }
                    ?.let { question ->
                        "Reply with: ${SensitiveTextRedactor.redact(question).take(120)}"
                    }
                    ?: "Reply with the missing detail to continue."
            }
            AgentRunCompletionStatus.BLOCKED -> when (stopReason) {
                AgentStepStopReason.APPROVAL_DENIED ->
                    "Approve the action or rephrase the task."
                else -> "Try a safer alternative or adjust your request."
            }
            AgentRunCompletionStatus.CANCELLED ->
                "Approve the pending action or send a revised task."
            AgentRunCompletionStatus.FAILED -> "Check logs or retry the task."
            AgentRunCompletionStatus.STOPPED -> when (stopReason) {
                AgentStepStopReason.MAX_STEPS ->
                    "Break the task into smaller steps and try again."
                AgentStepStopReason.REPEATED_TOOL_FAILURE ->
                    "Check the current screen state and retry."
                else -> "Review the step timeline and retry if needed."
            }
        }
    }

    private fun deriveStopReasonFromResult(result: AgentRunResult): String? {
        val reason = result.stopReason ?: return null
        val message = result.stopMessage.ifBlank { reason.userMessage }
        return SensitiveTextRedactor.redact(message)
    }

    private fun deriveStopReasonFromEvents(result: AgentRunResult): String {
        val events = result.events
        if (events.isEmpty()) return "No run data recorded"

        result.finalAnswer?.let { answer ->
            return "Completed: ${SensitiveTextRedactor.redact(answer)}"
        }
        return when (val last = events.last()) {
            is AgentEvent.PolicyBlocked -> "Blocked: ${SensitiveTextRedactor.redact(last.reason)}"
            is AgentEvent.ToolFailed -> "Tool failed: ${SensitiveTextRedactor.redact(last.message)}"
            is AgentEvent.AssistantMessage -> "Awaiting clarification"
            is AgentEvent.Clarification -> "Clarification needed: ${SensitiveTextRedactor.redact(last.question)}"
            is AgentEvent.FinalAnswer -> "Completed: ${SensitiveTextRedactor.redact(last.text)}"
            else -> "Run ended without final answer"
        }
    }

    private fun formatStepsFromStructured(steps: List<AgentStep>): List<AgentRunDisplayStep> {
        return steps.map { step ->
            AgentRunDisplayStep(
                index = step.sequenceNumber,
                status = statusFor(step),
                title = titleFor(step),
                detail = detailFor(step),
                timestampMillis = step.startedAtMillis
            )
        }
    }

    private fun formatStepsFromEvents(events: List<AgentEvent>): List<AgentRunDisplayStep> {
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

    private fun statusFor(step: AgentStep): AgentRunStepStatus {
        return when (step.status) {
            AgentStepStatus.PENDING -> AgentRunStepStatus.PENDING
            AgentStepStatus.RUNNING -> AgentRunStepStatus.RUNNING
            AgentStepStatus.OK -> when (step.type) {
                AgentStepType.STOP -> AgentRunStepStatus.COMPLETE
                else -> AgentRunStepStatus.SUCCESS
            }
            AgentStepStatus.FAILED -> AgentRunStepStatus.FAILED
            AgentStepStatus.BLOCKED -> AgentRunStepStatus.BLOCKED
            AgentStepStatus.CLARIFIED -> AgentRunStepStatus.WAITING
            AgentStepStatus.STOPPED -> AgentRunStepStatus.COMPLETE
        }
    }

    private fun titleFor(step: AgentStep): String {
        return when (step.type) {
            AgentStepType.OBSERVE -> "Observe"
            AgentStepType.DECIDE -> "Decide"
            AgentStepType.ACT -> "Act: ${step.toolCall?.tool.orEmpty()}"
            AgentStepType.VERIFY -> "Verify"
            AgentStepType.CLARIFY -> "Clarify"
            AgentStepType.STOP -> "Stop: ${step.stopReason?.wireName ?: "ended"}"
        }
    }

    private fun detailFor(step: AgentStep): String {
        return buildString {
            if (step.inputSummary.isNotBlank()) {
                appendLine("input: ${step.inputSummary}")
            }
            if (step.outputSummary.isNotBlank()) {
                appendLine("output: ${step.outputSummary}")
            }
            step.toolCall?.let { call ->
                appendLine("tool: ${call.tool}")
                appendLine("source: ${call.source}")
                if (call.args.isNotEmpty()) {
                    append("args:")
                    appendLine(formatStringMap(call.args))
                }
                call.result?.let { result ->
                    appendLine("result: ${if (result.ok) "ok" else "failed"}")
                    appendLine("message: ${result.message}")
                    if (result.data.isNotEmpty()) {
                        append("data:")
                        appendLine(formatStringMap(result.data))
                    }
                }
            }
            step.verification?.let { verification ->
                appendLine("verification: ${verification.status}")
                appendLine("verification reason: ${verification.reason}")
                if (verification.data.isNotEmpty()) {
                    append("verification data:")
                    appendLine(formatStringMap(verification.data))
                }
            }
            step.clarification?.let { clarification ->
                appendLine("clarification: ${clarification.reason.wireName}")
                appendLine("question: ${clarification.question}")
                if (clarification.detail.isNotBlank()) {
                    appendLine("detail: ${clarification.detail}")
                }
                if (clarification.candidateLabels.isNotEmpty()) {
                    appendLine("candidates: ${clarification.candidateLabels.joinToString(", ")}")
                }
            }
            step.stopReason?.let { reason ->
                appendLine("stop: ${reason.wireName}")
            }
            step.durationMillis?.let { duration ->
                appendLine("duration: ${duration}ms")
            }
        }.trimEnd()
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
            is AgentEvent.Clarification -> AgentRunStepStatus.WAITING
            is AgentEvent.FinalAnswer -> AgentRunStepStatus.COMPLETE
            is AgentEvent.RunCancelled -> AgentRunStepStatus.BLOCKED
            is AgentEvent.SkillActive -> AgentRunStepStatus.INFO
            is AgentEvent.TraceRecorded -> AgentRunStepStatus.INFO
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
            is AgentEvent.Clarification -> "Clarification needed"
            is AgentEvent.FinalAnswer -> "Final answer"
            is AgentEvent.RunCancelled -> "Run cancelled"
            is AgentEvent.SkillActive -> "Skill scope: ${event.title}"
            is AgentEvent.TraceRecorded -> "Workflow trace recorded"
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
            is AgentEvent.Clarification -> formatClarification(payload)
            is AgentEvent.FinalAnswer -> payload.getString("text")
            is AgentEvent.RunCancelled -> payload.getString("reason")
            is AgentEvent.SkillActive -> buildString {
                appendLine("source: ${payload.getString("activation_source")}")
                appendLine("risk: ${payload.getString("risk")}")
                append("reason: ${payload.getString("reason")}")
            }
            is AgentEvent.TraceRecorded -> "${payload.getInt("step_count")} step(s) captured"
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

    private fun formatClarification(payload: JSONObject): String {
        return buildString {
            payload.optString("tool").takeIf { it.isNotBlank() }?.let { appendLine("tool: $it") }
            appendLine("reason: ${payload.getString("reason")}")
            appendLine("question: ${payload.getString("question")}")
            payload.optString("detail").takeIf { it.isNotBlank() }?.let { appendLine("detail: $it") }
            val candidates = payload.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                appendLine("candidates:")
                for (index in 0 until candidates.length()) {
                    val candidate = candidates.optJSONObject(index) ?: continue
                    append("  - ")
                    append(candidate.optString("label", "unknown"))
                    candidate.optString("role").takeIf { it.isNotBlank() }?.let { append(" ($it)") }
                    candidate.optString("confidence").takeIf { it.isNotBlank() }?.let {
                        append(" confidence=$it")
                    }
                    appendLine()
                }
            }
        }.trimEnd()
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
