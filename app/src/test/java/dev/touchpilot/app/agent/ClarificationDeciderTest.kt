package dev.touchpilot.app.agent

import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClarificationDeciderTest {
    private val decider = ClarificationDecider()

    @Test
    fun noToolAndNoFinalAnswerProducesNeedsUserChoice() {
        val nothing = AgentCommand(tool = null, args = emptyMap(), finalAnswer = null)
        val decision = decider.decide(NextStepContext.initial("do something"), nothing)
        val clarify = assertIs<ClarificationDecision.Clarify>(decision)
        assertEquals(ClarificationReason.NEEDS_USER_CHOICE, clarify.clarificationReason)
        assertTrue(clarify.question.isNotBlank())
    }

    @Test
    fun blankToolStringIsTreatedAsNoTool() {
        val blank = AgentCommand(tool = "   ", args = emptyMap(), finalAnswer = "")
        val decision = decider.decide(NextStepContext.initial("x"), blank)
        val clarify = assertIs<ClarificationDecision.Clarify>(decision)
        assertEquals(ClarificationReason.NEEDS_USER_CHOICE, clarify.clarificationReason)
    }

    @Test
    fun firstStepWithValidToolJustContinues() {
        val command = AgentCommand(tool = "open_app", args = mapOf("target" to "Settings"), finalAnswer = null)
        val decision = decider.decide(NextStepContext.initial("open Settings"), command)
        assertIs<ClarificationDecision.Continue>(decision)
    }

    @Test
    fun ambiguousPreviousResultProducesMultipleTargetsClarification() {
        val previous = NextStepContext(
            task = "tap Settings",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "tap", args = mapOf("text" to "Settings"), finalAnswer = null),
            previousToolResult = ToolResult(
                ok = false,
                message = "Ambiguous input target: 3 candidates",
                data = mapOf("candidate_count" to "3"),
            ),
            candidateTargets = listOf(
                NextStepCandidate(nodeId = "0.1", displayLabel = "Settings"),
                NextStepCandidate(nodeId = "0.2", displayLabel = "Wi-Fi Settings"),
            ),
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "Settings"), finalAnswer = null)
        val clarify = assertIs<ClarificationDecision.Clarify>(decider.decide(previous, next))
        assertEquals(ClarificationReason.MULTIPLE_TARGETS, clarify.clarificationReason)
        assertEquals(2, clarify.candidates.size)
        assertContains(clarify.question, "Settings")
    }

    @Test
    fun targetNotFoundResultProducesMissingTargetClarification() {
        val previous = NextStepContext(
            task = "type a search term",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "type_text", args = mapOf("text" to "weather"), finalAnswer = null),
            previousToolResult = ToolResult(
                ok = false,
                message = "Input target not found: no matching node on screen",
                data = mapOf("debug_context" to "Searched 12 nodes"),
            ),
        )
        val next = AgentCommand(tool = "type_text", args = mapOf("text" to "weather"), finalAnswer = null)
        val clarify = assertIs<ClarificationDecision.Clarify>(decider.decide(previous, next))
        assertEquals(ClarificationReason.MISSING_TARGET, clarify.clarificationReason)
        assertTrue(clarify.question.isNotBlank())
    }

    @Test
    fun lowConfidenceSuccessProducesLowConfidenceClarification() {
        val previous = NextStepContext(
            task = "tap whatever",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "type_text", args = mapOf("text" to "x"), finalAnswer = null),
            previousToolResult = ToolResult(
                ok = true,
                message = "typed",
                data = mapOf("confidence" to "0.20"),
            ),
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "Next"), finalAnswer = null)
        val clarify = assertIs<ClarificationDecision.Clarify>(decider.decide(previous, next))
        assertEquals(ClarificationReason.LOW_CONFIDENCE, clarify.clarificationReason)
    }

    @Test
    fun confidentSuccessContinues() {
        val previous = NextStepContext(
            task = "tap whatever",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "type_text", args = mapOf("text" to "x"), finalAnswer = null),
            previousToolResult = ToolResult(
                ok = true,
                message = "typed",
                data = mapOf("confidence" to "0.92"),
            ),
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "Next"), finalAnswer = null)
        assertIs<ClarificationDecision.Continue>(decider.decide(previous, next))
    }

    @Test
    fun ambiguousRequestFailureReasonProducesAmbiguousRequestClarification() {
        val previous = NextStepContext(
            task = "the usual",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "observe_screen", args = emptyMap(), finalAnswer = null),
            previousToolResult = ToolResult(ok = false, message = "tool stopped"),
            failureReason = "Ambiguous request: the local model could not interpret 'the usual'",
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "Next"), finalAnswer = null)
        val clarify = assertIs<ClarificationDecision.Clarify>(decider.decide(previous, next))
        assertEquals(ClarificationReason.AMBIGUOUS_REQUEST, clarify.clarificationReason)
    }

    @Test
    fun unrelatedTransientFailureDoesNotProduceClarification() {
        val previous = NextStepContext(
            task = "go back",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "press_back", args = emptyMap(), finalAnswer = null),
            previousToolResult = ToolResult(ok = false, message = "Unable to perform press_back after 3 attempt(s)"),
        )
        val next = AgentCommand(tool = "press_back", args = emptyMap(), finalAnswer = null)
        assertIs<ClarificationDecision.Continue>(decider.decide(previous, next))
    }

    @Test
    fun policyBlockedResultDoesNotProduceClarification() {
        // A blocked-policy outcome reaches the runner via AgentEvent.PolicyBlocked
        // and stops the loop. If it ever surfaces in a NextStepContext, the
        // clarification decider must NOT reclassify it: blocked != uncertain.
        val previous = NextStepContext(
            task = "transfer money",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "tap", args = mapOf("text" to "Send"), finalAnswer = null),
            previousToolResult = ToolResult(
                ok = false,
                message = "TouchPilot will not run this because banking actions are blocked.",
            ),
            failureReason = "policy=block: banking",
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "Confirm"), finalAnswer = null)
        val decision = decider.decide(previous, next)
        assertIs<ClarificationDecision.Continue>(decision)
    }

    @Test
    fun deciderHonoursCustomLowConfidenceThreshold() {
        val strict = ClarificationDecider(lowConfidenceThreshold = 0.95f)
        val previous = NextStepContext(
            task = "x",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "type_text", args = emptyMap(), finalAnswer = null),
            previousToolResult = ToolResult(ok = true, message = "ok", data = mapOf("confidence" to "0.80")),
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "OK"), finalAnswer = null)
        val clarify = assertIs<ClarificationDecision.Clarify>(strict.decide(previous, next))
        assertEquals(ClarificationReason.LOW_CONFIDENCE, clarify.clarificationReason)
    }

    @Test
    fun thresholdOutOfRangeIsRejected() {
        kotlin.test.assertFails { ClarificationDecider(lowConfidenceThreshold = -0.1f) }
        kotlin.test.assertFails { ClarificationDecider(lowConfidenceThreshold = 1.1f) }
    }

    @Test
    fun clarificationCarriesProvidedCandidatesIntoQuestion() {
        val previous = NextStepContext(
            task = "tap Save",
            stepNumber = 1,
            previousCommand = AgentCommand(tool = "tap", args = mapOf("text" to "Save"), finalAnswer = null),
            previousToolResult = ToolResult(ok = false, message = "Ambiguous input target"),
            candidateTargets = listOf(
                NextStepCandidate(nodeId = "0.1", displayLabel = "Save"),
                NextStepCandidate(nodeId = "0.2", displayLabel = "Save as"),
                NextStepCandidate(nodeId = "0.3", displayLabel = "Save and exit"),
            ),
        )
        val next = AgentCommand(tool = "tap", args = mapOf("text" to "Save"), finalAnswer = null)
        val clarify = assertIs<ClarificationDecision.Clarify>(decider.decide(previous, next))
        assertContains(clarify.question, "Save")
        assertContains(clarify.question, "Save as")
        assertContains(clarify.question, "Save and exit")
    }
}
