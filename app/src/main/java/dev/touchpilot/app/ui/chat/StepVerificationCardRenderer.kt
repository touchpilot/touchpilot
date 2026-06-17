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
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.statusChip
import dev.touchpilot.app.ui.withMargins

class StepVerificationCardRenderer(
    private val activity: Activity,
) {
    fun render(cardModel: StepVerificationCardModel): View {
        val stroke = if (cardModel.passed) Theme.Accent else Theme.Danger
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = stroke
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
                text = cardModel.title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            activity.statusChip(
                if (cardModel.passed) "Verified" else "Failed",
                accent = cardModel.passed,
            ),
        )
        content.addView(header)
        content.addView(
            TextView(activity).apply {
                text = cardModel.body
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            },
        )
        card.addView(content)
        return card.withMargins(top = 8, bottom = 8)
    }
}
