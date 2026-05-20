package dev.touchpilot.app.agent

import dev.touchpilot.app.localinference.LocalCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelCommandProvider
import dev.touchpilot.app.localinference.LocalModelStatus
import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalReasoningCoreTest {
    private val defaultContext = LocalReasoningContext(
        skill = null,
        providerMode = AgentProviderMode.LOCAL_ROUTER,
        providerConfig = ProviderConfig(baseUrl = "", apiKey = "", model = "")
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
    fun providerFactoryPicksCloudWhenModeIsCloud() {
        val ctx = defaultContext.copy(
            providerMode = AgentProviderMode.CLOUD,
            providerConfig = ProviderConfig(
                baseUrl = "https://example.com/v1/chat/completions",
                apiKey = "k",
                model = "m"
            )
        )
        val provider = buildCommandProvider(
            task = "open Settings",
            context = ctx,
            localModelRuntime = NeverCalledRuntime
        )
        assertIs<OpenAiAgentCommandProvider>(provider)
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
        val cloudContext = LocalReasoningContext(
            skill = null,
            providerMode = AgentProviderMode.CLOUD,
            providerConfig = ProviderConfig(baseUrl = "u", apiKey = "k", model = "m")
        )
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                ctxsSeen += ctx
                AgentRunResult(transcript = "", finalAnswer = null, events = emptyList())
            },
            sessionContext = { cloudContext }
        )

        core.run("Go back")

        assertEquals(1, ctxsSeen.size)
        assertEquals(AgentProviderMode.LOCAL_ROUTER, ctxsSeen[0].providerMode)
    }

    @Test
    fun localModelNeededIntentLeavesContextUntouched() {
        val ctxsSeen = mutableListOf<LocalReasoningContext>()
        val cloudContext = LocalReasoningContext(
            skill = null,
            providerMode = AgentProviderMode.CLOUD,
            providerConfig = ProviderConfig(baseUrl = "u", apiKey = "k", model = "m")
        )
        val core = DefaultLocalReasoningCore(
            invocation = { _, ctx ->
                ctxsSeen += ctx
                AgentRunResult(transcript = "", finalAnswer = null, events = emptyList())
            },
            sessionContext = { cloudContext }
        )

        core.run("Find the Wi-Fi toggle")

        assertEquals(1, ctxsSeen.size)
        assertEquals(AgentProviderMode.CLOUD, ctxsSeen[0].providerMode)
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
