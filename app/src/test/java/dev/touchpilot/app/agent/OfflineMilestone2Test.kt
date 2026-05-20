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
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Milestone 2 offline validation suite. Each test exercises one user flow end
 * to end through the local components only — no provider, no API key, no
 * network. See docs/OFFLINE_VALIDATION_M2.md for the matching checklist.
 */
class OfflineMilestone2Test {

    @Test
    fun helloFlowEmitsConversationalEventsOffline() {
        val task = "Hello"
        val reply = assertNotNull(ConversationalGate.respond(task))

        val events = listOf(
            AgentEvent.UserMessage(task),
            AgentEvent.AssistantMessage(reply.message, detail = "ConversationalGate")
        )

        assertEquals(2, events.size)
        assertEquals("user_message", events[0].toJson().getString("type"))
        assertEquals("assistant_message", events[1].toJson().getString("type"))
        assertEquals(
            reply.message,
            events[1].toJson().getJSONObject("payload").getString("text")
        )
        assertTrue(events.none { it.type == AgentEventType.TOOL_REQUESTED })
    }

    @Test
    fun helpFlowEmitsConversationalEventsOffline() {
        val task = "help"
        val reply = assertNotNull(ConversationalGate.respond(task))

        val events = listOf(
            AgentEvent.UserMessage(task),
            AgentEvent.AssistantMessage(reply.message, detail = "ConversationalGate")
        )

        assertEquals("assistant_message", events[1].toJson().getString("type"))
        assertTrue(reply.message.contains("help"), "expected help reply, got '${reply.message}'")
        assertTrue(events.none { it.type == AgentEventType.TOOL_REQUESTED })
    }

    @Test
    fun openSettingsFlowRoutesLocallyToOpenApp() {
        val task = "Open Settings"
        val provider = LocalRouterCommandProvider(task, skill = null)

        val first = AgentCommandParser.parse(provider.complete("", ""))
        val second = AgentCommandParser.parse(provider.complete("", ""))

        assertEquals("observe_screen", first.tool)
        assertEquals("open_app", second.tool)
        assertEquals("settings", second.args["target"])

        val events = buildEventSequence(task, listOf(first, second))
        val types = events.map { it.type }
        assertTrue(types.contains(AgentEventType.USER_MESSAGE))
        assertTrue(types.contains(AgentEventType.TOOL_REQUESTED))
        assertTrue(types.contains(AgentEventType.TOOL_SUCCEEDED))
    }

    @Test
    fun goBackFlowRoutesLocallyToPressBack() {
        val task = "Go back"
        val provider = LocalRouterCommandProvider(task, skill = null)

        val first = AgentCommandParser.parse(provider.complete("", ""))
        val second = AgentCommandParser.parse(provider.complete("", ""))

        assertEquals("observe_screen", first.tool)
        assertEquals("press_back", second.tool)

        val events = buildEventSequence(task, listOf(first, second))
        assertTrue(events.any { it is AgentEvent.ToolSucceeded && it.tool == "press_back" })
    }

    @Test
    fun ambiguousRequestUsesLocalModelPathWithDeterministicFallback() {
        val task = "show me something useful"
        val provider = LocalModelCommandProvider(
            runtime = StubRuntime(modelOutput = null),
            fallback = LocalRouterCommandProvider(task, null),
            task = task,
            skill = null
        )

        // First call: fallback returns observe_screen (deterministic).
        val first = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("observe_screen", first.tool)

        // Second call: task does not match any deterministic pattern, so the
        // router yields a final answer instead of an unsafe tool call. This
        // proves the local-only path stops cleanly on ambiguity.
        val second = AgentCommandParser.parse(provider.complete("", ""))
        assertNull(second.tool, "ambiguous task should not invent a tool call")
        assertNotNull(second.finalAnswer, "ambiguous task should return a final answer")
    }

    @Test
    fun localModelPathPrefersModelOutputWhenAvailable() {
        val task = "open Settings"
        val provider = LocalModelCommandProvider(
            runtime = StubRuntime(modelOutput = """{"tool":"open_app","args":{"target":"settings"}}"""),
            fallback = LocalRouterCommandProvider(task, null),
            task = task,
            skill = null
        )

        val command = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("open_app", command.tool)
        assertEquals("settings", command.args["target"])
    }

    @Test
    fun sensitiveTypeTextIsBlockedByLocalPolicy() {
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
        val json = event.toJson()
        assertEquals("policy_blocked", json.getString("type"))
        assertEquals("type_text", json.getJSONObject("payload").getString("tool"))
    }

    /**
     * Replay the event-building logic AgentRunner uses, without instantiating
     * AndroidToolExecutor (which requires android.content.Context). For each
     * tool command we emit tool_requested, tool_running, and a synthetic
     * tool_succeeded so the sequence is comparable to what AgentRunner emits
     * on a real device.
     */
    private fun buildEventSequence(task: String, commands: List<AgentCommand>): List<AgentEvent> {
        val events = mutableListOf<AgentEvent>(AgentEvent.UserMessage(task))
        for (command in commands) {
            if (command.finalAnswer != null) {
                events += AgentEvent.FinalAnswer(command.finalAnswer)
                continue
            }
            val tool = command.tool ?: continue
            AgentEvent.toolRequested(command, ToolSource.LOCAL_ROUTER)?.let { events += it }
            AgentEvent.toolRunning(command, ToolSource.LOCAL_ROUTER)?.let { events += it }
            events += AgentEvent.toolResult(tool, ToolResult(ok = true, message = tool))
        }
        return events
    }

    private class StubRuntime(private val modelOutput: String?) : LocalCommandModelRuntime {
        override fun status(): LocalModelStatus {
            return LocalModelStatus(
                available = modelOutput != null,
                runtime = "Stub",
                modelAsset = "stub.tflite",
                version = "test",
                message = "offline test stub"
            )
        }

        override fun route(task: String, context: String, skill: Skill?): String {
            return modelOutput ?: error("no model bundled")
        }
    }
}
