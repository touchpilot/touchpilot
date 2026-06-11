package dev.touchpilot.app.ui.chat

import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest

object ChatToolTextFormatter {
    fun approvalMessage(request: ToolApprovalRequest): String {
        val policy = request.policy
        val redactedArgs = SensitiveTextRedactor.redact(request.args)
        val argsText = if (redactedArgs.isEmpty()) {
            "none"
        } else {
            redactedArgs.entries.joinToString(separator = "\n") { entry ->
                "${entry.key}: ${entry.value.take(MaxApprovalArgLength)}"
            }
        }

        return buildString {
            appendLine(policy.headline.ifBlank { policy.userMessage })
            appendLine()
            if (policy.workflowLabel.isNotBlank()) {
                appendLine("Workflow: ${policy.workflowLabel}")
            }
            appendLine(
                if (policy.riskSummary.isNotBlank()) {
                    "Risk: ${policy.riskSummary}"
                } else {
                    "Tool risk: ${request.tool.risk}"
                }
            )
            appendLine("Tool: ${request.tool.name}")
            appendLine("Description: ${request.tool.description}")
            appendLine("Why approval is needed: ${policy.reason}")
            if (policy.skillContext.isNotBlank()) {
                appendLine("Skill context: ${policy.skillContext}")
            }
            if (policy.cautionNote.isNotBlank()) {
                appendLine("Review carefully: ${policy.cautionNote}")
            }
            appendLine("What may change: ${policy.dataAffected}")
            appendLine("If approved: ${policy.ifApproved}")
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
                append(cardModel.message)
            }
            if (!cardModel.verificationStatus.isNullOrBlank()) {
                appendLine()
                appendLine()
                append("Verification: ")
                append(cardModel.verificationStatus)
                if (!cardModel.verificationReason.isNullOrBlank()) {
                    append(" - ")
                    append(cardModel.verificationReason)
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
}
