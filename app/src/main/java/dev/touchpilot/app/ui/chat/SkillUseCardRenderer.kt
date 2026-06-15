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
import dev.touchpilot.app.agent.SkillUseCardModel
import dev.touchpilot.app.memory.SkillDetailFormatter
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.withMargins

class SkillUseCardRenderer(
    private val activity: Activity,
    private val openSkillDetail: (String) -> Unit,
) {
    fun render(cardModel: SkillUseCardModel): View {
        val risk = SkillDetailFormatter.riskPresentation(cardModel.risk)
        val stroke = when (cardModel.risk) {
            SkillRisk.LOW -> Theme.StrokeDark
            SkillRisk.MEDIUM -> Theme.Warning
            SkillRisk.HIGH -> Theme.Danger
        }

        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            this.strokeColor = stroke
            strokeWidth = if (cardModel.risk == SkillRisk.LOW) 1 else 2
            radius = 8f
            cardElevation = 0f
            isClickable = true
            isFocusable = true
            setOnClickListener { openSkillDetail(cardModel.skillId) }
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
                text = cardModel.title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            activity.statusChip(
                risk.label,
                accent = risk.accent,
            )
        )
        content.addView(header)

        content.addView(
            TextView(activity).apply {
                text = cardModel.activationSource.label
                textSize = 11.5f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Theme.Accent)
                setPadding(0, 8, 0, 0)
            }
        )

        content.addView(
            TextView(activity).apply {
                text = cardModel.reason
                textSize = 12f
                setTextColor(Theme.MutedText)
                setLineSpacing(2f, 1f)
                setPadding(0, 4, 0, 0)
            }
        )

        content.addView(
            TextView(activity).apply {
                text = "Allowed tools: ${cardModel.allowedToolsSummary}"
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setLineSpacing(2f, 1f)
                setPadding(0, 8, 0, 0)
            }
        )

        content.addView(
            TextView(activity).apply {
                text = "Skill allowlists scope tools. Policy and approvals still decide execution."
                textSize = 11f
                setTextColor(Theme.MutedText)
                setLineSpacing(2f, 1f)
                setPadding(0, 8, 0, 0)
            }
        )

        content.addView(
            TextView(activity).apply {
                text = "Tap for skill details"
                textSize = 11.5f
                setTextColor(Theme.Accent)
                setPadding(0, 10, 0, 0)
            }
        )

        card.addView(content)
        return card.withMargins(top = 6, right = 24, bottom = 6)
    }
}
