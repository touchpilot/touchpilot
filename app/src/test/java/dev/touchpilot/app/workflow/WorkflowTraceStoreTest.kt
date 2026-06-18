package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkflowTraceStoreTest {
    private fun trace(runId: String, steps: Int = 1) = WorkflowTrace(
        runId = runId,
        task = "task",
        capturedAtMillis = 0L,
        steps = (1..steps).map { WorkflowTraceStep(it, "tap", emptyMap(), succeeded = true, verification = null) },
        screenSignals = emptyList(),
    )

    @Test
    fun recordsAndListsInOrder() {
        val store = WorkflowTraceStore()
        store.record(trace("run-1"))
        store.record(trace("run-2"))

        assertEquals(2, store.size)
        assertEquals(listOf("run-1", "run-2"), store.all().map { it.runId })
    }

    @Test
    fun recordingSameRunReplacesPriorTrace() {
        val store = WorkflowTraceStore()
        store.record(trace("run-1", steps = 1))
        store.record(trace("run-1", steps = 3))

        assertEquals(1, store.size)
        assertEquals(3, store.forRun("run-1")?.steps?.size)
    }

    @Test
    fun forRunReturnsNullWhenAbsent() {
        assertNull(WorkflowTraceStore().forRun("missing"))
    }
}
