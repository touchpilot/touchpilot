package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyEngineTest {
    private val engine = PolicyEngine()

    private fun tool(name: String, risk: ToolRisk = ToolRisk.MEDIUM) =
        ToolSpec(name = name, description = "test", risk = risk, arguments = emptyMap())

    private fun decide(
        toolName: String,
        risk: ToolRisk = ToolRisk.MEDIUM,
        args: Map<String, String> = emptyMap(),
        screen: String = "",
        source: ToolSource = ToolSource.LOCAL_ROUTER,
    ): PolicyEngine.Decision {
        return engine.decide(
            ToolPolicyRequest(
                tool = tool(toolName, risk),
                args = args,
                source = source,
                activeScreen = screen,
            ),
        )
    }

    @Test
    fun lowRiskToolIsAllowed() {
        val decision = decide("observe_screen", risk = ToolRisk.LOW)

        assertEquals(PolicyDecisionKind.ALLOW, decision.kind)
        assertEquals(PolicySubject.TOOL, decision.primary.subject)
    }

    @Test
    fun mediumToolWithoutSensitiveWorkflowAsks() {
        val decision = decide("tap", args = mapOf("text" to "Settings"))

        assertEquals(PolicyDecisionKind.ASK, decision.kind)
        assertEquals(PolicySubject.TOOL, decision.primary.subject)
        assertEquals(PolicyWorkflowClass.GENERAL, decision.primary.workflowClass)
    }

    @Test
    fun sensitiveWorkflowBlocksEvenWhenToolRiskWouldOnlyAsk() {
        val decision = decide("tap", args = mapOf("text" to "Pay now"))

        assertEquals(PolicyDecisionKind.BLOCK, decision.kind)
        assertEquals(PolicyWorkflowClass.PAYMENT, decision.primary.workflowClass)
    }

    @Test
    fun sensitiveTextEntryBlocks() {
        val decision = decide("type_text", args = mapOf("text" to "password: hunter2"))

        assertEquals(PolicyDecisionKind.BLOCK, decision.kind)
        assertEquals(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY, decision.primary.workflowClass)
    }

    @Test
    fun messageSendAsksWithMessagingContext() {
        val decision = decide("tap", args = mapOf("text" to "Send"), screen = "WhatsApp conversation")

        assertEquals(PolicyDecisionKind.ASK, decision.kind)
        assertEquals(PolicyWorkflowClass.MESSAGE_SEND, decision.primary.workflowClass)
    }

    @Test
    fun sendWithoutMessagingContextIsNotTreatedAsMessageSend() {
        val decision = decide("tap", args = mapOf("text" to "Send"), screen = "A generic form")

        assertEquals(PolicyDecisionKind.ASK, decision.kind)
        // Falls back to the tool-risk approval, not the message-send rule.
        assertEquals(PolicySubject.TOOL, decision.primary.subject)
    }

    @Test
    fun mcpSourceAsks() {
        val decision = decide("tap", args = mapOf("text" to "Run"), source = ToolSource.MCP)

        assertEquals(PolicyDecisionKind.ASK, decision.kind)
        assertEquals(PolicySubject.SOURCE, decision.primary.subject)
    }

    @Test
    fun blockedToolRiskBlocks() {
        val decision = decide("dangerous_tool", risk = ToolRisk.BLOCKED)

        assertEquals(PolicyDecisionKind.BLOCK, decision.kind)
        assertEquals(PolicySubject.TOOL, decision.primary.subject)
        assertEquals(PolicyWorkflowClass.GENERAL, decision.primary.workflowClass)
    }

    @Test
    fun lowRiskObservationIsAllowedDespiteSensitiveScreen() {
        // Preserves current behavior: passive low-risk tools are never escalated.
        val decision = decide("observe_screen", risk = ToolRisk.LOW, screen = "Banking app — password field")

        assertEquals(PolicyDecisionKind.ALLOW, decision.kind)
    }

    @Test
    fun sensitiveScreenAloneDoesNotBlockBenignAction() {
        // Preserves current behavior: screen context (not the action) does not block.
        val decision = decide("tap", args = mapOf("text" to "Settings"), screen = "Banking app — pay now")

        assertEquals(PolicyDecisionKind.ASK, decision.kind)
        assertEquals(PolicySubject.TOOL, decision.primary.subject)
    }

    @Test
    fun unknownSensitiveActionBlocksByDefault() {
        val decision = decide("tap", args = mapOf("text" to "Verify your identity"))

        assertEquals(PolicyDecisionKind.BLOCK, decision.kind)
        assertEquals(PolicyWorkflowClass.UNKNOWN_SENSITIVE, decision.primary.workflowClass)
    }
}
