package dev.touchpilot.app.workflow

import java.io.File
import org.json.JSONObject

/**
 * Stores captured [WorkflowTrace]s on disk and keeps an in-memory index. Recording
 * the same run again replaces the prior trace, so a run maps to at most one
 * trace. All entries are stored as JSON files in the provided directory.
 */
class WorkflowTraceStore(
    private val traceDirectory: File,
) {
    private val traces = mutableListOf<WorkflowTrace>()

    init {
        ensureDirectory()
        loadTraces()
    }

    private fun ensureDirectory() {
        if (!traceDirectory.exists()) {
            traceDirectory.mkdirs()
        }
    }

    private fun loadTraces() {
        val files = traceDirectory.listFiles { _, name -> name.endsWith(".json") } ?: return
        traces.clear()
        files.forEach { file ->
            val trace = runCatching {
                WorkflowTrace.fromJson(JSONObject(file.readText()))
            }.getOrNull()

            if (trace != null) traces += trace
        }
        traces.sortBy { it.capturedAtMillis }
    }

    private fun traceFile(runId: String): File {
        val safeRunId = runId
            .trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { "run" }
        return File(traceDirectory, "${safeRunId}.json")
    }

    fun record(trace: WorkflowTrace) {
        traces.removeAll { it.runId == trace.runId }
        traces += trace
        traces.sortBy { it.capturedAtMillis }

        val file = traceFile(trace.runId)
        runCatching {
            file.writeText(trace.toJson().toString(2))
        }
    }

    fun all(): List<WorkflowTrace> = traces.toList()

    fun forRun(runId: String): WorkflowTrace? = traces.lastOrNull { it.runId == runId }

    fun delete(runId: String): Boolean {
        val deletedFromMemory = traces.removeAll { it.runId == runId }
        val deletedFile = traceFile(runId).let { file ->
            if (file.exists()) file.delete() else false
        }
        return deletedFromMemory || deletedFile
    }

    fun clear() {
        val files = traceDirectory.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        files.forEach { it.delete() }
        traces.clear()
    }

    val size: Int get() = traces.size
}
