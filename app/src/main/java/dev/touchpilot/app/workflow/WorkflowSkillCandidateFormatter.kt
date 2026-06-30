package dev.touchpilot.app.workflow

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk

data class WorkflowSkillCandidate(
    val title: String,
    val description: String,
    val examples: List<String>,
    val allowedTools: List<String>,
    val risk: SkillRisk,
    val successCriteria: List<String>,
) {
    fun toMarkdown(): String {
        return buildString {
            appendLine("# ${title}")
            appendLine()
            appendLine("## Description")
            appendLine(description.ifBlank { "No description inferred." })
            appendLine()
            appendLine("## Risk")
            appendLine(risk.name.lowercase())
            appendLine()
            appendLine("## Allowed Tools")
            if (allowedTools.isEmpty()) {
                appendLine("None inferred.")
            } else {
                allowedTools.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## Examples")
            if (examples.isEmpty()) {
                appendLine("None inferred.")
            } else {
                examples.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## Success Criteria")
            if (successCriteria.isEmpty()) {
                appendLine("None inferred.")
            } else {
                successCriteria.forEach { appendLine("- $it") }
            }
        }.trim()
    }
}

object WorkflowSkillCandidateFormatter {
    fun fromTrace(trace: WorkflowTrace): WorkflowSkillCandidate? {
        if (trace.steps.isEmpty()) return null
        val allowedTools = trace.steps.map { it.tool }.distinct()
        val risk = inferredRisk(allowedTools)
        val examples = buildExamples(trace)
        val successCriteria = buildSuccessCriteria(trace)

        return WorkflowSkillCandidate(
            title = titleFromTrace(trace),
            description = descriptionFromTrace(trace),
            examples = examples,
            allowedTools = allowedTools,
            risk = risk,
            successCriteria = successCriteria,
        )
    }

    fun toMarkdown(trace: WorkflowTrace): String? {
        return fromTrace(trace)?.toMarkdown()
    }

    private fun titleFromTrace(trace: WorkflowTrace): String {
        return trace.task.trim().ifBlank { "Generated skill candidate" }
    }

    private fun descriptionFromTrace(trace: WorkflowTrace): String {
        val stepCount = trace.steps.size
        val summary = if (stepCount == 1) {
            "Replays one captured action from a local trace."
        } else {
            "Replays $stepCount captured actions from a local trace."
        }
        return buildString {
            append(summary)
            if (trace.allowedTools.isNotEmpty()) {
                append(" Allowed tools were inferred from the trace.")
            }
        }
    }

    private fun buildExamples(trace: WorkflowTrace): List<String> {
        val task = trace.task.trim()
        if (task.isBlank()) return emptyList()
        return listOf(task)
    }

    private fun buildSuccessCriteria(trace: WorkflowTrace): List<String> {
        val criteria = mutableListOf<String>()
        criteria += "Complete the captured task without deviations."
        trace.steps.lastOrNull()?.verification?.reason?.takeIf { it.isNotBlank() }?.let { reason ->
            criteria += "Match the observed final verification: $reason"
        }
        if (trace.steps.isNotEmpty()) {
            criteria += "Use the same core tool sequence as the trace."
        }
        return criteria
    }

    private fun inferredRisk(allowedTools: List<String>): SkillRisk {
        val hasHigh = allowedTools.any { AndroidToolCatalog.find(it)?.risk == ToolRisk.HIGH }
        val hasMedium = allowedTools.any { AndroidToolCatalog.find(it)?.risk == ToolRisk.MEDIUM }
        return when {
            hasHigh -> SkillRisk.HIGH
            hasMedium -> SkillRisk.MEDIUM
            else -> SkillRisk.LOW
        }
    }
}
