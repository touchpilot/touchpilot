package dev.touchpilot.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunCompletionStatus
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
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepTimelineBuilder
import dev.touchpilot.app.agent.timelineChipAccent
import dev.touchpilot.app.agent.timelineChipLabel
import dev.touchpilot.app.agent.timelineDetail
import dev.touchpilot.app.agent.timelineLabel
import dev.touchpilot.app.agent.ConversationalGate
import dev.touchpilot.app.agent.DefaultLocalReasoningCore
import dev.touchpilot.app.agent.LocalReasoningContext
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.agent.ToolCallPolicyStatus
import dev.touchpilot.app.agent.ToolCallResultStatus
import dev.touchpilot.app.agent.defaultAgentRunInvocation
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.localinference.LiteRtCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelStatus
import dev.touchpilot.app.logging.DeveloperLogEntry
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
import org.json.JSONArray
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
            gravity = Gravity.BOTTOM
            setPadding(0, 12, 0, 0)
        }
        chatTaskInput = EditText(this).apply {
            id = R.id.agent_task_input
            hint = "Message TouchPilot..."
            setSingleLine(false)
            minLines = 1
            maxLines = 5
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setHintTextColor(Theme.MutedText)
            setLineSpacing(4f, 1f)
            background = rounded(Theme.Card, 24, Theme.StrokeDark)
            setPadding(22, 14, 22, 14)
            minHeight = 56
            imeOptions = EditorInfo.IME_ACTION_SEND.toInt()
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                minHeight = 56
                minWidth = 0
                insetTop = 0
                insetBottom = 0
                gravity = Gravity.CENTER
                setTextColor(Theme.OnAccent)
                backgroundTintList = ColorStateList.valueOf(Theme.Accent)
                cornerRadius = 28
                setPadding(28, 0, 32, 0)
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_send)
                iconTint = ColorStateList.valueOf(Theme.OnAccent)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 10
                iconSize = 40
                setOnClickListener { submitChatMessage() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                56
            ).apply {
                leftMargin = 10
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
        if (activeRunDetailId != null) {
            renderAgentRunDetailScreen()
            return
        }

        contentRoot.addView(sectionTitle("Chat"))
        contentRoot.addView(statusPill())
        contentRoot.addView(runStatePill())
        contentRoot.addView(chatContextStrip())

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
            is ChatEvent.Working -> workingBubble(event.text, event.detail)
            is ChatEvent.Timeline -> timelineCard(
                title = event.title,
                body = event.body,
                actionHint = event.runId?.let { "Tap for run details" },
                onClick = event.runId?.let { runId -> { openRunDetail(runId) } }
            )
            is ChatEvent.StepTimeline -> stepTimelineCard(event)
            is ChatEvent.CompletionSummary -> completionSummaryCard(event.summary, event.runId)
            is ChatEvent.ToolCall -> toolCallCard(event.card)
            is ChatEvent.ApprovalPrompt -> approvalCard(event)
            is ChatEvent.ClarificationPrompt -> clarificationCard(event)
        }
    }

    private fun toolCallCard(cardModel: ToolCallCardModel): View {
        val blocked = cardModel.policyStatus == ToolCallPolicyStatus.BLOCKED ||
            cardModel.resultStatus == ToolCallResultStatus.BLOCKED
        val failed = cardModel.resultStatus == ToolCallResultStatus.FAILED
        val needsApproval = cardModel.policyStatus == ToolCallPolicyStatus.APPROVAL_REQUIRED
        val stroke = when {
            blocked || failed -> Theme.Danger
            needsApproval -> Theme.Warning
            cardModel.resultStatus == ToolCallResultStatus.SUCCEEDED -> Theme.Accent
            else -> Theme.StrokeDark
        }

        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stroke
            strokeWidth = if (blocked || failed || needsApproval) 2 else 1
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
                text = cardModel.tool
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            statusChip(cardModel.resultStatus.label, accent = cardModel.resultStatus == ToolCallResultStatus.SUCCEEDED)
        )
        content.addView(header)

        content.addView(
            TextView(this).apply {
                text = "Policy: ${cardModel.policyStatus.label}"
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(stroke)
                setPadding(0, 8, 0, 0)
            }
        )

        content.addView(
            TextView(this).apply {
                text = formatToolCallBody(cardModel)
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 6, right = 24, bottom = 6)
    }

    private fun completionSummaryCard(summary: AgentRunCompletionSummary, runId: String): View {
        val stroke = completionSummaryStroke(summary.status)
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stroke
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { openRunDetail(runId) }
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
                text = "Task summary"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        header.addView(
            statusChip(
                summary.status.label,
                accent = summary.status == AgentRunCompletionStatus.COMPLETED,
            )
        )
        content.addView(header)
        content.addView(completionSummaryRow("Stop reason", summary.stopReason))
        content.addView(
            completionSummaryRow(
                "Steps",
                "${summary.stepCount} step${if (summary.stepCount == 1) "" else "s"}",
            )
        )
        content.addView(
            completionSummaryRow(
                "Last verification",
                summary.lastVerificationOutcome ?: "None recorded",
            )
        )
        summary.nextAction?.let { nextAction ->
            content.addView(completionSummaryRow("Next", nextAction))
        }
        content.addView(
            TextView(this).apply {
                text = "Tap for full run details"
                textSize = 11.5f
                setTextColor(Theme.Accent)
                setPadding(0, 10, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 6, right = 24, bottom = 6)
    }

    private fun completionSummaryRow(label: String, value: String): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
            addView(
                TextView(this@MainActivity).apply {
                    text = label
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Theme.MutedText)
                }
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = value
                    textSize = 12.5f
                    setTextColor(Theme.BodyText)
                    setLineSpacing(2f, 1f)
                    setPadding(0, 2, 0, 0)
                }
            )
        }
    }

    private fun completionSummaryStroke(status: AgentRunCompletionStatus): Int {
        return when (status) {
            AgentRunCompletionStatus.COMPLETED -> Theme.Accent
            AgentRunCompletionStatus.BLOCKED,
            AgentRunCompletionStatus.FAILED -> Theme.Danger
            AgentRunCompletionStatus.STOPPED,
            AgentRunCompletionStatus.NEEDS_CLARIFICATION,
            AgentRunCompletionStatus.CANCELLED -> Theme.Warning
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
                primaryButton("Approve", iconRes = R.drawable.ic_check) {
                    resolveApproval(event, true)
                }.apply { id = R.id.approval_approve_button },
                rowButtonParams()
            )
            buttonRow.addView(
                secondaryButton("Reject", iconRes = R.drawable.ic_close) {
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

    private fun clarificationCard(event: ChatEvent.ClarificationPrompt): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = when (event.state) {
                ClarificationState.PENDING -> Theme.Accent
                ClarificationState.ANSWERED -> Theme.StrokeDark
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
            ClarificationState.PENDING -> "Clarification needed"
            ClarificationState.ANSWERED -> "Answered"
        }
        content.addView(
            TextView(this).apply {
                text = statusLabel
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        content.addView(
            TextView(this).apply {
                text = event.question
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 0)
            }
        )
        if (event.detail.isNotBlank()) {
            content.addView(
                TextView(this).apply {
                    text = SensitiveTextRedactor.redact(event.detail)
                    textSize = 12.5f
                    setTextColor(Theme.BodyText)
                    setPadding(0, 6, 0, 0)
                }
            )
        }

        if (event.state == ClarificationState.PENDING && event.choices.isNotEmpty()) {
            event.choices.forEach { choice ->
                val label = SensitiveTextRedactor.redact(choice)
                content.addView(
                    secondaryButton(label) {
                        resolveClarification(event, label)
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 10
                        }
                    }
                )
            }
        } else if (event.state == ClarificationState.ANSWERED) {
            content.addView(
                TextView(this).apply {
                    text = "You: ${event.selectedAnswer.orEmpty()}"
                    textSize = 12.5f
                    setTextColor(Theme.Accent)
                    setPadding(0, 10, 0, 0)
                }
            )
        } else if (event.state == ClarificationState.PENDING) {
            content.addView(
                TextView(this).apply {
                    text = "Reply in the chat box below."
                    textSize = 12.5f
                    setTextColor(Theme.MutedText)
                    setPadding(0, 10, 0, 0)
                }
            )
        }

        card.addView(content)
        return card.withMargins(top = 8, bottom = 8)
    }

    private fun resolveClarification(event: ChatEvent.ClarificationPrompt, answer: String) {
        if (event.state != ClarificationState.PENDING) return
        event.state = ClarificationState.ANSWERED
        event.selectedAnswer = answer
        event.onAnswer(answer)
        showSection(Section.CHAT)
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
        bindStepTimeline(event)
        scrollChatToBottom()
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
                openAccessibilitySettings()
            }
        )
    }

    private fun renderToolsScreen() {
        contentRoot.addView(sectionTitle("Android Tools"))
        contentRoot.addView(statusPill())
        contentRoot.addView(
            secondaryButton("Open Accessibility Settings") {
                openAccessibilitySettings()
            }.apply { id = R.id.open_accessibility_settings_button }
        )

        contentRoot.addView(
            primaryButton("Observe Current Screen") {
                executeAndRender("observe_screen", emptyMap())
                showSection(Section.TOOLS)
            }.apply { id = R.id.observe_screen_button }
        )

        contentRoot.addView(
            secondaryButton("Observe Screen Context") {
                executeAndRender("observe_screen_context", emptyMap())
                showSection(Section.TOOLS)
            }.apply { id = R.id.observe_screen_context_button }
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

        val settingsPanelInput = editText(
            "Settings panel: wifi, bluetooth, accessibility, app_info, notifications, system_settings"
        ).apply { id = R.id.open_settings_panel_input }
        contentRoot.addView(settingsPanelInput)
        contentRoot.addView(
            secondaryButton("Open Settings Panel") {
                hideKeyboard(settingsPanelInput)
                executeAndRender(
                    "open_settings_panel",
                    mapOf("panel" to settingsPanelInput.text.toString())
                )
                showSection(Section.TOOLS)
            }.apply { id = R.id.open_settings_panel_button }
        )

        val waitAppInput = editText("App package or launcher label to wait for").apply {
            id = R.id.wait_for_app_input
        }
        contentRoot.addView(waitAppInput)
        contentRoot.addView(
            secondaryButton("Wait For App") {
                val expected = waitAppInput.text.toString()
                val args = if (expected.contains(".")) {
                    mapOf("package" to expected, "timeout_ms" to "5000")
                } else {
                    mapOf("label" to expected, "timeout_ms" to "5000")
                }
                hideKeyboard(waitAppInput)
                Thread {
                    val result = toolExecutor.execute("wait_for_app", args, ToolSource.DIRECT_DEBUG)
                    runOnUiThread {
                        sectionResults.recordToolsResult(
                            SensitiveTextRedactor.redact("wait_for_app -> ${result.ok}: ${result.message}")
                        )
                        refreshExecutionLog()
                        showSection(Section.TOOLS)
                    }
                }.start()
            }.apply { id = R.id.wait_for_app_button }
        )

        val tapInput = editText("Visible text to tap").apply { id = R.id.tap_text_input }
        contentRoot.addView(tapInput)
        contentRoot.addView(
            secondaryButton("Tap Text") {
                val targetText = tapInput.text.toString()
                hideKeyboard(tapInput)
                contentRoot.postDelayed({
                    val result = executeAndRender("tap", mapOf("text" to targetText))
                    showToolResultToast("Tap", result)
                    showSection(Section.TOOLS)
                }, ToolActionKeyboardSettleMs)
            }.apply { id = R.id.tap_text_button }
        )

        val longPressInput = editText("Visible text to long-press").apply { id = R.id.long_press_text_input }
        contentRoot.addView(longPressInput)
        contentRoot.addView(
            secondaryButton("Long-Press Text") {
                val targetText = longPressInput.text.toString()
                hideKeyboard(longPressInput)
                contentRoot.postDelayed({
                    val result = executeAndRender("long_press", mapOf("text" to targetText))
                    showToolResultToast("Long-press", result)
                    showSection(Section.TOOLS)
                }, ToolActionKeyboardSettleMs)
            }.apply { id = R.id.long_press_text_button }
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

        contentRoot.addView(
            secondaryButton("Clear Focused Field") {
                executeAndRender("clear_text", emptyMap())
            }.apply { id = R.id.clear_text_button }
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

        val swipeHorizontalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swipeHorizontalRow.addView(
            secondaryButton("Swipe Left") {
                executeAndRender("swipe", mapOf("direction" to "left"))
                showSection(Section.TOOLS)
            }.apply { id = R.id.swipe_left_button },
            rowButtonParams()
        )
        swipeHorizontalRow.addView(
            secondaryButton("Swipe Right") {
                executeAndRender("swipe", mapOf("direction" to "right"))
                showSection(Section.TOOLS)
            }.apply { id = R.id.swipe_right_button },
            rowButtonParams()
        )
        contentRoot.addView(swipeHorizontalRow)

        val swipeVerticalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        swipeVerticalRow.addView(
            secondaryButton("Swipe Up") {
                executeAndRender("swipe", mapOf("direction" to "up"))
                showSection(Section.TOOLS)
            }.apply { id = R.id.swipe_up_button },
            rowButtonParams()
        )
        swipeVerticalRow.addView(
            secondaryButton("Swipe Down") {
                executeAndRender("swipe", mapOf("direction" to "down"))
                showSection(Section.TOOLS)
            }.apply { id = R.id.swipe_down_button },
            rowButtonParams()
        )
        contentRoot.addView(swipeVerticalRow)

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
                        sectionResults.recordToolsResult(
                            SensitiveTextRedactor.redact("wait_for_ui -> ${result.ok}: ${result.message}")
                        )
                        refreshExecutionLog()
                        showSection(Section.TOOLS)
                    }
                }.start()
            }.apply { id = R.id.wait_for_text_button }
        )

        contentRoot.addView(
            secondaryButton("Wait For Idle") {
                Thread {
                    val result = toolExecutor.execute(
                        "wait_for_idle",
                        emptyMap(),
                        ToolSource.DIRECT_DEBUG
                    )
                    runOnUiThread {
                        sectionResults.recordToolsResult(
                            SensitiveTextRedactor.redact("wait_for_idle -> ${result.ok}: ${result.message}")
                        )
                        refreshExecutionLog()
                        showSection(Section.TOOLS)
                    }
                }.start()
            }.apply { id = R.id.wait_for_idle_button }
        )

        contentRoot.addView(timelineCard("Latest result", sectionResults.forTools()))

        // Tail spacer so the Focus / Dismiss row can scroll above the soft
        // keyboard when an input is focused (adjustResize alone leaves them
        // flush against the IME). The spacer is 0 dp tall when the keyboard
        // is hidden and grows only while the IME is visible, so it does not
        // add visible whitespace in the resting layout.
        val keyboardScrollSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            )
        }
        contentRoot.addView(keyboardScrollSpacer)
        bindKeyboardScrollSpacer(keyboardScrollSpacer)
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
                sectionResults.recordMcpResult("Listing MCP tools...")
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
                        sectionResults.recordMcpResult(result)
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
                sectionResults.recordMcpResult("Calling MCP tool...")
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
                        sectionResults.recordMcpResult(result)
                        showSection(Section.SETTINGS)
                    }
                }.start()
            }.apply { id = R.id.call_mcp_tool_button },
            rowButtonParams()
        )
        contentRoot.addView(actionRow)
        contentRoot.addView(timelineCard("MCP result", sectionResults.forMcp()))
    }

    private fun renderLogsScreen() {
        if (activeRunDetailId != null) {
            renderAgentRunDetailScreen()
            return
        }

        contentRoot.addView(sectionTitle("Logs"))
        contentRoot.addView(
            primaryButton("Export Debug Trace") {
                val file = exportDebugTrace()
                sectionResults.recordLogsResult("Debug trace exported: ${file.absolutePath}")
                showSection(Section.LOGS)
            }.apply { id = R.id.export_debug_trace_button }
        )
        contentRoot.addView(timelineCard("Latest result", sectionResults.forLogs()))
        executionLogList = LinearLayout(this).apply {
            id = R.id.execution_log_list
            orientation = LinearLayout.VERTICAL
        }
        contentRoot.addView(executionLogList)
        refreshExecutionLog()
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
            executionLogList.removeAllViews()
            val entries = ToolExecutionLog.recentEntries()
            if (entries.isEmpty()) {
                executionLogList.addView(
                    timelineCard(
                        title = "No developer logs yet",
                        body = "Run a chat task or tool action to record local logs."
                    )
                )
                return
            }
            entries.forEach { entry ->
                executionLogList.addView(developerLogRow(entry))
            }
        }
    }

    private fun developerLogRow(entry: DeveloperLogEntry): View {
        val statusColor = logStatusColor(entry.status)
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = statusColor
            strokeWidth = 1
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            foreground = selectableItemBackground()
            setOnClickListener { showDeveloperLogDetails(entry.id) }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 11, 16, 11)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = entry.name.ifBlank { entry.type }
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                maxLines = 1
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            TextView(this).apply {
                text = DeveloperLogEntry.formatShortTimestamp(entry.timestampMillis)
                textSize = 11f
                setTextColor(Theme.MutedText)
                setPadding(8, 0, 10, 0)
            }
        )
        header.addView(logStatusChip(entry.status.ifBlank { "log" }))
        content.addView(header)
        val preview = entry.result.ifBlank { entry.payloadSummary }.lineSequence().firstOrNull().orEmpty()
        content.addView(
            TextView(this).apply {
                text = "${entry.type.ifBlank { "log" }} · ${entry.source.ifBlank { "unknown" }} · $preview"
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                maxLines = 1
                setPadding(0, 5, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 4, bottom = 4)
    }

    private fun showDeveloperLogDetails(id: Long) {
        val entry = ToolExecutionLog.findEntry(id) ?: return
        lateinit var dialog: AlertDialog
        val detailView = developerLogDetailView(
            entry = entry,
            onClose = { dialog.dismiss() }
        )
        dialog = AlertDialog.Builder(this)
            .setView(detailView)
            .create()
        dialog.show()
    }

    private fun developerLogDetailView(entry: DeveloperLogEntry, onClose: () -> Unit): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6, 0, 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(this@MainActivity).apply {
                        text = entry.name.ifBlank { "Log details" }
                        textSize = 16f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(Color.WHITE)
                        letterSpacing = 0.03f
                        maxLines = 1
                    }
                )
                addView(
                    TextView(this@MainActivity).apply {
                        text = DeveloperLogEntry.formatTimestamp(entry.timestampMillis)
                        textSize = 11f
                        setTextColor(Theme.MutedText)
                        maxLines = 1
                        setPadding(0, 3, 0, 0)
                    }
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(logIconButton(R.drawable.ic_copy, "Copy log") { copyDeveloperLog(entry) })
        header.addView(logIconButton(R.drawable.ic_close, "Close log details", onClose))
        content.addView(header)

        content.addView(logMetaChipRow(entry))

        val logTextView = TextView(this).apply {
            text = formattedLogText(entry.detailText())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#E8EAF6"))
            setTextIsSelectable(true)
            setLineSpacing(4f, 1f)
            background = rounded(Color.parseColor("#161B22"), 12, Theme.StrokeDark)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        content.addView(
            ScrollView(this).apply {
                addView(logTextView)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(320)
                ).apply {
                    setMargins(0, dp(12), 0, 0)
                }
            }
        )
        return content
    }

    private fun logMetaChipRow(entry: DeveloperLogEntry): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(logMetaChip(entry.type.ifBlank { "log" }, Theme.Accent))
            addView(spacer(dp(6)))
            addView(logMetaChip(entry.source.ifBlank { "unknown" }, Theme.Warning))
            addView(spacer(dp(6)))
            addView(logMetaChip(entry.status.ifBlank { "log" }, logStatusColor(entry.status)))
        }
    }

    private fun logMetaChip(text: String, bg: Int): TextView {
        return TextView(this).apply {
            this.text = text.uppercase()
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (bg == Theme.Accent || bg == Theme.Warning) Theme.OnAccent else Color.WHITE)
            background = rounded(bg, 8, bg)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
    }

    private fun spacer(width: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }
    }

    private fun logIconButton(
        @DrawableRes iconRes: Int,
        description: String,
        onClick: () -> Unit
    ): View {
        return MaterialButton(this).apply {
            icon = ContextCompat.getDrawable(this@MainActivity, iconRes)
            iconTint = ColorStateList.valueOf(Theme.BodyText)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            iconSize = 18
            minWidth = dp(36)
            minHeight = dp(36)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                setMargins(dp(6), 0, 0, 0)
            }
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(Theme.SurfaceRaised)
            strokeColor = ColorStateList.valueOf(Theme.StrokeDark)
            strokeWidth = 1
            cornerRadius = dp(18)
            contentDescription = description
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    private fun formattedLogText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun copyDeveloperLog(entry: DeveloperLogEntry) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                entry.name.ifBlank { "TouchPilot log" },
                entry.fullLogText()
            )
        )
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun logStatusChip(status: String): TextView {
        val color = logStatusColor(status)
        return TextView(this).apply {
            text = status.ifBlank { "log" }
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (color == Theme.Accent || color == Theme.Warning) Theme.OnAccent else Color.WHITE)
            isAllCaps = true
            background = rounded(color, 7, color)
            setPadding(8, 3, 8, 3)
        }
    }

    private fun logStatusColor(status: String): Int {
        return when (status.lowercase()) {
            "ok", "complete" -> Theme.Accent
            "fail", "failed", "error" -> Theme.Danger
            "running", "pending", "info" -> Theme.Warning
            else -> Theme.StrokeDark
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

    private fun primaryButton(
        text: String,
        @DrawableRes iconRes: Int? = null,
        onClick: () -> Unit
    ): TextView {
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
            if (iconRes != null) {
                icon = ContextCompat.getDrawable(this@MainActivity, iconRes)
                iconTint = ColorStateList.valueOf(Theme.OnAccent)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 8
                iconSize = 36
            }
            setOnClickListener { onClick() }
        }
    }

    private fun secondaryButton(
        text: String,
        @DrawableRes iconRes: Int? = null,
        onClick: () -> Unit
    ): TextView {
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
            if (iconRes != null) {
                icon = ContextCompat.getDrawable(this@MainActivity, iconRes)
                iconTint = ColorStateList.valueOf(Theme.BodyText)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = 8
                iconSize = 36
            }
            setOnClickListener { onClick() }
        }
    }

    private fun userBubble(text: String): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        column.addView(senderLabel("You", alignEnd = true))
        column.addView(
            TextView(this).apply {
                setText(text)
                textSize = 14.5f
                setTextColor(Theme.OnAccent)
                setLineSpacing(4f, 1f)
                background = bubbleBackground(Theme.Accent, Theme.Accent, tailOnRight = true)
                setPadding(20, 14, 20, 14)
                maxWidth = bubbleMaxWidth()
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END
            }
        )
        return column.withMargins(top = 6, bottom = 6)
    }

    private fun agentBubble(text: String, detail: String): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        column.addView(senderLabel("TouchPilot", alignEnd = false))

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bubbleBackground(Theme.Card, Theme.StrokeDark, tailOnRight = false)
            setPadding(20, 14, 20, 14)
        }
        bubble.addView(
            TextView(this).apply {
                setText(text)
                textSize = 14.5f
                setTextColor(Theme.BodyText)
                setLineSpacing(4f, 1f)
                maxWidth = bubbleMaxWidth()
            }
        )
        if (detail.isNotBlank()) {
            bubble.addView(
                TextView(this).apply {
                    setText(detail)
                    textSize = 12f
                    setTextColor(Theme.MutedText)
                    setLineSpacing(3f, 1f)
                    setPadding(0, 6, 0, 0)
                    maxWidth = bubbleMaxWidth()
                }
            )
        }
        column.addView(
            bubble,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
            }
        )
        return column.withMargins(top = 6, bottom = 6)
    }

    private fun workingBubble(text: String, detail: String): View {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        column.addView(senderLabel("TouchPilot", alignEnd = false))

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bubbleBackground(Theme.Card, Theme.Accent, tailOnRight = false)
            setPadding(20, 14, 20, 14)
        }

        val mainText = TextView(this).apply {
            setText("$text  ")
            textSize = 14.5f
            setTextColor(Theme.BodyText)
            setLineSpacing(4f, 1f)
            maxWidth = bubbleMaxWidth()
        }
        bubble.addView(mainText)

        if (detail.isNotBlank()) {
            bubble.addView(
                TextView(this).apply {
                    setText(detail)
                    textSize = 12f
                    setTextColor(Theme.MutedText)
                    setLineSpacing(3f, 1f)
                    setPadding(0, 6, 0, 0)
                    maxWidth = bubbleMaxWidth()
                }
            )
        }

        animateTypingDots(mainText, baseText = text)

        column.addView(
            bubble,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
            }
        )
        return column.withMargins(top = 6, bottom = 6)
    }

    private fun animateTypingDots(target: TextView, baseText: String) {
        var step = 0
        val intervalMs = 350L
        val runner = object : Runnable {
            override fun run() {
                val count = (step % 3) + 1
                target.text = baseText + "  " + ".".repeat(count) + " ".repeat(3 - count)
                step++
                target.postDelayed(this, intervalMs)
            }
        }
        target.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeCallbacks(runner)
                v.post(runner)
            }
            override fun onViewDetachedFromWindow(v: View) {
                v.removeCallbacks(runner)
            }
        })
        if (target.isAttachedToWindow) {
            target.removeCallbacks(runner)
            target.post(runner)
        }
    }

    private fun senderLabel(text: String, alignEnd: Boolean): TextView {
        return TextView(this).apply {
            setText(text)
            textSize = 11f
            setTextColor(Theme.MutedText)
            setPadding(6, 0, 6, 4)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (alignEnd) Gravity.END else Gravity.START
            }
        }
    }

    private fun bubbleMaxWidth(): Int =
        (resources.displayMetrics.widthPixels * 0.78f).toInt()

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

    private fun chatContextStrip(): View {
        val runtime = currentProviderMode().label()
        val skill = selectedSkill()?.title ?: "No skill selected"
        return TextView(this).apply {
            text = "Runtime: $runtime   ·   Skill: $skill"
            textSize = 11.5f
            setTextColor(Theme.MutedText)
            setLineSpacing(3f, 1f)
            setPadding(4, 0, 4, 0)
        }.withMargins(top = 2, bottom = 14)
    }

    private fun runStatePill(): View {
        val (label, color) = when (agentRunState) {
            AgentRunState.IDLE -> "Idle" to Theme.MutedText
            AgentRunState.RUNNING -> "Running" to Theme.Accent
            AgentRunState.WAITING_APPROVAL -> "Waiting for approval" to Color.rgb(255, 193, 7)
            AgentRunState.WAITING_CLARIFICATION -> "Waiting for clarification" to Color.rgb(255, 193, 7)
            AgentRunState.COMPLETED -> "Completed" to Theme.Accent
            AgentRunState.FAILED -> "Failed" to Color.rgb(239, 68, 68)
            AgentRunState.BLOCKED -> "Blocked" to Color.rgb(239, 68, 68)
            AgentRunState.CANCELLED -> "Cancelled" to Theme.MutedText
        }

        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = color
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        content.addView(
            TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val canCancel = agentRunState == AgentRunState.RUNNING || agentRunState == AgentRunState.WAITING_APPROVAL
        if (canCancel) {
            content.addView(
                MaterialButton(this).apply {
                    text = "Stop"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    minHeight = 32
                    minWidth = 60
                    insetTop = 0
                    insetBottom = 0
                    setTextColor(Color.rgb(239, 68, 68))
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    strokeColor = ColorStateList.valueOf(Color.rgb(239, 68, 68))
                    strokeWidth = 1
                    cornerRadius = 6
                    setPadding(8, 4, 8, 4)
                    setOnClickListener { cancelAgentRun() }
                }
            )
        }

        card.addView(content)
        return card.withMargins(top = 8, bottom = 8)
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

    private fun stepTimelineCard(event: ChatEvent.StepTimeline): View {
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
        val titleView = TextView(this).apply {
            text = "Agent steps"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val subtitleView = TextView(this).apply {
            textSize = 11.5f
            setTextColor(Theme.MutedText)
            setPadding(0, 4, 0, 0)
        }
        val stepsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 0)
        }
        content.addView(titleView)
        content.addView(subtitleView)
        content.addView(stepsContainer)
        card.addView(content)
        event.viewHolder = ChatEvent.StepTimeline.ViewHolder(
            subtitleView = subtitleView,
            stepsContainer = stepsContainer
        )
        bindStepTimeline(event)
        return card.withMargins(top = 8, bottom = 8)
    }

    private fun bindStepTimeline(event: ChatEvent.StepTimeline) {
        val holder = event.viewHolder ?: return
        val steps = event.steps
        holder.subtitleView.text = when {
            steps.isEmpty() -> "Waiting for the first step..."
            event.isComplete -> "Completed ${steps.size} step${if (steps.size == 1) "" else "s"}"
            else -> "Running locally on device"
        }
        holder.stepsContainer.removeAllViews()
        if (steps.isEmpty()) {
            holder.stepsContainer.addView(stepTimelineEmptyState())
            return
        }
        steps.forEachIndexed { index, step ->
            holder.stepsContainer.addView(stepTimelineRow(step, isLast = index == steps.lastIndex))
        }
    }

    private fun stepTimelineEmptyState(): View {
        return TextView(this).apply {
            text = "Steps will appear here as TouchPilot observes, decides, and acts."
            textSize = 12f
            setTextColor(Theme.MutedText)
            setPadding(0, 4, 0, 4)
        }
    }

    private fun stepTimelineRow(step: AgentStep, isLast: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, if (isLast) 0 else 6, 0, 6)
        }
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 2, 12, 0)
        }
        rail.addView(
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(10, 10).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                background = rounded(stepStatusColor(step.status), 5, stepStatusColor(step.status))
            }
        )
        if (!isLast) {
            rail.addView(
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(2, 28)
                    setBackgroundColor(Theme.StrokeDark)
                }
            )
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        header.addView(
            TextView(this).apply {
                text = step.type.timelineLabel()
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        header.addView(stepStatusChip(step.status))
        column.addView(header)
        column.addView(
            TextView(this).apply {
                text = step.timelineDetail()
                textSize = 12f
                setTextColor(Theme.BodyText)
                setPadding(0, 4, 0, 0)
            }
        )
        row.addView(rail)
        row.addView(column)
        return row
    }

    private fun stepStatusChip(status: AgentStepStatus): TextView {
        return statusChip(status.timelineChipLabel(), accent = status.timelineChipAccent()).apply {
            when (status) {
                AgentStepStatus.FAILED,
                AgentStepStatus.BLOCKED -> {
                    background = rounded(Theme.SurfaceRaised, 8, Color.rgb(180, 70, 70))
                    setTextColor(Color.rgb(255, 210, 210))
                }
                AgentStepStatus.CLARIFIED -> {
                    background = rounded(Theme.SurfaceRaised, 8, Color.rgb(200, 150, 40))
                    setTextColor(Color.rgb(255, 230, 170))
                }
                AgentStepStatus.PENDING -> {
                    background = rounded(Theme.SurfaceRaised, 8, Theme.StrokeDark)
                    setTextColor(Theme.MutedText)
                }
                else -> Unit
            }
        }
    }

    private fun stepStatusColor(status: AgentStepStatus): Int {
        return when (status) {
            AgentStepStatus.OK -> Theme.Accent
            AgentStepStatus.RUNNING -> Color.rgb(80, 170, 255)
            AgentStepStatus.FAILED -> Color.rgb(220, 90, 90)
            AgentStepStatus.BLOCKED -> Color.rgb(180, 70, 70)
            AgentStepStatus.CLARIFIED -> Color.rgb(220, 170, 60)
            AgentStepStatus.PENDING -> Theme.StrokeDark
            AgentStepStatus.STOPPED -> Theme.MutedText
        }
    }

    private fun timelineCard(
        title: String,
        body: String,
        actionHint: String? = null,
        onClick: (() -> Unit)? = null
    ): View {
        val card = MaterialCardView(this).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = if (onClick != null) Theme.Accent else Theme.StrokeDark
            strokeWidth = if (onClick != null) 2 else 1
            radius = 8f
            cardElevation = 0f
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
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
        if (actionHint != null) {
            content.addView(
                TextView(this).apply {
                    text = actionHint
                    textSize = 11.5f
                    setTextColor(Theme.Accent)
                    setPadding(0, 8, 0, 0)
                }
            )
        }
        card.addView(content)
        return card.withMargins(top = 8, bottom = 8)
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun selectableItemBackground(): Drawable? {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val typedArray = obtainStyledAttributes(attrs)
        return try {
            typedArray.getDrawable(0)
        } finally {
            typedArray.recycle()
        }
    }

    private fun bubbleBackground(fill: Int, stroke: Int, tailOnRight: Boolean): GradientDrawable {
        val large = 22f
        val tail = 6f
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            setStroke(1, stroke)
            cornerRadii = if (tailOnRight) {
                floatArrayOf(large, large, large, large, tail, tail, large, large)
            } else {
                floatArrayOf(large, large, large, large, large, large, tail, tail)
            }
        }
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

    private fun AgentProviderMode.label(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL -> "Local model with router fallback"
            AgentProviderMode.LOCAL_ROUTER -> "Local router"
        }
    }

    private fun AgentProviderMode.toLogSource(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL -> "local_model"
            AgentProviderMode.LOCAL_ROUTER -> "local_router"
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

    private sealed class ChatEvent {
        data class User(val text: String) : ChatEvent()
        data class Agent(val text: String, val detail: String) : ChatEvent()
        data class Working(val text: String, val detail: String) : ChatEvent()
        data class Timeline(val title: String, val body: String, val runId: String? = null) : ChatEvent()
        data class CompletionSummary(
            val summary: AgentRunCompletionSummary,
            val runId: String,
        ) : ChatEvent()
        class StepTimeline(
            steps: List<AgentStep> = emptyList(),
            isComplete: Boolean = false
        ) : ChatEvent() {
            var steps: List<AgentStep> = steps
            var isComplete: Boolean = isComplete
            var viewHolder: ViewHolder? = null

            class ViewHolder(
                val subtitleView: TextView,
                val stepsContainer: LinearLayout
            )
        }
        data class ToolCall(val card: ToolCallCardModel) : ChatEvent()
        class ApprovalPrompt(
            val request: ToolApprovalRequest,
            val onDecision: (Boolean) -> Unit
        ) : ChatEvent() {
            var state: ApprovalState = ApprovalState.PENDING
        }
        class ClarificationPrompt(
            val question: String,
            val detail: String,
            val choices: List<String>,
            val onAnswer: (String) -> Unit
        ) : ChatEvent() {
            var state: ClarificationState = ClarificationState.PENDING
            var selectedAnswer: String? = null
        }
    }

    private enum class ApprovalState { PENDING, APPROVED, REJECTED }
    private enum class ClarificationState { PENDING, ANSWERED }

    private enum class Section(val label: String, @DrawableRes val iconRes: Int) {
        CHAT("Chat", R.drawable.ic_chat),
        TOOLS("Tools", R.drawable.ic_tools),
        LOGS("Logs", R.drawable.ic_logs),
        SETTINGS("Settings", R.drawable.ic_settings)
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
        const val MaxToolCardFieldLength = 700
        const val ToolActionKeyboardSettleMs = 250L
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
        val Warning: Int = Color.rgb(242, 183, 74)
        val Danger: Int = Color.rgb(239, 97, 97)
        val OnAccent: Int = Color.rgb(4, 28, 12)
        val BodyText: Int = Color.rgb(214, 223, 231)
        val MutedText: Int = Color.rgb(137, 151, 164)
        val NavText: Int = Color.rgb(156, 168, 178)
    }
}
