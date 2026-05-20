package dev.touchpilot.app.localinference

import dev.touchpilot.app.agent.AgentCommandParser
import dev.touchpilot.app.agent.LocalRouterCommandProvider
import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalModelCommandProviderContractTest {
    @Test
    fun returnsValidModelToolCall() {
        val provider = providerFor("""{"tool":"open_app","args":{"target":"Settings"}}""")

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun returnsValidModelFinalAnswer() {
        val provider = providerFor("""{"final":"I cannot do that safely."}""")

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertEquals("I cannot do that safely.", command.finalAnswer)
    }

    @Test
    fun fallsBackOnMalformedModelOutput() {
        val provider = providerFor("not json", task = "go back")

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertEquals("observe_screen", command.tool)
    }

    @Test
    fun fallsBackOnUnknownToolOutput() {
        val provider = providerFor("""{"tool":"unknown_tool","args":{}}""", task = "go home")

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertEquals("observe_screen", command.tool)
    }

    @Test
    fun fallsBackOnInvalidArgumentOutput() {
        val provider = providerFor(
            """{"tool":"scroll","args":{"direction":"sideways"}}""",
            task = "scroll"
        )

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertEquals("observe_screen", command.tool)
    }

    @Test
    fun fallsBackOnSkillDisallowedOutput() {
        val skill = Skill(
            id = "observe-only",
            title = "Observe Only",
            markdown = "",
            allowedTools = setOf("observe_screen")
        )
        val provider = providerFor(
            output = """{"tool":"open_app","args":{"target":"Settings"}}""",
            task = "open Settings",
            skill = skill
        )

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertEquals("observe_screen", command.tool)
    }

    private fun providerFor(
        output: String,
        task: String = "open Settings",
        skill: Skill? = null
    ): LocalModelCommandProvider {
        return LocalModelCommandProvider(
            runtime = StubRuntime(output),
            fallback = LocalRouterCommandProvider(task, skill),
            task = task,
            skill = skill
        )
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
