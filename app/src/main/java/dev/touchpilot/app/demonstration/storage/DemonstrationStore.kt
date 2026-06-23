package dev.touchpilot.app.demonstration.storage

import dev.touchpilot.app.demonstration.DemonstrationMetadata
import dev.touchpilot.app.demonstration.DemonstrationSession

/**
 * In-memory store for demonstration sessions captured during the app session.
 * Mirrors [dev.touchpilot.app.workflow.WorkflowTraceStore] for issue #302.
 */
class DemonstrationStore {
    private val sessions = linkedMapOf<String, DemonstrationSession>()

    fun record(session: DemonstrationSession, maxSessions: Int = 50) {
        sessions[session.sessionId] = session
        trim(maxSessions)
    }

    fun find(sessionId: String): DemonstrationSession? = sessions[sessionId]

    fun findByRunId(runId: String): DemonstrationSession? {
        return sessions.values.lastOrNull { it.runId == runId }
    }

    fun all(): List<DemonstrationSession> = sessions.values.toList()

    fun metadata(): List<DemonstrationMetadata> = sessions.values.map { it.metadata }

    fun remove(sessionId: String): DemonstrationSession? = sessions.remove(sessionId)

    fun clear() {
        sessions.clear()
    }

    val size: Int
        get() = sessions.size

    private fun trim(maxSessions: Int) {
        while (sessions.size > maxSessions) {
            val oldest = sessions.keys.first()
            sessions.remove(oldest)
        }
    }
}
