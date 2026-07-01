package dev.touchpilot.app.security

/**
 * Trust boundary for capabilities outside the built-in Android tool catalog.
 * Kept separate from [ToolSource] / [PolicyEngine] so MCP and local extensions
 * can be reviewed and audited on their own surface.
 */
enum class ExternalCapabilityKind {
    MCP_SERVER,
    LOCAL_EXTENSION,
}

enum class ExternalCapabilityAction {
    LIST_TOOLS,
    CALL_TOOL,
}

data class ExternalCapabilityTarget(
    val kind: ExternalCapabilityKind,
    val endpoint: String,
    val name: String = "",
) {
    val id: String
        get() = when (kind) {
            ExternalCapabilityKind.MCP_SERVER -> "mcp:${endpoint.trim()}"
            ExternalCapabilityKind.LOCAL_EXTENSION ->
                "ext:${name.trim()}@${endpoint.trim()}"
        }

    fun displayLabel(): String = when (kind) {
        ExternalCapabilityKind.MCP_SERVER -> endpoint.ifBlank { "(unnamed MCP server)" }
        ExternalCapabilityKind.LOCAL_EXTENSION ->
            if (name.isBlank()) endpoint else "$name @ $endpoint"
    }
}

data class ExternalCapabilityPermissionGrant(
    val target: ExternalCapabilityTarget,
    val allowedActions: Set<ExternalCapabilityAction> = emptySet(),
    val grantedFeatureFlags: Set<String> = emptySet(),
) {
    fun allows(action: ExternalCapabilityAction): Boolean = action in allowedActions

    fun allowsFeature(flag: String): Boolean = flag in grantedFeatureFlags
}

enum class ExternalCapabilityPolicyOutcome {
    ALLOW,
    DENY,
}

data class ExternalCapabilityPolicyDecision(
    val outcome: ExternalCapabilityPolicyOutcome,
    val reason: String,
) {
    val isAllowed: Boolean get() = outcome == ExternalCapabilityPolicyOutcome.ALLOW

    fun auditLabel(): String = when (outcome) {
        ExternalCapabilityPolicyOutcome.ALLOW -> "allow"
        ExternalCapabilityPolicyOutcome.DENY -> "deny"
    }
}

data class ExternalCapabilityAuditRecord(
    val action: ExternalCapabilityAction,
    val target: ExternalCapabilityTarget,
    val parameters: Map<String, String>,
    val policyDecision: ExternalCapabilityPolicyDecision,
    val ok: Boolean,
    val message: String,
)
