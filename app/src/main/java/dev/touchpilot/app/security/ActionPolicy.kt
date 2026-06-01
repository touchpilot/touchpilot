package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec

enum class ToolSource {
    LOCAL_ROUTER,
    LOCAL_MODEL,
    SKILL_SELECTED,
    DIRECT_DEBUG,
    MCP
}

data class ToolPolicyRequest(
    val tool: ToolSpec,
    val args: Map<String, String>,
    val source: ToolSource,
    val activeScreen: String = "",
    val activeSkillId: String? = null
)

sealed class PolicyDecision {
    abstract val reason: String
    abstract val userMessage: String

    data class Allow(
        override val reason: String,
        override val userMessage: String = reason
    ) : PolicyDecision()

    /**
     * The tool requires explicit user approval before execution. This is a **blocking
     * contract**: every caller that evaluates policy must surface an approval gate and
     * must not proceed to execution unless the user explicitly approves. Treating
     * RequireApproval as a no-op is a security defect — it bypasses the consent model
     * for MEDIUM and HIGH risk tools.
     */
    data class RequireApproval(
        override val reason: String,
        override val userMessage: String,
        val dataAffected: String,
        val ifApproved: String
    ) : PolicyDecision()

    data class Deny(
        override val reason: String,
        override val userMessage: String
    ) : PolicyDecision()

    data class Block(
        override val reason: String,
        override val userMessage: String
    ) : PolicyDecision()
}

interface ActionPolicy {
    fun evaluate(request: ToolPolicyRequest): PolicyDecision
}

class DefaultActionPolicy : ActionPolicy {
    override fun evaluate(request: ToolPolicyRequest): PolicyDecision {
        if (request.tool.risk == ToolRisk.LOW) {
            return PolicyDecision.Allow("low risk action")
        }

        val intentHaystack = buildString {
            append(request.tool.name)
            append(' ')
            append(request.args.values.joinToString(separator = " "))
        }.lowercase()

        blockedWorkflow(intentHaystack)?.let { return it }

        val screenAwareHaystack = buildString {
            append(intentHaystack)
            append(' ')
            append(request.activeScreen)
        }.lowercase()

        if (isSensitiveTextEntry(request)) {
            return PolicyDecision.Block(
                reason = "password or secret entry is blocked",
                userMessage = "TouchPilot will not enter passwords, recovery codes, API keys, or other secrets."
            )
        }

        if (isMessageSend(request, screenAwareHaystack)) {
            return approval(
                request,
                reason = "sending a message requires explicit approval",
                dataAffected = "A message or outbound communication may be sent from the current app.",
                ifApproved = "TouchPilot will continue with the requested send action."
            )
        }

        if (request.source == ToolSource.MCP && request.tool.risk != ToolRisk.LOW) {
            return approval(
                request,
                reason = "MCP tools are outside the built-in Android trust boundary",
                dataAffected = "The MCP server may receive tool arguments and affect an external system.",
                ifApproved = "TouchPilot will call the requested MCP tool once."
            )
        }

        return when {
            request.tool.risk == ToolRisk.BLOCKED -> PolicyDecision.Block(
                reason = "tool is marked blocked",
                userMessage = "${request.tool.name} is blocked by policy."
            )
            request.tool.risk == ToolRisk.HIGH || request.tool.risk == ToolRisk.MEDIUM -> approval(
                request,
                reason = "${request.tool.risk.name.lowercase()} risk Android action",
                dataAffected = "The current Android app or screen may be changed.",
                ifApproved = "TouchPilot will run ${request.tool.name} with the shown arguments."
            )
            else -> PolicyDecision.Allow("low risk action")
        }
    }

    private fun blockedWorkflow(haystack: String): PolicyDecision.Block? {
        val blocked = listOf(
            "payment" to "payments are blocked",
            "pay " to "payments are blocked",
            "password" to "password workflows are blocked",
            "passcode" to "password workflows are blocked",
            "account recovery" to "account recovery workflows are blocked",
            "recover account" to "account recovery workflows are blocked",
            "factory reset" to "destructive settings changes are blocked",
            "erase all" to "destructive settings changes are blocked",
            "delete account" to "destructive account changes are blocked",
            "purchase" to "purchases are blocked",
            "buy now" to "purchases are blocked",
            "bank" to "banking or financial actions are blocked",
            "wire transfer" to "banking or financial actions are blocked",
            "transfer money" to "banking or financial actions are blocked"
        )
        val match = blocked.firstOrNull { (needle, _) -> needle in haystack } ?: return null
        return PolicyDecision.Block(
            reason = match.second,
            userMessage = "TouchPilot blocked this request because ${match.second}."
        )
    }

    private fun isSensitiveTextEntry(request: ToolPolicyRequest): Boolean {
        if (request.tool.name != "type_text") return false
        val text = request.args["text"].orEmpty()
        return SensitiveTextRedactor.containsSensitiveText(text)
    }

    private fun isMessageSend(request: ToolPolicyRequest, haystack: String): Boolean {
        if (request.tool.name != "tap") return false
        val tapText = request.args["text"].orEmpty().lowercase()
        val tapsSend = tapText in setOf("send", "send message", "submit")
        val looksLikeMessageApp = listOf("messages", "sms", "whatsapp", "telegram", "signal", "mail", "gmail")
            .any { it in haystack }
        return tapsSend && looksLikeMessageApp
    }

    private fun approval(
        request: ToolPolicyRequest,
        reason: String,
        dataAffected: String,
        ifApproved: String
    ): PolicyDecision.RequireApproval {
        return PolicyDecision.RequireApproval(
            reason = reason,
            userMessage = "Approval required for ${request.tool.name}: $reason.",
            dataAffected = dataAffected,
            ifApproved = ifApproved
        )
    }
}
