package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec

class AgentRunner(
    private val toolExecutor: AndroidToolExecutor,
    private val approvalProvider: ToolApprovalProvider,
    private val commandProvider: AgentCommandProvider,
    private val skill: Skill? = null,
    private val source: ToolSource = ToolSource.LOCAL_ROUTER,
    private val policy: ActionPolicy = DefaultActionPolicy(),
    private val cancellationSignal: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false),
    private val clarificationDecider: ClarificationDecider = ClarificationDecider()
) {
    fun run(
        task: String,
        maxSteps: Int = AgentRunLimits.DefaultMaxSteps,
        listener: AgentEventListener = AgentEventListener {},
        timeline: AgentStepTimelineBuilder? = null,
        cancellationSignal: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false)
    ): AgentRunResult {
        return run(
            task = task,
            limits = AgentRunLimits(maxSteps = maxSteps),
            listener = listener,
            timeline = timeline,
            cancellationSignal = cancellationSignal
        )
    }

    fun run(task: String, limits: AgentRunLimits): AgentRunResult {
        return run(task, limits, AgentEventListener {}, null, java.util.concurrent.atomic.AtomicBoolean(false))
    }

    fun run(
        task: String,
        limits: AgentRunLimits,
        listener: AgentEventListener,
        timeline: AgentStepTimelineBuilder?,
        cancellationSignal: java.util.concurrent.atomic.AtomicBoolean
    ): AgentRunResult {
        return BoundedLocalAgentLoop(
            tools = AndroidLoopTools(toolExecutor),
            approvalProvider = approvalProvider,
            commandProvider = commandProvider,
            skill = skill,
            source = source,
            policy = policy,
            cancellationSignal = cancellationSignal,
            clarificationDecider = clarificationDecider
        ).run(
            task = task,
            limits = limits,
            listener = listener,
            onStepsUpdated = timeline?.let { builder -> { steps -> builder.replaceAll(steps) } },
            cancellationSignal = cancellationSignal
        )
    }

    private class AndroidLoopTools(
        private val toolExecutor: AndroidToolExecutor
    ) : LocalAgentLoopTools {
        override fun observeScreen(): String = toolExecutor.observeScreen()

        override fun validate(name: String, args: Map<String, String>): String? {
            return toolExecutor.validate(name, args)
        }

        override fun findTool(name: String): ToolSpec? = AndroidToolCatalog.find(name)

        override fun execute(
            name: String,
            args: Map<String, String>,
            source: ToolSource
        ): ToolResult {
            return toolExecutor.execute(name, args, source)
        }

        override fun recordExecution(name: String, args: String, ok: Boolean, message: String) {
            ToolExecutionLog.record(name, args, ok, message)
        }
    }
}
