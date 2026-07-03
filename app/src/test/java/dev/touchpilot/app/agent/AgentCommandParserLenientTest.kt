package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Covers the lenient fallback in [AgentCommandParser]: recoverable JSON defects
 * that strict `org.json` parsing rejects (trailing commas, smart quotes,
 * comments) should still yield a usable command, while genuinely unparseable
 * output must still raise the same clear error.
 */
class AgentCommandParserLenientTest {

    @Test
    fun parsesToolWithTrailingCommaInArgs() {
        val command = AgentCommandParser.parse(
            """{"tool":"tap","args":{"text":"Sign In",}}"""
        )

        assertEquals("tap", command.tool)
        assertEquals("Sign In", command.args["text"])
    }

    @Test
    fun parsesToolWithTrailingCommaAfterArgsObject() {
        val command = AgentCommandParser.parse(
            """{"tool":"observe_screen","args":{},}"""
        )

        assertEquals("observe_screen", command.tool)
        assertEquals(0, command.args.size)
    }

    @Test
    fun parsesToolWithSmartQuotes() {
        val command = AgentCommandParser.parse(
            "{“tool”:“open_app”,“args”:{“target”:“Settings”}}"
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun parsesFinalWithSmartQuotes() {
        val command = AgentCommandParser.parse("{“final”:“All done.”}")

        assertEquals("All done.", command.finalAnswer)
        assertNull(command.tool)
    }

    @Test
    fun parsesToolWithCurlySingleQuotes() {
        // End-to-end recovery of curly single-quote delimited output: the repair
        // must yield valid double-quote JSON, not just ASCII single quotes.
        val command = AgentCommandParser.parse(
            "{‘tool’:‘open_app’,‘args’:{‘target’:‘Settings’}}"
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun parsesToolWithAsciiSingleQuotes() {
        val command = AgentCommandParser.parse(
            "{'tool':'tap','args':{'text':'Sign In'}}"
        )

        assertEquals("tap", command.tool)
        assertEquals("Sign In", command.args["text"])
    }

    @Test
    fun preservesApostropheInFinalAnswerContent() {
        val command = AgentCommandParser.parse("{“final”:“I’ll open Settings.”}")

        assertEquals("I’ll open Settings.", command.finalAnswer)
    }

    @Test
    fun parsesToolWithBlockComment() {
        val command = AgentCommandParser.parse(
            """{"tool":"tap"/* chosen target */,"args":{"text":"OK",}}"""
        )

        assertEquals("tap", command.tool)
        assertEquals("OK", command.args["text"])
    }

    @Test
    fun parsesFencedJsonWithTrailingComma() {
        val command = AgentCommandParser.parse(
            """
            ```json
            {"tool":"open_app","args":{"target":"Settings",}}
            ```
            """.trimIndent()
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun preservesStringContentWhileRepairingTrailingComma() {
        // The value contains a brace and a comma; only the trailing comma before
        // the closing brace should be removed.
        val command = AgentCommandParser.parse(
            """{"tool":"type_text","args":{"text":"press } then, wait",}}"""
        )

        assertEquals("type_text", command.tool)
        assertEquals("press } then, wait", command.args["text"])
    }

    @Test
    fun stillThrowsForProseWithoutJson() {
        val error = assertFailsWith<IllegalStateException> {
            AgentCommandParser.parse("the model emitted only prose with no json")
        }
        assertEquals(true, error.message!!.startsWith("Model did not return a JSON object"))
    }

    @Test
    fun stillThrowsForUnmatchedBraceEvenAfterRepair() {
        assertFailsWith<IllegalStateException> {
            AgentCommandParser.parse("""planning { but never closing it, and, more,""")
        }
    }
}
