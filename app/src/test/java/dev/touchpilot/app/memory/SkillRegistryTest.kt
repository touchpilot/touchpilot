package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        val registry = SkillRegistry(installedSkills = listOf(settings, browser))

        assertEquals(listOf(settings, browser), registry.enabledSkills)
        assertTrue(registry.isEnabled("settings"))
        assertTrue(registry.isEnabled("browser"))
    }

    @Test
    fun disabledSkillsAreExcludedFromEnabledSkills() {
        val registry = SkillRegistry(
            installedSkills = listOf(settings, browser),
            disabledSkillIds = setOf("browser")
        )

        assertEquals(listOf(settings), registry.enabledSkills)
        assertFalse(registry.isEnabled("browser"))
    }

    @Test
    fun disablingActiveSkillClearsActiveSelection() {
        val registry = SkillRegistry(
            installedSkills = listOf(settings, browser),
            activeSkillId = "browser"
        ).setEnabled("browser", enabled = false)

        assertNull(registry.activeSkillId)
        assertNull(registry.activeSkill)
        assertFalse(registry.isEnabled("browser"))
    }

    @Test
    fun selectingDisabledSkillFailsClosed() {
        val registry = SkillRegistry(
            installedSkills = listOf(settings, browser),
            disabledSkillIds = setOf("browser"),
            activeSkillId = "settings"
        ).select("browser")

        assertNull(registry.activeSkillId)
        assertNull(registry.activeSkill)
    }

    @Test
    fun enablingSkillMakesItSelectableAgain() {
        val registry = SkillRegistry(
            installedSkills = listOf(settings, browser),
            disabledSkillIds = setOf("browser")
        ).setEnabled("browser", enabled = true).select("browser")

        assertEquals("browser", registry.activeSkillId)
        assertEquals(browser, registry.activeSkill)
    }
}
