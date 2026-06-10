package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

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
    fun treatsJsonNullArgAsEmptyNotLiteralNull() {
        val command = AgentCommandParser.parse(
            """{"tool":"tap","args":{"text":null}}"""
        )

        assertEquals("tap", command.tool)
        // A JSON null must not become the literal string "null".
        assertEquals("", command.args["text"])
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

    @Test
    fun parsesActionWhenProseBeforeJsonContainsStrayBrace() {
        val command = AgentCommandParser.parse(
            """User asked: "open {Settings} app". Here is the command: {"tool":"open_app","args":{"target":"Settings"}}"""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun parsesActionWhenProseAfterJsonContainsStrayBrace() {
        val command = AgentCommandParser.parse(
            """{"tool":"open_app","args":{"target":"Settings"}} — that's the {final} answer."""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun picksActionObjectWhenModelEmitsThoughtThenAction() {
        val command = AgentCommandParser.parse(
            """{"thought":"I will open Settings"} {"tool":"open_app","args":{"target":"Settings"}}"""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun picksFinalObjectWhenModelEmitsThoughtThenFinal() {
        val command = AgentCommandParser.parse(
            """{"thought":"task is done"} {"final":"All set."}"""
        )

        assertEquals("All set.", command.finalAnswer)
        assertNull(command.tool)
    }

    @Test
    fun preservesClosingBraceInsideStringLiteralValue() {
        val command = AgentCommandParser.parse(
            """{"tool":"type_text","args":{"text":"close } here"}}"""
        )

        assertEquals("type_text", command.tool)
        assertEquals("close } here", command.args["text"])
    }

    @Test
    fun preservesEscapedQuoteInsideStringLiteralValue() {
        val command = AgentCommandParser.parse(
            """{"tool":"type_text","args":{"text":"he said \"hi\" then left"}}"""
        )

        assertEquals("type_text", command.tool)
        assertEquals("""he said "hi" then left""", command.args["text"])
    }

    @Test
    fun parsesNestedArgsObjectFromBareJson() {
        val command = AgentCommandParser.parse(
            """{"tool":"swipe","args":{"from":"{x:0,y:0}","to":"{x:100,y:200}"}}"""
        )

        assertEquals("swipe", command.tool)
        assertEquals("{x:0,y:0}", command.args["from"])
        assertEquals("{x:100,y:200}", command.args["to"])
    }

    @Test
    fun parsesFencedJsonWhenProseAndStrayBracesSurroundFence() {
        val command = AgentCommandParser.parse(
            "Plan {step1}. Running:\n```json\n{\"tool\":\"open_app\",\"args\":{\"target\":\"Settings\"}}\n```\nDone {next}."
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun ignoresInvalidJsonCandidatesBeforeValidAction() {
        val command = AgentCommandParser.parse(
            """Thinking: {unbalanced quotes 'foo} and {Settings} and {also bad}. Action: {"tool":"open_app","args":{"target":"Settings"}}"""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun parsesActionWhenUnmatchedOpeningBracePrecedesValidCommand() {
        val command = AgentCommandParser.parse(
            """Thinking { unfinished note. Action: {"tool":"open_app","args":{"target":"Settings"}}"""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun prefersObjectWithToolOverTrailingValidJsonWithoutToolOrFinal() {
        val command = AgentCommandParser.parse(
            """{"tool":"open_app","args":{"target":"Settings"}} status: {"ok":true}"""
        )

        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun throwsClearErrorWhenNoJsonObjectPresent() {
        val error = assertFailsWith<IllegalStateException> {
            AgentCommandParser.parse("the model emitted only prose with no json")
        }
        assertEquals(true, error.message!!.startsWith("Model did not return a JSON object"))
    }

    @Test
    fun throwsClearErrorWhenOnlyUnmatchedOpenBracePresent() {
        val error = assertFailsWith<IllegalStateException> {
            AgentCommandParser.parse("""the model emitted { but never closed it""")
        }
        assertEquals(true, error.message!!.startsWith("Model did not return a JSON object"))
    }

    @Test
    fun handlesEmptyArgsObject() {
        val command = AgentCommandParser.parse(
            """{"tool":"observe_screen","args":{}}"""
        )

        assertEquals("observe_screen", command.tool)
        assertEquals(0, command.args.size)
    }
}
