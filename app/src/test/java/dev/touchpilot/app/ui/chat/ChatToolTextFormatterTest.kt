package dev.touchpilot.app.ui.chat

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatToolTextFormatterTest {
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

    @Test
    fun rendersSkillContextLineWhenPresent() {
        val note = "This action is requested under the high-risk skill \"Messages\"."
        val text = ChatToolTextFormatter.approvalMessage(request(note))

        assertTrue(text.contains("Skill context: $note"), text)
    }

    @Test
    fun omitsSkillContextLineWhenBlank() {
        val text = ChatToolTextFormatter.approvalMessage(request(""))

        assertFalse(text.contains("Skill context:"), text)
        assertTrue(text.contains("Why approval is needed:"), text)
    }

    @Test
    fun redactsSensitiveApprovalPolicyCopy() {
        val text = ChatToolTextFormatter.approvalMessage(
            ToolApprovalRequest(
                tool = ToolSpec(
                    name = "tap",
                    description = "Tap a target",
                    risk = ToolRisk.MEDIUM,
                    arguments = mapOf("text" to "Target text")
                ),
                args = mapOf("text" to "otp 123456"),
                policy = PolicyDecision.RequireApproval(
                    reason = "message includes otp 123456",
                    userMessage = "Approval required for tap.",
                    dataAffected = "Email user@example.com may be sent.",
                    ifApproved = "TouchPilot will submit card 4111111111111111.",
                    skillContext = "Active skill references token=abc123"
                )
            )
        )

        assertFalse(text.contains("123456"), text)
        assertFalse(text.contains("user@example.com"), text)
        assertFalse(text.contains("4111111111111111"), text)
        assertFalse(text.contains("abc123"), text)
        assertTrue(text.contains("[REDACTED]"), text)
    }

    @Test
    fun redactsToolCallBodyDefensively() {
        val text = ChatToolTextFormatter.toolCallBody(
            dev.touchpilot.app.agent.ToolCallCardModel(
                tool = "type_text",
                args = mapOf("text" to "[REDACTED]"),
                policyStatus = dev.touchpilot.app.agent.ToolCallPolicyStatus.ALLOWED,
                resultStatus = dev.touchpilot.app.agent.ToolCallResultStatus.SUCCEEDED,
                message = "typed password=hunter2",
                verificationStatus = "passed",
                verificationReason = "email user@example.com changed"
            )
        )

        assertFalse(text.contains("hunter2"), text)
        assertFalse(text.contains("user@example.com"), text)
        assertTrue(text.contains("passed"), text)
        assertTrue(text.contains("[REDACTED]"), text)
    }
}
