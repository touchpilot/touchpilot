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
import dev.touchpilot.app.ui.dp
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.description
import dev.touchpilot.app.ui.editText
import dev.touchpilot.app.ui.formLabel
import dev.touchpilot.app.ui.label
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rowButtonParams
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.withMargins
import org.json.JSONObject

class SettingsScreenRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val preferences: SharedPreferences,
    private val skills: List<Skill>,
    private val localModelRuntime: LiteRtCommandModelRuntime,
    private val activeSettingsPanel: () -> SettingsPanel?,
    private val openSettingsPanel: (SettingsPanel) -> Unit,
    private val closeSettingsPanel: () -> Unit,
    private val selectedSkillId: () -> String?,
    private val commitSelectedSkill: (String?) -> Unit,
    private val openSkillDetail: (String) -> Unit,
    private val isSkillEnabled: (String) -> Boolean,
    private val setSkillEnabled: (String, Boolean) -> Unit,
    private val currentProviderMode: () -> AgentProviderMode,
    private val openAccessibilitySettings: () -> Unit,
    private val hideKeyboard: (View) -> Unit,
    private val recordMcpResult: (String) -> Unit,
    private val mcpResult: () -> String,
    private val refreshSettingsScreen: () -> Unit
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
            SettingsPanel.MCP -> renderMcpPanel()
            SettingsPanel.CLOUD -> renderCloudPanel()
            SettingsPanel.RUNTIME -> renderRuntimePanel()
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

        contentRoot.addView(
            skillSelectRow(
                title = "No skill",
                subtitle = "Run TouchPilot without a skill scope",
                badge = null,
                selected = selectedSkillId() == null,
                onSelect = { commitSelectedSkill(null) },
                onViewDetails = null
            )
        )
        skills.forEach { skill ->
            contentRoot.addView(skillManageRow(skill))
        }
    }

    private fun skillsPanelIntro(): View {
        return TextView(activity).apply {
            text = "Enable the skills TouchPilot may use, then select one to scope tools and prompts. " +
                "Disabled skills are excluded from selection and automatic matching."
            textSize = 12f
            setTextColor(Theme.MutedText)
            setPadding(0, 0, 0, activity.dp(8))
        }
    }

    /**
     * A skill row with an enable/disable control. A disabled skill is shown
     * dimmed with a "Disabled" chip, is not selectable, and (handled by the
     * registry) is excluded from active selection and automatic matching. Only
     * an enabled skill can be tapped to become the active scope.
     */
    private fun skillManageRow(skill: Skill): View {
        val enabled = isSkillEnabled(skill.id)
        val selected = enabled && selectedSkillId() == skill.id
        val description = SkillDetailFormatter.displayDescription(skill)
            .ifBlank { "No description provided" }

        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = if (selected) Theme.Accent else Theme.StrokeDark
            strokeWidth = if (selected) 2 else 1
            radius = 8f
            cardElevation = 0f
            alpha = if (enabled) 1f else 0.6f
            isClickable = enabled
            isFocusable = enabled
            if (enabled) {
                setOnClickListener { commitSelectedSkill(skill.id) }
            }
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val titleColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        titleColumn.addView(
            TextView(activity).apply {
                text = skill.title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(
                    when {
                        !enabled -> Theme.MutedText
                        selected -> Theme.Accent
                        else -> Color.WHITE
                    }
                )
            }
        )
        titleColumn.addView(
            TextView(activity).apply {
                text = description
                textSize = 12f
                setTextColor(Theme.MutedText)
                setPadding(0, 3, 0, 0)
            }
        )
        headerRow.addView(titleColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!enabled) {
            headerRow.addView(activity.statusChip("Disabled", accent = false))
        } else {
            headerRow.addView(
                activity.statusChip(
                    SkillDetailFormatter.formatLabel(skill.risk),
                    accent = skill.risk != SkillRisk.LOW
                )
            )
        }
        content.addView(headerRow)

        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actionRow.addView(
            activity.secondaryButton(if (enabled) "Disable" else "Enable") {
                setSkillEnabled(skill.id, !enabled)
                refreshSettingsScreen()
            }.apply { minHeight = 40 },
            rowButtonParams()
        )
        actionRow.addView(
            activity.secondaryButton("View details") {
                openSkillDetail(skill.id)
            }.apply { minHeight = 40 },
            rowButtonParams()
        )
        content.addView(actionRow.withMargins(top = activity.dp(10)))

        card.addView(content)
        return card.withMargins(top = 6, bottom = 6)
    }

    private fun renderRuntimePanel() {
        val mode = currentProviderMode()
        val localStatus = localModelRuntime.status()
        contentRoot.addView(
            activity.summaryCard(
                title = "Current runtime",
                value = mode.label(),
                chipText = if (localStatus.available) "model ready" else "fallback",
                chipAccent = localStatus.available
            )
        )

        contentRoot.addView(activity.formLabel("Runtime mode"))
        AgentProviderMode.values().forEach { option ->
            contentRoot.addView(
                skillSelectRow(
                    title = option.label(),
                    subtitle = option.description(),
                    badge = null,
                    selected = option == mode,
                    onSelect = {
                        preferences.edit().putString("agent_provider_mode", option.name).apply()
                        refreshSettingsScreen()
                    },
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
                        "MCP $toolName -> ${callResult.ok}\n${callResult.message}"
                    }.getOrElse { error ->
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
                    SettingsPanel.MCP -> R.id.settings_panel_mcp_button
                    SettingsPanel.CLOUD -> R.id.settings_panel_cloud_button
                    SettingsPanel.RUNTIME -> R.id.settings_panel_runtime_button
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
        selected: Boolean,
        onSelect: () -> Unit,
        onViewDetails: (() -> Unit)?
    ): View {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = if (selected) Theme.Accent else Theme.StrokeDark
            strokeWidth = if (selected) 2 else 1
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onSelect() }
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
                setTextColor(Theme.MutedText)
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
