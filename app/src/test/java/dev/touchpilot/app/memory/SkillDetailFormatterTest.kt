package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillDetailFormatterTest {
    @Test
    fun displayDescriptionPrefersStructuredField() {
        val skill = sampleSkill(description = "Structured summary.")

        assertEquals("Structured summary.", SkillDetailFormatter.displayDescription(skill))
    }

    @Test
    fun displayDescriptionFallsBackToLegacyMarkdown() {
        val skill = sampleSkill(
            description = "",
            markdown = """
                # Legacy Skill

                Use settings screens carefully.

                Allowed initial tools:
                - tap
            """.trimIndent()
        )

        assertEquals("Use settings screens carefully.", SkillDetailFormatter.displayDescription(skill))
    }

    @Test
    fun detailSectionsSurfaceAllMetadataFields() {
        val skill = sampleSkill(
            description = "Navigate settings safely.",
            risk = SkillRisk.MEDIUM,
            aliases = listOf("settings", "wi-fi"),
            examples = listOf("open Wi-Fi settings"),
            successCriteria = listOf("Settings screen is foreground."),
            allowedTools = linkedSetOf("tap", "scroll"),
            markdown = "# Settings\n\nStop when visible."
        )

        val sections = SkillDetailFormatter.detailSections(skill).associate { it.title to it.body }

        assertContains(sections.getValue("Description"), "Navigate settings safely.")
        assertContains(sections.getValue("Risk"), "Medium risk")
        assertContains(sections.getValue("Aliases"), "settings")
        assertContains(sections.getValue("Allowed tools"), "tap")
        assertContains(sections.getValue("Examples"), "open Wi-Fi settings")
        assertContains(sections.getValue("Success criteria"), "Settings screen is foreground.")
        assertContains(sections.getValue("Instructions"), "Stop when visible.")
    }

    @Test
    fun emptyOptionalSectionsAreMarkedEmpty() {
        val skill = sampleSkill()

        val aliases = SkillDetailFormatter.detailSections(skill).first { it.title == "Aliases" }
        val examples = SkillDetailFormatter.detailSections(skill).first { it.title == "Examples" }

        assertTrue(aliases.empty)
        assertEquals("None declared.", aliases.body)
        assertTrue(examples.empty)
    }

    @Test
    fun riskPresentationEscalatesCautionForHighRisk() {
        val high = SkillDetailFormatter.riskPresentation(SkillRisk.HIGH)

        assertEquals("High risk", high.label)
        assertTrue(high.caution)
        assertTrue(high.accent)
    }

    @Test
    fun riskPresentationKeepsLowRiskMuted() {
        val low = SkillDetailFormatter.riskPresentation(SkillRisk.LOW)

        assertEquals("Low risk", low.label)
        assertFalse(low.caution)
        assertFalse(low.accent)
    }

    @Test
    fun formatAllowedToolsSortsAlphabetically() {
        val formatted = SkillDetailFormatter.formatAllowedTools(linkedSetOf("scroll", "tap", "open_app"))

        assertEquals(
            """
            • open_app
            • scroll
            • tap
            """.trimIndent(),
            formatted
        )
    }

    private fun sampleSkill(
        description: String = "A demo skill.",
        risk: SkillRisk = SkillRisk.LOW,
        aliases: List<String> = emptyList(),
        examples: List<String> = emptyList(),
        successCriteria: List<String> = emptyList(),
        allowedTools: Set<String> = linkedSetOf("tap"),
        markdown: String = "# Demo\n\nBody text."
    ): Skill {
        return Skill(
            id = "demo",
            title = "Demo",
            markdown = markdown,
            allowedTools = allowedTools,
            description = description,
            risk = risk,
            aliases = aliases,
            examples = examples,
            successCriteria = successCriteria,
            format = SkillFormat.V2
        )
    }
}
