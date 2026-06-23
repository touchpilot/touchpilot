package dev.touchpilot.app.demonstration.storage

import android.content.Context
import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.serialization.DemonstrationJsonCodec
import java.io.File

/**
 * Persists demonstration sessions to app-private storage for later replay or export.
 */
class DemonstrationPersistence(
    private val context: Context,
    private val codec: DemonstrationJsonCodec = DemonstrationJsonCodec,
) {
    private val baseDir: File
        get() = File(context.filesDir, "demonstrations").also { it.mkdirs() }

    fun save(session: DemonstrationSession): File {
        val file = fileFor(session.sessionId)
        file.writeText(codec.encode(session))
        updateIndex(session)
        return file
    }

    fun load(sessionId: String): DemonstrationSession? {
        val file = fileFor(sessionId)
        if (!file.exists()) return null
        return runCatching { codec.decode(file.readText()) }.getOrNull()
    }

    fun delete(sessionId: String): Boolean {
        val file = fileFor(sessionId)
        val deleted = file.delete()
        if (deleted) removeFromIndex(sessionId)
        return deleted
    }

    fun listSessionIds(): List<String> {
        return baseDir.listFiles { f -> f.extension == "json" && f.name != INDEX_FILE }
            ?.map { it.nameWithoutExtension.removePrefix("demo-") }
            ?.sorted()
            ?: emptyList()
    }

    fun loadAll(): List<DemonstrationSession> {
        return baseDir.listFiles { f -> f.extension == "json" && f.name != INDEX_FILE }
            ?.mapNotNull { file ->
                runCatching { codec.decode(file.readText()) }.getOrNull()
            }
            ?: emptyList()
    }

    fun totalSizeBytes(): Long {
        return baseDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun fileFor(sessionId: String): File {
        val safeName = sessionId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(baseDir, "$safeName.json")
    }

    private fun updateIndex(session: DemonstrationSession) {
        val indexFile = File(baseDir, INDEX_FILE)
        val existing = if (indexFile.exists()) indexFile.readLines().toMutableSet() else mutableSetOf()
        existing += session.sessionId
        indexFile.writeText(existing.sorted().joinToString("\n"))
    }

    private fun removeFromIndex(sessionId: String) {
        val indexFile = File(baseDir, INDEX_FILE)
        if (!indexFile.exists()) return
        val updated = indexFile.readLines().filter { it != sessionId }
        indexFile.writeText(updated.joinToString("\n"))
    }

    companion object {
        private const val INDEX_FILE = "_index.txt"
    }
}
