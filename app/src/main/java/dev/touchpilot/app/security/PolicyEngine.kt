package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk

/**
 * Central, deterministic policy engine (Milestone 7, issue 3).
 *
 * Every command producer — direct tool execution, local model output, local
 * router output, skills, and MCP — resolves its safety decision here, so
 * behavior cannot drift between callers. Decisions come only from local,
 * testable signals (tool risk, sensitive workflow class, and source) expressed
 * as [PolicyRule]s over the Safety/Policy v2 model ([PolicyV2Model]) and the
 * [WorkflowRiskClassifier]. The model never decides approval.
 *
 * Decision precedence is strictest-wins (block > deny > ask > allow), so a
 * sensitive signal can only raise caution, never lower it, and an unknown
 * sensitive workflow ([PolicyWorkflowClass.UNKNOWN_SENSITIVE]) blocks by default.
 *
 * The engine preserves current behavior:
 * - low-risk observation/wait tools are always allowed (screen/workflow context
 *   cannot escalate them);
 * - sensitive-workflow and secret-entry actions block;
 * - message sends and medium/high-risk actions ask for approval;
 * - MCP tools ask before crossing the external trust boundary.
 */
class PolicyEngine {

    data class Decision(
        val kind: PolicyDecisionKind,
        /** The rule that determined the decision; used for user-facing copy. */
        val primary: PolicyRule,
        val rules: List<PolicyRule>,
    )

    fun decide(request: ToolPolicyRequest): Decision {
        val rules = rulesFor(request)
        val kind = PolicyDecisionKind.strictest(rules.map { it.decision })
        return Decision(kind = kind, primary = primaryRule(rules, kind), rules = rules)
    }

    private fun rulesFor(request: ToolPolicyRequest): List<PolicyRule> {
        val rules = mutableListOf<PolicyRule>()
        rules += PolicyV2Defaults.ruleForToolRisk(request.tool.risk)

        // Low-risk tools only observe or wait; they cannot perform a sensitive
        // workflow, so screen/workflow context must never escalate them.
        if (request.tool.risk == ToolRisk.LOW) return rules

        // Sensitive workflow inferred from the action itself (tool + arguments),
        // matching the previous argument-scoped blocked-workflow / secret-entry
        // checks. Screen text is intentionally excluded here so passive context
        // cannot turn an approval into a block.
        val actionClass = WorkflowRiskClassifier
            .classify(request.tool.name, request.args)
            .workflowClass
        if (actionClass != PolicyWorkflowClass.GENERAL) {
            rules += PolicyV2Defaults.ruleForWorkflow(actionClass)
        }

        // Sending a message is screen-aware: the messaging context only appears
        // in the active screen text.
        val sendsMessage = PolicyWorkflowClass.MESSAGE_SEND in WorkflowRiskClassifier
            .classify(request.tool.name, request.args, request.activeScreen)
            .matched
        if (sendsMessage) {
            rules += PolicyV2Defaults.ruleForWorkflow(PolicyWorkflowClass.MESSAGE_SEND)
        }

        // External MCP tools ask before crossing the built-in trust boundary.
        if (request.source == ToolSource.MCP) {
            rules += PolicyRule(
                id = "source-mcp",
                subject = PolicySubject.SOURCE,
                decision = PolicyDecisionKind.ASK,
                reason = "MCP tools are outside the built-in Android trust boundary",
                workflowClass = PolicyWorkflowClass.EXTERNAL_MCP,
                riskBand = PolicyRiskBand.MEDIUM,
            )
        }

        return rules
    }

    /**
     * The deciding rule for the chosen decision. Among rules that share the
     * strictest decision, the most specific reason wins: a named workflow, then
     * source, then the tool itself.
     */
    private fun primaryRule(rules: List<PolicyRule>, kind: PolicyDecisionKind): PolicyRule {
        val deciding = rules.filter { it.decision == kind }
        return deciding.minByOrNull { copyPriority(it) } ?: rules.first()
    }

    private fun copyPriority(rule: PolicyRule): Int = when {
        rule.subject == PolicySubject.WORKFLOW && rule.workflowClass != PolicyWorkflowClass.GENERAL -> 0
        rule.subject == PolicySubject.SOURCE -> 1
        else -> 2
    }
}
