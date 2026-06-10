package dev.touchpilot.app.localinference

import dev.touchpilot.app.agent.AgentCommandParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalModelCommandProviderTest {
    @Test
    fun usesModelCommandOnEveryStep() {
        val provider = LocalModelCommandProvider(
            runtime = FakeRuntime("""{"tool":"observe_screen","args":{}}"""),
            task = "go home",
            skill = null
        )

        assertEquals("""{"tool":"observe_screen","args":{}}""", provider.complete("", ""))
        assertEquals("""{"tool":"observe_screen","args":{}}""", provider.complete("", ""))
    }

    @Test
    fun returnsCannotRouteFinalAnswerWhenRuntimeUnavailable() {
        val provider = LocalModelCommandProvider(
            runtime = FakeRuntime(null),
            task = "go back",
            skill = null
        )

        val command = AgentCommandParser.parse(provider.complete("", ""))
        assertNull(command.tool)
        assertEquals("Local model could not route this task safely.", command.finalAnswer)
    }

    private class FakeRuntime(
        private val output: String?
    ) : LocalCommandModelRuntime {
        override fun status(): LocalModelStatus {
            return LocalModelStatus(
                available = output != null,
                runtime = "Test",
                modelAsset = "test.tflite",
                version = "test",
                message = "test"
            )
        }

        override fun route(task: String, context: String, skill: dev.touchpilot.app.memory.Skill?): String {
            return output ?: error("missing model")
        }
    }
}
