package dev.touchpilot.app.runtime

import dev.touchpilot.app.agent.AgentRunState

/** True while an agent or workflow replay thread is actively executing. */
internal fun isAgentRunInProgress(state: AgentRunState): Boolean {
    return state == AgentRunState.RUNNING || state == AgentRunState.WAITING_APPROVAL
}
