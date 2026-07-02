package dev.touchpilot.app.security

import dev.touchpilot.app.mcp.LocalExtensionTool

sealed class ExternalCapabilityInvocation {
    data class Ready(
        val target: ExternalCapabilityTarget,
        val requiredFeatureFlags: Set<String> = emptySet(),
    ) : ExternalCapabilityInvocation()

    data class Ambiguous(
        val endpoint: String,
        val extensionNames: List<String>,
    ) : ExternalCapabilityInvocation()
}

object ExternalCapabilityTargetResolver {
    fun resolve(
        endpoint: String,
        extensions: List<LocalExtensionTool>,
        policy: ExternalCapabilityPolicy,
        action: ExternalCapabilityAction,
        permissionStore: ExternalCapabilityPermissionStore,
    ): ExternalCapabilityInvocation {
        val normalized = endpoint.trim()
        if (normalized.isBlank()) {
            return ExternalCapabilityInvocation.Ready(
                target = ExternalCapabilityTarget(
                    kind = ExternalCapabilityKind.MCP_SERVER,
                    endpoint = normalized,
                ),
            )
        }

        val matches = extensions.filter { it.endpoint.trim() == normalized }
        if (matches.isEmpty()) {
            return ExternalCapabilityInvocation.Ready(
                target = ExternalCapabilityTarget(
                    kind = ExternalCapabilityKind.MCP_SERVER,
                    endpoint = normalized,
                ),
            )
        }

        val grantedMatches = matches.filter { extension ->
            val target = extensionTarget(extension)
            permissionStore.findGrant(target)?.allows(action) == true
        }

        val selected = when {
            matches.size == 1 -> matches.single()
            grantedMatches.size == 1 -> grantedMatches.single()
            else -> return ExternalCapabilityInvocation.Ambiguous(
                endpoint = normalized,
                extensionNames = matches.map { it.name }.sorted(),
            )
        }

        return ExternalCapabilityInvocation.Ready(
            target = extensionTarget(selected),
            requiredFeatureFlags = policy.requiredFlagsForExtension(selected.manifest.featureFlags),
        )
    }

    private fun extensionTarget(extension: LocalExtensionTool): ExternalCapabilityTarget {
        return ExternalCapabilityTarget(
            kind = ExternalCapabilityKind.LOCAL_EXTENSION,
            endpoint = extension.endpoint,
            name = extension.name,
        )
    }
}
