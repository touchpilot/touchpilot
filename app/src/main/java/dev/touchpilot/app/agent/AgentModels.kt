package dev.touchpilot.app.agent

enum class AgentProviderMode {
    LOCAL_MODEL,
    LOCAL_ROUTER
}

data class AgentCommand(
    val tool: String?,
    val args: Map<String, String>,
    val finalAnswer: String?
)

data class AgentRunResult(
    val transcript: String,
    val finalAnswer: String?,
    val events: List<AgentEvent> = emptyList()
)
