package dev.touchpilot.app.localinference

import dev.touchpilot.app.agent.AgentCommandParser
import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun returnsFinalAnswerOnMalformedModelOutput() {
        val provider = providerFor("not json", task = "go back")

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertNull(command.tool)
        assertEquals("Local model could not route this task safely.", command.finalAnswer)
    }

    @Test
    fun returnsFinalAnswerOnUnknownToolOutput() {
        val provider = providerFor("""{"tool":"unknown_tool","args":{}}""", task = "go home")

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertNull(command.tool)
        assertEquals("Local model could not route this task safely.", command.finalAnswer)
    }

    @Test
    fun returnsFinalAnswerOnInvalidArgumentOutput() {
        val provider = providerFor(
            """{"tool":"scroll","args":{"direction":"sideways"}}""",
            task = "scroll"
        )

        val command = AgentCommandParser.parse(provider.complete("", "screen"))

        assertNull(command.tool)
        assertEquals("Local model could not route this task safely.", command.finalAnswer)
    }

    @Test
    fun returnsFinalAnswerOnSkillDisallowedOutput() {
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

        assertNull(command.tool)
        assertEquals("Local model could not route this task safely.", command.finalAnswer)
    }

    private fun providerFor(
        output: String,
        task: String = "open Settings",
        skill: Skill? = null
    ): LocalModelCommandProvider {
        return LocalModelCommandProvider(
            runtime = StubRuntime(output),
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
