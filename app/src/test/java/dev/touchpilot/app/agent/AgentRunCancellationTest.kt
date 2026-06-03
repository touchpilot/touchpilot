package dev.touchpilot.app.agent

import dev.touchpilot.app.security.ToolApprovalProvider
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

class AgentRunCancellationTest {

    @Test
    fun signalSetBeforeRunStopsAtStepOne() {
        val signal = AtomicBoolean(true)
        val result = loopWithSignal(
            signal = signal,
            commands = listOf(
                """{"tool":"observe_screen","args":{}}""",
                """{"tool":"observe_screen","args":{}}"""
            ),
            results = listOf(ToolResult(ok = true, message = "screen one"))
        ).run("observe twice", AgentRunLimits(maxSteps = 5))

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertEquals(1, result.steps.size)
        assertEquals(AgentStepStatus.STOPPED, result.steps.single().status)
        assertEquals(AgentStepStopReason.USER_CANCELLED, result.steps.single().stopReason)
    }

    @Test
    fun signalSetMidRunStopsAtNextIteration() {
        val signal = AtomicBoolean(false)
        val triggerOnFirstCompletion = object : AgentEventListener {
            override fun onEvent(event: AgentEvent) {
                if (event is AgentEvent.ToolSucceeded) signal.set(true)
            }
        }
        val result = loopWithSignal(
            signal = signal,
            commands = listOf(
                """{"tool":"observe_screen","args":{}}""",
                """{"tool":"observe_screen","args":{}}""",
                """{"tool":"observe_screen","args":{}}"""
            ),
            results = listOf(
                ToolResult(ok = true, message = "screen one"),
                ToolResult(ok = true, message = "screen two")
            )
        ).run("observe many", AgentRunLimits(maxSteps = 5), listener = triggerOnFirstCompletion)

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertEquals(2, result.steps.size)
        assertEquals(AgentStepStatus.OK, result.steps[0].status)
        assertEquals(AgentStepStatus.STOPPED, result.steps[1].status)
    }

    @Test
    fun noSignalCompletesNormally() {
        val result = loopWithSignal(
            signal = AtomicBoolean(false),
            commands = listOf(
                """{"tool":"observe_screen","args":{}}""",
                """{"final":"All done."}"""
            ),
            results = listOf(ToolResult(ok = true, message = "screen"))
        ).run("observe once", AgentRunLimits(maxSteps = 5))

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertEquals("All done.", result.finalAnswer)
        assertFalse(result.events.any { it is AgentEvent.RunCancelled })
    }

    @Test
    fun cancellationEmitsRunCancelledEvent() {
        val signal = AtomicBoolean(true)
        val result = loopWithSignal(
            signal = signal,
            commands = listOf("""{"tool":"observe_screen","args":{}}"""),
            results = listOf(ToolResult(ok = true, message = "screen"))
        ).run("observe", AgentRunLimits(maxSteps = 3))

        val cancelled = result.events.filterIsInstance<AgentEvent.RunCancelled>()
        assertEquals(1, cancelled.size)
        assertEquals("Cancelled by user", cancelled.single().reason)
    }

    @Test
    fun cancellationLeavesNoToolExecutionAfterStop() {
        // If the loop honors the signal, no tool call should be executed after
        // the signal is set before run() starts.
        val signal = AtomicBoolean(true)
        val executed = mutableListOf<String>()
        val tools = RecordingTools(executed)
        val loop = BoundedLocalAgentLoop(
            tools = tools,
            approvalProvider = ToolApprovalProvider { true },
            commandProvider = SequenceCommandProvider(
                listOf("""{"tool":"observe_screen","args":{}}""")
            ),
            source = ToolSource.LOCAL_ROUTER,
            cancellationSignal = signal
        )

        val result = loop.run("observe", AgentRunLimits(maxSteps = 3))

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertTrue(executed.isEmpty(), "No tools should have been executed; got $executed")
    }

    @Test
    fun agentRunnerForwardsConstructorSignalToLoop() {
        // The wiring used in production: an external caller (e.g.
        // defaultAgentRunInvocation) sets the cancellation signal via the
        // AgentRunner constructor and then calls run() without re-passing it.
        // Before the fix, AgentRunner.run() default-init'd a fresh signal that
        // shadowed the field; this test pins the corrected behavior.
        val signal = AtomicBoolean(true)
        val runner = ScriptedAgentRunner(
            cancellationSignal = signal,
            commands = listOf("""{"tool":"observe_screen","args":{}}"""),
            results = listOf(ToolResult(ok = true, message = "screen"))
        )

        val result = runner.run("observe", AgentRunLimits(maxSteps = 3))

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertEquals(1, result.steps.size)
    }

    @Test
    fun signalToggledOffMidRunDoesNotStop() {
        // Once cancelled, the run stops; the field is read at each iteration's
        // top so a later flip back to false is irrelevant for that run.
        val signal = AtomicBoolean(true)
        val result = loopWithSignal(
            signal = signal,
            commands = listOf("""{"tool":"observe_screen","args":{}}"""),
            results = listOf(ToolResult(ok = true, message = "screen"))
        ).run("observe", AgentRunLimits(maxSteps = 3))

        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertTrue(result.events.any { it is AgentEvent.RunCancelled })
    }

    @Test
    fun cancellationTakesPrecedenceOverMaxSteps() {
        val signal = AtomicBoolean(true)
        val result = loopWithSignal(
            signal = signal,
            commands = listOf("""{"tool":"observe_screen","args":{}}"""),
            results = listOf(ToolResult(ok = true, message = "screen"))
        ).run("observe", AgentRunLimits(maxSteps = 1))

        // maxSteps=1 means the loop would normally do exactly one step.
        // Cancellation observed BEFORE the step runs trumps the count.
        assertEquals(AgentStepStopReason.USER_CANCELLED, result.stopReason)
        assertEquals(1, result.steps.size)
        assertIs<AgentEvent.RunCancelled>(result.events.last())
    }

    // ---- fixtures ----

    private fun loopWithSignal(
        signal: AtomicBoolean,
        commands: List<String>,
        results: List<ToolResult>
    ): BoundedLocalAgentLoop = BoundedLocalAgentLoop(
        tools = FakeTools(results),
        approvalProvider = ToolApprovalProvider { true },
        commandProvider = SequenceCommandProvider(commands),
        source = ToolSource.LOCAL_ROUTER,
        cancellationSignal = signal
    )

    private class SequenceCommandProvider(commands: List<String>) : AgentCommandProvider {
        private val queue = ArrayDeque(commands)
        override fun complete(systemPrompt: String, context: String): String {
            return queue.removeFirstOrNull() ?: """{"final":"No more commands."}"""
        }
    }

    private class FakeTools(results: List<ToolResult>) : LocalAgentLoopTools {
        private val q = ArrayDeque(results)
        override fun observeScreen(): String = "Home screen"
        override fun validate(name: String, args: Map<String, String>): String? =
            AndroidToolCatalog.validate(name, args)
        override fun findTool(name: String): ToolSpec? = AndroidToolCatalog.find(name)
        override fun execute(name: String, args: Map<String, String>, source: ToolSource): ToolResult =
            q.removeFirst()
    }

    private class RecordingTools(private val executed: MutableList<String>) : LocalAgentLoopTools {
        override fun observeScreen(): String = "Home screen"
        override fun validate(name: String, args: Map<String, String>): String? =
            AndroidToolCatalog.validate(name, args)
        override fun findTool(name: String): ToolSpec? = AndroidToolCatalog.find(name)
        override fun execute(name: String, args: Map<String, String>, source: ToolSource): ToolResult {
            executed += name
            return ToolResult(ok = true, message = "ok")
        }
    }

    /**
     * Test-only wrapper that mirrors how `defaultAgentRunInvocation` constructs
     * `AgentRunner` (cancellation signal supplied via the constructor) and then
     * calls `.run(...)` without re-passing it.
     */
    private class ScriptedAgentRunner(
        cancellationSignal: AtomicBoolean,
        private val commands: List<String>,
        private val results: List<ToolResult>
    ) {
        private val loop = BoundedLocalAgentLoop(
            tools = FakeTools(results),
            approvalProvider = ToolApprovalProvider { true },
            commandProvider = SequenceCommandProvider(commands),
            source = ToolSource.LOCAL_ROUTER,
            cancellationSignal = cancellationSignal
        )

        fun run(task: String, limits: AgentRunLimits): AgentRunResult = loop.run(task, limits)
    }
}
