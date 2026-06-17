package dev.touchpilot.app.agent

enum class AgentProviderMode {
    LOCAL_MODEL,
    LOCAL_ROUTER
}

enum class AgentRunState {
    IDLE,
    RUNNING,
    WAITING_APPROVAL,
    WAITING_CLARIFICATION,
    COMPLETED,
    FAILED,
    BLOCKED,
    CANCELLED
}

data class AgentCommand(
    val tool: String?,
    val args: Map<String, String>,
    val finalAnswer: String?
)

data class AgentRunResult(
    val transcript: String,
    val finalAnswer: String?,
    val events: List<AgentEvent> = emptyList(),
    val steps: List<AgentStep> = emptyList(),
    val stopReason: AgentStepStopReason? = null,
    val stopMessage: String = ""
)

data class AgentRunLimits(
    val maxSteps: Int = DefaultMaxSteps,
    val maxConsecutiveFailures: Int = DefaultMaxConsecutiveFailures
) {
    init {
        require(maxSteps > 0) { "maxSteps must be positive" }
        require(maxConsecutiveFailures > 0) { "maxConsecutiveFailures must be positive" }
    }

    companion object {
        const val DefaultMaxSteps = 4
        const val DefaultMaxConsecutiveFailures = 2
    }
}

val AgentStepStopReason.userMessage: String
    get() = when (this) {
        AgentStepStopReason.COMPLETED -> "The task completed successfully."
        AgentStepStopReason.MAX_STEPS -> "TouchPilot stopped after reaching the local step limit."
        AgentStepStopReason.REPEATED_TOOL_FAILURE -> "TouchPilot stopped after repeated tool failures."
        AgentStepStopReason.POLICY_BLOCKED -> "TouchPilot stopped because policy blocked the next action."
        AgentStepStopReason.APPROVAL_DENIED -> "TouchPilot stopped because approval was denied."
        AgentStepStopReason.USER_CANCELLED -> "TouchPilot stopped because the action was not approved."
        AgentStepStopReason.CLARIFICATION_NEEDED -> "TouchPilot needs clarification before continuing."
        AgentStepStopReason.PARSER_ERROR -> "TouchPilot could not parse the next local action."
        AgentStepStopReason.EXECUTOR_ERROR -> "TouchPilot stopped because the tool executor failed."
        AgentStepStopReason.NO_VALID_ACTION -> "TouchPilot could not find a valid next local action."
        AgentStepStopReason.VERIFICATION_FAILED ->
            "TouchPilot stopped because a workflow step did not reach the expected screen state."
    }
