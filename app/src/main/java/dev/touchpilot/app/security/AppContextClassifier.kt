package dev.touchpilot.app.security

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo

/**
 * Local app/screen categories used to tighten policy when foreground context
 * makes otherwise-normal tool calls sensitive.
 */
enum class PolicyAppCategory(
    val approvalReason: String,
    val workflowClass: PolicyWorkflowClass
) {
    BANKING(
        approvalReason = "banking or financial app context requires approval",
        workflowClass = PolicyWorkflowClass.PAYMENT
    ),
    CHECKOUT_PAYMENT(
        approvalReason = "checkout or payment screen requires approval",
        workflowClass = PolicyWorkflowClass.PAYMENT
    ),
    MESSAGING(
        approvalReason = "messaging app context requires approval",
        workflowClass = PolicyWorkflowClass.MESSAGE_SEND
    ),
    ACCOUNT_MANAGEMENT(
        approvalReason = "account management screen requires approval",
        workflowClass = PolicyWorkflowClass.ACCOUNT_CHANGE
    ),
    PERMISSIONS(
        approvalReason = "permission settings screen requires approval",
        workflowClass = PolicyWorkflowClass.PERMISSION_CHANGE
    ),
    SECURITY_SETTINGS(
        approvalReason = "security settings screen requires approval",
        workflowClass = PolicyWorkflowClass.SECURITY_SETTINGS
    ),
    DESTRUCTIVE_SETTINGS(
        approvalReason = "destructive settings screen requires approval",
        workflowClass = PolicyWorkflowClass.DELETION
    )
}

/**
 * Classifies foreground app and screen summaries into app-aware policy rules.
 * Uses local context only and only raises caution — it never lowers tool risk.
 */
object AppContextClassifier {
    data class AppContextPattern(
        val needle: String,
        val category: PolicyAppCategory
    )

    val patterns: List<AppContextPattern> = listOf(
        AppContextPattern("bank", PolicyAppCategory.BANKING),
        AppContextPattern("banking", PolicyAppCategory.BANKING),
        AppContextPattern("credit union", PolicyAppCategory.BANKING),
        AppContextPattern("com.chase", PolicyAppCategory.BANKING),
        AppContextPattern("com.wellsfargo", PolicyAppCategory.BANKING),
        AppContextPattern("com.bankofamerica", PolicyAppCategory.BANKING),
        AppContextPattern("checkout", PolicyAppCategory.CHECKOUT_PAYMENT),
        AppContextPattern("payment screen", PolicyAppCategory.CHECKOUT_PAYMENT),
        AppContextPattern("order total", PolicyAppCategory.CHECKOUT_PAYMENT),
        AppContextPattern("cart total", PolicyAppCategory.CHECKOUT_PAYMENT),
        AppContextPattern("messages", PolicyAppCategory.MESSAGING),
        AppContextPattern("sms", PolicyAppCategory.MESSAGING),
        AppContextPattern("whatsapp", PolicyAppCategory.MESSAGING),
        AppContextPattern("telegram", PolicyAppCategory.MESSAGING),
        AppContextPattern("signal", PolicyAppCategory.MESSAGING),
        AppContextPattern("gmail", PolicyAppCategory.MESSAGING),
        AppContextPattern("mail", PolicyAppCategory.MESSAGING),
        AppContextPattern("account settings", PolicyAppCategory.ACCOUNT_MANAGEMENT),
        AppContextPattern("manage account", PolicyAppCategory.ACCOUNT_MANAGEMENT),
        AppContextPattern("delete account", PolicyAppCategory.ACCOUNT_MANAGEMENT),
        AppContextPattern("permission", PolicyAppCategory.PERMISSIONS),
        AppContextPattern("app permissions", PolicyAppCategory.PERMISSIONS),
        AppContextPattern("allow access", PolicyAppCategory.PERMISSIONS),
        AppContextPattern("password", PolicyAppCategory.SECURITY_SETTINGS),
        AppContextPattern("passcode", PolicyAppCategory.SECURITY_SETTINGS),
        AppContextPattern("security settings", PolicyAppCategory.SECURITY_SETTINGS),
        AppContextPattern("two-factor", PolicyAppCategory.SECURITY_SETTINGS),
        AppContextPattern("2fa", PolicyAppCategory.SECURITY_SETTINGS),
        AppContextPattern("factory reset", PolicyAppCategory.DESTRUCTIVE_SETTINGS),
        AppContextPattern("erase all", PolicyAppCategory.DESTRUCTIVE_SETTINGS),
        AppContextPattern("wipe data", PolicyAppCategory.DESTRUCTIVE_SETTINGS)
    )

    fun classify(request: ToolPolicyRequest): List<PolicyAppCategory> {
        val haystack = contextHaystack(request)
        if (haystack.isBlank()) return emptyList()
        return patterns
            .filter { pattern -> pattern.needle in haystack }
            .map { it.category }
            .distinct()
    }

    fun rules(request: ToolPolicyRequest): List<PolicyRule> {
        return classify(request).map { category -> ruleForCategory(category) }
    }

    fun contextHaystack(request: ToolPolicyRequest): String {
        return contextHaystack(request.activeScreen, request.foregroundApp)
    }

    fun contextHaystack(activeScreen: String, foregroundApp: ForegroundAppInfo?): String {
        return buildString {
            append(activeScreen)
            append(' ')
            append(foregroundApp?.appLabel.orEmpty())
            append(' ')
            append(foregroundApp?.packageName.orEmpty())
            append(' ')
            append(foregroundApp?.windowTitle.orEmpty())
            append(' ')
            append(foregroundApp?.activityClass.orEmpty())
        }.lowercase()
    }

    private fun ruleForCategory(category: PolicyAppCategory): PolicyRule {
        return PolicyRule(
            id = "app-${category.name.lowercase()}",
            subject = PolicySubject.APP,
            decision = PolicyDecisionKind.ASK,
            reason = category.approvalReason,
            workflowClass = category.workflowClass,
            riskBand = PolicyRiskBand.HIGH
        )
    }
}
