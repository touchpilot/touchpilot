package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Groups a completed agent run's structured step timeline into the categories a
 * trace viewer needs to surface (issue #379): tool calls, approvals, and
 * errors. Each entry keeps the sequence number of the step that produced it, so
 * the viewer can link an entry back to the step — and therefore the screen
 * context — it came from.
 *
 * [AgentRunDetailFormatter.formatSteps] already renders the *flat* step
 * timeline; this formatter is the *grouped* projection over that same timeline,
 * so a reviewer can answer "what did the agent call, what needed approval, and
 * what failed?" without scanning every step. It reuses [AgentRunStepStatus] (and
 * its [severity]) so the renderer colors grouped entries exactly like the flat
 * timeline.
 *
 * A single step can legitimately appear in more than one group: a failed tool
 * call is both a tool call (with `FAILED` status) and an error. That is
 * intentional — the groups are views over one timeline, not a partition of it.
 *
 * Redaction: [AgentStep] display fields are already redacted at construction
 * (see [AgentStepFactory]), but this formatter still routes every string it
 * assembles through [SensitiveTextRedactor] — including the run-level
 * [AgentRunRecord.errorMessage], which is *not* pre-redacted — so the viewer and
 * its structured export ([toJson]) respect the same redaction rules the raw
 * trace export does.
 */
object StructuredTraceFormatter {

    /** The categories a structured trace groups a run into. */
    enum class TraceGroup(val label: String) {
        TOOL_CALLS("Tool calls"),
        APPROVALS("Approvals"),
        ERRORS("Errors"),
    }

    /**
     * A single grouped entry. [stepLabel] links the entry back to the timeline
     * ("Step 3", or "Run" for a run-level error). All display text is redacted.
     */
    data class TraceEntry(
        val stepLabel: String,
        val title: String,
        val status: AgentRunStepStatus,
        val detail: String,
    )

    data class TraceSection(
        val group: TraceGroup,
        val entries: List<TraceEntry>,
    ) {
        val count: Int get() = entries.size
    }

    data class StructuredTrace(
        val runId: String,
        val stepCount: Int,
        val toolCalls: TraceSection,
        val approvals: TraceSection,
        val errors: TraceSection,
    ) {
        /** Sections in display order. */
        val sections: List<TraceSection> get() = listOf(toolCalls, approvals, errors)

        /** One-line overview, e.g. "3 steps · 2 tool calls · 1 approval · 1 error". */
        val summary: String
            get() = listOf(
                countLabel(stepCount, "step"),
                countLabel(toolCalls.count, "tool call"),
                countLabel(approvals.count, "approval"),
                countLabel(errors.count, "error"),
            ).joinToString(separator = " · ")
    }

    // Stop reasons that represent a genuine failure. Deliberately excludes
    // MAX_STEPS / USER_CANCELLED / CLARIFICATION_NEEDED (bounded stops, not
    // errors) and the approval reasons below.
    private val errorStopReasons = setOf(
        AgentStepStopReason.REPEATED_TOOL_FAILURE,
        AgentStepStopReason.PARSER_ERROR,
        AgentStepStopReason.EXECUTOR_ERROR,
        AgentStepStopReason.NO_VALID_ACTION,
        AgentStepStopReason.VERIFICATION_FAILED,
    )

    // Stop reasons that ended the run on an approval / policy decision.
    private val approvalStopReasons = setOf(
        AgentStepStopReason.POLICY_BLOCKED,
        AgentStepStopReason.APPROVAL_DENIED,
    )

    /** Build the grouped structured trace for [record]. */
    fun build(record: AgentRunRecord): StructuredTrace {
        val steps = record.result?.steps.orEmpty()
        return StructuredTrace(
            runId = record.id,
            stepCount = steps.size,
            toolCalls = TraceSection(TraceGroup.TOOL_CALLS, toolCalls(steps)),
            approvals = TraceSection(TraceGroup.APPROVALS, approvals(steps)),
            errors = TraceSection(TraceGroup.ERRORS, errors(record, steps)),
        )
    }

    /**
     * The grouped structured trace as JSON, so trace export can share the same
     * structured shape the viewer renders. All strings are already redacted.
     */
    fun toJson(record: AgentRunRecord): JSONObject {
        val trace = build(record)
        return JSONObject().apply {
            put("run_id", trace.runId)
            put("step_count", trace.stepCount)
            put("summary", trace.summary)
            put("sections", JSONArray().apply {
                trace.sections.forEach { section ->
                    put(JSONObject().apply {
                        put("group", section.group.name.lowercase())
                        put("count", section.count)
                        put("entries", JSONArray().apply {
                            section.entries.forEach { entry ->
                                put(JSONObject().apply {
                                    put("step", entry.stepLabel)
                                    put("title", entry.title)
                                    put("status", entry.status.label)
                                    put("detail", entry.detail)
                                })
                            }
                        })
                    })
                }
            })
        }
    }

    private fun toolCalls(steps: List<AgentStep>): List<TraceEntry> {
        return steps
            .filter { it.type == AgentStepType.ACT }
            .map { step ->
                val call = step.toolCall
                TraceEntry(
                    stepLabel = stepLabel(step.sequenceNumber),
                    title = call?.tool?.takeIf { it.isNotBlank() } ?: "tool call",
                    status = displayStatus(step.status),
                    detail = toolCallDetail(step, call),
                )
            }
    }

    private fun approvals(steps: List<AgentStep>): List<TraceEntry> {
        return steps.mapNotNull { step ->
            when {
                step.type == AgentStepType.STOP && step.stopReason in approvalStopReasons ->
                    TraceEntry(
                        stepLabel = stepLabel(step.sequenceNumber),
                        title = step.stopReason?.wireName ?: "approval",
                        status = AgentRunStepStatus.BLOCKED,
                        detail = redactedOutput(step),
                    )
                step.status == AgentStepStatus.BLOCKED ->
                    TraceEntry(
                        stepLabel = stepLabel(step.sequenceNumber),
                        title = step.toolCall?.tool?.takeIf { it.isNotBlank() } ?: "blocked action",
                        status = AgentRunStepStatus.BLOCKED,
                        detail = redactedOutput(step),
                    )
                else -> null
            }
        }
    }

    private fun errors(record: AgentRunRecord, steps: List<AgentStep>): List<TraceEntry> {
        val entries = mutableListOf<TraceEntry>()
        record.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            entries += TraceEntry(
                stepLabel = "Run",
                title = "run error",
                status = AgentRunStepStatus.FAILED,
                detail = SensitiveTextRedactor.redact(message),
            )
        }
        steps.forEach { step ->
            when {
                step.status == AgentStepStatus.FAILED ->
                    entries += TraceEntry(
                        stepLabel = stepLabel(step.sequenceNumber),
                        title = errorTitle(step),
                        status = AgentRunStepStatus.FAILED,
                        detail = redactedOutput(step),
                    )
                step.type == AgentStepType.STOP && step.stopReason in errorStopReasons ->
                    entries += TraceEntry(
                        stepLabel = stepLabel(step.sequenceNumber),
                        title = step.stopReason?.wireName ?: "error",
                        status = AgentRunStepStatus.FAILED,
                        detail = redactedOutput(step),
                    )
            }
        }
        return entries
    }

    private fun toolCallDetail(step: AgentStep, call: AgentStepToolCall?): String {
        if (call == null) return redactedOutput(step)
        return buildString {
            append("source: ${call.source}")
            val args = SensitiveTextRedactor.redact(call.args)
            if (args.isNotEmpty()) {
                appendLine()
                append("args: ${args.entries.joinToString(separator = ", ") { "${it.key}=${it.value}" }}")
            }
            call.result?.let { result ->
                appendLine()
                append("result: ${if (result.ok) "ok" else "failed"}")
                val message = SensitiveTextRedactor.redact(result.message)
                if (message.isNotBlank()) append(" — $message")
            }
        }
    }

    private fun errorTitle(step: AgentStep): String {
        return when (step.type) {
            AgentStepType.ACT -> step.toolCall?.tool?.takeIf { it.isNotBlank() } ?: "tool error"
            AgentStepType.VERIFY -> "verification failed"
            else -> step.type.wireName
        }
    }

    private fun redactedOutput(step: AgentStep): String = SensitiveTextRedactor.redact(step.outputSummary)

    private fun stepLabel(sequenceNumber: Int): String = "Step $sequenceNumber"

    private fun displayStatus(status: AgentStepStatus): AgentRunStepStatus {
        return when (status) {
            AgentStepStatus.PENDING -> AgentRunStepStatus.PENDING
            AgentStepStatus.RUNNING -> AgentRunStepStatus.RUNNING
            AgentStepStatus.OK -> AgentRunStepStatus.SUCCESS
            AgentStepStatus.FAILED -> AgentRunStepStatus.FAILED
            AgentStepStatus.BLOCKED -> AgentRunStepStatus.BLOCKED
            AgentStepStatus.CLARIFIED -> AgentRunStepStatus.WAITING
            AgentStepStatus.STOPPED -> AgentRunStepStatus.COMPLETE
        }
    }
}

private fun countLabel(count: Int, singular: String): String =
    "$count $singular" + if (count == 1) "" else "s"
