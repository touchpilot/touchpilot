package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.security.PolicyWorkflowClass
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk
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
    val skillId: String? = null,
    val allowedTools: List<String> = emptyList(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("run_id", runId)
            put("task", task)
            put("captured_at_millis", capturedAtMillis)
            put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
            put("screen_signals", JSONArray().apply { screenSignals.forEach { put(it.toJson()) } })
            put("skill_id", skillId ?: JSONObject.NULL)
            put("allowed_tools", JSONArray().apply { allowedTools.forEach { put(it) } })
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
                        source = call.source,
                        succeeded = call.result?.ok ?: false,
                        verification = step.verification?.let {
                            WorkflowTraceVerification(status = it.status, reason = it.reason)
                        },
                        requiresApproval = toolRequiresApproval(call.tool),
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

        private fun toolRequiresApproval(tool: String): Boolean {
            val risk = AndroidToolCatalog.find(tool)?.risk ?: return false
            return risk == ToolRisk.MEDIUM || risk == ToolRisk.HIGH
        }

        fun fromJson(json: JSONObject): WorkflowTrace? {
            val runId = json.optString("run_id").trim()
            val task = json.optString("task").trim()
            val capturedAtMillis = json.optLong("captured_at_millis", Long.MIN_VALUE)
            if (runId.isBlank() || capturedAtMillis == Long.MIN_VALUE) return null

            val steps = json.optJSONArray("steps")?.let(::workflowStepsFromJson) ?: return null
            val screenSignals = workflowScreenSignalsFromJson(json.optJSONArray("screen_signals"))
            if (steps.isEmpty()) return null

            val allowedTools = json.optJSONArray("allowed_tools")?.let { array ->
                (0 until array.length())
                    .mapNotNull { index -> array.optString(index, "")
                        .trim()
                        .takeIf { it.isNotBlank() }
                    }
                    .toList()
            }.orEmpty()

            val skillId = if (json.isNull("skill_id")) {
                null
            } else {
                json.optString("skill_id").trim().ifBlank { null }
            }

            return WorkflowTrace(
                runId = runId,
                task = task,
                capturedAtMillis = capturedAtMillis,
                steps = steps,
                screenSignals = screenSignals,
                skillId = skillId,
                allowedTools = allowedTools,
            )
        }

        private fun workflowStepsFromJson(jsonArray: JSONArray): List<WorkflowTraceStep> {
            return (0 until jsonArray.length()).mapNotNull { index ->
                workflowStepFromJson(jsonArray.optJSONObject(index))
            }
        }

        private fun workflowStepFromJson(json: JSONObject?): WorkflowTraceStep? {
            if (json == null) return null
            val index = json.optInt("index", Int.MIN_VALUE)
            val tool = json.optString("tool").trim()
            if (index == Int.MIN_VALUE || tool.isBlank()) return null

            return WorkflowTraceStep(
                index = index,
                tool = tool,
                args = json.optJSONObject("args")?.let(::workflowArgsFromJson).orEmpty(),
                source = json.optString("source").trim(),
                succeeded = json.optBoolean("succeeded", false),
                verification = workflowVerificationFromJson(json.optJSONObject("verification")),
                requiresApproval = json.optBooleanOrNull("requires_approval"),
                workflowClass = json.optString("workflow_class").trim().uppercase().let { name ->
                    name.takeIf { it.isNotBlank() }?.let { runCatching { PolicyWorkflowClass.valueOf(it) }.getOrNull() }
                },
            )
        }

        private fun workflowVerificationFromJson(json: JSONObject?): WorkflowTraceVerification? {
            if (json == null) return null
            val status = json.optString("status").trim()
            val reason = json.optString("reason").trim()
            if (status.isBlank() && reason.isBlank()) return null
            return WorkflowTraceVerification(
                status = status,
                reason = reason,
            )
        }

        private fun workflowScreenSignalsFromJson(jsonArray: JSONArray?): List<WorkflowTraceScreenSignal> {
            if (jsonArray == null) return emptyList()
            return (0 until jsonArray.length()).mapNotNull { index ->
                workflowScreenSignalFromJson(jsonArray.optJSONObject(index))
            }
        }

        private fun workflowScreenSignalFromJson(json: JSONObject?): WorkflowTraceScreenSignal? {
            if (json == null) return null
            val phase = json.optString("phase").trim()
            if (phase.isBlank()) return null

            return WorkflowTraceScreenSignal(
                phase = phase,
                nodeCount = json.optInt("node_count", 0),
                containsSensitiveContent = json.optBoolean("contains_sensitive_content", false),
            )
        }

        private fun workflowArgsFromJson(json: JSONObject): Map<String, String> {
            val args = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                args[key] = json.optString(key).trim()
            }
            return args.toMap()
        }
    }
}

private fun JSONObject.optBooleanOrNull(name: String): Boolean? {
    if (!has(name) || isNull(name)) return null
    return try {
        getBoolean(name)
    } catch (_: Exception) {
        null
    }
}

/** One replayable tool action from the captured run. Arguments are pre-redacted. */
data class WorkflowTraceStep(
    val index: Int,
    val tool: String,
    val args: Map<String, String>,
    val source: String = "",
    val succeeded: Boolean,
    val verification: WorkflowTraceVerification?,
    val requiresApproval: Boolean? = null,
    val workflowClass: PolicyWorkflowClass? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        put("tool", tool)
        put("source", source)
        put("args", JSONObject(args))
        put("succeeded", succeeded)
        put("verification", verification?.toJson() ?: JSONObject.NULL)
        put("requires_approval", requiresApproval ?: JSONObject.NULL)
        put(
            "workflow_class",
            workflowClass?.name?.lowercase() ?: JSONObject.NULL,
        )
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
