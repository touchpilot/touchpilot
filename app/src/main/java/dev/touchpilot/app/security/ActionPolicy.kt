package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.screen.ScreenContext
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
    val activeScreenContext: ScreenContext = ScreenContext.Empty,
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

class DefaultActionPolicy : ActionPolicy {
    private val engine = PolicyEngine()

    override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
        if (request.tool.risk == ToolRisk.LOW) {
            return PolicyDecision.Allow("low risk action")
        }
        val evaluation = engine.evaluate(request)
        val primaryRule = evaluation.rules
            .filter { it.decision == evaluation.decision }
            .sortedByDescending {
                when (it.subject) {
                    PolicySubject.WORKFLOW -> 3
                    PolicySubject.APP -> 2
                    PolicySubject.SOURCE -> 1
                    else -> 0
                }
            }
            .firstOrNull()
        val reason = primaryRule?.reason ?: evaluation.userMessage

        return when (evaluation.decision) {
            PolicyDecisionKind.ALLOW -> PolicyDecision.Allow(reason)
            PolicyDecisionKind.ASK -> approval(
                request = request,
                reason = reason,
                dataAffected = engine.dataAffected(request, primaryRule),
                ifApproved = engine.ifApproved(request, primaryRule)
            )
            PolicyDecisionKind.DENY -> PolicyDecision.Deny(
                reason = reason,
                userMessage = "TouchPilot denied this request because $reason."
            )
            PolicyDecisionKind.BLOCK -> PolicyDecision.Block(
                reason = reason,
                userMessage = "TouchPilot blocked this request because $reason."
            )
        }
    }

    private fun approval(
        request: ToolPolicyRequest,
        reason: String,
        dataAffected: String,
        ifApproved: String
    ): PolicyDecision.RequireApproval {
        return PolicyDecision.RequireApproval(
            reason = reason,
            userMessage = "Approval required for ${request.tool.name}: $reason.",
            dataAffected = dataAffected,
            ifApproved = ifApproved,
            skillContext = skillRiskContext(request)
        )
    }

    /**
     * Surfaces the active skill's risk in the approval prompt. Returns a note
     * only for a medium- or high-risk skill, so an absent or low-risk skill
     * leaves the prompt unchanged. This may only raise caution; it never lowers
     * a tool's risk or bypasses approval.
     */
    private fun skillRiskContext(request: ToolPolicyRequest): String {
        val risk = request.activeSkillRisk ?: return ""
        if (risk == SkillRisk.LOW) return ""
        val title = request.activeSkillTitle?.takeIf { it.isNotBlank() } ?: "the active skill"
        return "This action is requested under the ${risk.name.lowercase()}-risk skill " +
            "\"$title\". Review carefully before approving."
    }
}
