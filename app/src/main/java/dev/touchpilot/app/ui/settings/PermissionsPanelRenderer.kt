package dev.touchpilot.app.ui.settings

import android.app.Activity
import android.widget.LinearLayout
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.logging.DeveloperLogEntry
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillDetailFormatter
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.security.AndroidToolPermissionStore
import dev.touchpilot.app.security.ExternalCapabilityPermissionGrant
import dev.touchpilot.app.security.ExternalCapabilityPermissionStore
import dev.touchpilot.app.security.ExternalCapabilityTarget
import dev.touchpilot.app.security.PermissionCategory
import dev.touchpilot.app.security.PermissionOverview
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.ui.formLabel
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard

class PermissionsPanelRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val skills: List<Skill>,
    private val isSkillEnabled: (String) -> Boolean,
    private val setSkillEnabled: (String, Boolean) -> Unit,
    private val selectedSkillId: () -> String?,
    private val commitSelectedSkill: (String?) -> Unit,
    private val androidToolPermissionStore: AndroidToolPermissionStore,
    private val externalCapabilityPermissionStore: ExternalCapabilityPermissionStore,
    private val refreshSettingsScreen: () -> Unit,
) {
    fun render() {
        val summary = PermissionOverview.buildSummary(
            accessibilityConnected = AccessibilityBridge.isConnected(),
            skills = skills,
            disabledSkillIds = skills.filterNot { isSkillEnabled(it.id) }.map { it.id }.toSet(),
            extensionGrants = externalCapabilityPermissionStore.allGrants(),
            revokedToolCount = androidToolPermissionStore.revokedTools().size,
        )
        contentRoot.addView(
            activity.summaryCard(
                title = "Permission surfaces",
                value = PermissionOverview.hostStatusLine(summary),
                chipText = "${summary.enabledSkillCount} skills",
                chipAccent = summary.revokedToolCount > 0,
            )
        )

        renderAndroidToolsSection()
        renderSkillsSection()
        renderExtensionsSection()
        renderAuditSection()
    }

    private fun renderAndroidToolsSection() {
        contentRoot.addView(activity.formLabel("Android tools"))
        val revoked = androidToolPermissionStore.revokedTools()
        val tools = androidToolPermissionStore.revocableTools()
        if (tools.isEmpty()) {
            contentRoot.addView(activity.timelineCard("No Android tools", "The built-in tool catalog is empty."))
            return
        }
        tools.forEach { tool ->
            val revokedTool = tool.name in revoked
            contentRoot.addView(
                activity.timelineCard(
                    title = AndroidToolPermissionStore.displayLabel(tool.name),
                    body = buildString {
                        appendLine("Allows: ${tool.description}")
                        appendLine("Risk: ${tool.risk.name.lowercase()}")
                        appendLine(
                            if (revokedTool) {
                                "Status: Revoked by User"
                            } else if (AccessibilityBridge.isConnected()) {
                                "Status: Allowed via accessibility + policy"
                            } else {
                                "Status: Requires accessibility service"
                            }
                        )
                    }.trimEnd(),
                    actionHint = if (revokedTool) "Re-enable Android tool" else "Revoke Android tool",
                    onClick = {
                        if (revokedTool) {
                            androidToolPermissionStore.grant(tool.name)
                        } else {
                            androidToolPermissionStore.revoke(tool.name)
                        }
                        refreshSettingsScreen()
                    },
                )
            )
            if (!revokedTool && tool.risk != ToolRisk.LOW) {
                contentRoot.addView(
                    activity.secondaryButton("Revoke ${tool.name.replace('_', ' ')}") {
                        androidToolPermissionStore.revoke(tool.name)
                        refreshSettingsScreen()
                    }
                )
            }
        }
    }

    private fun renderSkillsSection() {
        contentRoot.addView(activity.formLabel("Skills"))
        if (skills.isEmpty()) {
            contentRoot.addView(activity.timelineCard("No skills installed", "Bundled skills will appear here."))
            return
        }
        skills.forEach { skill ->
            val enabled = isSkillEnabled(skill.id)
            contentRoot.addView(
                activity.timelineCard(
                    title = skill.title,
                    body = buildString {
                        appendLine("Allows: ${SkillDetailFormatter.displayDescription(skill)}")
                        appendLine("${skill.allowedTools.size} Android tools in scope")
                        appendLine("Risk: ${SkillDetailFormatter.formatLabel(skill.risk)}")
                        append(
                            if (enabled) {
                                "Granted by: User (enabled on this device)"
                            } else {
                                "Revoked by: User (disabled on this device)"
                            }
                        )
                    },
                    actionHint = if (enabled) "Disable skill" else "Enable skill",
                    onClick = {
                        setSkillEnabled(skill.id, !enabled)
                        if (enabled && selectedSkillId() == skill.id) {
                            commitSelectedSkill(null)
                        }
                        refreshSettingsScreen()
                    },
                )
            )
            if (enabled && skill.risk != SkillRisk.LOW) {
                contentRoot.addView(
                    activity.secondaryButton("Disable ${skill.title}") {
                        setSkillEnabled(skill.id, false)
                        if (selectedSkillId() == skill.id) {
                            commitSelectedSkill(null)
                        }
                        refreshSettingsScreen()
                    }
                )
            }
        }
    }

    private fun renderExtensionsSection() {
        contentRoot.addView(activity.formLabel("Local extensions and MCP"))
        val grants = externalCapabilityPermissionStore.allGrants()
        if (grants.isEmpty()) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "No external grants",
                    body = "MCP servers and local extensions stay denied until you grant them under Settings → MCP.",
                )
            )
            return
        }
        grants.forEach { grant ->
            renderExtensionGrant(grant)
        }
    }

    private fun renderExtensionGrant(grant: ExternalCapabilityPermissionGrant) {
        val target = grant.target
        contentRoot.addView(
            activity.timelineCard(
                title = target.displayLabel(),
                body = buildString {
                    appendLine(
                        "Scope: ${
                            when (grant.target.kind) {
                                dev.touchpilot.app.security.ExternalCapabilityKind.LOCAL_EXTENSION ->
                                    PermissionCategory.LOCAL_EXTENSION.label
                                dev.touchpilot.app.security.ExternalCapabilityKind.MCP_SERVER ->
                                    PermissionCategory.MCP_SERVER.label
                            }
                        }"
                    )
                    appendLine("Granted by: User (this device)")
                    appendLine("Actions:")
                    grant.allowedActions.forEach { action ->
                        appendLine("- ${action.name.lowercase().replace('_', ' ')}")
                    }
                    if (grant.grantedFeatureFlags.isNotEmpty()) {
                        appendLine("Feature flags:")
                        grant.grantedFeatureFlags.sorted().forEach { flag ->
                            appendLine("- $flag")
                        }
                    }
                }.trimEnd(),
                actionHint = "Review extension permissions",
            )
        )
        contentRoot.addView(
            activity.secondaryButton("Revoke ${target.displayLabel()}") {
                externalCapabilityPermissionStore.revoke(
                    ExternalCapabilityTarget(
                        kind = target.kind,
                        endpoint = target.endpoint,
                        name = target.name,
                    )
                )
                refreshSettingsScreen()
            }
        )
    }

    private fun renderAuditSection() {
        contentRoot.addView(activity.formLabel("Permission audit log"))
        val entries = ToolExecutionLog.recentEntries()
            .filter { it.type == "permission" }
            .take(12)
        if (entries.isEmpty()) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "No permission changes yet",
                    body = "Grant, enable, or revoke a permission above to record an audit entry.",
                )
            )
            return
        }
        entries.forEach { entry ->
            contentRoot.addView(permissionAuditRow(entry))
        }
    }

    private fun permissionAuditRow(entry: DeveloperLogEntry): android.view.View {
        return activity.timelineCard(
            title = "${entry.name.replace('_', ' ')} · ${entry.source.replace('_', ' ')}",
            body = buildString {
                appendLine(entry.result)
                if (entry.target.isNotBlank()) {
                    appendLine("Target: ${entry.target}")
                }
                append("Actor: ${entry.actor}")
            },
        )
    }
}
