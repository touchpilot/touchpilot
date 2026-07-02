package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowReplayRepairPlannerTest {
    @Test
    fun findsTheFailedWorkflowStepFromReplaySteps() {
        val failed = AgentRunResult(
            transcript = "failed",
            finalAnswer = null,
            stopReason = AgentStepStopReason.VERIFICATION_FAILED,
            stopMessage = "Step 2 failed.",
            steps = listOf(
                actStep(1, AgentStepStatus.OK),
                verifyStep(2, AgentStepStatus.FAILED),
                stopStep(3, AgentStepStatus.FAILED),
            ),
        )

        assertEquals(1, WorkflowReplayRepairPlanner.failedStepIndex(failed))
        assertNull(
            WorkflowReplayRepairPlanner.failedStepIndex(
                failed.copy(stopReason = AgentStepStopReason.COMPLETED)
            )
        )
    }

    @Test
    fun skipsTheFailedStepAndKeepsOnlyStillReferencedParameters() {
        val workflow = WorkflowDefinition(
            id = "open-settings",
            title = "Open Settings",
            description = "Open the Settings app and toggle Wi-Fi.",
            parameters = listOf(
                WorkflowParameter(name = "target_label", default = "Wi-Fi"),
                WorkflowParameter(name = "unused_label", default = "Bluetooth"),
            ),
            steps = listOf(
                WorkflowStep(
                    id = "open-app",
                    tool = "open_app",
                    args = mapOf("target" to "{target_label}"),
                ),
                WorkflowStep(
                    id = "tap-toggle",
                    tool = "tap",
                    args = mapOf("text" to "Toggle"),
                ),
            ),
        )

        val repaired = WorkflowReplayRepairPlanner.skipFailedStep(workflow, failedStepIndex = 1)

        assertNotNull(repaired)
        assertEquals("open-settings-open-settings-repaired-skip-1", repaired.id)
        assertEquals("Open Settings (repaired)", repaired.title)
        assertEquals(1, repaired.steps.size)
        assertEquals("tap", repaired.steps.single().tool)
        assertTrue(repaired.parameters.isEmpty())
        assertTrue(repaired.description.contains("Repaired by skipping failed step 1."))
    }

    @Test
    fun doesNotCreateAnEmptyWorkflowWhenSkippingTheOnlyStep() {
        val workflow = WorkflowDefinition(
            id = "single-step",
            title = "Single Step",
            steps = listOf(
                WorkflowStep(
                    id = "tap",
                    tool = "tap",
                )
            ),
        )

        assertNull(WorkflowReplayRepairPlanner.skipFailedStep(workflow, failedStepIndex = 1))
    }

    private fun actStep(index: Int, status: AgentStepStatus): AgentStep {
        return AgentStep(
            sequenceNumber = index,
            type = AgentStepType.ACT,
            status = status,
            inputSummary = "act $index",
            outputSummary = "act $index",
            toolCall = dev.touchpilot.app.agent.AgentStepToolCall(
                tool = "tap",
                args = emptyMap(),
                source = "local_router",
            ),
            startedAtMillis = index.toLong(),
            endedAtMillis = index.toLong(),
        )
    }

    private fun verifyStep(index: Int, status: AgentStepStatus): AgentStep {
        return AgentStep(
            sequenceNumber = index,
            type = AgentStepType.VERIFY,
            status = status,
            inputSummary = "verify $index",
            outputSummary = "verify $index",
            verification = dev.touchpilot.app.agent.AgentStepVerification(
                status = if (status == AgentStepStatus.OK) "passed" else "failed",
                reason = "screen mismatch",
                data = emptyMap(),
            ),
            startedAtMillis = index.toLong(),
            endedAtMillis = index.toLong(),
        )
    }

    private fun stopStep(index: Int, status: AgentStepStatus): AgentStep {
        return AgentStep(
            sequenceNumber = index,
            type = AgentStepType.STOP,
            status = status,
            inputSummary = "stop $index",
            outputSummary = "stop $index",
            stopReason = AgentStepStopReason.VERIFICATION_FAILED,
            startedAtMillis = index.toLong(),
            endedAtMillis = index.toLong(),
        )
    }
}
