package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog

class AgentRunner(
    private val toolExecutor: AndroidToolExecutor,
    private val approvalProvider: ToolApprovalProvider,
    private val commandProvider: AgentCommandProvider,
    private val skill: Skill? = null,
    private val source: ToolSource = ToolSource.LOCAL_ROUTER,
    private val policy: ActionPolicy = DefaultActionPolicy()
) {
    fun run(task: String, maxSteps: Int = 4): AgentRunResult {
        val transcript = StringBuilder()
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage(task))
        var context = "User task: $task\n\nCurrent screen:\n${SensitiveTextRedactor.redact(toolExecutor.observeScreen())}"

        repeat(maxSteps) { step ->
            transcript.appendLine("Step ${step + 1}")
            val raw = runCatching {
                commandProvider.complete(AgentPrompts.systemPrompt(skill), context)
            }.getOrElse { error ->
                transcript.appendLine("Command provider error: ${error.message}")
                return AgentRunResult(transcript.toString(), null, events)
            }
            transcript.appendLine("Model: $raw")

            val command = runCatching {
                AgentCommandParser.parse(raw)
            }.getOrElse { error ->
                transcript.appendLine("Command parse error: ${error.message}")
                return AgentRunResult(transcript.toString(), null, events)
            }
            if (command.finalAnswer != null) {
                transcript.appendLine("Final: ${command.finalAnswer}")
                events += AgentEvent.FinalAnswer(command.finalAnswer)
                return AgentRunResult(transcript.toString(), command.finalAnswer, events)
            }

            val toolName = command.tool
            if (toolName == null) {
                transcript.appendLine("No tool or final answer returned.")
                return AgentRunResult(transcript.toString(), null, events)
            }
            AgentEvent.toolRequested(command, source)?.let { events += it }

            val validationError = toolExecutor.validate(toolName, command.args)
            val spec = AndroidToolCatalog.find(toolName)
            val allowlistError = validateSkillAllowlist(toolName)
            if (allowlistError != null) {
                transcript.appendLine("Skill allowlist denied tool: $allowlistError")
                ToolExecutionLog.record(
                    name = toolName,
                    args = "skill=${skill?.id.orEmpty()}",
                    ok = false,
                    message = "denied by skill allowlist"
                )
                events += AgentEvent.PolicyBlocked(
                    tool = toolName,
                    reason = "denied by skill allowlist",
                    userMessage = allowlistError
                )
                return AgentRunResult(transcript.toString(), null, events)
            } else if (validationError != null) {
                transcript.appendLine("Tool validation failed: $validationError")
            } else if (spec != null) {
                val decision = policy.evaluate(
                    ToolPolicyRequest(
                        tool = spec,
                        args = command.args,
                        source = source,
                        activeScreen = toolExecutor.observeScreen(),
                        activeSkillId = skill?.id
                    )
                )
                when (decision) {
                    is PolicyDecision.Allow -> {
                        transcript.appendLine("Policy allowed $toolName: ${decision.reason}")
                    }
                    is PolicyDecision.RequireApproval -> {
                        transcript.appendLine("Approval requested for $toolName: ${decision.reason}")
                        val approvalRequest = ToolApprovalRequest(spec, command.args, decision)
                        events += AgentEvent.approvalRequired(approvalRequest)
                        val approved = approvalProvider.approve(approvalRequest)
                        if (!approved) {
                            transcript.appendLine("Tool denied by user: $toolName")
                            ToolExecutionLog.record(
                                name = toolName,
                                args = "risk=${spec.risk}",
                                ok = false,
                                message = "denied by user: ${decision.reason}"
                            )
                            events += AgentEvent.PolicyBlocked(
                                tool = toolName,
                                reason = "denied by user: ${decision.reason}",
                                userMessage = "The user did not approve $toolName."
                            )
                            return AgentRunResult(transcript.toString(), null, events)
                        }
                        transcript.appendLine("Tool approved by user: $toolName")
                    }
                    is PolicyDecision.Deny -> {
                        transcript.appendLine("Policy denied $toolName: ${decision.reason}")
                        ToolExecutionLog.record(toolName, "policy=deny", false, decision.userMessage)
                        AgentEvent.policyBlocked(toolName, decision)?.let { events += it }
                        return AgentRunResult(transcript.toString(), null, events)
                    }
                    is PolicyDecision.Block -> {
                        transcript.appendLine("Policy blocked $toolName: ${decision.reason}")
                        ToolExecutionLog.record(toolName, "policy=block", false, decision.userMessage)
                        AgentEvent.policyBlocked(toolName, decision)?.let { events += it }
                        return AgentRunResult(transcript.toString(), null, events)
                    }
                }
            }

            AgentEvent.toolRunning(command, source)?.let { events += it }
            val result = runCatching {
                toolExecutor.execute(toolName, command.args, source)
            }.getOrElse { error ->
                transcript.appendLine("Tool execution error: ${error.message}")
                events += AgentEvent.ToolFailed(tool = toolName, message = error.message ?: "Tool execution error")
                context = recoveryContext(task, transcript.toString())
                return@repeat
            }
            events += AgentEvent.toolResult(toolName, result)
            transcript.appendLine("Tool result: ${result.ok} ${SensitiveTextRedactor.redact(result.message)}")
            if (result.data.isNotEmpty()) {
                transcript.appendLine("Tool data: ${SensitiveTextRedactor.redact(result.data)}")
            }

            val verificationScreen = if (toolName == "observe_screen") {
                result.message
            } else {
                toolExecutor.observeScreen()
            }
            transcript.appendLine("Verification screen length: ${verificationScreen.length}")

            context = buildString {
                appendLine("User task: $task")
                appendLine("Previous transcript:")
                appendLine(transcript.toString())
                appendLine("Verification screen:")
                appendLine(SensitiveTextRedactor.redact(verificationScreen))
            }
        }

        return AgentRunResult(transcript.toString(), null, events)
    }

    private fun recoveryContext(task: String, transcript: String): String {
        return buildString {
            appendLine("User task: $task")
            appendLine("Previous transcript:")
            appendLine(transcript)
            appendLine("Current screen:")
            appendLine(SensitiveTextRedactor.redact(toolExecutor.observeScreen()))
            appendLine("Recover from the last error or return a final answer if recovery is unsafe.")
        }
    }

    private fun validateSkillAllowlist(toolName: String): String? {
        val activeSkill = skill ?: return null
        if (toolName in activeSkill.allowedTools) return null
        return "$toolName is not allowed by ${activeSkill.title}"
    }
}
