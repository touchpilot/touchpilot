package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.SkillRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SkillUseCardModelTest {
    @Test
    fun formatsShortAllowedToolList() {
        assertEquals(
            "observe_screen, open_app",
            SkillUseCardModel.formatAllowedToolsSummary(setOf("open_app", "observe_screen"))
        )
    }

    @Test
    fun formatsLongAllowedToolListWithCount() {
        assertEquals(
            "4 tools: alpha, beta, delta…",
            SkillUseCardModel.formatAllowedToolsSummary(setOf("alpha", "beta", "gamma", "delta"))
        )
    }

    @Test
    fun buildsFromSkillActiveEvent() {
        val event = AgentEvent.SkillActive(
            skillId = "settings",
            title = "Settings",
            risk = SkillRisk.MEDIUM,
            allowedTools = setOf("open_app", "tap"),
            activationSource = SkillActivationSource.MATCHED,
            reason = "task mentions skill title"
        )

        val card = SkillUseCardModel.from(event)

        assertEquals("settings", card.skillId)
        assertEquals("Settings", card.title)
        assertEquals(SkillRisk.MEDIUM, card.risk)
        assertEquals(SkillActivationSource.MATCHED, card.activationSource)
        assertEquals("task mentions skill title", card.reason)
        assertEquals("open_app, tap", card.allowedToolsSummary)
    }

    @Test
    fun skillActiveEventSerializesToJson() {
        val event = AgentEvent.SkillActive(
            skillId = "settings",
            title = "Settings",
            risk = SkillRisk.LOW,
            allowedTools = setOf("open_app"),
            activationSource = SkillActivationSource.MANUAL,
            reason = "Active skill selected in Settings"
        )

        val json = event.toJson()
        assertEquals("skill_active", json.getString("type"))
        val payload = json.getJSONObject("payload")
        assertEquals("settings", payload.getString("skill_id"))
        assertEquals("Settings", payload.getString("title"))
        assertEquals("manual", payload.getString("activation_source"))
    }
}
