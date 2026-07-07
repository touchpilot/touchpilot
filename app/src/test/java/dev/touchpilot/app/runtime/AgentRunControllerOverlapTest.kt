package dev.touchpilot.app.runtime

import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentRunState
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepTimelineBuilder
import dev.touchpilot.app.agent.LocalReasoningCore
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRunLifecycleTest {
    @Test
    fun isAgentRunInProgressForRunningAndWaitingApprovalOnly() {
        assertTrue(isAgentRunInProgress(AgentRunState.RUNNING))
        assertTrue(isAgentRunInProgress(AgentRunState.WAITING_APPROVAL))
        assertFalse(isAgentRunInProgress(AgentRunState.IDLE))
        assertFalse(isAgentRunInProgress(AgentRunState.COMPLETED))
        assertFalse(isAgentRunInProgress(AgentRunState.WAITING_CLARIFICATION))
    }

    @Test
    fun resolveChatRunTerminalStateUsesWaitingClarification() {
        assertEquals(
            AgentRunState.WAITING_CLARIFICATION,
            resolveChatRunTerminalState(
                cancelled = false,
                runFailed = false,
                stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
            )
        )
    }

    @Test
    fun resolveChatRunTerminalStatePrefersCancelledAndFailed() {
        assertEquals(
            AgentRunState.CANCELLED,
            resolveChatRunTerminalState(
                cancelled = true,
                runFailed = true,
                stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
            )
        )
        assertEquals(
            AgentRunState.FAILED,
            resolveChatRunTerminalState(
                cancelled = false,
                runFailed = true,
                stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
            )
        )
    }

    @Test
    fun resolveWorkflowReplayTerminalStateUsesWaitingClarification() {
        assertEquals(
            AgentRunState.WAITING_CLARIFICATION,
            resolveWorkflowReplayTerminalState(
                cancelled = false,
                runFailed = false,
                stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
            )
        )
        assertEquals(
            AgentRunState.COMPLETED,
            resolveWorkflowReplayTerminalState(
                cancelled = false,
                runFailed = false,
                stopReason = AgentStepStopReason.COMPLETED,
            )
        )
    }

    @Test
    fun chatRunFailedDetectsToolAndPolicyFailures() {
        assertTrue(
            chatRunFailed(
                runOutcomeFailed = false,
                resultEvents = listOf(AgentEvent.ToolFailed(tool = "tap", message = "failed")),
            )
        )
        assertFalse(chatRunFailed(runOutcomeFailed = false, resultEvents = emptyList()))
    }
}

class AgentRunControllerOverlapTest {
    @Test
    fun startFromChatRejectsSecondTaskWhileRunInProgress() {
        val runStarted = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val conversation = mutableListOf<ChatEvent>()
        val controller = controller(
            conversation = conversation,
            reasoningCore = BlockingReasoningCore(
                onRunStarted = { runStarted.countDown() },
                release = releaseRun,
            ),
        )

        controller.startFromChat("first task")
        assertTrue(runStarted.await(2, TimeUnit.SECONDS))
        assertEquals(AgentRunState.RUNNING, controller.runState)

        val userEventsBefore = conversation.count { it is ChatEvent.User }
        controller.startFromChat("second task")

        assertEquals(AgentRunState.RUNNING, controller.runState)
        assertEquals(userEventsBefore, conversation.count { it is ChatEvent.User })
        val rejection = conversation.filterIsInstance<ChatEvent.Agent>().last()
        assertEquals("Run already in progress.", rejection.text)
        assertTrue(rejection.detail.contains("tap Stop"))

        releaseRun.countDown()
        waitForRunToFinish(controller)
    }

    @Test
    fun startWorkflowReplayRejectsWhileRunInProgress() {
        val runStarted = CountDownLatch(1)
        val releaseRun = CountDownLatch(1)
        val conversation = mutableListOf<ChatEvent>()
        val controller = controller(
            conversation = conversation,
            reasoningCore = BlockingReasoningCore(
                onRunStarted = { runStarted.countDown() },
                release = releaseRun,
            ),
        )

        controller.startFromChat("first task")
        assertTrue(runStarted.await(2, TimeUnit.SECONDS))

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
        assertEquals(AgentRunState.RUNNING, controller.runState)
        assertEquals(
            "Run already in progress.",
            conversation.filterIsInstance<ChatEvent.Agent>().last().text
        )

        releaseRun.countDown()
        waitForRunToFinish(controller)
    }

    private fun waitForRunToFinish(controller: AgentRunController) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            if (controller.runState != AgentRunState.RUNNING) return
            Thread.sleep(25L)
        }
        error("Timed out waiting for run to finish; state=${controller.runState}")
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

    private class BlockingReasoningCore(
        private val onRunStarted: () -> Unit,
        private val release: CountDownLatch,
    ) : LocalReasoningCore {
        override fun run(
            task: String,
            timeline: AgentStepTimelineBuilder?,
            listener: AgentEventListener,
            cancellationSignal: AtomicBoolean,
        ): AgentRunResult {
            onRunStarted()
            release.await(5, TimeUnit.SECONDS)
            return AgentRunResult(
                transcript = "done",
                finalAnswer = "Done.",
                stopReason = AgentStepStopReason.COMPLETED,
                stopMessage = "Completed",
            )
        }

        override fun replayWorkflow(
            definition: WorkflowDefinition,
            parameters: Map<String, String>,
            timeline: AgentStepTimelineBuilder?,
            listener: AgentEventListener,
            cancellationSignal: AtomicBoolean,
        ): AgentRunResult {
            error("Unexpected workflow replay in overlap test")
        }
    }
}
