package dev.touchpilot.app.agent

data class AgentRunRecord(
    val id: String,
    val task: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val result: AgentRunResult?,
    val errorMessage: String? = null
) {
    val events: List<AgentEvent>
        get() = result?.events.orEmpty()
}

object AgentRunIds {
    private var sequence = 0L

    fun next(): String {
        sequence += 1
        return "run-$sequence"
    }
}
