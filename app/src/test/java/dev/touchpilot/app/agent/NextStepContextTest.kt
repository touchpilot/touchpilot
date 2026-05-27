package dev.touchpilot.app.agent

import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NextStepContextTest {

    @Test
    fun initialContextIsStepZeroWithNoPriorTool() {
        val ctx = NextStepContext.initial("open Settings")
        assertEquals(0, ctx.stepNumber)
        assertEquals("open Settings", ctx.task)
        assertFalse(ctx.hasPreviousStep)
        assertFalse(ctx.previousSucceeded)
    }

    @Test
    fun hasPreviousStepTrueOnlyWhenStepAndCommandPresent() {
        val withCommand = NextStepContext(
            task = "tap Save",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "tap", args = mapOf("text" to "Save"), finalAnswer = null),
        )
        val withoutCommand = NextStepContext(task = "tap Save", stepNumber = 1)
        assertTrue(withCommand.hasPreviousStep)
        assertFalse(withoutCommand.hasPreviousStep)
    }

    @Test
    fun previousSucceededReflectsToolResultOk() {
        val ok = NextStepContext(
            task = "tap Save",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "tap", args = mapOf("text" to "Save"), finalAnswer = null),
            previousToolResult = ToolResult(ok = true, message = "tap"),
        )
        val failed = ok.copy(previousToolResult = ToolResult(ok = false, message = "Ambiguous input target"))
        assertTrue(ok.previousSucceeded)
        assertFalse(failed.previousSucceeded)
    }

    @Test
    fun redactedSummaryRedactsSensitiveToolMessage() {
        val ctx = NextStepContext(
            task = "type my password",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "type_text", args = emptyMap(), finalAnswer = null),
            previousToolResult = ToolResult(ok = false, message = "Failed near password: hunter2"),
        )
        val summary = ctx.redactedSummary()
        assertFalse(summary.contains("hunter2"), "raw secret leaked into summary: $summary")
        assertContains(summary, "type_text")
        assertContains(summary, "failed")
    }

    @Test
    fun redactedSummaryHandlesNoPriorTool() {
        val ctx = NextStepContext.initial("anything")
        assertContains(ctx.redactedSummary(), "no prior tool")
    }

    @Test
    fun negativeStepNumberIsRejected() {
        assertFails { NextStepContext(task = "t", stepNumber = -1) }
    }

    @Test
    fun candidateConfidenceMustBeBetweenZeroAndOne() {
        assertFails {
            NextStepCandidate(
                nodeId = "0",
                displayLabel = "Settings",
                confidence = -0.1f,
            )
        }
        assertFails {
            NextStepCandidate(
                nodeId = "0",
                displayLabel = "Settings",
                confidence = 1.5f,
            )
        }
    }
}
