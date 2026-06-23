package dev.touchpilot.app.demonstration.export

import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.DemonstrationStatus
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowElementPredicate
import dev.touchpilot.app.workflow.WorkflowExpectedState
import dev.touchpilot.app.workflow.WorkflowParameter
import dev.touchpilot.app.workflow.WorkflowParameters
import dev.touchpilot.app.workflow.WorkflowStep
import dev.touchpilot.app.workflow.WorkflowStepPolicy
import dev.touchpilot.app.workflow.WorkflowTextMatch
import dev.touchpilot.app.workflow.WorkflowTrace
import dev.touchpilot.app.workflow.WorkflowTraceSerializer
import dev.touchpilot.app.workflow.WorkflowTraceStep
import dev.touchpilot.app.workflow.WorkflowTraceVerification

/**
 * Converts a captured [DemonstrationSession] into a portable [WorkflowDefinition]
 * for replay, using per-step screen deltas to infer expected state.
 */
object DemonstrationWorkflowConverter {
    fun toWorkflowDefinition(session: DemonstrationSession): WorkflowDefinition? {
        if (session.steps.isEmpty()) return null
        if (session.metadata.status == DemonstrationStatus.CANCELLED) return null

        val trace = toWorkflowTrace(session) ?: return null
        return WorkflowTraceSerializer.toDefinition(
            trace = trace,
            workflowId = "demo-${session.sessionId}",
            title = "Demo: ${session.metadata.task}",
        )
    }

    fun toWorkflowTrace(session: DemonstrationSession): WorkflowTrace? {
        if (session.steps.isEmpty()) return null

        val steps = session.steps.map { step ->
            WorkflowTraceStep(
                index = step.index,
                tool = step.action.tool,
                args = step.action.args,
                source = step.action.source,
                succeeded = step.action.succeeded,
                verification = step.action.verificationStatus?.let { status ->
                    WorkflowTraceVerification(
                        status = status,
                        reason = step.action.verificationReason.orEmpty(),
                    )
                },
            )
        }

        val screenSignals = buildList {
            session.initialFrame?.let {
                add(dev.touchpilot.app.workflow.WorkflowTraceScreenSignal(
                    phase = it.phase.wireName,
                    nodeCount = it.nodeCount,
                    containsSensitiveContent = it.containsSensitiveContent,
                ))
            }
            session.finalFrame?.let {
                add(dev.touchpilot.app.workflow.WorkflowTraceScreenSignal(
                    phase = it.phase.wireName,
                    nodeCount = it.nodeCount,
                    containsSensitiveContent = it.containsSensitiveContent,
                ))
            }
        }

        return WorkflowTrace(
            runId = session.runId,
            task = session.metadata.task,
            capturedAtMillis = session.metadata.completedAtMillis ?: session.metadata.startedAtMillis,
            steps = steps,
            screenSignals = screenSignals,
            skillId = session.metadata.skillId,
        )
    }

    fun toWorkflowSteps(session: DemonstrationSession): List<WorkflowStep> {
        val definition = toWorkflowDefinition(session) ?: return emptyList()
        return definition.steps
    }

    fun expectedStateFromStep(session: DemonstrationSession, stepIndex: Int): WorkflowExpectedState? {
        val step = session.steps.firstOrNull { it.index == stepIndex } ?: return null
        val tappedText = step.action.args["text"]?.takeIf { it.isNotBlank() }
        val deltaTexts = step.screenDelta?.addedTexts.orEmpty()

        val elementPredicates = tappedText?.let {
            listOf(WorkflowElementPredicate(text = it, match = WorkflowTextMatch.CONTAINS))
        }.orEmpty()

        val screenText = buildList {
            tappedText?.let { add(it) }
            addAll(deltaTexts.take(3))
            step.action.verificationReason?.let { add(it) }
        }.distinct()

        if (elementPredicates.isEmpty() && screenText.isEmpty()) return null

        return WorkflowExpectedState(
            screenTextContains = screenText,
            elementPresent = elementPredicates,
        )
    }

    fun inferParameters(session: DemonstrationSession): List<WorkflowParameter> {
        val trace = toWorkflowTrace(session) ?: return emptyList()
        val definition = WorkflowTraceSerializer.toDefinition(trace)
        return definition.parameters
    }
}
