package dev.touchpilot.app.ui.chat

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatToolTextFormatterTest {
    @Test
    fun rendersStructuredApprovalCopy() {
        val text = ChatToolTextFormatter.approvalMessage(
            ToolApprovalRequest(
                tool = ToolSpec(
                    name = "tap",
                    description = "Tap a target",
                    risk = ToolRisk.MEDIUM,
                    arguments = mapOf("text" to "Target text")
                ),
                args = mapOf("text" to "Send"),
                policy = PolicyDecision.RequireApproval(
                    reason = "sending a message requires explicit approval",
                    userMessage = "Approval required for tap.",
                    dataAffected = "A message or outbound communication may be sent from the current app.",
                    ifApproved = "TouchPilot will tap or submit the send action with the shown arguments.",
                    skillContext = "Requested under the high-risk skill \"Messages\".",
                    headline = "Approve sending a message?",
                    riskSummary = "Medium risk · medium risk tool",
                    workflowLabel = "Message sending",
                    cautionNote = "This may send a message immediately after approval."
                )
            )
        )

        assertTrue(text.startsWith("Approve sending a message?"), text)
        assertTrue(text.contains("Workflow: Message sending"), text)
        assertTrue(text.contains("Skill context:"), text)
        assertTrue(text.contains("Review carefully:"), text)
        assertTrue(text.contains("What may change:"), text)
        assertTrue(text.contains("If approved:"), text)
    }

    @Test
    fun omitsOptionalSectionsWhenBlank() {
        val text = ChatToolTextFormatter.approvalMessage(
            request(skillContext = "")
        )

        assertFalse(text.contains("Skill context:"), text)
        assertFalse(text.contains("Review carefully:"), text)
        assertTrue(text.contains("Why approval is needed:"), text)
    }

    private fun request(skillContext: String) = ToolApprovalRequest(
        tool = ToolSpec(
            name = "tap",
            description = "Tap a target",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf("text" to "Target text")
        ),
        args = mapOf("text" to "Send"),
        policy = PolicyDecision.RequireApproval(
            reason = "medium risk Android action",
            userMessage = "Approval required for tap.",
            dataAffected = "The current app or screen may be changed.",
            ifApproved = "TouchPilot will run tap.",
            skillContext = skillContext
        )
    )
}
