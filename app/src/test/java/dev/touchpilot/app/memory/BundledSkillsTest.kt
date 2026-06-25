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
    fun bundledAppLaunchSkillUsesV2Metadata() {
        val markdown = readBundledSkill("app-launch")
        val skill = valid(SkillParser.parse("app-launch", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("App Launch", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue("open_app" in skill.allowedTools)
    }

    @Test
    fun bundledDeviceHelpSkillUsesV2Metadata() {
        val markdown = readBundledSkill("device-help")
        val skill = valid(SkillParser.parse("device-help", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Device Help", skill.title)
        assertEquals(SkillRisk.MEDIUM, skill.risk)
        assertTrue(skill.allowedTools.contains("open_settings_panel"))
    }

    @Test
    fun bundledWifiSkillUsesV2Metadata() {
        val markdown = readBundledSkill("wifi")
        val skill = valid(SkillParser.parse("wifi", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Wi-Fi", skill.title)
        assertEquals(SkillRisk.MEDIUM, skill.risk)
        assertTrue("open_settings_panel" in skill.allowedTools)
        assertTrue("observe_screen_context" in skill.allowedTools)
    }

    @Test
    fun bundledMediaVolumeSkillUsesV2Metadata() {
        val markdown = readBundledSkill("media-volume")
        val skill = valid(SkillParser.parse("media-volume", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Volume", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue("open_settings_panel" in skill.allowedTools)
        assertTrue("scroll" in skill.allowedTools)
    }

    @Test
    fun bundledLauncherSkillUsesV2Metadata() {
        val markdown = readBundledSkill("launcher")
        val skill = valid(SkillParser.parse("launcher", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Launcher", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue("press_home" in skill.allowedTools)
        assertTrue("open_app" in skill.allowedTools)
    }

    @Test
    fun bundledClipboardSkillUsesV2Metadata() {
        val markdown = readBundledSkill("clipboard")
        val skill = valid(SkillParser.parse("clipboard", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Clipboard", skill.title)
        assertEquals(SkillRisk.LOW, skill.risk)
        assertTrue("long_press" in skill.allowedTools)
        assertTrue("focus_input" in skill.allowedTools)
    }

    @Test
    fun bundledShareSkillUsesV2Metadata() {
        val markdown = readBundledSkill("share")
        val skill = valid(SkillParser.parse("share", markdown, knownTools))

        assertEquals(SkillFormat.V2, skill.format)
        assertEquals("Share", skill.title)
        assertEquals(SkillRisk.MEDIUM, skill.risk)
        assertTrue(skill.successCriteria.any { it.contains("approval", ignoreCase = true) })
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
