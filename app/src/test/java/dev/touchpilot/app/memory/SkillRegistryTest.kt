package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSkillPreferences(
    disabledIds: Set<String> = emptySet(),
    activeId: String? = null
) : SkillPreferences {
    private var disabled = disabledIds.toMutableSet()
    private var active = activeId

    override fun disabledSkillIds(): Set<String> = disabled.toSet()
    override fun setDisabledSkillIds(ids: Set<String>) { disabled = ids.toMutableSet() }
    override fun activeSkillId(): String? = active
    override fun setActiveSkillId(id: String?) { active = id }
}

class SkillRegistryTest {
    private val settings = Skill(
        id = "settings",
        title = "Settings",
        markdown = "",
        allowedTools = setOf("open_app")
    )
    private val browser = Skill(
        id = "browser",
        title = "Browser",
        markdown = "",
        allowedTools = setOf("open_app", "type_text")
    )

    @Test
    fun allInstalledSkillsAreEnabledByDefault() {
        val registry = SkillRegistry(
            skills = listOf(settings, browser),
            preferences = FakeSkillPreferences()
        )

        assertEquals(listOf(settings, browser), registry.enabledSkills())
        assertTrue(registry.isEnabled("settings"))
        assertTrue(registry.isEnabled("browser"))
    }

    @Test
    fun disabledSkillsAreExcludedFromEnabledSkills() {
        val registry = SkillRegistry(
            skills = listOf(settings, browser),
            preferences = FakeSkillPreferences(disabledIds = setOf("browser"))
        )

        assertEquals(listOf(settings), registry.enabledSkills())
        assertFalse(registry.isEnabled("browser"))
    }

    @Test
    fun disablingActiveSkillClearsActiveSelection() {
        val registry = SkillRegistry(
            skills = listOf(settings, browser),
            preferences = FakeSkillPreferences(activeId = "browser")
        )
        registry.setEnabled("browser", enabled = false)

        assertNull(registry.activeSkill())
        assertFalse(registry.isEnabled("browser"))
    }

    @Test
    fun selectingDisabledSkillFailsClosed() {
        val registry = SkillRegistry(
            skills = listOf(settings, browser),
            preferences = FakeSkillPreferences(
                disabledIds = setOf("browser"),
                activeId = "settings"
            )
        )
        registry.setActiveSkill("browser")

        assertNull(registry.activeSkill())
    }

    @Test
    fun enablingSkillMakesItSelectableAgain() {
        val registry = SkillRegistry(
            skills = listOf(settings, browser),
            preferences = FakeSkillPreferences(disabledIds = setOf("browser"))
        )
        registry.setEnabled("browser", enabled = true)
        registry.setActiveSkill("browser")

        assertEquals(browser, registry.activeSkill())
    }
}
