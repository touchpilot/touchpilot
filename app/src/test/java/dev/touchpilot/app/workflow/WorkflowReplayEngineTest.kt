package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec
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

    private class FakeVerifier(
        private val outcomes: List<WorkflowVerificationOutcome>,
    ) : WorkflowStepVerifier {
        private var index = 0

        override fun verify(expected: ExpectedState, timeoutMs: Long): WorkflowVerificationOutcome {
            return outcomes[index++]
        }
    }

    private object FakeObservation : WorkflowObservation {
        override fun waitForText(text: String, timeoutMs: Long): Boolean = false
        override fun isKeyboardVisible(): Boolean = false
        override fun observeScreenContext() = dev.touchpilot.app.screen.ScreenContext.Empty
    }

    private object AllowAllPolicy : ActionPolicy {
        override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
            return PolicyDecision.Allow(reason = "test allow")
        }
    }
}
