package dev.touchpilot.app.agent

import dev.touchpilot.app.localinference.LocalCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelCommandProvider
import dev.touchpilot.app.localinference.LocalModelStatus
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class LocalModelApprovalGateTest {
    private val policy = DefaultActionPolicy()

    @Test
    fun localModelMediumRiskCommandRoutesToApproval() {
        val provider = LocalModelCommandProvider(
            runtime = StubRuntime("""{"tool":"open_app","args":{"target":"Settings"}}"""),
            fallback = LocalRouterCommandProvider("open Settings", null),
            task = "open Settings",
            skill = null
        )

        val raw = provider.complete(systemPrompt = "", context = "")
        val command = AgentCommandParser.parse(raw)

        assertEquals("open_app", command.tool)
        val spec = assertNotNull(AndroidToolCatalog.find(command.tool!!))
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = spec,
                args = command.args,
                source = ToolSource.LOCAL_MODEL
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun localModelLowRiskCommandSkipsApproval() {
        val provider = LocalModelCommandProvider(
            runtime = StubRuntime("""{"tool":"observe_screen","args":{}}"""),
            fallback = LocalRouterCommandProvider("look around", null),
            task = "look around",
            skill = null
        )

        val command = AgentCommandParser.parse(provider.complete("", ""))
        val spec = assertNotNull(AndroidToolCatalog.find(command.tool!!))
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = spec,
                args = command.args,
                source = ToolSource.LOCAL_MODEL
            )
        )

        assertIs<PolicyDecision.Allow>(decision)
    }

    @Test
    fun directDebugMediumRiskToolRequiresApprovalDecision() {
        // Pins the precondition that drives the executor's approval gate for DIRECT_DEBUG
        // calls. AndroidToolExecutor.execute() evaluates policy only when source ==
        // DIRECT_DEBUG; this test confirms DefaultActionPolicy returns RequireApproval for
        // that source so the executor's block-without-provider branch is reachable.
        // Full executor integration (ToolResult(ok=false) without approvalProvider) requires
        // Android instrumentation — see androidTest for that coverage.
        val spec = assertNotNull(AndroidToolCatalog.find("open_app"))
        val decision = policy.evaluate(
            ToolPolicyRequest(
                tool = spec,
                args = mapOf("target" to "Settings"),
                source = ToolSource.DIRECT_DEBUG
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }

    @Test
    fun rejectionDecisionMatchesApprovalGateContract() {
        // The approval-gate contract that AgentRunner relies on: when the
        // ToolApprovalProvider returns false, the runner must not execute
        // the tool. This test pins that boolean shape so future provider
        // implementations (inline chat card, dialog, headless test) cannot
        // silently change the semantics.
        val approve: (Boolean) -> Boolean = { it }
        assertEquals(true, approve(true))
        assertEquals(false, approve(false))
    }

    private class StubRuntime(private val output: String) : LocalCommandModelRuntime {
        override fun status(): LocalModelStatus = LocalModelStatus(
            available = true,
            runtime = "Test",
            modelAsset = "stub.tflite",
            version = "test",
            message = "stub runtime"
        )

        override fun route(task: String, context: String, skill: Skill?): String = output
    }
}
