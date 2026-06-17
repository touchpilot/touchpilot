package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
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
    fun replaysAllStepsAndEmitsWorkflowLifecycleEvents() {
        val definition = WorkflowDefinition(
            id = "open-settings",
            title = "Open Settings",
            parameters = listOf(WorkflowParameter(name = "target", default = "Settings")),
            steps = listOf(
                step("open_app", args = mapOf("target" to "{target}"), id = "step-1"),
                step("observe_screen", id = "step-2"),
            ),
        )

        val result = engine(
            results = listOf(
                ToolResult(ok = true, message = "opened Settings"),
                ToolResult(ok = true, message = "Settings screen"),
            ),
        ).replay(definition)

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertTrue(result.finalAnswer.orEmpty().contains("completed successfully"))
        assertEquals(
            listOf(AgentStepStatus.OK, AgentStepStatus.OK, AgentStepStatus.STOPPED),
            result.steps.map { it.status },
        )
        assertIs<AgentEvent.WorkflowPolicyPreview>(result.events.first { it is AgentEvent.WorkflowPolicyPreview })
        val firstRequest = result.events.filterIsInstance<AgentEvent.ToolRequested>().first()
        assertEquals("open_app", firstRequest.tool)
        assertEquals("Settings", firstRequest.args["target"])
        assertEquals(ToolSource.WORKFLOW_REPLAY, firstRequest.source)
    }

    @Test
    fun completesWorkflowWhenEachStepVerifies() {
        val workflow = sampleWorkflow()
        val tools = FakeLoopTools(
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

        val result = verificationEngine(tools = tools, verifier = verifier)
            .replay(workflow)
            .toWorkflowReplayResult(workflow)

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
        val workflow = sampleWorkflow(verifyOnlyFirstStep = true)
        val tools = FakeLoopTools(
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

        val runResult = verificationEngine(tools = tools, verifier = verifier).replay(workflow)
        val result = runResult.toWorkflowReplayResult(workflow)

        assertEquals(AgentStepStopReason.VERIFICATION_FAILED, result.stopReason)
        assertEquals(1, result.completedStepCount)
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
        val workflow = sampleWorkflow(verifyOnlyFirstStep = true)
        val tools = FakeLoopTools(
            results = listOf(ToolResult(ok = false, message = "Target not found")),
        )

        val result = verificationEngine(tools = tools)
            .replay(workflow)
            .toWorkflowReplayResult(workflow)

        assertEquals(AgentStepStopReason.EXECUTOR_ERROR, result.stopReason)
        assertEquals(0, result.completedStepCount)
        assertTrue(result.verificationCards.isEmpty())
        assertTrue(result.events.none { it is AgentEvent.WorkflowStepVerificationFailed })
    }

    @Test
    fun skipsVerificationWhenExpectedStateIsAbsent() {
        val workflow = WorkflowDefinition(
            id = "no-verify",
            title = "No verify",
            steps = listOf(
                step("press_home", id = "home"),
            ),
        )
        val tools = FakeLoopTools(results = listOf(ToolResult(ok = true, message = "done")))
        val verifier = FakeVerifier(outcomes = emptyList())

        val result = verificationEngine(tools = tools, verifier = verifier)
            .replay(workflow)
            .toWorkflowReplayResult(workflow)

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
    fun requestsApprovalForMediumRiskToolsUsingLiveScreenContext() {
        var observedScreen = "Home screen"
        val definition = WorkflowDefinition(
            id = "open-settings",
            title = "Open Settings",
            steps = listOf(step("open_app", args = mapOf("target" to "Settings"))),
        )
        var approvalCount = 0
        val replayEngine = WorkflowReplayEngine(
            tools = FakeLoopTools(
                results = listOf(ToolResult(ok = true, message = "opened")),
                activeScreen = { observedScreen },
            ),
            approvalProvider = ToolApprovalProvider {
                approvalCount += 1
                true
            },
            liveContextProvider = { WorkflowLivePolicyContext(activeScreen = observedScreen) },
        )

        val result = replayEngine.replay(definition)

        assertEquals(1, approvalCount)
        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertTrue(result.events.any { it is AgentEvent.ApprovalRequired })
    }

    @Test
    fun stopsReplayWhenApprovalIsDenied() {
        val definition = WorkflowDefinition(
            id = "open-settings",
            title = "Open Settings",
            steps = listOf(step("open_app", args = mapOf("target" to "Settings"))),
        )

        val result = engine(
            results = emptyList(),
            approvalProvider = ToolApprovalProvider { false },
        ).replay(definition)

        assertEquals(AgentStepStopReason.APPROVAL_DENIED, result.stopReason)
        assertTrue(result.events.any { it is AgentEvent.PolicyBlocked })
        assertEquals(0, result.events.count { it is AgentEvent.ToolRunning })
    }

    @Test
    fun stopsReplayWhenPolicyBlocksSensitiveWorkflow() {
        val definition = WorkflowDefinition(
            id = "pay",
            title = "Pay bill",
            steps = listOf(step("tap", args = mapOf("text" to "Pay now"))),
        )

        val result = engine(results = emptyList()).replay(definition)

        assertEquals(AgentStepStopReason.POLICY_BLOCKED, result.stopReason)
        assertTrue(result.events.any { it is AgentEvent.PolicyBlocked })
        assertEquals(0, result.events.count { it is AgentEvent.ToolRunning })
    }

    @Test
    fun warnsAndStopsWhenPreflightMismatchIsRejected() {
        val definition = WorkflowDefinition(
            id = "messages",
            title = "Messages",
            expectedForegroundPackage = "com.example.messages",
            steps = listOf(step("observe_screen")),
        )

        val result = WorkflowReplayEngine(
            tools = FakeLoopTools(results = listOf(ToolResult(ok = true, message = "screen"))),
            approvalProvider = ToolApprovalProvider { false },
            liveContextProvider = {
                WorkflowLivePolicyContext(
                    foregroundApp = ForegroundAppInfo(
                        accessibilityConnected = true,
                        packageName = "com.other.app",
                        appLabel = "Other",
                    ),
                    activeScreen = "Other app",
                )
            },
        ).replay(definition)

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertIs<AgentEvent.WorkflowPreflightWarning>(
            result.events.first { it is AgentEvent.WorkflowPreflightWarning }
        )
        assertEquals(0, result.events.count { it is AgentEvent.ToolRunning })
    }

    @Test
    fun stopsOnToolFailureAndSurfacesClearErrorEvents() {
        val definition = WorkflowDefinition(
            id = "tap-missing",
            title = "Tap Missing",
            steps = listOf(step("tap", args = mapOf("text" to "Missing"))),
        )

        val result = engine(
            results = listOf(ToolResult(ok = false, message = "Target not found")),
            approvalProvider = ToolApprovalProvider { true },
        ).replay(definition)

        assertEquals(AgentStepStopReason.EXECUTOR_ERROR, result.stopReason)
        assertTrue(result.finalAnswer.orEmpty().contains("failed at step 1"))
        assertIs<AgentEvent.ToolFailed>(result.events.first { it is AgentEvent.ToolFailed })
        assertIs<AgentEvent.WorkflowReplayDone>(result.events.first { it is AgentEvent.WorkflowReplayDone }).also {
            assertFalse(it.success)
            assertEquals(0, it.completedSteps)
        }
    }

    @Test
    fun stopsWhenValidationFailsBeforeExecution() {
        val definition = WorkflowDefinition(
            id = "invalid",
            title = "Invalid Tool",
            steps = listOf(step("not_a_real_tool")),
        )

        val result = engine(results = emptyList()).replay(definition)

        assertEquals(AgentStepStopReason.EXECUTOR_ERROR, result.stopReason)
        assertTrue(result.finalAnswer.orEmpty().contains("Unknown tool"))
        assertEquals(listOf(AgentStepStatus.STOPPED), result.steps.map { it.status })
    }

    @Test
    fun stopsWhenCancelledBeforeNextStep() {
        val cancellation = AtomicBoolean(false)
        val definition = WorkflowDefinition(
            id = "two-step",
            title = "Two Step",
            steps = listOf(
                step("observe_screen", id = "step-1"),
                step("observe_screen", id = "step-2"),
            ),
        )
        val tools = FakeLoopTools(
            results = listOf(ToolResult(ok = true, message = "screen one")),
            onExecute = { cancellation.set(true) },
        )

        val result = WorkflowReplayEngine(
            tools = tools,
            cancellationSignal = cancellation,
        ).replay(definition)

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertEquals(listOf(AgentStepStatus.OK, AgentStepStatus.STOPPED), result.steps.map { it.status })
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
            steps += step("press_home", id = "press-home")
        }
        return WorkflowDefinition(
            id = "settings",
            title = "Open Settings",
            steps = steps,
        )
    }

    private fun engine(
        results: List<ToolResult>,
        approvalProvider: ToolApprovalProvider = ToolApprovalProvider { true },
    ): WorkflowReplayEngine {
        return WorkflowReplayEngine(
            tools = FakeLoopTools(results),
            policy = DefaultActionPolicy(),
            approvalProvider = approvalProvider,
            liveContextProvider = { WorkflowLivePolicyContext(activeScreen = "Home screen") },
        )
    }

    private fun verificationEngine(
        tools: FakeLoopTools,
        verifier: WorkflowStepVerifier = FakeVerifier(emptyList()),
    ): WorkflowReplayEngine {
        return WorkflowReplayEngine(
            tools = tools,
            approvalProvider = ToolApprovalProvider { true },
            verifier = verifier,
            policy = AllowAllPolicy,
            liveContextProvider = { WorkflowLivePolicyContext(activeScreen = "Home screen") },
        )
    }

    private class FakeLoopTools(
        private val results: List<ToolResult>,
        private val activeScreen: () -> String = { "Home screen" },
        private val onExecute: () -> Unit = {},
    ) : LocalAgentLoopTools {
        private val queue = ArrayDeque(results)

        override fun observeScreen(): String = activeScreen()

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

    private fun step(
        tool: String,
        args: Map<String, String> = emptyMap(),
        id: String = "step-1",
    ) = WorkflowStep(id = id, tool = tool, args = args)
}
