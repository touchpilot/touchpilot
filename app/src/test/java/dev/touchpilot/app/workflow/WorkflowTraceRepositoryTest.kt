package dev.touchpilot.app.workflow

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowTraceRepositoryTest {
    private fun mockContext(): Context {
        return object : Context() {
            private val testDir = File(System.getProperty("java.io.tmpdir"), "workflow-trace-test-${System.currentTimeMillis()}")
            
            override fun getFilesDir(): File {
                return testDir.apply { mkdirs() }
            }

            // Minimal Context implementation - other methods throw by default
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
                args = mapOf("text" to "Submit"),
                succeeded = true,
                verification = WorkflowTraceVerification(status = "ok", reason = "button tapped"),
                requiresApproval = false,
            )
        ),
        screenSignals = listOf(
            WorkflowTraceScreenSignal(
                phase = "initial",
                nodeCount = 42,
                containsSensitiveContent = false,
            )
        ),
        skillId = "test-skill",
        allowedTools = listOf("tap", "type"),
    )

    @Test
    fun saveAndLoadRoundTrip() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)
        val original = trace("run-1")

        repository.save(original)
        val loaded = repository.load("run-1")

        assertNotNull(loaded)
        assertEquals(original.runId, loaded.runId)
        assertEquals(original.task, loaded.task)
        assertEquals(original.capturedAtMillis, loaded.capturedAtMillis)
        assertEquals(original.steps.size, loaded.steps.size)
        assertEquals(original.steps[0].tool, loaded.steps[0].tool)
        assertEquals(original.steps[0].args, loaded.steps[0].args)
        assertEquals(original.screenSignals.size, loaded.screenSignals.size)
        assertEquals(original.skillId, loaded.skillId)
        assertEquals(original.allowedTools, loaded.allowedTools)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun loadReturnsNullForNonexistentTrace() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        val loaded = repository.load("missing")

        assertNull(loaded)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun loadAllReturnsAllTracesSortedByTime() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        repository.save(trace("run-1", capturedAt = 1000L))
        repository.save(trace("run-2", capturedAt = 3000L))
        repository.save(trace("run-3", capturedAt = 2000L))

        val all = repository.loadAll()

        assertEquals(3, all.size)
        assertEquals(listOf("run-2", "run-3", "run-1"), all.map { it.runId })

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deleteRemovesTraceFile() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        repository.save(trace("run-1"))
        repository.save(trace("run-2"))

        val deleted = repository.delete("run-1")

        assertTrue(deleted)
        assertNull(repository.load("run-1"))
        assertNotNull(repository.load("run-2"))

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deleteReturnsFalseForNonexistentTrace() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        val deleted = repository.delete("missing")

        assertFalse(deleted)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun deleteAllRemovesAllTraces() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        repository.save(trace("run-1"))
        repository.save(trace("run-2"))
        repository.save(trace("run-3"))

        val count = repository.deleteAll()

        assertEquals(3, count)
        assertEquals(0, repository.loadAll().size)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun countReturnsNumberOfStoredTraces() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        assertEquals(0, repository.count())

        repository.save(trace("run-1"))
        assertEquals(1, repository.count())

        repository.save(trace("run-2"))
        assertEquals(2, repository.count())

        repository.delete("run-1")
        assertEquals(1, repository.count())

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun listRunIdsReturnsAllRunIds() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        repository.save(trace("run-1", capturedAt = 1000L))
        repository.save(trace("run-2", capturedAt = 3000L))
        repository.save(trace("run-3", capturedAt = 2000L))

        val runIds = repository.listRunIds()

        assertEquals(listOf("run-2", "run-3", "run-1"), runIds)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun savingDuplicateRunIdReplacesExistingTrace() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        repository.save(trace("run-1", task = "original task"))
        repository.save(trace("run-1", task = "updated task"))

        val loaded = repository.load("run-1")

        assertNotNull(loaded)
        assertEquals("updated task", loaded.task)
        assertEquals(1, repository.count())

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun sanitizesRunIdForFilename() {
        val context = mockContext()
        val repository = WorkflowTraceRepository(context)

        // Run IDs with special characters should be sanitized
        repository.save(trace("run:with/special\\chars"))

        val all = repository.loadAll()
        assertEquals(1, all.size)
        assertEquals("run:with/special\\chars", all[0].runId)

        // Cleanup
        context.filesDir.deleteRecursively()
    }

    @Test
    fun fromJsonDeserializesCorrectly() {
        val json = JSONObject("""
            {
                "run_id": "test-run",
                "task": "test task",
                "captured_at_millis": 12345,
                "steps": [
                    {
                        "index": 1,
                        "tool": "tap",
                        "source": "model",
                        "args": {"text": "Submit"},
                        "succeeded": true,
                        "verification": {
                            "status": "ok",
                            "reason": "button visible"
                        },
                        "requires_approval": false,
                        "workflow_class": "default"
                    }
                ],
                "screen_signals": [
                    {
                        "phase": "initial",
                        "node_count": 42,
                        "contains_sensitive_content": false
                    }
                ],
                "skill_id": "test-skill",
                "allowed_tools": ["tap", "type"]
            }
        """.trimIndent())

        val trace = WorkflowTraceRepository.fromJson(json)

        assertEquals("test-run", trace.runId)
        assertEquals("test task", trace.task)
        assertEquals(12345L, trace.capturedAtMillis)
        assertEquals(1, trace.steps.size)
        assertEquals("tap", trace.steps[0].tool)
        assertEquals(mapOf("text" to "Submit"), trace.steps[0].args)
        assertTrue(trace.steps[0].succeeded)
        assertEquals("ok", trace.steps[0].verification?.status)
        assertEquals(false, trace.steps[0].requiresApproval)
        assertEquals(1, trace.screenSignals.size)
        assertEquals("test-skill", trace.skillId)
        assertEquals(listOf("tap", "type"), trace.allowedTools)
    }

    @Test
    fun fromJsonHandlesOptionalFields() {
        val json = JSONObject("""
            {
                "run_id": "minimal-run",
                "task": "minimal task",
                "captured_at_millis": 9999,
                "steps": [
                    {
                        "index": 1,
                        "tool": "tap",
                        "args": {},
                        "succeeded": false,
                        "verification": null
                    }
                ],
                "screen_signals": [],
                "skill_id": null,
                "allowed_tools": []
            }
        """.trimIndent())

        val trace = WorkflowTraceRepository.fromJson(json)

        assertEquals("minimal-run", trace.runId)
        assertNull(trace.skillId)
        assertEquals(emptyList(), trace.allowedTools)
        assertNull(trace.steps[0].verification)
        assertNull(trace.steps[0].requiresApproval)
    }
}
