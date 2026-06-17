package dev.touchpilot.app.workflow

import dev.touchpilot.app.security.DefaultActionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkflowPolicyPreviewTest {
    private val policy = DefaultActionPolicy()

    @Test
    fun marksLowRiskStepsAsAuto() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Observe",
            steps = listOf(step("observe_screen")),
        )

        val preview = WorkflowPolicyPreview.preview(definition, policy = policy)

        assertEquals(1, preview.size)
        assertEquals(WorkflowStepPolicyOutcome.AUTO, preview.single().outcome)
    }

    @Test
    fun marksMediumRiskStepsAsNeedingApproval() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Open app",
            steps = listOf(step("open_app", args = mapOf("target" to "Settings"))),
        )

        val preview = WorkflowPolicyPreview.preview(definition, policy = policy)

        assertEquals(WorkflowStepPolicyOutcome.NEEDS_APPROVAL, preview.single().outcome)
    }

    @Test
    fun usesLiveScreenForMessageSendDetection() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Send message",
            steps = listOf(step("tap", args = mapOf("text" to "Send"))),
        )

        val withMessagingScreen = WorkflowPolicyPreview.preview(
            definition = definition,
            policy = policy,
            liveContext = WorkflowLivePolicyContext(activeScreen = "Chat thread with Send button"),
        )

        assertEquals(WorkflowStepPolicyOutcome.NEEDS_APPROVAL, withMessagingScreen.single().outcome)
        assertTrue(withMessagingScreen.single().note.contains("Approval required"))
    }

    @Test
    fun summaryLineCountsApprovalAndBlockedSteps() {
        val definition = WorkflowDefinition(
            id = "wf-1",
            title = "Mixed",
            steps = listOf(
                step("observe_screen", id = "step-1"),
                step("open_app", args = mapOf("target" to "Settings"), id = "step-2"),
            ),
        )
        val preview = WorkflowPolicyPreview.preview(definition, policy = policy)

        val summary = WorkflowPolicyPreview.summaryLine(preview)

        assertTrue(summary.contains("1 of 2"))
        assertTrue(summary.contains("approval"))
    }

    private fun step(
        tool: String,
        args: Map<String, String> = emptyMap(),
        id: String = "step-1",
    ) = WorkflowStep(id = id, tool = tool, args = args)
}
