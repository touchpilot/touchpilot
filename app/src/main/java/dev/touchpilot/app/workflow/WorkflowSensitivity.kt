package dev.touchpilot.app.workflow

import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk

/**
 * Determines which workflow steps are approval-sensitive so review surfaces
 * (the capture offer, the workflow editor) can warn before a workflow is
 * saved. A step counts as sensitive when its own policy hint already says so,
 * or when the underlying tool's catalog risk is MEDIUM, HIGH, or BLOCKED.
 *
 * This mirrors the floor [WorkflowReplayEngine] enforces live via the policy
 * engine during replay, so review surfaces never understate a risk that
 * replay will still gate behind approval (issue #381 safety requirement:
 * captured workflows must respect existing policy decisions per step).
 */
object WorkflowSensitivity {
    fun isSensitive(step: WorkflowStep): Boolean {
        if (step.policy?.requiresApproval == true) return true
        return when (AndroidToolCatalog.find(step.tool)?.risk) {
            ToolRisk.MEDIUM, ToolRisk.HIGH, ToolRisk.BLOCKED -> true
            else -> false
        }
    }

    fun sensitiveStepIndices(steps: List<WorkflowStep>): List<Int> {
        return steps.withIndex().filter { (_, step) -> isSensitive(step) }.map { it.index }
    }

    fun sensitiveStepCount(steps: List<WorkflowStep>): Int = sensitiveStepIndices(steps).size
}
