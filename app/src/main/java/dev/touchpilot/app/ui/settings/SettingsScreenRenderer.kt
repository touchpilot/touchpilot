package dev.touchpilot.app.ui.settings

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.R
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.mcp.McpHttpClient
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillDetailFormatter
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.navigation.SettingsPanel
import dev.touchpilot.app.runtime.ToolExecutionController
import dev.touchpilot.app.tools.ToolExecutionLog
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
    private val hideKeyboard: (View) -> Unit,
    private val bindKeyboardScrollSpacer: (View) -> Unit,
    private val getFocusSelectorIndex: () -> Int,
    private val setFocusSelectorIndex: (Int) -> Unit,
    private val getLastFocusInputArgs: () -> Map<String, String>?,
    private val setLastFocusInputArgs: (Map<String, String>?) -> Unit,
    private val recordMcpResult: (String) -> Unit,
    private val mcpResult: () -> String,
    private val refreshSettingsScreen: () -> Unit,
    private val demonstrationRecordingEnabled: () -> Boolean = { false },
    private val demonstrationAutoExportEnabled: () -> Boolean = { false },
    private val demonstrationSessionCount: () -> Int = { 0 },
    private val demonstrationSummaries: () -> List<String> = { emptyList() },
    private val onDemonstrationRecordingToggled: (Boolean) -> Unit = {},
    private val onDemonstrationAutoExportToggled: (Boolean) -> Unit = {},
) {
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
            SettingsPanel.SKILLS -> renderSkillsPanel()
            SettingsPanel.TOOLS -> renderToolsPanel()
            SettingsPanel.MCP -> renderMcpPanel()
            SettingsPanel.CLOUD -> renderCloudPanel()
            SettingsPanel.RUNTIME -> renderRuntimePanel()
            SettingsPanel.RECORDING -> renderRecordingPanel()
        }
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
            val description = SkillDetailFormatter.displayDescription(skill)
            contentRoot.addView(
                skillSelectRow(
                    title = skill.title,
                    subtitle = description.ifBlank { "No description provided" },
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

        contentRoot.addView(activity.formLabel("Recording mode"))
        contentRoot.addView(
            skillSelectRow(
                title = "Record demonstrations",
                subtitle = "Capture tool calls and screen state for each agent action",
                badge = if (enabled) "on" else null,
                enabled = true,
                selected = enabled,
                onSelect = { onDemonstrationRecordingToggled(true) },
                onToggleEnabled = null,
                onViewDetails = null,
            )
        )
        contentRoot.addView(
            skillSelectRow(
                title = "Do not record",
                subtitle = "Run agents without capturing demonstration data",
                badge = if (!enabled) "active" else null,
                enabled = true,
                selected = !enabled,
                onSelect = { onDemonstrationRecordingToggled(false) },
                onToggleEnabled = null,
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

        val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(
            activity.secondaryButton("List Tools") {
                val endpoint = endpointInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                recordMcpResult("Listing MCP tools...")
                refreshSettingsScreen()
                Thread {
                    val result = runCatching {
                        val client = McpHttpClient(endpoint)
                        val initialized = client.initialize()
                        val tools = client.listTools()
                        ToolExecutionLog.recordAction(
                            name = "mcp_list_tools",
                            result = "Listed ${tools.size} MCP tool(s)",
                            status = "ok",
                            source = "mcp",
                            details = "endpoint=$endpoint\ninitialized=$initialized"
                        )
                        buildString {
                            appendLine("MCP initialized:")
                            appendLine(initialized)
                            appendLine()
                            appendLine("Tools:")
                            if (tools.isEmpty()) {
                                appendLine("No tools returned.")
                            } else {
                                tools.forEach { tool ->
                                    appendLine("- ${tool.name}: ${tool.description}")
                                }
                            }
                        }
                    }.getOrElse { error ->
                        ToolExecutionLog.recordAction(
                            name = "mcp_list_tools",
                            result = error.message.orEmpty(),
                            status = "fail",
                            source = "mcp",
                            details = "endpoint=$endpoint"
                        )
                        "MCP list failed: ${error.message}"
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
                    val result = runCatching {
                        val client = McpHttpClient(endpoint)
                        client.initialize()
                        val callResult = client.callTool(toolName, JSONObject(argsText))
                        ToolExecutionLog.recordAction(
                            name = "mcp_call_tool",
                            result = "Called $toolName -> ${callResult.ok}",
                            status = if (callResult.ok) "ok" else "fail",
                            source = "mcp",
                            details = buildString {
                                appendLine("endpoint=$endpoint")
                                appendLine("tool=$toolName")
                                appendLine("args=$argsText")
                                appendLine("message=${callResult.message}")
                            }
                        )
                        "MCP $toolName -> ${callResult.ok}\n${callResult.message}"
                    }.getOrElse { error ->
                        ToolExecutionLog.recordAction(
                            name = "mcp_call_tool",
                            result = error.message.orEmpty(),
                            status = "fail",
                            source = "mcp",
                            details = buildString {
                                appendLine("endpoint=$endpoint")
                                appendLine("tool=$toolName")
                                appendLine("args=$argsText")
                            }
                        )
                        "MCP call failed: ${error.message}"
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
        val savedKey = preferences.getString("agent_api_key", "").orEmpty()
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
                    .putString("agent_api_key", apiKeyInput.text.toString().trim())
                    .apply()
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
                    SettingsPanel.TOOLS -> R.id.settings_panel_tools_button
                    SettingsPanel.MCP -> R.id.settings_panel_mcp_button
                    SettingsPanel.CLOUD -> R.id.settings_panel_cloud_button
                    SettingsPanel.RUNTIME -> R.id.settings_panel_runtime_button
                    SettingsPanel.RECORDING -> R.id.settings_panel_recording_button
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
