package dev.touchpilot.app.demonstration.export

import android.content.Context
import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.serialization.DemonstrationJsonCodec
import dev.touchpilot.app.workflow.WorkflowTraceSerializer
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports demonstration sessions to app-accessible storage for sharing or backup.
 */
class DemonstrationExporter(
    private val context: Context,
    private val codec: DemonstrationJsonCodec = DemonstrationJsonCodec,
) {
    private val exportDir: File
        get() = File(context.getExternalFilesDir(null), "demonstrations").also { it.mkdirs() }

    fun export(session: DemonstrationSession): File {
        val fileName = buildFileName(session)
        val file = File(exportDir, fileName)
        file.writeText(codec.encode(session))
        writeManifest(session, file)
        return file
    }

    fun exportAsWorkflow(session: DemonstrationSession): File? {
        val workflow = DemonstrationWorkflowConverter.toWorkflowDefinition(session) ?: return null
        val fileName = "${slugify(session.metadata.task)}-workflow.json"
        val file = File(exportDir, fileName)
        file.writeText(workflow.toJson().toString(2))
        return file
    }

    fun listExports(): List<File> {
        return exportDir.listFiles { f -> f.extension == "json" && !f.name.endsWith("-manifest.json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun totalExportSizeBytes(): Long {
        return exportDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun buildFileName(session: DemonstrationSession): String {
        val slug = slugify(session.metadata.task)
        val timestamp = DATE_FORMAT.format(Date(session.metadata.startedAtMillis))
        return "demo-${slug}-$timestamp.json"
    }

    private fun writeManifest(session: DemonstrationSession, exportFile: File) {
        val manifest = JSONObject().apply {
            put("session_id", session.sessionId)
            put("run_id", session.runId)
            put("export_file", exportFile.name)
            put("step_count", session.steps.size)
            put("exported_at_millis", System.currentTimeMillis())
        }
        File(exportDir, "${exportFile.nameWithoutExtension}-manifest.json")
            .writeText(manifest.toString(2))
    }

    private fun slugify(value: String): String {
        return WorkflowTraceSerializer.slugify(value)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
