package dev.touchpilot.app.agent

enum class AgentRunCompletionStatus(val label: String) {
    COMPLETED("Completed"),
    STOPPED("Stopped"),
    BLOCKED("Blocked"),
    NEEDS_CLARIFICATION("Needs clarification"),
    CANCELLED("Cancelled"),
    FAILED("Failed"),
}

data class AgentRunCompletionSummary(
    val status: AgentRunCompletionStatus,
    val stopReason: String,
    val stepCount: Int,
    val lastVerificationOutcome: String?,
    val nextAction: String?,
)
