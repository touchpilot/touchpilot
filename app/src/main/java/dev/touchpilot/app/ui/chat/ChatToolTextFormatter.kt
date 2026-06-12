package dev.touchpilot.app.ui.chat

import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest

object ChatToolTextFormatter {
    fun approvalMessage(request: ToolApprovalRequest): String {
        val redactedArgs = SensitiveTextRedactor.redact(request.args)
        val argsText = if (redactedArgs.isEmpty()) {
            "none"
        } else {
            redactedArgs.entries.joinToString(separator = "\n") { entry ->
                "${entry.key}: ${entry.value.take(MaxApprovalArgLength)}"
            }
        }

        return buildString {
            appendLine("Risk: ${request.tool.risk}")
            appendLine("Tool: ${request.tool.name}")
            appendLine("Description: ${request.tool.description}")
            appendLine("Why approval is needed: ${request.policy.reason.redacted()}")
            if (request.policy.skillContext.isNotBlank()) {
                appendLine("Skill context: ${request.policy.skillContext.redacted()}")
            }
            appendLine("Data affected: ${request.policy.dataAffected.redacted()}")
            appendLine("If approved: ${request.policy.ifApproved.redacted()}")
            appendLine()
            appendLine("Arguments:")
            append(argsText)
        }
    }

    fun toolCallBody(cardModel: ToolCallCardModel): String {
        return buildString {
            appendLine("Arguments:")
            append(toolArgs(cardModel.args))
            if (cardModel.message.isNotBlank()) {
                appendLine()
                appendLine()
                append("Result: ")
                append(cardModel.message.redacted())
            }
            if (!cardModel.verificationStatus.isNullOrBlank()) {
                appendLine()
                appendLine()
                append("Verification: ")
                append(cardModel.verificationStatus.redacted())
                if (!cardModel.verificationReason.isNullOrBlank()) {
                    append(" - ")
                    append(cardModel.verificationReason.redacted())
                }
            }
        }
    }

    private fun toolArgs(args: Map<String, String>): String {
        if (args.isEmpty()) return "none"
        return args.entries.joinToString(separator = "\n") { entry ->
            "${entry.key}: ${entry.value.take(MaxToolCardFieldLength)}"
        }
    }

    private const val MaxApprovalArgLength = 500
    private const val MaxToolCardFieldLength = 700

    private fun String?.redacted(): String = SensitiveTextRedactor.redact(this.orEmpty())
}
