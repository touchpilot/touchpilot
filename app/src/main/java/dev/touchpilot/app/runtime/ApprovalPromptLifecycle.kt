package dev.touchpilot.app.runtime

import dev.touchpilot.app.ui.chat.ApprovalState
import dev.touchpilot.app.ui.chat.ChatEvent

/**
 * Rejects every pending approval prompt and notifies blocked waiters.
 *
 * [ChatEvent.ApprovalPrompt.onDecision] must be invoked so threads blocked in
 * [AgentRunController.approveTool] release immediately. Updating [ApprovalState]
 * alone leaves the [java.util.concurrent.CountDownLatch] waiting until timeout.
 */
internal fun rejectPendingApprovalPrompts(conversation: Iterable<ChatEvent>) {
    conversation.forEach { event ->
        if (event is ChatEvent.ApprovalPrompt && event.state == ApprovalState.PENDING) {
            event.state = ApprovalState.REJECTED
            event.onDecision(false)
        }
    }
}
