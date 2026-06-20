package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkflowTraceStoreTest {
    private fun trace(runId: String, steps: Int = 1, capturedAt: Long = System.currentTimeMillis()) = WorkflowTrace(
        runId = runId,
        task = "task",
        capturedAtMillis = capturedAt,
        steps = (1..steps).map { WorkflowTraceStep(it, "tap", emptyMap(), succeeded = true, verification = null) },
        screenSignals = emptyList(),
    )

    @Test
    fun recordsAndListsInOrder() {
        val store = WorkflowTraceStore(context = null)
        store.record(trace("run-1", capturedAt = 1000L))
        store.record(trace("run-2", capturedAt = 2000L))

        assertEquals(2, store.size)
        // Traces should be sorted by capture time (newest first)
        assertEquals(listOf("run-2", "run-1"), store.all().map { it.runId })
    }

    @Test
    fun recordingSameRunReplacesPriorTrace() {
        val store = WorkflowTraceStore(context = null)
        store.record(trace("run-1", steps = 1))
        store.record(trace("run-1", steps = 3))

        assertEquals(1, store.size)
        assertEquals(3, store.forRun("run-1")?.steps?.size)
    }

    @Test
    fun forRunReturnsNullWhenAbsent() {
        assertNull(WorkflowTraceStore(context = null).forRun("missing"))
    }

    @Test
    fun deleteRemovesTraceFromMemory() {
        val store = WorkflowTraceStore(context = null)
        store.record(trace("run-1"))
        store.record(trace("run-2"))

        val deleted = store.delete("run-1")

        assertEquals(true, deleted)
        assertEquals(1, store.size)
        assertNull(store.forRun("run-1"))
        assertEquals("run-2", store.forRun("run-2")?.runId)
    }

    @Test
    fun deleteReturnsFalseWhenTraceNotFound() {
        val store = WorkflowTraceStore(context = null)
        val deleted = store.delete("missing")
        assertEquals(false, deleted)
    }

    @Test
    fun deleteAllClearsAllTraces() {
        val store = WorkflowTraceStore(context = null)
        store.record(trace("run-1"))
        store.record(trace("run-2"))
        store.record(trace("run-3"))

        val count = store.deleteAll()

        assertEquals(3, count)
        assertEquals(0, store.size)
        assertEquals(emptyList(), store.all())
    }
}
