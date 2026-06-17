package dev.touchpilot.app.agent

import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowReplayEngine
import dev.touchpilot.app.workflow.WorkflowStep
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkflowReplayCoreTest {
    @Test
    fun localReasoningCoreReplaysWorkflowThroughConfiguredEngine() {
        val definition = WorkflowDefinition(
            id = "observe",
            title = "Observe Screen",
            steps = listOf(WorkflowStep(id = "observe", tool = "observe_screen")),
        )
        val core = DefaultLocalReasoningCore(
            invocation = { _, _, _, _, _ -> error("agent invocation must not run") },
            sessionContext = {
                LocalReasoningContext(skill = null, providerMode = AgentProviderMode.LOCAL_ROUTER)
            },
            workflowReplayEngineFactory = { _ ->
                WorkflowReplayEngine(
                    tools = FakeObserveTools(),
                    approvalProvider = dev.touchpilot.app.security.ToolApprovalProvider { true },
                    source = ToolSource.WORKFLOW_REPLAY,
                )
            },
        )

        val result = core.replayWorkflow(definition)

        assertEquals(AgentStepStopReason.COMPLETED, result.stopReason)
        assertIs<AgentEvent.WorkflowReplayDone>(result.events.first { it is AgentEvent.WorkflowReplayDone })
        assertEquals(ToolSource.WORKFLOW_REPLAY, assertIs<AgentEvent.ToolRequested>(result.events[2]).source)
    }

    private class FakeObserveTools : LocalAgentLoopTools {
        override fun observeScreen(): String = "Home screen"

        override fun validate(name: String, args: Map<String, String>): String? {
            return dev.touchpilot.app.tools.AndroidToolCatalog.validate(name, args)
        }

        override fun findTool(name: String): dev.touchpilot.app.tools.ToolSpec? {
            return dev.touchpilot.app.tools.AndroidToolCatalog.find(name)
        }

        override fun execute(
            name: String,
            args: Map<String, String>,
            source: ToolSource,
            foregroundApp: ForegroundAppInfo,
        ): dev.touchpilot.app.tools.ToolResult {
            return dev.touchpilot.app.tools.ToolResult(ok = true, message = "screen")
        }
    }
}
