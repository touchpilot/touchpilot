package dev.touchpilot.app.runtime

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentRunState
import dev.touchpilot.app.agent.AgentStepStopReason

/** True while an agent or workflow replay thread is actively executing. */
internal fun isAgentRunInProgress(state: AgentRunState): Boolean {
    return state == AgentRunState.RUNNING || state == AgentRunState.WAITING_APPROVAL
}

internal fun resolveChatRunTerminalState(
    cancelled: Boolean,
    runFailed: Boolean,
    stopReason: AgentStepStopReason?,
): AgentRunState {
    return when {
        cancelled -> AgentRunState.CANCELLED
        runFailed -> AgentRunState.FAILED
        stopReason == AgentStepStopReason.CLARIFICATION_NEEDED -> AgentRunState.WAITING_CLARIFICATION
        else -> AgentRunState.COMPLETED
    }
}

internal fun resolveWorkflowReplayTerminalState(
    cancelled: Boolean,
    runFailed: Boolean,
    stopReason: AgentStepStopReason?,
): AgentRunState {
    return when {
        cancelled -> AgentRunState.CANCELLED
        stopReason == AgentStepStopReason.CLARIFICATION_NEEDED -> AgentRunState.WAITING_CLARIFICATION
        runFailed -> AgentRunState.FAILED
        stopReason == AgentStepStopReason.COMPLETED -> AgentRunState.COMPLETED
        else -> AgentRunState.FAILED
    }
}

internal fun chatRunFailed(
    runOutcomeFailed: Boolean,
    resultEvents: List<AgentEvent>?,
): Boolean {
    return runOutcomeFailed ||
        (resultEvents?.any { it is AgentEvent.ToolFailed || it is AgentEvent.PolicyBlocked } == true)
}
