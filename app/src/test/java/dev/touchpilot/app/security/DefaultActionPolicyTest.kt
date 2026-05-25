package dev.touchpilot.app.security

import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

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
    fun requiresApprovalForOpenSettingsPanel() {
        val tool = AndroidToolCatalog.find("open_settings_panel")
        assertNotNull(tool)
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = tool,
                args = mapOf("panel" to "wifi"),
                source = ToolSource.LOCAL_MODEL
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
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
