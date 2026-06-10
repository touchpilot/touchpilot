package dev.touchpilot.app.ui.chat

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.agent.ToolCallPolicyStatus
import dev.touchpilot.app.agent.ToolCallResultStatus
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.withMargins

class ToolCallCardRenderer(
    private val activity: Activity
) {
    fun render(cardModel: ToolCallCardModel): View {
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

        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stroke
            strokeWidth = if (blocked || failed || needsApproval) 2 else 1
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
                text = cardModel.tool
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            activity.statusChip(
                cardModel.resultStatus.label,
                accent = cardModel.resultStatus == ToolCallResultStatus.SUCCEEDED
            )
        )
        content.addView(header)

        content.addView(
            TextView(activity).apply {
                text = "Policy: ${cardModel.policyStatus.label}"
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(stroke)
                setPadding(0, 8, 0, 0)
            }
        )

        content.addView(
            TextView(activity).apply {
                text = ChatToolTextFormatter.toolCallBody(cardModel)
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            }
        )
        card.addView(content)
        return card.withMargins(top = 6, right = 24, bottom = 6)
    }
}
