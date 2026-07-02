package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType

object WorkflowReplayRepairPlanner {
    fun failedStepIndex(result: AgentRunResult): Int? {
        if (result.stopReason == AgentStepStopReason.COMPLETED) return null
        val failedStepPosition = result.steps.indexOfLast { step ->
            step.type != AgentStepType.STOP && step.status == AgentStepStatus.FAILED
        }
        if (failedStepPosition < 0) return null
        return result.steps.take(failedStepPosition + 1).count { it.type == AgentStepType.ACT }
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
