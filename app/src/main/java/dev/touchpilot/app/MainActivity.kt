package dev.touchpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
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
import dev.touchpilot.app.agent.AgentRunner
import dev.touchpilot.app.agent.LocalRouterCommandProvider
import dev.touchpilot.app.agent.OpenAiAgentCommandProvider
import dev.touchpilot.app.agent.ProviderConfig
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.mcp.McpHttpClient
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillStore
import dev.touchpilot.app.security.ProviderSecretStore
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
    private lateinit var secretStore: ProviderSecretStore
    private lateinit var skills: List<Skill>
    private lateinit var toolExecutor: AndroidToolExecutor
    private lateinit var contentRoot: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var executionLogView: TextView
    private var bottomNav: TabLayout? = null

    private var activeSection = Section.CHAT
    private var selectedSkillId: String? = null
    private val conversation = mutableListOf<ChatEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("touchpilot", MODE_PRIVATE)
        secretStore = ProviderSecretStore(this)
        skills = SkillStore(this).loadSkills()
        toolExecutor = AndroidToolExecutor(this)
        selectedSkillId = preferences.getString("active_skill", null)

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
            setSelectedTabIndicatorHeight(6)

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
            Section.SKILLS -> renderSkillsScreen()
            Section.RUNTIME -> renderRuntimeScreen()
            Section.TOOLS -> renderToolsScreen()
            Section.MCP -> renderMcpScreen()
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
            when (event) {
                is ChatEvent.User -> contentRoot.addView(userBubble(event.text))
                is ChatEvent.Agent -> contentRoot.addView(agentBubble(event.text, event.detail))
                is ChatEvent.Timeline -> contentRoot.addView(timelineCard(event.title, event.body))
            }
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
            when (event) {
                is ChatEvent.User -> contentRoot.addView(userBubble(event.text))
                is ChatEvent.Agent -> contentRoot.addView(agentBubble(event.text, event.detail))
                is ChatEvent.Timeline -> contentRoot.addView(timelineCard(event.title, event.body))
            }
        }
    }

    private fun runAgentFromChat(task: String) {
        conversation += ChatEvent.User(task)
        conversation += ChatEvent.Agent("Working on it.", "Runtime: ${currentProviderMode().label()}")
        showSection(Section.CHAT)

        Thread {
            val providerMode = currentProviderMode()
            val selectedSkill = selectedSkill()
            val providerConfig = ProviderConfig(
                baseUrl = preferences.getString("provider_url", DefaultProviderUrl).orEmpty(),
                apiKey = if (providerMode == AgentProviderMode.CLOUD) {
                    runCatching { secretStore.loadApiKey().orEmpty() }.getOrDefault("")
                } else {
                    ""
                },
                model = preferences.getString("provider_model", DefaultModel).orEmpty()
            )

            val transcript = runCatching {
                AgentRunner(
                    toolExecutor = toolExecutor,
                    approvalProvider = ToolApprovalProvider { request ->
                        approveAgentTool(request)
                    },
                    commandProvider = when (providerMode) {
                        AgentProviderMode.CLOUD -> OpenAiAgentCommandProvider(providerConfig)
                        AgentProviderMode.LOCAL_ROUTER -> LocalRouterCommandProvider(task, selectedSkill)
                    },
                    skill = selectedSkill,
                    source = when (providerMode) {
                        AgentProviderMode.CLOUD -> ToolSource.CLOUD_FALLBACK
                        AgentProviderMode.LOCAL_ROUTER -> ToolSource.LOCAL_ROUTER
                    }
                ).run(task).transcript
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

    private fun renderSkillsScreen() {
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
                showSection(Section.SKILLS)
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

    private fun renderRuntimeScreen() {
        contentRoot.addView(sectionTitle("Local Runtime"))
        contentRoot.addView(statusPill())
        contentRoot.addView(
            timelineCard(
                "Runtime boundary",
                """
                Local router is the default command provider. It maps simple tasks to typed Android tools without a network dependency.

                Future runtime layers will fit behind the same JSON command boundary:
                - small local routing model
                - local LLM runtime
                - experimental cloud fallback
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
                val mode = if (providerModeSpinner.selectedItemPosition == 1) {
                    AgentProviderMode.CLOUD
                } else {
                    AgentProviderMode.LOCAL_ROUTER
                }
                preferences.edit().putString("agent_provider_mode", mode.name).apply()
                showSection(Section.RUNTIME)
            }
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

    private fun renderMcpScreen() {
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
                showSection(Section.MCP)
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
                        showSection(Section.MCP)
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
                showSection(Section.MCP)
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
                        showSection(Section.MCP)
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

    private fun renderSettingsScreen() {
        contentRoot.addView(sectionTitle("Settings"))
        contentRoot.addView(timelineCard("Cloud fallback", "Optional. Local router remains the default runtime."))

        val urlInput = editText("Cloud fallback chat completions URL").apply {
            id = R.id.agent_provider_url_input
            setText(preferences.getString("provider_url", DefaultProviderUrl))
        }
        val modelInput = editText("Model name").apply {
            id = R.id.agent_model_input
            setText(preferences.getString("provider_model", DefaultModel))
        }
        val apiKeyInput = editText(
            if (secretStore.hasApiKey()) {
                "Cloud API key stored; leave blank to keep it"
            } else {
                "Cloud API key"
            }
        ).apply {
            id = R.id.agent_api_key_input
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        contentRoot.addView(urlInput)
        contentRoot.addView(modelInput)
        contentRoot.addView(apiKeyInput)
        contentRoot.addView(
            primaryButton("Save Settings") {
                preferences.edit()
                    .putString("provider_url", urlInput.text.toString())
                    .putString("provider_model", modelInput.text.toString())
                    .apply()
                resolveApiKey(apiKeyInput, secretStore)
                outputText = "Settings saved."
                showSection(Section.SETTINGS)
            }
        )
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
        return if (preferences.getString("agent_provider_mode", AgentProviderMode.LOCAL_ROUTER.name) == AgentProviderMode.CLOUD.name) {
            AgentProviderMode.CLOUD
        } else {
            AgentProviderMode.LOCAL_ROUTER
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
            "${currentProviderMode().label()}\n${if (AccessibilityBridge.isConnected()) "Accessibility connected" else "Accessibility not connected"}"
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
        return if (savedMode == AgentProviderMode.CLOUD.name) 1 else 0
    }

    private fun AgentProviderMode.label(): String {
        return when (this) {
            AgentProviderMode.CLOUD -> "Experimental cloud fallback"
            AgentProviderMode.LOCAL_ROUTER -> "Local router"
        }
    }

    private fun resolveApiKey(
        apiKeyInput: EditText,
        secretStore: ProviderSecretStore
    ): String {
        val enteredApiKey = apiKeyInput.text.toString()
        if (enteredApiKey.isNotBlank()) {
            secretStore.saveApiKey(enteredApiKey)
            apiKeyInput.text.clear()
            apiKeyInput.hint = "Cloud API key stored; leave blank to keep it"
            return enteredApiKey
        }

        return runCatching { secretStore.loadApiKey().orEmpty() }
            .getOrElse {
                secretStore.clearApiKey()
                ""
            }
    }

    private fun approveAgentTool(request: ToolApprovalRequest): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)
        val tool = request.tool

        runOnUiThread {
            conversation += ChatEvent.Timeline(
                "Approval requested",
                buildApprovalMessage(request)
            )
            showSection(Section.CHAT)
            AlertDialog.Builder(this)
                .setTitle("Approve ${tool.name}?")
                .setMessage(buildApprovalMessage(request))
                .setPositiveButton("Approve") { _, _ ->
                    approved.set(true)
                    conversation += ChatEvent.Timeline("Approval", "Approved ${tool.name}.")
                    latch.countDown()
                }
                .setNegativeButton("Deny") { _, _ ->
                    approved.set(false)
                    conversation += ChatEvent.Timeline("Approval", "Denied ${tool.name}.")
                    latch.countDown()
                }
                .setOnCancelListener {
                    approved.set(false)
                    conversation += ChatEvent.Timeline("Approval", "Approval cancelled for ${tool.name}.")
                    latch.countDown()
                }
                .show()
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
    }

    private enum class Section(val label: String) {
        CHAT("Chat"),
        SKILLS("Skills"),
        RUNTIME("Run"),
        TOOLS("Tools"),
        MCP("MCP"),
        LOGS("Logs"),
        SETTINGS("Settings")
    }

    private companion object {
        const val ApprovalTimeoutMs = 5 * 60 * 1000L
        const val MaxApprovalArgLength = 500
        const val DefaultProviderUrl = "https://api.openai.com/v1/chat/completions"
        const val DefaultModel = "gpt-5.2-mini"
        val ProviderModeLabels = listOf("Local router", "Experimental cloud fallback")
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
