package dev.touchpilot.app.workflow

import android.content.Context
import dev.touchpilot.app.security.PolicyWorkflowClass
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent storage for [WorkflowTrace] demonstration recordings (issue #309).
 *
 * Traces are stored as individual JSON files in the app's private storage
 * directory (`<filesDir>/workflow-traces/`). Each trace is saved with its
 * run ID as the filename for easy lookup and deletion.
 *
 * The repository provides:
 * - **save**: Persist a trace as JSON
 * - **load**: Read a specific trace by run ID
 * - **loadAll**: Read all stored traces, sorted by capture time (newest first)
 * - **delete**: Remove a trace file by run ID
 * - **deleteAll**: Clear all stored traces
 */
class WorkflowTraceRepository(private val context: Context) {
    private val traceDirectory: File
        get() = File(context.filesDir, TRACE_DIR).apply { mkdirs() }

    /**
     * Persists a [WorkflowTrace] to disk as a JSON file. If a trace with the
     * same run ID already exists, it will be replaced.
     */
    fun save(trace: WorkflowTrace) {
        val file = traceFile(trace.runId)
        file.writeText(trace.toJson().toString(JSON_INDENT))
    }

    /**
     * Loads a single trace by run ID, or null if not found or parsing fails.
     */
    fun load(runId: String): WorkflowTrace? {
        val file = traceFile(runId)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            fromJson(json)
        }.getOrNull()
    }

    /**
     * Loads all stored traces, sorted by capture time (newest first).
     * Corrupted or unparseable files are silently skipped.
     */
    fun loadAll(): List<WorkflowTrace> {
        val dir = traceDirectory
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(TRACE_EXTENSION) }
            ?.mapNotNull { file ->
                runCatching {
                    val json = JSONObject(file.readText())
                    fromJson(json)
                }.getOrNull()
            }
            ?.sortedByDescending { it.capturedAtMillis }
            ?: emptyList()
    }

    /**
     * Deletes a trace file by run ID. Returns true if the file existed and was
     * deleted, false otherwise.
     */
    fun delete(runId: String): Boolean {
        val file = traceFile(runId)
        return file.exists() && file.delete()
    }

    /**
     * Deletes all stored trace files. Returns the count of deleted files.
     */
    fun deleteAll(): Int {
        val dir = traceDirectory
        if (!dir.exists()) return 0

        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(TRACE_EXTENSION) }
            ?.count { it.delete() }
            ?: 0
    }

    /**
     * Returns the list of all stored trace run IDs, sorted by capture time
     * (newest first). This is a lighter-weight operation than [loadAll]
     * for UI listing purposes.
     */
    fun listRunIds(): List<String> {
        return loadAll().map { it.runId }
    }

    /**
     * Returns the number of stored trace files.
     */
    fun count(): Int {
        val dir = traceDirectory
        if (!dir.exists()) return 0
        return dir.listFiles()
            ?.count { it.isFile && it.name.endsWith(TRACE_EXTENSION) }
            ?: 0
    }

    private fun traceFile(runId: String): File {
        val sanitized = runId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(traceDirectory, "$sanitized$TRACE_EXTENSION")
    }

    companion object {
        private const val TRACE_DIR = "workflow-traces"
        private const val TRACE_EXTENSION = ".json"
        private const val JSON_INDENT = 2

        /**
         * Deserializes a [WorkflowTrace] from a JSON object. Inverse of
         * [WorkflowTrace.toJson].
         */
        fun fromJson(json: JSONObject): WorkflowTrace {
            val runId = json.getString("run_id")
            val task = json.getString("task")
            val capturedAtMillis = json.getLong("captured_at_millis")
            val stepsArray = json.getJSONArray("steps")
            val signalsArray = json.getJSONArray("screen_signals")
            val skillId = json.optString("skill_id").takeIf { it.isNotBlank() }
            val allowedToolsArray = json.optJSONArray("allowed_tools")

            val steps = (0 until stepsArray.length()).map { i ->
                stepFromJson(stepsArray.getJSONObject(i))
            }

            val screenSignals = (0 until signalsArray.length()).map { i ->
                screenSignalFromJson(signalsArray.getJSONObject(i))
            }

            val allowedTools = allowedToolsArray?.let { array ->
                (0 until array.length()).map { i -> array.getString(i) }
            } ?: emptyList()

            return WorkflowTrace(
                runId = runId,
                task = task,
                capturedAtMillis = capturedAtMillis,
                steps = steps,
                screenSignals = screenSignals,
                skillId = skillId,
                allowedTools = allowedTools,
            )
        }

        private fun stepFromJson(json: JSONObject): WorkflowTraceStep {
            val index = json.getInt("index")
            val tool = json.getString("tool")
            val source = json.optString("source", "")
            val argsObj = json.getJSONObject("args")
            val args = argsObj.keys().asSequence()
                .associateWith { key -> argsObj.getString(key) }
            val succeeded = json.getBoolean("succeeded")
            val verificationObj = json.optJSONObject("verification")
            val verification = verificationObj?.let {
                WorkflowTraceVerification(
                    status = it.getString("status"),
                    reason = it.getString("reason"),
                )
            }
            val requiresApproval = json.optBoolean("requires_approval")
                .takeIf { json.has("requires_approval") }
            val workflowClassStr = json.optString("workflow_class")
            val workflowClass = workflowClassStr.takeIf { it.isNotBlank() }?.let {
                runCatching {
                    PolicyWorkflowClass.valueOf(it.uppercase())
                }.getOrNull()
            }

            return WorkflowTraceStep(
                index = index,
                tool = tool,
                args = args,
                source = source,
                succeeded = succeeded,
                verification = verification,
                requiresApproval = requiresApproval,
                workflowClass = workflowClass,
            )
        }

        private fun screenSignalFromJson(json: JSONObject): WorkflowTraceScreenSignal {
            return WorkflowTraceScreenSignal(
                phase = json.getString("phase"),
                nodeCount = json.getInt("node_count"),
                containsSensitiveContent = json.getBoolean("contains_sensitive_content"),
            )
        }
    }
}
