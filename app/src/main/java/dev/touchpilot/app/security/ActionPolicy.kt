package dev.touchpilot.app.security

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
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
    val foregroundApp: ForegroundAppInfo? = null,
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
        val skillContext: String = "",
        /** Short prompt title shown in the approval UI. */
        val headline: String = "",
        /** Human-readable risk band summary for the approval card. */
        val riskSummary: String = "",
        /** Workflow or app-context label derived from policy metadata. */
        val workflowLabel: String = "",
        /** Extra caution shown for high-risk tools, skills, or workflows. */
        val cautionNote: String = ""
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

class DefaultActionPolicy(
    private val engine: PolicyEngine = PolicyEngine()
) : ActionPolicy {
    override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
        return engine.decide(request)
    }
}
