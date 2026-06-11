package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk

/**
 * Safety/Policy v2 vocabulary. This file is intentionally model-only so the
 * first Milestone 7 PR can establish stable concepts without changing runtime
 * enforcement paths yet.
 */
enum class PolicySubject {
    TOOL,
    APP,
    WORKFLOW,
    SKILL,
    SOURCE
}

enum class PolicyWorkflowClass {
    GENERAL,
    MESSAGE_SEND,
    PAYMENT,
    PURCHASE,
    DELETION,
    ACCOUNT_CHANGE,
    ACCOUNT_RECOVERY,
    PERMISSION_CHANGE,
    SECURITY_SETTINGS,
    SENSITIVE_TEXT_ENTRY,
    EXTERNAL_MCP,
    UNKNOWN_SENSITIVE
}

enum class PolicyRiskBand {
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED;

    companion object {
        fun fromToolRisk(risk: ToolRisk): PolicyRiskBand = when (risk) {
            ToolRisk.LOW -> LOW
            ToolRisk.MEDIUM -> MEDIUM
            ToolRisk.HIGH -> HIGH
            ToolRisk.BLOCKED -> BLOCKED
        }
    }
}

enum class PolicyDecisionKind(val precedence: Int) {
    ALLOW(0),
    ASK(1),
    DENY(2),
    BLOCK(3);

    companion object {
        fun strictest(kinds: Iterable<PolicyDecisionKind>): PolicyDecisionKind {
            return kinds.maxByOrNull { it.precedence } ?: ALLOW
        }
    }
}

data class PolicyRule(
    val id: String,
    val subject: PolicySubject,
    val decision: PolicyDecisionKind,
    val reason: String,
    val workflowClass: PolicyWorkflowClass = PolicyWorkflowClass.GENERAL,
    val riskBand: PolicyRiskBand = PolicyRiskBand.LOW
)

data class PolicyEvaluation(
    val decision: PolicyDecisionKind,
    val rules: List<PolicyRule>,
    val userMessage: String
) {
    companion object {
        fun fromRules(rules: List<PolicyRule>): PolicyEvaluation {
            val decision = PolicyDecisionKind.strictest(rules.map { it.decision })
            val reason = rules
                .filter { it.decision == decision }
                .joinToString(separator = "; ") { it.reason }
                .ifBlank { "No policy rule matched." }
            return PolicyEvaluation(
                decision = decision,
                rules = rules,
                userMessage = reason
            )
        }
    }
}

object PolicyV2Defaults {
    fun decisionForToolRisk(risk: ToolRisk): PolicyDecisionKind {
        return when (risk) {
            ToolRisk.LOW -> PolicyDecisionKind.ALLOW
            ToolRisk.MEDIUM,
            ToolRisk.HIGH -> PolicyDecisionKind.ASK
            ToolRisk.BLOCKED -> PolicyDecisionKind.BLOCK
        }
    }

    fun decisionForWorkflow(workflowClass: PolicyWorkflowClass): PolicyDecisionKind {
        return when (workflowClass) {
            PolicyWorkflowClass.GENERAL -> PolicyDecisionKind.ALLOW
            PolicyWorkflowClass.MESSAGE_SEND -> PolicyDecisionKind.ASK
            PolicyWorkflowClass.PAYMENT,
            PolicyWorkflowClass.PURCHASE,
            PolicyWorkflowClass.DELETION,
            PolicyWorkflowClass.ACCOUNT_CHANGE,
            PolicyWorkflowClass.ACCOUNT_RECOVERY,
            PolicyWorkflowClass.PERMISSION_CHANGE,
            PolicyWorkflowClass.SECURITY_SETTINGS,
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY,
            PolicyWorkflowClass.EXTERNAL_MCP,
            PolicyWorkflowClass.UNKNOWN_SENSITIVE -> PolicyDecisionKind.BLOCK
        }
    }

    fun ruleForToolRisk(risk: ToolRisk): PolicyRule {
        val band = PolicyRiskBand.fromToolRisk(risk)
        return PolicyRule(
            id = "tool-risk-${risk.name.lowercase()}",
            subject = PolicySubject.TOOL,
            decision = decisionForToolRisk(risk),
            reason = "${risk.name.lowercase()} risk tool",
            riskBand = band
        )
    }

    fun ruleForWorkflow(workflowClass: PolicyWorkflowClass): PolicyRule {
        return PolicyRule(
            id = "workflow-${workflowClass.name.lowercase()}",
            subject = PolicySubject.WORKFLOW,
            decision = decisionForWorkflow(workflowClass),
            reason = "${workflowClass.name.lowercase()} workflow",
            workflowClass = workflowClass,
            riskBand = when (decisionForWorkflow(workflowClass)) {
                PolicyDecisionKind.ALLOW -> PolicyRiskBand.LOW
                PolicyDecisionKind.ASK -> PolicyRiskBand.MEDIUM
                PolicyDecisionKind.DENY -> PolicyRiskBand.HIGH
                PolicyDecisionKind.BLOCK -> PolicyRiskBand.BLOCKED
            }
        )
    }
}
