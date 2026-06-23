package dev.touchpilot.app.ui.chat

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.agent.AgentRunCompletionStatus
import dev.touchpilot.app.agent.AgentRunCompletionSummary
import dev.touchpilot.app.agent.AgentRunState
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.timelineChipAccent
import dev.touchpilot.app.agent.timelineChipLabel
import dev.touchpilot.app.agent.timelineDetail
import dev.touchpilot.app.agent.timelineLabel
import dev.touchpilot.app.ui.RuntimeIndicator
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.chatContextStrip
import dev.touchpilot.app.ui.rounded
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.withMargins

class ChatScreenRenderer(
    private val activity: Activity,
    private val scrollView: ScrollView,
    private val contentRoot: LinearLayout,
    private val conversation: List<ChatEvent>,
    private val agentRunState: () -> AgentRunState,
    private val runtimeIndicator: () -> RuntimeIndicator,
    private val skillTitle: () -> String,
    private val cancelAgentRun: () -> Unit,
    private val openRunDetail: (String) -> Unit,
    private val openSkillDetail: (String) -> Unit,
    private val refreshChatScreen: () -> Unit,
    private val isDemonstrationRecording: () -> Boolean = { false },
    private val demonstrationRecordingEnabled: () -> Boolean = { false },
) {
    fun render() {
        contentRoot.addView(runStatePill())
        if (demonstrationRecordingEnabled() && (isDemonstrationRecording() || agentRunState() == AgentRunState.RUNNING)) {
            contentRoot.addView(demonstrationRecordingBanner())
        }
        contentRoot.addView(chatContextStrip())

        conversation.forEach { event ->
            contentRoot.addView(renderChatEvent(event))
        }

        contentRoot.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    24
                )
            }
        )
        scrollChatToBottom()
    }

    fun bindStepTimeline(event: ChatEvent.StepTimeline) {
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

    private fun scrollChatToBottom() {
        scrollView.post {
            scrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun renderChatEvent(event: ChatEvent): View {
        return when (event) {
            is ChatEvent.User -> userBubble(event.text)
            is ChatEvent.Agent -> agentBubble(event.text, event.detail)
            is ChatEvent.ScreenSummary -> screenSummaryCard(event.summary, event.suggestions)
            is ChatEvent.Working -> workingBubble(event.text, event.detail)
            is ChatEvent.Timeline -> activity.timelineCard(
                title = event.title,
                body = event.body,
                actionHint = event.runId?.let { "Tap for run details" },
                onClick = event.runId?.let { runId -> { openRunDetail(runId) } }
            )
            is ChatEvent.StepTimeline -> stepTimelineCard(event)
            is ChatEvent.CompletionSummary -> completionSummaryCard(event.summary, event.runId)
            is ChatEvent.ToolCall -> ToolCallCardRenderer(activity).render(event.card)
            is ChatEvent.StepVerification -> StepVerificationCardRenderer(activity).render(event.card)
            is ChatEvent.SkillUse -> SkillUseCardRenderer(
                activity = activity,
                openSkillDetail = openSkillDetail,
            ).render(event.card)
            is ChatEvent.ApprovalPrompt -> ChatDecisionCardRenderer(
                activity = activity,
                refreshChatScreen = refreshChatScreen
            ).approvalCard(event)
            is ChatEvent.ClarificationPrompt -> ChatDecisionCardRenderer(
                activity = activity,
                refreshChatScreen = refreshChatScreen
            ).clarificationCard(event)
            is ChatEvent.DemonstrationRecording -> demonstrationRecordingCard(event)
        }
    }

    private fun userBubble(text: String): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        column.addView(senderLabel("You", alignEnd = true))
        column.addView(
            TextView(activity).apply {
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
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        column.addView(senderLabel("TouchPilot", alignEnd = false))

        val bubble = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = bubbleBackground(Theme.Card, Theme.StrokeDark, tailOnRight = false)
            setPadding(20, 14, 20, 14)
        }
        bubble.addView(
            TextView(activity).apply {
                setText(text)
                textSize = 14.5f
                setTextColor(Theme.BodyText)
                setLineSpacing(4f, 1f)
                maxWidth = bubbleMaxWidth()
            }
        )
        if (detail.isNotBlank()) {
            bubble.addView(
                TextView(activity).apply {
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

    /**
     * Surfaces the local [dev.touchpilot.app.screen.ScreenSummary] as a product
     * card: a one-line summary plus a small, capped, descriptive list of
     * suggested actions. Suggestions are display-only — nothing runs until the
     * user explicitly asks. An empty/weak screen shows a clear local fallback.
     * The summarizer already produces redacted, display-safe text.
     */
    private fun screenSummaryCard(summary: String, suggestions: List<String>): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        column.addView(senderLabel("TouchPilot", alignEnd = false))

        val bubble = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = bubbleBackground(Theme.Card, Theme.StrokeDark, tailOnRight = false)
            setPadding(20, 14, 20, 14)
        }

        bubble.addView(cardCaption("Screen understanding"))
        bubble.addView(
            TextView(activity).apply {
                setText(summary)
                textSize = 14.5f
                setTextColor(Theme.BodyText)
                setLineSpacing(4f, 1f)
                setPadding(0, 4, 0, 0)
                maxWidth = bubbleMaxWidth()
            }
        )

        val capped = suggestions.take(MaxScreenSuggestions)
        if (capped.isEmpty()) {
            bubble.addView(
                TextView(activity).apply {
                    setText("No suggested actions for this screen.")
                    textSize = 12f
                    setTextColor(Theme.MutedText)
                    setPadding(0, 8, 0, 0)
                    maxWidth = bubbleMaxWidth()
                }
            )
        } else {
            bubble.addView(
                cardCaption("Suggested actions").apply { setPadding(0, 10, 0, 0) }
            )
            capped.forEach { label ->
                bubble.addView(
                    TextView(activity).apply {
                        setText("•  $label")
                        textSize = 13.5f
                        setTextColor(Theme.BodyText)
                        setLineSpacing(3f, 1f)
                        setPadding(0, 4, 0, 0)
                        maxWidth = bubbleMaxWidth()
                    }
                )
            }
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

    private fun cardCaption(text: String): TextView {
        return TextView(activity).apply {
            setText(text)
            textSize = 11f
            isAllCaps = true
            letterSpacing = 0.06f
            setTextColor(Theme.MutedText)
            maxWidth = bubbleMaxWidth()
        }
    }

    private fun workingBubble(text: String, detail: String): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
        }
        column.addView(senderLabel("TouchPilot", alignEnd = false))

        val bubble = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = bubbleBackground(Theme.Card, Theme.Accent, tailOnRight = false)
            setPadding(20, 14, 20, 14)
        }

        val mainText = TextView(activity).apply {
            setText("$text  ")
            textSize = 14.5f
            setTextColor(Theme.BodyText)
            setLineSpacing(4f, 1f)
            maxWidth = bubbleMaxWidth()
        }
        bubble.addView(mainText)

        if (detail.isNotBlank()) {
            bubble.addView(
                TextView(activity).apply {
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
        return TextView(activity).apply {
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
        (activity.resources.displayMetrics.widthPixels * 0.78f).toInt()

    private fun chatContextStrip(): View {
        return TextView(activity).apply {
            text = runtimeIndicator().chatContextStrip(skillTitle())
            textSize = 11.5f
            setTextColor(Theme.MutedText)
            setLineSpacing(3f, 1f)
            setPadding(4, 0, 4, 0)
        }.withMargins(top = 2, bottom = 14)
    }

    private fun runStatePill(): View {
        val (label, color) = when (agentRunState()) {
            AgentRunState.IDLE -> "Idle" to Theme.MutedText
            AgentRunState.RUNNING -> "Running" to Theme.Accent
            AgentRunState.WAITING_APPROVAL -> "Waiting for approval" to Color.rgb(255, 193, 7)
            AgentRunState.WAITING_CLARIFICATION -> "Waiting for clarification" to Color.rgb(255, 193, 7)
            AgentRunState.COMPLETED -> "Completed" to Theme.Accent
            AgentRunState.FAILED -> "Failed" to Color.rgb(239, 68, 68)
            AgentRunState.BLOCKED -> "Blocked" to Color.rgb(239, 68, 68)
            AgentRunState.CANCELLED -> "Cancelled" to Theme.MutedText
        }

        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = color
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
        }

        content.addView(
            TextView(activity).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(color)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val state = agentRunState()
        val canCancel = state == AgentRunState.RUNNING || state == AgentRunState.WAITING_APPROVAL
        if (canCancel) {
            content.addView(
                MaterialButton(activity).apply {
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

    private fun demonstrationRecordingBanner(): View {
        val recording = isDemonstrationRecording()
        val color = if (recording) Color.rgb(220, 80, 80) else Theme.MutedText
        return MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = color
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 10, 16, 10)
                    addView(
                        TextView(activity).apply {
                            text = if (recording) "● Recording demonstration" else "Demonstration mode enabled"
                            textSize = 12f
                            typeface = Typeface.DEFAULT_BOLD
                            setTextColor(color)
                        }
                    )
                }
            )
        }.withMargins(top = 4, bottom = 4)
    }

    private fun demonstrationRecordingCard(event: ChatEvent.DemonstrationRecording): View {
        val stroke = if (event.active) Color.rgb(220, 80, 80) else Theme.Accent
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            this.strokeColor = stroke
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
            if (event.runId != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { openRunDetail(event.runId!!) }
            }
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }
        content.addView(
            TextView(activity).apply {
                text = if (event.active) "Recording demonstration…" else "Demonstration captured"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        if (!event.active && event.stepCount > 0) {
            content.addView(
                TextView(activity).apply {
                    text = "${event.stepCount} step(s) with screen context"
                    textSize = 12f
                    setTextColor(Theme.BodyText)
                    setPadding(0, 4, 0, 0)
                }
            )
            if (event.summary.isNotBlank()) {
                content.addView(
                    TextView(activity).apply {
                        text = event.summary
                        textSize = 11.5f
                        setTextColor(Theme.MutedText)
                        setPadding(0, 4, 0, 0)
                    }
                )
            }
        }
        card.addView(content)
        return card.withMargins(top = 6, bottom = 6)
    }

    private fun completionSummaryCard(summary: AgentRunCompletionSummary, runId: String): View {
        val stroke = completionSummaryStroke(summary.status)
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stroke
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { openRunDetail(runId) }
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(activity).apply {
                text = "Task summary"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        header.addView(
            activity.statusChip(
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
            TextView(activity).apply {
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
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
            addView(
                TextView(activity).apply {
                    text = label
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Theme.MutedText)
                }
            )
            addView(
                TextView(activity).apply {
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

    private fun stepTimelineCard(event: ChatEvent.StepTimeline): View {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = Theme.StrokeDark
            strokeWidth = 1
            radius = 8f
            cardElevation = 0f
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
        }
        val titleView = TextView(activity).apply {
            text = "Agent steps"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        val subtitleView = TextView(activity).apply {
            textSize = 11.5f
            setTextColor(Theme.MutedText)
            setPadding(0, 4, 0, 0)
        }
        val stepsContainer = LinearLayout(activity).apply {
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

    private fun stepTimelineEmptyState(): View {
        return TextView(activity).apply {
            text = "Steps will appear here as TouchPilot observes, decides, and acts."
            textSize = 12f
            setTextColor(Theme.MutedText)
            setPadding(0, 4, 0, 4)
        }
    }

    private fun stepTimelineRow(step: AgentStep, isLast: Boolean): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, if (isLast) 0 else 6, 0, 6)
        }
        val rail = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 2, 12, 0)
        }
        rail.addView(
            View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(10, 10).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                background = rounded(stepStatusColor(step.status), 5, stepStatusColor(step.status))
            }
        )
        if (!isLast) {
            rail.addView(
                View(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(2, 28)
                    setBackgroundColor(Theme.StrokeDark)
                }
            )
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        header.addView(
            TextView(activity).apply {
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
            TextView(activity).apply {
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
        return activity.statusChip(status.timelineChipLabel(), accent = status.timelineChipAccent()).apply {
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

    private companion object {
        const val MaxScreenSuggestions = 6
    }
}
