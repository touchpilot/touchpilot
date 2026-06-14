package dev.touchpilot.app.memory

import dev.touchpilot.app.tools.AndroidToolCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledSkillsTest {
    private val knownTools = AndroidToolCatalog.initialTools.map { it.name }.toSet()

    @Test
    fun bundledSettingsSkillUsesV2Metadata() {
        val markdown = readBundledSkill("settings")
        val skill = valid(SkillParser.parse("settings", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Settings", skill.title)
        assertEquals(SkillRisk.MEDIUM, skill.risk)
        assertTrue(skill.aliases.isNotEmpty())
        assertTrue(skill.examples.isNotEmpty())
        assertTrue(skill.successCriteria.isNotEmpty())
        assertTrue("open_settings_panel" in skill.allowedTools)
    }

    @Test
    fun bundledBrowserSkillUsesV2Metadata() {
        val markdown = readBundledSkill("browser")
        val skill = valid(SkillParser.parse("browser", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Browser", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue("type_text" in skill.allowedTools)
    }

    @Test
    fun bundledMessagesSkillMarksHighRisk() {
        val markdown = readBundledSkill("messages")
        val skill = valid(SkillParser.parse("messages", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Messages", skill.title)
        assertEquals(SkillRisk.HIGH, skill.risk)
        assertTrue(skill.successCriteria.any { it.contains("approval", ignoreCase = true) })
    }

    @Test
    fun bundledAppLauncherSkillUsesV2Metadata() {
        val markdown = readBundledSkill("apps")
        val skill = valid(SkillParser.parse("apps", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("App Launcher", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue(skill.aliases.isNotEmpty())
        assertTrue(skill.examples.isNotEmpty())
        assertTrue(skill.successCriteria.isNotEmpty())
        assertTrue("open_app" in skill.allowedTools)
    }

    @Test
    fun bundledNavigationSkillUsesV2Metadata() {
        val markdown = readBundledSkill("navigation")
        val skill = valid(SkillParser.parse("navigation", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Device Navigation", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue("press_home" in skill.allowedTools)
        assertTrue("press_back" in skill.allowedTools)
    }

    @Test
    fun everyBundledSkillParsesAsValidV2() {
        val ids = bundledSkillIds()
        assertTrue(ids.size >= 5, "expected at least the starter pack, found $ids")
        for (id in ids) {
            val skill = valid(SkillParser.parse(id, readBundledSkill(id), knownTools))
            assertEquals(SkillFormat.V2, skill.format, "skill '$id' must be Skills v2")
            assertEquals(id, skill.id)
            assertTrue(skill.allowedTools.isNotEmpty(), "skill '$id' must declare allowed tools")
            assertTrue(
                skill.allowedTools.all { it in knownTools },
                "skill '$id' references an unknown tool"
            )
        }
    }

    private fun bundledSkillIds(): List<String> {
        val roots = listOf(File("src/main/assets/skills"), File("app/src/main/assets/skills"))
        val root = roots.firstOrNull { it.isDirectory }
            ?: error("Missing bundled skills directory")
        return root.listFiles { file -> file.isDirectory && File(file, "SKILL.md").isFile }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    private fun readBundledSkill(id: String): String {
        val candidates = listOf(
            File("src/main/assets/skills/$id/SKILL.md"),
            File("app/src/main/assets/skills/$id/SKILL.md")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing bundled skill asset: skills/$id/SKILL.md")
        return file.readText()
    }

    private fun valid(result: SkillParseResult): Skill = when (result) {
        is SkillParseResult.Valid -> result.skill
        is SkillParseResult.Invalid -> error("expected Valid, got Invalid: ${result.errors}")
    }
}
