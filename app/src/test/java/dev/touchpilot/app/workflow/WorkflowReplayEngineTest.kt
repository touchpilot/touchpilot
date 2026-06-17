package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventType
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkflowReplayEngineTest {
    @Test
    fun completesWorkflowWhenEachStepVerifies() {
        val tools = FakeReplayTools(
            results = listOf(
                ToolResult(ok = true, message = "opened"),
                ToolResult(ok = true, message = "tapped"),
            ),
        )
        val verifier = FakeVerifier(
            outcomes = listOf(
                WorkflowVerificationOutcome(
                    passed = true,
                    expectedSummary = "Text \"Network\" is present on screen",
                    observedSummary = "visible text includes \"Network\"",
                    reason = "Expected text appeared on screen.",
                )
            ),
        )

        val result = engine(tools = tools, verifier = verifier).replay(sampleWorkflow())

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertEquals(2, result.completedStepCount)
        assertEquals(1, result.verificationCards.size)
        assertTrue(result.verificationCards.single().passed)
        assertIs<AgentEvent.WorkflowStepVerificationPassed>(
            result.events.filterIsInstance<AgentEvent.WorkflowStepVerificationPassed>().single()
        )
        assertEquals(AgentStepType.VERIFY, result.steps[1].type)
    }

    @Test
    fun abortsReplayWhenVerificationFails() {
        val tools = FakeReplayTools(
            results = listOf(ToolResult(ok = true, message = "tapped")),
        )
        val verifier = FakeVerifier(
            outcomes = listOf(
                WorkflowVerificationOutcome(
                    passed = false,
                    expectedSummary = "Text \"Network\" is present on screen",
                    observedSummary = "visible text includes \"Home\"",
                    reason = "Expected state was not reached within 5000ms.",
                )
            ),
        )

        val result = engine(tools = tools, verifier = verifier).replay(sampleWorkflow(verifyOnlyFirstStep = true))

        assertEquals(AgentStepStopReason.VERIFICATION_FAILED, result.stopReason)
        assertEquals(0, result.completedStepCount)
        val failedEvent = result.events.filterIsInstance<AgentEvent.WorkflowStepVerificationFailed>().single()
        assertEquals(1, failedEvent.stepIndex)
        assertTrue(failedEvent.userMessage.contains("Expected"))
        assertTrue(failedEvent.userMessage.contains("Observed"))
        val failedCard = result.verificationCards.single()
        assertFalse(failedCard.passed)
        assertEquals(StepVerificationCardModel.from(failedEvent), failedCard)
        assertEquals(AgentStepStopReason.VERIFICATION_FAILED, result.steps.last().stopReason)
    }

    @Test
    fun abortsReplayWhenToolFailsBeforeVerification() {
        val tools = FakeReplayTools(
            results = listOf(ToolResult(ok = false, message = "Target not found")),
        )

        val result = engine(tools = tools).replay(sampleWorkflow(verifyOnlyFirstStep = true))

        assertEquals(AgentStepStopReason.REPEATED_TOOL_FAILURE, result.stopReason)
        assertEquals(0, result.completedStepCount)
        assertTrue(result.verificationCards.isEmpty())
        assertTrue(result.events.none { it is AgentEvent.WorkflowStepVerificationFailed })
    }

    @Test
    fun skipsVerificationWhenExpectedStateIsAbsent() {
        val tools = FakeReplayTools(
            results = listOf(ToolResult(ok = true, message = "done")),
        )
        val verifier = FakeVerifier(outcomes = emptyList())
        val workflow = WorkflowDefinition(
            id = "no-verify",
            title = "No verify",
            steps = listOf(
                WorkflowStep(id = "home", tool = "press_home", args = emptyMap(), expectedState = null),
            ),
        )

        val result = engine(tools = tools, verifier = verifier).replay(workflow)

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertTrue(result.verificationCards.isEmpty())
        assertTrue(
            result.events.none {
                it is AgentEvent.WorkflowStepVerificationPassed ||
                    it is AgentEvent.WorkflowStepVerificationFailed
            }
        )
    }

    @Test
    fun replaysAllStepsAndEmitsWorkflowLifecycleEvents() {
        val definition = WorkflowDefinition(
            id = "open-settings",
            title = "Open Settings",
            parameters = listOf(WorkflowParameter(name = "target", default = "Settings")),
            steps = listOf(
                WorkflowStep(id = "open-app", tool = "open_app", args = mapOf("target" to "{target}")),
                WorkflowStep(id = "observe", tool = "observe_screen"),
            ),
        )

        val result = lifecycleEngine(
            results = listOf(
                ToolResult(ok = true, message = "opened Settings"),
                ToolResult(ok = true, message = "Settings screen"),
            )
        ).replay(definition)

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertTrue(result.stopMessage.contains("replayed successfully"))
        assertEquals(
            listOf(AgentStepStatus.OK, AgentStepStatus.OK, AgentStepStatus.STOPPED),
            result.steps.map { it.status },
        )
        assertEquals(
            listOf(
                AgentEventType.USER_MESSAGE,
                AgentEventType.WORKFLOW_STEP_STARTED,
                AgentEventType.TOOL_REQUESTED,
                AgentEventType.TOOL_RUNNING,
                AgentEventType.TOOL_SUCCEEDED,
                AgentEventType.WORKFLOW_STEP_COMPLETED,
                AgentEventType.WORKFLOW_STEP_STARTED,
                AgentEventType.TOOL_REQUESTED,
                AgentEventType.TOOL_RUNNING,
                AgentEventType.TOOL_SUCCEEDED,
                AgentEventType.WORKFLOW_STEP_COMPLETED,
                AgentEventType.WORKFLOW_REPLAY_DONE,
                AgentEventType.FINAL_ANSWER,
            ),
            result.events.map { it.type },
        )
        val firstRequest = assertIs<AgentEvent.ToolRequested>(result.events[2])
        assertEquals("open_app", firstRequest.tool)
        assertEquals("Settings", firstRequest.args["target"])
        assertEquals(ToolSource.WORKFLOW_REPLAY, firstRequest.source)
    }

    @Test
    fun stopsOnToolFailureAndSurfacesClearErrorEvents() {
        val definition = WorkflowDefinition(
            id = "tap-missing",
            title = "Tap Missing",
            steps = listOf(
                WorkflowStep(id = "tap", tool = "tap", args = mapOf("text" to "Missing")),
            ),
        )

        val result = lifecycleEngine(
            results = listOf(ToolResult(ok = false, message = "Target not found")),
        ).replay(definition)

        assertEquals(AgentStepStopReason.REPEATED_TOOL_FAILURE, result.stopReason)
        val replayDone = assertIs<AgentEvent.WorkflowReplayDone>(
            result.events.filterIsInstance<AgentEvent.WorkflowReplayDone>().single()
        )
        assertTrue(replayDone.message.contains("failed at step 1"))
        assertIs<AgentEvent.ToolFailed>(result.events[4])
        assertIs<AgentEvent.WorkflowStepCompleted>(result.events[5]).also {
            assertFalse(it.success)
        }
        assertIs<AgentEvent.WorkflowReplayDone>(result.events[6]).also {
            assertFalse(it.success)
            assertEquals(0, it.completedSteps)
        }
    }

    @Test
    fun stopsWhenValidationFailsBeforeExecution() {
        val definition = WorkflowDefinition(
            id = "invalid",
            title = "Invalid Tool",
            steps = listOf(WorkflowStep(id = "invalid", tool = "not_a_real_tool")),
        )

        val result = lifecycleEngine(results = emptyList()).replay(definition)

        assertEquals(AgentStepStopReason.NO_VALID_ACTION, result.stopReason)
        assertTrue(result.stopMessage.contains("Unknown tool"))
    }

    @Test
    fun stopsWhenCancelledBeforeNextStep() {
        val cancellation = AtomicBoolean(false)
        val definition = WorkflowDefinition(
            id = "two-step",
            title = "Two Step",
            steps = listOf(
                WorkflowStep(id = "observe-1", tool = "observe_screen"),
                WorkflowStep(id = "observe-2", tool = "observe_screen"),
            ),
        )
        val tools = FakeLoopTools(
            results = listOf(ToolResult(ok = true, message = "screen one")),
            onExecute = { cancellation.set(true) },
        )

        val result = WorkflowReplayEngine(
            tools = tools,
            approvalProvider = ToolApprovalProvider { true },
            cancellationSignal = cancellation,
        ).replay(definition)

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertIs<AgentEvent.RunCancelled>(result.events.last { it is AgentEvent.RunCancelled })
    }

    private fun sampleWorkflow(verifyOnlyFirstStep: Boolean = false): WorkflowDefinition {
        val steps = mutableListOf(
            WorkflowStep(
                id = "tap-settings",
                tool = "tap",
                args = mapOf("text" to "Settings"),
                expectedState = WorkflowExpectedState(screenTextContains = listOf("Network")),
            ),
        )
        if (!verifyOnlyFirstStep) {
            steps += WorkflowStep(id = "press-home", tool = "press_home", args = emptyMap())
        }
        return WorkflowDefinition(
            id = "settings",
            title = "Open Settings",
            steps = steps,
        )
    }

    private fun engine(
        tools: FakeReplayTools,
        verifier: WorkflowStepVerifier = FakeVerifier(emptyList()),
    ): WorkflowReplayEngine {
        return WorkflowReplayEngine(
            tools = tools,
            approvalProvider = ToolApprovalProvider { true },
            verifier = verifier,
            policy = AllowAllPolicy,
        )
    }

    private fun lifecycleEngine(results: List<ToolResult>): WorkflowReplayEngine {
        return WorkflowReplayEngine(
            tools = FakeLoopTools(results),
            approvalProvider = ToolApprovalProvider { true },
            policy = AllowAllPolicy,
        )
    }

    private class FakeReplayTools(
        private val results: List<ToolResult>,
    ) : LocalAgentLoopTools {
        private var index = 0

        override fun observeScreen(): String = "screen-${index.coerceAtMost(results.lastIndex)}"

        override fun validate(name: String, args: Map<String, String>): String? = null

        override fun findTool(name: String): ToolSpec? = ToolSpec(
            name = name,
            description = "test tool",
            risk = dev.touchpilot.app.tools.ToolRisk.LOW,
            arguments = emptyMap(),
        )

        override fun execute(
            name: String,
            args: Map<String, String>,
            source: ToolSource,
            foregroundApp: ForegroundAppInfo,
        ): ToolResult {
            return results[index++]
        }
    }

    private class FakeLoopTools(
        private val results: List<ToolResult>,
        private val onExecute: () -> Unit = {},
    ) : LocalAgentLoopTools {
        private val queue = ArrayDeque(results)

        override fun observeScreen(): String = "Home screen"

        override fun validate(name: String, args: Map<String, String>): String? {
            return AndroidToolCatalog.validate(name, args)
        }

        override fun findTool(name: String): ToolSpec? = AndroidToolCatalog.find(name)

        override fun execute(
            name: String,
            args: Map<String, String>,
            source: ToolSource,
            foregroundApp: ForegroundAppInfo,
        ): ToolResult {
            onExecute()
            return queue.removeFirst()
        }
    }

    private class FakeVerifier(
        private val outcomes: List<WorkflowVerificationOutcome>,
    ) : WorkflowStepVerifier {
        private var index = 0

        override fun verify(expected: ExpectedState, timeoutMs: Long): WorkflowVerificationOutcome {
            return outcomes[index++]
        }
    }

    private object AllowAllPolicy : ActionPolicy {
        override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
            return PolicyDecision.Allow(reason = "test allow")
        }
    }
}
