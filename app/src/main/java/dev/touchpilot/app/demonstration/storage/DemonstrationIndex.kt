package dev.touchpilot.app.demonstration.storage

import dev.touchpilot.app.demonstration.DemonstrationMetadata
import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.DemonstrationStatus

/**
 * Searchable index over demonstration sessions for listing and filtering.
 */
class DemonstrationIndex {
    private val entries = linkedMapOf<String, IndexEntry>()

    fun index(session: DemonstrationSession) {
        entries[session.sessionId] = IndexEntry.from(session)
    }

    fun remove(sessionId: String) {
        entries.remove(sessionId)
    }

    fun rebuild(sessions: List<DemonstrationSession>) {
        entries.clear()
        sessions.forEach { index(it) }
    }

    fun search(query: DemonstrationQuery): List<IndexEntry> {
        return entries.values.filter { query.matches(it) }.sortedByDescending { it.startedAtMillis }
    }

    fun all(): List<IndexEntry> = entries.values.sortedByDescending { it.startedAtMillis }

    val size: Int get() = entries.size

    data class IndexEntry(
        val sessionId: String,
        val runId: String,
        val task: String,
        val startedAtMillis: Long,
        val completedAtMillis: Long?,
        val status: DemonstrationStatus,
        val stepCount: Int,
        val skillId: String?,
        val containsSensitiveContent: Boolean,
        val toolsUsed: Set<String>,
    ) {
        companion object {
            fun from(session: DemonstrationSession): IndexEntry {
                return IndexEntry(
                    sessionId = session.sessionId,
                    runId = session.runId,
                    task = session.metadata.task,
                    startedAtMillis = session.metadata.startedAtMillis,
                    completedAtMillis = session.metadata.completedAtMillis,
                    status = session.metadata.status,
                    stepCount = session.steps.size,
                    skillId = session.metadata.skillId,
                    containsSensitiveContent = session.containsSensitiveContent,
                    toolsUsed = session.steps.map { it.action.tool }.toSet(),
                )
            }
        }
    }
}

data class DemonstrationQuery(
    val text: String? = null,
    val status: DemonstrationStatus? = null,
    val skillId: String? = null,
    val tool: String? = null,
    val minSteps: Int? = null,
    val maxSteps: Int? = null,
    val sinceMillis: Long? = null,
    val untilMillis: Long? = null,
) {
    fun matches(entry: DemonstrationIndex.IndexEntry): Boolean {
        if (status != null && entry.status != status) return false
        if (skillId != null && entry.skillId != skillId) return false
        if (tool != null && tool !in entry.toolsUsed) return false
        if (minSteps != null && entry.stepCount < minSteps) return false
        if (maxSteps != null && entry.stepCount > maxSteps) return false
        if (sinceMillis != null && entry.startedAtMillis < sinceMillis) return false
        if (untilMillis != null && entry.startedAtMillis > untilMillis) return false
        if (text != null && text.isNotBlank()) {
            val haystack = "${entry.task} ${entry.toolsUsed.joinToString(" ")}".lowercase()
            if (!haystack.contains(text.lowercase())) return false
        }
        return true
    }
}
