package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillLoadMergerTest {
    @Test
    fun localSkillsShadowBundledSkillsById() {
        val bundled = SkillLoad(
            skills = listOf(
                skill("settings", "Bundled Settings"),
                skill("browser", "Bundled Browser"),
            ),
            invalid = listOf(SkillParseResult.Invalid("settings", listOf("bundled invalid")))
        )
        val local = SkillLoad(
            skills = listOf(skill("settings", "Local Settings")),
            invalid = listOf(SkillParseResult.Invalid("settings", listOf("local invalid")))
        )

        val merged = SkillLoadMerger.merge(bundled, local, shadowedIds = setOf("settings"))

        assertEquals(listOf("browser", "settings"), merged.skills.map { it.id })
        assertEquals("Local Settings", merged.skills.last { it.id == "settings" }.title)
        assertEquals(1, merged.invalid.size)
        assertTrue(merged.invalid.first().errors.first().contains("local invalid"))
    }

    @Test
    fun invalidLocalSkillsDoNotHideBundledFallbacks() {
        val bundled = SkillLoad(
            skills = listOf(
                skill("settings", "Bundled Settings"),
            ),
            invalid = emptyList(),
        )
        val local = SkillLoad(
            skills = emptyList(),
            invalid = listOf(SkillParseResult.Invalid("settings", listOf("local invalid"))),
        )

        val merged = SkillLoadMerger.merge(bundled, local, shadowedIds = emptySet())

        assertEquals(listOf("settings"), merged.skills.map { it.id })
        assertEquals("Bundled Settings", merged.skills.first().title)
        assertEquals(1, merged.invalid.size)
        assertTrue(merged.invalid.first().errors.first().contains("local invalid"))
    }
    private fun skill(id: String, title: String): Skill {
        return Skill(
            id = id,
            title = title,
            markdown = "",
            allowedTools = setOf("tap"),
        )
    }
}
