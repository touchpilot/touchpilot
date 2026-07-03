package dev.touchpilot.app.workflow

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowSensitivityTest {
    @Test
    fun lowRiskStepWithNoPolicyHintIsNotSensitive() {
        val step = WorkflowStep(id = "s1", tool = "wait_for_idle")

        assertFalse(WorkflowSensitivity.isSensitive(step))
    }

    @Test
    fun mediumRiskToolIsSensitiveEvenWithoutPolicyHint() {
        val step = WorkflowStep(id = "s1", tool = "tap", args = mapOf("text" to "Wi-Fi"))

        assertTrue(WorkflowSensitivity.isSensitive(step))
    }

    @Test
    fun policyHintMarksALowRiskToolSensitive() {
        val step = WorkflowStep(
            id = "s1",
            tool = "wait_for_idle",
            policy = WorkflowStepPolicy(requiresApproval = true),
        )

        assertTrue(WorkflowSensitivity.isSensitive(step))
    }

    @Test
    fun sensitiveStepIndicesFindsOnlySensitiveSteps() {
        val steps = listOf(
            WorkflowStep(id = "s1", tool = "observe_screen_context"),
            WorkflowStep(id = "s2", tool = "tap", args = mapOf("text" to "Wi-Fi")),
            WorkflowStep(id = "s3", tool = "wait_for_idle"),
            WorkflowStep(id = "s4", tool = "type_text", args = mapOf("text" to "hello")),
        )

        assertEquals(listOf(1, 3), WorkflowSensitivity.sensitiveStepIndices(steps))
        assertEquals(2, WorkflowSensitivity.sensitiveStepCount(steps))
    }
}
