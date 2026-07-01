package dev.touchpilot.app.ui.product

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillCatalogFormatter
import dev.touchpilot.app.navigation.AppSection
import dev.touchpilot.app.ui.TouchPilotTheme
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.withMargins
import dev.touchpilot.app.workflow.WorkflowLibraryEntry

class ProductScreenRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val skills: List<Skill>,
    private val workflows: List<WorkflowLibraryEntry>,
    private val openAccessibilitySettings: () -> Unit,
    private val showSection: (AppSection) -> Unit,
    private val openSettingsTools: () -> Unit,
    private val runSkill: (String) -> Unit,
    private val openWorkflowDetail: (String) -> Unit,
) {
    fun render() {
        contentRoot.addView(
            activity.summaryCard(
                title = "TouchPilot",
                value = "Things you can use right now",
                chipText = "local-first",
                chipAccent = true
            )
        )

        contentRoot.addView(activity.sectionTitle("Start here"))
        contentRoot.addView(
            activity.timelineCard(
                title = "Ask TouchPilot",
                body = "Open chat for a request, a question, or a step-by-step task.",
                actionHint = "Open Chat"
            ) {
                showSection(AppSection.CHAT)
            }
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Inspect the device",
                body = "Read the current screen, app, and basic device state.",
                actionHint = "Open Tools"
            ) {
                openSettingsTools()
            }
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Review activity",
                body = "Check recent runs, approvals, and tool results.",
                actionHint = "Open Logs"
            ) {
                showSection(AppSection.LOGS)
            }
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Configure host",
                body = "Choose skills, runtime mode, MCP, and other settings.",
                actionHint = "Open Settings"
            ) {
                showSection(AppSection.SETTINGS)
            }
        )

        contentRoot.addView(activity.sectionTitle("Skills you can run"))
        if (skills.isEmpty()) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "No bundled skills are enabled",
                    body = "Enable a skill in Settings to unlock a focused task set.",
                    actionHint = "Open Settings"
                ) {
                    showSection(AppSection.SETTINGS)
                }
            )
        } else {
            skills.forEach { skill ->
                val card = SkillCatalogFormatter.useCard(skill)
                contentRoot.addView(skillCard(card) { runSkill(skill.id) })
            }
        }

        contentRoot.addView(activity.sectionTitle("Workflows you can review"))
        if (workflows.isEmpty()) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "No saved workflows yet",
                    body = "Captured workflows and bundled examples will appear here once available.",
                    actionHint = "Open Settings"
                ) {
                    showSection(AppSection.SETTINGS)
                }
            )
        } else {
            workflows.forEach { workflow ->
                val status = workflow.lastRun?.displayLabel ?: "never run"
                val body = buildString {
                    appendLine(workflow.definition.description.ifBlank { "No description provided." })
                    appendLine()
                    appendLine("${workflow.stepCount} steps")
                    append("Last replay: ")
                    append(status)
                }
                contentRoot.addView(
                    activity.timelineCard(
                        title = workflow.definition.title,
                        body = body,
                        actionHint = "Review workflow"
                    ) {
                        openWorkflowDetail(workflow.definition.id)
                    }
                )
            }
        }

        contentRoot.addView(activity.sectionTitle("Quick actions"))
        val quickActionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        quickActionRow.addView(
            activity.primaryButton("Open Chat") {
                showSection(AppSection.CHAT)
            },
            rowParams()
        )
        quickActionRow.addView(
            activity.primaryButton("Open Settings") {
                showSection(AppSection.SETTINGS)
            },
            rowParams()
        )
        contentRoot.addView(quickActionRow)

        contentRoot.addView(
            activity.secondaryButton("Open Accessibility Settings") {
                openAccessibilitySettings()
            }
        )
    }

    private fun skillCard(card: SkillCatalogFormatter.UseCard, onRun: () -> Unit): View {
        val root = MaterialCardView(activity).apply {
            setCardBackgroundColor(TouchPilotTheme.Card)
            strokeColor = TouchPilotTheme.Accent
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { onRun() }
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
                text = card.title
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(activity.statusChip(card.riskLabel, accent = card.riskAccent))
        content.addView(header)

        content.addView(bodyText(card.scope, muted = false, topPadding = 8))

        if (card.examples.isNotEmpty()) {
            content.addView(cardLabel("Example prompts"))
            content.addView(
                bodyText(
                    card.examples.joinToString(separator = "\n") { example -> "• $example" },
                    muted = false,
                    topPadding = 2
                )
            )
        }

        content.addView(cardLabel("Permissions"))
        content.addView(bodyText(card.permissions, muted = true, topPadding = 2))

        content.addView(
            TextView(activity).apply {
                text = card.runLabel
                textSize = 11.5f
                setTextColor(TouchPilotTheme.Accent)
                setPadding(0, 10, 0, 0)
            }
        )

        root.addView(content)
        return root.withMargins(top = 8, bottom = 8)
    }

    private fun cardLabel(text: String): TextView = TextView(activity).apply {
        setText(text)
        textSize = 10.5f
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = true
        letterSpacing = 0.06f
        setTextColor(TouchPilotTheme.MutedText)
        setPadding(0, 12, 0, 0)
    }

    private fun bodyText(value: String, muted: Boolean, topPadding: Int): TextView = TextView(activity).apply {
        text = value
        textSize = 12.5f
        setTextColor(if (muted) TouchPilotTheme.MutedText else TouchPilotTheme.BodyText)
        setPadding(0, topPadding, 0, 0)
    }

    private fun rowParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(0, 0, 8, 0)
    }
}
