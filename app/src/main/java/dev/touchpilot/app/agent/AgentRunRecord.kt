package dev.touchpilot.app.agent

import dev.touchpilot.app.screen.ScreenContext
import org.json.JSONObject

data class AgentRunRecord(
    val id: String,
    val task: String,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
    val result: AgentRunResult?,
    val errorMessage: String? = null,
    val screenRecords: List<AgentScreenRecord> = emptyList()
) {
    val events: List<AgentEvent>
        get() = result?.events.orEmpty()
}

data class AgentScreenRecord(
    val sequenceNumber: Int,
    val phase: String,
    val timestampMillis: Long,
    val contextJson: String,
    val nodeCount: Int,
    val containsSensitiveContent: Boolean
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sequence_number", sequenceNumber)
            put("phase", phase)
            put("timestamp_millis", timestampMillis)
            put("node_count", nodeCount)
            put("contains_sensitive_content", containsSensitiveContent)
            put("context", JSONObject(contextJson))
        }
    }

    companion object {
        fun capture(
            sequenceNumber: Int,
            phase: String,
            timestampMillis: Long,
            context: ScreenContext
        ): AgentScreenRecord {
            return AgentScreenRecord(
                sequenceNumber = sequenceNumber,
                phase = phase,
                timestampMillis = timestampMillis,
                contextJson = context.toRedactedJson(),
                nodeCount = context.nodes.size,
                containsSensitiveContent = context.containsSensitiveContent
            )
        }
    }
}

object AgentRunIds {
    private var sequence = 0L

    fun next(): String {
        sequence += 1
        return "run-$sequence"
    }
}
