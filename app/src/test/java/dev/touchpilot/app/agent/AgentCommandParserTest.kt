package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentCommandParserTest {
    @Test
    fun parsesToolCommandFromJson() {
        val command = AgentCommandParser.parse(
            """{"tool":"open_app","args":{"target":"Settings"}}"""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun parsesFinalAnswerFromFencedJson() {
        val command = AgentCommandParser.parse(
            """
            ```json
            {"final":"I cannot do that safely."}
            ```
            """.trimIndent()
        )

        assertEquals("I cannot do that safely.", command.finalAnswer)
    }
}
