package dev.touchpilot.app.security

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.tools.ToolSpec

object PermissionOverview {
    data class Summary(
        val accessibilityConnected: Boolean,
        val enabledSkillCount: Int,
        val disabledSkillCount: Int,
        val extensionGrantCount: Int,
        val mcpGrantCount: Int,
        val revokedToolCount: Int,
    )

    fun buildSummary(
        accessibilityConnected: Boolean,
        skills: List<Skill>,
        disabledSkillIds: Set<String>,
        extensionGrants: List<ExternalCapabilityPermissionGrant>,
        revokedToolCount: Int,
    ): Summary {
        val disabled = disabledSkillIds.map { it.lowercase() }.toSet()
        val enabledSkills = skills.count { it.id.lowercase() !in disabled }
        val extensionCount = extensionGrants.count { it.target.kind == ExternalCapabilityKind.LOCAL_EXTENSION }
        val mcpCount = extensionGrants.count { it.target.kind == ExternalCapabilityKind.MCP_SERVER }
        return Summary(
            accessibilityConnected = accessibilityConnected,
            enabledSkillCount = enabledSkills,
            disabledSkillCount = skills.size - enabledSkills,
            extensionGrantCount = extensionCount,
            mcpGrantCount = mcpCount,
            revokedToolCount = revokedToolCount,
        )
    }

    fun hostStatusLine(summary: Summary): String {
        return buildString {
            append(
                if (summary.accessibilityConnected) {
                    "Accessibility: connected"
                } else {
                    "Accessibility: not connected"
                }
            )
            append(" · Skills: ${summary.enabledSkillCount} enabled")
            val external = summary.extensionGrantCount + summary.mcpGrantCount
            if (external > 0) {
                append(" · External: $external granted")
            }
            if (summary.revokedToolCount > 0) {
                append(" · Tools: ${summary.revokedToolCount} revoked")
            }
        }
    }

    fun androidToolEntries(
        tools: List<ToolSpec>,
        revokedTools: Set<String>,
        accessibilityConnected: Boolean,
    ): List<PermissionGrantEntry> {
        return tools.map { tool ->
            val revoked = tool.name in revokedTools
            PermissionGrantEntry(
                category = PermissionCategory.ANDROID_TOOL,
                label = AndroidToolPermissionStore.displayLabel(tool.name),
                allows = tool.description,
                grantedBy = when {
                    revoked -> "Revoked by User"
                    accessibilityConnected -> "Accessibility + policy"
                    else -> "Requires accessibility service"
                },
                revocable = !revoked,
            )
        }
    }

    fun skillEntries(
        skills: List<Skill>,
        disabledSkillIds: Set<String>,
    ): List<PermissionGrantEntry> {
        val disabled = disabledSkillIds.map { it.lowercase() }.toSet()
        return skills.map { skill ->
            val enabled = skill.id.lowercase() !in disabled
            PermissionGrantEntry(
                category = PermissionCategory.SKILL,
                label = skill.title,
                allows = "${skill.allowedTools.size} Android tools · ${skill.description.take(120)}",
                grantedBy = if (enabled) "Enabled by User" else "Disabled by User",
                revocable = enabled,
            )
        }
    }

    fun extensionEntries(
        grants: List<ExternalCapabilityPermissionGrant>,
    ): List<PermissionGrantEntry> {
        return grants.map { grant ->
            val category = when (grant.target.kind) {
                ExternalCapabilityKind.LOCAL_EXTENSION -> PermissionCategory.LOCAL_EXTENSION
                ExternalCapabilityKind.MCP_SERVER -> PermissionCategory.MCP_SERVER
            }
            val actions = grant.allowedActions.joinToString { action ->
                action.name.lowercase().replace('_', ' ')
            }
            val flags = grant.grantedFeatureFlags.sorted().joinToString()
            PermissionGrantEntry(
                category = category,
                label = grant.target.displayLabel(),
                allows = buildString {
                    append("Actions: $actions")
                    if (flags.isNotBlank()) append(" · Flags: $flags")
                },
                grantedBy = "User (this device)",
                revocable = true,
            )
        }
    }
}
