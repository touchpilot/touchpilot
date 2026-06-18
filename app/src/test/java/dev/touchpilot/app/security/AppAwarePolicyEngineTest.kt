package dev.touchpilot.app.security

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppAwarePolicyEngineTest {
    private val engine = PolicyEngine()

    @Test
    fun bankingScreenRequiresApprovalWithAppReason() {
        val decision = engine.decide(
            request(
                activeScreen = "Banking app home screen"
            )
        )

        val approval = assertIs<PolicyDecision.RequireApproval>(decision)
        assertTrue(approval.reason.contains("banking"))
        assertTrue(approval.dataAffected.contains("Bank balances"))
    }

    @Test
    fun appAwareRulesDoNotLowerBlockedWorkflowDecision() {
        val decision = engine.decide(
            request(
                args = mapOf("text" to "Pay now"),
                activeScreen = "Checkout payment screen"
            )
        )

        assertIs<PolicyDecision.Block>(decision)
    }

    @Test
    fun lowRiskObservationIgnoresAppAwareRules() {
        val decision = engine.decide(
            request(
                tool = ToolSpec(
                    name = "observe_screen",
                    description = "Observe screen",
                    risk = ToolRisk.LOW,
                    arguments = emptyMap()
                ),
                args = emptyMap(),
                activeScreen = "Banking app home screen",
                foregroundApp = ForegroundAppInfo(
                    packageName = "com.chase.sig.android",
                    accessibilityConnected = true
                )
            )
        )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun evaluationIncludesAppSubjectRule() {
        val evaluation = engine.evaluate(
            request(activeScreen = "Permission settings for TouchPilot")
        )

        assertEquals(PolicyDecisionKind.ASK, evaluation.decision)
        assertTrue(evaluation.rules.any { it.subject == PolicySubject.APP })
    }

    private fun request(
        tool: ToolSpec = mediumTool("tap"),
        args: Map<String, String> = mapOf("text" to "Settings"),
        activeScreen: String = "",
        foregroundApp: ForegroundAppInfo? = null
    ): ToolPolicyRequest {
        return ToolPolicyRequest(
            tool = tool,
            args = args,
            source = ToolSource.LOCAL_ROUTER,
            activeScreen = activeScreen,
            foregroundApp = foregroundApp
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
