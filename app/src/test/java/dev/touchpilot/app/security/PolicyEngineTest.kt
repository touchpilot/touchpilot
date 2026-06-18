package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PolicyEngineTest {
    private val engine = PolicyEngine()

    @Test
    fun lowRiskToolIsAllowed() {
        val decision = decide("observe_screen", risk = ToolRisk.LOW)

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun lowRiskToolsBypassWorkflowChecks() {
        val decision = engine.decide(
            request(
                tool = observeScreen(),
                args = emptyMap(),
                activeScreen = "Password field visible"
            )
        )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun mediumToolWithoutSensitiveWorkflowAsks() {
        val decision = decide("tap", args = mapOf("text" to "Settings"))

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun sensitiveWorkflowBlocksEvenWhenToolRiskWouldOnlyAsk() {
        val decision = decide("tap", args = mapOf("text" to "Pay now"))

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun strictestDecisionBlocksPaymentOverMediumToolRisk() {
        val evaluation = engine.evaluate(
            request(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Pay now"),
                activeScreen = "Checkout payment screen"
            )
        )

        assertEquals(PolicyDecisionKind.BLOCK, evaluation.decision)
        assertTrue(evaluation.rules.any { it.workflowClass == PolicyWorkflowClass.PAYMENT })
    }

    @Test
    fun sensitiveTextEntryBlocks() {
        val decision = decide("type_text", args = mapOf("text" to "password: hunter2"))

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun messageSendAsksWithMessagingContext() {
        val decision = decide("tap", args = mapOf("text" to "Send"), screen = "WhatsApp conversation")

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("message"))
    }

    @Test
    fun sendWithoutMessagingContextIsNotTreatedAsMessageSend() {
        val decision = decide("tap", args = mapOf("text" to "Send"), screen = "A generic form")

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun mcpSourceRequiresApprovalNotBlock() {
        val decision = decide(
            toolName = "custom_mcp_tool",
            args = emptyMap(),
            source = ToolSource.MCP
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("MCP"))
    }

    @Test
    fun aggregatesRulesFromToolWorkflowAndSource() {
        val evaluation = engine.evaluate(
            request(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Send"),
                activeScreen = "Messages conversation",
                source = ToolSource.LOCAL_MODEL
            )
        )

        assertEquals(PolicyDecisionKind.ASK, evaluation.decision)
        assertTrue(evaluation.rules.any { it.subject == PolicySubject.TOOL })
        assertTrue(evaluation.rules.any { it.workflowClass == PolicyWorkflowClass.MESSAGE_SEND })
    }

    @Test
    fun approvalIncludesSkillContextWithoutChangingDecision() {
        val decision = engine.decide(
            request(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Send"),
                activeScreen = "Messages conversation",
                activeSkillTitle = "Messages",
                activeSkillRisk = SkillRisk.HIGH
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.skillContext.contains("high-risk"))
    }

    @Test
    fun blockedToolRiskBlocks() {
        val decision = decide("dangerous_tool", risk = ToolRisk.BLOCKED)

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun lowRiskObservationIsAllowedDespiteSensitiveScreen() {
        val decision = decide("observe_screen", risk = ToolRisk.LOW, screen = "Banking app — password field")

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun sensitiveScreenAloneDoesNotBlockBenignAction() {
        val decision = decide("tap", args = mapOf("text" to "Settings"), screen = "Banking app — pay now")

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun unknownSensitiveActionBlocksByDefault() {
        val decision = decide("tap", args = mapOf("text" to "Verify your identity"))

        assertIs<PolicyDecision.Block>(decision)
    }

    private fun tool(name: String, risk: ToolRisk = ToolRisk.MEDIUM) =
        ToolSpec(name = name, description = "test", risk = risk, arguments = emptyMap())

    private fun decide(
        toolName: String,
        risk: ToolRisk = ToolRisk.MEDIUM,
        args: Map<String, String> = emptyMap(),
        screen: String = "",
        source: ToolSource = ToolSource.LOCAL_ROUTER,
    ): PolicyDecision {
        return engine.decide(
            ToolPolicyRequest(
                tool = tool(toolName, risk),
                args = args,
                source = source,
                activeScreen = screen,
            ),
        )
    }

    private fun observeScreen(): ToolSpec {
        return ToolSpec(
            name = "observe_screen",
            description = "Observe screen",
            risk = ToolRisk.LOW,
            arguments = emptyMap()
        )
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

    private fun request(
        tool: ToolSpec,
        args: Map<String, String>,
        source: ToolSource = ToolSource.LOCAL_ROUTER,
        activeScreen: String = "",
        activeSkillTitle: String? = null,
        activeSkillRisk: SkillRisk? = null
    ): ToolPolicyRequest {
        return ToolPolicyRequest(
            tool = tool,
            args = args,
            source = source,
            activeScreen = activeScreen,
            activeSkillTitle = activeSkillTitle,
            activeSkillRisk = activeSkillRisk
        )
    }
}
