package dev.touchpilot.app.ui.settings

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.R
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.logging.DeveloperLogEntry
import dev.touchpilot.app.mcp.ExternalCapabilityInvoker
import dev.touchpilot.app.mcp.ExternalCapabilityInvokeResult
import dev.touchpilot.app.mcp.LocalExtensionTool
import dev.touchpilot.app.mcp.LocalExtensionParseResult
import dev.touchpilot.app.mcp.LocalExtensionToolStore
import dev.touchpilot.app.mcp.PluginApiManifest
import dev.touchpilot.app.security.AesGcmSecretCipher
import dev.touchpilot.app.security.AndroidKeystoreSecretKeyProvider
import dev.touchpilot.app.security.AndroidToolPermissionStore
import dev.touchpilot.app.security.EncryptedSecretStore
import dev.touchpilot.app.security.ExternalCapabilityAction
import dev.touchpilot.app.security.ExternalCapabilityInvocation
import dev.touchpilot.app.security.ExternalCapabilityKind
import dev.touchpilot.app.security.ExternalCapabilityPermissionStore
import dev.touchpilot.app.security.ExternalCapabilityPolicy
import dev.touchpilot.app.security.ExternalCapabilityTarget
import dev.touchpilot.app.security.ExternalCapabilityTargetResolver
import dev.touchpilot.app.security.SharedPreferencesSecretPreferences
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillDetailFormatter
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.DemonstrationStatus
import dev.touchpilot.app.demonstration.formatting.DemonstrationSummaryFormatter
import dev.touchpilot.app.navigation.SettingsPanel
import dev.touchpilot.app.runtime.ToolExecutionController
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.onboarding.DeviceCompatibilitySummary
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.RuntimeIndicator
import dev.touchpilot.app.ui.description
import dev.touchpilot.app.ui.editText
import dev.touchpilot.app.ui.formLabel
import dev.touchpilot.app.ui.label
import dev.touchpilot.app.ui.settingsChipAccent
import dev.touchpilot.app.ui.settingsChipText
import dev.touchpilot.app.ui.settingsDetailBody
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rowButtonParams
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.withMargins
import dev.touchpilot.app.ui.tools.ToolsScreenRenderer
import org.json.JSONObject

class SettingsScreenRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val preferences: SharedPreferences,
    private val skills: List<Skill>,
    private val isLocalSkill: (String) -> Boolean = { false },
    private val localModelRuntime: LiteRtCommandModelRuntime,
    private val toolExecutionController: ToolExecutionController,
    private val activeSettingsPanel: () -> SettingsPanel?,
    private val openSettingsPanel: (SettingsPanel) -> Unit,
    private val closeSettingsPanel: () -> Unit,
    private val isSkillEnabled: (String) -> Boolean,
    private val setSkillEnabled: (String, Boolean) -> Unit,
    private val selectedSkillId: () -> String?,
    private val commitSelectedSkill: (String?) -> Unit,
    private val openSkillDetail: (String) -> Unit,
    private val currentProviderMode: () -> AgentProviderMode,
    private val openAccessibilitySettings: () -> Unit,
    private val openAppInfoSettings: () -> Unit,
    private val openBatteryOptimizationSettings: () -> Unit,
    private val hideKeyboard: (View) -> Unit,
    private val bindKeyboardScrollSpacer: (View) -> Unit,
    private val getFocusSelectorIndex: () -> Int,
    private val setFocusSelectorIndex: (Int) -> Unit,
    private val getLastFocusInputArgs: () -> Map<String, String>?,
    private val setLastFocusInputArgs: (Map<String, String>?) -> Unit,
    private val compatibilitySummary: () -> DeviceCompatibilitySummary,
    private val recordMcpResult: (String) -> Unit,
    private val mcpResult: () -> String,
    private val refreshSettingsScreen: () -> Unit,
    private val demonstrationRecordingEnabled: () -> Boolean = { false },
    private val demonstrationAutoExportEnabled: () -> Boolean = { false },
    private val demonstrationSessionCount: () -> Int = { 0 },
    private val demonstrationSessions: () -> List<DemonstrationSession> = { emptyList() },
    private val demonstrationSummaries: () -> List<String> = { emptyList() },
    private val onDemonstrationRecordingToggled: (Boolean) -> Unit = {},
    private val onDemonstrationAutoExportToggled: (Boolean) -> Unit = {},
    private val onDemonstrationReplayRequested: (String) -> Unit = {},
) {
    /**
     * Encrypted store for the cloud provider API key. The key is encrypted with
     * an Android Keystore-backed AES-256-GCM key before being written to
     * SharedPreferences, and legacy plaintext keys migrate on first read.
     */
    private val secretStore: EncryptedSecretStore by lazy {
        EncryptedSecretStore(
            preferences = SharedPreferencesSecretPreferences(preferences),
            cipher = AesGcmSecretCipher(AndroidKeystoreSecretKeyProvider()),
            onError = { message, error -> Log.w("SettingsSecretStore", message, error) },
        )
    }

    private fun localExtensionToolStore(): LocalExtensionToolStore {
        return LocalExtensionToolStore(
            readJson = { preferences.getString("local_extension_tools", "").orEmpty() },
            writeJson = { preferences.edit().putString("local_extension_tools", it).apply() }
        )
    }

    private fun externalCapabilityPermissionStore(): ExternalCapabilityPermissionStore {
        return ExternalCapabilityPermissionStore(
            readJson = { preferences.getString("external_capability_permissions", "").orEmpty() },
            writeJson = { preferences.edit().putString("external_capability_permissions", it).apply() }
        )
    }

    private fun androidToolPermissionStore(): AndroidToolPermissionStore {
        return AndroidToolPermissionStore(
            readJson = { preferences.getString("android_tool_revocations", "").orEmpty() },
            writeJson = { preferences.edit().putString("android_tool_revocations", it).apply() }
        )
    }

    private fun externalCapabilityInvoker(): ExternalCapabilityInvoker {
        val store = externalCapabilityPermissionStore()
        return ExternalCapabilityInvoker(ExternalCapabilityPolicy(store))
    }

    fun render() {
        val panel = activeSettingsPanel()
        if (panel == null) {
            contentRoot.addView(settingsIntro("Choose a settings area to configure TouchPilot."))
            contentRoot.addView(settingsPanelSwitcher())
            return
        }

        contentRoot.addView(settingsIntro(panel.intro))
        contentRoot.addView(settingsGoBackButton())
        when (panel) {
            SettingsPanel.COMPATIBILITY -> renderCompatibilityPanel()
            SettingsPanel.PERMISSIONS -> renderPermissionsPanel()
            SettingsPanel.SKILLS -> renderSkillsPanel()
            SettingsPanel.TOOLS -> renderToolsPanel()
            SettingsPanel.HELP -> renderHelpPanel()
            SettingsPanel.MCP -> renderMcpPanel()
            SettingsPanel.CLOUD -> renderCloudPanel()
            SettingsPanel.RUNTIME -> renderRuntimePanel()
            SettingsPanel.RECORDING -> renderRecordingPanel()
        }
    }

    private fun renderPermissionsPanel() {
        PermissionsPanelRenderer(
            activity = activity,
            contentRoot = contentRoot,
            skills = skills,
            isSkillEnabled = isSkillEnabled,
            setSkillEnabled = setSkillEnabled,
            selectedSkillId = selectedSkillId,
            commitSelectedSkill = commitSelectedSkill,
            androidToolPermissionStore = androidToolPermissionStore(),
            externalCapabilityPermissionStore = externalCapabilityPermissionStore(),
            refreshSettingsScreen = refreshSettingsScreen,
        ).render()
    }

    private fun renderSkillsPanel() {
        val active = selectedSkill()
        contentRoot.addView(
            activity.summaryCard(
                title = "Active skill",
                value = active?.title ?: "No skill selected",
                chipText = active?.let { SkillDetailFormatter.formatLabel(it.risk) } ?: "none",
                chipAccent = active != null && active.risk != SkillRisk.LOW
            )
        )
        if (active != null) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "Active skill scope",
                    body = buildString {
                        appendLine("Source: ${skillSourceLabel(active)}")
                        appendLine(SkillDetailFormatter.displayDescription(active))
                        appendLine()
                        append("${active.allowedTools.size} allowed tools")
                    },
                    actionHint = "Tap to inspect skill details",
                    onClick = { openSkillDetail(active.id) }
                )
            )
        }

        contentRoot.addView(activity.formLabel("Available skills"))
        contentRoot.addView(skillsPanelIntro())
        if (skills.isEmpty()) {
            contentRoot.addView(activity.timelineCard("Installed skills", "No bundled skills found."))
            return
        }

        skills.forEach { skill ->
            val enabled = isSkillEnabled(skill.id)
            val description = buildString {
                append(SkillDetailFormatter.displayDescription(skill).ifBlank { "No description provided" })
                appendLine()
                append("${skillSourceLabel(skill)} · ${skill.allowedTools.size} allowed tool${if (skill.allowedTools.size == 1) "" else "s"}")
            }
            contentRoot.addView(
                skillSelectRow(
                    title = skill.title,
                    subtitle = description.trimEnd(),
                    badge = SkillDetailFormatter.formatLabel(skill.risk),
                    enabled = enabled,
                    selected = selectedSkillId() == skill.id,
                    onSelect = if (enabled) { { commitSelectedSkill(skill.id) } } else null,
                    onToggleEnabled = {
                        setSkillEnabled(skill.id, !enabled)
                        if (enabled && selectedSkillId() == skill.id) {
                            commitSelectedSkill(null)
                        }
                    },
                    onViewDetails = { openSkillDetail(skill.id) }
                )
            )
        }
        contentRoot.addView(
            skillSelectRow(
                title = "No skill",
                subtitle = "Run TouchPilot without a skill scope",
                badge = null,
                enabled = true,
                selected = selectedSkillId() == null,
                onSelect = { commitSelectedSkill(null) },
                onToggleEnabled = null,
                onViewDetails = null
            )
        )
    }

    private fun skillsPanelIntro(): View {
        return TextView(activity).apply {
            text = "Select a skill to scope tools and prompts. Open details to review risk, allowed tools, and success criteria before enabling."
            textSize = 12f
            setTextColor(Theme.MutedText)
            setPadding(0, 0, 0, activity.dp(8))
        }
    }

    private fun renderRuntimePanel() {
        val mode = currentProviderMode()
        val indicator = RuntimeIndicator(mode, localModelRuntime.status())
        contentRoot.addView(
            activity.summaryCard(
                title = "Current runtime",
                value = mode.label(),
                chipText = indicator.settingsChipText(),
                chipAccent = indicator.settingsChipAccent()
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Runtime status",
                body = indicator.settingsDetailBody()
            )
        )

        contentRoot.addView(activity.formLabel("Runtime mode"))
        AgentProviderMode.values().forEach { option ->
            contentRoot.addView(
                skillSelectRow(
                    title = option.label(),
                    subtitle = option.description(),
                    badge = null,
                    enabled = true,
                    selected = option == mode,
                    onSelect = {
                        preferences.edit().putString("agent_provider_mode", option.name).apply()
                        refreshSettingsScreen()
                    },
                    onToggleEnabled = null,
                    onViewDetails = null
                )
            )
        }

        contentRoot.addView(
            activity.secondaryButton("Open Accessibility Settings") {
                openAccessibilitySettings()
            }
        )
    }

    private fun renderToolsPanel() {
        ToolsScreenRenderer(
            activity = activity,
            contentRoot = contentRoot,
            toolExecutionController = toolExecutionController,
            openAccessibilitySettings = openAccessibilitySettings,
            refreshToolsScreen = refreshSettingsScreen,
            hideKeyboard = hideKeyboard,
            bindKeyboardScrollSpacer = bindKeyboardScrollSpacer,
            getFocusSelectorIndex = getFocusSelectorIndex,
            setFocusSelectorIndex = setFocusSelectorIndex,
            getLastFocusInputArgs = getLastFocusInputArgs,
            setLastFocusInputArgs = setLastFocusInputArgs
        ).render()
    }

    private fun renderHelpPanel() {
        contentRoot.addView(
            activity.summaryCard(
                title = "Known limitations",
                value = "Updated for real-device beta",
                chipText = "v1",
                chipAccent = true,
            )
        )

        contentRoot.addView(
            activity.timelineCard(
                title = "Before filing a bug",
                body = "Use this order: verify your OEM profile, then check known issue and workaround, then file an issue only if no existing entry applies."
            )
        )
        contentRoot.addView(
            activity.formLabel("Common real-device limitations")
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Accessibility tree sparsity",
                body = "Some custom drawables / WebView-heavy screens do not expose full accessibility elements. Retry with `wait_for_idle` and OCR fallback."
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Background service suspension",
                body = "Some OEMs aggressively stop AccessibilityService in background. Disable battery optimization and verify permissions before long automation sessions."
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Settings panel targets",
                body = "Certain OEM `Settings` launchers open a generic panel. Use explicit navigation paths (open app settings manually first) and report launcher package details."
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "If missing",
                body = "Open the Device Compatibility Checklist and append a result log under `docs/compatibility/results/` so this page can be updated."
            )
        )
    }

    private fun renderRecordingPanel() {
        val enabled = demonstrationRecordingEnabled()
        val autoExport = demonstrationAutoExportEnabled()
        val sessionCount = demonstrationSessionCount()

        contentRoot.addView(
            activity.summaryCard(
                title = "Demonstration recording",
                value = if (enabled) "Enabled" else "Disabled",
                chipText = if (enabled) "recording" else "off",
                chipAccent = enabled,
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "About demonstration mode",
                body = buildString {
                    appendLine("When enabled, TouchPilot records each tool call with arguments and screen context before and after every step.")
                    appendLine()
                    appendLine("Captured demonstrations can be exported as JSON or converted to replayable workflows.")
                    appendLine()
                    append("$sessionCount session(s) captured this launch.")
                },
            )
        )
        val summaries = demonstrationSummaries()
        if (summaries.isNotEmpty()) {
            contentRoot.addView(activity.formLabel("Latest summary"))
            contentRoot.addView(
                activity.timelineCard(
                    title = "Captured demonstration",
                    body = summaries.first(),
                )
            )
        }

        val sessions = demonstrationSessions()
        if (sessions.isNotEmpty()) {
            contentRoot.addView(activity.formLabel("Captured demonstrations"))
            sessions.asReversed().forEach { session ->
                val replayable = session.metadata.status == DemonstrationStatus.COMPLETED && session.steps.isNotEmpty()
                contentRoot.addView(
                    activity.timelineCard(
                        title = session.metadata.task.ifBlank { session.sessionId },
                        body = DemonstrationSummaryFormatter.format(session),
                        actionHint = if (replayable) "Replay approved demonstration" else null,
                        onClick = if (replayable) {
                            { onDemonstrationReplayRequested(session.sessionId) }
                        } else {
                            null
                        },
                    )
                )
            }
        }

        contentRoot.addView(activity.formLabel("Recording mode"))
        contentRoot.addView(
            skillSelectRow(
                title = "Demonstration mode",
                subtitle = if (enabled) {
                    "Demonstration capture is live for this run session. You can stop at any time."
                } else {
                    "Enable capture before running an agent task."
                },
                badge = if (enabled) "recording" else "off",
                enabled = true,
                selected = enabled,
                onSelect = null,
                onToggleEnabled = { onDemonstrationRecordingToggled(!enabled) },
                onViewDetails = null,
            )
        )

        if (enabled) {
            contentRoot.addView(activity.formLabel("Export options"))
            contentRoot.addView(
                skillSelectRow(
                    title = "Auto-export after each run",
                    subtitle = "Save demonstration JSON to app storage when a run completes",
                    badge = if (autoExport) "on" else null,
                    enabled = true,
                    selected = autoExport,
                    onSelect = { onDemonstrationAutoExportToggled(true) },
                    onToggleEnabled = null,
                    onViewDetails = null,
                )
            )
            contentRoot.addView(
                skillSelectRow(
                    title = "Keep in memory only",
                    subtitle = "Store demonstrations for this session without writing files",
                    badge = if (!autoExport) "active" else null,
                    enabled = true,
                    selected = !autoExport,
                    onSelect = { onDemonstrationAutoExportToggled(false) },
                    onToggleEnabled = null,
                    onViewDetails = null,
                )
            )
        }
    }

    private fun renderMcpPanel() {
        val savedEndpoint = preferences.getString("mcp_endpoint", "").orEmpty()
        val extensionStore = localExtensionToolStore()
        val permissionStore = externalCapabilityPermissionStore()
        val policy = ExternalCapabilityPolicy(permissionStore)
        val invoker = externalCapabilityInvoker()
        val extensionLoad = extensionStore.load()
        val extensionTools = extensionLoad.tools
        val mcpTarget = ExternalCapabilityTarget(
            kind = ExternalCapabilityKind.MCP_SERVER,
            endpoint = savedEndpoint,
        )
        val mcpGrant = permissionStore.findGrant(mcpTarget)
        contentRoot.addView(
            activity.summaryCard(
                title = "Plugin API",
                value = "Supported api_version ${PluginApiManifest.SUPPORTED_API_VERSION}",
                chipText = "manifest",
                chipAccent = true
            )
        )
        contentRoot.addView(
            activity.summaryCard(
                title = "MCP endpoint",
                value = savedEndpoint.ifBlank { "Not configured" },
                chipText = if (savedEndpoint.isBlank()) "not set" else "configured",
                chipAccent = savedEndpoint.isNotBlank()
            )
        )

        contentRoot.addView(activity.formLabel("Server"))
        val endpointInput = activity.editText("MCP HTTP JSON-RPC endpoint").apply {
            id = R.id.mcp_endpoint_input
            setText(savedEndpoint)
        }
        contentRoot.addView(endpointInput)

        contentRoot.addView(activity.formLabel("MCP server permissions"))
        contentRoot.addView(
            activity.timelineCard(
                title = savedEndpoint.ifBlank { "No MCP endpoint configured" },
                body = buildString {
                    if (savedEndpoint.isBlank()) {
                        appendLine("Set an endpoint above, then review and grant permissions separately from Android tools.")
                    } else {
                        appendLine("Default deny: list tools and call tool require an explicit grant.")
                        appendLine("List tools: ${permissionLabel(mcpGrant?.allows(ExternalCapabilityAction.LIST_TOOLS) == true)}")
                        appendLine("Call tool: ${permissionLabel(mcpGrant?.allows(ExternalCapabilityAction.CALL_TOOL) == true)}")
                    }
                },
                actionHint = if (savedEndpoint.isBlank()) null else "Grant all MCP permissions",
                onClick = if (savedEndpoint.isBlank()) {
                    null
                } else {
                    {
                        permissionStore.grant(
                            target = mcpTarget,
                            actions = setOf(
                                ExternalCapabilityAction.LIST_TOOLS,
                                ExternalCapabilityAction.CALL_TOOL,
                            ),
                        )
                        refreshSettingsScreen()
                    }
                },
            )
        )
        if (mcpGrant != null && savedEndpoint.isNotBlank()) {
            contentRoot.addView(
                activity.secondaryButton("Revoke MCP server permissions") {
                    permissionStore.revoke(mcpTarget)
                    refreshSettingsScreen()
                }
            )
        }

        contentRoot.addView(activity.formLabel("Tool call"))
        val toolInput = activity.editText("MCP tool name").apply { id = R.id.mcp_tool_input }
        val argsInput = activity.editText("MCP tool arguments JSON").apply {
            id = R.id.mcp_args_input
            setSingleLine(false)
            minLines = 3
            setText("{}")
        }
        contentRoot.addView(toolInput)
        contentRoot.addView(argsInput)

        contentRoot.addView(activity.formLabel("Local extension tool"))
        val extensionNameInput = activity.editText("Extension tool name").apply {
            id = R.id.local_extension_tool_name_input
        }
        val extensionDescriptionInput = activity.editText("Tool description").apply {
            id = R.id.local_extension_tool_description_input
        }
        contentRoot.addView(extensionNameInput)
        contentRoot.addView(extensionDescriptionInput)
        contentRoot.addView(
            activity.primaryButton("Register Tool") {
                val manifest = PluginApiManifest(
                    apiVersion = PluginApiManifest.SUPPORTED_API_VERSION,
                    name = extensionNameInput.text.toString().trim(),
                    description = extensionDescriptionInput.text.toString().trim(),
                    endpoint = savedEndpoint,
                    featureFlags = mapOf("network_access" to true),
                )
                when (val result = extensionStore.add(LocalExtensionTool(manifest))) {
                    is LocalExtensionParseResult.Valid -> {
                        val extensionTarget = ExternalCapabilityTarget(
                            kind = ExternalCapabilityKind.LOCAL_EXTENSION,
                            endpoint = manifest.endpoint,
                            name = manifest.name,
                        )
                        recordMcpResult(
                            "Registered ${manifest.name}. Review extension permissions below and grant list/call access, plus any required feature flags, before use."
                        )
                        refreshSettingsScreen()
                    }
                    is LocalExtensionParseResult.Invalid -> {
                        recordMcpResult(
                            buildString {
                                appendLine("Extension manifest rejected for ${result.name}:")
                                result.errors.forEach { appendLine("- $it") }
                                result.recommendedAction?.let {
                                    appendLine()
                                    append("Recommended action: ")
                                    append(it)
                                }
                            }
                        )
                        refreshSettingsScreen()
                    }
                }
            }
        )

        if (extensionLoad.invalid.isNotEmpty()) {
            contentRoot.addView(activity.formLabel("Incompatible extension manifests"))
            extensionLoad.invalid.forEach { invalid ->
                contentRoot.addView(
                    activity.timelineCard(
                        title = "${invalid.name} (incompatible)",
                        body = buildString {
                            invalid.endpoint?.let {
                                appendLine("Endpoint: $it")
                            }
                            invalid.errors.forEach { appendLine(it) }
                            invalid.recommendedAction?.let {
                                appendLine()
                                append("Recommended action: ")
                                append(it)
                            }
                        },
                        actionHint = "Remove entry",
                        onClick = {
                            val removed = when {
                                invalid.name == "(storage)" -> extensionStore.clearStorage()
                                !invalid.endpoint.isNullOrBlank() ->
                                    extensionStore.remove(invalid.name, invalid.endpoint)
                                invalid.storageIndex != null ->
                                    extensionStore.removeAt(invalid.storageIndex)
                                else -> false
                            }
                            if (removed) refreshSettingsScreen()
                        },
                    )
                )
            }
        }

        contentRoot.addView(activity.formLabel("Registered extension tools"))
        if (extensionTools.isEmpty()) {
            contentRoot.addView(activity.timelineCard("No extension tools registered", "Add a local MCP tool above to store it here."))
        } else {
            val extensionAuditEntries = ToolExecutionLog.recentEntries()
                .filter { it.type == "capability" && it.source == "local_extension" }
            extensionTools.forEach { tool ->
                val extensionTarget = ExternalCapabilityTarget(
                    kind = ExternalCapabilityKind.LOCAL_EXTENSION,
                    endpoint = tool.endpoint,
                    name = tool.name,
                )
                val grant = permissionStore.findGrant(extensionTarget)
                val requiredFlags = policy.requiredFlagsForExtension(tool.manifest.featureFlags)
                val extensionLastUsed = lastExtensionUsageFor(tool, extensionAuditEntries)
                contentRoot.addView(
                    activity.timelineCard(
                        title = tool.name,
                        body = buildString {
                            appendLine("Permission scope: local extension")
                            appendLine(tool.description.ifBlank { "No description provided." })
                            appendLine("api_version: ${tool.manifest.apiVersion}")
                            appendLine("Endpoint: ${tool.endpoint}")
                            appendLine()
                            appendLine("Extension permissions (separate from Android tools):")
                            appendLine("List tools: ${permissionLabel(grant?.allows(ExternalCapabilityAction.LIST_TOOLS) == true)}")
                            appendLine("Call tool: ${permissionLabel(grant?.allows(ExternalCapabilityAction.CALL_TOOL) == true)}")
                            if (requiredFlags.isNotEmpty()) {
                                appendLine("Feature flags:")
                                requiredFlags.sorted().forEach { flag ->
                                    appendLine("- $flag: ${permissionLabel(grant?.allowsFeature(flag) == true)}")
                                }
                            }
                            appendLine("Last used: ${extensionLastUsed}")
                        },
                        actionHint = "Grant extension permissions",
                        onClick = {
                            permissionStore.grant(
                                target = extensionTarget,
                                actions = setOf(
                                    ExternalCapabilityAction.LIST_TOOLS,
                                    ExternalCapabilityAction.CALL_TOOL,
                                ),
                                featureFlags = requiredFlags,
                            )
                            refreshSettingsScreen()
                        },
                    )
                )
                contentRoot.addView(
                    activity.secondaryButton("Revoke permissions") {
                        permissionStore.revoke(extensionTarget)
                        refreshSettingsScreen()
                    }
                )
                contentRoot.addView(
                    activity.secondaryButton("Remove ${tool.name}") {
                        extensionStore.remove(tool.name, tool.endpoint)
                        permissionStore.revoke(extensionTarget)
                        refreshSettingsScreen()
                    }
                )
            }
        }

        val extensionAuditEntries = ToolExecutionLog.recentEntries()
            .filter { it.type == "capability" && it.source == "local_extension" }
            .take(5)
        contentRoot.addView(activity.formLabel("Extension audit log"))
        if (extensionAuditEntries.isEmpty()) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "No local extension activity yet",
                    body = "Extension tool calls will appear here after a local extension is registered and used."
                )
            )
        } else {
            extensionAuditEntries.forEach { entry ->
                contentRoot.addView(
                    activity.timelineCard(
                        title = "${entry.name} · ${entry.status}",
                        body = buildString {
                            appendLine("Target: ${entry.target.ifBlank { "unknown" }}")
                            appendLine("Result: ${entry.result.ifBlank { "n/a" }}")
                            if (entry.policyDecision.isNotBlank()) {
                                appendLine("Policy: ${entry.policyDecision}")
                            }
                            if (entry.payloadSummary.isNotBlank()) {
                                appendLine("Payload: ${entry.payloadSummary}")
                            }
                        }.trim(),
                    )
                )
            }
        }

        val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(
            activity.secondaryButton("List Tools") {
                val endpoint = endpointInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                recordMcpResult("Listing MCP tools...")
                refreshSettingsScreen()
                Thread {
                    val result = invokeExternalCapability(
                        endpoint = endpoint,
                        action = ExternalCapabilityAction.LIST_TOOLS,
                        permissionStore = permissionStore,
                        policy = policy,
                        invoker = invoker,
                    ) { target, requiredFlags ->
                        invoker.listTools(target, requiredFlags)
                    }
                    activity.runOnUiThread {
                        recordMcpResult(result)
                        refreshSettingsScreen()
                    }
                }.start()
            }.apply { id = R.id.list_mcp_tools_button },
            rowButtonParams()
        )
        actionRow.addView(
            activity.primaryButton("Call Tool") {
                val endpoint = endpointInput.text.toString()
                val toolName = toolInput.text.toString()
                val argsText = argsInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                recordMcpResult("Calling MCP tool...")
                refreshSettingsScreen()
                Thread {
                    val result = invokeExternalCapability(
                        endpoint = endpoint,
                        action = ExternalCapabilityAction.CALL_TOOL,
                        permissionStore = permissionStore,
                        policy = policy,
                        invoker = invoker,
                    ) { target, requiredFlags ->
                        invoker.callTool(target, toolName, JSONObject(argsText), requiredFlags)
                    }
                    activity.runOnUiThread {
                        recordMcpResult(result)
                        refreshSettingsScreen()
                    }
                }.start()
            }.apply { id = R.id.call_mcp_tool_button },
            rowButtonParams()
        )
        contentRoot.addView(actionRow)
        contentRoot.addView(activity.timelineCard("MCP result", mcpResult()))
    }

    private fun renderCloudPanel() {
        val savedUrl = preferences.getString("agent_provider_url", "").orEmpty()
        val savedModel = preferences.getString("agent_model", "").orEmpty()
        val savedKey = secretStore.read("agent_api_key").orEmpty()
        val configured = savedUrl.isNotBlank() && savedModel.isNotBlank() && savedKey.isNotBlank()

        contentRoot.addView(
            activity.summaryCard(
                title = "Cloud profile",
                value = if (configured) "$savedModel via ${shortHost(savedUrl)}" else "Not configured",
                chipText = if (configured) "configured" else "incomplete",
                chipAccent = configured
            )
        )

        contentRoot.addView(activity.formLabel("Provider URL"))
        val providerInput = activity.editText("https://api.example.com/v1").apply {
            id = R.id.agent_provider_url_input
            setText(savedUrl)
        }
        contentRoot.addView(providerInput)

        contentRoot.addView(activity.formLabel("Model"))
        val modelInput = activity.editText("e.g. gpt-4o-mini").apply {
            id = R.id.agent_model_input
            setText(savedModel)
        }
        contentRoot.addView(modelInput)

        contentRoot.addView(activity.formLabel("API key"))
        val apiKeyInput = activity.editText("paste API key").apply {
            id = R.id.agent_api_key_input
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(savedKey)
        }
        contentRoot.addView(apiKeyInput)

        contentRoot.addView(
            activity.primaryButton("Save Cloud API") {
                preferences.edit()
                    .putString("agent_provider_url", providerInput.text.toString().trim())
                    .putString("agent_model", modelInput.text.toString().trim())
                    .apply()
                secretStore.write("agent_api_key", apiKeyInput.text.toString())
                hideKeyboard(apiKeyInput)
                refreshSettingsScreen()
            }.apply { id = R.id.save_cloud_api_button }
        )

        contentRoot.addView(
            activity.timelineCard(
                "Stored profile",
                buildString {
                    appendLine("Provider URL: ${savedUrl.ifBlank { "not set" }}")
                    appendLine("Model: ${savedModel.ifBlank { "not set" }}")
                    append("API key: ${if (savedKey.isBlank()) "not set" else "configured"}")
                }
            )
        )
    }

    private fun permissionLabel(granted: Boolean): String = if (granted) "granted" else "denied (default)"

    private fun skillSourceLabel(skill: Skill): String {
        return if (isLocalSkill(skill.id.lowercase())) "Imported" else "Bundled"
    }

    private fun lastExtensionUsageFor(tool: LocalExtensionTool, entries: List<DeveloperLogEntry>): String {
        val match = entries.firstOrNull {
            it.type == "capability" &&
                it.source == "local_extension" &&
                it.target.contains(tool.endpoint, ignoreCase = true) &&
                it.target.contains(tool.name, ignoreCase = true)
        } ?: return "never"
        return "recent at ${DeveloperLogEntry.formatShortTimestamp(match.timestampMillis)}"
    }

    private fun invokeExternalCapability(
        endpoint: String,
        action: ExternalCapabilityAction,
        permissionStore: ExternalCapabilityPermissionStore,
        policy: ExternalCapabilityPolicy,
        invoker: ExternalCapabilityInvoker,
        execute: (ExternalCapabilityTarget, Set<String>) -> ExternalCapabilityInvokeResult,
    ): String {
        val extensions = localExtensionToolStore().load().tools
        return when (
            val invocation = ExternalCapabilityTargetResolver.resolve(
                endpoint = endpoint,
                extensions = extensions,
                policy = policy,
                action = action,
                permissionStore = permissionStore,
            )
        ) {
            is ExternalCapabilityInvocation.Ambiguous -> {
                "Permission denied: multiple local extensions share endpoint $endpoint " +
                    "(${invocation.extensionNames.joinToString()}). Grant permissions for one extension " +
                    "or use a unique endpoint per extension."
            }
            is ExternalCapabilityInvocation.Ready -> when (
                val outcome = execute(invocation.target, invocation.requiredFeatureFlags)
            ) {
                is ExternalCapabilityInvokeResult.Success -> outcome.message
                is ExternalCapabilityInvokeResult.Denied -> "Permission denied: ${outcome.decision.reason}"
                is ExternalCapabilityInvokeResult.Failed -> when (action) {
                    ExternalCapabilityAction.LIST_TOOLS -> "MCP list failed: ${outcome.message}"
                    ExternalCapabilityAction.CALL_TOOL -> "MCP call failed: ${outcome.message}"
                }
            }
        }
    }

    private fun settingsIntro(value: String): View {
        return TextView(activity).apply {
            text = value
            textSize = 13f
            setTextColor(Theme.MutedText)
            setPadding(0, 0, 0, 12)
        }
    }

    private fun settingsGoBackButton(): View {
        return activity.secondaryButton("Go Back") {
            closeSettingsPanel()
            refreshSettingsScreen()
        }.apply {
            minHeight = 46
        }.withMargins(bottom = 12)
    }

    private fun settingsPanelSwitcher(): View {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        SettingsPanel.values().forEach { panel ->
            val isActive = panel == activeSettingsPanel()
            val row = MaterialCardView(activity).apply {
                setCardBackgroundColor(if (isActive) Theme.Accent else Theme.Card)
                strokeColor = if (isActive) Theme.Accent else Theme.StrokeDark
                strokeWidth = 1
                radius = 8f
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (activeSettingsPanel() != panel) {
                        openSettingsPanel(panel)
                        refreshSettingsScreen()
                    }
                }
                id = when (panel) {
                    SettingsPanel.SKILLS -> R.id.settings_panel_skills_button
                    SettingsPanel.PERMISSIONS -> R.id.settings_panel_permissions_button
                    SettingsPanel.TOOLS -> R.id.settings_panel_tools_button
                    SettingsPanel.HELP -> R.id.settings_panel_help_button
                    SettingsPanel.MCP -> R.id.settings_panel_mcp_button
                    SettingsPanel.CLOUD -> R.id.settings_panel_cloud_button
                    SettingsPanel.RUNTIME -> R.id.settings_panel_runtime_button
                    SettingsPanel.RECORDING -> R.id.settings_panel_recording_button
                    SettingsPanel.COMPATIBILITY -> R.id.settings_panel_compatibility_button
                }
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
            }
            content.addView(
                TextView(activity).apply {
                    text = panel.label
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isActive) Theme.OnAccent else Color.WHITE)
                }
            )
            content.addView(
                TextView(activity).apply {
                    text = panel.intro
                    textSize = 12f
                    setTextColor(if (isActive) Theme.OnAccent else Theme.MutedText)
                    setPadding(0, 4, 0, 0)
                }
            )
            row.addView(content)
            container.addView(row.withMargins(top = 4, bottom = 6))
        }
        return container.withMargins(bottom = 12)
    }

    private fun renderCompatibilityPanel() {
        val summary = compatibilitySummary()
        val ready = if (summary.isReadyForRun) "ready" else "not ready"

        contentRoot.addView(
            activity.summaryCard(
                title = "Real-device onboarding",
                value = summary.deviceLabel,
                chipText = ready,
                chipAccent = summary.isReadyForRun,
            )
        )

        contentRoot.addView(
            activity.timelineCard(
                title = "Required checks",
                body = summary.checks.joinToString("\n\n") { check ->
                    "${check.title}: ${check.status}\n${check.details}${if (check.required && !check.passed) "\nAction required." else ""}"
                }
            )
        )

        if (!summary.isReadyForRun) {
            contentRoot.addView(activity.formLabel("Required setup actions"))
            val actions = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

            actions.addView(activity.primaryButton("Open Accessibility Settings") { openAccessibilitySettings() })
            actions.addView(activity.secondaryButton("Open app details") { openAppInfoSettings() })
            actions.addView(
                activity.secondaryButton("Battery optimization settings") {
                    openBatteryOptimizationSettings()
                }
            )

            contentRoot.addView(actions)
        }

        contentRoot.addView(activity.formLabel("Real-device beta checklist"))
        contentRoot.addView(
            activity.timelineCard(
                title = "Compatibility validation",
                body = buildString {
                    appendLine("1) Install app and launch on physical device.")
                    appendLine("2) Enable TouchPilot accessibility service.")
                    appendLine("3) Confirm accessibility works in Settings and touch operations.")
                    appendLine("4) Disable battery optimization if automation drops in background.")
                    appendLine("5) Export debug trace after failures for local review.")
                }
            )
        )

        contentRoot.addView(activity.formLabel("Known limitations"))
        contentRoot.addView(
            activity.timelineCard(
                title = "Known limitations",
                body = buildString {
                    appendLine("- OEM-specific Settings screens can differ by skin and may show different options.")
                    appendLine("- Accessibility trees may be sparse in custom-drawn or WebView-heavy apps.")
                    appendLine("- Background tasks can be killed aggressively on some vendor ROMs.")
                    appendLine("- Use local logs and device-specific notes to report compatibility gaps.")
                }
            )
        )
    }

    private fun skillSelectRow(
        title: String,
        subtitle: String,
        badge: String?,
        enabled: Boolean,
        selected: Boolean,
        onSelect: (() -> Unit)?,
        onToggleEnabled: (() -> Unit)?,
        onViewDetails: (() -> Unit)?
    ): View {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = when {
                !enabled -> Theme.StrokeDark
                selected -> Theme.Accent
                else -> Theme.StrokeDark
            }
            strokeWidth = if (selected && enabled) 2 else 1
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            isEnabled = enabled || onSelect != null
            setOnClickListener { onSelect?.invoke() }
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        column.addView(
            TextView(activity).apply {
                text = title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (selected) Theme.Accent else Color.WHITE)
            }
        )
        column.addView(
            TextView(activity).apply {
                text = subtitle
                textSize = 12f
                setTextColor(if (enabled) Theme.MutedText else Theme.StrokeDark)
                setPadding(0, 3, 0, 0)
            }
        )
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (badge != null) {
            row.addView(
                activity.statusChip(
                    badge,
                    accent = badge != SkillDetailFormatter.formatLabel(SkillRisk.LOW)
                )
            )
        }
        if (!enabled) {
            row.addView(
                activity.statusChip("disabled", accent = false),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = activity.dp(8)
                }
            )
        }
        if (onToggleEnabled != null) {
            row.addView(
                activity.secondaryButton(if (enabled) "Disable" else "Enable") {
                    onToggleEnabled()
                }.apply {
                    minHeight = 38
                }.withMargins(left = activity.dp(10))
            )
        }
        content.addView(row)
        if (onViewDetails != null) {
            content.addView(
                activity.secondaryButton("View details") {
                    onViewDetails()
                }.apply {
                    minHeight = 40
                }.withMargins(top = activity.dp(10))
            )
        }
        card.addView(content)
        return card.withMargins(top = 6, bottom = 6)
    }

    private fun selectedSkill(): Skill? {
        return skills.firstOrNull { it.id == selectedSkillId() }
    }

    private fun shortHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        return withoutScheme.substringBefore('/').ifBlank { trimmed }
    }
}
