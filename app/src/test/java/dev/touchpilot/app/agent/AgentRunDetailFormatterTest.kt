package dev.touchpilot.app.agent

import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolVerificationResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRunDetailFormatterTest {
    @Test
    fun buildCompletionSummaryIncludesStructuredRunDetails() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "tap",
                    args = emptyMap(),
                    source = "local_router",
                    result = ToolResult(ok = true, message = "Tapped target"),
                ),
                AgentStepFactory.verify(
                    sequenceNumber = 2,
                    verification = ToolVerificationResult.Passed("screen changed"),
                ),
                AgentStepFactory.stop(
                    sequenceNumber = 3,
                    reason = AgentStepStopReason.COMPLETED,
                    outputSummary = "Done.",
                ),
            ),
            stopReason = AgentStepStopReason.COMPLETED,
            stopMessage = AgentStepStopReason.COMPLETED.userMessage,
            finalAnswer = "Done.",
        )

        val summary = AgentRunDetailFormatter.buildCompletionSummary(record)

        assertEquals(AgentRunCompletionStatus.COMPLETED, summary.status)
        assertEquals(3, summary.stepCount)
        assertContains(summary.stopReason, AgentStepStopReason.COMPLETED.userMessage)
        assertContains(summary.lastVerificationOutcome.orEmpty(), "Passed")
        assertContains(summary.lastVerificationOutcome.orEmpty(), "screen changed")
        assertEquals(null, summary.nextAction)
    }

    @Test
    fun buildCompletionSummaryMapsBlockedPolicyStop() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.stop(
                    sequenceNumber = 1,
                    reason = AgentStepStopReason.POLICY_BLOCKED,
                    outputSummary = "TouchPilot will not enter secrets.",
                ),
            ),
            stopReason = AgentStepStopReason.POLICY_BLOCKED,
            stopMessage = "TouchPilot stopped because policy blocked the next action.",
        )

        val summary = AgentRunDetailFormatter.buildCompletionSummary(record)

        assertEquals(AgentRunCompletionStatus.BLOCKED, summary.status)
        assertContains(summary.stopReason, "policy blocked")
        assertContains(summary.nextAction.orEmpty(), "safer alternative")
    }

    @Test
    fun buildCompletionSummaryUsesDisplayStepsWhenResultMissing() {
        val record = AgentRunRecord(
            id = "run-failed",
            task = "Open settings",
            startedAtMillis = 1L,
            completedAtMillis = 2L,
            result = null,
            errorMessage = "Agent failed: timeout",
        )
        val displaySteps = listOf(
            AgentStepFactory.act(
                sequenceNumber = 1,
                tool = "observe_screen",
                args = emptyMap(),
                source = "local_router",
            ),
            AgentStepFactory.stop(
                sequenceNumber = 2,
                reason = AgentStepStopReason.EXECUTOR_ERROR,
                outputSummary = "Agent failed: timeout",
            ),
        )

        val summary = AgentRunDetailFormatter.buildCompletionSummary(record, displaySteps)

        assertEquals(AgentRunCompletionStatus.FAILED, summary.status)
        assertEquals(2, summary.stepCount)
        assertContains(summary.stopReason, "timeout")
        assertContains(summary.nextAction.orEmpty(), "retry")
    }

    @Test
    fun buildCompletionSummaryMapsClarificationNeeded() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.clarify(
                    sequenceNumber = 1,
                    clarification = AgentStepClarification(
                        reason = AgentStepClarificationReason.AMBIGUOUS_REQUEST,
                        question = "Which button should I tap?",
                    ),
                ),
            ),
            stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
            stopMessage = AgentStepStopReason.CLARIFICATION_NEEDED.userMessage,
        )

        val summary = AgentRunDetailFormatter.buildCompletionSummary(record)

        assertEquals(AgentRunCompletionStatus.NEEDS_CLARIFICATION, summary.status)
        assertContains(summary.nextAction.orEmpty(), "Which button should I tap?")
    }

    @Test
    fun buildCompletionSummaryMapsCancelledStop() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.stop(
                    sequenceNumber = 1,
                    reason = AgentStepStopReason.USER_CANCELLED,
                    outputSummary = "Approval not granted",
                ),
            ),
            stopReason = AgentStepStopReason.USER_CANCELLED,
            stopMessage = AgentStepStopReason.USER_CANCELLED.userMessage,
        )

        val summary = AgentRunDetailFormatter.buildCompletionSummary(record)

        assertEquals(AgentRunCompletionStatus.CANCELLED, summary.status)
        assertContains(summary.nextAction.orEmpty(), "Approve")
    }

    @Test
    fun compactSummaryPrefersStructuredSteps() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "observe_screen",
                    args = emptyMap(),
                    source = "local_router",
                    result = ToolResult(ok = true, message = "Settings screen"),
                ),
                AgentStepFactory.stop(
                    sequenceNumber = 2,
                    reason = AgentStepStopReason.MAX_STEPS,
                    outputSummary = "step limit reached",
                ),
            ),
            stopReason = AgentStepStopReason.MAX_STEPS,
            stopMessage = AgentStepStopReason.MAX_STEPS.userMessage,
        )

        val summary = AgentRunDetailFormatter.compactSummary(record)

        assertContains(summary, "2 step(s)")
        assertContains(summary, "1 tool result(s)")
        assertContains(summary, AgentStepStopReason.MAX_STEPS.userMessage)
        assertFalse(summary.contains("event(s)"))
    }

    @Test
    fun deriveStopReasonUsesCanonicalStopMessage() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.stop(
                    sequenceNumber = 1,
                    reason = AgentStepStopReason.POLICY_BLOCKED,
                    outputSummary = "blocked",
                ),
            ),
            stopReason = AgentStepStopReason.POLICY_BLOCKED,
            stopMessage = "TouchPilot stopped because policy blocked the next action.",
        )

        assertEquals(
            "TouchPilot stopped because policy blocked the next action.",
            AgentRunDetailFormatter.deriveStopReason(record),
        )
    }

    @Test
    fun formatStepsFromStructuredIncludesVerificationDetails() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.verify(
                    sequenceNumber = 1,
                    verification = ToolVerificationResult.Passed("screen changed"),
                ),
            ),
        )

        val steps = AgentRunDetailFormatter.formatSteps(record)

        assertEquals(1, steps.size)
        assertEquals("Verify", steps.single().title)
        assertContains(steps.single().detail, "verification: passed")
        assertContains(steps.single().detail, "verification reason: screen changed")
    }

    @Test
    fun exportIncludesStructuredStepsAndEvents() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "type_text",
                    args = mapOf("text" to "password=hunter2"),
                    source = "local_router",
                ),
            ),
            events = listOf(
                AgentEvent.ToolRequested(
                    tool = "type_text",
                    args = mapOf("text" to "password=hunter2"),
                    source = ToolSource.LOCAL_ROUTER,
                ),
            ),
            stopReason = AgentStepStopReason.NO_VALID_ACTION,
            stopMessage = "custom stop message",
        )

        val trace = AgentRunDetailFormatter.exportRedactedTrace(record)

        assertContains(trace, "canonical_stop_reason=no_valid_action")
        assertContains(trace, "canonical_stop_message=custom stop message")
        assertContains(trace, "steps:")
        assertContains(trace, "\"sequence_number\":1")
        assertContains(trace, "events:")
        assertContains(trace, "tool_requested")
        assertFalse("hunter2" in trace)
    }

    @Test
    fun exportIncludesRedactedScreenRecords() {
        val record = sampleRecord(
            screenRecords = listOf(
                AgentScreenRecord.capture(
                    sequenceNumber = 0,
                    phase = "initial",
                    timestampMillis = 1_000L,
                    context = ScreenContext(
                        packageName = "com.example.bank",
                        nodes = listOf(
                            ScreenNode(
                                nodeId = "0.1",
                                role = NodeRole.INPUT,
                                text = ScreenText.of("password: hunter2"),
                                isInputField = true
                            )
                        )
                    )
                )
            )
        )

        val trace = AgentRunDetailFormatter.exportRedactedTrace(record)

        assertContains(trace, "screen_records:")
        assertContains(trace, "\"phase\":\"initial\"")
        assertContains(trace, "\"contains_sensitive_content\":true")
        assertContains(trace, "[REDACTED]")
        assertFalse("hunter2" in trace)
    }

    @Test
    fun eventsFallbackWhenNoStructuredSteps() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Open settings"),
                AgentEvent.FinalAnswer("Done."),
            ),
            finalAnswer = "Done.",
        )

        val summary = AgentRunDetailFormatter.compactSummary(record)

        assertContains(summary, "2 event(s)")
        assertContains(summary, "Completed: Done.")
        assertEquals(2, AgentRunDetailFormatter.formatSteps(record).size)
    }

    @Test
    fun deriveStopReasonFallsBackToPolicyBlockEvent() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Type password"),
                AgentEvent.PolicyBlocked(
                    tool = "type_text",
                    reason = "password or secret entry is blocked",
                    userMessage = "TouchPilot will not enter secrets.",
                ),
            ),
        )

        assertEquals(
            "Blocked: password or secret entry is blocked",
            AgentRunDetailFormatter.deriveStopReason(record),
        )
    }

    @Test
    fun formatStepsFromEventsIncludesVerificationDetails() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Tap save"),
                AgentEvent.ToolSucceeded(
                    tool = "tap",
                    message = "Tapped target",
                    data = mapOf(
                        "verification_status" to "passed",
                        "verification_reason" to "screen changed",
                        "screen_changed" to "true",
                    ),
                ),
            ),
        )

        val steps = AgentRunDetailFormatter.formatSteps(record)

        assertEquals(2, steps.size)
        assertEquals(AgentRunStepStatus.SUCCESS, steps[1].status)
        assertContains(steps[1].detail, "verification: passed")
        assertContains(steps[1].detail, "verification reason: screen changed")
    }

    @Test
    fun formatStepsRedactsSensitiveVerificationReasons() {
        val record = sampleRecord(
            events = listOf(
                AgentEvent.UserMessage("Tap save"),
                AgentEvent.ToolSucceeded(
                    tool = "tap",
                    message = "Tapped target",
                    data = mapOf(
                        "verification_status" to "passed",
                        "verification_reason" to "password: hunter2",
                        "screen_changed" to "true",
                    ),
                ),
            ),
        )

        val steps = AgentRunDetailFormatter.formatSteps(record)

        assertEquals(2, steps.size)
        assertContains(steps[1].detail, "verification reason: [REDACTED]")
    }

    @Test
    fun unavailableRunDataProducesEmptyStepsAndReason() {
        val record = AgentRunRecord(
            id = "run-missing",
            task = "Open settings",
            startedAtMillis = 1L,
            completedAtMillis = 2L,
            result = null,
            errorMessage = "Agent failed: timeout",
        )

        assertTrue(AgentRunDetailFormatter.formatSteps(record).isEmpty())
        assertEquals("Error: Agent failed: timeout", AgentRunDetailFormatter.deriveStopReason(record))
    }

    @Test
    fun formatsTraceRecordedEvent() {
        val record = sampleRecord(events = listOf(AgentEvent.TraceRecorded(runId = "run-1", stepCount = 3)))

        val step = AgentRunDetailFormatter.formatSteps(record).single { it.title == "Workflow trace recorded" }

        assertEquals(AgentRunStepStatus.INFO, step.status)
        assertContains(step.detail, "3 step(s) captured")
    }

    private fun sampleRecord(
        events: List<AgentEvent> = emptyList(),
        steps: List<AgentStep> = emptyList(),
        screenRecords: List<AgentScreenRecord> = emptyList(),
        finalAnswer: String? = null,
        stopReason: AgentStepStopReason? = null,
        stopMessage: String = "",
    ): AgentRunRecord {
        return AgentRunRecord(
            id = "run-1",
            task = "Open settings",
            startedAtMillis = 1_000L,
            completedAtMillis = 2_000L,
            result = AgentRunResult(
                transcript = "transcript",
                finalAnswer = finalAnswer,
                events = events,
                steps = steps,
                stopReason = stopReason,
                stopMessage = stopMessage,
            ),
            screenRecords = screenRecords,
        )
    }
}
