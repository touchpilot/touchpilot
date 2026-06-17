package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentStepTimelineBuilder
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolExecutor

class WorkflowReplayRunner(
    private val toolExecutor: AndroidToolExecutor,
    private val approvalProvider: ToolApprovalProvider,
    private val source: ToolSource = ToolSource.LOCAL_ROUTER,
    private val policy: ActionPolicy = dev.touchpilot.app.security.DefaultActionPolicy(),
    private val verifier: WorkflowStepVerifier = WorkflowStateVerifier(),
    private val cancellationSignal: java.util.concurrent.atomic.AtomicBoolean =
        java.util.concurrent.atomic.AtomicBoolean(false),
) {
    fun replay(
        workflow: WorkflowDefinition,
        listener: AgentEventListener = AgentEventListener {},
        timeline: AgentStepTimelineBuilder? = null,
    ): WorkflowReplayResult {
        return WorkflowReplayEngine(
            tools = AndroidLoopTools(toolExecutor),
            approvalProvider = approvalProvider,
            verifier = verifier,
            source = source,
            policy = policy,
            cancellationSignal = cancellationSignal,
        ).replay(
            workflow = workflow,
            listener = listener,
            onStepsUpdated = timeline?.let { builder -> { steps -> builder.replaceAll(steps) } },
        )
    }

    private class AndroidLoopTools(
        private val toolExecutor: AndroidToolExecutor,
    ) : LocalAgentLoopTools {
        override fun observeScreen(): String = toolExecutor.observeScreen()

        override fun foregroundApp(): ForegroundAppInfo = AccessibilityBridge.getForegroundApp()

        override fun validate(name: String, args: Map<String, String>): String? {
            return toolExecutor.validate(name, args)
        }

        override fun findTool(name: String) =
            dev.touchpilot.app.tools.AndroidToolCatalog.find(name)

        override fun execute(
            name: String,
            args: Map<String, String>,
            source: ToolSource,
            foregroundApp: ForegroundAppInfo,
        ) = toolExecutor.execute(name, args, source, foregroundApp)

        override fun recordExecution(name: String, args: String, ok: Boolean, message: String) {
            dev.touchpilot.app.tools.ToolExecutionLog.record(name, args, ok, message)
        }
    }
}
