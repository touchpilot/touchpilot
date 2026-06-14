package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the alias/example/keyword skill matching added for issue #225. Uses
 * fixtures that mirror the bundled Settings (medium), Browser (low), and
 * Messages (high) skills.
 */
class IntentGateSkillMatchingTest {
    private val gate = IntentGate()

    private val settings = Skill(
        id = "settings",
        title = "Settings",
        markdown = "",
        allowedTools = setOf("observe_screen_context", "open_settings_panel", "tap"),
        description = "Navigate and inspect Android Settings screens safely.",
        risk = SkillRisk.MEDIUM,
        aliases = listOf("settings", "android settings", "wi-fi settings", "wifi settings", "bluetooth settings"),
        examples = listOf("open Wi-Fi settings", "show Bluetooth settings", "go to notification settings")
    )

    private val browser = Skill(
        id = "browser",
        title = "Browser",
        markdown = "",
        allowedTools = setOf("observe_screen_context", "open_app", "tap", "type_text"),
        description = "Open pages, search the web, and inspect visible browser results.",
        risk = SkillRisk.LOW,
        aliases = listOf("browser", "chrome", "web search", "search the web"),
        examples = listOf("open google.com", "search for touchpilot android", "show my open browser tabs")
    )

    private val messages = Skill(
        id = "messages",
        title = "Messages",
        markdown = "",
        allowedTools = setOf("observe_screen_context", "tap", "type_text"),
        description = "Draft and review SMS or messaging actions with explicit user approval before sending.",
        risk = SkillRisk.HIGH,
        aliases = listOf("messages", "sms", "text message", "send a text"),
        examples = listOf("draft a text to Alex", "open messages and compose a reply", "prepare an SMS but do not send it")
    )

    private val all = listOf(settings, browser, messages)

    @Test
    fun matchesSkillByAliasPhrase() {
        val decision = gate.classify("I want to use chrome", all)
        val match = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("browser", match.skillId)
        assertEquals(0.9, match.confidence)
        assertTrue(match.reason.contains("alias"))
    }

    @Test
    fun matchesSkillByTitlePhraseWithHighConfidence() {
        val decision = gate.classify("take me to settings", all)
        val match = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("settings", match.skillId)
        assertEquals(1.0, match.confidence)
    }

    @Test
    fun matchesNaturalWifiRequestByKeyword() {
        // The motivating example from the issue: a natural request with no exact
        // command and no settings panel phrase should still find the skill.
        val decision = gate.classify("help me connect to wifi", all)
        val match = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("settings", match.skillId)
        assertTrue(match.confidence in 0.5..0.8, "confidence=${match.confidence}")
    }

    @Test
    fun matchesHighRiskMessagesByExamplePhrase() {
        // A high-risk skill can still match — but only via an explicit phrase
        // it declares (here, an example), not loose keyword overlap.
        val decision = gate.classify("draft a text to Alex", all)
        val match = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("messages", match.skillId)
        assertTrue(match.confidence >= 0.8)
    }

    @Test
    fun highRiskSkillDoesNotMatchOnLooseKeywordAlone() {
        // "compose" is a Messages example keyword, but Messages is high-risk, so
        // a single keyword with no phrase must not route into it.
        val decision = gate.classify("compose something for me", all)
        assertIs<IntentDecision.LocalModelNeeded>(decision)
    }

    @Test
    fun exactCommandStillBeatsSkillMatching() {
        // "scroll" is an exact command and must win even when skills are present.
        val decision = gate.classify("scroll down", all)
        assertIs<IntentDecision.ExactCommand>(decision)
    }

    @Test
    fun aliasMatchingRespectsWordBoundaries() {
        // "chrome" must not be found inside "synchrome"; nothing else matches, so
        // the request defers to the local model.
        val decision = gate.classify("synchrome the data layer", all)
        assertIs<IntentDecision.LocalModelNeeded>(decision)
    }

    @Test
    fun unrelatedRequestDefersToLocalModel() {
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("tell me a joke", all))
    }

    @Test
    fun noSkillsMeansNoSkillMatch() {
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("help me connect to wifi", emptyList()))
    }

    @Test
    fun prefersHigherConfidenceSkillWhenSeveralCouldMatch() {
        // "search the web in my browser" hits the Browser alias "browser"
        // (phrase) over any weaker keyword match.
        val decision = gate.classify("search the web in my browser", all)
        val match = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("browser", match.skillId)
        assertTrue(match.confidence >= 0.9)
    }
}
