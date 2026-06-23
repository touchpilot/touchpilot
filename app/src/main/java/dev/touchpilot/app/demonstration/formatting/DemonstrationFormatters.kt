package dev.touchpilot.app.demonstration.formatting

import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.DemonstrationStatus
import dev.touchpilot.app.demonstration.DemonstrationStep
import dev.touchpilot.app.demonstration.analysis.DemonstrationContextExtractor

object DemonstrationSummaryFormatter {
    fun format(session: DemonstrationSession): String {
        val meta = session.metadata
        return buildString {
            appendLine("Demonstration: ${meta.task}")
            appendLine("Status: ${meta.status.name.lowercase()}")
            appendLine("Steps: ${session.steps.size}")
            meta.completedAtMillis?.let { completed ->
                val duration = completed - meta.startedAtMillis
                appendLine("Duration: ${formatDuration(duration)}")
            }
            meta.skillId?.let { appendLine("Skill: $it") }
            meta.providerMode?.let { appendLine("Provider: $it") }
            if (session.containsSensitiveContent) appendLine("Contains sensitive content")
            session.stopReason?.let { appendLine("Stop reason: $it") }
            session.errorMessage?.let { appendLine("Error: $it") }
        }.trim()
    }

    fun compact(session: DemonstrationSession): String {
        val status = session.metadata.status.name.lowercase()
        return "${session.steps.size} step(s) · $status · ${session.metadata.task.take(60)}"
    }

    fun completionMessage(session: DemonstrationSession): String {
        return when (session.metadata.status) {
            DemonstrationStatus.COMPLETED ->
                "Demonstration recorded: ${session.steps.size} step(s) with screen context."
            DemonstrationStatus.FAILED ->
                "Demonstration partially recorded: ${session.steps.size} step(s) before failure."
            DemonstrationStatus.CANCELLED ->
                "Demonstration cancelled after ${session.steps.size} step(s)."
            DemonstrationStatus.RECORDING ->
                "Recording in progress..."
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}

object DemonstrationTimelineFormatter {
    fun format(session: DemonstrationSession): String {
        return buildString {
            appendLine("Demonstration timeline")
            appendLine("Task: ${session.metadata.task}")
            appendLine()
            session.initialFrame?.let { frame ->
                appendLine("Initial screen (${frame.nodeCount} nodes)")
                appendLine(DemonstrationContextExtractor.headline(parseContext(frame.contextJson)))
                appendLine()
            }
            session.steps.forEach { step ->
                append(formatStep(step))
                appendLine()
            }
            session.finalFrame?.let { frame ->
                appendLine("Final screen (${frame.nodeCount} nodes)")
                appendLine(DemonstrationContextExtractor.headline(parseContext(frame.contextJson)))
            }
        }.trim()
    }

    fun formatStep(step: DemonstrationStep): String {
        return buildString {
            appendLine("Step ${step.index}: ${step.action.tool}")
            appendLine("  Args: ${step.action.args.entries.joinToString { "${it.key}=${it.value}" }}")
            appendLine("  Result: ${if (step.action.succeeded) "ok" else "failed"} — ${step.action.message}")
            step.screenDelta?.let { delta ->
                appendLine("  Screen: ${delta.summary}")
            }
            appendLine("  Duration: ${step.durationMillis}ms")
        }.trimEnd()
    }

    private fun parseContext(json: String): dev.touchpilot.app.screen.ScreenContext {
        return runCatching {
            dev.touchpilot.app.screen.ScreenContext.fromJson(org.json.JSONObject(json))
        }.getOrDefault(dev.touchpilot.app.screen.ScreenContext.Empty)
    }
}

object DemonstrationDetailFormatter {
    fun formatForRunDetail(session: DemonstrationSession): List<String> {
        return buildList {
            add("Demonstration session: ${session.sessionId}")
            add("Steps captured: ${session.steps.size}")
            add("Frames: ${session.allFrames.size}")
            session.steps.forEach { step ->
                add("  ${step.index}. ${step.action.tool} → ${step.action.message.take(80)}")
                step.screenDelta?.summary?.let { add("     Δ $it") }
            }
        }
    }

    fun settingsSummary(sessionCount: Int, enabled: Boolean): String {
        return if (enabled) {
            "Recording enabled · $sessionCount session(s) this launch"
        } else {
            "Recording disabled · $sessionCount session(s) saved"
        }
    }
}
