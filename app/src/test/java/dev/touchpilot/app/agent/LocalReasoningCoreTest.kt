package dev.touchpilot.app.agent

import dev.touchpilot.app.localinference.LocalCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelCommandProvider
import dev.touchpilot.app.localinference.LocalModelStatus
import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalReasoningCoreTest {
    private val defaultContext = LocalReasoningContext(
        skill = null,
        providerMode = AgentProviderMode.LOCAL_ROUTER
    )

    @Test
    fun conversationalInputShortCircuitsBeforeInvocation() {
        val invocations = mutableListOf<String>()
        val collected = mutableListOf<AgentEvent>()
        val core = DefaultLocalReasoningCore(
            invocation = { task, _ ->
                invocations += task
                error("invocation must not run for conversational input")
            },
            sessionContext = { defaultContext }
        )

        val result = core.run("Hello") { collected += it }

        assertEquals("Hello, I am TouchPilot, how can I help you?", result.finalAnswer)
        assertTrue(invocations.isEmpty())
        assertEquals(2, collected.size)
        assertIs<AgentEvent.UserMessage>(collected[0])
        assertIs<AgentEvent.FinalAnswer>(collected[1])
    }

    @Test
    fun nonConversationalInputDelegatesAndForwardsEvents() {
        val runnerEvents = listOf<AgentEvent>(
            AgentEvent.UserMessage("open Settings"),
            AgentEvent.AssistantMessage("ack", "router")
        )
        val sentinelResult = AgentRunResult(
            transcript = "Step 1\nFinal: stubbed",
            finalAnswer = "stubbed",
            events = runnerEvents
        )
        val recordedContexts = mutableListOf<LocalReasoningContext>()
        val collected = mutableListOf<AgentEvent>()
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                recordedContexts += ctx
                sentinelResult
            },
            sessionContext = { defaultContext }
        )

        val result = core.run("open Settings") { collected += it }

        assertEquals("stubbed", result.finalAnswer)
        assertEquals(runnerEvents, result.events)
        assertEquals(runnerEvents, collected)
        assertEquals(1, recordedContexts.size)
        assertEquals(AgentProviderMode.LOCAL_ROUTER, recordedContexts[0].providerMode)
    }

    @Test
    fun nullListenerIsTolerated() {
        val core = DefaultLocalReasoningCore(
            invocation = { _, _ -> error("should not be called") },
            sessionContext = { defaultContext }
        )
        val result = core.run("help")
        assertEquals(
            "I can help you control Android apps, open settings, tap visible text, scroll, " +
                "go back or home, and use approved skills. What would you like to do?",
            result.finalAnswer
        )
    }

    @Test
    fun customIntentsCanOverrideTheDefaultGate() {
        val core = DefaultLocalReasoningCore(
            invocation = { _, _ -> error("should not be called") },
            sessionContext = { defaultContext },
            intents = { input ->
                if (input == "ping") ConversationalResponse("pong") else null
            }
        )
        val result = core.run("ping")
        assertEquals("pong", result.finalAnswer)
    }

    @Test
    fun mixedHelpPhraseFallsThroughToInvocation() {
        var sawInvocation = false
        val core = DefaultLocalReasoningCore(
            invocation = { task, _ ->
                sawInvocation = true
                AgentRunResult(
                    transcript = "stub",
                    finalAnswer = null,
                    events = listOf(AgentEvent.UserMessage(task))
                )
            },
            sessionContext = { defaultContext }
        )

        val result = core.run("help me open Settings")
        assertTrue(sawInvocation)
        assertNull(result.finalAnswer)
        assertFalse(result.events.any { it is AgentEvent.FinalAnswer })
    }

    @Test
    fun providerFactoryPicksLocalRouterByDefault() {
        val provider = buildCommandProvider(
            task = "open Settings",
            context = defaultContext,
            localModelRuntime = NeverCalledRuntime
        )
        assertIs<LocalRouterCommandProvider>(provider)
    }

    @Test
    fun providerFactoryPicksLocalModelWhenModeIsLocalModel() {
        val ctx = defaultContext.copy(providerMode = AgentProviderMode.LOCAL_MODEL)
        val provider = buildCommandProvider(
            task = "open Settings",
            context = ctx,
            localModelRuntime = StubRuntime("""{"tool":"open_app","args":{"target":"Settings"}}""")
        )
        assertIs<LocalModelCommandProvider>(provider)
    }

    @Test
    fun localRouterRespectsSkillAllowlist() {
        val restrictedSkill = Skill(
            id = "observe-only",
            title = "Observe Only",
            markdown = "",
            allowedTools = setOf("observe_screen")
        )
        val ctx = defaultContext.copy(skill = restrictedSkill)
        val provider = buildCommandProvider(
            task = "go home",
            context = ctx,
            localModelRuntime = NeverCalledRuntime
        )

        assertIs<LocalRouterCommandProvider>(provider)
        val first = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("observe_screen", first.tool)
        val second = AgentCommandParser.parse(provider.complete("", ""))
        assertNull(second.tool)
        assertEquals(
            "Local router completed its safe routing pass. Try a more specific request, a skill, or local model mode for ambiguous tasks.",
            second.finalAnswer
        )
    }

    @Test
    fun unsafeIntentShortCircuitsBeforeInvocation() {
        var invoked = false
        val collected = mutableListOf<AgentEvent>()
        val core = DefaultLocalReasoningCore(
            invocation = { _, _ ->
                invoked = true
                error("unsafe intent must not invoke the runner")
            },
            sessionContext = { defaultContext }
        )

        val result = core.run("Send this payment") { collected += it }

        assertFalse(invoked)
        assertEquals(3, collected.size)
        assertIs<AgentEvent.UserMessage>(collected[0])
        assertIs<AgentEvent.PolicyBlocked>(collected[1])
        assertIs<AgentEvent.FinalAnswer>(collected[2])
        assertEquals("Blocked by intent gate: payments are blocked", result.transcript)
    }

    @Test
    fun clarificationIntentShortCircuitsBeforeInvocation() {
        var invoked = false
        val collected = mutableListOf<AgentEvent>()
        val core = DefaultLocalReasoningCore(
            invocation = { _, _ ->
                invoked = true
                error("clarification intent must not invoke the runner")
            },
            sessionContext = { defaultContext }
        )

        val result = core.run("Do the thing I usually do here") { collected += it }

        assertFalse(invoked)
        assertEquals(2, collected.size)
        assertIs<AgentEvent.UserMessage>(collected[0])
        val ask = assertIs<AgentEvent.AssistantMessage>(collected[1])
        assertEquals(
            "Can you describe what you would like me to do more specifically?",
            ask.text
        )
        assertEquals(
            "Can you describe what you would like me to do more specifically?",
            result.finalAnswer
        )
    }

    @Test
    fun exactCommandIntentForcesLocalRouterModeOnInvocation() {
        val ctxsSeen = mutableListOf<LocalReasoningContext>()
        val localModelContext = LocalReasoningContext(
            skill = null,
            providerMode = AgentProviderMode.LOCAL_MODEL
        )
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                ctxsSeen += ctx
                AgentRunResult(transcript = "", finalAnswer = null, events = emptyList())
            },
            sessionContext = { localModelContext }
        )

        core.run("Go back")

        assertEquals(1, ctxsSeen.size)
        assertEquals(AgentProviderMode.LOCAL_ROUTER, ctxsSeen[0].providerMode)
        // The gate's decided tool/args must reach the invocation so a second
        // exact-command parser cannot pick a different action.
        assertEquals("press_back", ctxsSeen[0].exactCommand?.tool)
        assertEquals(emptyMap(), ctxsSeen[0].exactCommand?.args)
    }

    @Test
    fun scrollBackIntentExecutesWhateverTheGateDecidedNotPressBack() {
        // Regression for the reviewer's example on PR #48: "scroll back"
        // makes IntentGate emit scroll/forward, but LocalRouterCommandProvider's
        // independent parser would press_back. The gate's decision must win.
        val ctxsSeen = mutableListOf<LocalReasoningContext>()
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                ctxsSeen += ctx
                AgentRunResult(transcript = "", finalAnswer = null, events = emptyList())
            },
            sessionContext = { defaultContext }
        )

        core.run("scroll back")

        val gateCommand = ctxsSeen.single().exactCommand
        assertEquals("scroll", gateCommand?.tool)
        assertEquals("forward", gateCommand?.args?.get("direction"))
    }

    @Test
    fun exactCommandContextProducesFixedCommandProvider() {
        val ctx = defaultContext.copy(
            exactCommand = AgentCommand(
                tool = "press_back",
                args = emptyMap(),
                finalAnswer = null
            )
        )
        val provider = buildCommandProvider(
            task = "go back",
            context = ctx,
            localModelRuntime = NeverCalledRuntime
        )
        assertIs<FixedCommandProvider>(provider)

        val parsed = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("press_back", parsed.tool)
    }

    @Test
    fun localModelNeededIntentLeavesContextUntouched() {
        val ctxsSeen = mutableListOf<LocalReasoningContext>()
        val localModelContext = LocalReasoningContext(
            skill = null,
            providerMode = AgentProviderMode.LOCAL_MODEL
        )
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                ctxsSeen += ctx
                AgentRunResult(transcript = "", finalAnswer = null, events = emptyList())
            },
            sessionContext = { localModelContext }
        )

        core.run("Find the Wi-Fi toggle")

        assertEquals(1, ctxsSeen.size)
        assertEquals(AgentProviderMode.LOCAL_MODEL, ctxsSeen[0].providerMode)
    }

    @Test
    fun knownSkillIntentOverridesSessionSkillWithMatchedSkill() {
        val baseSkill = Skill(
            id = "messages",
            title = "Messages",
            markdown = "",
            allowedTools = setOf("open_app", "type_text")
        )
        val matchedSkill = Skill(
            id = "settings",
            title = "Settings",
            markdown = "",
            allowedTools = setOf("open_app", "tap")
        )
        val ctxsSeen = mutableListOf<LocalReasoningContext>()
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                ctxsSeen += ctx
                AgentRunResult(transcript = "", finalAnswer = "ok", events = emptyList())
            },
            sessionContext = {
                defaultContext.copy(
                    skill = baseSkill,
                    providerMode = AgentProviderMode.LOCAL_MODEL
                )
            },
            availableSkills = { listOf(matchedSkill) }
        )

        core.run("help me with settings")

        assertEquals(1, ctxsSeen.size)
        val effectiveSkill = assertNotNull(ctxsSeen.single().skill)
        assertEquals("settings", effectiveSkill.id)
        assertEquals(setOf("open_app", "tap"), effectiveSkill.allowedTools)
        assertEquals(AgentProviderMode.LOCAL_MODEL, ctxsSeen.single().providerMode)
    }

    @Test
    fun unresolvedKnownSkillAsksForClarification() {
        var invoked = false
        val collected = mutableListOf<AgentEvent>()
        val core = DefaultLocalReasoningCore(
            invocation = { _, _ ->
                invoked = true
                error("missing skill should not invoke runner")
            },
            sessionContext = { defaultContext },
            intentClassifier = IntentClassifier { _, _ ->
                IntentDecision.KnownSkill(
                    skillId = "missing-skill",
                    skillTitle = "Missing Skill",
                    reason = "test mismatch"
                )
            }
        )

        val result = core.run("use missing skill") { collected += it }

        assertFalse(invoked)
        assertEquals(2, collected.size)
        assertIs<AgentEvent.UserMessage>(collected[0])
        val assistant = assertIs<AgentEvent.AssistantMessage>(collected[1])
        assertEquals(
            "I couldn't load that skill. Please pick another skill or rephrase the request.",
            assistant.text
        )
        assertEquals(assistant.text, result.finalAnswer)
    }

    private class StubRuntime(private val output: String) : LocalCommandModelRuntime {
        override fun status(): LocalModelStatus = LocalModelStatus(
            available = true,
            runtime = "Stub",
            modelAsset = "stub.tflite",
            version = "test",
            message = "stub"
        )

        override fun route(task: String, context: String, skill: Skill?): String = output
    }

    private object NeverCalledRuntime : LocalCommandModelRuntime {
        override fun status(): LocalModelStatus = error("runtime must not be queried")
        override fun route(task: String, context: String, skill: Skill?): String =
            error("runtime must not be invoked")
    }
}
