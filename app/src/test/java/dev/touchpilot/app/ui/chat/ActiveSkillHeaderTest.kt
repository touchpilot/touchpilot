package dev.touchpilot.app.ui.chat

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillFormat
import dev.touchpilot.app.memory.SkillRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveSkillHeaderTest {
    @Test
    fun fromSurfacesTitleAndRisk() {
        val header = ActiveSkillHeader.from(sampleSkill(title = "Wi-Fi Settings", risk = SkillRisk.MEDIUM))

        assertEquals("Wi-Fi Settings", header.title)
        assertEquals("Medium risk", header.riskLabel)
        assertTrue(header.riskAccent)
    }

    @Test
    fun fromKeepsLowRiskMuted() {
        val header = ActiveSkillHeader.from(sampleSkill(risk = SkillRisk.LOW))

        assertEquals("Low risk", header.riskLabel)
        assertFalse(header.riskAccent)
    }

    @Test
    fun fromFallsBackToIdWhenTitleBlank() {
        val header = ActiveSkillHeader.from(sampleSkill(title = ""))

        assertEquals("demo", header.title)
    }

    private fun sampleSkill(
        title: String = "Demo",
        risk: SkillRisk = SkillRisk.LOW
    ): Skill {
        return Skill(
            id = "demo",
            title = title,
            markdown = "# Demo\n\nBody text.",
            allowedTools = linkedSetOf("tap"),
            description = "A demo skill.",
            risk = risk,
            format = SkillFormat.V2
        )
    }
}
