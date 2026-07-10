package dev.touchpilot.app.agent

import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StructuredTraceFormatterTest {

    @Test
    fun buildGroupsToolCallsApprovalsAndErrors() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "tap",
                    args = emptyMap(),
                    source = "local_router",
                    result = ToolResult(ok = true, message = "Tapped target"),
                ),
                AgentStepFactory.act(
                    sequenceNumber = 2,
                    tool = "type_text",
                    args = emptyMap(),
                    source = "local_model",
                    result = ToolResult(ok = false, message = "field not focused"),
                ),
                AgentStepFactory.stop(
                    sequenceNumber = 3,
                    reason = AgentStepStopReason.POLICY_BLOCKED,
                    outputSummary = "TouchPilot will not enter secrets.",
                ),
            ),
        )

        val trace = StructuredTraceFormatter.build(record)

        assertEquals(3, trace.stepCount)
        assertEquals(2, trace.toolCalls.count)
        assertEquals(1, trace.approvals.count)
        assertEquals(1, trace.errors.count)
        assertEquals("3 steps · 2 tool calls · 1 approval · 1 error", trace.summary)
        assertEquals("policy_blocked", trace.approvals.entries.single().title)
        assertEquals(
            listOf(
                StructuredTraceFormatter.TraceGroup.TOOL_CALLS,
                StructuredTraceFormatter.TraceGroup.APPROVALS,
                StructuredTraceFormatter.TraceGroup.ERRORS,
            ),
            trace.sections.map { it.group },
        )
    }

    @Test
    fun failedToolCallAppearsInBothToolCallsAndErrors() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 5,
                    tool = "open_app",
                    args = emptyMap(),
                    source = "local_router",
                    result = ToolResult(ok = false, message = "app not installed"),
                ),
            ),
        )

        val trace = StructuredTraceFormatter.build(record)

        val toolCall = trace.toolCalls.entries.single()
        assertEquals("open_app", toolCall.title)
        assertEquals(AgentRunStepStatus.FAILED, toolCall.status)
        assertEquals("Step 5", toolCall.stepLabel)

        val error = trace.errors.entries.single()
        assertEquals("open_app", error.title)
        assertEquals("Step 5", error.stepLabel)
        assertEquals(AgentRunStepStatus.FAILED, error.status)
    }

    @Test
    fun blockedActionBecomesApprovalNotError() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 2,
                    tool = "type_text",
                    args = emptyMap(),
                    source = "local_model",
                    status = AgentStepStatus.BLOCKED,
                ),
            ),
        )

        val trace = StructuredTraceFormatter.build(record)

        assertEquals(1, trace.approvals.count)
        assertEquals("type_text", trace.approvals.entries.single().title)
        assertEquals(AgentRunStepStatus.BLOCKED, trace.approvals.entries.single().status)
        assertEquals(0, trace.errors.count)
    }

    @Test
    fun approvalDeniedStopBecomesApproval() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.stop(
                    sequenceNumber = 1,
                    reason = AgentStepStopReason.APPROVAL_DENIED,
                    outputSummary = "You did not approve the action.",
                ),
            ),
        )

        val trace = StructuredTraceFormatter.build(record)

        assertEquals(1, trace.approvals.count)
        assertEquals("approval_denied", trace.approvals.entries.single().title)
        assertEquals(0, trace.errors.count)
    }

    @Test
    fun errorStopReasonBecomesErrorButMaxStepsDoesNot() {
        val errorRecord = sampleRecord(
            steps = listOf(
                AgentStepFactory.stop(
                    sequenceNumber = 1,
                    reason = AgentStepStopReason.EXECUTOR_ERROR,
                    outputSummary = "tool executor crashed",
                ),
            ),
        )
        val maxStepsRecord = sampleRecord(
            steps = listOf(
                AgentStepFactory.stop(
                    sequenceNumber = 1,
                    reason = AgentStepStopReason.MAX_STEPS,
                    outputSummary = "step limit reached",
                ),
            ),
        )

        assertEquals("executor_error", StructuredTraceFormatter.build(errorRecord).errors.entries.single().title)
        // MAX_STEPS is a bounded stop, not a failure — it must not surface as an error.
        assertEquals(0, StructuredTraceFormatter.build(maxStepsRecord).errors.count)
        assertEquals(0, StructuredTraceFormatter.build(maxStepsRecord).approvals.count)
    }

    @Test
    fun runLevelErrorBecomesRunEntryWhenResultMissing() {
        val record = AgentRunRecord(
            id = "run-failed",
            task = "Open settings",
            startedAtMillis = 1L,
            completedAtMillis = 2L,
            result = null,
            errorMessage = "Agent failed: timeout",
        )

        val trace = StructuredTraceFormatter.build(record)

        assertEquals(0, trace.stepCount)
        assertEquals(1, trace.errors.count)
        val error = trace.errors.entries.single()
        assertEquals("Run", error.stepLabel)
        assertEquals("run error", error.title)
        assertContains(error.detail, "timeout")
    }

    @Test
    fun redactsSensitiveToolArgsAndRunError() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "type_text",
                    args = mapOf("password" to "hunter2"),
                    source = "local_router",
                    result = ToolResult(ok = true, message = "typed into field"),
                ),
            ),
            errorMessage = "auth=ghp_supersecrettoken rejected",
        )

        val trace = StructuredTraceFormatter.build(record)
        val allText = (trace.toolCalls.entries + trace.errors.entries)
            .joinToString(separator = "\n") { it.detail }

        assertFalse("hunter2" in allText, "raw secret arg must be redacted")
        assertFalse("ghp_supersecrettoken" in allText, "raw secret in run error must be redacted")
        assertContains(allText, "[REDACTED]")
    }

    @Test
    fun toJsonMirrorsStructuredGroupsAndRedacts() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "type_text",
                    args = mapOf("password" to "hunter2"),
                    source = "local_router",
                ),
            ),
        )

        val json = StructuredTraceFormatter.toJson(record)

        assertEquals("run-1", json.getString("run_id"))
        assertEquals(1, json.getInt("step_count"))
        assertContains(json.getString("summary"), "1 tool call")
        val groups = (0 until json.getJSONArray("sections").length()).map {
            json.getJSONArray("sections").getJSONObject(it).getString("group")
        }
        assertEquals(listOf("tool_calls", "approvals", "errors"), groups)
        assertFalse("hunter2" in json.toString(), "structured export must respect redaction")
        assertContains(json.toString(), "[REDACTED]")
    }

    @Test
    fun emptyRunProducesEmptySectionsAndSummary() {
        val trace = StructuredTraceFormatter.build(sampleRecord())

        assertEquals(0, trace.stepCount)
        assertTrue(trace.sections.all { it.count == 0 })
        assertEquals("0 steps · 0 tool calls · 0 approvals · 0 errors", trace.summary)
    }

    @Test
    fun summaryUsesSingularForSingleCounts() {
        val record = sampleRecord(
            steps = listOf(
                AgentStepFactory.act(
                    sequenceNumber = 1,
                    tool = "tap",
                    args = emptyMap(),
                    source = "local_router",
                    result = ToolResult(ok = true, message = "ok"),
                ),
            ),
        )

        assertEquals("1 step · 1 tool call · 0 approvals · 0 errors", StructuredTraceFormatter.build(record).summary)
    }

    private fun sampleRecord(
        steps: List<AgentStep> = emptyList(),
        events: List<AgentEvent> = emptyList(),
        errorMessage: String? = null,
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
                finalAnswer = null,
                events = events,
                steps = steps,
                stopReason = stopReason,
                stopMessage = stopMessage,
            ),
            errorMessage = errorMessage,
        )
    }
}
