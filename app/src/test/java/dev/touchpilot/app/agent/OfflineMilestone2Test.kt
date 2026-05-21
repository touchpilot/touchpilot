package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Milestone 2 offline validation suite.
 *
 * Each test drives the real [DefaultLocalReasoningCore] (the runtime entry
 * point the UI uses) and asserts the agent-event contract it actually emits.
 *
 * For flows that hit [AgentRunner] (Open Settings, Go back, local-model-needed
 * fallback), we inject a fake [AgentRunInvocation] that:
 *  1. captures the [LocalReasoningContext] the core constructed, so we can
 *     assert the gate's classification was wired through correctly, and
 *  2. returns an [AgentRunResult] built with the same `AgentEvent.toolRequested`
 *     / `toolRunning` / `toolResult` / `approvalRequired` factories the
 *     production runner uses (see [AgentRunner.run] lines 26, 56, 94, 128, 137,
 *     and 47 for the matching emission points).
 *
 * See docs/OFFLINE_VALIDATION_M2.md for the matching live checklist.
 */
class OfflineMilestone2Test {

    private val baseContext = LocalReasoningContext(
        skill = null,
        providerMode = AgentProviderMode.LOCAL_ROUTER,
        providerConfig = ProviderConfig(baseUrl = "", apiKey = "", model = ""),
        exactCommand = null
    )

    private fun core(
        sessionContext: LocalReasoningContext = baseContext,
        skills: List<Skill> = emptyList(),
        invocation: AgentRunInvocation = AgentRunInvocation { _, _ ->
            error("invocation should not run for this flow")
        }
    ): DefaultLocalReasoningCore = DefaultLocalReasoningCore(
        invocation = invocation,
        sessionContext = { sessionContext },
        intents = ConversationalGate,
        intentClassifier = IntentGate(),
        availableSkills = { skills }
    )

    // ----- Conversational flows: no invocation, core emits UserMessage + FinalAnswer -----

    @Test
    fun helloFlowEmitsUserMessageAndFinalAnswerThroughCore() {
        val observed = mutableListOf<AgentEvent>()
        val result = core().run("Hello") { observed += it }

        assertEquals(2, result.events.size, "Hello path should be exactly two events")
        val user = assertIs<AgentEvent.UserMessage>(result.events[0])
        val final = assertIs<AgentEvent.FinalAnswer>(result.events[1])
        assertEquals("Hello", user.text)
        assertEquals("Hello, I am TouchPilot, how can I help you?", final.text)
        assertEquals(result.events, observed, "listener should receive every emitted event")

        // No tool events appear on the conversational path.
        assertTrue(result.events.none { it is AgentEvent.ToolRequested })
        assertTrue(result.events.none { it is AgentEvent.ToolRunning })
    }

    @Test
    fun helpFlowEmitsUserMessageAndFinalAnswerThroughCore() {
        val result = core().run("help")

        assertEquals(2, result.events.size)
        assertIs<AgentEvent.UserMessage>(result.events[0])
        val final = assertIs<AgentEvent.FinalAnswer>(result.events[1])
        assertTrue(
            final.text.startsWith("I can help you control Android apps"),
            "expected help reply, got '${final.text}'"
        )
    }

    // ----- ExactCommand flows: gate routes through invocation with exactCommand set -----

    @Test
    fun openSettingsRoutesThroughIntentGateAsExactCommand() {
        val capture = CapturingInvocation { _, ctx ->
            val cmd = assertNotNull(ctx.exactCommand)
            scriptedRunnerEvents(
                task = "Open Settings",
                command = cmd,
                source = ctx.providerMode.toToolSource(),
                approvalDecision = mediumRiskApproval(cmd.tool!!),
                resultMessage = "openApp"
            )
        }
        val result = core(invocation = capture).run("Open Settings")

        val ctx = assertNotNull(capture.context)
        val cmd = assertNotNull(ctx.exactCommand)
        assertEquals("open_app", cmd.tool)
        assertEquals("settings", cmd.args["target"])
        assertEquals(
            AgentProviderMode.LOCAL_ROUTER, ctx.providerMode,
            "ExactCommand path must force LOCAL_ROUTER per LocalReasoningCore.kt:90"
        )

        // Real runtime contract: UserMessage -> ToolRequested -> ApprovalRequired ->
        // ToolRunning -> ToolSucceeded -> FinalAnswer
        val types = result.events.map { it.type }
        assertEquals(
            listOf(
                AgentEventType.USER_MESSAGE,
                AgentEventType.TOOL_REQUESTED,
                AgentEventType.APPROVAL_REQUIRED,
                AgentEventType.TOOL_RUNNING,
                AgentEventType.TOOL_SUCCEEDED,
                AgentEventType.FINAL_ANSWER
            ),
            types
        )
        val approval = result.events.filterIsInstance<AgentEvent.ApprovalRequired>().single()
        assertEquals("open_app", approval.tool)
        assertEquals("settings", approval.args["target"])
        assertEquals("approval_required", approval.toJson().getString("type"))
    }

    @Test
    fun goBackRoutesThroughIntentGateAsExactCommand() {
        val capture = CapturingInvocation { _, ctx ->
            val cmd = assertNotNull(ctx.exactCommand)
            scriptedRunnerEvents(
                task = "Go back",
                command = cmd,
                source = ctx.providerMode.toToolSource(),
                approvalDecision = mediumRiskApproval(cmd.tool!!),
                resultMessage = "pressBack"
            )
        }
        val result = core(invocation = capture).run("Go back")

        val ctx = assertNotNull(capture.context)
        val cmd = assertNotNull(ctx.exactCommand)
        assertEquals("press_back", cmd.tool)
        assertTrue(cmd.args.isEmpty())

        // Same shape as Open Settings: the runtime emits an approval for every
        // MEDIUM-risk tool the deterministic router selects.
        val approval = result.events.filterIsInstance<AgentEvent.ApprovalRequired>().single()
        assertEquals("press_back", approval.tool)
        val succeeded = result.events.filterIsInstance<AgentEvent.ToolSucceeded>().single()
        assertEquals("press_back", succeeded.tool)
    }

    // ----- IntentGate refusing unsafe requests before the runner runs -----

    @Test
    fun passwordTaskBlockedByIntentGateBeforeReachingRunner() {
        val result = core(
            invocation = AgentRunInvocation { _, _ ->
                error("unsafe requests must not reach the runner")
            }
        ).run("change my password please")

        val types = result.events.map { it.type }
        assertEquals(
            listOf(
                AgentEventType.USER_MESSAGE,
                AgentEventType.POLICY_BLOCKED,
                AgentEventType.FINAL_ANSWER
            ),
            types,
            "IntentGate.UnsafeRequest must surface as UserMessage + PolicyBlocked + FinalAnswer"
        )
        val blocked = result.events[1] as AgentEvent.PolicyBlocked
        assertContains(blocked.reason, "password")
        assertNull(blocked.tool, "intent-gate block has no tool yet")
        // Wire shape is what the UI consumes.
        assertEquals("policy_blocked", blocked.toJson().getString("type"))
    }

    // ----- Ambiguous reference -> ClarificationNeeded (no invocation) -----

    @Test
    fun ambiguousReferencePromptsForClarification() {
        val result = core(
            invocation = AgentRunInvocation { _, _ ->
                error("ambiguous references must not reach the runner")
            }
        ).run("do the thing")

        val types = result.events.map { it.type }
        assertEquals(
            listOf(AgentEventType.USER_MESSAGE, AgentEventType.ASSISTANT_MESSAGE),
            types
        )
        val assistant = result.events[1] as AgentEvent.AssistantMessage
        assertContains(assistant.text, "specifically")
        assertContains(assistant.detail, "ambiguous")
    }

    // ----- LocalModelNeeded: gate falls through, core preserves session mode -----

    @Test
    fun unmatchedRequestRoutesToLocalModelNeededAndPreservesSessionMode() {
        val capture = CapturingInvocation { task, _ ->
            // The runner would normally produce events here; we only need to
            // confirm the core hands off to invocation with the right context.
            AgentRunResult(
                transcript = "local-model path",
                finalAnswer = null,
                events = listOf(AgentEvent.UserMessage(task))
            )
        }
        val localModelContext = baseContext.copy(providerMode = AgentProviderMode.LOCAL_MODEL)
        core(sessionContext = localModelContext, invocation = capture)
            .run("show me something useful")

        val ctx = assertNotNull(capture.context)
        assertEquals(AgentProviderMode.LOCAL_MODEL, ctx.providerMode)
        assertNull(ctx.exactCommand, "LocalModelNeeded must not set exactCommand")
    }

    // ----- Policy layer (isolated, complements the IntentGate test above) -----

    @Test
    fun sensitiveTypeTextIsBlockedByDefaultActionPolicy() {
        // The IntentGate test above blocks "password" requests before they
        // reach the runner. This complementary test confirms the second
        // defence-in-depth layer: if a tool call ever reaches the policy with
        // a sensitive payload, it is still blocked.
        val spec = assertNotNull(AndroidToolCatalog.find("type_text"))
        val decision = DefaultActionPolicy().evaluate(
            ToolPolicyRequest(
                tool = spec,
                args = mapOf("text" to "my password is hunter2"),
                source = ToolSource.LOCAL_ROUTER
            )
        )
        assertTrue(decision is PolicyDecision.Block, "expected Block, got $decision")
        val event = assertNotNull(AgentEvent.policyBlocked("type_text", decision))
        assertEquals("policy_blocked", event.toJson().getString("type"))
        assertEquals("type_text", event.toJson().getJSONObject("payload").getString("tool"))
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Captures the context the core passes to invocation so tests can assert on it. */
    private class CapturingInvocation(
        private val producer: (String, LocalReasoningContext) -> AgentRunResult
    ) : AgentRunInvocation {
        var context: LocalReasoningContext? = null
            private set

        override fun invoke(task: String, context: LocalReasoningContext): AgentRunResult {
            this.context = context
            return producer(task, context)
        }
    }

    /**
     * Builds the event sequence AgentRunner would emit for a single-step run of
     * a MEDIUM-risk tool that requires approval and succeeds. Uses the same
     * AgentEvent factory functions AgentRunner uses internally (see
     * AgentRunner.kt:26, :56, :94, :128, :137, :47), so the wire shape is
     * identical to production.
     */
    private fun scriptedRunnerEvents(
        task: String,
        command: AgentCommand,
        source: ToolSource,
        approvalDecision: PolicyDecision.RequireApproval,
        resultMessage: String
    ): AgentRunResult {
        val tool = assertNotNull(command.tool)
        val spec = assertNotNull(AndroidToolCatalog.find(tool))
        val events = listOf(
            AgentEvent.UserMessage(task),
            assertNotNull(AgentEvent.toolRequested(command, source)),
            AgentEvent.approvalRequired(ToolApprovalRequest(spec, command.args, approvalDecision)),
            assertNotNull(AgentEvent.toolRunning(command, source)),
            AgentEvent.toolResult(tool, ToolResult(ok = true, message = resultMessage)),
            AgentEvent.FinalAnswer("Done.")
        )
        return AgentRunResult(transcript = "scripted", finalAnswer = "Done.", events = events)
    }

    private fun mediumRiskApproval(toolName: String) = PolicyDecision.RequireApproval(
        reason = "medium risk Android action",
        userMessage = "Approval required for $toolName: medium risk Android action.",
        dataAffected = "The current Android app or screen may be changed.",
        ifApproved = "TouchPilot will run $toolName with the shown arguments."
    )

    private fun AgentProviderMode.toToolSource(): ToolSource = when (this) {
        AgentProviderMode.CLOUD -> ToolSource.CLOUD_FALLBACK
        AgentProviderMode.LOCAL_MODEL -> ToolSource.LOCAL_MODEL
        AgentProviderMode.LOCAL_ROUTER -> ToolSource.LOCAL_ROUTER
    }
}
