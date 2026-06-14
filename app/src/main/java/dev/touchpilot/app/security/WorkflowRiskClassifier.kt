package dev.touchpilot.app.security

import dev.touchpilot.app.tools.targets.TypeTextTarget

/**
 * Everything the [WorkflowRiskClassifier] can read when deciding whether a tool
 * action belongs to a sensitive Android workflow. Every field is optional except
 * the tool name so callers can classify with as much (or as little) context as
 * they have — a bare tool/args pair, or a full screen-aware signal.
 *
 * Nothing here changes execution. The classifier only produces a
 * [PolicyWorkflowClass]; the central policy engine (issue #250) decides what to
 * do with it.
 */
data class WorkflowSignal(
    val toolName: String,
    val args: Map<String, String> = emptyMap(),
    /** Foreground app package or launcher label, when known. */
    val activeApp: String = "",
    /** Active screen/window title, when known. */
    val activeScreen: String = "",
    /** Redacted visible screen text, when available. */
    val screenText: String = ""
)

/** A single workflow class the signal matched, with the evidence that matched it. */
data class WorkflowMatch(
    val workflowClass: PolicyWorkflowClass,
    val evidence: String
)

/**
 * Result of classifying a [WorkflowSignal].
 *
 * [primary] is the single class downstream policy should act on. [matches] keeps
 * every class that fired so logs and approval copy can explain *why* a workflow
 * was treated as sensitive instead of only showing the winner.
 */
data class WorkflowClassification(
    val primary: PolicyWorkflowClass,
    val matches: List<WorkflowMatch>,
    val reason: String
) {
    /** True for anything other than an ordinary [PolicyWorkflowClass.GENERAL] action. */
    val isSensitive: Boolean
        get() = primary != PolicyWorkflowClass.GENERAL

    companion object {
        val General = WorkflowClassification(
            primary = PolicyWorkflowClass.GENERAL,
            matches = emptyList(),
            reason = "no sensitive workflow signal"
        )
    }
}

/**
 * Detects sensitive Android workflow classes from a tool call and its context.
 *
 * The classifier is deterministic and pure (no Android dependency) so it is
 * fully unit-testable with fixtures. It builds on the Policy v2 rule model
 * (issue #254): when several classes match, the winner is the one whose default
 * decision in [PolicyV2Defaults] is strictest, breaking ties with a fixed
 * specificity order. This keeps the classifier honest about severity without
 * re-encoding decisions it does not own.
 *
 * Design bias: **false negatives are more dangerous than false positives.** An
 * action that smells sensitive but does not fit a specific class is reported as
 * [PolicyWorkflowClass.UNKNOWN_SENSITIVE] so later policy can still ask or block,
 * rather than silently treating it as general.
 */
class WorkflowRiskClassifier {

    fun classify(signal: WorkflowSignal): WorkflowClassification {
        val tool = signal.toolName.trim().lowercase()
        val haystack = haystackOf(signal)
        val matches = LinkedHashMap<PolicyWorkflowClass, String>()

        fun record(workflowClass: PolicyWorkflowClass, evidence: String) {
            // Keep the first (most specific) evidence per class.
            matches.putIfAbsent(workflowClass, evidence)
        }

        // 1. Sensitive text entry is checked first and from the args directly:
        // a secret never reaches the keyword scan because it is redacted out of
        // any screen context, so the tool + argument shape is the only signal.
        sensitiveTextEntryEvidence(tool, signal)?.let {
            record(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY, it)
        }

        // 2. A tap/click whose label is a "send" affordance inside a messaging
        // surface is the classic send action keyword scanning alone can miss.
        messageSendActionEvidence(tool, signal, haystack)?.let {
            record(PolicyWorkflowClass.MESSAGE_SEND, it)
        }

        // 3. Keyword scan across tool name, args, app, screen, and screen text.
        for ((workflowClass, needles) in KeywordRules) {
            val hit = needles.firstOrNull { it in haystack } ?: continue
            record(workflowClass, "matched \"${hit.trim()}\"")
        }

        // 4. Ambiguous-but-sensitive fallback: a destructive/irreversible marker
        // with no specific class is treated conservatively, never as general.
        if (matches.isEmpty()) {
            AmbiguousMarkers.firstOrNull { it in haystack }?.let {
                record(PolicyWorkflowClass.UNKNOWN_SENSITIVE, "ambiguous sensitive marker \"$it\"")
            }
        }

        if (matches.isEmpty()) {
            return WorkflowClassification.General
        }

        val ordered = matches.entries
            .map { WorkflowMatch(it.key, it.value) }
            .sortedWith(
                compareByDescending<WorkflowMatch> {
                    PolicyV2Defaults.decisionForWorkflow(it.workflowClass).precedence
                }.thenBy { specificityRank(it.workflowClass) }
            )
        val primary = ordered.first()
        return WorkflowClassification(
            primary = primary.workflowClass,
            matches = ordered,
            reason = "${label(primary.workflowClass)}: ${primary.evidence}"
        )
    }

    private fun haystackOf(signal: WorkflowSignal): String {
        return buildString {
            append(signal.toolName)
            append(' ')
            append(signal.args.values.joinToString(" "))
            append(' ')
            append(signal.activeApp)
            append(' ')
            append(signal.activeScreen)
            append(' ')
            append(signal.screenText)
        }.lowercase()
    }

    private fun sensitiveTextEntryEvidence(tool: String, signal: WorkflowSignal): String? {
        if (tool !in TextEntryTools) return null
        val text = signal.args[TypeTextTarget.TextArg].orEmpty()
        if (text.isNotBlank() && SensitiveTextRedactor.containsSensitiveText(text)) {
            return "secret-like text entered via $tool"
        }
        // No raw secret in the value (it may have been redacted upstream), but
        // the field being typed into clearly asks for one. The target label and
        // surrounding screen text are checked so a redacted value still counts.
        val fieldContext = buildString {
            append(signal.args[TypeTextTarget.TargetTextArg].orEmpty())
            append(' ')
            append(signal.args[TypeTextTarget.TargetContentDescriptionArg].orEmpty())
            append(' ')
            append(signal.activeScreen)
            append(' ')
            append(signal.screenText)
        }.lowercase()
        val marker = SecretFieldMarkers.firstOrNull { it in fieldContext } ?: return null
        return "$tool into a \"$marker\" field"
    }

    private fun messageSendActionEvidence(
        tool: String,
        signal: WorkflowSignal,
        haystack: String
    ): String? {
        if (tool != "tap" && tool != "long_press") return null
        val label = signal.args["text"].orEmpty().trim().lowercase()
        if (label !in SendAffordances) return null
        val inMessagingSurface = MessagingSurfaces.any { it in haystack }
        if (!inMessagingSurface) return null
        return "tap \"$label\" in a messaging surface"
    }

    private fun specificityRank(workflowClass: PolicyWorkflowClass): Int {
        val index = SpecificityOrder.indexOf(workflowClass)
        return if (index < 0) SpecificityOrder.size else index
    }

    private fun label(workflowClass: PolicyWorkflowClass): String =
        workflowClass.name.lowercase().replace('_', ' ')

    private companion object {
        val TextEntryTools = setOf("type_text", "clear_text", "focus_input")

        val SendAffordances = setOf(
            "send", "send message", "send sms", "send text", "send now", "submit", "post"
        )

        val MessagingSurfaces = listOf(
            "message", "messages", "sms", "mms", "chat", "whatsapp", "telegram",
            "signal", "messenger", "mail", "gmail", "outlook", "compose", "inbox"
        )

        val SecretFieldMarkers = listOf(
            "password", "passcode", "passphrase", "pin", "secret", "api key",
            "recovery code", "verification code", "one-time code", "otp",
            "card number", "cvv", "cvc", "security code"
        )

        // Most ambiguous-but-clearly-risky phrasing; only used when nothing
        // more specific matched so a sensitive action never falls through to
        // GENERAL.
        val AmbiguousMarkers = listOf(
            "this cannot be undone", "this can't be undone", "cannot be undone",
            "permanently", "irreversible", "this is permanent", "are you sure"
        )

        // Tie-break order when several classes share the same default decision.
        // Earlier = more specific / preferred reason.
        val SpecificityOrder = listOf(
            PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY,
            PolicyWorkflowClass.ACCOUNT_RECOVERY,
            PolicyWorkflowClass.PAYMENT,
            PolicyWorkflowClass.PURCHASE,
            PolicyWorkflowClass.ACCOUNT_CHANGE,
            PolicyWorkflowClass.DELETION,
            PolicyWorkflowClass.SECURITY_SETTINGS,
            PolicyWorkflowClass.PERMISSION_CHANGE,
            PolicyWorkflowClass.MESSAGE_SEND,
            PolicyWorkflowClass.UNKNOWN_SENSITIVE
        )

        // Keyword rules. Keep needles lowercase. A leading/trailing space keeps
        // short verbs ("pay ", "buy ") from matching inside unrelated words
        // ("display", "buying guide" is intentionally still caught by "buy ").
        val KeywordRules: List<Pair<PolicyWorkflowClass, List<String>>> = listOf(
            PolicyWorkflowClass.ACCOUNT_RECOVERY to listOf(
                "account recovery", "recover account", "recover your account",
                "reset password", "reset your password", "forgot password",
                "forgotten password", "recovery code", "recovery email",
                "verify your identity", "identity verification", "restore account"
            ),
            PolicyWorkflowClass.PAYMENT to listOf(
                "payment", "pay now", "pay ", "make a payment", "credit card",
                "debit card", "card number", "cvv", "cvc", "billing", "wallet",
                "send money", "wire transfer", "transfer money", "bank transfer",
                "iban", "routing number", "paypal", "venmo", "google pay"
            ),
            PolicyWorkflowClass.PURCHASE to listOf(
                "purchase", "buy now", "buy ", "add to cart", "place order",
                "place your order", "checkout", "check out", "confirm order",
                "complete order", "subscribe", "start subscription", "in-app purchase"
            ),
            PolicyWorkflowClass.ACCOUNT_CHANGE to listOf(
                "delete account", "close account", "deactivate account",
                "remove account", "manage account", "account settings",
                "change email", "change username", "update profile"
            ),
            PolicyWorkflowClass.DELETION to listOf(
                "delete", "remove ", "erase", "factory reset", "delete all",
                "clear data", "clear all data", "uninstall", "wipe", "empty trash",
                "move to trash"
            ),
            PolicyWorkflowClass.SECURITY_SETTINGS to listOf(
                "security settings", "lock screen", "screen lock", "biometric",
                "fingerprint", "face unlock", "find my device", "encryption",
                "developer options", "device admin", "google play protect",
                "screen pinning", "vpn"
            ),
            PolicyWorkflowClass.PERMISSION_CHANGE to listOf(
                "permission", "allow access", "grant access", "deny access",
                "app permissions", "location permission", "camera permission",
                "microphone permission", "accessibility permission",
                "usage access", "notification access", "special app access"
            ),
            PolicyWorkflowClass.MESSAGE_SEND to listOf(
                "send message", "send a message", "send text", "send a text",
                "send sms", "send email", "send reply", "send your message"
            )
        )
    }
}
