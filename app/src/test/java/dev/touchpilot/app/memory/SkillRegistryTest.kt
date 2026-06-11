package dev.touchpilot.app.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillRegistryTest {
    private class FakeSkillPreferences(
        private var disabled: Set<String> = emptySet(),
        private var active: String? = null
    ) : SkillPreferences {
        override fun disabledSkillIds(): Set<String> = disabled
        override fun setDisabledSkillIds(ids: Set<String>) { disabled = ids }
        override fun activeSkillId(): String? = active
        override fun setActiveSkillId(id: String?) { active = id }
    }

    private fun skill(
        id: String,
        title: String = id.replaceFirstChar { it.uppercase() },
        aliases: List<String> = emptyList()
    ) = Skill(
        id = id,
        title = title,
        markdown = "",
        allowedTools = setOf("observe_screen_context"),
        aliases = aliases
    )

    private fun registry(
        prefs: FakeSkillPreferences = FakeSkillPreferences()
    ): Pair<SkillRegistry, FakeSkillPreferences> {
        val skills = listOf(
            skill("settings", "Settings", aliases = listOf("settings", "wi-fi settings")),
            skill("browser", "Browser", aliases = listOf("web", "browser")),
            skill("messages", "Messages", aliases = listOf("texts"))
        )
        return SkillRegistry(skills, prefs) to prefs
    }

    @Test
    fun enabledSkillsExcludesDisabled() {
        val (reg, _) = registry(FakeSkillPreferences(disabled = setOf("browser")))

        val ids = reg.enabledSkills().map { it.id }
        assertEquals(listOf("settings", "messages"), ids)
        assertEquals(3, reg.allSkills().size)
        assertFalse(reg.isEnabled("browser"))
        assertTrue(reg.isEnabled("settings"))
    }

    @Test
    fun setEnabledPersistsAndIsReversible() {
        val (reg, prefs) = registry()

        reg.setEnabled("browser", false)
        assertEquals(setOf("browser"), prefs.disabledSkillIds())
        assertNull(reg.findById("browser"))

        reg.setEnabled("browser", true)
        assertTrue(prefs.disabledSkillIds().isEmpty())
        assertEquals("browser", reg.findById("browser")?.id)
    }

    @Test
    fun disablingActiveSkillClearsActiveSelection() {
        val (reg, prefs) = registry(FakeSkillPreferences(active = "settings"))

        assertEquals("settings", reg.activeSkill()?.id)

        reg.setEnabled("settings", false)

        assertNull(prefs.activeSkillId())
        assertNull(reg.activeSkill())
    }

    @Test
    fun lookupsReturnEnabledSkillsOnly() {
        val (reg, _) = registry(FakeSkillPreferences(disabled = setOf("messages")))

        assertEquals("settings", reg.findById("settings")?.id)
        assertEquals("settings", reg.findByTitle("Settings")?.id)
        assertEquals("settings", reg.findByAlias("wi-fi settings")?.id)

        // messages is disabled -> not found by any lookup key.
        assertNull(reg.findById("messages"))
        assertNull(reg.findByTitle("Messages"))
        assertNull(reg.findByAlias("texts"))
    }

    @Test
    fun lookupsAreCaseInsensitiveAndTrimmed() {
        val (reg, _) = registry()

        // Skill ids are lower-case by contract, but lookup must tolerate
        // mixed/upper case and surrounding whitespace, like title/alias lookup.
        assertEquals("settings", reg.findById("  SETTINGS  ")?.id)
        assertEquals("settings", reg.findById("Settings")?.id)
        assertEquals("browser", reg.resolve("  BrOwSeR ")?.id)
        assertEquals("settings", reg.findByTitle("  sEtTiNgS  ")?.id)
        assertEquals("browser", reg.findByAlias("  WEB ")?.id)
        assertNull(reg.findById("   "))
        assertNull(reg.findByTitle("  "))
        assertNull(reg.findByAlias(""))
    }

    @Test
    fun setActiveSkillNormalizesCaseToCanonicalId() {
        val (reg, prefs) = registry()

        reg.setActiveSkill("SETTINGS")

        assertEquals("settings", prefs.activeSkillId())
        assertEquals("settings", reg.activeSkill()?.id)
    }

    @Test
    fun resolvePrefersIdThenTitleThenAlias() {
        val (reg, _) = registry()

        assertEquals("settings", reg.resolve("settings")?.id) // id
        assertEquals("browser", reg.resolve("Browser")?.id)   // title
        assertEquals("messages", reg.resolve("texts")?.id)    // alias
        assertNull(reg.resolve("nonexistent"))
    }

    @Test
    fun activeSkillClearsStaleSelectionWhenRemovedOrDisabled() {
        val (reg, prefs) = registry(FakeSkillPreferences(active = "ghost"))

        // active points at a skill that does not exist -> cleared, returns null.
        assertNull(reg.activeSkill())
        assertNull(prefs.activeSkillId())
    }

    @Test
    fun setActiveSkillRejectsDisabledOrUnknownAndFailsClosed() {
        val (reg, prefs) = registry(FakeSkillPreferences(disabled = setOf("browser")))

        reg.setActiveSkill("settings")
        assertEquals("settings", prefs.activeSkillId())

        // selecting a disabled skill fails closed to no scope.
        reg.setActiveSkill("browser")
        assertNull(prefs.activeSkillId())

        // selecting an unknown skill fails closed.
        reg.setActiveSkill("ghost")
        assertNull(prefs.activeSkillId())

        // null clears.
        reg.setActiveSkill("settings")
        reg.setActiveSkill(null)
        assertNull(prefs.activeSkillId())
    }
}
