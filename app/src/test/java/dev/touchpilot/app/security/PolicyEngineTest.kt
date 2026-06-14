package dev.touchpilot.app.security

import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PolicyEngineTest {
    private val engine = PolicyEngine()

    private fun ctx(
        tool: String,
        risk: ToolRisk,
        args: Map<String, String> = emptyMap(),
        source: ToolSource = ToolSource.LOCAL_ROUTER,
        screen: String = "",
        skillTitle: String? = null,
        skillRisk: SkillRisk? = null,
        workflowClass: PolicyWorkflowClass? = null
    ) = PolicyContext(
        toolName = tool,
        toolRisk = risk,
        args = args,
        source = source,
        activeScreen = screen,
        activeSkillTitle = skillTitle,
        activeSkillRisk = skillRisk,
        workflowClass = workflowClass
    )

    @Test
    fun lowRiskToolIsAllowedAndTraced() {
        val outcome = engine.evaluate(ctx("observe_screen", ToolRisk.LOW, screen = "Password field visible"))
        assertIs<PolicyDecision.Allow>(outcome.decision)
        assertEquals(PolicyDecisionKind.ALLOW, outcome.evaluation.decision)
        assertEquals(PolicyWorkflowClass.GENERAL, outcome.workflowClass)
    }

    @Test
    fun mediumToolWithNoSensitiveContextRequiresApproval() {
        val outcome = engine.evaluate(ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Settings")))
        assertIs<PolicyDecision.RequireApproval>(outcome.decision)
        assertEquals(PolicyDecisionKind.ASK, outcome.evaluation.decision)
    }

    @Test
    fun blockedToolIsBlocked() {
        val outcome = engine.evaluate(ctx("dangerous_tool", ToolRisk.BLOCKED))
        val block = assertIs<PolicyDecision.Block>(outcome.decision)
        assertEquals(PolicyDecisionKind.BLOCK, outcome.evaluation.decision)
        assertTrue(block.userMessage.contains("blocked by policy"))
    }

    @Test
    fun paymentKeywordBlocksWithPreservedReason() {
        val outcome = engine.evaluate(ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Pay now")))
        val block = assertIs<PolicyDecision.Block>(outcome.decision)
        assertEquals("payments are blocked", block.reason)
        assertEquals(PolicyWorkflowClass.PAYMENT, outcome.workflowClass)
    }

    @Test
    fun sensitiveTextEntryBlocks() {
        val outcome = engine.evaluate(
            ctx("type_text", ToolRisk.MEDIUM, mapOf("text" to "482913"))
        )
        val block = assertIs<PolicyDecision.Block>(outcome.decision)
        assertEquals(PolicyWorkflowClass.SENSITIVE_TEXT_ENTRY, outcome.workflowClass)
        assertTrue(block.userMessage.contains("passwords", ignoreCase = true))
    }

    @Test
    fun messageSendRequiresApprovalNotBlock() {
        val outcome = engine.evaluate(
            ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Send"), screen = "Messages conversation")
        )
        val approval = assertIs<PolicyDecision.RequireApproval>(outcome.decision)
        assertEquals(PolicyWorkflowClass.MESSAGE_SEND, outcome.workflowClass)
        assertTrue(approval.userMessage.contains("Approval required"))
    }

    @Test
    fun mcpSourceRequiresApprovalForNonLowTool() {
        val outcome = engine.evaluate(ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Run"), source = ToolSource.MCP))
        val approval = assertIs<PolicyDecision.RequireApproval>(outcome.decision)
        assertTrue(approval.reason.contains("MCP"))
    }

    @Test
    fun mcpSourceDoesNotEscalateLowRiskTool() {
        // Low-risk MCP reads stay allowed, matching prior behavior.
        val outcome = engine.evaluate(ctx("observe_screen", ToolRisk.LOW, source = ToolSource.MCP))
        assertIs<PolicyDecision.Allow>(outcome.decision)
    }

    @Test
    fun explicitWorkflowClassEscalatesToBlock() {
        // The classifier (issue #249) flags a payment workflow the keyword path
        // would have missed (e.g. recognized only from screen/app context). The
        // engine must honor it and block.
        val outcome = engine.evaluate(
            ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Confirm"), workflowClass = PolicyWorkflowClass.PAYMENT)
        )
        val block = assertIs<PolicyDecision.Block>(outcome.decision)
        assertEquals(PolicyWorkflowClass.PAYMENT, outcome.workflowClass)
        assertTrue(block.userMessage.contains("payment"))
    }

    @Test
    fun explicitGeneralWorkflowClassNeverWeakensKeywordBlock() {
        // Even if a caller passes GENERAL, the keyword-derived payment block must
        // still win — an explicit class can only raise caution, never lower it.
        val outcome = engine.evaluate(
            ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Pay now"), workflowClass = PolicyWorkflowClass.GENERAL)
        )
        assertIs<PolicyDecision.Block>(outcome.decision)
    }

    @Test
    fun unknownSensitiveWorkflowFailsClosedToBlock() {
        val outcome = engine.evaluate(
            ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Continue"), workflowClass = PolicyWorkflowClass.UNKNOWN_SENSITIVE)
        )
        assertIs<PolicyDecision.Block>(outcome.decision)
    }

    @Test
    fun elevatedSkillAddsCautionToApprovalButDoesNotEscalateLowTool() {
        val approvalOutcome = engine.evaluate(
            ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Send"), screen = "Messages conversation",
                skillTitle = "Messages", skillRisk = SkillRisk.HIGH)
        )
        val approval = assertIs<PolicyDecision.RequireApproval>(approvalOutcome.decision)
        assertTrue(approval.skillContext.contains("high-risk"))
        assertTrue(approval.skillContext.contains("Messages"))

        val lowOutcome = engine.evaluate(
            ctx("observe_screen", ToolRisk.LOW, skillTitle = "Messages", skillRisk = SkillRisk.HIGH)
        )
        assertIs<PolicyDecision.Allow>(lowOutcome.decision)
    }

    @Test
    fun elevatedSkillDoesNotBypassBlock() {
        val outcome = engine.evaluate(
            ctx("tap", ToolRisk.MEDIUM, mapOf("text" to "Pay now"), skillTitle = "Shopping", skillRisk = SkillRisk.HIGH)
        )
        assertIs<PolicyDecision.Block>(outcome.decision)
    }
}
