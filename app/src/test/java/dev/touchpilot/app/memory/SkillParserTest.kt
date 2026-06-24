package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class SkillParserTest {
    private val knownTools = setOf(
        "observe_screen_context",
        "open_app",
        "open_settings_panel",
        "tap",
        "scroll",
        "press_back",
        "wait_for_idle",
        "wait_for_app"
    )

    private fun validV2(): String = """
        ---
        id: settings
        title: Settings
        description: Navigate and inspect Android Settings screens safely.
        risk: medium
        aliases:
          - settings
          - android settings
          - wi-fi settings
        allowed_tools:
          - observe_screen_context
          - open_app
          - open_settings_panel
          - tap
          - scroll
          - press_back
          - wait_for_idle
        success_criteria:
          - The requested Settings screen is foreground.
          - The agent stops after the requested state is visible.
        examples:
          - open Wi-Fi settings
          - show Bluetooth settings
        ---

        # Settings

        Prefer direct settings panels when available.
    """.trimIndent()

    private fun valid(result: SkillParseResult): Skill = when (result) {
        is SkillParseResult.Valid -> result.skill
        is SkillParseResult.Invalid -> fail("expected Valid, got Invalid: ${result.errors}")
    }

    private fun invalid(result: SkillParseResult): SkillParseResult.Invalid = when (result) {
        is SkillParseResult.Invalid -> result
        is SkillParseResult.Valid -> fail("expected Invalid, got Valid: ${result.skill}")
    }

    @Test
    fun parsesAllV2Fields() {
        val skill = valid(SkillParser.parse("settings", validV2(), knownTools))

        assertEquals("settings", skill.id)
        assertEquals("Settings", skill.title)
        assertEquals(SkillFormat.V2, skill.format)
        assertEquals(SkillRisk.MEDIUM, skill.risk)
        assertEquals("Navigate and inspect Android Settings screens safely.", skill.description)
        assertEquals(
            listOf("settings", "android settings", "wi-fi settings"),
            skill.aliases
        )
        assertEquals(listOf("open Wi-Fi settings", "show Bluetooth settings"), skill.examples)
        assertEquals(2, skill.successCriteria.size)
        assertTrue("observe_screen_context" in skill.allowedTools)
        assertEquals(7, skill.allowedTools.size)
    }

    @Test
    fun preservesMarkdownBodyOnly() {
        val skill = valid(SkillParser.parse("settings", validV2(), knownTools))

        assertTrue(skill.markdown.startsWith("# Settings"), skill.markdown)
        assertFalse(skill.markdown.contains("allowed_tools"))
        assertFalse(skill.markdown.contains("---"))
    }

    @Test
    fun stripsQuotesFromScalarValues() {
        val markdown = """
            ---
            id: demo
            title: "Quoted Title"
            description: 'Single quoted.'
            risk: low
            allowed_tools:
              - tap
            ---
            body
        """.trimIndent()

        val skill = valid(SkillParser.parse("demo", markdown, knownTools))

        assertEquals("Quoted Title", skill.title)
        assertEquals("Single quoted.", skill.description)
    }

    @Test
    fun reportsUnknownRiskAndFailsClosed() {
        val markdown = validV2().replace("risk: medium", "risk: critical")

        val result = invalid(SkillParser.parse("settings", markdown, knownTools))

        assertTrue(
            result.errors.any { it.contains("invalid risk 'critical'") },
            result.errors.toString()
        )
    }

    @Test
    fun reportsUnknownToolsInAllowlist() {
        val markdown = validV2().replace("  - wait_for_idle", "  - teleport\n  - hack_root")

        val result = invalid(SkillParser.parse("settings", markdown, knownTools))

        val toolError = result.errors.single { it.contains("unknown tool") }
        assertTrue(toolError.contains("teleport"), toolError)
        assertTrue(toolError.contains("hack_root"), toolError)
    }

    @Test
    fun reportsEmptyAllowedTools() {
        val markdown = """
            ---
            id: demo
            title: Demo
            description: A demo skill.
            risk: low
            allowed_tools:
            ---
            body
        """.trimIndent()

        val result = invalid(SkillParser.parse("demo", markdown, knownTools))

        assertTrue(
            result.errors.any { it.contains("allowed_tools") },
            result.errors.toString()
        )
    }

    @Test
    fun reportsMissingRequiredFields() {
        val markdown = """
            ---
            id: demo
            allowed_tools:
              - tap
            ---
            body
        """.trimIndent()

        val result = invalid(SkillParser.parse("demo", markdown, knownTools))

        assertTrue(result.errors.any { it.contains("missing required field: title") }, result.errors.toString())
        assertTrue(result.errors.any { it.contains("missing required field: description") }, result.errors.toString())
        assertTrue(result.errors.any { it.contains("missing required field: risk") }, result.errors.toString())
    }

    @Test
    fun reportsIdMismatchWithDirectory() {
        val result = invalid(SkillParser.parse("settings-panel", validV2(), knownTools))

        assertTrue(
            result.errors.any { it.contains("does not match the skill directory 'settings-panel'") },
            result.errors.toString()
        )
    }

    @Test
    fun reportsMalformedId() {
        val markdown = validV2().replace("id: settings", "id: Settings_Panel")

        val result = invalid(SkillParser.parse("Settings_Panel", markdown, knownTools))

        assertTrue(
            result.errors.any { it.contains("must use only a-z, 0-9, and '-'") },
            result.errors.toString()
        )
    }

    @Test
    fun reportsUnclosedFrontMatter() {
        val markdown = """
            ---
            id: demo
            title: Demo
            description: A demo.
            risk: low
            allowed_tools:
              - tap

            # Demo
            no closing fence
        """.trimIndent()

        val result = invalid(SkillParser.parse("demo", markdown, knownTools))

        assertTrue(
            result.errors.any { it.contains("front matter must be closed") },
            result.errors.toString()
        )
    }

    @Test
    fun collectsMultipleErrorsAtOnce() {
        val markdown = """
            ---
            id: demo
            title: Demo
            description: A demo.
            risk: spicy
            allowed_tools:
              - teleport
            ---
            body
        """.trimIndent()

        val result = invalid(SkillParser.parse("demo", markdown, knownTools))

        assertTrue(result.errors.size >= 2, result.errors.toString())
        assertTrue(result.errors.any { it.contains("invalid risk") }, result.errors.toString())
        assertTrue(result.errors.any { it.contains("unknown tool") }, result.errors.toString())
    }

    @Test
    fun parsesLegacyV1WithoutToolValidation() {
        // The legacy path must stay permissive for older skill packs that still
        // use the heading + "Allowed initial tools:" format.
        val markdown = """
            # Settings Skill

            Use Android settings screens carefully.

            Allowed initial tools:

            - `open_app`
            - `wait_for_ui`
        """.trimIndent()

        val skill = valid(SkillParser.parse("settings", markdown, knownTools))

        assertEquals(SkillFormat.LEGACY_V1, skill.format)
        assertEquals("Settings Skill", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertEquals(setOf("open_app", "wait_for_ui"), skill.allowedTools)
        assertTrue(skill.aliases.isEmpty())
    }

    @Test
    fun optionalListFieldsDefaultToEmpty() {
        val markdown = """
            ---
            id: demo
            title: Demo
            description: A demo.
            risk: high
            allowed_tools:
              - tap
            ---
            body
        """.trimIndent()

        val skill = valid(SkillParser.parse("demo", markdown, knownTools))

        assertEquals(SkillRisk.HIGH, skill.risk)
        assertTrue(skill.aliases.isEmpty())
        assertTrue(skill.examples.isEmpty())
        assertTrue(skill.successCriteria.isEmpty())
    }
}
