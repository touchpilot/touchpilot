package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentScreenRecord
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepFactory
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepVerification
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowTraceTest {

    private fun actStep(seq: Int, tool: String, args: Map<String, String>, ok: Boolean = true): AgentStep =
        AgentStepFactory.act(
            sequenceNumber = seq,
            tool = tool,
            args = args,
            source = "local_router",
            result = ToolResult(ok = ok, message = if (ok) "ok" else "failed"),
        )

    private fun record(
        steps: List<AgentStep>,
        stopReason: AgentStepStopReason? = AgentStepStopReason.COMPLETED,
        errorMessage: String? = null,
        task: String = "open settings and tap wifi",
        screenRecords: List<AgentScreenRecord> = emptyList(),
        result: AgentRunResult? = AgentRunResult(
            transcript = "t",
            finalAnswer = "done",
            steps = steps,
            stopReason = stopReason,
        ),
    ): AgentRunRecord = AgentRunRecord(
        id = "run-1",
        task = task,
        startedAtMillis = 1_000L,
        completedAtMillis = 2_000L,
        result = result,
        errorMessage = errorMessage,
        screenRecords = screenRecords,
    )

    @Test
    fun derivesTraceFromSuccessfulRunWithToolActions() {
        val steps = listOf(
            actStep(1, "open_app", mapOf("target" to "Settings")),
            actStep(2, "tap", mapOf("text" to "Wi-Fi")),
        )
        val screens = listOf(
            AgentScreenRecord(0, "initial", 1_000L, "{}", 3, false),
            AgentScreenRecord(1, "final", 2_000L, "{}", 5, false),
        )

        val trace = WorkflowTrace.from(record(steps, screenRecords = screens))!!

        assertEquals("run-1", trace.runId)
        assertEquals(2_000L, trace.capturedAtMillis)
        assertEquals(listOf("open_app", "tap"), trace.steps.map { it.tool })
        assertEquals("Settings", trace.steps[0].args["target"])
        assertTrue(trace.steps.all { it.succeeded })
        assertEquals(listOf("initial", "final"), trace.screenSignals.map { it.phase })
    }

    @Test
    fun capturesVerificationOutcome() {
        val verified = actStep(1, "tap", mapOf("text" to "Wi-Fi"))
            .copy(verification = AgentStepVerification(status = "passed", reason = "Wi-Fi visible"))

        val trace = WorkflowTrace.from(record(listOf(verified)))!!

        assertEquals("passed", trace.steps[0].verification?.status)
        assertEquals("Wi-Fi visible", trace.steps[0].verification?.reason)
    }

    @Test
    fun ignoresNonActStepsButKeepsActSteps() {
        val steps = listOf(
            AgentStepFactory.observe(sequenceNumber = 1, inputSummary = "obs", outputSummary = "screen"),
            actStep(2, "tap", mapOf("text" to "Wi-Fi")),
            AgentStepFactory.stop(
                sequenceNumber = 3,
                reason = AgentStepStopReason.COMPLETED,
                outputSummary = "done",
            ),
        )

        val trace = WorkflowTrace.from(record(steps))!!

        assertEquals(listOf("tap"), trace.steps.map { it.tool })
    }

    @Test
    fun skipsRunsWithoutToolActions() {
        val onlyStop = listOf(
            AgentStepFactory.stop(
                sequenceNumber = 1,
                reason = AgentStepStopReason.COMPLETED,
                outputSummary = "done",
            ),
        )
        assertNull(WorkflowTrace.from(record(onlyStop)))
    }

    @Test
    fun skipsNonCompletedRuns() {
        val steps = listOf(actStep(1, "tap", mapOf("text" to "Send")))
        assertNull(WorkflowTrace.from(record(steps, stopReason = AgentStepStopReason.POLICY_BLOCKED)))
    }

    @Test
    fun skipsErroredRuns() {
        val steps = listOf(actStep(1, "tap", mapOf("text" to "OK")))
        assertNull(WorkflowTrace.from(record(steps, errorMessage = "boom")))
    }

    @Test
    fun skipsRunsWithoutResult() {
        assertNull(WorkflowTrace.from(record(emptyList(), result = null)))
    }

    @Test
    fun redactsTaskAtCapture() {
        val raw = "my password is hunter2"
        val trace = WorkflowTrace.from(record(listOf(actStep(1, "tap", mapOf("text" to "OK"))), task = raw))!!
        // Redaction is applied at capture, not at display time.
        assertEquals(SensitiveTextRedactor.redact(raw), trace.task)
    }

    @Test
    fun serializesToJson() {
        val trace = WorkflowTrace.from(
            record(listOf(actStep(1, "open_app", mapOf("target" to "Settings")))),
        )!!
        val json = trace.toJson()

        assertEquals("run-1", json.getString("run_id"))
        assertEquals(1, json.getJSONArray("steps").length())
        assertEquals("open_app", json.getJSONArray("steps").getJSONObject(0).getString("tool"))
    }
}
