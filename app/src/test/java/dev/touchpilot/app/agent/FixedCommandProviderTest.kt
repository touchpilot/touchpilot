package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixedCommandProviderTest {
    @Test
    fun emitsCommandThenFinal() {
        val provider = FixedCommandProvider(
            command = AgentCommand(
                tool = "press_back",
                args = emptyMap(),
                finalAnswer = null
            )
        )

        val first = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("press_back", first.tool)
        assertEquals(emptyMap(), first.args)
        assertNull(first.finalAnswer)

        val second = AgentCommandParser.parse(provider.complete("", ""))
        assertNull(second.tool)
        assertEquals("Exact command completed.", second.finalAnswer)
    }

    @Test
    fun keepsArgsVerbatim() {
        val provider = FixedCommandProvider(
            command = AgentCommand(
                tool = "scroll",
                args = mapOf("direction" to "backward"),
                finalAnswer = null
            )
        )

        val parsed = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("scroll", parsed.tool)
        assertEquals("backward", parsed.args["direction"])
    }

    @Test
    fun escapesQuotesAndBackslashesInArgs() {
        val provider = FixedCommandProvider(
            command = AgentCommand(
                tool = "tap",
                args = mapOf("text" to """OK"button\path"""),
                finalAnswer = null
            )
        )

        val parsed = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("tap", parsed.tool)
        assertEquals("""OK"button\path""", parsed.args["text"])
    }

    @Test
    fun customFinalMessageOverridesDefault() {
        val provider = FixedCommandProvider(
            command = AgentCommand(
                tool = "press_home",
                args = emptyMap(),
                finalAnswer = null
            ),
            finalMessage = "Pressed home from intent gate."
        )

        provider.complete("", "")
        val second = AgentCommandParser.parse(provider.complete("", ""))
        assertEquals("Pressed home from intent gate.", second.finalAnswer)
    }

    @Test
    fun finalIsStickyAfterFirstCommand() {
        val provider = FixedCommandProvider(
            command = AgentCommand(
                tool = "press_back",
                args = emptyMap(),
                finalAnswer = null
            )
        )
        provider.complete("", "")
        val secondJson = provider.complete("", "")
        val thirdJson = provider.complete("", "")
        assertEquals(secondJson, thirdJson)
        assertTrue("final" in secondJson)
    }
}
