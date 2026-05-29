package dev.touchpilot.app.agent

/**
 * Chat timeline display helpers for [AgentStep] records (issue #124).
 */
fun AgentStep.timelineDetail(): String = outputSummary.ifBlank { inputSummary }

fun AgentStepType.timelineLabel(): String =
    name.lowercase().replaceFirstChar { char -> char.titlecase() }

fun AgentStepStatus.timelineChipLabel(): String = when (this) {
    AgentStepStatus.PENDING -> "Pending"
    AgentStepStatus.RUNNING -> "Running"
    AgentStepStatus.OK -> "Success"
    AgentStepStatus.FAILED -> "Failed"
    AgentStepStatus.BLOCKED -> "Blocked"
    AgentStepStatus.CLARIFIED -> "Needs info"
    AgentStepStatus.STOPPED -> "Stopped"
}

fun AgentStepStatus.timelineChipAccent(): Boolean = when (this) {
    AgentStepStatus.OK, AgentStepStatus.RUNNING -> true
    else -> false
}
