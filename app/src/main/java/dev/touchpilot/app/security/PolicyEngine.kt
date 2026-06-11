package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk

/**
 * Central policy path for tool execution decisions. Collects rules from tool
 * risk, workflow class, and source, then applies the strictest decision so
 * callers cannot drift into different safety behavior.
 */
class PolicyEngine {
    fun evaluate(request: ToolPolicyRequest): PolicyEvaluation {
        if (request.tool.risk == ToolRisk.LOW) {
            return PolicyEvaluation(
                decision = PolicyDecisionKind.ALLOW,
                rules = listOf(PolicyV2Defaults.ruleForToolRisk(ToolRisk.LOW)),
                userMessage = "low risk action"
            )
        }

        val rules = mutableListOf<PolicyRule>()
        rules.add(PolicyV2Defaults.ruleForToolRisk(request.tool.risk))

        WorkflowClassifier.blockedIntentRule(request)?.let(rules::add)

        WorkflowClassifier.classify(request).forEach { workflowClass ->
            if (workflowClass == PolicyWorkflowClass.GENERAL) return@forEach
            if (workflowClass == PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY) {
                rules.add(sensitiveTextEntryRule())
                return@forEach
            }
            if (workflowClass == PolicyWorkflowClass.MESSAGE_SEND) {
                rules.add(messageSendRule())
                return@forEach
            }
        }

        if (request.source == ToolSource.MCP && request.tool.risk != ToolRisk.LOW) {
            rules.add(mcpSourceRule())
        }

        rules.addAll(AppContextClassifier.rules(request))

        return PolicyEvaluation.fromRules(rules)
    }

    fun decide(request: ToolPolicyRequest): PolicyDecision {
        return toRuntimeDecision(request, evaluate(request))
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

        val reason = primary.reason
        val userMessage = when {
            primary.workflowClass == PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY ->
                "TouchPilot will not enter passwords, recovery codes, API keys, or other secrets."
            primary.subject == PolicySubject.TOOL && request.tool.risk == ToolRisk.BLOCKED ->
                "${request.tool.name} is blocked by policy."
            else -> "TouchPilot blocked this request because $reason."
        }
        return PolicyDecision.Block(reason = reason, userMessage = userMessage)
    }

    private fun sensitiveTextEntryRule(): PolicyRule {
        return PolicyRule(
            id = "workflow-sensitive-text-entry",
            subject = PolicySubject.WORKFLOW,
            decision = PolicyDecisionKind.BLOCK,
            reason = "password or secret entry is blocked",
            workflowClass = PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY,
            riskBand = PolicyRiskBand.BLOCKED
        )
    }

    private fun messageSendRule(): PolicyRule {
        return PolicyRule(
            id = "workflow-message-send",
            subject = PolicySubject.WORKFLOW,
            decision = PolicyDecisionKind.ASK,
            reason = "sending a message requires explicit approval",
            workflowClass = PolicyWorkflowClass.MESSAGE_SEND,
            riskBand = PolicyRiskBand.MEDIUM
        )
    }

    private fun mcpSourceRule(): PolicyRule {
        return PolicyRule(
            id = "source-mcp",
            subject = PolicySubject.SOURCE,
            decision = PolicyDecisionKind.ASK,
            reason = "MCP tools are outside the built-in Android trust boundary",
            riskBand = PolicyRiskBand.MEDIUM
        )
    }
}
