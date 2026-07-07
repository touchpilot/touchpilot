package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SkillCatalogFormatterTest {
    @Test
    fun useCardSurfacesTitleScopeRiskAndPermissions() {
        val skill = sampleSkill(
            title = "Wi-Fi Settings",
            description = "Navigate settings safely.",
            risk = SkillRisk.MEDIUM,
            examples = listOf("open Wi-Fi settings", "turn on airplane mode"),
            allowedTools = linkedSetOf("scroll", "tap")
        )

        val card = SkillCatalogFormatter.useCard(skill)

        assertEquals("Wi-Fi Settings", card.title)
        assertEquals("Navigate settings safely.", card.scope)
        assertEquals("Medium risk", card.riskLabel)
        assertTrue(card.riskAccent)
        assertEquals(listOf("open Wi-Fi settings", "turn on airplane mode"), card.examples)
        assertEquals("scroll, tap", card.permissions)
        assertEquals("Run skill", card.runLabel)
    }

    @Test
    fun useCardKeepsLowRiskMuted() {
        val card = SkillCatalogFormatter.useCard(sampleSkill(risk = SkillRisk.LOW))

        assertEquals("Low risk", card.riskLabel)
        assertFalse(card.riskAccent)
    }

    @Test
    fun useCardLimitsExamplesAndDropsBlankOnes() {
        val skill = sampleSkill(
            examples = listOf("one", "", "two", "three", "four")
        )

        val card = SkillCatalogFormatter.useCard(skill)

        assertEquals(listOf("one", "two", "three"), card.examples)
    }

    @Test
    fun useCardFallsBackToIdWhenTitleBlankAndProvidesDefaults() {
        val skill = sampleSkill(
            title = "",
            description = "",
            examples = emptyList(),
            allowedTools = emptySet(),
            markdown = "# Only a heading"
        )

        val card = SkillCatalogFormatter.useCard(skill)

        assertEquals("demo", card.title)
        assertEquals("No description provided.", card.scope)
        assertTrue(card.examples.isEmpty())
        assertEquals("No tools required.", card.permissions)
    }

    @Test
    fun formatPermissionsSortsAlphabetically() {
        assertEquals(
            "open_app, scroll, tap",
            SkillCatalogFormatter.formatPermissions(linkedSetOf("scroll", "tap", "open_app"))
        )
    }

    private fun sampleSkill(
        title: String = "Demo",
        description: String = "A demo skill.",
        risk: SkillRisk = SkillRisk.LOW,
        examples: List<String> = emptyList(),
        allowedTools: Set<String> = linkedSetOf("tap"),
        markdown: String = "# Demo\n\nBody text."
    ): Skill {
        return Skill(
            id = "demo",
            title = title,
            markdown = markdown,
            allowedTools = allowedTools,
            description = description,
            risk = risk,
            examples = examples,
            format = SkillFormat.V2
        )
    }
}
