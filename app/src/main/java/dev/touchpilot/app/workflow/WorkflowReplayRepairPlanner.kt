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
        val zeroBasedIndex = failedStepIndex - 1
        if (zeroBasedIndex !in workflow.steps.indices) return null

        val repairedSteps = workflow.steps.drop(zeroBasedIndex)
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
        val repairedDescription = buildString {
            val original = workflow.description.trim()
            if (original.isNotBlank()) {
                appendLine(original)
                appendLine()
            }
            append("Repaired by retrying from failed step $failedStepIndex.")
        }

        return workflow.copy(
            id = WorkflowTraceSerializer.slugify("${workflow.id}-${repairedTitle}-retry-$failedStepIndex"),
            title = repairedTitle,
            description = repairedDescription,
            parameters = repairedParameters,
            steps = repairedSteps,
        )
    }

    fun skipFailedStep(workflow: WorkflowDefinition, failedStepIndex: Int): WorkflowDefinition? {
        val zeroBasedIndex = failedStepIndex - 1
        if (zeroBasedIndex !in workflow.steps.indices) return null

        val repairedSteps = workflow.steps.toMutableList().apply {
            removeAt(zeroBasedIndex)
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
        val repairedDescription = buildString {
            val original = workflow.description.trim()
            if (original.isNotBlank()) {
                appendLine(original)
                appendLine()
            }
            append("Repaired by skipping failed step $failedStepIndex.")
        }

        return workflow.copy(
            id = WorkflowTraceSerializer.slugify("${workflow.id}-${repairedTitle}-skip-$failedStepIndex"),
            title = repairedTitle,
            description = repairedDescription,
            parameters = repairedParameters,
            steps = repairedSteps,
        )
    }
}
