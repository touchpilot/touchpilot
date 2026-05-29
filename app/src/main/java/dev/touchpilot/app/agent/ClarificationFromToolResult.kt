package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.ToolResult

/**
 * Parses tool failures that need user disambiguation before the agent can continue.
 */
object ClarificationFromToolResult {
    data class Prompt(
        val question: String,
        val choices: List<String>,
    )

    fun parse(result: ToolResult): Prompt? {
        if (!result.message.startsWith("Ambiguous", ignoreCase = true) &&
            result.data["clarification_needed"] != "true"
        ) {
            return null
        }

        val choices = readCandidateLabels(result.data)
        val question = when {
            result.message.contains("scroll", ignoreCase = true) ->
                "Which area should I scroll?"
            result.message.contains("input", ignoreCase = true) ->
                "Which field should I use?"
            else -> "Which item should I tap?"
        }
        return Prompt(
            question = question,
            choices = choices.map { SensitiveTextRedactor.redact(it) },
        )
    }

    fun dataFromCandidateLabels(labels: List<String>): Map<String, String> {
        return buildMap {
            put("clarification_needed", "true")
            put("candidate_count", labels.size.toString())
            labels.forEachIndexed { index, label ->
                put("candidate_label_$index", label)
            }
        }
    }

    fun readCandidateLabels(data: Map<String, String>): List<String> {
        val count = data["candidate_count"]?.toIntOrNull() ?: 0
        if (count <= 0) return emptyList()
        return (0 until count).mapNotNull { index -> data["candidate_label_$index"] }
    }

    fun candidatesFromResult(result: ToolResult): List<NextStepCandidate> {
        return readCandidateLabels(result.data).map { label ->
            NextStepCandidate(
                nodeId = null,
                displayLabel = SensitiveTextRedactor.redact(label),
                sensitive = SensitiveTextRedactor.containsSensitiveText(label),
            )
        }
    }
}
