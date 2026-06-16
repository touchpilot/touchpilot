package dev.touchpilot.app.workflow

/**
 * In-memory, session-scoped store of captured [WorkflowTrace]s (issue #289).
 * Recording the same run again replaces the prior trace, so a run maps to at
 * most one trace. Cross-session (SQLite) persistence is a deliberate follow-on.
 */
class WorkflowTraceStore {
    private val traces = mutableListOf<WorkflowTrace>()

    fun record(trace: WorkflowTrace) {
        traces.removeAll { it.runId == trace.runId }
        traces += trace
    }

    fun all(): List<WorkflowTrace> = traces.toList()

    fun forRun(runId: String): WorkflowTrace? = traces.lastOrNull { it.runId == runId }

    val size: Int get() = traces.size
}
