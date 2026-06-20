package dev.touchpilot.app.workflow

import android.content.Context

/**
 * Hybrid in-memory and persistent store of captured [WorkflowTrace]s (issue #289, #309).
 *
 * The store maintains an in-memory cache for fast access during the current
 * session and delegates to [WorkflowTraceRepository] for persistent storage.
 * Recording a trace saves it both in memory and to disk. Recording the same
 * run again replaces the prior trace, so a run maps to at most one trace.
 *
 * On initialization, the store loads all persisted traces from disk into the
 * in-memory cache for immediate availability.
 */
class WorkflowTraceStore(context: Context? = null) {
    private val repository: WorkflowTraceRepository? = context?.let { WorkflowTraceRepository(it) }
    private val traces = mutableListOf<WorkflowTrace>()

    init {
        // Load persisted traces from disk into memory on initialization
        repository?.loadAll()?.let { persisted ->
            traces.addAll(persisted)
        }
    }

    /**
     * Records a [WorkflowTrace] in memory and persists it to disk. If a trace
     * with the same run ID already exists, it will be replaced.
     */
    fun record(trace: WorkflowTrace) {
        traces.removeAll { it.runId == trace.runId }
        traces += trace
        repository?.save(trace)
    }

    /**
     * Returns all traces in memory, sorted by capture time (newest first).
     */
    fun all(): List<WorkflowTrace> = traces.sortedByDescending { it.capturedAtMillis }

    /**
     * Retrieves a trace by run ID, or null if not found.
     */
    fun forRun(runId: String): WorkflowTrace? = traces.lastOrNull { it.runId == runId }

    /**
     * Deletes a trace by run ID from both memory and disk. Returns true if
     * the trace existed and was deleted, false otherwise.
     */
    fun delete(runId: String): Boolean {
        val removed = traces.removeAll { it.runId == runId }
        val deletedFromDisk = repository?.delete(runId) ?: false
        return removed || deletedFromDisk
    }

    /**
     * Deletes all traces from both memory and disk. Returns the count of
     * deleted traces.
     */
    fun deleteAll(): Int {
        val count = traces.size
        traces.clear()
        repository?.deleteAll()
        return count
    }

    /**
     * Returns the number of traces currently in memory.
     */
    val size: Int get() = traces.size
}
