package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
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
                activeScreen = "Banking app home screen",
                activeScreenContext = screenContext(
                    appLabel = "MyBank",
                    packageName = "com.example.bank",
                    texts = listOf("Accounts", "Transfer")
                )
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("banking"), approval.reason)
    }

    @Test
    fun benignOpenAppRequiresApprovalWhenScreenContainsPassword() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("open_app"),
                args = mapOf("target" to "Calculator"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Passwords saved earlier in this session",
                activeScreenContext = screenContext(
                    appLabel = "Vault",
                    packageName = "com.example.vault",
                    texts = listOf("Saved passwords", "Recovery codes")
                )
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("sensitive"), approval.reason)
    }

    @Test
    fun benignScrollRequiresApprovalWhenScreenContainsPurchase() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("scroll"),
                args = mapOf("direction" to "forward"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Order history including past purchase totals",
                activeScreenContext = screenContext(
                    appLabel = "Shop",
                    packageName = "com.example.shop",
                    texts = listOf("Checkout", "Order total")
                )
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("checkout") || approval.reason.contains("payment"), approval.reason)
    }

    @Test
    fun benignPressBackRequiresApprovalWhenScreenMentionsDeleteAccount() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("press_back"),
                args = emptyMap(),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Help article: how to delete account permanently",
                activeScreenContext = screenContext(
                    appLabel = "Help Center",
                    packageName = "com.example.help",
                    texts = listOf("Delete account", "Permanently remove account")
                )
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

    @Test
    fun blocksPermissionChangeInPermissionDialog() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Allow"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Camera permission",
                activeScreenContext = screenContext(
                    appLabel = "Permission controller",
                    packageName = "com.android.permissioncontroller",
                    texts = listOf("Allow", "While using the app")
                )
            )
        )

        val blocked = assertIs<PolicyDecision.Block>(decision)
        assertTrue(blocked.reason.contains("permission"), blocked.reason)
    }

    @Test
    fun blocksPaymentConfirmInBankingApp() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Confirm"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Wire transfer review",
                activeScreenContext = screenContext(
                    appLabel = "MyBank",
                    packageName = "com.example.bank",
                    texts = listOf("Transfer", "Confirm")
                )
            )
        )

        val blocked = assertIs<PolicyDecision.Block>(decision)
        assertTrue(blocked.reason.contains("payment"), blocked.reason)
    }

    @Test
    fun messagingScreenUsesAppAwareApprovalReason() {
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Archive"),
                source = ToolSource.LOCAL_ROUTER,
                activeScreen = "Messages conversation",
                activeScreenContext = screenContext(
                    appLabel = "Messages",
                    packageName = "com.google.android.apps.messaging",
                    texts = listOf("Archive", "Reply")
                )
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("messaging"), approval.reason)
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

    private fun screenContext(
        appLabel: String,
        packageName: String,
        texts: List<String>
    ): ScreenContext {
        return ScreenContext(
            appLabel = appLabel,
            packageName = packageName,
            nodes = texts.mapIndexed { index, text ->
                ScreenNode(
                    nodeId = "n$index",
                    role = NodeRole.BUTTON,
                    text = ScreenText.of(text),
                    bounds = NodeBounds(0, index * 10, 100, index * 10 + 10),
                    clickable = true
                )
            }
        )
    }
}
