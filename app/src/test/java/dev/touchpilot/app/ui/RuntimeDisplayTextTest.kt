package dev.touchpilot.app.ui

import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.localinference.LocalModelStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeDisplayTextTest {

    private val availableStatus = LocalModelStatus(
        available = true,
        runtime = "LiteRT",
        modelAsset = "models/command_router/model.tflite",
        version = "tiny-router-1",
        message = "LiteRT command model loaded."
    )

    private val unavailableStatus = LocalModelStatus(
        available = false,
        runtime = "LiteRT",
        modelAsset = "models/command_router/model.tflite",
        version = "tiny-router-1",
        message = "LiteRT model asset is missing; the run will stop with a final answer."
    )

    @Test
    fun localModelModeLabelDoesNotMentionRouterFallback() {
        assertEquals("Local LiteRT model", AgentProviderMode.LOCAL_MODEL.label())
        assertFalse(AgentProviderMode.LOCAL_MODEL.description().contains("router"))
    }

    @Test
    fun routerModeSettingsChipShowsLocalOnly() {
        val indicator = RuntimeIndicator(AgentProviderMode.LOCAL_ROUTER, unavailableStatus)
        assertEquals("local only", indicator.settingsChipText())
        assertTrue(indicator.settingsChipAccent())
    }

    @Test
    fun modelModeSettingsChipReflectsAvailability() {
        val ready = RuntimeIndicator(AgentProviderMode.LOCAL_MODEL, availableStatus)
        assertEquals("model ready", ready.settingsChipText())
        assertTrue(ready.settingsChipAccent())

        val missing = RuntimeIndicator(AgentProviderMode.LOCAL_MODEL, unavailableStatus)
        assertEquals("model unavailable", missing.settingsChipText())
        assertFalse(missing.settingsChipAccent())
    }

    @Test
    fun chatContextStripShowsLocalOnlyAndActivePath() {
        val indicator = RuntimeIndicator(AgentProviderMode.LOCAL_ROUTER, availableStatus)
        val strip = indicator.chatContextStrip("No skill selected")
        assertContains(strip, "Core: local only")
        assertContains(strip, "Deterministic router")
        assertContains(strip, "Skill: No skill selected")
    }

    @Test
    fun settingsDetailDistinguishesCoreRuntimeFromIntegrations() {
        val indicator = RuntimeIndicator(AgentProviderMode.LOCAL_ROUTER, availableStatus)
        val body = indicator.settingsDetailBody()
        assertContains(body, "no cloud is required for routing")
        assertContains(body, "Optional integrations")
        assertContains(body, "not used in router mode")
    }

    @Test
    fun modelModeWelcomeDetailMentionsUnavailableModel() {
        val indicator = RuntimeIndicator(AgentProviderMode.LOCAL_MODEL, unavailableStatus)
        assertContains(indicator.welcomeDetail(), "LiteRT is unavailable")
    }

    @Test
    fun shortLineIncludesVersionWhenAvailable() {
        assertEquals("LiteRT model ready (tiny-router-1)", availableStatus.shortLine())
    }
}
