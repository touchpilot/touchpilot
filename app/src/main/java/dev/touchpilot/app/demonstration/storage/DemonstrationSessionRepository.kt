package dev.touchpilot.app.demonstration.storage

import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.serialization.DemonstrationJsonCodec

/**
 * Repository combining in-memory store and optional disk persistence.
 */
class DemonstrationSessionRepository(
    private val store: DemonstrationStore = DemonstrationStore(),
    private val persistence: DemonstrationPersistence? = null,
    private val index: DemonstrationIndex = DemonstrationIndex(),
) {
    fun save(session: DemonstrationSession, maxInMemory: Int = 50) {
        store.record(session, maxInMemory)
        index.index(session)
        persistence?.save(session)
    }

    fun find(sessionId: String): DemonstrationSession? {
        return store.find(sessionId) ?: persistence?.load(sessionId)?.also {
            store.record(it)
            index.index(it)
        }
    }

    fun findByRunId(runId: String): DemonstrationSession? {
        return store.findByRunId(runId)
    }

    fun search(query: DemonstrationQuery): List<DemonstrationIndex.IndexEntry> {
        return index.search(query)
    }

    fun allSessions(): List<DemonstrationSession> = store.all()

    fun loadFromDisk(): Int {
        val persistence = persistence ?: return 0
        val loaded = persistence.loadAll()
        loaded.forEach { session ->
            store.record(session)
            index.index(session)
        }
        return loaded.size
    }

    fun delete(sessionId: String): Boolean {
        store.remove(sessionId)
        index.remove(sessionId)
        return persistence?.delete(sessionId) ?: true
    }
}
