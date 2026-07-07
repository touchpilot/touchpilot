package dev.touchpilot.app.workflow

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WorkflowReplayPolicyTest {
    private val observeTool = requireNotNull(AndroidToolCatalog.find("observe_screen"))

    @Test
    fun skillScopeViolationWhenToolNotAllowed() {
        val workflow = WorkflowDefinition(
            id = "scoped",
            title = "Scoped",
            skillScope = WorkflowSkillScope(allowedTools = listOf("observe_screen")),
            steps = listOf(WorkflowStep(id = "tap", tool = "tap", args = mapOf("text" to "OK"))),
        )

        assertEquals(
            "tap is not allowed by workflow skill scope",
            WorkflowReplayPolicy.skillScopeViolation(workflow, workflow.steps.single()),
        )
    }

    @Test
    fun skillScopeAllowsListedTool() {
        val workflow = WorkflowDefinition(
            id = "scoped",
            title = "Scoped",
            skillScope = WorkflowSkillScope(allowedTools = listOf("observe_screen")),
            steps = listOf(WorkflowStep(id = "observe", tool = "observe_screen")),
        )

        assertNull(WorkflowReplayPolicy.skillScopeViolation(workflow, workflow.steps.single()))
    }

    @Test
    fun stepPolicyFloorUpgradesAllowToRequireApproval() {
        val step = WorkflowStep(
            id = "observe",
            tool = "observe_screen",
            policy = WorkflowStepPolicy(requiresApproval = true),
        )
        val decision = WorkflowReplayPolicy.applyStepPolicyFloor(
            decision = PolicyDecision.Allow(reason = "low risk"),
            step = step,
            tool = observeTool,
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun stepPolicyFloorDoesNotLowerBlockDecision() {
        val step = WorkflowStep(
            id = "blocked",
            tool = "tap",
            policy = WorkflowStepPolicy(requiresApproval = true),
        )
        val blocked = PolicyDecision.Block(
            reason = "blocked",
            userMessage = "Blocked.",
        )
        val decision = WorkflowReplayPolicy.applyStepPolicyFloor(
            decision = blocked,
            step = step,
            tool = tapTool(),
        )

        assertEquals(blocked, decision)
    }

    private fun tapTool(): ToolSpec = ToolSpec(
        name = "tap",
        description = "tap",
        risk = ToolRisk.MEDIUM,
        arguments = emptyMap(),
    )
}
