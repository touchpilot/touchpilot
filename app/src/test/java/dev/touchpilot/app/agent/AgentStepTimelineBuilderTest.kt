package dev.touchpilot.app.agent

import dev.touchpilot.app.security.ToolSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentStepTimelineBuilderTest {
    @Test
    fun decideActVerifyStopSequenceIsBuiltFromRunnerCallbacksAndEvents() {
        val builder = AgentStepTimelineBuilder()
        builder.startDecide(1)
        builder.completeDecide("Selected tap_text")

        val command = AgentCommand(tool = "tap_text", args = mapOf("text" to "Settings"), finalAnswer = null)
        builder.onEvent(AgentEvent.UserMessage("open Settings"))
        builder.onEvent(requireNotNull(AgentEvent.toolRequested(command, ToolSource.LOCAL_ROUTER)))
        builder.onEvent(requireNotNull(AgentEvent.toolRunning(command, ToolSource.LOCAL_ROUTER)))
        builder.onEvent(
            AgentEvent.ToolSucceeded(
                tool = "tap_text",
                message = "Tapped Settings"
            )
        )
        builder.startVerify()
        builder.completeVerify("Screen checked")
        builder.onEvent(AgentEvent.FinalAnswer("Opened Settings."))

        val steps = builder.snapshot
        assertEquals(
            listOf(
                AgentStepType.DECIDE,
                AgentStepType.ACT,
                AgentStepType.VERIFY,
                AgentStepType.STOP
            ),
            steps.map { it.type }
        )
        assertEquals(AgentStepStatus.STOPPED, steps.last().status)
        assertEquals("Opened Settings.", steps.last().outputSummary)
    }

    @Test
    fun observeToolMapsToObserveStepType() {
        val builder = AgentStepTimelineBuilder()
        val command = AgentCommand(tool = "observe_screen", args = emptyMap(), finalAnswer = null)
        builder.onEvent(requireNotNull(AgentEvent.toolRequested(command, ToolSource.LOCAL_ROUTER)))
        builder.onEvent(requireNotNull(AgentEvent.toolRunning(command, ToolSource.LOCAL_ROUTER)))
        builder.onEvent(
            AgentEvent.ToolSucceeded(
                tool = "observe_screen",
                message = "screen snapshot"
            )
        )

        assertEquals(AgentStepType.OBSERVE, builder.snapshot.single().type)
        assertEquals(AgentStepStatus.OK, builder.snapshot.single().status)
    }

    @Test
    fun clarificationRequestCreatesClarifyStep() {
        val builder = AgentStepTimelineBuilder()
        builder.requestClarification("Which app should I open?")

        val step = builder.snapshot.single()
        assertEquals(AgentStepType.CLARIFY, step.type)
        assertEquals(AgentStepStatus.CLARIFIED, step.status)
        assertEquals("Which app should I open?", step.outputSummary)
    }

    @Test
    fun policyBlockedMarksActStepBlockedWhenToolWasActive() {
        val builder = AgentStepTimelineBuilder()
        val command = AgentCommand(tool = "type_text", args = mapOf("text" to "secret"), finalAnswer = null)
        builder.onEvent(requireNotNull(AgentEvent.toolRequested(command, ToolSource.LOCAL_ROUTER)))
        builder.onEvent(
            AgentEvent.PolicyBlocked(
                tool = "type_text",
                reason = "blocked",
                userMessage = "That action is blocked."
            )
        )

        val act = builder.snapshot.single()
        assertEquals(AgentStepType.ACT, act.type)
        assertEquals(AgentStepStatus.BLOCKED, act.status)
    }

    @Test
    fun sensitiveArgsAreRedactedInSummaries() {
        val builder = AgentStepTimelineBuilder()
        val command = AgentCommand(
            tool = "type_text",
            args = mapOf("text" to "password is hunter2"),
            finalAnswer = null
        )
        builder.onEvent(requireNotNull(AgentEvent.toolRequested(command, ToolSource.LOCAL_ROUTER)))

        assertTrue(builder.snapshot.single().inputSummary.contains("[REDACTED]"))
    }
}
