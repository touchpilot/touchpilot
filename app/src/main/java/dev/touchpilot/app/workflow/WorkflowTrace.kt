package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONArray
import org.json.JSONObject

/**
 * A captured recording of a successful agent run, kept as the raw input for a
 * future workflow definition (issue #289).
 *
 * It is **derived** from the existing [AgentRunRecord] rather than captured
 * through a parallel pipeline, so there is a single source of truth for what the
 * run did. Only runs that completed successfully and performed at least one tool
 * action produce a trace; informational, clarification, blocked, failed, and
 * cancelled runs return null.
 *
 * Redaction happens at capture time: tool arguments, results, and verification
 * reasons are already redacted inside the run's [dev.touchpilot.app.agent.AgentStep]s,
 * and the task string is redacted here. No raw secret enters a stored trace.
 */
data class WorkflowTrace(
    val runId: String,
    val task: String,
    val capturedAtMillis: Long,
    val steps: List<WorkflowTraceStep>,
    val screenSignals: List<WorkflowTraceScreenSignal>,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("run_id", runId)
            put("task", task)
            put("captured_at_millis", capturedAtMillis)
            put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
            put("screen_signals", JSONArray().apply { screenSignals.forEach { put(it.toJson()) } })
        }
    }

    companion object {
        /**
         * Derives a trace from a completed, successful run, or null when the run
         * is not a capturable workflow (no result, error, non-completed stop
         * reason, or no tool actions).
         */
        fun from(record: AgentRunRecord): WorkflowTrace? {
            val result = record.result ?: return null
            if (record.errorMessage != null) return null
            if (result.stopReason != AgentStepStopReason.COMPLETED) return null

            val steps = result.steps
                .filter { it.type == AgentStepType.ACT && it.toolCall != null }
                .map { step ->
                    val call = step.toolCall!!
                    WorkflowTraceStep(
                        index = step.sequenceNumber,
                        tool = call.tool,
                        args = call.args,
                        succeeded = call.result?.ok ?: false,
                        verification = step.verification?.let {
                            WorkflowTraceVerification(status = it.status, reason = it.reason)
                        },
                    )
                }
            if (steps.isEmpty()) return null

            val screenSignals = record.screenRecords.map {
                WorkflowTraceScreenSignal(
                    phase = it.phase,
                    nodeCount = it.nodeCount,
                    containsSensitiveContent = it.containsSensitiveContent,
                )
            }

            return WorkflowTrace(
                runId = record.id,
                task = SensitiveTextRedactor.redact(record.task),
                capturedAtMillis = record.completedAtMillis,
                steps = steps,
                screenSignals = screenSignals,
            )
        }
    }
}

/** One replayable tool action from the captured run. Arguments are pre-redacted. */
data class WorkflowTraceStep(
    val index: Int,
    val tool: String,
    val args: Map<String, String>,
    val succeeded: Boolean,
    val verification: WorkflowTraceVerification?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("tool", tool)
        put("args", JSONObject(args))
        put("succeeded", succeeded)
        put("verification", verification?.toJson() ?: JSONObject.NULL)
    }
}

/** Verification outcome recorded for a step, used later to express expected state. */
data class WorkflowTraceVerification(
    val status: String,
    val reason: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("status", status)
        put("reason", reason)
    }
}

/** Coarse screen-context signal captured at a run phase (e.g. "initial", "final"). */
data class WorkflowTraceScreenSignal(
    val phase: String,
    val nodeCount: Int,
    val containsSensitiveContent: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("phase", phase)
        put("node_count", nodeCount)
        put("contains_sensitive_content", containsSensitiveContent)
    }
}
