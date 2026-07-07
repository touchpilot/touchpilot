package dev.touchpilot.app.runtime

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentRunState
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepTimelineBuilder
import dev.touchpilot.app.agent.ClarificationReason
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.NextStepCandidate
import dev.touchpilot.app.ui.chat.ChatEvent
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowStep
import dev.touchpilot.app.workflow.WorkflowTraceStore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRunControllerClarificationStateTest {
    @Test
    fun clarificationNeededSetsWaitingClarificationState() {
        val conversation = mutableListOf<ChatEvent>()
        val controller = controller(
            conversation = conversation,
            reasoningCore = ClarificationReasoningCore(),
        )

        controller.startFromChat("tap Settings")
        waitForRunState(controller, AgentRunState.WAITING_CLARIFICATION)
        waitForConversationEvent<ChatEvent.ClarificationPrompt>(conversation)
    }

    @Test
    fun cancelRunClearsWaitingClarificationState() {
        val conversation = mutableListOf<ChatEvent>()
        val controller = controller(
            conversation = conversation,
            reasoningCore = ClarificationReasoningCore(),
        )

        controller.startFromChat("tap Settings")
        waitForRunState(controller, AgentRunState.WAITING_CLARIFICATION)

        controller.cancelRun()

        assertEquals(AgentRunState.CANCELLED, controller.runState)
    }

    @Test
    fun clarificationReplyStartsNewRun() {
        val secondStarted = CountDownLatch(1)
        val conversation = mutableListOf<ChatEvent>()
        val controller = controller(
            conversation = conversation,
            reasoningCore = object : LocalReasoningCore {
                private var calls = 0

                override fun run(
                    task: String,
                    timeline: AgentStepTimelineBuilder?,
                    listener: AgentEventListener,
                    cancellationSignal: AtomicBoolean,
                ): AgentRunResult {
                    calls += 1
                    return if (calls == 1) {
                        clarificationNeededResult()
                    } else {
                        secondStarted.countDown()
                        AgentRunResult(
                            transcript = "done",
                            finalAnswer = "Done.",
                            stopReason = AgentStepStopReason.COMPLETED,
                            stopMessage = "Completed",
                        )
                    }
                }

                override fun replayWorkflow(
                    definition: dev.touchpilot.app.workflow.WorkflowDefinition,
                    parameters: Map<String, String>,
                    timeline: AgentStepTimelineBuilder?,
                    listener: AgentEventListener,
                    cancellationSignal: AtomicBoolean,
                ): AgentRunResult {
                    error("not used")
                }
            },
        )

        controller.startFromChat("tap Settings")
        waitForRunState(controller, AgentRunState.WAITING_CLARIFICATION)

        controller.startFromChat("Wi-Fi Settings")
        assertTrue(secondStarted.await(3, TimeUnit.SECONDS))
        waitForRunState(controller, AgentRunState.COMPLETED)
    }

    @Test
    fun startWorkflowReplayRejectsWhileWaitingClarification() {
        val conversation = mutableListOf<ChatEvent>()
        val controller = controller(
            conversation = conversation,
            reasoningCore = ClarificationReasoningCore(),
        )

        controller.startFromChat("tap Settings")
        waitForRunState(controller, AgentRunState.WAITING_CLARIFICATION)

        var finished = false
        var finishedMessage = ""
        controller.startWorkflowReplay(
            definition = WorkflowDefinition(
                id = "wf-1",
                title = "Replay me",
                steps = listOf(
                    WorkflowStep(id = "step-1", tool = "press_home", args = emptyMap()),
                ),
            ),
            onFinished = { _, message, _ ->
                finished = true
                finishedMessage = message
            },
        )

        assertTrue(finished)
        assertTrue(finishedMessage.contains("Wait for the current run"))
        assertEquals(AgentRunState.WAITING_CLARIFICATION, controller.runState)
        assertEquals(
            "Run already in progress.",
            conversation.filterIsInstance<ChatEvent.Agent>().last().text
        )
    }

    private fun waitForRunState(controller: AgentRunController, expected: AgentRunState) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            if (controller.runState == expected) return
            Thread.sleep(25L)
        }
        error("Timed out waiting for $expected, got ${controller.runState}")
    }

    private inline fun <reified T : ChatEvent> waitForConversationEvent(conversation: List<ChatEvent>) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            if (conversation.any { it is T }) return
            Thread.sleep(25L)
        }
        error("Timed out waiting for ${T::class.simpleName} in conversation")
    }

    private fun controller(
        conversation: MutableList<ChatEvent>,
        reasoningCore: LocalReasoningCore,
    ): AgentRunController {
        val traceDir = File.createTempFile("touchpilot-traces", "").apply {
            delete()
            mkdirs()
        }
        return AgentRunController(
            reasoningCore = reasoningCore,
            conversation = conversation,
            currentProviderMode = { AgentProviderMode.LOCAL_ROUTER },
            runtimeWorkingDetail = { "test runtime" },
            runOnUiThread = { block -> block() },
            showChat = {},
            refreshExecutionLog = {},
            refreshStatus = {},
            refreshStepTimeline = { _, _, _ -> },
            workflowTraceStore = WorkflowTraceStore(traceDir),
        )
    }

    private class ClarificationReasoningCore : LocalReasoningCore {
        override fun run(
            task: String,
            timeline: AgentStepTimelineBuilder?,
            listener: AgentEventListener,
            cancellationSignal: AtomicBoolean,
        ): AgentRunResult {
            return clarificationNeededResult()
        }

        override fun replayWorkflow(
            definition: dev.touchpilot.app.workflow.WorkflowDefinition,
            parameters: Map<String, String>,
            timeline: AgentStepTimelineBuilder?,
            listener: AgentEventListener,
            cancellationSignal: AtomicBoolean,
        ): AgentRunResult {
            error("not used")
        }
    }
}

private fun clarificationNeededResult(): AgentRunResult {
    return AgentRunResult(
        transcript = "needs clarification",
        finalAnswer = "Which target?",
        events = listOf(
            AgentEvent.Clarification(
                reason = ClarificationReason.MULTIPLE_TARGETS,
                question = "Which target?",
                detail = "Multiple matches found.",
                candidates = listOf(
                    NextStepCandidate(nodeId = "0.1", displayLabel = "Settings"),
                    NextStepCandidate(nodeId = "0.2", displayLabel = "Wi-Fi Settings"),
                ),
                tool = "tap",
            )
        ),
        stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
        stopMessage = "TouchPilot needs clarification before continuing.",
    )
}
