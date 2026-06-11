package dev.touchpilot.app.security

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalCopyBuilderTest {
    private val engine = PolicyEngine()

    @Test
    fun messageSendUsesWorkflowSpecificHeadline() {
        val approval = approvalFor(
            request(
                tool = mediumTool("tap"),
                args = mapOf("text" to "Send"),
                activeScreen = "Messages conversation"
            )
        )

        assertEquals("Approve sending a message?", approval.headline)
        assertEquals("Message sending", approval.workflowLabel)
        assertTrue(approval.cautionNote.contains("send a message"))
        assertTrue(approval.dataAffected.contains("message"))
    }

    @Test
    fun mcpSourceUsesExternalToolCopy() {
        val approval = approvalFor(
            request(
                tool = mediumTool("search_web"),
                source = ToolSource.MCP
            )
        )

        assertEquals("Approve external MCP tool call?", approval.headline)
        assertEquals("External MCP call", approval.workflowLabel)
        assertTrue(approval.cautionNote.contains("trust boundary"))
    }

    @Test
    fun bankingAppContextUsesFinancialCopy() {
        val approval = approvalFor(
            request(
                activeScreen = "Banking app home screen",
                foregroundApp = ForegroundAppInfo(
                    packageName = "com.chase.sig.android",
                    appLabel = "Chase",
                    accessibilityConnected = true
                )
            )
        )

        assertTrue(approval.headline.contains("banking"))
        assertEquals("Banking or financial app", approval.workflowLabel)
        assertTrue(approval.dataAffected.contains("Bank balances"))
        assertTrue(approval.cautionNote.contains("Financial"))
    }

    @Test
    fun highRiskSkillAddsCautionWithoutChangingDecision() {
        val approval = approvalFor(
            request(
                activeScreen = "Messages conversation",
                activeSkillTitle = "Messages",
                activeSkillRisk = SkillRisk.HIGH
            )
        )

        assertTrue(approval.skillContext.contains("high-risk"))
        assertTrue(approval.cautionNote.contains("high risk"))
    }

    @Test
    fun highRiskToolIncludesStrongerRiskSummary() {
        val approval = approvalFor(
            request(
                tool = ToolSpec(
                    name = "open_app",
                    description = "Open app",
                    risk = ToolRisk.HIGH,
                    arguments = emptyMap()
                )
            )
        )

        assertTrue(approval.riskSummary.contains("High risk"))
        assertTrue(approval.cautionNote.contains("high risk"))
        assertTrue(approval.headline.contains("high-risk"))
    }

    private fun approvalFor(request: ToolPolicyRequest): PolicyDecision.RequireApproval {
        val decision = engine.decide(request)
        return decision as PolicyDecision.RequireApproval
    }

    private fun request(
        tool: ToolSpec = mediumTool("tap"),
        args: Map<String, String> = mapOf("text" to "Settings"),
        source: ToolSource = ToolSource.LOCAL_ROUTER,
        activeScreen: String = "",
        foregroundApp: ForegroundAppInfo? = null,
        activeSkillTitle: String? = null,
        activeSkillRisk: SkillRisk? = null
    ): ToolPolicyRequest {
        return ToolPolicyRequest(
            tool = tool,
            args = args,
            source = source,
            activeScreen = activeScreen,
            foregroundApp = foregroundApp,
            activeSkillTitle = activeSkillTitle,
            activeSkillRisk = activeSkillRisk
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
}
