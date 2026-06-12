package dev.touchpilot.app.ui.chat

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import dev.touchpilot.app.R
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.ui.TouchPilotTheme as Theme
import dev.touchpilot.app.ui.primaryButton
import dev.touchpilot.app.ui.rowButtonParams
import dev.touchpilot.app.ui.secondaryButton
import dev.touchpilot.app.ui.withMargins

class ChatDecisionCardRenderer(
    private val activity: Activity,
    private val refreshChatScreen: () -> Unit
) {
    fun approvalCard(event: ChatEvent.ApprovalPrompt): View {
        val request = event.request
        val tool = request.tool
        val card = MaterialCardView(activity).apply {
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
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
        }

        val statusLabel = when (event.state) {
            ApprovalState.PENDING -> "Approval requested"
            ApprovalState.APPROVED -> "Approved"
            ApprovalState.REJECTED -> "Rejected"
        }
        content.addView(
            TextView(activity).apply {
                text = request.policy.headline.ifBlank { "$statusLabel - ${tool.name}" }
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        content.addView(
            TextView(activity).apply {
                text = ChatToolTextFormatter.approvalMessage(event.request)
                textSize = 12.5f
                setTextColor(Theme.BodyText)
                setPadding(0, 8, 0, 0)
            }
        )

        if (event.state == ApprovalState.PENDING) {
            val buttonRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 14, 0, 0)
            }
            buttonRow.addView(
                activity.primaryButton("Approve", iconRes = R.drawable.ic_check) {
                    resolveApproval(event, true)
                }.apply { id = R.id.approval_approve_button },
                rowButtonParams()
            )
            buttonRow.addView(
                activity.secondaryButton("Reject", iconRes = R.drawable.ic_close) {
                    resolveApproval(event, false)
                }.apply { id = R.id.approval_reject_button },
                rowButtonParams()
            )
            content.addView(buttonRow)
        }

        card.addView(content)
        return card.withMargins(top = 8, bottom = 8)
    }

    fun clarificationCard(event: ChatEvent.ClarificationPrompt): View {
        val card = MaterialCardView(activity).apply {
            setCardBackgroundColor(Theme.Card)
            strokeColor = when (event.state) {
                ClarificationState.PENDING -> Theme.Accent
                ClarificationState.ANSWERED -> Theme.StrokeDark
            }
            strokeWidth = 2
            radius = 8f
            cardElevation = 0f
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
        }

        val statusLabel = when (event.state) {
            ClarificationState.PENDING -> "Clarification needed"
            ClarificationState.ANSWERED -> "Answered"
        }
        content.addView(
            TextView(activity).apply {
                text = statusLabel
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            }
        )
        content.addView(
            TextView(activity).apply {
                text = event.question
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(0, 8, 0, 0)
            }
        )
        if (event.detail.isNotBlank()) {
            content.addView(
                TextView(activity).apply {
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
                    activity.secondaryButton(label) {
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
                TextView(activity).apply {
                    text = "You: ${event.selectedAnswer.orEmpty()}"
                    textSize = 12.5f
                    setTextColor(Theme.Accent)
                    setPadding(0, 10, 0, 0)
                }
            )
        } else if (event.state == ClarificationState.PENDING) {
            content.addView(
                TextView(activity).apply {
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

    private fun resolveApproval(event: ChatEvent.ApprovalPrompt, approved: Boolean) {
        if (event.state != ApprovalState.PENDING) return
        event.state = if (approved) ApprovalState.APPROVED else ApprovalState.REJECTED
        event.onDecision(approved)
        refreshChatScreen()
    }

    private fun resolveClarification(event: ChatEvent.ClarificationPrompt, answer: String) {
        if (event.state != ClarificationState.PENDING) return
        event.state = ClarificationState.ANSWERED
        event.selectedAnswer = answer
        event.onAnswer(answer)
        refreshChatScreen()
    }
}
