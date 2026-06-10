package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillRisk
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentPromptsTest {
    private fun richSkill() = Skill(
        id = "settings",
        title = "Settings",
        markdown = "Prefer direct settings panels when available.",
        allowedTools = setOf("observe_screen_context", "open_settings_panel"),
        description = "Navigate and inspect Android Settings screens safely.",
        risk = SkillRisk.MEDIUM,
        examples = listOf("open Wi-Fi settings", "show Bluetooth settings"),
        successCriteria = listOf("The requested Settings screen is foreground.")
    )

    @Test
    fun systemPromptIncludesRichSkillContext() {
        val prompt = AgentPrompts.systemPrompt(richSkill())

        assertTrue(prompt.contains("Active skill: Settings"), prompt)
        assertTrue(prompt.contains("Description: Navigate and inspect Android Settings screens safely."), prompt)
        assertTrue(prompt.contains("Risk: medium"), prompt)
        assertTrue(prompt.contains("Allowed tools:"), prompt)
        assertTrue(prompt.contains("open_settings_panel"), prompt)
        assertTrue(prompt.contains("Example requests:"), prompt)
        assertTrue(prompt.contains("- open Wi-Fi settings"), prompt)
        assertTrue(prompt.contains("Success criteria:"), prompt)
        assertTrue(prompt.contains("- The requested Settings screen is foreground."), prompt)
        assertTrue(prompt.contains("Prefer direct settings panels when available."), prompt)
        assertTrue(prompt.contains("Only use tools allowed by the active skill allowlist."), prompt)
    }

    @Test
    fun systemPromptOmitsEmptyOptionalFields() {
        val sparse = Skill(
            id = "browser",
            title = "Browser",
            markdown = "",
            allowedTools = setOf("open_app")
        )

        val prompt = AgentPrompts.systemPrompt(sparse)

        assertTrue(prompt.contains("Active skill: Browser"), prompt)
        assertTrue(prompt.contains("Risk: low"), prompt)
        assertFalse(prompt.contains("Description:"), prompt)
        assertFalse(prompt.contains("Example requests:"), prompt)
        assertFalse(prompt.contains("Success criteria:"), prompt)
        assertFalse(prompt.contains("Instructions:"), prompt)
    }

    @Test
    fun systemPromptWithoutSkillHasNoSkillBlock() {
        val prompt = AgentPrompts.systemPrompt(null)

        assertFalse(prompt.contains("Active skill"), prompt)
        assertTrue(prompt.contains("Available tools:"), prompt)
    }
}
