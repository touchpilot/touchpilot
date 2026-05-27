package dev.touchpilot.app.agent

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ToolCallCardModelTest {
    @Test
    fun buildsSucceededCardWithRedactedArgsAndVerification() {
        val command = AgentCommand(
            tool = "type_text",
            args = mapOf("text" to "password=hunter2"),
            finalAnswer = null
        )
        val events = listOf(
            assertNotNull(AgentEvent.toolRequested(command, ToolSource.LOCAL_MODEL)),
            assertNotNull(AgentEvent.toolRunning(command, ToolSource.LOCAL_MODEL)),
            AgentEvent.toolResult(
                tool = "type_text",
                result = ToolResult(
                    ok = true,
                    message = "typed password=hunter2",
                    data = mapOf(
                        "verification_status" to "passed",
                        "verification_reason" to "field changed"
                    )
                )
            )
        )

        val card = ToolCallCardModel.fromEvents(events).single()

        assertEquals("type_text", card.tool)
        assertEquals("[REDACTED]", card.args["text"])
        assertEquals(ToolCallPolicyStatus.ALLOWED, card.policyStatus)
        assertEquals(ToolCallResultStatus.SUCCEEDED, card.resultStatus)
        assertEquals("passed", card.verificationStatus)
        assertEquals("field changed", card.verificationReason)
        assertFalse("hunter2" in card.message)
    }

    @Test
    fun marksApprovalAndBlockedStatesClearly() {
        val spec = assertNotNull(AndroidToolCatalog.find("open_app"))
        val command = AgentCommand(
            tool = "open_app",
            args = mapOf("target" to "Settings"),
            finalAnswer = null
        )
        val approval = AgentEvent.approvalRequired(
            ToolApprovalRequest(
                tool = spec,
                args = mapOf("target" to "Settings"),
                policy = PolicyDecision.RequireApproval(
                    reason = "medium risk Android action",
                    userMessage = "Opening Settings requires approval.",
                    dataAffected = "The foreground app changes.",
                    ifApproved = "TouchPilot opens Settings."
                )
            )
        )
        val running = assertNotNull(AgentEvent.toolRunning(command, ToolSource.LOCAL_ROUTER))
        val succeeded = AgentEvent.toolResult(
            tool = "open_app",
            result = ToolResult(ok = true, message = "Opened Settings")
        )
        val blocked = AgentEvent.PolicyBlocked(
            tool = "type_text",
            reason = "secret entry is blocked",
            userMessage = "TouchPilot will not enter secrets."
        )

        val cards = ToolCallCardModel.fromEvents(listOf(approval, running, succeeded, blocked))

        assertEquals(ToolCallPolicyStatus.APPROVED, cards[0].policyStatus)
        assertEquals(ToolCallResultStatus.SUCCEEDED, cards[0].resultStatus)
        assertEquals(ToolCallPolicyStatus.BLOCKED, cards[1].policyStatus)
        assertEquals(ToolCallResultStatus.BLOCKED, cards[1].resultStatus)
    }
}
