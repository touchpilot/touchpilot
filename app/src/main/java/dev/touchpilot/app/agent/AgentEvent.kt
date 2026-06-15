package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

sealed class AgentEvent(
    open val id: String = nextId(),
    open val timestampMillis: Long = System.currentTimeMillis()
) {
    abstract val type: AgentEventType

    fun toJson(redactSensitive: Boolean = true): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", type.wireName)
            .put("timestamp_millis", timestampMillis)
            .put("payload", JSONObject(payload(redactSensitive)))
    }

    protected abstract fun payload(redactSensitive: Boolean): Map<String, Any?>

    data class UserMessage(
        val text: String,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.USER_MESSAGE

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf("text" to text.redacted(redactSensitive))
        }
    }

    data class AssistantMessage(
        val text: String,
        val detail: String = "",
        val choices: List<String> = emptyList(),
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.ASSISTANT_MESSAGE

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf(
                "text" to text.redacted(redactSensitive),
                "detail" to detail.redacted(redactSensitive),
                "choices" to JSONArray().apply {
                    choices.forEach { put(it.redacted(redactSensitive)) }
                }
            )
        }
    }

    data class ToolRequested(
        val tool: String,
        val args: Map<String, String>,
        val source: ToolSource,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.TOOL_REQUESTED

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return toolPayload(tool, args, redactSensitive) + mapOf("source" to source.name.lowercase())
        }
    }

    data class ToolRunning(
        val tool: String,
        val args: Map<String, String>,
        val source: ToolSource,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.TOOL_RUNNING

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return toolPayload(tool, args, redactSensitive) + mapOf("source" to source.name.lowercase())
        }
    }

    data class ToolSucceeded(
        val tool: String,
        val message: String,
        val data: Map<String, String> = emptyMap(),
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.TOOL_SUCCEEDED

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf(
                "tool" to tool,
                "message" to message.redacted(redactSensitive),
                "data" to data.redacted(redactSensitive)
            )
        }
    }

    data class ToolFailed(
        val tool: String,
        val message: String,
        val data: Map<String, String> = emptyMap(),
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.TOOL_FAILED

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf(
                "tool" to tool,
                "message" to message.redacted(redactSensitive),
                "data" to data.redacted(redactSensitive)
            )
        }
    }

    data class ApprovalRequired(
        val tool: String,
        val args: Map<String, String>,
        val reason: String,
        val userMessage: String,
        val dataAffected: String,
        val ifApproved: String,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.APPROVAL_REQUIRED

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return toolPayload(tool, args, redactSensitive) + mapOf(
                "reason" to reason.redacted(redactSensitive),
                "user_message" to userMessage.redacted(redactSensitive),
                "data_affected" to dataAffected.redacted(redactSensitive),
                "if_approved" to ifApproved.redacted(redactSensitive)
            )
        }
    }

    data class PolicyBlocked(
        val reason: String,
        val userMessage: String,
        val tool: String? = null,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.POLICY_BLOCKED

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf(
                "tool" to tool,
                "reason" to reason.redacted(redactSensitive),
                "user_message" to userMessage.redacted(redactSensitive)
            )
        }
    }

    data class FinalAnswer(
        val text: String,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.FINAL_ANSWER

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf("text" to text.redacted(redactSensitive))
        }
    }

    /**
     * The agent stopped the loop to ask the user a clarifying question. This
     * is the structured complement to a freeform `AssistantMessage` and is
     * deliberately separate from [PolicyBlocked]: clarification means
     * "safe but uncertain", policy-block means "unsafe and refused".
     */
    data class Clarification(
        val reason: ClarificationReason,
        val question: String,
        val detail: String = "",
        val candidates: List<NextStepCandidate> = emptyList(),
        val tool: String? = null,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.CLARIFICATION

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf(
                "tool" to tool,
                "reason" to reason.wireName,
                "question" to question.redacted(redactSensitive),
                "detail" to detail.redacted(redactSensitive),
                "candidates" to candidates.map { candidate ->
                    mapOf(
                        "node_id" to candidate.nodeId,
                        "label" to candidate.displayLabel.redacted(redactSensitive),
                        "role" to candidate.role,
                        "confidence" to candidate.confidence,
                        "sensitive" to candidate.sensitive,
                    )
                }
            )
        }
    }

    data class RunCancelled(
        val reason: String,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.RUN_CANCELLED

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf("reason" to reason.redacted(redactSensitive))
        }
    }

    /**
     * Emitted when an agent run scopes tools to an active or matched skill.
     * Display-only metadata for chat/run UI; does not change skill selection.
     */
    data class SkillActive(
        val skillId: String,
        val title: String,
        val risk: SkillRisk,
        val allowedTools: Set<String>,
        val activationSource: SkillActivationSource,
        val reason: String,
        override val id: String = nextId(),
        override val timestampMillis: Long = System.currentTimeMillis()
    ) : AgentEvent(id, timestampMillis) {
        override val type = AgentEventType.SKILL_ACTIVE

        override fun payload(redactSensitive: Boolean): Map<String, Any?> {
            return mapOf(
                "skill_id" to skillId,
                "title" to title.redacted(redactSensitive),
                "risk" to risk.name.lowercase(),
                "allowed_tools" to allowedTools.sorted(),
                "activation_source" to activationSource.wireName,
                "reason" to reason.redacted(redactSensitive)
            )
        }
    }

    companion object {
        private var sequence = 0L

        fun nextId(): String {
            sequence += 1
            return "event-$sequence"
        }

        fun toolRequested(command: AgentCommand, source: ToolSource): ToolRequested? {
            val tool = command.tool ?: return null
            return ToolRequested(tool = tool, args = command.args, source = source)
        }

        fun toolRunning(command: AgentCommand, source: ToolSource): ToolRunning? {
            val tool = command.tool ?: return null
            return ToolRunning(tool = tool, args = command.args, source = source)
        }

        fun toolResult(tool: String, result: ToolResult): AgentEvent {
            return if (result.ok) {
                ToolSucceeded(tool = tool, message = result.message, data = result.data)
            } else {
                ToolFailed(tool = tool, message = result.message, data = result.data)
            }
        }

        fun approvalRequired(request: ToolApprovalRequest): ApprovalRequired {
            return ApprovalRequired(
                tool = request.tool.name,
                args = request.args,
                reason = request.policy.reason,
                userMessage = request.policy.userMessage,
                dataAffected = request.policy.dataAffected,
                ifApproved = request.policy.ifApproved
            )
        }

        fun policyBlocked(tool: String?, decision: PolicyDecision): PolicyBlocked? {
            return when (decision) {
                is PolicyDecision.Block -> PolicyBlocked(
                    tool = tool,
                    reason = decision.reason,
                    userMessage = decision.userMessage
                )
                is PolicyDecision.Deny -> PolicyBlocked(
                    tool = tool,
                    reason = decision.reason,
                    userMessage = decision.userMessage
                )
                else -> null
            }
        }

        fun finalAnswer(command: AgentCommand): FinalAnswer? {
            return command.finalAnswer?.let { FinalAnswer(it) }
        }

        private fun toolPayload(
            tool: String,
            args: Map<String, String>,
            redactSensitive: Boolean
        ): Map<String, Any?> {
            return mapOf(
                "tool" to tool,
                "args" to args.redacted(redactSensitive)
            )
        }

        private fun String.redacted(redactSensitive: Boolean): String {
            return if (redactSensitive) SensitiveTextRedactor.redact(this) else this
        }

        private fun Map<String, String>.redacted(redactSensitive: Boolean): Map<String, String> {
            return if (redactSensitive) SensitiveTextRedactor.redact(this) else this
        }
    }
}

enum class AgentEventType(val wireName: String) {
    USER_MESSAGE("user_message"),
    ASSISTANT_MESSAGE("assistant_message"),
    TOOL_REQUESTED("tool_requested"),
    TOOL_RUNNING("tool_running"),
    TOOL_SUCCEEDED("tool_succeeded"),
    TOOL_FAILED("tool_failed"),
    APPROVAL_REQUIRED("approval_required"),
    POLICY_BLOCKED("policy_blocked"),
    CLARIFICATION("clarification"),
    FINAL_ANSWER("final_answer"),
    RUN_CANCELLED("run_cancelled"),
    SKILL_ACTIVE("skill_active")
}
