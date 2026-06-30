package dev.touchpilot.app.ui.logs

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.R
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunDisplayStep
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunStepSeverity
import dev.touchpilot.app.agent.AgentRunStepStatus
import dev.touchpilot.app.logging.DebugTraceExporter
import dev.touchpilot.app.agent.severity
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.ui.rowButtonParams
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.summaryCard
import dev.touchpilot.app.ui.timelineCard
import dev.touchpilot.app.ui.withMargins
import java.io.File

class AgentRunDetailRenderer(
    private val activity: Activity,
    private val contentRoot: LinearLayout,
    private val runId: String?,
    private val findAgentRun: (String) -> AgentRunRecord?,
    private val closeRunDetail: () -> Unit,
    private val exportRunTrace: (AgentRunRecord) -> File,
    private val exportSkillCandidate: (AgentRunRecord) -> File?,
    private val buildSkillCandidate: (AgentRunRecord) -> String?,
    private val saveSkillCandidate: (String) -> DebugTraceExporter.SkillCandidateSaveResult
) {
    fun render() {
        contentRoot.addView(
            activity.secondaryButton("Go Back") {
                closeRunDetail()
            }.apply {
                id = R.id.run_detail_back_button
                minHeight = 46
            }.withMargins(bottom = 12)
        )

        val record = runId?.let(findAgentRun)
        if (record == null) {
            contentRoot.addView(
                activity.timelineCard(
                    title = "Run unavailable",
                    body = "This run is no longer available. It may have been cleared when the app restarted."
                )
            )
            return
        }

        contentRoot.addView(
            activity.summaryCard(
                title = "Task",
                value = SensitiveTextRedactor.redact(record.task),
                chipText = record.id,
                chipAccent = true
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Timing",
                body = buildString {
                    append("Started: ${AgentRunDetailFormatter.formatTimestamp(record.startedAtMillis)}")
                    appendLine()
                    append("Completed: ${AgentRunDetailFormatter.formatTimestamp(record.completedAtMillis)}")
                }
            )
        )
        contentRoot.addView(
            activity.timelineCard(
                title = "Stop reason",
                body = AgentRunDetailFormatter.deriveStopReason(record)
            )
        )
        contentRoot.addView(
            activity.primaryButton("Export Run Trace") {
                val file = exportRunTrace(record)
                Toast.makeText(
                    activity,
                    "Run trace exported: ${file.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }.apply { id = R.id.export_run_trace_button }
        )
        contentRoot.addView(
            activity.secondaryButton("Export Skill Candidate") {
                val file = exportSkillCandidate(record)
                Toast.makeText(
                    activity,
                    if (file == null) {
                        "No workflow trace available for this run."
                    } else {
                        "Skill candidate exported: ${file.name}"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }.apply { id = R.id.export_skill_candidate_button }
        )
        renderSkillCandidateReview(record)

        val steps = AgentRunDetailFormatter.formatSteps(record)
        contentRoot.addView(
            TextView(activity).apply {
                text = if (steps.isEmpty()) "Steps" else "Steps (${steps.size})"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 12, 0, 4)
            }
        )
        if (steps.isEmpty()) {
            contentRoot.addView(
                activity.timelineCard(
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

    private fun renderSkillCandidateReview(record: AgentRunRecord) {
        val candidate = buildSkillCandidate(record)
        contentRoot.addView(
            activity.summaryCard(
                title = "Skill Candidate",
                value = if (candidate == null) {
                    "No workflow trace available for this run."
                } else {
                    "Review, edit, and approve this generated candidate before saving it as a skill."
                },
                chipText = "candidate",
                chipAccent = candidate != null
            )
        )
        if (candidate == null) return

        val editor = EditText(activity).apply {
            id = R.id.skill_candidate_editor
            setText(candidate)
            setTextColor(Color.WHITE)
            setHint("Editable skill markdown")
            setHintTextColor(Theme.MutedText)
            setTextIsSelectable(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setHorizontallyScrolling(false)
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Theme.SurfaceRaised)
            minLines = 10
            isVerticalScrollBarEnabled = true
            typeface = Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (activity.resources.displayMetrics.density * 260f).toInt()
            ).apply {
                setMargins(0, 8, 0, 8)
            }
        }

        contentRoot.addView(editor)

        val actions = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.addView(
            activity.primaryButton("Save as skill") {
                when (val result = saveSkillCandidate(editor.text.toString())) {
                    is DebugTraceExporter.SkillCandidateSaveResult.Saved -> {
                        Toast.makeText(
                            activity,
                            "Skill candidate saved: ${result.skillId}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is DebugTraceExporter.SkillCandidateSaveResult.InvalidCandidate -> {
                        Toast.makeText(
                            activity,
                            "Cannot save candidate: ${result.errors.joinToString(", ")}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is DebugTraceExporter.SkillCandidateSaveResult.Failure -> {
                        Toast.makeText(
                            activity,
                            "Cannot save candidate: ${result.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }.apply {
                id = R.id.approve_skill_candidate_button
            },
            rowButtonParams()
        )
        actions.addView(
            activity.secondaryButton("Discard") {
                editor.setText(candidate)
                Toast.makeText(
                    activity,
                    "Candidate discarded.",
                    Toast.LENGTH_SHORT
                ).show()
            }.apply {
                id = R.id.discard_skill_candidate_button
            },
            rowButtonParams()
        )
        contentRoot.addView(actions)
    }

    private fun runDetailStepCard(step: AgentRunDisplayStep): View {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stepStatusColor(step.status)
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
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
                text = "Step ${step.index}"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(activity.statusChip(step.status.label, accent = step.status != AgentRunStepStatus.INFO))
        content.addView(header)
        content.addView(
            TextView(activity).apply {
                text = step.title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 0)
            }
        )
        content.addView(
            TextView(activity).apply {
                text = AgentRunDetailFormatter.formatTimestamp(step.timestampMillis)
                textSize = 11f
                setTextColor(Theme.MutedText)
                setPadding(0, 4, 0, 0)
            }
        )
        content.addView(
            TextView(activity).apply {
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
        return when (status.severity) {
            AgentRunStepSeverity.POSITIVE -> Theme.Accent
            AgentRunStepSeverity.NEGATIVE -> Theme.Danger
            AgentRunStepSeverity.CAUTION -> Theme.Warning
            AgentRunStepSeverity.IN_PROGRESS -> InProgressColor
            AgentRunStepSeverity.NEUTRAL -> Theme.StrokeDark
        }
    }

    private companion object {
        // Calm "in progress" blue, kept distinct from the amber caution color.
        val InProgressColor: Int = Color.rgb(86, 156, 255)
    }
}
