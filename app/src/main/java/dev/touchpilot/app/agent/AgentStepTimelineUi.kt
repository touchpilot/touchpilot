package dev.touchpilot.app.agent

/**
 * Chat timeline display helpers for [AgentStep] records (issue #124).
 */
fun AgentStep.timelineDetail(): String = outputSummary.ifBlank { inputSummary }

fun AgentStepType.timelineLabel(): String = when (this) {
    AgentStepType.OBSERVE -> "Observe screen"
    AgentStepType.DECIDE -> "Decide action"
    AgentStepType.ACT -> "Take action"
    AgentStepType.VERIFY -> "Verify result"
    AgentStepType.CLARIFY -> "Ask clarification"
    AgentStepType.STOP -> "Stop"
}

fun AgentStepStatus.timelineChipLabel(): String = when (this) {
    AgentStepStatus.PENDING -> "Pending"
    AgentStepStatus.RUNNING -> "Running"
    AgentStepStatus.OK -> "Done"
    AgentStepStatus.FAILED -> "Failed"
    AgentStepStatus.BLOCKED -> "Blocked"
    AgentStepStatus.CLARIFIED -> "Needs info"
    AgentStepStatus.STOPPED -> "Stopped"
}

fun AgentStepStatus.timelineChipAccent(): Boolean = when (this) {
    AgentStepStatus.OK, AgentStepStatus.RUNNING -> true
    else -> false
}
