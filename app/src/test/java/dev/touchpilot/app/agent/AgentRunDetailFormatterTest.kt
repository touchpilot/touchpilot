package dev.touchpilot.app.agent

import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRunDetailFormatterTest {
    @Test
    fun compactSummaryIncludesEventAndStopReason() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Open settings"),
                AgentEvent.FinalAnswer("Done.")
            ),
            finalAnswer = "Done."
        )

        val summary = AgentRunDetailFormatter.compactSummary(record)

        assertContains(summary, "2 event(s)")
        assertContains(summary, "Completed: Done.")
        assertContains(summary, "Tap to inspect full run details.")
    }

    @Test
    fun deriveStopReasonForPolicyBlock() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Type password"),
                AgentEvent.PolicyBlocked(
                    tool = "type_text",
                    reason = "password or secret entry is blocked",
                    userMessage = "TouchPilot will not enter secrets."
                )
            )
        )

        assertEquals(
            "Blocked: password or secret entry is blocked",
            AgentRunDetailFormatter.deriveStopReason(record)
        )
    }

    @Test
    fun formatStepsIncludesVerificationDetails() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Tap save"),
                AgentEvent.ToolSucceeded(
                    tool = "tap",
                    message = "Tapped target",
                    data = mapOf(
                        "verification_status" to "passed",
                        "verification_reason" to "screen changed",
                        "screen_changed" to "true"
                    )
                )
            )
        )

        val steps = AgentRunDetailFormatter.formatSteps(record)

        assertEquals(2, steps.size)
        assertEquals(AgentRunStepStatus.SUCCESS, steps[1].status)
        assertContains(steps[1].detail, "verification: passed")
        assertContains(steps[1].detail, "verification reason: screen changed")
    }

    @Test
    fun exportRedactedTraceDoesNotLeakSensitiveArgs() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.ToolRequested(
                    tool = "type_text",
                    args = mapOf("text" to "password=hunter2"),
                    source = ToolSource.LOCAL_ROUTER
                )
            )
        )

        val trace = AgentRunDetailFormatter.exportRedactedTrace(record)

        assertContains(trace, "run_id=run-1")
        assertContains(trace, "[REDACTED]")
        assertFalse("hunter2" in trace)
    }

    @Test
    fun unavailableRunDataProducesEmptyStepsAndReason() {
        val record = AgentRunRecord(
            id = "run-missing",
            task = "Open settings",
            startedAtMillis = 1L,
            completedAtMillis = 2L,
            result = null,
            errorMessage = "Agent failed: timeout"
        )

        assertTrue(AgentRunDetailFormatter.formatSteps(record).isEmpty())
        assertEquals("Error: Agent failed: timeout", AgentRunDetailFormatter.deriveStopReason(record))
    }

    private fun sampleRecord(
        events: List<AgentEvent>,
        finalAnswer: String? = null
    ): AgentRunRecord {
        return AgentRunRecord(
            id = "run-1",
            task = "Open settings",
            startedAtMillis = 1_000L,
            completedAtMillis = 2_000L,
            result = AgentRunResult(
                transcript = "transcript",
                finalAnswer = finalAnswer,
                events = events
            )
        )
    }
}
