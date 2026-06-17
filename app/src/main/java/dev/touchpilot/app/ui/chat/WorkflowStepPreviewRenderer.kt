package dev.touchpilot.app.ui.chat

import android.app.Activity
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.withMargins
import dev.touchpilot.app.workflow.WorkflowStepPolicyOutcome

class WorkflowStepPreviewRenderer(
    private val activity: Activity,
) {
    fun render(event: ChatEvent.WorkflowPolicyPreview): View {
        val card = MaterialCardView(activity).apply {
            radius = 16f
            cardElevation = 0f
            setCardBackgroundColor(Theme.Card)
            strokeWidth = 1
            strokeColor = Theme.StrokeDark
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 14, 16, 14)
        }

        column.addView(
            TextView(activity).apply {
                text = "Workflow policy preview"
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Theme.BodyText)
            }
        )
        column.addView(
            TextView(activity).apply {
                text = event.workflowTitle
                textSize = 12f
                setTextColor(Theme.MutedText)
                setPadding(0, 2, 0, 8)
            }
        )
        column.addView(
            TextView(activity).apply {
                text = event.summary
                textSize = 13.5f
                setTextColor(Theme.BodyText)
                setLineSpacing(4f, 1f)
                setPadding(0, 0, 0, 10)
            }
        )

        event.steps.forEach { step ->
            column.addView(stepRow(step.stepIndex, step.tool, step.outcome, step.note))
        }

        card.addView(column)
        return card.withMargins(top = 6, bottom = 6)
    }

    private fun stepRow(
        stepIndex: Int,
        tool: String,
        outcome: WorkflowStepPolicyOutcome,
        note: String,
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 4)
        }
        row.addView(
            TextView(activity).apply {
                text = outcomeLabel(outcome)
                textSize = 11f
                setTextColor(outcomeColor(outcome))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 2, 10, 0)
                minWidth = 108
            }
        )
        row.addView(
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(activity).apply {
                        text = "Step $stepIndex · $tool"
                        textSize = 13f
                        setTextColor(Theme.BodyText)
                    }
                )
                if (note.isNotBlank() && outcome != WorkflowStepPolicyOutcome.AUTO) {
                    addView(
                        TextView(activity).apply {
                            text = note
                            textSize = 12f
                            setTextColor(Theme.MutedText)
                            setPadding(0, 2, 0, 0)
                        }
                    )
                }
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        return row
    }

    private fun outcomeLabel(outcome: WorkflowStepPolicyOutcome): String = when (outcome) {
        WorkflowStepPolicyOutcome.AUTO -> "Auto"
        WorkflowStepPolicyOutcome.NEEDS_APPROVAL -> "Approval"
        WorkflowStepPolicyOutcome.BLOCKED -> "Blocked"
    }

    private fun outcomeColor(outcome: WorkflowStepPolicyOutcome): Int = when (outcome) {
        WorkflowStepPolicyOutcome.AUTO -> Theme.Accent
        WorkflowStepPolicyOutcome.NEEDS_APPROVAL -> Theme.Warning
        WorkflowStepPolicyOutcome.BLOCKED -> Theme.Danger
    }
}
