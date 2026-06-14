package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolSpec

enum class ToolSource {
    LOCAL_ROUTER,
    LOCAL_MODEL,
    SKILL_SELECTED,
    DIRECT_DEBUG,
    MCP
}

data class ToolPolicyRequest(
    val tool: ToolSpec,
    val args: Map<String, String>,
    val source: ToolSource,
    val activeScreen: String = "",
    val activeSkillId: String? = null,
    val activeSkillTitle: String? = null,
    val activeSkillRisk: SkillRisk? = null
)

sealed class PolicyDecision {
    abstract val reason: String
    abstract val userMessage: String

    data class Allow(
        override val reason: String,
        override val userMessage: String = reason
    ) : PolicyDecision()

    data class RequireApproval(
        override val reason: String,
        override val userMessage: String,
        val dataAffected: String,
        val ifApproved: String,
        /**
         * Advisory note shown when the action is requested under an elevated-risk
         * skill. Empty when no skill (or only a low-risk skill) is active. It only
         * adds caution to the prompt — it never changes the decision.
         */
        val skillContext: String = ""
    ) : PolicyDecision()

    data class Deny(
        override val reason: String,
        override val userMessage: String
    ) : PolicyDecision()

    data class Block(
        override val reason: String,
        override val userMessage: String
    ) : PolicyDecision()
}

interface ActionPolicy {
    fun evaluate(request: ToolPolicyRequest): PolicyDecision
}

/**
 * The runtime [ActionPolicy] used across tool execution, the agent runner, and
 * the reasoning core. It now adapts a [ToolPolicyRequest] into a [PolicyContext]
 * and delegates the decision to the central [PolicyEngine], so every caller
 * shares one policy path instead of re-deriving safety behavior. The previously
 * inlined checks (blocked workflows, sensitive text, message send, MCP, tool
 * risk, and skill-risk approval copy) now live in the engine unchanged.
 */
class DefaultActionPolicy(
    private val engine: PolicyEngine = PolicyEngine()
) : ActionPolicy {
    override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
        return engine.evaluate(
            PolicyContext(
                toolName = request.tool.name,
                toolRisk = request.tool.risk,
                args = request.args,
                source = request.source,
                activeScreen = request.activeScreen,
                activeSkillId = request.activeSkillId,
                activeSkillTitle = request.activeSkillTitle,
                activeSkillRisk = request.activeSkillRisk
            )
        ).decision
    }
}
