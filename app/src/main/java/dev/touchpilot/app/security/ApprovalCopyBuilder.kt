package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec

/**
 * Builds risk- and workflow-specific approval copy from structured policy
 * metadata. Copy is generated locally and must stay free of raw secrets.
 */
object ApprovalCopyBuilder {
    fun build(request: ToolPolicyRequest, evaluation: PolicyEvaluation): PolicyDecision.RequireApproval {
        val askRules = evaluation.rules.filter { it.decision == PolicyDecisionKind.ASK }
        val primary = selectPrimaryRule(askRules)
        val appCategory = appCategoryFromRule(primary)
        val workflowLabel = workflowLabel(primary, appCategory)
        val reason = approvalReason(primary, request.tool)
        val dataAffected = dataAffected(primary, appCategory, request.tool)
        val ifApproved = ifApproved(primary, appCategory, request.tool)
        val headline = headline(primary, appCategory, request.tool)
        val riskSummary = riskSummary(primary, request.tool)
        val skillContext = skillContext(request)
        val cautionNote = cautionNote(request, primary, appCategory)

        return PolicyDecision.RequireApproval(
            reason = reason,
            userMessage = "Approval required for ${request.tool.name}: $reason.",
            dataAffected = dataAffected,
            ifApproved = ifApproved,
            skillContext = skillContext,
            headline = headline,
            riskSummary = riskSummary,
            workflowLabel = workflowLabel,
            cautionNote = cautionNote
        )
    }

    private fun selectPrimaryRule(askRules: List<PolicyRule>): PolicyRule {
        return askRules.firstOrNull { it.subject == PolicySubject.WORKFLOW && it.workflowClass == PolicyWorkflowClass.MESSAGE_SEND }
            ?: askRules.firstOrNull { it.subject == PolicySubject.SOURCE }
            ?: askRules.firstOrNull { it.subject == PolicySubject.APP }
            ?: askRules.firstOrNull { it.subject == PolicySubject.TOOL }
            ?: askRules.first()
    }

    private fun appCategoryFromRule(rule: PolicyRule): PolicyAppCategory? {
        if (rule.subject != PolicySubject.APP) return null
        return PolicyAppCategory.entries.firstOrNull { category ->
            rule.id == "app-${category.name.lowercase()}"
        }
    }

    private fun workflowLabel(rule: PolicyRule, appCategory: PolicyAppCategory?): String {
        return when {
            rule.workflowClass == PolicyWorkflowClass.MESSAGE_SEND -> "Message sending"
            rule.subject == PolicySubject.SOURCE -> "External MCP call"
            appCategory != null -> appCategory.workflowLabel()
            rule.subject == PolicySubject.TOOL -> "Android UI action"
            else -> rule.workflowClass.name.lowercase().replace('_', ' ')
        }
    }

    private fun approvalReason(rule: PolicyRule, tool: ToolSpec): String {
        return when (rule.subject) {
            PolicySubject.TOOL -> "${tool.risk.name.lowercase()} risk Android action"
            else -> rule.reason
        }
    }

    private fun headline(rule: PolicyRule, appCategory: PolicyAppCategory?, tool: ToolSpec): String {
        return when {
            rule.workflowClass == PolicyWorkflowClass.MESSAGE_SEND ->
                "Approve sending a message?"
            rule.subject == PolicySubject.SOURCE ->
                "Approve external MCP tool call?"
            appCategory != null ->
                appCategory.approvalHeadline()
            tool.risk == ToolRisk.HIGH ->
                "Approve high-risk action: ${tool.name}?"
            else ->
                "Approve ${tool.name}?"
        }
    }

    private fun dataAffected(
        rule: PolicyRule,
        appCategory: PolicyAppCategory?,
        tool: ToolSpec
    ): String {
        return when {
            rule.workflowClass == PolicyWorkflowClass.MESSAGE_SEND ->
                "A message or outbound communication may be sent from the current app."
            rule.subject == PolicySubject.SOURCE ->
                "The MCP server may receive tool arguments and affect an external system outside TouchPilot's built-in Android tools."
            appCategory != null ->
                appCategory.dataAffected()
            else ->
                "The current Android app or screen may be changed by ${tool.name}."
        }
    }

    private fun ifApproved(rule: PolicyRule, appCategory: PolicyAppCategory?, tool: ToolSpec): String {
        return when {
            rule.workflowClass == PolicyWorkflowClass.MESSAGE_SEND ->
                "TouchPilot will tap or submit the send action with the shown arguments."
            rule.subject == PolicySubject.SOURCE ->
                "TouchPilot will call ${tool.name} on the configured MCP server once."
            appCategory != null ->
                appCategory.ifApproved(tool.name)
            else ->
                "TouchPilot will run ${tool.name} once with the shown arguments."
        }
    }

    private fun riskSummary(rule: PolicyRule, tool: ToolSpec): String {
        val band = if (rule.riskBand != PolicyRiskBand.LOW) {
            rule.riskBand
        } else {
            PolicyRiskBand.fromToolRisk(tool.risk)
        }
        return "${band.name.lowercase().replaceFirstChar { it.uppercase() }} risk · ${tool.risk.name.lowercase()} risk tool"
    }

    private fun skillContext(request: ToolPolicyRequest): String {
        val risk = request.activeSkillRisk ?: return ""
        if (risk == SkillRisk.LOW) return ""
        val title = request.activeSkillTitle?.takeIf { it.isNotBlank() } ?: "the active skill"
        return "Requested under the ${risk.name.lowercase()}-risk skill \"$title\"."
    }

    private fun cautionNote(
        request: ToolPolicyRequest,
        rule: PolicyRule,
        appCategory: PolicyAppCategory?
    ): String {
        val notes = linkedSetOf<String>()
        if (request.tool.risk == ToolRisk.HIGH) {
            notes += "This tool is marked high risk."
        }
        if (request.activeSkillRisk == SkillRisk.HIGH) {
            notes += "The active skill is high risk."
        }
        when {
            rule.workflowClass == PolicyWorkflowClass.MESSAGE_SEND ->
                notes += "This may send a message immediately after approval."
            appCategory == PolicyAppCategory.BANKING || appCategory == PolicyAppCategory.CHECKOUT_PAYMENT ->
                notes += "Financial apps can move money or confirm purchases."
            appCategory == PolicyAppCategory.ACCOUNT_MANAGEMENT ->
                notes += "Account screens can delete data or change sign-in details."
            appCategory == PolicyAppCategory.PERMISSIONS ->
                notes += "Permission changes can expose data or device control."
            appCategory == PolicyAppCategory.SECURITY_SETTINGS ->
                notes += "Security settings can weaken protection on this device."
            appCategory == PolicyAppCategory.DESTRUCTIVE_SETTINGS ->
                notes += "This screen may lead to factory reset or data loss."
            rule.subject == PolicySubject.SOURCE ->
                notes += "MCP tools run outside TouchPilot's built-in Android trust boundary."
        }
        return notes.joinToString(" ")
    }

    private fun PolicyAppCategory.workflowLabel(): String = when (this) {
        PolicyAppCategory.BANKING -> "Banking or financial app"
        PolicyAppCategory.CHECKOUT_PAYMENT -> "Checkout or payment"
        PolicyAppCategory.MESSAGING -> "Messaging app"
        PolicyAppCategory.ACCOUNT_MANAGEMENT -> "Account management"
        PolicyAppCategory.PERMISSIONS -> "App permissions"
        PolicyAppCategory.SECURITY_SETTINGS -> "Security settings"
        PolicyAppCategory.DESTRUCTIVE_SETTINGS -> "Destructive settings"
    }

    private fun PolicyAppCategory.approvalHeadline(): String = when (this) {
        PolicyAppCategory.BANKING -> "Approve action in a banking or financial app?"
        PolicyAppCategory.CHECKOUT_PAYMENT -> "Approve action on a checkout or payment screen?"
        PolicyAppCategory.MESSAGING -> "Approve action in a messaging app?"
        PolicyAppCategory.ACCOUNT_MANAGEMENT -> "Approve account management action?"
        PolicyAppCategory.PERMISSIONS -> "Approve permission-related action?"
        PolicyAppCategory.SECURITY_SETTINGS -> "Approve security settings action?"
        PolicyAppCategory.DESTRUCTIVE_SETTINGS -> "Approve action near destructive settings?"
    }

    private fun PolicyAppCategory.dataAffected(): String = when (this) {
        PolicyAppCategory.BANKING ->
            "Bank balances, payment details, or transfers in the current financial app may be affected."
        PolicyAppCategory.CHECKOUT_PAYMENT ->
            "Cart contents, payment methods, or purchase confirmation on this screen may be affected."
        PolicyAppCategory.MESSAGING ->
            "Draft or sent messages, recipients, or attachments in the current messaging app may be affected."
        PolicyAppCategory.ACCOUNT_MANAGEMENT ->
            "Profile details, linked accounts, or account recovery options may be changed."
        PolicyAppCategory.PERMISSIONS ->
            "App permissions for camera, contacts, storage, or other sensitive access may change."
        PolicyAppCategory.SECURITY_SETTINGS ->
            "Passwords, passcodes, two-factor settings, or other security controls may be changed."
        PolicyAppCategory.DESTRUCTIVE_SETTINGS ->
            "Factory reset, erase, or other irreversible device settings may become reachable."
    }

    private fun PolicyAppCategory.ifApproved(toolName: String): String = when (this) {
        PolicyAppCategory.BANKING,
        PolicyAppCategory.CHECKOUT_PAYMENT ->
            "TouchPilot will run $toolName in the current financial or checkout context."
        PolicyAppCategory.MESSAGING ->
            "TouchPilot will run $toolName in the current messaging app."
        PolicyAppCategory.ACCOUNT_MANAGEMENT ->
            "TouchPilot will run $toolName on the current account management screen."
        PolicyAppCategory.PERMISSIONS ->
            "TouchPilot will run $toolName on the current permission settings screen."
        PolicyAppCategory.SECURITY_SETTINGS ->
            "TouchPilot will run $toolName on the current security settings screen."
        PolicyAppCategory.DESTRUCTIVE_SETTINGS ->
            "TouchPilot will run $toolName near destructive system settings."
    }
}
