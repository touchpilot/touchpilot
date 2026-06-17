package dev.touchpilot.app.ui.chat

import android.widget.LinearLayout
import android.widget.TextView
import dev.touchpilot.app.agent.AgentRunCompletionSummary
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.SkillUseCardModel
import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.security.ToolApprovalRequest

sealed class ChatEvent {
    data class User(val text: String) : ChatEvent()
    data class Agent(val text: String, val detail: String) : ChatEvent()
    data class ScreenSummary(val summary: String, val suggestions: List<String>) : ChatEvent()
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
    data class SkillUse(val card: SkillUseCardModel) : ChatEvent()
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

enum class ApprovalState { PENDING, APPROVED, REJECTED }
enum class ClarificationState { PENDING, ANSWERED }
