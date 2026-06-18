package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk

/**
 * Central, deterministic policy engine (Milestone 7, issue 3).
 *
 * Every command producer — direct tool execution, local model output, local
 * router output, skills, and MCP — resolves its safety decision here, so
 * behavior cannot drift between callers. Decisions come only from local,
 * testable signals (tool risk, sensitive workflow class, app context, and
 * source) expressed as [PolicyRule]s over the Safety/Policy v2 model
 * ([PolicyV2Model]) and the [WorkflowRiskClassifier]. The model never decides
 * approval.
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
 * - MCP tools ask before crossing the external trust boundary;
 * - app-aware rules only ask and never lower a block.
 */
class PolicyEngine {
    fun evaluate(request: ToolPolicyRequest): PolicyEvaluation {
        val rules = rulesFor(request)
        if (request.tool.risk == ToolRisk.LOW) {
            return PolicyEvaluation(
                decision = PolicyDecisionKind.ALLOW,
                rules = rules,
                userMessage = "low risk action"
            )
        }
        return PolicyEvaluation.fromRules(rules)
    }

    fun decide(request: ToolPolicyRequest): PolicyDecision {
        return toRuntimeDecision(request, evaluate(request))
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

        rules += AppContextClassifier.rules(request)

        return rules
    }

    private fun toRuntimeDecision(
        request: ToolPolicyRequest,
        evaluation: PolicyEvaluation
    ): PolicyDecision {
        return when (evaluation.decision) {
            PolicyDecisionKind.ALLOW -> PolicyDecision.Allow(
                reason = evaluation.userMessage.ifBlank { "low risk action" }
            )
            PolicyDecisionKind.ASK -> ApprovalCopyBuilder.build(request, evaluation)
            PolicyDecisionKind.DENY -> PolicyDecision.Deny(
                reason = evaluation.userMessage,
                userMessage = evaluation.userMessage
            )
            PolicyDecisionKind.BLOCK -> blockDecision(request, evaluation)
        }
    }

    private fun blockDecision(
        request: ToolPolicyRequest,
        evaluation: PolicyEvaluation
    ): PolicyDecision.Block {
        val blockRules = evaluation.rules.filter { it.decision == PolicyDecisionKind.BLOCK }
        val primary = blockRules.firstOrNull { it.workflowClass == PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY }
            ?: blockRules.firstOrNull { it.subject == PolicySubject.WORKFLOW }
            ?: blockRules.firstOrNull { it.subject == PolicySubject.TOOL }
            ?: blockRules.first()

        val reason = when (primary.workflowClass) {
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY -> "password or secret entry is blocked"
            PolicyWorkflowClass.GENERAL ->
                if (primary.subject == PolicySubject.TOOL && request.tool.risk == ToolRisk.BLOCKED) {
                    "tool is marked blocked"
                } else {
                    primary.reason
                }
            else -> blockedPhrase(primary.workflowClass)
        }
        val userMessage = when {
            primary.workflowClass == PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY ->
                "TouchPilot will not enter passwords, recovery codes, API keys, or other secrets."
            primary.subject == PolicySubject.TOOL && request.tool.risk == ToolRisk.BLOCKED ->
                "${request.tool.name} is blocked by policy."
            else -> "TouchPilot blocked this request because $reason."
        }
        return PolicyDecision.Block(reason = reason, userMessage = userMessage)
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
}
