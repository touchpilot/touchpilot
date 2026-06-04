package dev.touchpilot.app

import android.app.Activity
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunDisplayStep
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunStepStatus
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.DefaultLocalReasoningCore
import dev.touchpilot.app.agent.LocalReasoningContext
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.agent.defaultAgentRunInvocation
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillStore
import dev.touchpilot.app.navigation.AppSection
import dev.touchpilot.app.navigation.NavigationController
import dev.touchpilot.app.runtime.ToolExecutionCallbacks
import dev.touchpilot.app.runtime.ToolExecutionController
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.runtime.AgentRunController
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.label
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rounded
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.shortLine
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.chat.ChatEvent
import dev.touchpilot.app.ui.chat.ChatScreenRenderer
import dev.touchpilot.app.ui.logs.LogsScreenRenderer
import dev.touchpilot.app.ui.settings.SettingsScreenRenderer
import dev.touchpilot.app.ui.tools.ToolsScreenRenderer
import dev.touchpilot.app.ui.withMargins
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var preferences: SharedPreferences
    private lateinit var skills: List<Skill>
    private lateinit var toolExecutor: AndroidToolExecutor
    private lateinit var localModelRuntime: LiteRtCommandModelRuntime
    private lateinit var reasoningCore: LocalReasoningCore
    private lateinit var agentRunController: AgentRunController
    private lateinit var contentRoot: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var chatInputBar: LinearLayout
    private lateinit var chatTaskInput: EditText
    private lateinit var statusView: TextView
    private lateinit var executionLogList: LinearLayout
    private val navigationController = NavigationController()
    private var bottomNav: TabLayout? = null

    private var selectedSkillId: String? = null
    private var expandedSkillReferenceId: String? = null
    private var lastFocusInputArgs: Map<String, String>? = null
    private var focusSelectorIndex: Int = 0
    private val conversation = mutableListOf<ChatEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("touchpilot", MODE_PRIVATE)
        skills = SkillStore(this).loadSkills()
        ToolExecutionLog.configure(this)
        toolExecutor = AndroidToolExecutor(this)
        localModelRuntime = LiteRtCommandModelRuntime(this)
        selectedSkillId = preferences.getString("active_skill", null)

        reasoningCore = DefaultLocalReasoningCore(
            invocation = defaultAgentRunInvocation(
                toolExecutor = toolExecutor,
                approvalProvider = ToolApprovalProvider { request ->
                    agentRunController.approveTool(request)
                },
                localModelRuntime = localModelRuntime
            ),
            sessionContext = { currentReasoningContext() },
            availableSkills = { skills },
            screenContextProvider = { AccessibilityBridge.observeScreenContext() }
        )
        agentRunController = AgentRunController(
            reasoningCore = reasoningCore,
            conversation = conversation,
            currentProviderMode = ::currentProviderMode,
            runOnUiThread = { block -> runOnUiThread(block) },
            showChat = { showSection(AppSection.CHAT) },
            refreshExecutionLog = ::refreshExecutionLog,
            refreshStatus = ::refreshStatus,
            refreshStepTimeline = ::refreshStepTimeline,
        )

        if (conversation.isEmpty()) {
            conversation += ChatEvent.Agent(
                "What would you like me to do?",
                "Local router is ready for simple Android actions."
            )
        }

        setContentView(buildRoot())
        showSection(AppSection.CHAT)
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        if (::contentRoot.isInitialized) {
            showSection(navigationController.activeSection)
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

            chatInputBar = chatScreenRenderer().buildChatInputBar()
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

            AppSection.values().forEach { section ->
                addTab(
                    newTab()
                        .setCustomView(bottomNavLabel(section))
                        .setTag(section),
                    section == navigationController.activeSection
                )
            }

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    (tab.tag as? AppSection)?.let { section ->
                        if (section != navigationController.activeSection) {
                            showSection(section)
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }.withMargins()
    }

    private fun bottomNavLabel(section: AppSection): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = 0
            minimumHeight = 56
            setPadding(8, 6, 8, 6)
        }
        column.addView(
            ImageView(this).apply {
                setImageResource(section.iconRes)
                imageTintList = ColorStateList.valueOf(Theme.NavText)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = section.label
            },
            LinearLayout.LayoutParams(40, 40)
        )
        column.addView(
            TextView(this).apply {
                text = section.label
                gravity = Gravity.CENTER
                setSingleLine(true)
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Theme.NavText)
                setPadding(0, 2, 0, 0)
            }
        )
        return column
    }

    private fun showSection(section: AppSection) {
        navigationController.showSection(section)
        updateBottomNav()
        chatInputBar.visibility = if (section == AppSection.CHAT) View.VISIBLE else View.GONE
        contentRoot.removeAllViews()
        when (section) {
            AppSection.CHAT -> renderChatScreen()
            AppSection.TOOLS -> renderToolsScreen()
            AppSection.LOGS -> renderLogsScreen()
            AppSection.SETTINGS -> renderSettingsScreen()
        }
        animatePendingSettingsTransition(section)
    }

    private fun animatePendingSettingsTransition(section: AppSection) {
        if (section != AppSection.SETTINGS) return

        val direction = navigationController.consumeSettingsAnimationDirection()
        if (direction == 0) return
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
        val activeSection = navigationController.activeSection
        val index = AppSection.values().indexOf(activeSection)
        if (index >= 0 && nav.selectedTabPosition != index) {
            nav.getTabAt(index)?.select()
        }
        AppSection.values().forEachIndexed { tabIndex, section ->
            val container = nav.getTabAt(tabIndex)?.customView as? LinearLayout
            val selected = section == activeSection
            val tint = if (selected) Theme.OnAccent else Theme.NavText
            (container?.getChildAt(0) as? ImageView)?.imageTintList = ColorStateList.valueOf(tint)
            (container?.getChildAt(1) as? TextView)?.setTextColor(tint)
            container?.background = rounded(
                fill = if (selected) Theme.Accent else Color.TRANSPARENT,
                radius = 10,
                stroke = if (selected) Theme.Accent else Color.TRANSPARENT
            )
        }
    }

    private fun submitChatMessage() {
        val task = chatTaskInput.text.toString().trim()
        if (task.isEmpty()) return
        hideKeyboard(chatTaskInput)
        chatTaskInput.text.clear()
        agentRunController.startFromChat(task)
    }

    private fun chatScreenRenderer(): ChatScreenRenderer {
        return ChatScreenRenderer(
            activity = this,
            scrollView = scrollView,
            contentRoot = contentRoot,
            conversation = conversation,
            statusPill = ::statusPill,
            agentRunState = { agentRunController.runState },
            runtimeLabel = { currentProviderMode().label() },
            skillTitle = { selectedSkill()?.title ?: "No skill selected" },
            setChatTaskInput = { chatTaskInput = it },
            submitChatMessage = ::submitChatMessage,
            cancelAgentRun = agentRunController::cancelRun,
            openRunDetail = ::openRunDetail,
            refreshChatScreen = { showSection(AppSection.CHAT) },
            buildApprovalMessage = { buildApprovalMessage(it.request) },
            formatToolCallBody = ::formatToolCallBody,
        )
    }

    private fun renderChatScreen() {
        if (navigationController.activeRunDetailId != null) {
            renderAgentRunDetailScreen()
            return
        }

        chatScreenRenderer().render()
    }

    private fun scrollChatToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun refreshStepTimeline(
        event: ChatEvent.StepTimeline,
        steps: List<AgentStep>,
        complete: Boolean = false
    ) {
        event.steps = steps
        event.isComplete = complete || event.isComplete
        chatScreenRenderer().bindStepTimeline(event)
        scrollChatToBottom()
    }

    private fun currentReasoningContext(): LocalReasoningContext {
        val providerMode = currentProviderMode()
        return LocalReasoningContext(
            skill = selectedSkill(),
            providerMode = providerMode
        )
    }

    private fun commitSelectedSkill(id: String?) {
        selectedSkillId = id
        expandedSkillReferenceId = when {
            id == null -> null
            expandedSkillReferenceId == id -> null
            else -> id
        }
        preferences.edit().putString("active_skill", selectedSkillId).apply()
        showSection(AppSection.SETTINGS)
    }

    private fun renderToolsScreen() {
        ToolsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            toolExecutionController = toolExecutionController(),
            statusPill = ::statusPill,
            openAccessibilitySettings = ::openAccessibilitySettings,
            toolsResult = sectionResults::forTools,
            refreshToolsScreen = { showSection(AppSection.TOOLS) },
            hideKeyboard = ::hideKeyboard,
            bindKeyboardScrollSpacer = ::bindKeyboardScrollSpacer,
            getFocusSelectorIndex = { focusSelectorIndex },
            setFocusSelectorIndex = { focusSelectorIndex = it },
            getLastFocusInputArgs = { lastFocusInputArgs },
            setLastFocusInputArgs = { lastFocusInputArgs = it }
        ).render()
    }

    private fun toolExecutionController(): ToolExecutionController {
        return ToolExecutionController(
            activity = this,
            toolExecutor = toolExecutor,
            callbacks = object : ToolExecutionCallbacks {
                override fun recordToolsResult(message: String) {
                    sectionResults.recordToolsResult(message)
                }

                override fun refreshDeveloperLogs() {
                    refreshExecutionLog()
                }

                override fun refreshToolsScreen() {
                    showSection(AppSection.TOOLS)
                }
            }
        )
    }

    private fun bindKeyboardScrollSpacer(spacer: View) {
        val rootView = window.decorView
        val expandedHeight = (320 * resources.displayMetrics.density).toInt()
        val visibleRect = Rect()
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            rootView.getWindowVisibleDisplayFrame(visibleRect)
            val screenHeight = rootView.height
            if (screenHeight <= 0) return@OnGlobalLayoutListener
            val occluded = screenHeight - visibleRect.bottom
            // Anything above ~15% of screen height is conservatively treated
            // as the soft keyboard rather than a tall status/nav bar.
            val keyboardVisible = occluded > screenHeight * 0.15
            val target = if (keyboardVisible) expandedHeight else 0
            val params = spacer.layoutParams as? LinearLayout.LayoutParams ?: return@OnGlobalLayoutListener
            if (params.height != target) {
                params.height = target
                spacer.layoutParams = params
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(listener)
        spacer.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                rootView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
        })
    }

    private fun renderLogsScreen() {
        if (navigationController.activeRunDetailId != null) {
            renderAgentRunDetailScreen()
            return
        }

        executionLogList = logsScreenRenderer().render()
    }

    private fun renderSettingsScreen() {
        SettingsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            preferences = preferences,
            skills = skills,
            localModelRuntime = localModelRuntime,
            activeSettingsPanel = { navigationController.activeSettingsPanel },
            openSettingsPanel = navigationController::openSettingsPanel,
            closeSettingsPanel = navigationController::closeSettingsPanel,
            selectedSkillId = { selectedSkillId },
            expandedSkillReferenceId = { expandedSkillReferenceId },
            commitSelectedSkill = ::commitSelectedSkill,
            currentProviderMode = ::currentProviderMode,
            openAccessibilitySettings = ::openAccessibilitySettings,
            hideKeyboard = ::hideKeyboard,
            recordMcpResult = sectionResults::recordMcpResult,
            mcpResult = sectionResults::forMcp,
            refreshSettingsScreen = { showSection(AppSection.SETTINGS) }
        ).render()
    }

    private val sectionResults = SectionResultStore()

    private fun refreshExecutionLog() {
        if (::executionLogList.isInitialized) {
            logsScreenRenderer().renderLogRows(executionLogList)
        }
    }

    private fun logsScreenRenderer(): LogsScreenRenderer {
        return LogsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            latestResult = sectionResults::forLogs,
            exportDebugTrace = ::exportDebugTrace,
            recordLogsResult = sectionResults::recordLogsResult,
            refreshLogsScreen = { showSection(AppSection.LOGS) }
        )
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

    private fun hideKeyboard(anchor: View) {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun openAccessibilitySettings() {
        startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
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

    private fun formatToolCallBody(cardModel: ToolCallCardModel): String {
        return buildString {
            appendLine("Arguments:")
            append(formatToolArgs(cardModel.args))
            if (cardModel.message.isNotBlank()) {
                appendLine()
                appendLine()
                append("Result: ")
                append(cardModel.message)
            }
            if (!cardModel.verificationStatus.isNullOrBlank()) {
                appendLine()
                appendLine()
                append("Verification: ")
                append(cardModel.verificationStatus)
                if (!cardModel.verificationReason.isNullOrBlank()) {
                    append(" - ")
                    append(cardModel.verificationReason)
                }
            }
        }
    }

    private fun formatToolArgs(args: Map<String, String>): String {
        if (args.isEmpty()) return "none"
        return args.entries.joinToString(separator = "\n") { entry ->
            "${entry.key}: ${entry.value.take(MaxToolCardFieldLength)}"
        }
    }

    private fun openRunDetail(runId: String) {
        navigationController.openRunDetail(runId)
        showSection(navigationController.activeSection)
    }

    private fun closeRunDetail() {
        navigationController.closeRunDetail()
        showSection(navigationController.activeSection)
    }

    private fun findAgentRun(runId: String): AgentRunRecord? {
        return agentRunController.findRun(runId)
    }

    private fun renderRecentAgentRuns() {
        contentRoot.addView(
            TextView(this).apply {
                text = "Recent agent runs"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 12, 0, 4)
            }
        )
        if (agentRunController.runHistory.isEmpty()) {
            contentRoot.addView(
                timelineCard(
                    title = "No agent runs yet",
                    body = "Run a task from Chat to inspect structured run details here."
                )
            )
            return
        }

        agentRunController.runHistory.asReversed().forEach { record ->
            contentRoot.addView(
                timelineCard(
                    title = record.task,
                    body = AgentRunDetailFormatter.compactSummary(record),
                    actionHint = "Tap for run details",
                    onClick = { openRunDetail(record.id) }
                )
            )
        }
    }

    private fun renderAgentRunDetailScreen() {
        val runId = navigationController.activeRunDetailId
        contentRoot.addView(sectionTitle("Run details"))
        contentRoot.addView(
            secondaryButton("Go Back") {
                closeRunDetail()
            }.apply {
                id = R.id.run_detail_back_button
                minHeight = 46
            }.withMargins(bottom = 12)
        )

        val record = runId?.let(::findAgentRun)
        if (record == null) {
            contentRoot.addView(
                timelineCard(
                    title = "Run unavailable",
                    body = "This run is no longer available. It may have been cleared when the app restarted."
                )
            )
            return
        }

        contentRoot.addView(
            summaryCard(
                title = "Task",
                value = SensitiveTextRedactor.redact(record.task),
                chipText = record.id,
                chipAccent = true
            )
        )
        contentRoot.addView(
            timelineCard(
                title = "Timing",
                body = buildString {
                    append("Started: ${AgentRunDetailFormatter.formatTimestamp(record.startedAtMillis)}")
                    appendLine()
                    append("Completed: ${AgentRunDetailFormatter.formatTimestamp(record.completedAtMillis)}")
                }
            )
        )
        contentRoot.addView(
            timelineCard(
                title = "Stop reason",
                body = AgentRunDetailFormatter.deriveStopReason(record)
            )
        )
        contentRoot.addView(
            primaryButton("Export Run Trace") {
                val file = exportRunTrace(record)
                sectionResults.recordLogsResult("Run trace exported: ${file.absolutePath}")
                showSection(navigationController.activeSection)
            }.apply { id = R.id.export_run_trace_button }
        )

        val steps = AgentRunDetailFormatter.formatSteps(record)
        contentRoot.addView(
            TextView(this).apply {
                text = if (steps.isEmpty()) "Steps" else "Steps (${steps.size})"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 12, 0, 4)
            }
        )
        if (steps.isEmpty()) {
            contentRoot.addView(
                timelineCard(
                    title = "No step data",
                    body = record.errorMessage?.let { error ->
                        "Run failed before structured events were recorded: ${SensitiveTextRedactor.redact(error)}"
                    } ?: "Structured run events are unavailable for this run."
                )
            )
            return
        }

        steps.forEach { step ->
            contentRoot.addView(runDetailStepCard(step))
        }
    }

    private fun runDetailStepCard(step: AgentRunDisplayStep): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stepStatusColor(step.status)
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = "Step ${step.index}"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(statusChip(step.status.label, accent = step.status != AgentRunStepStatus.INFO))
        content.addView(header)
        content.addView(
            TextView(this).apply {
                text = step.title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 0)
            }
        )
        content.addView(
            TextView(this).apply {
                text = AgentRunDetailFormatter.formatTimestamp(step.timestampMillis)
                textSize = 11f
                setTextColor(Theme.MutedText)
                setPadding(0, 4, 0, 0)
            }
        )
        content.addView(
            TextView(this).apply {
                text = step.detail
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 6, bottom = 6)
    }

    private fun stepStatusColor(status: AgentRunStepStatus): Int {
        return when (status) {
            AgentRunStepStatus.SUCCESS,
            AgentRunStepStatus.COMPLETE -> Theme.Accent
            AgentRunStepStatus.FAILED,
            AgentRunStepStatus.BLOCKED -> Theme.StrokeDark
            AgentRunStepStatus.RUNNING,
            AgentRunStepStatus.WAITING,
            AgentRunStepStatus.PENDING -> Color.rgb(255, 196, 86)
            AgentRunStepStatus.INFO -> Theme.StrokeDark
        }
    }

    private fun exportRunTrace(record: AgentRunRecord): File {
        val directory = File(getExternalFilesDir(null), "debug-traces").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "touchpilot-run-${record.id}-$timestamp.txt")
        file.writeText(AgentRunDetailFormatter.exportRedactedTrace(record))
        return file
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

    private companion object {
        const val MaxApprovalArgLength = 500
        const val MaxToolCardFieldLength = 700
        val ProviderModeLabels = listOf("Local router", "Local model")
    }

}
