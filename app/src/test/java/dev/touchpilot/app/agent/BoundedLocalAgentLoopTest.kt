package dev.touchpilot.app.agent

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BoundedLocalAgentLoopTest {
    @Test
    fun stopsWithSuccessWhenFinalAnswerIsProduced() {
        val result = loop(
            commands = listOf(
                """{"tool":"observe_screen","args":{}}""",
                """{"final":"Done."}"""
            ),
            results = listOf(ToolResult(ok = true, message = "Settings screen"))
        ).run("check settings", AgentRunLimits(maxSteps = 3))

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertEquals("Done.", result.finalAnswer)
        assertEquals(listOf(AgentStepStatus.OK, AgentStepStatus.STOPPED), result.steps.map { it.status })
        assertEquals("observe_screen", result.steps.first().toolCall?.tool)
    }

    @Test
    fun stopsWhenMaxStepsIsReached() {
        val result = loop(
            commands = listOf(
                """{"tool":"observe_screen","args":{}}""",
                """{"tool":"observe_screen","args":{}}"""
            ),
            results = listOf(
                ToolResult(ok = true, message = "screen one"),
                ToolResult(ok = true, message = "screen two")
            )
        ).run("keep observing", AgentRunLimits(maxSteps = 2))

        assertEquals(AgentStepStopReason.MAX_STEPS, result.stopReason)
        assertEquals(2, result.steps.size)
        assertEquals(listOf(AgentStepStatus.OK, AgentStepStatus.OK), result.steps.map { it.status })
    }

    @Test
    fun modelOutputIsRedactedInTranscript() {
        val result = loop(
            commands = listOf("""{"tool":"type_text","args":{"text":"password=hunter2"}}"""),
            results = emptyList()
        ).run("enter password", AgentRunLimits(maxSteps = 2))

        assertEquals(AgentStepStopReason.POLICY_BLOCKED, result.stopReason)
        assertTrue(result.transcript.contains("Model:"))
        assertFalse("hunter2" in result.transcript, "secret leaked in transcript: ${result.transcript}")
    }

    @Test
    fun stopsWhenPolicyBlocksNextAction() {
        val result = loop(
            commands = listOf("""{"tool":"type_text","args":{"text":"password=hunter2"}}"""),
            results = emptyList()
        ).run("enter password", AgentRunLimits(maxSteps = 2))

        assertEquals(AgentStepStopReason.POLICY_BLOCKED, result.stopReason)
        assertEquals(AgentStepStatus.BLOCKED, result.steps.single().status)
        assertEquals("type_text", result.steps.single().toolCall?.tool)
        assertEquals("[REDACTED]", result.steps.single().toolCall?.args?.get("text"))
        assertFalse("hunter2" in result.steps.single().outputSummary)
    }

    @Test
    fun stopsAfterRepeatedToolFailures() {
        val result = loop(
            commands = listOf(
                """{"tool":"observe_screen","args":{}}""",
                """{"tool":"observe_screen","args":{}}"""
            ),
            results = listOf(
                ToolResult(ok = false, message = "screen unavailable"),
                ToolResult(ok = false, message = "screen still unavailable")
            )
        ).run("observe twice", AgentRunLimits(maxSteps = 4, maxConsecutiveFailures = 2))

        assertEquals(AgentStepStopReason.REPEATED_TOOL_FAILURE, result.stopReason)
        assertEquals(2, result.steps.size)
        assertEquals(listOf(AgentStepStatus.FAILED, AgentStepStatus.FAILED), result.steps.map { it.status })
        assertEquals(AgentStepStopReason.REPEATED_TOOL_FAILURE, result.steps.last().stopReason)
    }

    @Test
    fun asksForClarificationWhenCommandHasNoAction() {
        val result = loop(
            commands = listOf("""{"args":{}}"""),
            results = emptyList()
        ).run("do the thing", AgentRunLimits(maxSteps = 2))

        assertEquals(AgentStepStopReason.CLARIFICATION_NEEDED, result.stopReason)
        assertEquals(AgentStepStatus.CLARIFIED, result.steps.single().status)
        assertEquals(AgentStepType.CLARIFY, result.steps.single().type)
        assertIs<AgentEvent.Clarification>(result.events.last())
    }

    @Test
    fun asksForClarificationAfterAmbiguousToolResult() {
        val ambiguous = ToolResult(
            ok = false,
            message = "Ambiguous input target: 2 candidates",
            data = ClarificationFromToolResult.dataFromCandidateLabels(
                listOf("Settings", "Wi-Fi Settings"),
            ),
        )
        val result = loop(
            commands = listOf(
                """{"tool":"tap","args":{"text":"Settings"}}""",
                """{"tool":"tap","args":{"text":"Settings"}}"""
            ),
            results = listOf(
                ambiguous,
                ToolResult(ok = false, message = "unexpected second tool call"),
            )
        ).run("tap Settings", AgentRunLimits(maxSteps = 3, maxConsecutiveFailures = 2))

        assertEquals(AgentStepStopReason.CLARIFICATION_NEEDED, result.stopReason)
        assertEquals(listOf(AgentStepStatus.FAILED, AgentStepStatus.CLARIFIED), result.steps.map { it.status })
        val clarification = assertIs<AgentEvent.Clarification>(result.events.last())
        assertEquals(ClarificationReason.MULTIPLE_TARGETS, clarification.reason)
        assertEquals(2, clarification.candidates.size)
    }

    private fun loop(
        commands: List<String>,
        results: List<ToolResult>
    ): BoundedLocalAgentLoop {
        return BoundedLocalAgentLoop(
            tools = FakeLoopTools(results),
            approvalProvider = ToolApprovalProvider { true },
            commandProvider = SequenceCommandProvider(commands),
            source = ToolSource.LOCAL_ROUTER
        )
    }

    private class SequenceCommandProvider(commands: List<String>) : AgentCommandProvider {
        private val queue = ArrayDeque(commands)

        override fun complete(systemPrompt: String, context: String): String {
            return queue.removeFirstOrNull() ?: """{"final":"No more commands."}"""
        }
    }

    private class FakeLoopTools(results: List<ToolResult>) : LocalAgentLoopTools {
        private val resultQueue = ArrayDeque(results)

        override fun observeScreen(): String = "Home screen"

        override fun validate(name: String, args: Map<String, String>): String? {
            return AndroidToolCatalog.validate(name, args)
        }

        override fun findTool(name: String): ToolSpec? = AndroidToolCatalog.find(name)

        override fun execute(
            name: String,
            args: Map<String, String>,
            source: ToolSource,
            foregroundApp: ForegroundAppInfo
        ): ToolResult {
            return resultQueue.removeFirstOrNull()
                ?: ToolResult(ok = false, message = "No queued tool result for $name")
        }
    }
}
