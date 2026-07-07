package dev.touchpilot.app.runtime

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.ui.chat.ApprovalState
import dev.touchpilot.app.ui.chat.ChatEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApprovalPromptLifecycleTest {

    @Test
    fun rejectPendingApprovalPrompts_unblocksWaitingApprovalThread() {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(true)
        val prompt = approvalPrompt(
            onDecision = { decision ->
                approved.set(decision)
                latch.countDown()
            }
        )
        val conversation = listOf<ChatEvent>(prompt)

        rejectPendingApprovalPrompts(conversation)

        assertTrue(latch.await(100, TimeUnit.MILLISECONDS))
        assertFalse(approved.get())
        assertEquals(ApprovalState.REJECTED, prompt.state)
    }

    @Test
    fun rejectPendingApprovalPrompts_leavesResolvedPromptsUntouched() {
        val latch = CountDownLatch(1)
        var callbackCount = 0
        val prompt = approvalPrompt(
            onDecision = {
                callbackCount += 1
                latch.countDown()
            }
        )
        prompt.state = ApprovalState.APPROVED

        rejectPendingApprovalPrompts(listOf(prompt))

        assertFalse(latch.await(50, TimeUnit.MILLISECONDS))
        assertEquals(0, callbackCount)
        assertEquals(ApprovalState.APPROVED, prompt.state)
    }

    @Test
    fun cancelDuringApprovalReleasesBlockedAgentLoop() {
        val approvalShown = CountDownLatch(1)
        val conversation = mutableListOf<ChatEvent>()
        val signal = AtomicBoolean(false)

        val agentThread = Thread {
            val approved = simulateApproveTool(
                conversation = conversation,
                cancellationSignal = signal,
                request = sampleApprovalRequest(),
                runOnUiThread = { block -> block() },
                onPromptPosted = { approvalShown.countDown() },
            )
            assertFalse(approved)
        }

        agentThread.start()
        assertTrue(
            approvalShown.await(500, TimeUnit.MILLISECONDS),
            "Expected approval prompt to be posted before cancel"
        )

        signal.set(true)
        rejectPendingApprovalPrompts(conversation)

        agentThread.join(1_000)
        assertFalse(agentThread.isAlive, "Agent thread should unblock promptly after cancel")
        assertEquals(ApprovalState.REJECTED, (conversation.single() as ChatEvent.ApprovalPrompt).state)
    }

    private fun simulateApproveTool(
        conversation: MutableList<ChatEvent>,
        cancellationSignal: AtomicBoolean,
        request: ToolApprovalRequest,
        runOnUiThread: (() -> Unit) -> Unit,
        onPromptPosted: () -> Unit = {},
    ): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)

        runOnUiThread {
            val prompt = ChatEvent.ApprovalPrompt(
                request = request,
                onDecision = { decision ->
                    approved.set(decision)
                    latch.countDown()
                }
            )
            conversation += prompt
            onPromptPosted()
        }

        val approvedByUser = latch.await(5 * 60 * 1000L, TimeUnit.MILLISECONDS) && approved.get()
        runOnUiThread {
            if (!cancellationSignal.get()) {
                // no-op in this harness
            }
        }
        return approvedByUser
    }

    private fun approvalPrompt(onDecision: (Boolean) -> Unit): ChatEvent.ApprovalPrompt {
        return ChatEvent.ApprovalPrompt(
            request = sampleApprovalRequest(),
            onDecision = onDecision,
        )
    }

    private fun sampleApprovalRequest(): ToolApprovalRequest {
        val spec = AndroidToolCatalog.find("open_app")!!
        return ToolApprovalRequest(
            tool = spec,
            args = mapOf("target" to "Settings"),
            policy = PolicyDecision.RequireApproval(
                reason = "medium-risk action",
                userMessage = "Approve opening Settings.",
                dataAffected = "The foreground app may change.",
                ifApproved = "TouchPilot will open Settings.",
            ),
        )
    }
}
