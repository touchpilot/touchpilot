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
import android.view.animation.DecelerateInterpolator
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
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
import dev.touchpilot.app.tools.ToolResult
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
    private lateinit var scrollView: ScrollView
    private lateinit var chatInputBar: LinearLayout
    private lateinit var chatTaskInput: EditText
    private lateinit var statusView: TextView
    private lateinit var executionLogView: TextView
    private lateinit var latestResultView: TextView
    private var bottomNav: TabLayout? = null

    private var activeSection = Section.CHAT
    private var activeSettingsPanel: SettingsPanel? = null
    private var pendingSettingsAnimationDirection = 0
    private var selectedSkillId: String? = null
    private var expandedSkillReferenceId: String? = null
    private var lastFocusInputArgs: Map<String, String>? = null
    private var focusSelectorIndex: Int = 0
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

            scrollView = ScrollView(this@MainActivity).apply {
                id = R.id.chat_scroll_view
                setFillViewport(false)
                isScrollbarFadingEnabled = true
                contentRoot = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 18, 24, 20)
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

            chatInputBar = buildChatInputBar()
            addView(chatInputBar)

            addView(buildBottomNav())
        }
    }

    private fun buildHeader(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 64, 28, 12)
            setBackgroundColor(Theme.Background)

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            row.addView(
                TextView(this@MainActivity).apply {
                    id = R.id.touchpilot_title
                    text = "Touch"
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                }
            )
            row.addView(
                TextView(this@MainActivity).apply {
                    text = "Pilot"
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Theme.Accent)
                }
            )

            addView(row)

            statusView = TextView(this@MainActivity).apply {
                id = R.id.touchpilot_status
                textSize = 11.5f
                setPadding(0, 6, 0, 0)
                setTextColor(Color.rgb(150, 164, 178))
            }
            addView(statusView)
        }
    }

    private fun buildBottomNav(): View {
        return TabLayout(this).apply {
            bottomNav = this
            setBackgroundColor(Theme.Card)
            setPadding(12, 8, 12, 18)
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_FILL
            setSelectedTabIndicatorColor(Color.TRANSPARENT)
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
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            minWidth = 0
            minHeight = 44
            setPadding(8, 10, 8, 10)
        }
    }

    private fun showSection(section: Section) {
        activeSection = section
        updateBottomNav()
        chatInputBar.visibility = if (section == Section.CHAT) View.VISIBLE else View.GONE
        contentRoot.removeAllViews()
        when (section) {
            Section.CHAT -> renderChatScreen()
            Section.TOOLS -> renderToolsScreen()
            Section.LOGS -> renderLogsScreen()
            Section.SETTINGS -> renderSettingsScreen()
        }
        animatePendingSettingsTransition(section)
    }

    private fun animatePendingSettingsTransition(section: Section) {
        if (section != Section.SETTINGS || pendingSettingsAnimationDirection == 0) return

        val direction = pendingSettingsAnimationDirection
        pendingSettingsAnimationDirection = 0
        val travel = (contentRoot.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels).toFloat()
        contentRoot.translationX = travel * direction
        contentRoot.alpha = 0.96f
        contentRoot.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun updateBottomNav() {
        val nav = bottomNav ?: return
        val index = Section.values().indexOf(activeSection)
        if (index >= 0 && nav.selectedTabPosition != index) {
            nav.getTabAt(index)?.select()
        }
        Section.values().forEachIndexed { tabIndex, section ->
            val label = nav.getTabAt(tabIndex)?.customView as? TextView
            val selected = section == activeSection
            label?.setTextColor(if (selected) Theme.OnAccent else Theme.NavText)
            label?.background = rounded(
                fill = if (selected) Theme.Accent else Color.TRANSPARENT,
                radius = 10,
                stroke = if (selected) Theme.Accent else Color.TRANSPARENT
            )
        }
    }

    private fun buildChatInputBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            id = R.id.chat_input_bar
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(Theme.SurfaceRaised)
            setPadding(20, 12, 20, 10)
        }
        bar.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
                setBackgroundColor(Theme.StrokeDark)
            }
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 0)
        }
        chatTaskInput = EditText(this).apply {
            id = R.id.agent_task_input
            hint = "Message TouchPilot..."
            setSingleLine(false)
            minLines = 2
            maxLines = 4
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            background = rounded(Theme.Card, 10, Theme.StrokeDark)
            setPadding(18, 12, 18, 12)
            minHeight = 64
            imeOptions = EditorInfo.IME_ACTION_SEND.toInt()
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitChatMessage()
                    true
                } else {
                    false
                }
            }
        }
        inputRow.addView(
            chatTaskInput,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        )
        inputRow.addView(
            MaterialButton(this).apply {
                id = R.id.run_agent_button
                text = "Send"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                minHeight = 44
                minWidth = 88
                insetTop = 0
                insetBottom = 0
                gravity = Gravity.CENTER
                setTextColor(Theme.OnAccent)
                backgroundTintList = ColorStateList.valueOf(Theme.Accent)
                cornerRadius = 10
                setPadding(12, 0, 12, 0)
                setOnClickListener { submitChatMessage() }
            },
            LinearLayout.LayoutParams(
                88,
                52
            ).apply {
                leftMargin = 8
            }
        )
        bar.addView(inputRow)
        return bar
    }

    private fun submitChatMessage() {
        val task = chatTaskInput.text.toString().trim()
        if (task.isEmpty()) return
        hideKeyboard(chatTaskInput)
        chatTaskInput.text.clear()
        runAgentFromChat(task)
    }

    private fun renderChatScreen() {
        contentRoot.addView(sectionTitle("Chat"))
        contentRoot.addView(statusPill())
        contentRoot.addView(skillPill())

        conversation.forEach { event ->
            contentRoot.addView(renderChatEvent(event))
        }

        contentRoot.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    24
                )
            }
        )
        scrollChatToBottom()
    }

    private fun scrollChatToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
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
            radius = 8f
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
        return card.withMargins(top = 8, bottom = 8)
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

    private fun renderSkillsPanel() {
        val active = selectedSkill()
        contentRoot.addView(
            summaryCard(
                title = "Active skill",
                value = active?.title ?: "No skill selected",
                chipText = if (active != null) "${active.allowedTools.size} tools" else "none",
                chipAccent = active != null
            )
        )

        contentRoot.addView(formLabel("Available skills"))
        if (skills.isEmpty()) {
            contentRoot.addView(timelineCard("Installed skills", "No bundled skills found."))
            return
        }

        contentRoot.addView(
            skillSelectRow(
                title = "No skill",
                subtitle = "Run TouchPilot without a skill scope",
                badge = null,
                selected = selectedSkillId == null
            ) { commitSelectedSkill(null) }
        )
        skills.forEach { skill ->
            val description = skill.markdown.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("-") }
                .orEmpty()
            contentRoot.addView(
                skillSelectRow(
                    title = skill.title,
                    subtitle = description.ifBlank { "No description provided" },
                    badge = "${skill.allowedTools.size} tools",
                    selected = selectedSkillId == skill.id
                ) { commitSelectedSkill(skill.id) }
            )
            if (expandedSkillReferenceId == skill.id) {
                contentRoot.addView(
                    timelineCard(
                        "Skill reference",
                        buildString {
                            appendLine(skill.markdown.trim())
                            appendLine()
                            appendLine("Allowed tools:")
                            skill.allowedTools.forEach { appendLine("- $it") }
                        }.trim()
                    )
                )
            }
        }
    }

    private fun commitSelectedSkill(id: String?) {
        selectedSkillId = id
        expandedSkillReferenceId = when {
            id == null -> null
            expandedSkillReferenceId == id -> null
            else -> id
        }
        preferences.edit().putString("active_skill", selectedSkillId).apply()
        showSection(Section.SETTINGS)
    }

    private fun renderRuntimePanel() {
        val mode = currentProviderMode()
        val localStatus = localModelRuntime.status()
        contentRoot.addView(
            summaryCard(
                title = "Current runtime",
                value = mode.label(),
                chipText = if (localStatus.available) "model ready" else "fallback",
                chipAccent = localStatus.available
            )
        )

        contentRoot.addView(formLabel("Runtime mode"))
        AgentProviderMode.values().forEach { option ->
            contentRoot.addView(
                skillSelectRow(
                    title = option.label(),
                    subtitle = option.description(),
                    badge = null,
                    selected = option == mode
                ) {
                    preferences.edit().putString("agent_provider_mode", option.name).apply()
                    showSection(Section.SETTINGS)
                }
            )
        }

        contentRoot.addView(
            secondaryButton("Open Accessibility Settings") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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

        contentRoot.addView(
            secondaryButton("Get Foreground App") {
                executeAndRender("get_foreground_app", emptyMap())
                showSection(Section.TOOLS)
            }.apply { id = R.id.get_foreground_app_button }
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
                val focusResult = lastFocusInputArgs?.let { focusArgs ->
                    executeAndRender("focus_input", focusArgs)
                }
                if (focusResult == null || focusResult.ok) {
                    executeAndRender("type_text", mapOf("text" to value))
                }
            }.apply { id = R.id.type_text_button }
        )

        contentRoot.addView(formLabel("Focus selector"))

        val focusInputField = editText(FocusInputSelectorHints[focusSelectorIndex]).apply {
            id = R.id.focus_input_input
        }
        val segmentButtons = mutableListOf<MaterialButton>()

        fun refreshSegments() {
            segmentButtons.forEachIndexed { i, btn ->
                val active = i == focusSelectorIndex
                btn.backgroundTintList = ColorStateList.valueOf(
                    if (active) Theme.Accent else Theme.SurfaceRaised
                )
                btn.setTextColor(if (active) Theme.OnAccent else Theme.MutedText)
                btn.strokeWidth = if (active) 0 else 1
            }
            focusInputField.hint = FocusInputSelectorHints[focusSelectorIndex]
        }

        val selectorRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 4) }
        }
        FocusInputSelectorLabels.forEachIndexed { i, label ->
            val btn = MaterialButton(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                cornerRadius = 16
                minHeight = 44
                insetTop = 0
                insetBottom = 0
                strokeColor = ColorStateList.valueOf(Theme.StrokeDark)
                setOnClickListener {
                    focusSelectorIndex = i
                    refreshSegments()
                }
            }
            segmentButtons.add(btn)
            selectorRow.addView(
                btn,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(3, 0, 3, 0)
                }
            )
        }
        refreshSegments()

        contentRoot.addView(selectorRow)
        contentRoot.addView(focusInputField)
        contentRoot.addView(
            secondaryButton("Focus Input Field") {
                hideKeyboard(focusInputField)
                val args = mapOf(focusInputSelectorKey(focusSelectorIndex) to focusInputField.text.toString())
                val result = executeAndRender("focus_input", args)
                if (result.ok) {
                    lastFocusInputArgs = args
                } else {
                    lastFocusInputArgs = null
                }
            }.apply { id = R.id.focus_input_button }
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

        contentRoot.addView(latestResultCard())
    }

    private fun renderMcpPanel() {
        val savedEndpoint = preferences.getString("mcp_endpoint", "").orEmpty()
        contentRoot.addView(
            summaryCard(
                title = "MCP endpoint",
                value = savedEndpoint.ifBlank { "Not configured" },
                chipText = if (savedEndpoint.isBlank()) "not set" else "configured",
                chipAccent = savedEndpoint.isNotBlank()
            )
        )

        contentRoot.addView(formLabel("Server"))
        val endpointInput = editText("MCP HTTP JSON-RPC endpoint").apply {
            id = R.id.mcp_endpoint_input
            setText(savedEndpoint)
        }
        contentRoot.addView(endpointInput)

        contentRoot.addView(formLabel("Tool call"))
        val toolInput = editText("MCP tool name").apply { id = R.id.mcp_tool_input }
        val argsInput = editText("MCP tool arguments JSON").apply {
            id = R.id.mcp_args_input
            setSingleLine(false)
            minLines = 3
            setText("{}")
        }
        contentRoot.addView(toolInput)
        contentRoot.addView(argsInput)

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(
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
        actionRow.addView(
            primaryButton("Call Tool") {
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
        contentRoot.addView(actionRow)

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
            setPadding(16, 16, 16, 16)
            background = rounded(Theme.Card, 8, Theme.StrokeDark)
        }
        contentRoot.addView(executionLogView)
    }

    private fun renderSettingsScreen() {
        contentRoot.addView(sectionTitle("Settings"))
        val panel = activeSettingsPanel
        if (panel == null) {
            contentRoot.addView(settingsIntro("Choose a settings area to configure TouchPilot."))
            contentRoot.addView(settingsPanelSwitcher())
            return
        }

        contentRoot.addView(settingsIntro(panel.intro))
        contentRoot.addView(settingsGoBackButton())
        when (activeSettingsPanel) {
            SettingsPanel.SKILLS -> renderSkillsPanel()
            SettingsPanel.MCP -> renderMcpPanel()
            SettingsPanel.CLOUD -> renderCloudPanel()
            SettingsPanel.RUNTIME -> renderRuntimePanel()
            null -> Unit
        }
    }

    private fun settingsIntro(value: String): View {
        return TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(Theme.MutedText)
            setPadding(0, 0, 0, 12)
        }
    }

    private fun settingsGoBackButton(): View {
        return secondaryButton("Go Back") {
            activeSettingsPanel = null
            pendingSettingsAnimationDirection = -1
            showSection(Section.SETTINGS)
        }.apply {
            minHeight = 46
        }.withMargins(bottom = 12)
    }

    private fun renderCloudPanel() {
        val savedUrl = preferences.getString("agent_provider_url", "").orEmpty()
        val savedModel = preferences.getString("agent_model", "").orEmpty()
        val savedKey = preferences.getString("agent_api_key", "").orEmpty()
        val configured = savedUrl.isNotBlank() && savedModel.isNotBlank() && savedKey.isNotBlank()

        contentRoot.addView(
            summaryCard(
                title = "Cloud profile",
                value = if (configured) "$savedModel via ${shortHost(savedUrl)}" else "Not configured",
                chipText = if (configured) "configured" else "incomplete",
                chipAccent = configured
            )
        )

        contentRoot.addView(formLabel("Provider URL"))
        val providerInput = editText("https://api.example.com/v1").apply {
            id = R.id.agent_provider_url_input
            setText(savedUrl)
        }
        contentRoot.addView(providerInput)

        contentRoot.addView(formLabel("Model"))
        val modelInput = editText("e.g. gpt-4o-mini").apply {
            id = R.id.agent_model_input
            setText(savedModel)
        }
        contentRoot.addView(modelInput)

        contentRoot.addView(formLabel("API key"))
        val apiKeyInput = editText("paste API key").apply {
            id = R.id.agent_api_key_input
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(savedKey)
        }
        contentRoot.addView(apiKeyInput)

        contentRoot.addView(
            primaryButton("Save Cloud API") {
                preferences.edit()
                    .putString("agent_provider_url", providerInput.text.toString().trim())
                    .putString("agent_model", modelInput.text.toString().trim())
                    .putString("agent_api_key", apiKeyInput.text.toString().trim())
                    .apply()
                hideKeyboard(apiKeyInput)
                outputText = "Cloud API settings saved."
                showSection(Section.SETTINGS)
            }.apply { id = R.id.save_cloud_api_button }
        )

        contentRoot.addView(
            timelineCard(
                "Stored profile",
                buildString {
                    appendLine("Provider URL: ${savedUrl.ifBlank { "not set" }}")
                    appendLine("Model: ${savedModel.ifBlank { "not set" }}")
                    append("API key: ${if (savedKey.isBlank()) "not set" else "configured"}")
                }
            )
        )
    }

    private var outputText: String = "No result yet."

    private fun executeAndRender(name: String, args: Map<String, String>): ToolResult {
        val result = toolExecutor.execute(name, args, ToolSource.DIRECT_DEBUG)
        outputText = SensitiveTextRedactor.redact("$name($args) -> ${result.ok}: ${result.message}")
        refreshLatestResult()
        refreshExecutionLog()
        return result
    }

    private fun refreshLatestResult() {
        if (::latestResultView.isInitialized) {
            latestResultView.text = outputText
        }
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

    private fun focusInputSelectorKey(index: Int): String {
        return when (index) {
            1 -> "node_id"
            2 -> "view_id"
            else -> "text"
        }
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
            setPadding(0, 2, 0, 10)
        }
    }

    private fun formLabel(text: String): TextView {
        return TextView(this).apply {
            setText(text)
            textSize = 12f
            setTextColor(Theme.MutedText)
            setPadding(0, 12, 0, 6)
        }
    }

    private fun editText(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            contentDescription = hintText
            setSingleLine(true)
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            background = rounded(Theme.SurfaceRaised, 8, Theme.StrokeDark)
            layoutParams = controlParams()
            minHeight = 52
            setPadding(16, 8, 16, 8)
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
            cornerRadius = 10
            layoutParams = controlParams()
            minHeight = 50
            setPadding(16, 12, 16, 12)
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
            cornerRadius = 10
            layoutParams = controlParams()
            minHeight = 48
            setPadding(14, 12, 14, 12)
            setOnClickListener { onClick() }
        }
    }

    private fun userBubble(text: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        row.addView(
            TextView(this).apply {
                setText(text)
                textSize = 14f
                setTextColor(Theme.OnAccent)
                background = rounded(Theme.Accent, 10, Theme.Accent)
                setPadding(18, 14, 18, 14)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 56
            }
        )
        return row.withMargins(top = 8, bottom = 8)
    }

    private fun agentBubble(text: String, detail: String): View {
        return timelineCard(text, detail).apply {
            (layoutParams as? LinearLayout.LayoutParams)?.rightMargin = 36
        }
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

    private fun settingsPanelSwitcher(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        SettingsPanel.values().forEach { panel ->
            val isActive = panel == activeSettingsPanel
            val row = MaterialCardView(this).apply {
                setCardBackgroundColor(if (isActive) Theme.Accent else Theme.Card)
                strokeColor = if (isActive) Theme.Accent else Theme.StrokeDark
                strokeWidth = 1
                radius = 8f
                cardElevation = 0f
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (activeSettingsPanel != panel) {
                        activeSettingsPanel = panel
                        pendingSettingsAnimationDirection = 1
                        showSection(Section.SETTINGS)
                    }
                }
                id = when (panel) {
                    SettingsPanel.SKILLS -> R.id.settings_panel_skills_button
                    SettingsPanel.MCP -> R.id.settings_panel_mcp_button
                    SettingsPanel.CLOUD -> R.id.settings_panel_cloud_button
                    SettingsPanel.RUNTIME -> R.id.settings_panel_runtime_button
                }
            }
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
            }
            content.addView(
                TextView(this).apply {
                    text = panel.label
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (isActive) Theme.OnAccent else Color.WHITE)
                }
            )
            content.addView(
                TextView(this).apply {
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

    private fun summaryCard(
        title: String,
        value: String,
        chipText: String,
        chipAccent: Boolean
    ): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = if (chipAccent) Theme.Accent else Theme.StrokeDark
            strokeWidth = if (chipAccent) 2 else 1
            radius = 8f
            cardElevation = 0f
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        textColumn.addView(
            TextView(this).apply {
                text = title
                textSize = 11.5f
                setTextColor(Theme.MutedText)
            }
        )
        textColumn.addView(
            TextView(this).apply {
                text = value
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 4, 0, 0)
            }
        )
        content.addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(statusChip(chipText, chipAccent))
        card.addView(content)
        return card.withMargins(top = 4, bottom = 12)
    }

    private fun statusChip(text: String, accent: Boolean): TextView {
        val fill = if (accent) Theme.Accent else Theme.SurfaceRaised
        val textColor = if (accent) Theme.OnAccent else Theme.MutedText
        val stroke = if (accent) Theme.Accent else Theme.StrokeDark
        return TextView(this).apply {
            setText(text)
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            isAllCaps = true
            background = rounded(fill, 8, stroke)
            setPadding(12, 5, 12, 5)
        }
    }

    private fun skillSelectRow(
        title: String,
        subtitle: String,
        badge: String?,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = if (selected) Theme.Accent else Theme.StrokeDark
            strokeWidth = if (selected) 2 else 1
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        column.addView(
            TextView(this).apply {
                text = title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (selected) Theme.Accent else Color.WHITE)
            }
        )
        column.addView(
            TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Theme.MutedText)
                setPadding(0, 3, 0, 0)
            }
        )
        row.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (badge != null) {
            row.addView(statusChip(badge, accent = selected))
        }
        card.addView(row)
        return card.withMargins(top = 6, bottom = 6)
    }

    private fun shortHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        val withoutScheme = trimmed.substringAfter("://", trimmed)
        return withoutScheme.substringBefore('/').ifBlank { trimmed }
    }

    private fun timelineCard(title: String, body: String): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = Theme.StrokeDark
            strokeWidth = 1
            radius = 8f
            cardElevation = 0f
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
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
        return card.withMargins(top = 8, bottom = 8)
    }

    private fun latestResultCard(): View {
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
                text = "Latest result"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        latestResultView = TextView(this).apply {
            text = outputText
            textSize = 12.5f
            setTextColor(Theme.BodyText)
            setPadding(0, 8, 0, 0)
        }
        content.addView(latestResultView)
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

    private fun AgentProviderMode.label(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL -> "Local model with router fallback"
            AgentProviderMode.LOCAL_ROUTER -> "Local router"
        }
    }

    private fun AgentProviderMode.description(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL ->
                "Use LiteRT command model first; fall back to deterministic routing if unavailable."
            AgentProviderMode.LOCAL_ROUTER ->
                "Use the deterministic router only. Predictable, no on-device model load."
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

    private enum class SettingsPanel(val label: String, val intro: String) {
        SKILLS(
            "Skills",
            "Skills bundle the tools and prompts TouchPilot uses for a kind of task."
        ),
        MCP(
            "MCP",
            "Connect TouchPilot to an external MCP HTTP JSON-RPC server to call its tools."
        ),
        CLOUD(
            "Cloud API",
            "Optional cloud agent endpoint. Used only when explicitly selected as the runtime."
        ),
        RUNTIME(
            "Runtime",
            "Choose how TouchPilot reasons about your requests on this device."
        )
    }

    private companion object {
        const val ApprovalTimeoutMs = 5 * 60 * 1000L
        const val MaxApprovalArgLength = 500
        val ProviderModeLabels = listOf("Local router", "Local model")
        val FocusInputSelectorLabels = listOf("Text", "Node ID", "View ID")
        val FocusInputSelectorHints = listOf(
            "Text or content description",
            "Node path  ·  e.g. 0.1.2",
            "Resource ID  ·  e.g. com.app:id/field"
        )
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
