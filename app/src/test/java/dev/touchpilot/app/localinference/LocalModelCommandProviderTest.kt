package dev.touchpilot.app.localinference

import dev.touchpilot.app.agent.LocalRouterCommandProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalModelCommandProviderTest {
    @Test
    fun usesValidModelCommandBeforeFallback() {
        val provider = LocalModelCommandProvider(
            runtime = FakeRuntime("""{"tool":"observe_screen","args":{}}"""),
            fallback = LocalRouterCommandProvider("go back", null),
            task = "go home",
            skill = null
        )

        assertEquals("""{"tool":"observe_screen","args":{}}""", provider.complete("", ""))
    }

    @Test
    fun fallsBackWhenRuntimeUnavailable() {
        val provider = LocalModelCommandProvider(
            runtime = FakeRuntime(null),
            fallback = LocalRouterCommandProvider("go back", null),
            task = "go back",
            skill = null
        )

        assertEquals("""{"tool":"observe_screen","args":{}}""", provider.complete("", ""))
        assertTrue(provider.complete("", "").contains("press_back"))
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
