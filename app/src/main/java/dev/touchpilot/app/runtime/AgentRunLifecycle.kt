package dev.touchpilot.app.runtime

import dev.touchpilot.app.agent.AgentRunState

/** True while an agent or workflow replay thread is actively executing. */
internal fun isAgentRunInProgress(state: AgentRunState): Boolean {
    return state == AgentRunState.RUNNING || state == AgentRunState.WAITING_APPROVAL
}

/** True when a new chat or workflow run must not be started yet. */
internal fun isRunStartBlocked(state: AgentRunState, runInFlight: Boolean): Boolean {
    return runInFlight || isAgentRunInProgress(state)
}
