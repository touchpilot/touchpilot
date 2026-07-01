package dev.touchpilot.app.security

import dev.touchpilot.app.mcp.PluginApiManifest

/**
 * Policy layer for MCP servers and local extensions — separate from Android
 * [PolicyEngine] / [ActionPolicy] which govern built-in tools only.
 */
class ExternalCapabilityPolicy(
    private val permissionStore: ExternalCapabilityPermissionStore,
) {
    fun evaluate(
        action: ExternalCapabilityAction,
        target: ExternalCapabilityTarget,
        requiredFeatureFlags: Set<String> = emptySet(),
    ): ExternalCapabilityPolicyDecision {
        if (target.endpoint.isBlank()) {
            return deny("External capability target endpoint is not configured")
        }

        val grant = permissionStore.findGrant(target)
            ?: return deny(
                "No permission grant for ${target.displayLabel()}. " +
                    "Review and grant ${target.kind.name.lowercase()} permissions in Settings > MCP."
            )

        if (!grant.allows(action)) {
            return deny(
                "${action.name.lowercase().replace('_', ' ')} is not granted for ${target.displayLabel()}"
            )
        }

        val missingFlags = requiredFeatureFlags.filterNot { grant.allowsFeature(it) }.toSet()
        if (missingFlags.isNotEmpty()) {
            return deny(
                "Missing granted feature flags for ${target.displayLabel()}: " +
                    missingFlags.sorted().joinToString()
            )
        }

        return ExternalCapabilityPolicyDecision(
            outcome = ExternalCapabilityPolicyOutcome.ALLOW,
            reason = "Granted ${action.name.lowercase().replace('_', ' ')} for ${target.displayLabel()}",
        )
    }

    fun requiredFlagsForExtension(declaredFlags: Map<String, Boolean>): Set<String> {
        return declaredFlags.filterValues { it }.keys.filter { flag ->
            flag in PluginApiManifest.AllKnownFeatureFlags
        }.toSet()
    }

    private fun deny(reason: String): ExternalCapabilityPolicyDecision {
        return ExternalCapabilityPolicyDecision(
            outcome = ExternalCapabilityPolicyOutcome.DENY,
            reason = reason,
        )
    }
}
