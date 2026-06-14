package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk

/**
 * Everything the central [PolicyEngine] reads to decide a single tool call. It is
 * the one input shape every caller — tool execution, the local model, the local
 * router, skills, and MCP — converts into, so no path can drift into different
 * safety behavior.
 *
 * [workflowClass] is the optional, pre-computed classification from the
 * WorkflowRiskClassifier (issue #249). When supplied it can only *raise*
 * caution: the engine also derives a class from the tool name and arguments and
 * always takes the stricter of the two, so passing a class never weakens a
 * decision the string-based path would have made.
 */
data class PolicyContext(
    val toolName: String,
    val toolRisk: ToolRisk,
    val args: Map<String, String> = emptyMap(),
    val source: ToolSource = ToolSource.LOCAL_ROUTER,
    val activeScreen: String = "",
    val activeApp: String = "",
    val activeSkillId: String? = null,
    val activeSkillTitle: String? = null,
    val activeSkillRisk: SkillRisk? = null,
    val workflowClass: PolicyWorkflowClass? = null
)

/**
 * The engine's full result: the runtime [PolicyDecision] the approval/execution
 * layers already understand, plus the Policy v2 [PolicyEvaluation] rule trace
 * (issue #254) and the resolved [PolicyWorkflowClass] for logging and audit.
 */
data class PolicyOutcome(
    val decision: PolicyDecision,
    val evaluation: PolicyEvaluation,
    val workflowClass: PolicyWorkflowClass
)

/**
 * The single, deterministic policy decision path.
 *
 * The engine gathers candidate [PolicyRule]s from tool risk, the workflow class,
 * the call source, and sensitive-text/blocked-workflow detection, then lets the
 * Policy v2 model pick the **strictest** outcome (`ALLOW < ASK < DENY < BLOCK`).
 * The matching candidate supplies the user-facing copy so existing approval and
 * block messages are preserved.
 *
 * Safety properties:
 * - It is pure and deterministic — no model judgment decides execution.
 * - It fails closed: a non-low tool with no clear allow path requires approval
 *   rather than running, and an unrecognized-but-sensitive workflow blocks.
 * - Skill risk is advisory only: it adds caution to approval copy and can never
 *   lower a decision or escalate an auto-allowed low-risk tool.
 *
 * This replaces the previously inlined checks in [DefaultActionPolicy], which now
 * delegates here, so every caller shares one policy.
 */
class PolicyEngine {

    fun evaluate(context: PolicyContext): PolicyOutcome {
        // Low-risk tools (observe/wait/foreground checks) are read-only and stay
        // auto-allowed regardless of screen, skill, or workflow context. This
        // preserves the long-standing guarantee that observation never needs
        // approval and is what keeps an elevated skill from escalating them.
        if (context.toolRisk == ToolRisk.LOW) {
            val allowRule = PolicyV2Defaults.ruleForToolRisk(ToolRisk.LOW)
            return PolicyOutcome(
                decision = PolicyDecision.Allow("low risk action"),
                evaluation = PolicyEvaluation.fromRules(listOf(allowRule)),
                workflowClass = PolicyWorkflowClass.GENERAL
            )
        }

        val derived = deriveWorkflow(context)
        val candidates = buildCandidates(context, derived)
        val evaluation = PolicyEvaluation.fromRules(candidates.map { it.rule })

        val winner = candidates
            .filter { it.rule.decision == evaluation.decision }
            .minByOrNull { it.category.priority }
            // Fail closed: a non-low tool that produced no candidate (should not
            // happen) requires approval rather than silently running.
            ?: Candidate(failClosedRule(context), CandidateCategory.TOOL_APPROVAL)

        return PolicyOutcome(
            decision = toDecision(winner, context),
            evaluation = evaluation,
            workflowClass = winner.resolvedWorkflow ?: derived.workflowClass
        )
    }

    private fun buildCandidates(context: PolicyContext, derived: DerivedWorkflow): List<Candidate> {
        val candidates = mutableListOf<Candidate>()

        // Tool risk.
        when (context.toolRisk) {
            ToolRisk.BLOCKED -> candidates += Candidate(
                rule = PolicyV2Defaults.ruleForToolRisk(ToolRisk.BLOCKED),
                category = CandidateCategory.BLOCKED_TOOL
            )
            ToolRisk.MEDIUM, ToolRisk.HIGH -> candidates += Candidate(
                rule = PolicyV2Defaults.ruleForToolRisk(context.toolRisk),
                category = CandidateCategory.TOOL_APPROVAL
            )
            ToolRisk.LOW -> Unit // handled by the short-circuit above
        }

        // Workflow class: always include the string-derived class, and also the
        // explicit one when present, so the strictest of the two wins.
        candidates += workflowCandidate(derived.workflowClass, derived.reason)
        context.workflowClass
            ?.takeIf { it != derived.workflowClass }
            ?.let { candidates += workflowCandidate(it, reason = null) }

        // Call source: MCP tools sit outside the built-in Android trust boundary.
        if (context.source == ToolSource.MCP) {
            candidates += Candidate(
                rule = PolicyRule(
                    id = "source-mcp",
                    subject = PolicySubject.SOURCE,
                    decision = PolicyDecisionKind.ASK,
                    reason = "MCP tools are outside the built-in Android trust boundary",
                    riskBand = PolicyRiskBand.MEDIUM
                ),
                category = CandidateCategory.MCP_SOURCE
            )
        }

        return candidates
    }

    private fun workflowCandidate(workflowClass: PolicyWorkflowClass, reason: String?): Candidate {
        val effectiveReason = reason ?: defaultWorkflowReason(workflowClass)
        val rule = PolicyV2Defaults.ruleForWorkflow(workflowClass)
            .let { if (effectiveReason != null) it.copy(reason = effectiveReason) else it }
        val category = when (workflowClass) {
            PolicyWorkflowClass.GENERAL -> CandidateCategory.WORKFLOW_GENERAL
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY -> CandidateCategory.SENSITIVE_TEXT
            PolicyWorkflowClass.MESSAGE_SEND -> CandidateCategory.MESSAGE_SEND
            else -> CandidateCategory.WORKFLOW_SENSITIVE
        }
        return Candidate(rule, category, resolvedWorkflow = workflowClass)
    }

    private fun defaultWorkflowReason(workflowClass: PolicyWorkflowClass): String? {
        if (workflowClass == PolicyWorkflowClass.GENERAL) return null
        return "this is a ${workflowClass.name.lowercase().replace('_', ' ')} workflow"
    }

    private fun toDecision(winner: Candidate, context: PolicyContext): PolicyDecision {
        return when (winner.category) {
            CandidateCategory.BLOCKED_TOOL -> PolicyDecision.Block(
                reason = "tool is marked blocked",
                userMessage = "${context.toolName} is blocked by policy."
            )
            CandidateCategory.SENSITIVE_TEXT -> PolicyDecision.Block(
                reason = "password or secret entry is blocked",
                userMessage = "TouchPilot will not enter passwords, recovery codes, API keys, or other secrets."
            )
            CandidateCategory.WORKFLOW_SENSITIVE -> {
                val reason = winner.rule.reason
                PolicyDecision.Block(
                    reason = reason,
                    userMessage = "TouchPilot blocked this request because $reason."
                )
            }
            CandidateCategory.MESSAGE_SEND -> approval(
                context,
                reason = "sending a message requires explicit approval",
                dataAffected = "A message or outbound communication may be sent from the current app.",
                ifApproved = "TouchPilot will continue with the requested send action."
            )
            CandidateCategory.MCP_SOURCE -> approval(
                context,
                reason = "MCP tools are outside the built-in Android trust boundary",
                dataAffected = "The MCP server may receive tool arguments and affect an external system.",
                ifApproved = "TouchPilot will call the requested MCP tool once."
            )
            CandidateCategory.TOOL_APPROVAL -> approval(
                context,
                reason = "${context.toolRisk.name.lowercase()} risk Android action",
                dataAffected = "The current Android app or screen may be changed.",
                ifApproved = "TouchPilot will run ${context.toolName} with the shown arguments."
            )
            CandidateCategory.WORKFLOW_GENERAL -> PolicyDecision.Allow("low risk action")
        }
    }

    private fun approval(
        context: PolicyContext,
        reason: String,
        dataAffected: String,
        ifApproved: String
    ): PolicyDecision.RequireApproval {
        return PolicyDecision.RequireApproval(
            reason = reason,
            userMessage = "Approval required for ${context.toolName}: $reason.",
            dataAffected = dataAffected,
            ifApproved = ifApproved,
            skillContext = skillRiskContext(context)
        )
    }

    /**
     * Surfaces the active skill's risk in the approval prompt. Returns a note
     * only for a medium- or high-risk skill, so an absent or low-risk skill
     * leaves the prompt unchanged. This may only raise caution; it never lowers
     * a tool's risk or bypasses approval.
     */
    private fun skillRiskContext(context: PolicyContext): String {
        val risk = context.activeSkillRisk ?: return ""
        if (risk == SkillRisk.LOW) return ""
        val title = context.activeSkillTitle?.takeIf { it.isNotBlank() } ?: "the active skill"
        return "This action is requested under the ${risk.name.lowercase()}-risk skill " +
            "\"$title\". Review carefully before approving."
    }

    private fun failClosedRule(context: PolicyContext): PolicyRule = PolicyRule(
        id = "fail-closed",
        subject = PolicySubject.TOOL,
        decision = PolicyDecisionKind.ASK,
        reason = "${context.toolRisk.name.lowercase()} risk Android action",
        riskBand = PolicyRiskBand.MEDIUM
    )

    /**
     * Derives a workflow class from the tool name and arguments, mirroring the
     * checks that used to live inline in [DefaultActionPolicy]: secret entry,
     * the blocked-workflow keyword list (on tool + args only, not screen text),
     * and the message-send affordance (which is screen-aware). The matched
     * blocked-workflow phrase is carried as the reason so block copy is unchanged.
     */
    private fun deriveWorkflow(context: PolicyContext): DerivedWorkflow {
        val intentHaystack = buildString {
            append(context.toolName)
            append(' ')
            append(context.args.values.joinToString(" "))
        }.lowercase()

        // Order mirrors the original DefaultActionPolicy: the blocked-workflow
        // keyword list (tool + args only) is checked first so its specific copy
        // is preserved, then secret entry, then the screen-aware send affordance.
        BlockedWorkflows.firstOrNull { it.needle in intentHaystack }?.let {
            return DerivedWorkflow(it.workflowClass, reason = it.reason)
        }

        if (context.toolName == "type_text") {
            val text = context.args[TEXT_ARG].orEmpty()
            if (SensitiveTextRedactor.containsSensitiveText(text)) {
                return DerivedWorkflow(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY, reason = null)
            }
        }

        val screenAware = (intentHaystack + " " + context.activeScreen).lowercase()
        if (isMessageSend(context, screenAware)) {
            return DerivedWorkflow(PolicyWorkflowClass.MESSAGE_SEND, reason = null)
        }

        return DerivedWorkflow(PolicyWorkflowClass.GENERAL, reason = null)
    }

    private fun isMessageSend(context: PolicyContext, haystack: String): Boolean {
        if (context.toolName != "tap") return false
        val tapText = context.args["text"].orEmpty().lowercase()
        val tapsSend = tapText in setOf("send", "send message", "submit")
        val looksLikeMessageApp = listOf("messages", "sms", "whatsapp", "telegram", "signal", "mail", "gmail")
            .any { it in haystack }
        return tapsSend && looksLikeMessageApp
    }

    private data class DerivedWorkflow(val workflowClass: PolicyWorkflowClass, val reason: String?)

    private data class Candidate(
        val rule: PolicyRule,
        val category: CandidateCategory,
        val resolvedWorkflow: PolicyWorkflowClass? = null
    )

    /**
     * Tie-break order when several candidates share the strictest decision: the
     * lowest [priority] wins and supplies the user-facing copy. Mirrors the
     * original ordered checks (sensitive text, then blocked workflow, then
     * blocked tool; message send, then MCP, then generic tool approval).
     */
    private enum class CandidateCategory(val priority: Int) {
        SENSITIVE_TEXT(0),
        WORKFLOW_SENSITIVE(1),
        BLOCKED_TOOL(2),
        MESSAGE_SEND(3),
        MCP_SOURCE(4),
        TOOL_APPROVAL(5),
        WORKFLOW_GENERAL(6)
    }

    private companion object {
        const val TEXT_ARG = "text"

        data class BlockedWorkflow(
            val needle: String,
            val workflowClass: PolicyWorkflowClass,
            val reason: String
        )

        // Mirrors the original DefaultActionPolicy.blockedWorkflow list so the
        // central engine blocks exactly the same name+args phrases with the same
        // user-facing reasons.
        val BlockedWorkflows: List<BlockedWorkflow> = listOf(
            BlockedWorkflow("payment", PolicyWorkflowClass.PAYMENT, "payments are blocked"),
            BlockedWorkflow("pay ", PolicyWorkflowClass.PAYMENT, "payments are blocked"),
            BlockedWorkflow("password", PolicyWorkflowClass.UNKNOWN_SENSITIVE, "password workflows are blocked"),
            BlockedWorkflow("passcode", PolicyWorkflowClass.UNKNOWN_SENSITIVE, "password workflows are blocked"),
            BlockedWorkflow("account recovery", PolicyWorkflowClass.ACCOUNT_RECOVERY, "account recovery workflows are blocked"),
            BlockedWorkflow("recover account", PolicyWorkflowClass.ACCOUNT_RECOVERY, "account recovery workflows are blocked"),
            BlockedWorkflow("factory reset", PolicyWorkflowClass.DELETION, "destructive settings changes are blocked"),
            BlockedWorkflow("erase all", PolicyWorkflowClass.DELETION, "destructive settings changes are blocked"),
            BlockedWorkflow("delete account", PolicyWorkflowClass.ACCOUNT_CHANGE, "destructive account changes are blocked"),
            BlockedWorkflow("purchase", PolicyWorkflowClass.PURCHASE, "purchases are blocked"),
            BlockedWorkflow("buy now", PolicyWorkflowClass.PURCHASE, "purchases are blocked"),
            BlockedWorkflow("bank", PolicyWorkflowClass.PAYMENT, "banking or financial actions are blocked"),
            BlockedWorkflow("wire transfer", PolicyWorkflowClass.PAYMENT, "banking or financial actions are blocked"),
            BlockedWorkflow("transfer money", PolicyWorkflowClass.PAYMENT, "banking or financial actions are blocked")
        )
    }
}
