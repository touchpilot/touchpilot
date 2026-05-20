package dev.touchpilot.app.localinference

import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

class LocalModelContractsTest {
    @Test
    fun requestIncludesFilteredToolContractsForActiveSkill() {
        val skill = Skill(
            id = "settings",
            title = "Settings",
            markdown = "",
            allowedTools = setOf("observe_screen", "tap")
        )

        val request = LocalModelRequest.from(
            task = "Open Wi-Fi settings",
            context = "screen context",
            skill = skill
        )
        val json = request.toJson()
        val tools = request.tools.map { it.name }

        assertEquals("Open Wi-Fi settings", request.task)
        assertEquals("settings", request.activeSkillId)
        assertEquals(listOf("observe_screen", "tap"), tools)
        assertEquals("settings", json.getString("active_skill_id"))
        assertEquals(2, json.getJSONArray("tools").length())
    }

    @Test
    fun parsesToolCallOutput() {
        val output = LocalModelOutputParser.parse(
            """{"tool":"tap","args":{"text":"Network & internet"}}"""
        )

        val toolCall = assertIs<LocalModelOutput.ToolCall>(output)
        assertEquals("tap", toolCall.tool)
        assertEquals("Network & internet", toolCall.args["text"])
        assertEquals(
            """{"tool":"tap","args":{"text":"Network & internet"}}""",
            toolCall.toCommandJson()
        )
    }

    @Test
    fun parsesFinalAnswerOutput() {
        val output = LocalModelOutputParser.parse(
            """{"final":"I cannot do that safely."}"""
        )

        val finalAnswer = assertIs<LocalModelOutput.FinalAnswer>(output)
        assertEquals("I cannot do that safely.", finalAnswer.text)
        assertEquals(
            """{"final":"I cannot do that safely."}""",
            finalAnswer.toCommandJson()
        )
    }

    @Test
    fun rejectsMalformedOutput() {
        val error = runCatching {
            LocalModelOutputParser.parse("not json")
        }.exceptionOrNull()

        assertThrowable(error)
        assertTrue(error!!.message.orEmpty().contains("JSON object"))
    }

    @Test
    fun rejectsOutputWithUnknownTool() {
        val output = LocalModelOutput.ToolCall("unknown_tool", emptyMap())
        val result = LocalModelOutputValidator.validate(output, skill = null)

        val invalid = assertIs<LocalModelOutputValidation.Invalid>(result)
        assertEquals("Unknown tool: unknown_tool", invalid.reason)
    }

    @Test
    fun rejectsOutputWithInvalidArgs() {
        val output = LocalModelOutput.ToolCall(
            tool = "tap",
            args = mapOf("text" to "OK", "bounds" to "0,0,1,1")
        )

        val result = LocalModelOutputValidator.validate(output, skill = null)

        val invalid = assertIs<LocalModelOutputValidation.Invalid>(result)
        assertEquals("tap requires exactly one selector: text, node_id, or bounds", invalid.reason)
    }

    @Test
    fun rejectsOutputBlockedBySkillAllowlist() {
        val skill = Skill(
            id = "observe-only",
            title = "Observe Only",
            markdown = "",
            allowedTools = setOf("observe_screen")
        )
        val output = LocalModelOutput.ToolCall(
            tool = "open_app",
            args = mapOf("target" to "Settings")
        )

        val result = LocalModelOutputValidator.validate(output, skill)

        val invalid = assertIs<LocalModelOutputValidation.Invalid>(result)
        assertEquals("open_app is not allowed by Observe Only.", invalid.reason)
    }

    @Test
    fun acceptsValidFinalAnswerAndToolCall() {
        val final = LocalModelOutputValidator.validate(
            LocalModelOutput.FinalAnswer("Done."),
            skill = null
        )
        val tool = LocalModelOutputValidator.validate(
            LocalModelOutput.ToolCall("open_app", mapOf("target" to "Settings")),
            skill = null
        )

        assertIs<LocalModelOutputValidation.Valid>(final)
        assertIs<LocalModelOutputValidation.Valid>(tool)
    }

    private fun assertThrowable(value: Throwable?) {
        if (value == null) fail("Expected non-null Throwable")
    }
}
