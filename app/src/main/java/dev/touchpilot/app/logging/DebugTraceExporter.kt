package dev.touchpilot.app.logging

import android.content.Context
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.memory.SkillParser
import dev.touchpilot.app.memory.SkillParseResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.touchpilot.app.workflow.WorkflowTrace
import dev.touchpilot.app.workflow.WorkflowTraceSerializer

class DebugTraceExporter(
    private val context: Context,
    private val accessibilityConnected: () -> Boolean,
    private val observeScreen: () -> String,
    private val renderToolLog: () -> String = { ToolExecutionLog.renderChronological() },
    private val timestamp: () -> String = {
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    }
) {
    sealed class SkillCandidateSaveResult {
        data class Saved(val skillId: String, val file: File) : SkillCandidateSaveResult()

        data class InvalidCandidate(val errors: List<String>) : SkillCandidateSaveResult()

        data class Failure(val message: String) : SkillCandidateSaveResult()
    }

    private val knownTools: Set<String> = AndroidToolCatalog.initialTools.map { it.name }.toSet()

    fun buildSkillCandidate(record: AgentRunRecord): String? {
        val trace = WorkflowTrace.from(record) ?: return null
        return WorkflowTraceSerializer.toSkillMarkdown(trace)
    }

    fun saveSkillCandidate(markdown: String): SkillCandidateSaveResult {
        val candidate = markdown.trim()
        if (candidate.isBlank()) {
            return SkillCandidateSaveResult.InvalidCandidate(listOf("candidate content is empty"))
        }

        val id = parseFrontMatterId(candidate)
            ?: return SkillCandidateSaveResult.InvalidCandidate(listOf("candidate has no front matter id"))
        return when (val result = SkillParser.parse(id, candidate, knownTools)) {
            is SkillParseResult.Invalid -> {
                SkillCandidateSaveResult.InvalidCandidate(result.errors)
            }
            is SkillParseResult.Valid -> {
                runCatching {
                    val outputDirectory = File(customSkillsDirectory(), result.skill.id).apply { mkdirs() }
                    val file = File(outputDirectory, "SKILL.md")
                    file.writeText(candidate)
                    SkillCandidateSaveResult.Saved(result.skill.id, file)
                }.getOrDefault(
                    SkillCandidateSaveResult.Failure("Unable to save candidate due to an unexpected error.")
                )
            }
        }
    }

    fun exportRunTrace(record: AgentRunRecord): File {
        val timestamp = timestamp()
        val file = File(traceDirectory(), "touchpilot-run-${record.id}-$timestamp.txt")
        file.writeText(AgentRunDetailFormatter.exportRedactedTrace(record))
        return file
    }

    fun exportSkillCandidate(record: AgentRunRecord): File? {
        val markdown = buildSkillCandidate(record) ?: return null
        val timestamp = timestamp()
        val runId = WorkflowTrace.from(record)?.runId ?: record.id
        val file = File(traceDirectory(), "touchpilot-skill-candidate-${runId}-$timestamp.md")
        file.writeText(markdown)
        return file
    }

    fun exportDebugTrace(): File {
        val timestamp = timestamp()
        val file = File(traceDirectory(), "touchpilot-trace-$timestamp.txt")
        file.writeText(
            buildString {
                appendLine("TouchPilot debug trace")
                appendLine("timestamp=$timestamp")
                appendLine()
                appendLine("Accessibility connected=${accessibilityConnected()}")
                appendLine()
                appendLine("Tool executions")
                appendLine(renderToolLog())
                appendLine()
                appendLine("Current screen")
                appendLine(SensitiveTextRedactor.redact(observeScreen()))
            }
        )
        return file
    }

    private fun traceDirectory(): File {
        return File(context.getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
    }

    private fun customSkillsDirectory(): File {
        return File(context.filesDir, "custom-skills").apply {
            mkdirs()
        }
    }

    private fun parseFrontMatterId(markdown: String): String? {
        val normalized = markdown.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines()
        val openIndex = lines.indexOfFirst { it.trim() == "---" }
        if (openIndex != 0) return null

        val closeIndex = (openIndex + 1 until lines.size)
            .firstOrNull { lines[it].trim() == "---" }
            ?: return null

        for (lineIndex in openIndex + 1 until closeIndex) {
            val line = lines[lineIndex].trim()
            if (!line.startsWith("id:")) continue
            val candidate = line.removePrefix("id:")
                .trim()
                .removePrefix("'")
                .removeSuffix("'")
                .removePrefix("\"")
                .removeSuffix("\"")
                .trim()
            if (candidate.isNotBlank()) return candidate
        }
        return null
    }
}
