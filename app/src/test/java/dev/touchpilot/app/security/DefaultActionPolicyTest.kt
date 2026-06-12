package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultActionPolicyTest {
    private val policy = DefaultActionPolicy()

    @Test
    fun blocksPaymentWorkflow() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Pay now"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Checkout payment screen"
            )
        )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun blocksSensitiveTextEntry() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("type_text"),
                args = mapOf("text" to "password=correct horse battery staple"),
                source = ToolSource.LOCAL_MODEL
            )
        )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun requiresApprovalForMessageSend() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Send"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Messages conversation"
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun benignTapRequiresApprovalWhenScreenContainsBank() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Settings"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Banking app home screen"
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun benignOpenAppRequiresApprovalWhenScreenContainsPassword() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("open_app"),
                args = mapOf("target" to "Calculator"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Passwords saved earlier in this session"
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun benignScrollRequiresApprovalWhenScreenContainsPurchase() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("scroll"),
                args = mapOf("direction" to "forward"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Order history including past purchase totals"
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun benignPressBackRequiresApprovalWhenScreenMentionsDeleteAccount() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("press_back"),
                args = emptyMap(),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Help article: how to delete account permanently"
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun mediumRiskToolFromDirectDebugRequiresApproval() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "OK"),
                source = ToolSource.DIRECT_DEBUG
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun highRiskToolFromDirectDebugRequiresApproval() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = ToolSpec(
                    name = "open_app",
                    description = "Open an app",
                    risk = ToolRisk.HIGH,
                    arguments = emptyMap(),
                    requiredArguments = emptySet()
                ),
                args = mapOf("target" to "Settings"),
                source = ToolSource.DIRECT_DEBUG
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun allowsLowRiskObservation() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = ToolSpec(
                    name = "observe_screen",
                    description = "Observe screen",
                    risk = ToolRisk.LOW,
                    arguments = emptyMap()
                ),
                args = emptyMap(),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Password field visible"
            )
        )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun approvalMentionsElevatedRiskSkill() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Send"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Messages conversation",
                activeSkillTitle = "Messages",
                activeSkillRisk = SkillRisk.HIGH
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.skillContext.contains("high-risk"), approval.skillContext)
        assertTrue(approval.skillContext.contains("Messages"), approval.skillContext)
    }

    @Test
    fun approvalHasNoSkillContextWithoutSkill() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("open_app"),
                args = mapOf("target" to "Settings"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Banking app home screen"
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.skillContext.isEmpty(), approval.skillContext)
    }

    @Test
    fun lowRiskSkillAddsNoExtraCaution() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Send"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Messages conversation",
                activeSkillTitle = "Browser",
                activeSkillRisk = SkillRisk.LOW
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.skillContext.isEmpty(), approval.skillContext)
    }

    @Test
    fun elevatedSkillDoesNotEscalateLowRiskTool() {
        // A high-risk skill must NOT turn an auto-allowed low-risk tool into an
        // approval — risk metadata may only raise caution in copy, not policy.
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = ToolSpec(
                    name = "observe_screen",
                    description = "Observe screen",
                    risk = ToolRisk.LOW,
                    arguments = emptyMap()
                ),
                args = emptyMap(),
                source = ToolSource.LOCAL_ROUTER,
                activeSkillTitle = "Messages",
                activeSkillRisk = SkillRisk.HIGH
            )
        )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun elevatedSkillDoesNotBypassBlockedWorkflow() {
        // A skill of any risk must NOT unblock a blocked workflow.
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Pay now"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Checkout payment screen",
                activeSkillTitle = "Shopping",
                activeSkillRisk = SkillRisk.HIGH
            )
        )

        assertIs<PolicyDecision.Block>(decision)
    }

    private fun mediumTool(name: String): ToolSpec {
        return ToolSpec(
            name = name,
            description = "Test tool",
            risk = ToolRisk.MEDIUM,
            arguments = emptyMap(),
            requiredArguments = emptySet()
        )
    }
}
