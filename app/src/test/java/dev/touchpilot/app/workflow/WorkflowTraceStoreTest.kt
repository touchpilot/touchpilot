package dev.touchpilot.app.workflow

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowTraceStoreTest {
    private fun trace(runId: String, steps: Int = 1, capturedAtMillis: Long = 0L) = WorkflowTrace(
        runId = runId,
        task = "task",
        capturedAtMillis = capturedAtMillis,
        steps = (1..steps).map { WorkflowTraceStep(it, "tap", emptyMap(), succeeded = true, verification = null) },
        screenSignals = emptyList(),
    )

    private fun withStore(block: (WorkflowTraceStore) -> Unit) {
        val dir = Files.createTempDirectory("workflow-trace-store").toFile()
        try {
            block(WorkflowTraceStore(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recordsAndListsInOrder() {
        withStore { store ->
            store.record(trace("run-1", capturedAtMillis = 1L))
            store.record(trace("run-2", capturedAtMillis = 2L))

            assertEquals(2, store.size)
            assertEquals(listOf("run-1", "run-2"), store.all().map { it.runId })
        }
    }

    @Test
    fun persistsTracesInDirectoryAndReloads() {
        val runId = "run-1"
        val dir = Files.createTempDirectory("workflow-trace-store").toFile()

        try {
            val firstStore = WorkflowTraceStore(dir)
            firstStore.record(trace(runId, capturedAtMillis = 1L))
            assertEquals(1, firstStore.size)

            val reloaded = WorkflowTraceStore(dir)
            assertEquals(1, reloaded.size)
            assertEquals(runId, reloaded.forRun(runId)?.runId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun deletingRemovesTraceFromStoreAndDisk() {
        val dir = Files.createTempDirectory("workflow-trace-store").toFile()
        try {
            val store = WorkflowTraceStore(dir)
            store.record(trace("run-1"))
            val traceFile = File(dir, "run-1.json")
            assertTrue(traceFile.exists())

            val removed = store.delete("run-1")
            assertTrue(removed)
            assertEquals(0, store.size)
            assertFalse(traceFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun recordingSameRunReplacesPriorTrace() {
        withStore { store ->
            store.record(trace("run-1", steps = 1, capturedAtMillis = 1L))
            store.record(trace("run-1", steps = 3, capturedAtMillis = 2L))

            assertEquals(1, store.size)
            assertEquals(3, store.forRun("run-1")?.steps?.size)
        }
    }

    @Test
    fun forRunReturnsNullWhenAbsent() {
        withStore { store ->
            assertNull(store.forRun("missing"))
        }
    }

}
