package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertIs

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
                source = ToolSource.CLOUD_FALLBACK
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
