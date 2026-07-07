package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType

object WorkflowReplayRepairPlanner {
    fun failedStepIndex(result: AgentRunResult): Int? {
        if (result.stopReason == AgentStepStopReason.COMPLETED) return null
        val failedStepPosition = when (result.stopReason) {
            AgentStepStopReason.VERIFICATION_FAILED -> {
                result.steps.indexOfLast { step ->
                    step.type == AgentStepType.VERIFY && step.status == AgentStepStatus.FAILED
                }
            }
            AgentStepStopReason.REPEATED_TOOL_FAILURE,
            AgentStepStopReason.NO_VALID_ACTION,
            AgentStepStopReason.EXECUTOR_ERROR -> {
                result.steps.indexOfLast { step ->
                    step.type == AgentStepType.ACT && step.status == AgentStepStatus.FAILED
                }
            }
            else -> -1
        }
        if (failedStepPosition < 0) return null
        return result.steps.take(failedStepPosition + 1).count { it.type == AgentStepType.ACT }
    }

    fun retryFailedStep(workflow: WorkflowDefinition, failedStepIndex: Int): WorkflowDefinition? {
        return buildRepairedWorkflow(
            workflow = workflow,
            failedStepIndex = failedStepIndex,
            action = RepairAction.RETRY,
        )
    }

    fun reObserveBeforeRetry(workflow: WorkflowDefinition, failedStepIndex: Int): WorkflowDefinition? {
        val retried = retryFailedStep(workflow, failedStepIndex) ?: return null
        val observeStep = WorkflowStep(
            id = "reobserve-before-step-$failedStepIndex",
            tool = "observe_screen_context",
            args = emptyMap(),
            description = "Re-observe the current screen before retrying step $failedStepIndex.",
        )
        return retried.copy(
            steps = listOf(observeStep) + retried.steps,
            description = buildString {
                appendLine(retried.description.trim())
                appendLine()
                append("Prepended a re-observe step before retrying from step $failedStepIndex.")
            },
        )
    }

    fun skipFailedStep(workflow: WorkflowDefinition, failedStepIndex: Int): WorkflowDefinition? {
        return buildRepairedWorkflow(
            workflow = workflow,
            failedStepIndex = failedStepIndex,
            action = RepairAction.SKIP,
        )
    }

    private enum class RepairAction {
        RETRY,
        SKIP,
    }

    private fun buildRepairedWorkflow(
        workflow: WorkflowDefinition,
        failedStepIndex: Int,
        action: RepairAction,
    ): WorkflowDefinition? {
        val zeroBasedIndex = failedStepIndex - 1
        if (zeroBasedIndex !in workflow.steps.indices) return null

        val repairedSteps = when (action) {
            RepairAction.RETRY -> workflow.steps.drop(zeroBasedIndex)
            RepairAction.SKIP -> workflow.steps.toMutableList().apply { removeAt(zeroBasedIndex) }
        }
        if (repairedSteps.isEmpty()) return null

        val repairedParameters = workflow.parameters.filter { parameter ->
            repairedSteps.any { step ->
                step.args.values.any { value -> WorkflowParameters.placeholderName(value) == parameter.name }
            }
        }

        val repairedTitle = if (workflow.title.endsWith(" (repaired)")) {
            workflow.title
        } else {
            "${workflow.title} (repaired)"
        }
        val actionLabel = when (action) {
            RepairAction.RETRY -> "retrying from failed step $failedStepIndex"
            RepairAction.SKIP -> "skipping failed step $failedStepIndex"
        }
        val slugAction = when (action) {
            RepairAction.RETRY -> "retry"
            RepairAction.SKIP -> "skip"
        }
        val repairedDescription = buildString {
            val original = workflow.description.trim()
            if (original.isNotBlank()) {
                appendLine(original)
                appendLine()
            }
            append("Repaired by $actionLabel.")
            appendLine()
            append("Original workflow preserved.")
        }

        return workflow.copy(
            id = WorkflowTraceSerializer.slugify("${workflow.id}-${repairedTitle}-$slugAction-$failedStepIndex"),
            title = repairedTitle,
            description = repairedDescription,
            parameters = repairedParameters,
            steps = repairedSteps,
        )
    }
}
