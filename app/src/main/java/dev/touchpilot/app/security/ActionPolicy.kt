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
 * Adapts the central [PolicyEngine] to the legacy [PolicyDecision] surface the
 * runtime and approval UI already consume.
 *
 * All callers route through one engine so tool execution, local model output,
 * local router output, skills, and MCP cannot drift into different safety
 * behavior. This class makes no decisions of its own — it only translates the
 * engine's decision into the existing [PolicyDecision] shape and user-facing
 * copy (including the advisory active-skill risk note on approvals).
 */
class DefaultActionPolicy(
    private val engine: PolicyEngine = PolicyEngine()
) : ActionPolicy {
    override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
        val decision = engine.decide(request)
        return when (decision.kind) {
            PolicyDecisionKind.ALLOW -> PolicyDecision.Allow("low risk action")
            PolicyDecisionKind.ASK -> approval(request, decision.primary)
            PolicyDecisionKind.DENY -> deny(request, decision.primary)
            PolicyDecisionKind.BLOCK -> block(request, decision.primary)
        }
    }

    private fun approval(request: ToolPolicyRequest, primary: PolicyRule): PolicyDecision.RequireApproval {
        val toolName = request.tool.name
        val (reason, dataAffected, ifApproved) = when {
            primary.workflowClass == PolicyWorkflowClass.MESSAGE_SEND -> Triple(
                "sending a message requires explicit approval",
                "A message or outbound communication may be sent from the current app.",
                "TouchPilot will continue with the requested send action.",
            )
            primary.subject == PolicySubject.SOURCE -> Triple(
                "MCP tools are outside the built-in Android trust boundary",
                "The MCP server may receive tool arguments and affect an external system.",
                "TouchPilot will call the requested MCP tool once.",
            )
            else -> Triple(
                "${request.tool.risk.name.lowercase()} risk Android action",
                "The current Android app or screen may be changed.",
                "TouchPilot will run $toolName with the shown arguments.",
            )
        }
        return PolicyDecision.RequireApproval(
            reason = reason,
            userMessage = "Approval required for $toolName: $reason.",
            dataAffected = dataAffected,
            ifApproved = ifApproved,
            skillContext = skillRiskContext(request),
        )
    }

    private fun block(request: ToolPolicyRequest, primary: PolicyRule): PolicyDecision.Block {
        val toolName = request.tool.name
        return when (primary.workflowClass) {
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY -> PolicyDecision.Block(
                reason = "password or secret entry is blocked",
                userMessage = "TouchPilot will not enter passwords, recovery codes, API keys, or other secrets.",
            )
            PolicyWorkflowClass.GENERAL -> PolicyDecision.Block(
                reason = "tool is marked blocked",
                userMessage = "$toolName is blocked by policy.",
            )
            else -> {
                val phrase = blockedPhrase(primary.workflowClass)
                PolicyDecision.Block(
                    reason = phrase,
                    userMessage = "TouchPilot blocked this request because $phrase.",
                )
            }
        }
    }

    private fun deny(request: ToolPolicyRequest, primary: PolicyRule): PolicyDecision.Deny {
        return PolicyDecision.Deny(
            reason = primary.reason,
            userMessage = "TouchPilot denied ${request.tool.name}: ${primary.reason}.",
        )
    }

    private fun blockedPhrase(workflowClass: PolicyWorkflowClass): String = when (workflowClass) {
        PolicyWorkflowClass.PAYMENT -> "payments are blocked"
        PolicyWorkflowClass.PURCHASE -> "purchases are blocked"
        PolicyWorkflowClass.DELETION -> "destructive changes are blocked"
        PolicyWorkflowClass.ACCOUNT_CHANGE -> "account changes are blocked"
        PolicyWorkflowClass.ACCOUNT_RECOVERY -> "account recovery workflows are blocked"
        PolicyWorkflowClass.PERMISSION_CHANGE -> "permission changes are blocked"
        PolicyWorkflowClass.SECURITY_SETTINGS -> "security settings changes are blocked"
        PolicyWorkflowClass.UNKNOWN_SENSITIVE -> "this looks like a sensitive workflow"
        else -> "this workflow is blocked"
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
