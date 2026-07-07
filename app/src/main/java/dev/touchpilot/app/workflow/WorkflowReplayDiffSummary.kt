package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.agent.StepVerificationCardModel

/**
 * Formats pass/fail diff summaries for workflow replay surfaces (issue #382).
 */
object WorkflowReplayDiffSummary {
    fun verificationDiff(result: AgentRunResult): String? {
        result.events.filterIsInstance<AgentEvent.WorkflowStepVerificationFailed>().lastOrNull()?.let { failed ->
            return buildString {
                appendLine("Expected: ${failed.expectedSummary}")
                append("Observed: ${failed.observedSummary}")
            }
        }

        val failedVerifyStep = result.steps.lastOrNull { step ->
            step.type == AgentStepType.VERIFY && step.status == AgentStepStatus.FAILED
        } ?: return null

        val expected = failedVerifyStep.verification?.data?.get("expected")
        val observed = failedVerifyStep.verification?.data?.get("observed")
        if (expected == null && observed == null) return null
        return buildString {
            if (expected != null) appendLine("Expected: $expected")
            if (observed != null) append("Observed: $observed")
        }
    }

    fun completionMessage(result: WorkflowReplayResult): String {
        val base = if (result.stopReason == AgentStepStopReason.COMPLETED) {
            "Workflow \"${result.workflowTitle}\" replayed successfully " +
                "(${result.completedStepCount}/${result.totalStepCount} steps verified)."
        } else {
            result.stopMessage.ifBlank { "Workflow replay stopped before completion." }
        }
        val timing = if (result.durationMs > 0L) {
            " Replay duration: ${result.durationMs}ms."
        } else {
            ""
        }
        val diff = result.verificationCards.lastOrNull { !it.passed }?.let { card ->
            "\nExpected: ${card.expectedSummary}\nObserved: ${card.observedSummary}"
        }.orEmpty()
        return base + timing + diff
    }
}
