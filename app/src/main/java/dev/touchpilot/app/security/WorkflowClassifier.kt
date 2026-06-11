package dev.touchpilot.app.security

/**
 * Deterministic workflow classification for policy evaluation. Mirrors the
 * intent-only blocked patterns and screen-aware message-send detection used
 * before Policy v2 so existing safety guarantees stay unchanged.
 */
object WorkflowClassifier {
    data class BlockedIntentPattern(
        val needle: String,
        val workflowClass: PolicyWorkflowClass,
        val blockReason: String
    )

    val blockedIntentPatterns: List<BlockedIntentPattern> = listOf(
        BlockedIntentPattern("payment", PolicyWorkflowClass.PAYMENT, "payments are blocked"),
        BlockedIntentPattern("pay ", PolicyWorkflowClass.PAYMENT, "payments are blocked"),
        BlockedIntentPattern("password", PolicyWorkflowClass.SECURITY_SETTINGS, "password workflows are blocked"),
        BlockedIntentPattern("passcode", PolicyWorkflowClass.SECURITY_SETTINGS, "password workflows are blocked"),
        BlockedIntentPattern(
            "account recovery",
            PolicyWorkflowClass.ACCOUNT_RECOVERY,
            "account recovery workflows are blocked"
        ),
        BlockedIntentPattern(
            "recover account",
            PolicyWorkflowClass.ACCOUNT_RECOVERY,
            "account recovery workflows are blocked"
        ),
        BlockedIntentPattern(
            "factory reset",
            PolicyWorkflowClass.DELETION,
            "destructive settings changes are blocked"
        ),
        BlockedIntentPattern(
            "erase all",
            PolicyWorkflowClass.DELETION,
            "destructive settings changes are blocked"
        ),
        BlockedIntentPattern(
            "delete account",
            PolicyWorkflowClass.ACCOUNT_CHANGE,
            "destructive account changes are blocked"
        ),
        BlockedIntentPattern("purchase", PolicyWorkflowClass.PURCHASE, "purchases are blocked"),
        BlockedIntentPattern("buy now", PolicyWorkflowClass.PURCHASE, "purchases are blocked"),
        BlockedIntentPattern("bank", PolicyWorkflowClass.PAYMENT, "banking or financial actions are blocked"),
        BlockedIntentPattern(
            "wire transfer",
            PolicyWorkflowClass.PAYMENT,
            "banking or financial actions are blocked"
        ),
        BlockedIntentPattern(
            "transfer money",
            PolicyWorkflowClass.PAYMENT,
            "banking or financial actions are blocked"
        )
    )

    fun classify(request: ToolPolicyRequest): List<PolicyWorkflowClass> {
        val intentHaystack = intentHaystack(request)
        val screenAwareHaystack = buildString {
            append(intentHaystack)
            append(' ')
            append(request.activeScreen)
        }.lowercase()

        val classes = linkedSetOf<PolicyWorkflowClass>()
        if (isSensitiveTextEntry(request)) {
            classes.add(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY)
        }
        if (isMessageSend(request, screenAwareHaystack)) {
            classes.add(PolicyWorkflowClass.MESSAGE_SEND)
        }
        blockedIntentPatterns
            .filter { pattern -> pattern.needle in intentHaystack }
            .forEach { pattern -> classes.add(pattern.workflowClass) }

        if (classes.isEmpty()) {
            classes.add(PolicyWorkflowClass.GENERAL)
        }
        return classes.toList()
    }

    fun blockedIntentRule(request: ToolPolicyRequest): PolicyRule? {
        val intentHaystack = intentHaystack(request)
        val match = blockedIntentPatterns.firstOrNull { pattern -> pattern.needle in intentHaystack }
            ?: return null
        return PolicyRule(
            id = "workflow-intent-${match.workflowClass.name.lowercase()}",
            subject = PolicySubject.WORKFLOW,
            decision = PolicyV2Defaults.decisionForWorkflow(match.workflowClass),
            reason = match.blockReason,
            workflowClass = match.workflowClass,
            riskBand = PolicyRiskBand.BLOCKED
        )
    }

    private fun intentHaystack(request: ToolPolicyRequest): String {
        return buildString {
            append(request.tool.name)
            append(' ')
            append(request.args.values.joinToString(separator = " "))
        }.lowercase()
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
}
