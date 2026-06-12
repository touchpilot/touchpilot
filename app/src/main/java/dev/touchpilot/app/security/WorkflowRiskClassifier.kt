package dev.touchpilot.app.security

/**
 * Deterministic, local classifier that detects which sensitive
 * [PolicyWorkflowClass] a tool action touches, from the tool name, its
 * arguments, and the active screen/app text.
 *
 * Milestone 7 issue 2: this is a standalone signal producer built on the
 * Safety/Policy v2 model ([PolicyV2Model]). It deliberately does **not** change
 * runtime enforcement — the central policy engine consumes it in a later issue.
 * Today the equivalent detection is string-based and embedded inside
 * [DefaultActionPolicy]; this classifier formalizes and completes it.
 *
 * Safety bias: false negatives are worse than false positives, so detection is
 * intentionally generous and conservative:
 * - when several sensitive classes match, the one with the strictest policy
 *   decision wins (ties keep the first-detected class);
 * - input that looks sensitive but fits no specific class is
 *   [PolicyWorkflowClass.UNKNOWN_SENSITIVE];
 * - plainly non-sensitive input is [PolicyWorkflowClass.GENERAL].
 */
object WorkflowRiskClassifier {

    /**
     * @param workflowClass the strictest sensitive workflow detected (or
     *   [PolicyWorkflowClass.GENERAL] when none matched).
     * @param reason short human-readable reason, consistent with
     *   [PolicyV2Defaults.ruleForWorkflow].
     * @param matched every sensitive class detected, in detection order, so a
     *   later policy engine can build one rule per signal.
     */
    data class WorkflowClassification(
        val workflowClass: PolicyWorkflowClass,
        val reason: String,
        val matched: List<PolicyWorkflowClass>,
    )

    /**
     * @param screenText combined active screen and app/window context text; may
     *   be empty when no screen has been observed.
     */
    fun classify(
        toolName: String,
        args: Map<String, String> = emptyMap(),
        screenText: String = "",
    ): WorkflowClassification {
        // Insertion order is the tie-breaker when decisions are equally strict.
        val matched = LinkedHashSet<PolicyWorkflowClass>()

        // Argument-derived: entering a secret, independent of the screen text.
        if (isSensitiveTextEntry(toolName, args)) {
            matched += PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY
        }

        val haystack = haystackOf(toolName, args, screenText)

        // Sending a message needs both a send-like action and a messaging context.
        if (isMessageSend(toolName, args, haystack)) {
            matched += PolicyWorkflowClass.MESSAGE_SEND
        }

        for ((workflowClass, needles) in KeywordRules) {
            if (needles.any { it in haystack }) {
                matched += workflowClass
            }
        }

        // Conservative fallback: clearly sensitive, but no specific class fit.
        if (matched.isEmpty() && GenericSensitiveMarkers.any { it in haystack }) {
            matched += PolicyWorkflowClass.UNKNOWN_SENSITIVE
        }

        if (matched.isEmpty()) {
            return WorkflowClassification(
                workflowClass = PolicyWorkflowClass.GENERAL,
                reason = "no sensitive workflow detected",
                matched = emptyList(),
            )
        }

        val primary = matched.maxByOrNull { PolicyV2Defaults.decisionForWorkflow(it).precedence }
            ?: PolicyWorkflowClass.GENERAL
        return WorkflowClassification(
            workflowClass = primary,
            reason = PolicyV2Defaults.ruleForWorkflow(primary).reason,
            matched = matched.toList(),
        )
    }

    private fun isSensitiveTextEntry(toolName: String, args: Map<String, String>): Boolean {
        if (toolName != TYPE_TEXT_TOOL) return false
        return SensitiveTextRedactor.containsSensitiveText(args["text"].orEmpty())
    }

    private fun isMessageSend(toolName: String, args: Map<String, String>, haystack: String): Boolean {
        if (toolName != TAP_TOOL) return false
        val tapText = args["text"].orEmpty().trim().lowercase()
        val tapsSend = tapText in SendActions
        return tapsSend && MessagingContexts.any { it in haystack }
    }

    private fun haystackOf(toolName: String, args: Map<String, String>, screenText: String): String {
        return buildString {
            append(toolName)
            append(' ')
            append(args.values.joinToString(separator = " "))
            append(' ')
            append(screenText)
        }.lowercase()
    }

    private const val TAP_TOOL = "tap"
    private const val TYPE_TEXT_TOOL = "type_text"

    private val SendActions = setOf("send", "send message", "submit")

    private val MessagingContexts = listOf(
        "messages", "sms", "whatsapp", "telegram", "signal", "mail", "gmail",
    )

    /**
     * Keyword rules in priority order. All map to a BLOCK decision in
     * [PolicyV2Defaults], so order only breaks ties for the reported primary
     * class (more specific workflows first). Matching is case-insensitive
     * substring matching against [haystackOf]; that mirrors the existing
     * string-based checks and is intentionally biased toward over-matching.
     */
    private val KeywordRules: List<Pair<PolicyWorkflowClass, List<String>>> = listOf(
        PolicyWorkflowClass.ACCOUNT_RECOVERY to listOf(
            "account recovery", "recover account", "recover your account",
            "reset password", "forgot password",
        ),
        PolicyWorkflowClass.PAYMENT to listOf(
            "payment", "pay ", "wire transfer", "transfer money", "send money",
            "bank", "credit card", "debit card",
        ),
        PolicyWorkflowClass.PURCHASE to listOf(
            "purchase", "buy now", "checkout", "place order", "add to cart",
            "complete order",
        ),
        PolicyWorkflowClass.ACCOUNT_CHANGE to listOf(
            "delete account", "close account", "deactivate account",
            "change email", "change phone number",
        ),
        PolicyWorkflowClass.DELETION to listOf(
            "factory reset", "erase all", "delete all", "wipe device",
            "delete permanently",
        ),
        PolicyWorkflowClass.PERMISSION_CHANGE to listOf(
            "grant permission", "allow access", "app permissions",
            "enable accessibility", "permission to",
        ),
        PolicyWorkflowClass.SECURITY_SETTINGS to listOf(
            "security settings", "password", "passcode", "screen lock",
            "two-factor", "2fa", "biometric", "fingerprint",
        ),
    )

    /** High-signal sensitivity markers that do not map to a specific class. */
    private val GenericSensitiveMarkers = listOf(
        "verify your identity", "identity verification", "one-time code",
        "security code", "authentication code",
    )
}
