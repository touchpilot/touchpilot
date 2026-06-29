package dev.touchpilot.app.workflow

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured local summary derived from a captured workflow trace.
 *
 * The summary stays on device and is computed only from the redacted trace
 * payload: captured tool steps plus coarse screen signals. It is intended to be
 * a lightweight inspection artifact for the demonstration-to-skill pipeline.
 */
data class WorkflowTraceSummary(
    val runId: String,
    val task: String,
    val capturedAtMillis: Long,
    val stepSummaries: List<WorkflowTraceStepSummary>,
    val screenSignals: List<WorkflowTraceScreenSignalSummary>,
    val toolCounts: List<WorkflowTraceToolCount>,
    val overview: String,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("run_id", runId)
            put("task", task)
            put("captured_at_millis", capturedAtMillis)
            put(
                "step_summaries",
                JSONArray().apply { stepSummaries.forEach { put(it.toJson()) } },
            )
            put(
                "screen_signals",
                JSONArray().apply { screenSignals.forEach { put(it.toJson()) } },
            )
            put(
                "tool_counts",
                JSONArray().apply { toolCounts.forEach { put(it.toJson()) } },
            )
            put("overview", overview)
        }
    }
}

data class WorkflowTraceStepSummary(
    val index: Int,
    val tool: String,
    val source: String,
    val succeeded: Boolean,
    val requiresApproval: Boolean,
    val verificationStatus: String? = null,
    val verificationReason: String? = null,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("index", index)
            put("tool", tool)
            put("source", source)
            put("succeeded", succeeded)
            put("requires_approval", requiresApproval)
            put("verification_status", verificationStatus ?: JSONObject.NULL)
            put("verification_reason", verificationReason ?: JSONObject.NULL)
        }
    }
}

data class WorkflowTraceScreenSignalSummary(
    val phase: String,
    val nodeCount: Int,
    val containsSensitiveContent: Boolean,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("phase", phase)
            put("node_count", nodeCount)
            put("contains_sensitive_content", containsSensitiveContent)
        }
    }
}

data class WorkflowTraceToolCount(
    val tool: String,
    val count: Int,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("tool", tool)
            put("count", count)
        }
    }
}

object WorkflowTraceSummarizer {
    fun summarize(trace: WorkflowTrace): WorkflowTraceSummary {
        val stepSummaries = trace.steps.map { step ->
            WorkflowTraceStepSummary(
                index = step.index,
                tool = step.tool,
                source = step.source,
                succeeded = step.succeeded,
                requiresApproval = step.requiresApproval == true,
                verificationStatus = step.verification?.status,
                verificationReason = step.verification?.reason,
            )
        }

        val toolCounts = trace.steps
            .groupingBy { it.tool }
            .eachCount()
            .map { (tool, count) -> WorkflowTraceToolCount(tool = tool, count = count) }
            .sortedWith(compareByDescending<WorkflowTraceToolCount> { it.count }.thenBy { it.tool })

        val screenSignals = trace.screenSignals.map { signal ->
            WorkflowTraceScreenSignalSummary(
                phase = signal.phase,
                nodeCount = signal.nodeCount,
                containsSensitiveContent = signal.containsSensitiveContent,
            )
        }

        return WorkflowTraceSummary(
            runId = trace.runId,
            task = trace.task,
            capturedAtMillis = trace.capturedAtMillis,
            stepSummaries = stepSummaries,
            screenSignals = screenSignals,
            toolCounts = toolCounts,
            overview = buildOverview(stepSummaries, screenSignals, toolCounts),
        )
    }

    private fun buildOverview(
        stepSummaries: List<WorkflowTraceStepSummary>,
        screenSignals: List<WorkflowTraceScreenSignalSummary>,
        toolCounts: List<WorkflowTraceToolCount>,
    ): String {
        if (stepSummaries.isEmpty()) {
            return "No tool actions were captured."
        }

        val parts = mutableListOf<String>()
        parts += "Captured ${stepSummaries.size} step${if (stepSummaries.size == 1) "" else "s"}."
        parts += when (toolCounts.size) {
            0 -> "No tools were recorded."
            1 -> "Tool used: ${toolCounts.first().tool} (${toolCounts.first().count})."
            else -> "Tools used: ${toolCounts.joinToString { "${it.tool} (${it.count})" }}."
        }

        val approvalCount = stepSummaries.count { it.requiresApproval }
        parts += when (approvalCount) {
            0 -> "No steps required approval."
            1 -> "1 step required approval."
            else -> "$approvalCount steps required approval."
        }

        if (screenSignals.isEmpty()) {
            parts += "No screen snapshots were recorded."
        } else {
            val visibleScreens = screenSignals.filter { !it.containsSensitiveContent }
            val sensitiveScreens = screenSignals.count { it.containsSensitiveContent }
            parts += "Captured ${screenSignals.size} screen snapshot${if (screenSignals.size == 1) "" else "s"}."
            if (visibleScreens.isNotEmpty()) {
                parts += "Screens observed: ${visibleScreens.joinToString { "${it.phase} (${it.nodeCount} nodes)" }}."
            }
            if (sensitiveScreens > 0) {
                parts += "$sensitiveScreens screen snapshot${if (sensitiveScreens == 1) "" else "s"} contained sensitive content."
            }
        }

        return parts.joinToString(" ")
    }
}
