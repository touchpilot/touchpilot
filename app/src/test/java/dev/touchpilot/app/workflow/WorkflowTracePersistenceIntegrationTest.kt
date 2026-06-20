package dev.touchpilot.app.workflow

import android.content.Context
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Integration test that verifies the full workflow trace persistence lifecycle:
 * save to store → persist to disk → reload from disk on new store instance.
 */
class WorkflowTracePersistenceIntegrationTest {
    private fun mockContext(): Context {
        return object : Context() {
            private val testDir = File(System.getProperty("java.io.tmpdir"), "workflow-integration-test-${System.currentTimeMillis()}")

            override fun getFilesDir(): File {
                return testDir.apply { mkdirs() }
            }

            // Minimal Context implementation
            override fun getApplicationContext(): Context = this
            override fun getPackageName(): String = "dev.touchpilot.app.test"
            override fun getApplicationInfo() = throw UnsupportedOperationException()
            override fun getResources() = throw UnsupportedOperationException()
            override fun getPackageManager() = throw UnsupportedOperationException()
            override fun getContentResolver() = throw UnsupportedOperationException()
            override fun getMainLooper() = throw UnsupportedOperationException()
            override fun getAssets() = throw UnsupportedOperationException()
            override fun getClassLoader() = throw UnsupportedOperationException()
            override fun getTheme() = throw UnsupportedOperationException()
            override fun setTheme(resid: Int) = throw UnsupportedOperationException()
        }
    }

    private fun trace(runId: String, task: String = "test task", capturedAt: Long = 1000L) = WorkflowTrace(
        runId = runId,
        task = task,
        capturedAtMillis = capturedAt,
        steps = listOf(
            WorkflowTraceStep(
                index = 1,
                tool = "tap",
                args = mapOf("text" to "Button"),
                succeeded = true,
                verification = null,
            )
        ),
        screenSignals = emptyList(),
    )

    @Test
    fun tracesPersistedAcrossStoreInstances() {
        val context = mockContext()

        // First session: create store, record traces
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1", capturedAt = 1000L))
        store1.record(trace("run-2", capturedAt = 2000L))
        assertEquals(2, store1.size)

        // Second session: create new store, should load persisted traces
        val store2 = WorkflowTraceStore(context)
        assertEquals(2, store2.size)
        assertNotNull(store2.forRun("run-1"))
        assertNotNull(store2.forRun("run-2"))

        // Verify correct ordering (newest first)
        assertEquals(listOf("run-2", "run-1"), store2.all().map { it.runId })

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deletedTracesDoNotReappearInNewStore() {
        val context = mockContext()

        // First session: create and delete
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1"))
        store1.record(trace("run-2"))
        store1.delete("run-1")
        assertEquals(1, store1.size)

        // Second session: deleted trace should not be loaded
        val store2 = WorkflowTraceStore(context)
        assertEquals(1, store2.size)
        assertNull(store2.forRun("run-1"))
        assertNotNull(store2.forRun("run-2"))

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deleteAllClearsPersistedTraces() {
        val context = mockContext()

        // First session: create and delete all
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1"))
        store1.record(trace("run-2"))
        store1.record(trace("run-3"))
        val count = store1.deleteAll()
        assertEquals(3, count)
        assertEquals(0, store1.size)

        // Second session: should start empty
        val store2 = WorkflowTraceStore(context)
        assertEquals(0, store2.size)
        assertEquals(emptyList(), store2.all())

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun updatingTraceReplacesPersistedVersion() {
        val context = mockContext()

        // First session: create trace
        val store1 = WorkflowTraceStore(context)
        store1.record(trace("run-1", task = "original"))
        assertEquals("original", store1.forRun("run-1")?.task)

        // Second session: update trace
        val store2 = WorkflowTraceStore(context)
        store2.record(trace("run-1", task = "updated"))
        assertEquals("updated", store2.forRun("run-1")?.task)
        assertEquals(1, store2.size)

        // Third session: should see updated version
        val store3 = WorkflowTraceStore(context)
        assertEquals(1, store3.size)
        assertEquals("updated", store3.forRun("run-1")?.task)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun storeWithoutContextDoesNotPersist() {
        // Store without context should work in-memory only
        val store1 = WorkflowTraceStore(context = null)
        store1.record(trace("run-1"))
        store1.record(trace("run-2"))
        assertEquals(2, store1.size)

        // New store instance should start empty (no persistence)
        val store2 = WorkflowTraceStore(context = null)
        assertEquals(0, store2.size)
    }
}
