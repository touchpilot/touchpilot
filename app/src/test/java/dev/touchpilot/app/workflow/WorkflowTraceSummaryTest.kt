package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowTraceSummaryTest {
    @Test
    fun summarizesTraceIntoStructuredCountsAndOverview() {
        val trace = WorkflowTrace(
            runId = "run-42",
            task = "open settings",
            capturedAtMillis = 1_234L,
            steps = listOf(
                WorkflowTraceStep(
                    index = 1,
                    tool = "tap",
                    args = mapOf("text" to "Settings"),
                    source = "local_router",
                    succeeded = true,
                    verification = WorkflowTraceVerification(status = "passed", reason = "Settings opened"),
                    requiresApproval = false,
                ),
                WorkflowTraceStep(
                    index = 2,
                    tool = "scroll",
                    args = mapOf("direction" to "forward"),
                    source = "local_router",
                    succeeded = false,
                    verification = null,
                    requiresApproval = true,
                ),
            ),
            screenSignals = listOf(
                WorkflowTraceScreenSignal(
                    phase = "initial",
                    nodeCount = 15,
                    containsSensitiveContent = false,
                ),
                WorkflowTraceScreenSignal(
                    phase = "final",
                    nodeCount = 9,
                    containsSensitiveContent = true,
                ),
            ),
        )

        val summary = WorkflowTraceSummarizer.summarize(trace)

        assertEquals("run-42", summary.runId)
        assertEquals("open settings", summary.task)
        assertEquals(2, summary.stepSummaries.size)
        assertEquals(listOf("tap", "scroll"), summary.stepSummaries.map { it.tool })
        assertEquals(2, summary.toolCounts.size)
        assertEquals("scroll", summary.toolCounts.first().tool)
        assertEquals(1, summary.toolCounts.first().count)
        assertTrue(summary.overview.contains("Captured 2 steps."))
        assertTrue(summary.overview.contains("Tools used: scroll (1), tap (1)."))
        assertTrue(summary.overview.contains("1 step required approval."))
        assertTrue(summary.overview.contains("Captured 2 screen snapshots."))
        assertTrue(summary.overview.contains("1 screen snapshot contained sensitive content."))

        val json = summary.toJson()
        assertEquals("run-42", json.getString("run_id"))
        assertEquals(2, json.getJSONArray("step_summaries").length())
        assertEquals(2, json.getJSONArray("screen_signals").length())
    }

    @Test
    fun summarizesEmptyTraceAsNoActionTrace() {
        val summary = WorkflowTraceSummarizer.summarize(
            WorkflowTrace(
                runId = "run-empty",
                task = "",
                capturedAtMillis = 0L,
                steps = emptyList(),
                screenSignals = emptyList(),
            )
        )

        assertEquals(0, summary.stepSummaries.size)
        assertEquals("No tool actions were captured.", summary.overview)
    }
}
