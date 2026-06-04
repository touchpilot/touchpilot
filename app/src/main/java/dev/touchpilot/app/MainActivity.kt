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
import android.widget.Toast
import androidx.annotation.DrawableRes
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunCompletionSummary
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunDisplayStep
import dev.touchpilot.app.agent.AgentRunIds
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentRunState
import dev.touchpilot.app.agent.AgentRunStepStatus
import dev.touchpilot.app.agent.AgentScreenRecord
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepTimelineBuilder
import dev.touchpilot.app.agent.ConversationalGate
import dev.touchpilot.app.agent.DefaultLocalReasoningCore
import dev.touchpilot.app.agent.LocalReasoningContext
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.agent.defaultAgentRunInvocation
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillStore
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolResult
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
import dev.touchpilot.app.ui.chat.ApprovalState
import dev.touchpilot.app.ui.chat.ChatEvent
import dev.touchpilot.app.ui.chat.ChatScreenRenderer
import dev.touchpilot.app.ui.logs.LogsScreenRenderer
import dev.touchpilot.app.ui.settings.SettingsPanel
import dev.touchpilot.app.ui.settings.SettingsScreenRenderer
import dev.touchpilot.app.ui.tools.ToolsScreenRenderer
import dev.touchpilot.app.ui.withMargins
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
    private lateinit var executionLogList: LinearLayout
    private var bottomNav: TabLayout? = null

    private var activeSection = Section.CHAT
    private var activeSettingsPanel: SettingsPanel? = null
    private var pendingSettingsAnimationDirection = 0
    private var selectedSkillId: String? = null
    private var expandedSkillReferenceId: String? = null
    private var lastFocusInputArgs: Map<String, String>? = null
    private var focusSelectorIndex: Int = 0
    private val conversation = mutableListOf<ChatEvent>()
    private var agentRunState: AgentRunState = AgentRunState.IDLE
    private var agentCancellationSignal: AtomicBoolean = AtomicBoolean(false)
    private val agentRunHistory = mutableListOf<AgentRunRecord>()
    private var activeRunDetailId: String? = null
    private var pendingClarification: PendingClarification? = null

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

    private fun bottomNavLabel(section: Section): View {
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

    private fun showSection(section: Section) {
        if (section != Section.CHAT && section != Section.LOGS) {
            activeRunDetailId = null
        }
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
        runAgentFromChat(task)
    }

    private fun chatScreenRenderer(): ChatScreenRenderer {
        return ChatScreenRenderer(
            activity = this,
            scrollView = scrollView,
            contentRoot = contentRoot,
            conversation = conversation,
            statusPill = ::statusPill,
            agentRunState = { agentRunState },
            runtimeLabel = { currentProviderMode().label() },
            skillTitle = { selectedSkill()?.title ?: "No skill selected" },
            setChatTaskInput = { chatTaskInput = it },
            submitChatMessage = ::submitChatMessage,
            cancelAgentRun = ::cancelAgentRun,
            openRunDetail = ::openRunDetail,
            refreshChatScreen = { showSection(Section.CHAT) },
            buildApprovalMessage = { buildApprovalMessage(it.request) },
            formatToolCallBody = ::formatToolCallBody,
        )
    }

    private fun renderChatScreen() {
        if (activeRunDetailId != null) {
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

    private fun runAgentFromChat(task: String) {
        val pending = pendingClarification
        val originalTask = pending?.originalTask
        val agentTask = if (pending != null) {
            pendingClarification = null
            "${pending.originalTask}\n\nUser clarification: $task"
        } else {
            task
        }

        conversation += ChatEvent.User(task)
        ToolExecutionLog.recordChat(
            name = if (pending != null) "clarification_reply" else "user_message",
            actor = "User",
            message = task
        )

        val conversationalResponse = ConversationalGate.respond(agentTask)
        if (conversationalResponse != null) {
            conversation += ChatEvent.Agent(conversationalResponse.message, "")
            ToolExecutionLog.recordChat(
                name = "assistant_message",
                actor = "TouchPilot",
                message = conversationalResponse.message
            )
            showSection(Section.CHAT)
            return
        }

        val workingIndex = conversation.size
        agentCancellationSignal.set(false)
        setAgentRunState(AgentRunState.RUNNING)
        conversation += ChatEvent.Working("Working on it.", "Runtime: ${currentProviderMode().label()}")
        val stepTimeline = ChatEvent.StepTimeline()
        conversation += stepTimeline
        showSection(Section.CHAT)

        val runId = AgentRunIds.next()
        val startedAtMillis = System.currentTimeMillis()
        val taskForRecord = originalTask ?: task
        ToolExecutionLog.recordAction(
            name = "agent_run_started",
            result = taskForRecord,
            status = "running",
            source = currentProviderMode().toLogSource()
        )
        val initialScreenRecord = AgentScreenRecord.capture(
            sequenceNumber = 0,
            phase = "initial",
            timestampMillis = startedAtMillis,
            context = AccessibilityBridge.observeScreenContext()
        )

        Thread {
            val timelineBuilder = AgentStepTimelineBuilder()
            val runOutcome = runCatching {
                reasoningCore.run(
                    task = agentTask,
                    timeline = timelineBuilder,
                    listener = AgentEventListener {
                        runOnUiThread {
                            refreshStepTimeline(stepTimeline, timelineBuilder.snapshot)
                        }
                    },
                    cancellationSignal = agentCancellationSignal
                )
            }
            val completedAtMillis = System.currentTimeMillis()
            val screenRecords = listOf(
                initialScreenRecord,
                AgentScreenRecord.capture(
                    sequenceNumber = 1,
                    phase = "final",
                    timestampMillis = completedAtMillis,
                    context = AccessibilityBridge.observeScreenContext()
                )
            )
            val record = if (runOutcome.isSuccess) {
                AgentRunRecord(
                    id = runId,
                    task = taskForRecord,
                    startedAtMillis = startedAtMillis,
                    completedAtMillis = completedAtMillis,
                    result = runOutcome.getOrThrow(),
                    screenRecords = screenRecords
                )
            } else {
                AgentRunRecord(
                    id = runId,
                    task = taskForRecord,
                    startedAtMillis = startedAtMillis,
                    completedAtMillis = completedAtMillis,
                    result = null,
                    errorMessage = runOutcome.exceptionOrNull()?.message ?: "Unknown agent error",
                    screenRecords = screenRecords
                )
            }

            runOnUiThread {
                val steps = if (runOutcome.isFailure) {
                    timelineBuilder.snapshot + timelineBuilder.failureStop(
                        "Agent failed: ${runOutcome.exceptionOrNull()?.message.orEmpty()}"
                    )
                } else {
                    timelineBuilder.snapshot
                }
                // Set agent run state based on outcome
                if (agentCancellationSignal.get()) {
                    setAgentRunState(AgentRunState.CANCELLED)
                } else if (runOutcome.isFailure ||
                           (record.result?.events?.any { it is AgentEvent.ToolFailed || it is AgentEvent.PolicyBlocked } == true)) {
                    setAgentRunState(AgentRunState.FAILED)
                    conversation.forEach { event ->
                        if (event is ChatEvent.ApprovalPrompt && event.state == ApprovalState.PENDING) {
                            event.state = ApprovalState.REJECTED
                        }
                    }
                } else {
                    setAgentRunState(AgentRunState.COMPLETED)
                }
                refreshStepTimeline(stepTimeline, steps, complete = true)
                finishAgentChatRun(
                    record = record,
                    runOutcome = runOutcome,
                    workingIndex = workingIndex,
                    resumeOriginalTask = originalTask,
                    timelineSteps = steps,
                )
            }
        }.start()
    }

    private fun finishAgentChatRun(
        record: AgentRunRecord,
        runOutcome: Result<AgentRunResult>,
        workingIndex: Int,
        resumeOriginalTask: String?,
        timelineSteps: List<AgentStep> = emptyList(),
    ) {
        removeWorkingIndicator(workingIndex)
        agentRunHistory += record
        ToolExecutionLog.recordAction(
            name = "agent_run_finished",
            result = record.errorMessage ?: AgentRunDetailFormatter.compactSummary(record),
            status = if (runOutcome.isSuccess) "complete" else "fail",
            source = currentProviderMode().toLogSource(),
            details = "run_id=${record.id}"
        )
        refreshExecutionLog()
        refreshStatus()

        val result = runOutcome.getOrNull()
        when {
            result?.stopReason == AgentStepStopReason.CLARIFICATION_NEEDED -> {
                val structured = result.events.filterIsInstance<AgentEvent.Clarification>().lastOrNull()
                val assistant = result.events.filterIsInstance<AgentEvent.AssistantMessage>().lastOrNull()
                val prompt = when {
                    structured != null -> ClarificationChatPrompt(
                        question = structured.question,
                        detail = structured.detail,
                        choices = structured.candidates.map {
                            SensitiveTextRedactor.redact(it.displayLabel)
                        },
                    )
                    assistant != null -> ClarificationChatPrompt(
                        question = assistant.text,
                        detail = assistant.detail,
                        choices = assistant.choices,
                    )
                    else -> null
                }
                if (prompt != null) {
                    val originalTask = resumeOriginalTask ?: record.task
                    pendingClarification = PendingClarification(originalTask = originalTask)
                    ToolExecutionLog.recordChat(
                        name = "clarification_prompt",
                        actor = "TouchPilot",
                        message = "${prompt.question}\n${prompt.detail}"
                    )
                    conversation += ChatEvent.ClarificationPrompt(
                        question = prompt.question,
                        detail = prompt.detail,
                        choices = prompt.choices,
                        onAnswer = { answer -> runAgentFromChat(answer) }
                    )
                } else {
                    conversation += ChatEvent.Agent(
                        "TouchPilot needs clarification before continuing.",
                        result.stopMessage
                    )
                    ToolExecutionLog.recordChat(
                        name = "assistant_message",
                        actor = "TouchPilot",
                        message = result.stopMessage
                    )
                }
            }
            result?.stopReason == AgentStepStopReason.COMPLETED &&
                isInformationalAssistantRun(result) -> {
                val assistant = result.events.filterIsInstance<AgentEvent.AssistantMessage>().last()
                conversation += ChatEvent.Agent(assistant.text, assistant.detail)
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = "${assistant.text}\n${assistant.detail}"
                )
            }
            runOutcome.isSuccess -> {
                record.result?.events
                    ?.let(ToolCallCardModel::fromEvents)
                    ?.forEach { card ->
                        conversation += ChatEvent.ToolCall(card)
                    }
                conversation += ChatEvent.CompletionSummary(
                    summary = AgentRunDetailFormatter.buildCompletionSummary(record, timelineSteps),
                    runId = record.id,
                )
                conversation += ChatEvent.Timeline(
                    title = "Action timeline",
                    body = AgentRunDetailFormatter.compactSummary(record),
                    runId = record.id
                )
                val finalAnswer = result?.finalAnswer
                val doneDetail = when {
                    finalAnswer != null -> finalAnswer
                    timelineSteps.isEmpty() -> "No steps recorded."
                    else -> "Tap the timeline card to inspect tool calls, verification, and stop reason."
                }
                conversation += ChatEvent.Agent("Done.", doneDetail)
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = doneDetail,
                    status = "complete"
                )
            }
            else -> {
                conversation += ChatEvent.Agent(
                    "Run failed.",
                    record.errorMessage ?: "Unknown agent error"
                )
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = record.errorMessage ?: "Unknown agent error",
                    status = "fail"
                )
            }
        }
        showSection(Section.CHAT)
    }

    private fun removeWorkingIndicator(workingIndex: Int) {
        if (workingIndex in conversation.indices &&
            conversation[workingIndex] is ChatEvent.Working
        ) {
            conversation.removeAt(workingIndex)
        }
    }

    private fun isInformationalAssistantRun(result: AgentRunResult): Boolean {
        val hasAssistant = result.events.any { it is AgentEvent.AssistantMessage }
        val invokedTools = result.events.any {
            it is AgentEvent.ToolRequested || it is AgentEvent.ToolRunning
        }
        return hasAssistant && !invokedTools
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
        showSection(Section.SETTINGS)
    }

    private fun renderToolsScreen() {
        ToolsScreenRenderer(
            activity = this,
            contentRoot = contentRoot,
            toolExecutor = toolExecutor,
            statusPill = ::statusPill,
            openAccessibilitySettings = ::openAccessibilitySettings,
            executeAndRender = ::executeAndRender,
            recordToolsResult = sectionResults::recordToolsResult,
            toolsResult = sectionResults::forTools,
            refreshExecutionLog = ::refreshExecutionLog,
            refreshToolsScreen = { showSection(Section.TOOLS) },
            hideKeyboard = ::hideKeyboard,
            bindKeyboardScrollSpacer = ::bindKeyboardScrollSpacer,
            getFocusSelectorIndex = { focusSelectorIndex },
            setFocusSelectorIndex = { focusSelectorIndex = it },
            getLastFocusInputArgs = { lastFocusInputArgs },
            setLastFocusInputArgs = { lastFocusInputArgs = it },
            showToolResultToast = ::showToolResultToast
        ).render()
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
        if (activeRunDetailId != null) {
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
            activeSettingsPanel = { activeSettingsPanel },
            setActiveSettingsPanel = { activeSettingsPanel = it },
            setPendingAnimationDirection = { pendingSettingsAnimationDirection = it },
            selectedSkillId = { selectedSkillId },
            expandedSkillReferenceId = { expandedSkillReferenceId },
            commitSelectedSkill = ::commitSelectedSkill,
            currentProviderMode = ::currentProviderMode,
            openAccessibilitySettings = ::openAccessibilitySettings,
            hideKeyboard = ::hideKeyboard,
            recordMcpResult = sectionResults::recordMcpResult,
            mcpResult = sectionResults::forMcp,
            refreshSettingsScreen = { showSection(Section.SETTINGS) }
        ).render()
    }

    private val sectionResults = SectionResultStore()

    private fun executeAndRender(name: String, args: Map<String, String>): ToolResult {
        val result = toolExecutor.execute(name, args, ToolSource.DIRECT_DEBUG)
        sectionResults.recordToolsResult(
            SensitiveTextRedactor.redact("$name($args) -> ${result.ok}: ${result.message}")
        )
        refreshExecutionLog()
        return result
    }

    private fun showToolResultToast(label: String, result: ToolResult) {
        Toast.makeText(
            this,
            if (result.ok) {
                "$label succeeded"
            } else {
                "$label failed: ${result.message}"
            },
            Toast.LENGTH_LONG
        ).show()
    }

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
            refreshLogsScreen = { showSection(Section.LOGS) }
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

    private fun setAgentRunState(state: AgentRunState) {
        agentRunState = state
        if (activeSection == Section.CHAT) {
            showSection(Section.CHAT)
        }
    }

    private fun cancelAgentRun() {
        agentCancellationSignal.set(true)
        setAgentRunState(AgentRunState.CANCELLED)

        // Reject any pending approval prompts
        conversation.forEach { event ->
            if (event is ChatEvent.ApprovalPrompt && event.state == ApprovalState.PENDING) {
                event.state = ApprovalState.REJECTED
            }
        }

        conversation += ChatEvent.Agent("Run cancelled.", "Stopped by user request.")
        showSection(Section.CHAT)
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

    private fun AgentProviderMode.toLogSource(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL -> "local_model"
            AgentProviderMode.LOCAL_ROUTER -> "local_router"
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
        activeRunDetailId = runId
        showSection(activeSection)
    }

    private fun closeRunDetail() {
        activeRunDetailId = null
        showSection(activeSection)
    }

    private fun findAgentRun(runId: String): AgentRunRecord? {
        return agentRunHistory.lastOrNull { it.id == runId }
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
        if (agentRunHistory.isEmpty()) {
            contentRoot.addView(
                timelineCard(
                    title = "No agent runs yet",
                    body = "Run a task from Chat to inspect structured run details here."
                )
            )
            return
        }

        agentRunHistory.asReversed().forEach { record ->
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
        val runId = activeRunDetailId
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
                showSection(activeSection)
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

    private data class PendingClarification(
        val originalTask: String,
    )

    private data class ClarificationChatPrompt(
        val question: String,
        val detail: String,
        val choices: List<String>,
    )

    private enum class Section(val label: String, @DrawableRes val iconRes: Int) {
        CHAT("Chat", R.drawable.ic_chat),
        TOOLS("Tools", R.drawable.ic_tools),
        LOGS("Logs", R.drawable.ic_logs),
        SETTINGS("Settings", R.drawable.ic_settings)
    }

    private companion object {
        const val ApprovalTimeoutMs = 5 * 60 * 1000L
        const val MaxApprovalArgLength = 500
        const val MaxToolCardFieldLength = 700
        val ProviderModeLabels = listOf("Local router", "Local model")
    }

}
