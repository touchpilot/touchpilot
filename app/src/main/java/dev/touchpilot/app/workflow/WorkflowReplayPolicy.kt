package dev.touchpilot.app.workflow

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.tools.ToolSpec

/**
 * Workflow-specific policy rules applied during deterministic replay (issue #382).
 * These sit on top of the central [dev.touchpilot.app.security.PolicyEngine] and
 * never lower a block/deny decision.
 */
object WorkflowReplayPolicy {
    fun skillScopeViolation(workflow: WorkflowDefinition, step: WorkflowStep): String? {
        val allowedTools = workflow.skillScope?.allowedTools.orEmpty()
        if (allowedTools.isEmpty()) return null
        return if (step.tool in allowedTools) {
            null
        } else {
            "${step.tool} is not allowed by workflow skill scope"
        }
    }

    fun applyStepPolicyFloor(
        decision: PolicyDecision,
        step: WorkflowStep,
        tool: ToolSpec,
    ): PolicyDecision {
        if (step.policy?.requiresApproval != true) return decision
        return when (decision) {
            is PolicyDecision.Block,
            is PolicyDecision.Deny,
            is PolicyDecision.RequireApproval -> decision
            is PolicyDecision.Allow -> PolicyDecision.RequireApproval(
                reason = "workflow step policy requires approval",
                userMessage = "Approval required for ${tool.name}: this workflow step is marked approval-sensitive.",
                dataAffected = "Current screen and ${tool.name} arguments from the workflow replay.",
                ifApproved = "TouchPilot will run ${tool.name} as recorded in this workflow step.",
                workflowLabel = step.policy.workflowClass?.name?.lowercase()?.replace('_', ' ')
                    ?: "workflow replay",
            )
        }
    }
}
