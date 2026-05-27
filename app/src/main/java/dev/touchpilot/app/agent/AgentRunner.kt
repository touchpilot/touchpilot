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
    private val clarificationDecider: ClarificationDecider = ClarificationDecider()
) {
    fun run(task: String, maxSteps: Int = AgentRunLimits.DefaultMaxSteps): AgentRunResult {
        return run(task, AgentRunLimits(maxSteps = maxSteps))
    }

    fun run(task: String, limits: AgentRunLimits): AgentRunResult {
        return BoundedLocalAgentLoop(
            tools = AndroidLoopTools(toolExecutor),
            approvalProvider = approvalProvider,
            commandProvider = commandProvider,
            skill = skill,
            source = source,
            policy = policy,
            clarificationDecider = clarificationDecider
        ).run(task, limits)
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
