package dev.touchpilot.app.agent

import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.requiresManualApproval
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog

class AgentRunner(
    private val toolExecutor: AndroidToolExecutor,
    private val approvalProvider: ToolApprovalProvider
) {
    fun run(task: String, config: ProviderConfig, maxSteps: Int = 4): AgentRunResult {
        val client = OpenAiCompatibleClient(config)
        val transcript = StringBuilder()
        var context = "User task: $task\n\nCurrent screen:\n${toolExecutor.observeScreen()}"

        repeat(maxSteps) { step ->
            transcript.appendLine("Step ${step + 1}")
            val raw = runCatching {
                client.complete(AgentPrompts.systemPrompt(), context)
            }.getOrElse { error ->
                transcript.appendLine("Provider error: ${error.message}")
                return AgentRunResult(transcript.toString(), null)
            }
            transcript.appendLine("Model: $raw")

            val command = runCatching {
                AgentCommandParser.parse(raw)
            }.getOrElse { error ->
                transcript.appendLine("Command parse error: ${error.message}")
                return AgentRunResult(transcript.toString(), null)
            }
            if (command.finalAnswer != null) {
                transcript.appendLine("Final: ${command.finalAnswer}")
                return AgentRunResult(transcript.toString(), command.finalAnswer)
            }

            val toolName = command.tool
            if (toolName == null) {
                transcript.appendLine("No tool or final answer returned.")
                return AgentRunResult(transcript.toString(), null)
            }

            val validationError = toolExecutor.validate(toolName, command.args)
            val spec = AndroidToolCatalog.find(toolName)
            if (validationError != null) {
                transcript.appendLine("Tool validation failed: $validationError")
            } else if (spec != null && spec.requiresManualApproval()) {
                transcript.appendLine("Approval requested for $toolName (${spec.risk}).")
                val approved = approvalProvider.approve(spec, command.args)
                if (!approved) {
                    transcript.appendLine("Tool denied by user: $toolName")
                    ToolExecutionLog.record(
                        name = toolName,
                        args = "risk=${spec.risk}",
                        ok = false,
                        message = "denied by user"
                    )
                    return AgentRunResult(transcript.toString(), null)
                }
                transcript.appendLine("Tool approved by user: $toolName")
            }

            val result = runCatching {
                toolExecutor.execute(toolName, command.args)
            }.getOrElse { error ->
                transcript.appendLine("Tool execution error: ${error.message}")
                context = recoveryContext(task, transcript.toString())
                return@repeat
            }
            transcript.appendLine("Tool result: ${result.ok} ${result.message}")
            if (result.data.isNotEmpty()) {
                transcript.appendLine("Tool data: ${result.data}")
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
                appendLine(verificationScreen)
            }
        }

        return AgentRunResult(transcript.toString(), null)
    }

    private fun recoveryContext(task: String, transcript: String): String {
        return buildString {
            appendLine("User task: $task")
            appendLine("Previous transcript:")
            appendLine(transcript)
            appendLine("Current screen:")
            appendLine(toolExecutor.observeScreen())
            appendLine("Recover from the last error or return a final answer if recovery is unsafe.")
        }
    }
}
