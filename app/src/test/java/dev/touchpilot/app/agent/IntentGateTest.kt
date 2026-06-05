package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class IntentGateTest {
    private val gate = IntentGate()

    @Test
    fun classifiesGoBackAsExactPressBack() {
        val decision = gate.classify("Go back")
        val command = assertIs<IntentDecision.ExactCommand>(decision)
        assertEquals("press_back", command.tool)
        assertEquals(emptyMap(), command.args)
    }

    @Test
    fun classifiesGoHomeAsExactPressHome() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("go home"))
        assertEquals("press_home", command.tool)
    }

    @Test
    fun classifiesScrollUpAsBackwardScroll() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("scroll up"))
        assertEquals("scroll", command.tool)
        assertEquals("backward", command.args["direction"])
    }

    @Test
    fun classifiesScrollAsForwardScroll() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("scroll down"))
        assertEquals("scroll", command.tool)
        assertEquals("forward", command.args["direction"])
    }

    @Test
    fun classifiesOpenSettingsAsExactOpenApp() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("Open Settings"))
        assertEquals("open_app", command.tool)
        assertEquals("Settings", command.args["target"])
    }

    @Test
    fun classifiesOpenWifiSettingsAsSettingsPanel() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("Open Wi-Fi settings"))
        assertEquals("open_settings_panel", command.tool)
        assertEquals("wifi", command.args["panel"])
    }

    @Test
    fun classifiesLaunchAppAsExactOpenApp() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("launch maps"))
        assertEquals("open_app", command.tool)
        assertEquals("maps", command.args["target"])
    }

    @Test
    fun classifiesTapTextAsExactTap() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("tap OK"))
        assertEquals("tap", command.tool)
        assertEquals("OK", command.args["text"])
    }

    @Test
    fun classifiesLongPressTextAsExactLongPress() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("long-press App info"))
        assertEquals("long_press", command.tool)
        assertEquals("App info", command.args["text"])
    }

    @Test
    fun classifiesPaymentAsUnsafe() {
        val unsafe = assertIs<IntentDecision.UnsafeRequest>(gate.classify("Send this payment"))
        assertEquals("payments are blocked", unsafe.reason)
    }

    @Test
    fun classifiesWireTransferAsUnsafe() {
        val unsafe = assertIs<IntentDecision.UnsafeRequest>(gate.classify("Set up a wire transfer"))
        assertEquals("banking or financial actions are blocked", unsafe.reason)
    }

    @Test
    fun classifiesFactoryResetAsUnsafe() {
        assertIs<IntentDecision.UnsafeRequest>(gate.classify("Factory reset the phone"))
    }

    @Test
    fun unsafeWinsOverExactCommandWhenBothMatch() {
        // "pay " unsafe check should fire before the "open" exact command.
        val decision = gate.classify("Open the bank app to pay rent")
        assertIs<IntentDecision.UnsafeRequest>(decision)
    }

    @Test
    fun classifiesAmbiguousReferenceAsClarification() {
        val decision = gate.classify("Do the thing I usually do here")
        val clarification = assertIs<IntentDecision.ClarificationNeeded>(decision)
        assertEquals(
            "Can you describe what you would like me to do more specifically?",
            clarification.clarification
        )
    }

    @Test
    fun classifiesBlankInputAsClarification() {
        val decision = gate.classify("   ")
        assertIs<IntentDecision.ClarificationNeeded>(decision)
    }

    @Test
    fun classifiesUnmatchedRequestAsLocalModelNeeded() {
        // No exact-command keyword, no skill, no unsafe phrase, no ambiguous reference.
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("Find the Wi-Fi toggle"))
    }

    @Test
    fun classifiesKnownSkillByTitleMatch() {
        val skill = Skill(
            id = "wifi",
            title = "Wi-Fi",
            markdown = "Toggle Wi-Fi from Quick Settings",
            allowedTools = setOf("observe_screen", "tap")
        )
        val decision = gate.classify("Help me with wi-fi", skills = listOf(skill))
        val matched = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("wifi", matched.skillId)
        assertEquals("Wi-Fi", matched.skillTitle)
    }

    @Test
    fun exactCommandTakesPrecedenceOverSkill() {
        // "go back" is an exact command; a skill titled "back" should not steal
        // the route.
        val skill = Skill(
            id = "back",
            title = "back",
            markdown = "",
            allowedTools = emptySet()
        )
        val decision = gate.classify("Go back", skills = listOf(skill))
        assertIs<IntentDecision.ExactCommand>(decision)
    }

    @Test
    fun classifiesWhatScreenAmIOnAsScreenInquiry() {
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("What screen am I on?"))
    }

    @Test
    fun classifiesWhatCanYouDoHereAsScreenInquiry() {
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("what can you do here"))
    }

    @Test
    fun classifiesWhatButtonsAreVisibleAsScreenInquiry() {
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("What buttons are visible?"))
    }

    @Test
    fun classifiesSummarizeThisScreenAsScreenInquiry() {
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("Summarize this screen"))
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("summarise the screen"))
    }

    @Test
    fun classifiesSuggestActionsForThisScreenAsScreenInquiry() {
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("Suggest actions for this screen"))
    }

    @Test
    fun whatCanYouDoWithoutHereStaysOutOfScreenInquiry() {
        // "what can you do" (without "here") is handled by ConversationalGate
        // as the generic help reply; IntentGate should not claim it.
        val decision = gate.classify("what can you do")
        assertIs<IntentDecision.LocalModelNeeded>(decision)
    }

    @Test
    fun screenInquiryWinsOverExactCommandWhenBothCouldMatch() {
        // "what can i do here" has no exact command, but a phrasing like
        // "summarize the home screen" contains "home" which would otherwise
        // trip the press_home exact command. Screen inquiry runs first.
        assertIs<IntentDecision.ScreenInquiry>(gate.classify("summarize the home screen"))
    }
}
