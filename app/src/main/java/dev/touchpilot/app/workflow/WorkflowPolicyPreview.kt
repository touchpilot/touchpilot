package dev.touchpilot.app.workflow

import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog

enum class WorkflowStepPolicyOutcome {
    AUTO,
    NEEDS_APPROVAL,
    BLOCKED,
}

data class WorkflowStepPolicyPreview(
    val stepIndex: Int,
    val tool: String,
    val outcome: WorkflowStepPolicyOutcome,
    val note: String,
)

object WorkflowPolicyPreview {
    fun preview(
        definition: WorkflowDefinition,
        parameters: Map<String, String> = emptyMap(),
        policy: ActionPolicy = DefaultActionPolicy(),
        liveContext: WorkflowLivePolicyContext = WorkflowLivePolicyContext(),
        source: ToolSource = ToolSource.WORKFLOW_REPLAY,
    ): List<WorkflowStepPolicyPreview> {
        val resolvedParameters = WorkflowParameters.resolveValues(definition, parameters)
        return definition.steps.mapIndexed { index, step ->
            val resolvedArgs = WorkflowParameters.substitute(step.args, resolvedParameters)
            val spec = AndroidToolCatalog.find(step.tool)
            val outcome = if (spec == null) {
                WorkflowStepPolicyPreview(
                    stepIndex = index + 1,
                    tool = step.tool,
                    outcome = WorkflowStepPolicyOutcome.BLOCKED,
                    note = "Unknown tool",
                )
            } else {
                when (
                    val decision = policy.evaluate(
                        ToolPolicyRequest(
                            tool = spec,
                            args = resolvedArgs,
                            source = source,
                            activeScreen = liveContext.activeScreen,
                            foregroundApp = liveContext.foregroundApp,
                        )
                    )
                ) {
                    is PolicyDecision.Allow -> WorkflowStepPolicyPreview(
                        stepIndex = index + 1,
                        tool = step.tool,
                        outcome = WorkflowStepPolicyOutcome.AUTO,
                        note = "Runs automatically",
                    )
                    is PolicyDecision.RequireApproval -> WorkflowStepPolicyPreview(
                        stepIndex = index + 1,
                        tool = step.tool,
                        outcome = WorkflowStepPolicyOutcome.NEEDS_APPROVAL,
                        note = decision.userMessage,
                    )
                    is PolicyDecision.Deny,
                    is PolicyDecision.Block -> WorkflowStepPolicyPreview(
                        stepIndex = index + 1,
                        tool = step.tool,
                        outcome = WorkflowStepPolicyOutcome.BLOCKED,
                        note = decision.userMessage,
                    )
                }
            }
            outcome
        }
    }

    fun summaryLine(previews: List<WorkflowStepPolicyPreview>): String {
        val approvalCount = previews.count { it.outcome == WorkflowStepPolicyOutcome.NEEDS_APPROVAL }
        val blockedCount = previews.count { it.outcome == WorkflowStepPolicyOutcome.BLOCKED }
        return when {
            blockedCount > 0 && approvalCount > 0 ->
                "$approvalCount step(s) need approval; $blockedCount step(s) are blocked by policy."
            approvalCount > 0 ->
                "$approvalCount of ${previews.size} step(s) will ask for approval during replay."
            blockedCount > 0 ->
                "$blockedCount of ${previews.size} step(s) are blocked by policy."
            else -> "All steps run automatically under current policy."
        }
    }
}
