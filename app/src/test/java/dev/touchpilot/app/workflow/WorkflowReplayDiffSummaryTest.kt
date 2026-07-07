package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStepStopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowReplayDiffSummaryTest {
    @Test
    fun formatsVerificationDiffFromFailedEvent() {
        val result = AgentRunResult(
            transcript = "failed",
            finalAnswer = null,
            stopReason = AgentStepStopReason.VERIFICATION_FAILED,
            stopMessage = "Step failed.",
            events = listOf(
                AgentEvent.WorkflowStepVerificationFailed(
                    stepIndex = 1,
                    tool = "tap",
                    expectedSummary = "Text \"Network\" is present on screen",
                    observedSummary = "visible text includes \"Home\"",
                    reason = "timeout",
                )
            ),
        )

        val diff = WorkflowReplayDiffSummary.verificationDiff(result)

        assertTrue(diff!!.contains("Expected: Text \"Network\" is present on screen"))
        assertTrue(diff.contains("Observed: visible text includes \"Home\""))
    }

    @Test
    fun completionMessageIncludesDurationAndStepCounts() {
        val message = WorkflowReplayDiffSummary.completionMessage(
            WorkflowReplayResult(
                workflowId = "wf",
                workflowTitle = "Open Settings",
                events = emptyList(),
                steps = emptyList(),
                stopReason = AgentStepStopReason.COMPLETED,
                stopMessage = "",
                completedStepCount = 2,
                totalStepCount = 2,
                durationMs = 1250L,
            )
        )

        assertTrue(message.contains("replayed successfully"))
        assertTrue(message.contains("2/2"))
        assertTrue(message.contains("1250ms"))
    }
}
