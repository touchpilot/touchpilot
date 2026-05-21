package dev.touchpilot.app

import android.app.Activity
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.ConversationalGate
import dev.touchpilot.app.agent.DefaultLocalReasoningCore
import dev.touchpilot.app.agent.LocalReasoningContext
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.defaultAgentRunInvocation
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelStatus
import dev.touchpilot.app.mcp.McpHttpClient
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillStore
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var skills: List<Skill>
    private lateinit var toolExecutor: AndroidToolExecutor
    private lateinit var localModelRuntime: LiteRtCommandModelRuntime
    private lateinit var reasoningCore: LocalReasoningCore
    private lateinit var contentRoot: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var executionLogView: TextView
    private var bottomNav: TabLayout? = null

    private var activeSection = Section.CHAT
    private var activeSettingsPanel = SettingsPanel.SKILLS
    private var selectedSkillId: String? = null
    private val conversation = mutableListOf<ChatEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("touchpilot", MODE_PRIVATE)
        skills = SkillStore(this).loadSkills()
        toolExecutor = AndroidToolExecutor(this)
        localModelRuntime = LiteRtCommandModelRuntime(this)
        selectedSkillId = preferences.getString("active_skill", null)

        reasoningCore = DefaultLocalReasoningCore(
            invocation = defaultAgentRunInvocation(
                toolExecutor = toolExecutor,
                approvalProvider = ToolApprovalProvider { request ->
                    approveAgentTool(request)
                },
                localModelRuntime = localModelRuntime
            ),
            sessionContext = { currentReasoningContext() },
            availableSkills = { skills },
            screenContextProvider = { AccessibilityBridge.observeScreenContext() }
        )

        if (conversation.isEmpty()) {
            conversation += ChatEvent.Agent(
                "What would you like me to do?",
                "Local router is ready for simple Android actions."
            )
        }

        setContentView(buildRoot())
        showSection(Section.CHAT)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (::contentRoot.isInitialized) {
            showSection(activeSection)
        }
    }

    private fun buildRoot(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.Background)

            addView(buildHeader())

            val scrollView = ScrollView(this@MainActivity).apply {
                setFillViewport(false)
                contentRoot = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(28, 16, 28, 18)
                }
                addView(contentRoot)
            }
            addView(
                scrollView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            addView(buildBottomNav())
        }
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 82, 30, 14)
            setBackgroundColor(Theme.Background)

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(
                TextView(this@MainActivity).apply {
                    id = R.id.touchpilot_title
                    text = "Touch"
                    textSize = 25f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                }
            )
            row.addView(
                TextView(this@MainActivity).apply {
                    text = "Pilot"
                    textSize = 25f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Theme.Accent)
                }
            )

            addView(row)

            statusView = TextView(this@MainActivity).apply {
                id = R.id.touchpilot_status
                textSize = 12f
                setPadding(0, 8, 0, 0)
                setTextColor(Color.rgb(150, 164, 178))
            }
            addView(statusView)
        }
    }

    private fun buildBottomNav(): View {
        return TabLayout(this).apply {
            bottomNav = this
            setBackgroundColor(Theme.Background)
            setPadding(0, 6, 0, 30)
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
            setSelectedTabIndicatorColor(Theme.Accent)
            setTabTextColors(Theme.NavText, Theme.Accent)

            Section.values().forEach { section ->
                addTab(
                    newTab()
                        .setCustomView(bottomNavLabel(section))
                        .setTag(section),
                    section == activeSection
                )
            }

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    (tab.tag as? Section)?.let { section ->
                        if (section != activeSection) {
                            showSection(section)
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }.withMargins()
    }

    private fun bottomNavLabel(section: Section): TextView {
        return TextView(this).apply {
            text = section.label
            gravity = Gravity.CENTER
            setSingleLine(true)
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            minWidth = 0
            setPadding(0, 10, 0, 10)
        }
    }

    private fun showSection(section: Section) {
        activeSection = section
        updateBottomNav()
        contentRoot.removeAllViews()
        when (section) {
            Section.CHAT -> renderChatScreen()
            Section.TOOLS -> renderToolsScreen()
            Section.LOGS -> renderLogsScreen()
            Section.SETTINGS -> renderSettingsScreen()
        }
    }

    private fun updateBottomNav() {
        val nav = bottomNav ?: return
        val index = Section.values().indexOf(activeSection)
        if (index >= 0 && nav.selectedTabPosition != index) {
            nav.getTabAt(index)?.select()
        }
        Section.values().forEachIndexed { tabIndex, section ->
            val label = nav.getTabAt(tabIndex)?.customView as? TextView
            label?.setTextColor(if (section == activeSection) Theme.Accent else Theme.NavText)
        }
    }

    private fun renderChatScreen() {
        contentRoot.addView(sectionTitle("Agent"))
        contentRoot.addView(statusPill())
        contentRoot.addView(skillPill())

        val introEvents = conversation.takeWhile { it is ChatEvent.Agent }
        val timelineEvents = conversation.drop(introEvents.size)

        introEvents.forEach { event ->
            contentRoot.addView(renderChatEvent(event))
        }

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 18, 0, 0)
        }
        val taskInput = EditText(this).apply {
            id = R.id.agent_task_input
            hint = "Message TouchPilot..."
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            background = rounded(Theme.SurfaceRaised, 26, Theme.StrokeDark)
            setPadding(24, 10, 24, 10)
        }
        inputRow.addView(taskInput, LinearLayout.LayoutParams(0, 58, 1f))
        inputRow.addView(
            TextView(this).apply {
                id = R.id.run_agent_button
                text = "Go"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(5, 26, 12))
                background = rounded(Theme.Accent, 30, Theme.Accent)
                setOnClickListener {
                    val task = taskInput.text.toString().trim()
                    if (task.isNotEmpty()) {
                        hideKeyboard(taskInput)
                        taskInput.text.clear()
                        runAgentFromChat(task)
                    }
                }
            },
            LinearLayout.LayoutParams(64, 58).apply {
                leftMargin = 10
            }
        )
        contentRoot.addView(inputRow)

        timelineEvents.forEach { event ->
            contentRoot.addView(renderChatEvent(event))
        }
    }

    private fun renderChatEvent(event: ChatEvent): View {
        return when (event) {
            is ChatEvent.User -> userBubble(event.text)
            is ChatEvent.Agent -> agentBubble(event.text, event.detail)
            is ChatEvent.Timeline -> timelineCard(event.title, event.body)
            is ChatEvent.ApprovalPrompt -> approvalCard(event)
        }
    }

    private fun approvalCard(event: ChatEvent.ApprovalPrompt): View {
        val request = event.request
        val tool = request.tool
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = when (event.state) {
                ApprovalState.PENDING -> Theme.Accent
                ApprovalState.APPROVED -> Theme.Accent
                ApprovalState.REJECTED -> Theme.StrokeDark
            }
            strokeWidth = 2
            radius = 18f
            cardElevation = 0f
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
        }

        val statusLabel = when (event.state) {
            ApprovalState.PENDING -> "Approval requested"
            ApprovalState.APPROVED -> "Approved"
            ApprovalState.REJECTED -> "Rejected"
        }
        content.addView(
            TextView(this).apply {
                text = "$statusLabel - ${tool.name}"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        content.addView(
            TextView(this).apply {
                text = buildApprovalMessage(request)
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            }
        )

        if (event.state == ApprovalState.PENDING) {
            val buttonRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 14, 0, 0)
            }
            buttonRow.addView(
                primaryButton("Approve") {
                    resolveApproval(event, true)
                }.apply { id = R.id.approval_approve_button },
                rowButtonParams()
            )
            buttonRow.addView(
                secondaryButton("Reject") {
                    resolveApproval(event, false)
                }.apply { id = R.id.approval_reject_button },
                rowButtonParams()
            )
            content.addView(buttonRow)
        }

        card.addView(content)
        return card.withMargins(top = 10, right = 42, bottom = 10)
    }

    private fun resolveApproval(event: ChatEvent.ApprovalPrompt, approved: Boolean) {
        if (event.state != ApprovalState.PENDING) return
        event.state = if (approved) ApprovalState.APPROVED else ApprovalState.REJECTED
        event.onDecision(approved)
        showSection(Section.CHAT)
    }

    private fun runAgentFromChat(task: String) {
        conversation += ChatEvent.User(task)

        val conversationalResponse = ConversationalGate.respond(task)
        if (conversationalResponse != null) {
            conversation += ChatEvent.Agent(conversationalResponse.message, "")
            showSection(Section.CHAT)
            return
        }

        conversation += ChatEvent.Agent("Working on it.", "Runtime: ${currentProviderMode().label()}")
        showSection(Section.CHAT)

        Thread {
            val transcript = runCatching {
                reasoningCore.run(task).transcript
            }.getOrElse { error ->
                "Agent failed: ${error.message}"
            }

            runOnUiThread {
                conversation += ChatEvent.Timeline("Action timeline", transcript.trim())
                conversation += ChatEvent.Agent("Done.", "Review the timeline for tool calls and results.")
                refreshExecutionLog()
                refreshStatus()
                showSection(Section.CHAT)
            }
        }.start()
    }

    private fun currentReasoningContext(): LocalReasoningContext {
        val providerMode = currentProviderMode()
        return LocalReasoningContext(
            skill = selectedSkill(),
            providerMode = providerMode
        )
    }

    private fun renderSkillsSettingsPanel() {
        contentRoot.addView(sectionTitle("Skills"))
        contentRoot.addView(agentBubble("Active skill", selectedSkill()?.title ?: "No skill"))

        val skillSpinner = Spinner(this).apply {
            id = R.id.skill_spinner
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                listOf("No skill") + skills.map { it.title }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val selectedIndex = skills.indexOfFirst { it.id == selectedSkillId }
            setSelection(if (selectedIndex >= 0) selectedIndex + 1 else 0)
        }
        contentRoot.addView(formLabel("Active Skill"))
        contentRoot.addView(skillSpinner)
        contentRoot.addView(
            primaryButton("Save Skill") {
                val index = skillSpinner.selectedItemPosition - 1
                selectedSkillId = skills.getOrNull(index)?.id
                preferences.edit().putString("active_skill", selectedSkillId).apply()
                showSection(Section.SETTINGS)
            }
        )

        if (skills.isEmpty()) {
            contentRoot.addView(timelineCard("Installed skills", "No bundled skills found."))
        } else {
            skills.forEach { skill ->
                contentRoot.addView(
                    timelineCard(
                        skill.title,
                        buildString {
                            appendLine(skill.markdown.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
                            appendLine()
                            appendLine("Allowed tools:")
                            appendLine(skill.allowedTools.joinToString(separator = "\n") { "- $it" })
                        }.trim()
                    )
                )
            }
        }
    }

    private fun renderRuntimeSettingsPanel() {
        contentRoot.addView(sectionTitle("Runtime"))
        contentRoot.addView(statusPill())
        contentRoot.addView(localModelStatusCard())
        contentRoot.addView(
            timelineCard(
                "Runtime boundary",
                """
                LiteRT command routing is the first local model runtime target. If the model asset is unavailable, TouchPilot falls back to the deterministic local router.

                Every local model command still goes through JSON parsing, tool validation, skill allowlists, safety policy, approval, and redacted logs.
                """.trimIndent()
            )
        )

        val providerModeSpinner = Spinner(this).apply {
            id = R.id.agent_provider_spinner
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                ProviderModeLabels
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(providerModeIndex(preferences.getString("agent_provider_mode", AgentProviderMode.LOCAL_ROUTER.name)))
        }
        contentRoot.addView(formLabel("Agent Runtime"))
        contentRoot.addView(providerModeSpinner)
        contentRoot.addView(
            primaryButton("Save Runtime") {
                val mode = when (providerModeSpinner.selectedItemPosition) {
                    1 -> AgentProviderMode.LOCAL_MODEL
                    else -> AgentProviderMode.LOCAL_ROUTER
                }
                preferences.edit().putString("agent_provider_mode", mode.name).apply()
                showSection(Section.SETTINGS)
            }
        )
        contentRoot.addView(
            secondaryButton("Open Accessibility Settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.apply { id = R.id.open_accessibility_settings_button }
        )
    }

    private fun renderToolsScreen() {
        contentRoot.addView(sectionTitle("Android Tools"))
        contentRoot.addView(statusPill())
        contentRoot.addView(
            secondaryButton("Open Accessibility Settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }.apply { id = R.id.open_accessibility_settings_button }
        )

        contentRoot.addView(
            primaryButton("Observe Current Screen") {
                executeAndRender("observe_screen", emptyMap())
                showSection(Section.TOOLS)
            }.apply { id = R.id.observe_screen_button }
        )

        val appInput = editText("App package or launcher label").apply { id = R.id.open_app_input }
        contentRoot.addView(appInput)
        contentRoot.addView(
            secondaryButton("Open App") {
                hideKeyboard(appInput)
                executeAndRender("open_app", mapOf("target" to appInput.text.toString()))
                showSection(Section.TOOLS)
            }.apply { id = R.id.open_app_button }
        )

        val tapInput = editText("Visible text to tap").apply { id = R.id.tap_text_input }
        contentRoot.addView(tapInput)
        contentRoot.addView(
            secondaryButton("Tap Text") {
                hideKeyboard(tapInput)
                executeAndRender("tap", mapOf("text" to tapInput.text.toString()))
                showSection(Section.TOOLS)
            }.apply { id = R.id.tap_text_button }
        )

        val typeInput = editText("Text to type into focused field").apply { id = R.id.type_text_input }
        contentRoot.addView(typeInput)
        contentRoot.addView(
            secondaryButton("Type Into Focused Field") {
                val value = typeInput.text.toString()
                hideKeyboard(typeInput)
                typeInput.requestFocus()
                executeAndRender("type_text", mapOf("text" to value))
                showSection(Section.TOOLS)
            }.apply { id = R.id.type_text_button }
        )

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(
            secondaryButton("Back") {
                executeAndRender("press_back", emptyMap())
            }.apply { id = R.id.back_button },
            rowButtonParams()
        )
        actionRow.addView(
            secondaryButton("Home") {
                executeAndRender("press_home", emptyMap())
            }.apply { id = R.id.home_button },
            rowButtonParams()
        )
        contentRoot.addView(actionRow)

        val scrollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        scrollRow.addView(
            secondaryButton("Scroll Down") {
                executeAndRender("scroll", mapOf("direction" to "forward"))
                showSection(Section.TOOLS)
            }.apply { id = R.id.scroll_down_button },
            rowButtonParams()
        )
        scrollRow.addView(
            secondaryButton("Scroll Up") {
                executeAndRender("scroll", mapOf("direction" to "backward"))
                showSection(Section.TOOLS)
            }.apply { id = R.id.scroll_up_button },
            rowButtonParams()
        )
        contentRoot.addView(scrollRow)

        val waitInput = editText("Text to wait for").apply { id = R.id.wait_for_text_input }
        contentRoot.addView(waitInput)
        contentRoot.addView(
            secondaryButton("Wait For Text") {
                val expected = waitInput.text.toString()
                Thread {
                    val result = toolExecutor.execute(
                        "wait_for_ui",
                        mapOf("text" to expected, "timeout_ms" to "5000"),
                        ToolSource.DIRECT_DEBUG
                    )
                    runOnUiThread {
                        outputText = SensitiveTextRedactor.redact("wait_for_ui -> ${result.ok}: ${result.message}")
                        refreshExecutionLog()
                        showSection(Section.TOOLS)
                    }
                }.start()
            }.apply { id = R.id.wait_for_text_button }
        )

        contentRoot.addView(timelineCard("Latest result", outputText))
    }

    private fun renderMcpSettingsPanel() {
        contentRoot.addView(sectionTitle("MCP"))

        val endpointInput = editText("MCP HTTP JSON-RPC endpoint").apply {
            id = R.id.mcp_endpoint_input
            setText(preferences.getString("mcp_endpoint", ""))
        }
        val toolInput = editText("MCP tool name").apply { id = R.id.mcp_tool_input }
        val argsInput = editText("MCP tool arguments JSON").apply {
            id = R.id.mcp_args_input
            setSingleLine(false)
            minLines = 3
            setText("{}")
        }

        contentRoot.addView(endpointInput)
        val mcpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mcpRow.addView(
            secondaryButton("List Tools") {
                val endpoint = endpointInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                outputText = "Listing MCP tools..."
                showSection(Section.SETTINGS)
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
                    runOnUiThread {
                        outputText = result
                        showSection(Section.SETTINGS)
                    }
                }.start()
            }.apply { id = R.id.list_mcp_tools_button },
            rowButtonParams()
        )
        mcpRow.addView(
            secondaryButton("Call Tool") {
                val endpoint = endpointInput.text.toString()
                val toolName = toolInput.text.toString()
                val argsText = argsInput.text.toString()
                preferences.edit().putString("mcp_endpoint", endpoint).apply()
                outputText = "Calling MCP tool..."
                showSection(Section.SETTINGS)
                Thread {
                    val result = runCatching {
                        val client = McpHttpClient(endpoint)
                        client.initialize()
                        val callResult = client.callTool(toolName, JSONObject(argsText))
                        "MCP $toolName -> ${callResult.ok}\n${callResult.message}"
                    }.getOrElse { error ->
                        "MCP call failed: ${error.message}"
                    }
                    runOnUiThread {
                        outputText = result
                        showSection(Section.SETTINGS)
                    }
                }.start()
            }.apply { id = R.id.call_mcp_tool_button },
            rowButtonParams()
        )
        contentRoot.addView(mcpRow)
        contentRoot.addView(toolInput)
        contentRoot.addView(argsInput)
        contentRoot.addView(timelineCard("MCP result", outputText))
    }

    private fun renderLogsScreen() {
        contentRoot.addView(sectionTitle("Logs"))
        contentRoot.addView(
            primaryButton("Export Debug Trace") {
                val file = exportDebugTrace()
                outputText = "Debug trace exported: ${file.absolutePath}"
                showSection(Section.LOGS)
            }.apply { id = R.id.export_debug_trace_button }
        )
        contentRoot.addView(timelineCard("Latest result", outputText))
        executionLogView = TextView(this).apply {
            id = R.id.execution_log_view
            text = ToolExecutionLog.render()
            textSize = 12f
            setTextColor(Theme.BodyText)
            setPadding(18, 18, 18, 18)
            background = rounded(Theme.Card, 18, Theme.StrokeDark)
        }
        contentRoot.addView(executionLogView)
    }

    private fun renderCloudApiSettingsPanel() {
        contentRoot.addView(sectionTitle("Cloud API"))

        val providerInput = editText("Provider URL").apply {
            id = R.id.agent_provider_url_input
            setText(preferences.getString("agent_provider_url", ""))
        }
        val modelInput = editText("Model name").apply {
            id = R.id.agent_model_input
            setText(preferences.getString("agent_model", ""))
        }
        val apiKeyInput = editText("API key").apply {
            id = R.id.agent_api_key_input
            setText(preferences.getString("agent_api_key", ""))
        }

        contentRoot.addView(providerInput)
        contentRoot.addView(modelInput)
        contentRoot.addView(apiKeyInput)
        contentRoot.addView(
            primaryButton("Save Cloud API") {
                preferences.edit()
                    .putString("agent_provider_url", providerInput.text.toString().trim())
                    .putString("agent_model", modelInput.text.toString().trim())
                    .putString("agent_api_key", apiKeyInput.text.toString().trim())
                    .apply()
                outputText = "Cloud API settings saved."
                showSection(Section.SETTINGS)
            }
        )
        contentRoot.addView(
            timelineCard(
                "Current cloud profile",
                buildString {
                    appendLine("Provider URL: ${preferences.getString("agent_provider_url", "").orEmpty().ifBlank { "not set" }}")
                    appendLine("Model: ${preferences.getString("agent_model", "").orEmpty().ifBlank { "not set" }}")
                    append("API key: ${if (preferences.getString("agent_api_key", "").isNullOrBlank()) "not set" else "configured"}")
                }
            )
        )
    }

    private fun renderSettingsScreen() {
        contentRoot.addView(sectionTitle("Settings"))
        val panelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        SettingsPanel.values().forEach { panel ->
            panelRow.addView(settingsPanelButton(panel), rowButtonParams())
        }
        contentRoot.addView(panelRow)

        when (activeSettingsPanel) {
            SettingsPanel.SKILLS -> renderSkillsSettingsPanel()
            SettingsPanel.MCP -> renderMcpSettingsPanel()
            SettingsPanel.CLOUD_API -> renderCloudApiSettingsPanel()
            SettingsPanel.RUNTIME -> renderRuntimeSettingsPanel()
        }
    }

    private fun settingsPanelButton(panel: SettingsPanel): TextView {
        val selected = panel == activeSettingsPanel
        val button = if (selected) {
            primaryButton(panel.label) {
                activeSettingsPanel = panel
                showSection(Section.SETTINGS)
            }
        } else {
            secondaryButton(panel.label) {
                activeSettingsPanel = panel
                showSection(Section.SETTINGS)
            }
        }
        return button.apply { minHeight = 46 }
    }

    private var outputText: String = "No result yet."

    private fun executeAndRender(name: String, args: Map<String, String>) {
        val result = toolExecutor.execute(name, args, ToolSource.DIRECT_DEBUG)
        outputText = SensitiveTextRedactor.redact("$name($args) -> ${result.ok}: ${result.message}")
        refreshExecutionLog()
    }

    private fun refreshExecutionLog() {
        if (::executionLogView.isInitialized) {
            executionLogView.text = ToolExecutionLog.render()
        }
    }

    private fun refreshStatus() {
        if (::statusView.isInitialized) {
            statusView.text = if (AccessibilityBridge.isConnected()) {
                "Accessibility service: connected"
            } else {
                "Accessibility service: not connected"
            }
            statusView.setTextColor(if (AccessibilityBridge.isConnected()) Theme.Accent else Theme.MutedText)
        }
    }

    private fun currentProviderMode(): AgentProviderMode {
        return when (preferences.getString("agent_provider_mode", AgentProviderMode.LOCAL_ROUTER.name)) {
            AgentProviderMode.LOCAL_MODEL.name -> AgentProviderMode.LOCAL_MODEL
            else -> AgentProviderMode.LOCAL_ROUTER
        }
    }

    private fun selectedSkill(): Skill? {
        return skills.firstOrNull { it.id == selectedSkillId }
    }

    private fun rowButtonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(4, 6, 4, 6)
        }
    }

    private fun controlParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 7, 0, 7)
        }
    }

    private fun sectionTitle(text: String): TextView {
        return TextView(this).apply {
            setText(text)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, 4, 0, 14)
        }
    }

    private fun formLabel(text: String): TextView {
        return TextView(this).apply {
            setText(text)
            textSize = 12f
            setTextColor(Theme.MutedText)
            setPadding(0, 14, 0, 6)
        }
    }

    private fun editText(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            background = rounded(Theme.SurfaceRaised, 18, Theme.StrokeDark)
            layoutParams = controlParams()
            minHeight = 54
            setPadding(20, 8, 20, 8)
        }
    }

    private fun primaryButton(text: String, onClick: () -> Unit): TextView {
        return MaterialButton(this).apply {
            setText(text)
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(Theme.OnAccent)
            backgroundTintList = ColorStateList.valueOf(Theme.Accent)
            strokeColor = ColorStateList.valueOf(Theme.Accent)
            strokeWidth = 1
            cornerRadius = 20
            layoutParams = controlParams()
            minHeight = 52
            setPadding(18, 14, 18, 14)
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(text: String, onClick: () -> Unit): TextView {
        return MaterialButton(this).apply {
            setText(text)
            gravity = Gravity.CENTER
            textSize = 13f
            isAllCaps = false
            setTextColor(Theme.BodyText)
            backgroundTintList = ColorStateList.valueOf(Theme.SurfaceRaised)
            strokeColor = ColorStateList.valueOf(Theme.StrokeDark)
            strokeWidth = 1
            cornerRadius = 18
            layoutParams = controlParams()
            minHeight = 50
            setPadding(14, 13, 14, 13)
            setOnClickListener { onClick() }
        }
    }

    private fun userBubble(text: String): View {
        return TextView(this).apply {
            setText(text)
            textSize = 14f
            setTextColor(Color.rgb(3, 30, 13))
            background = rounded(Theme.Accent, 22, Theme.Accent)
            setPadding(22, 16, 22, 16)
        }.withMargins(left = 84, top = 10, bottom = 10)
    }

    private fun agentBubble(text: String, detail: String): View {
        return timelineCard(text, detail)
    }

    private fun statusPill(): View {
        return timelineCard(
            "Runtime",
            "${currentProviderMode().label()}\n${localModelRuntime.status().shortLine()}\n${if (AccessibilityBridge.isConnected()) "Accessibility connected" else "Accessibility not connected"}"
        )
    }

    private fun localModelStatusCard(): View {
        val status = localModelRuntime.status()
        return timelineCard(
            "Local model",
            """
            Runtime: ${status.runtime}
            Status: ${if (status.available) "available" else "fallback active"}
            Model asset: ${status.modelAsset}
            Version: ${status.version}
            ${status.message}
            """.trimIndent()
        )
    }

    private fun skillPill(): View {
        return timelineCard("Active skill", selectedSkill()?.title ?: "No skill")
    }

    private fun timelineCard(title: String, body: String): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = Theme.StrokeDark
            strokeWidth = 1
            radius = 18f
            cardElevation = 0f
            setPadding(18, 16, 18, 16)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
        }
        content.addView(
            TextView(this).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        content.addView(
            TextView(this).apply {
                text = body
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 10, right = 42, bottom = 10)
    }

    private fun View.withMargins(
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0
    ): View {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(left, top, right, bottom)
        }
        return this
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(1, stroke)
        }
    }

    private fun hideKeyboard(anchor: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun providerModeIndex(savedMode: String?): Int {
        return when (savedMode) {
            AgentProviderMode.LOCAL_MODEL.name -> 1
            else -> 0
        }
    }

    private fun AgentProviderMode.label(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL -> "Local model with router fallback"
            AgentProviderMode.LOCAL_ROUTER -> "Local router"
        }
    }

    private fun LocalModelStatus.shortLine(): String {
        return if (available) {
            "$runtime model available"
        } else {
            "$runtime fallback: ${message.substringBefore(';')}"
        }
    }

    private fun approveAgentTool(request: ToolApprovalRequest): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)

        runOnUiThread {
            val prompt = ChatEvent.ApprovalPrompt(
                request = request,
                onDecision = { decision ->
                    approved.set(decision)
                    latch.countDown()
                }
            )
            conversation += prompt
            showSection(Section.CHAT)
        }

        return latch.await(ApprovalTimeoutMs, TimeUnit.MILLISECONDS) && approved.get()
    }

    private fun buildApprovalMessage(request: ToolApprovalRequest): String {
        val redactedArgs = SensitiveTextRedactor.redact(request.args)
        val argsText = if (redactedArgs.isEmpty()) {
            "none"
        } else {
            redactedArgs.entries.joinToString(separator = "\n") { entry ->
                "${entry.key}: ${entry.value.take(MaxApprovalArgLength)}"
            }
        }

        return """
            Risk: ${request.tool.risk}
            Tool: ${request.tool.name}
            Description: ${request.tool.description}
            Why approval is needed: ${request.policy.reason}
            Data affected: ${request.policy.dataAffected}
            If approved: ${request.policy.ifApproved}

            Arguments:
            $argsText
        """.trimIndent()
    }

    private fun exportDebugTrace(): File {
        val directory = File(getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "touchpilot-trace-$timestamp.txt")
        file.writeText(
            buildString {
                appendLine("TouchPilot debug trace")
                appendLine("timestamp=$timestamp")
                appendLine()
                appendLine("Accessibility connected=${AccessibilityBridge.isConnected()}")
                appendLine()
                appendLine("Tool executions")
                appendLine(ToolExecutionLog.renderChronological())
                appendLine()
                appendLine("Current screen")
                appendLine(SensitiveTextRedactor.redact(toolExecutor.observeScreen()))
            }
        )
        return file
    }

    private sealed class ChatEvent {
        data class User(val text: String) : ChatEvent()
        data class Agent(val text: String, val detail: String) : ChatEvent()
        data class Timeline(val title: String, val body: String) : ChatEvent()
        class ApprovalPrompt(
            val request: ToolApprovalRequest,
            val onDecision: (Boolean) -> Unit
        ) : ChatEvent() {
            var state: ApprovalState = ApprovalState.PENDING
        }
    }

    private enum class ApprovalState { PENDING, APPROVED, REJECTED }

    private enum class Section(val label: String) {
        CHAT("Chat"),
        TOOLS("Tools"),
        LOGS("Logs"),
        SETTINGS("Settings")
    }

    private enum class SettingsPanel(val label: String) {
        SKILLS("Skills"),
        MCP("MCP"),
        CLOUD_API("Cloud API"),
        RUNTIME("Runtime")
    }

    private companion object {
        const val ApprovalTimeoutMs = 5 * 60 * 1000L
        const val MaxApprovalArgLength = 500
        val ProviderModeLabels = listOf("Local router", "Local model")
    }

    private object Theme {
        val Background: Int = Color.rgb(8, 13, 16)
        val SurfaceRaised: Int = Color.rgb(18, 28, 36)
        val Card: Int = Color.rgb(17, 25, 33)
        val StrokeDark: Int = Color.rgb(32, 45, 55)
        val Accent: Int = Color.rgb(47, 220, 105)
        val OnAccent: Int = Color.rgb(4, 28, 12)
        val BodyText: Int = Color.rgb(214, 223, 231)
        val MutedText: Int = Color.rgb(137, 151, 164)
        val NavText: Int = Color.rgb(156, 168, 178)
    }
}
