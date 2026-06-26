package dev.touchpilot.app.workflow

import java.io.File
import org.json.JSONObject

enum class WorkflowRunStatus(val label: String) {
    NEVER_RUN("never run"),
    SUCCEEDED("succeeded"),
    FAILED("failed")
}

data class WorkflowRunSummary(
    val status: WorkflowRunStatus,
    val message: String = "",
    val completedAtMillis: Long = 0L,
) {
    val displayLabel: String
        get() = when (status) {
            WorkflowRunStatus.NEVER_RUN -> status.label
            else -> status.label
        }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("status", status.name)
            put("message", message)
            put("completed_at_millis", completedAtMillis)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowRunSummary {
            val status = runCatching {
                WorkflowRunStatus.valueOf(json.optString("status", WorkflowRunStatus.NEVER_RUN.name))
            }.getOrElse { WorkflowRunStatus.NEVER_RUN }
            return WorkflowRunSummary(
                status = status,
                message = json.optString("message", ""),
                completedAtMillis = json.optLong("completed_at_millis", 0L),
            )
        }
    }
}

data class WorkflowLibraryEntry(
    val definition: WorkflowDefinition,
    val sourceFile: File,
    val lastRun: WorkflowRunSummary? = null,
) {
    val isEditable: Boolean get() = true

    val stepCount: Int get() = definition.steps.size
}

/**
 * File-backed local workflow library.
 *
 * Workflow definitions live as JSON files in a private app directory so the
 * UI can list, preview, rename, delete, and replay them without any network
 * dependency.
 */
class WorkflowLibrary(
    private val rootDir: File,
    seedDefinitions: List<WorkflowDefinition> = emptyList(),
) {
    init {
        rootDir.mkdirs()
        if (!seedMarkerFile().exists()) {
            seedDefinitions.forEach(::save)
            seedMarkerFile().writeText("seeded")
        }
    }

    fun all(): List<WorkflowLibraryEntry> {
        rootDir.mkdirs()
        return rootDir
            .listFiles { file -> file.isFile && file.extension == "json" && !file.name.endsWith(".state.json") }
            ?.sortedWith(compareBy<File> { readDefinition(it)?.title?.lowercase() ?: it.name.lowercase() }.thenBy { it.name })
            ?.mapNotNull { file ->
                val definition = readDefinition(file) ?: return@mapNotNull null
                WorkflowLibraryEntry(
                    definition = definition,
                    sourceFile = file,
                    lastRun = readState(file)
                )
            }
            .orEmpty()
    }

    fun find(workflowId: String): WorkflowLibraryEntry? {
        return all().firstOrNull { it.definition.id == workflowId }
    }

    fun save(definition: WorkflowDefinition): WorkflowLibraryEntry {
        val file = definitionFile(definition.id)
        file.writeText(definition.toJson().toString(2))
        return WorkflowLibraryEntry(definition = definition, sourceFile = file, lastRun = readState(file))
    }

    fun rename(workflowId: String, newTitle: String): WorkflowLibraryEntry? {
        val entry = find(workflowId) ?: return null
        val updated = entry.definition.copy(title = newTitle.trim())
        return save(updated)
    }

    fun delete(workflowId: String): Boolean {
        val file = definitionFile(workflowId)
        val deleted = file.delete()
        stateFile(workflowId).delete()
        return deleted
    }

    fun recordRun(workflowId: String, status: WorkflowRunStatus, message: String = "") {
        val summary = WorkflowRunSummary(
            status = status,
            message = message,
            completedAtMillis = System.currentTimeMillis(),
        )
        stateFile(workflowId).writeText(summary.toJson().toString(2))
    }

    private fun definitionFile(workflowId: String): File {
        return File(rootDir, "$workflowId.json")
    }

    private fun stateFile(workflowId: String): File {
        return File(rootDir, "$workflowId.state.json")
    }

    private fun readDefinition(file: File): WorkflowDefinition? {
        val parsed = WorkflowDefinitionParser.parse(file.readText())
        return when (parsed) {
            is WorkflowParseResult.Valid -> parsed.definition
            is WorkflowParseResult.Invalid -> null
        }
    }

    private fun readState(file: File): WorkflowRunSummary? {
        val state = stateFile(file.nameWithoutExtension)
        if (!state.exists()) return null
        return runCatching { WorkflowRunSummary.fromJson(JSONObject(state.readText())) }.getOrNull()
    }

    private fun definitionFiles(): List<File> {
        return rootDir
            .listFiles { file -> file.isFile && file.extension == "json" && !file.name.endsWith(".state.json") }
            ?.toList()
            .orEmpty()
    }

    private fun seedMarkerFile(): File {
        return File(rootDir, ".seeded")
    }
}
