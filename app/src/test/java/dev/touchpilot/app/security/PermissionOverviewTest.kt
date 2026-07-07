package dev.touchpilot.app.security

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillRisk
import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PermissionOverviewTest {
    @Test
    fun hostStatusLineSummarizesSeparateSurfaces() {
        val summary = PermissionOverview.buildSummary(
            accessibilityConnected = true,
            skills = listOf(sampleSkill("s1"), sampleSkill("s2")),
            disabledSkillIds = setOf("s2"),
            extensionGrants = listOf(
                ExternalCapabilityPermissionGrant(
                    target = ExternalCapabilityTarget(
                        kind = ExternalCapabilityKind.LOCAL_EXTENSION,
                        endpoint = "content://demo",
                        name = "demo_tool",
                    ),
                    allowedActions = setOf(ExternalCapabilityAction.CALL_TOOL),
                )
            ),
            revokedToolCount = 1,
        )

        val line = PermissionOverview.hostStatusLine(summary)

        assertTrue(line.contains("Accessibility: connected"))
        assertTrue(line.contains("Skills: 1 enabled"))
        assertTrue(line.contains("External: 1 granted"))
        assertTrue(line.contains("Tools: 1 revoked"))
    }

    @Test
    fun androidToolEntriesUseReadableLabels() {
        val entries = PermissionOverview.androidToolEntries(
            tools = listOf(
                ToolSpec(
                    name = "tap",
                    description = "Tap a visible UI target.",
                    risk = ToolRisk.MEDIUM,
                    arguments = emptyMap(),
                )
            ),
            revokedTools = setOf("tap"),
            accessibilityConnected = true,
        )

        assertEquals(1, entries.size)
        assertEquals(PermissionCategory.ANDROID_TOOL, entries[0].category)
        assertEquals("Revoked by User", entries[0].grantedBy)
        assertTrue(entries[0].label.contains("Tap"))
    }

    private fun sampleSkill(id: String): Skill {
        return Skill(
            id = id,
            title = "Skill $id",
            markdown = "# Skill $id",
            description = "Demo skill",
            risk = SkillRisk.LOW,
            allowedTools = setOf("observe_screen"),
        )
    }
}
