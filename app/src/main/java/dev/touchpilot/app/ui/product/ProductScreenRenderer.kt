package dev.touchpilot.app.ui.product

import android.app.Activity
import android.widget.LinearLayout
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.navigation.AppSection
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.sectionTitle
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.secondaryButton
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
                val examples = skill.examples.take(3)
                val skillLabel = if (examples.isNotEmpty()) {
                    examples.first()
                } else {
                    skill.title
                }
                val body = buildString {
                    appendLine(skill.description)
                    if (examples.isNotEmpty()) {
                        appendLine()
                        append("Examples")
                        appendLine(":")
                        append(examples.joinToString(separator = "\n") { example -> "• $example" })
                    }
                }
                contentRoot.addView(
                    activity.timelineCard(
                        title = skillLabel,
                        body = body,
                        actionHint = "Run skill"
                    ) {
                        runSkill(skill.id)
                    }
                )
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

    private fun rowParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins(0, 0, 8, 0)
    }
}
