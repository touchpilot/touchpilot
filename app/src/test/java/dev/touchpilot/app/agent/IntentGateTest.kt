package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
    fun classifiesPressBackAsExactPressBack() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("press back"))
        assertEquals("press_back", command.tool)
        assertEquals(emptyMap(), command.args)
    }

    @Test
    fun classifiesPressHomeAsExactPressHome() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("press home"))
        assertEquals("press_home", command.tool)
        assertEquals(emptyMap(), command.args)
    }

    @Test
    fun pressTargetOtherThanBackOrHomeRemainsTap() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("press OK"))
        assertEquals("tap", command.tool)
        assertEquals("ok", command.args["text"])
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
    fun classifiesSwipeLeftAsExactSwipe() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("swipe left"))
        assertEquals("swipe", command.tool)
        assertEquals("left", command.args["direction"])
    }

    @Test
    fun classifiesSwipeRightAsExactSwipe() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("Swipe right"))
        assertEquals("swipe", command.tool)
        assertEquals("right", command.args["direction"])
    }

    @Test
    fun classifiesSwipeUpAsExactSwipe() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("swipe up"))
        assertEquals("swipe", command.tool)
        assertEquals("up", command.args["direction"])
    }

    @Test
    fun classifiesSwipeDownAsExactSwipe() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("swipe down"))
        assertEquals("swipe", command.tool)
        assertEquals("down", command.args["direction"])
    }

    @Test
    fun classifiesSwipeWithFillerAsExactSwipe() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("swipe to the left"))
        assertEquals("swipe", command.tool)
        assertEquals("left", command.args["direction"])
    }

    @Test
    fun swipeWithoutDirectionFallsThroughToLocalModel() {
        // "swipe" with no recognizable direction is ambiguous; the deterministic
        // gate should not invent one and instead defer to the local model.
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("swipe across the carousel"))
    }

    @Test
    fun tapTargetContainingSwipeIsTap() {
        // "swipe" appearing inside a tap target should not steal the route.
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("tap swipe tutorial"))
        assertEquals("tap", command.tool)
        assertEquals("swipe tutorial", command.args["text"])
    }

    @Test
    fun classifiesOpenSettingsAsExactOpenApp() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("Open Settings"))
        assertEquals("open_app", command.tool)
        assertEquals("settings", command.args["target"])
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
        assertEquals("ok", command.args["text"])
    }

    @Test
    fun classifiesLongPressTextAsExactLongPress() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("long-press App info"))
        assertEquals("long_press", command.tool)
        assertEquals("app info", command.args["text"])
    }

    @Test
    fun classifiesRecentAppsAsExactRecentApps() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("show recent apps"))
        assertEquals("recent_apps", command.tool)
        assertEquals(emptyMap(), command.args)
    }

    @Test
    fun classifiesAppSwitcherAsExactRecentApps() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("open the app switcher"))
        assertEquals("recent_apps", command.tool)
    }

    @Test
    fun classifiesSwitchAppsAsExactRecentApps() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("switch apps"))
        assertEquals("recent_apps", command.tool)
    }

    @Test
    fun recentsPhraseTakesPrecedenceOverOpenApp() {
        // "open recents" contains the open/launch keyword; recents detection
        // must win so it does not become open_app with target "recents".
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("open recents"))
        assertEquals("recent_apps", command.tool)
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
    fun classifiesKnownSkillByAliasMatch() {
        val skill = Skill(
            id = "settings",
            title = "Settings Skill",
            markdown = "Use Android Settings screens carefully.",
            allowedTools = setOf("observe_screen", "open_settings_panel"),
            aliases = listOf("wi-fi", "network settings")
        )

        val decision = gate.classify("Help me connect to wifi", skills = listOf(skill))
        val matched = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("settings", matched.skillId)
        assertContains(matched.reason, "alias")
        assertTrue(matched.confidence >= 0.65)
    }

    @Test
    fun classifiesKnownSkillByExamplePhraseMatch() {
        val skill = Skill(
            id = "settings",
            title = "Settings Skill",
            markdown = "Use Android Settings screens carefully.",
            allowedTools = setOf("observe_screen", "open_settings_panel"),
            examples = listOf("connect to Wi-Fi")
        )

        val decision = gate.classify("Can you help me connect to wifi?", skills = listOf(skill))
        val matched = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("settings", matched.skillId)
        assertContains(matched.reason, "example")
    }

    @Test
    fun classifiesKnownSkillByExampleKeywordOverlap() {
        val skill = Skill(
            id = "messages",
            title = "Messages Skill",
            markdown = "Draft messages before asking the user to send.",
            allowedTools = setOf("observe_screen", "type_text"),
            examples = listOf("draft a text message")
        )

        val decision = gate.classify("Please draft a text", skills = listOf(skill))
        val matched = assertIs<IntentDecision.KnownSkill>(decision)
        assertEquals("messages", matched.skillId)
        assertContains(matched.reason, "example keywords")
    }

    @Test
    fun singleKeywordOverlapDoesNotSelectSkill() {
        val skill = Skill(
            id = "messages",
            title = "Messages Skill",
            markdown = "Draft messages before asking the user to send.",
            allowedTools = setOf("observe_screen", "type_text"),
            aliases = listOf("text message"),
            examples = listOf("draft a text message")
        )

        val decision = gate.classify("Read text on screen", skills = listOf(skill))
        assertIs<IntentDecision.LocalModelNeeded>(decision)
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
    fun exactCommandTakesPrecedenceOverAliasAndExampleMatches() {
        val skill = Skill(
            id = "navigation",
            title = "Navigation",
            markdown = "",
            allowedTools = emptySet(),
            aliases = listOf("back", "scroll"),
            examples = listOf("scroll down", "go home")
        )

        assertIs<IntentDecision.ExactCommand>(gate.classify("scroll down", skills = listOf(skill)))
        assertIs<IntentDecision.ExactCommand>(gate.classify("go home", skills = listOf(skill)))
    }

    @Test
    fun directToolPhraseTakesPrecedenceOverSkillMatch() {
        val skill = Skill(
            id = "buttons",
            title = "Button Help",
            markdown = "",
            allowedTools = emptySet(),
            aliases = listOf("ok button"),
            examples = listOf("tap ok button")
        )

        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("tap OK", skills = listOf(skill)))
        assertEquals("tap", command.tool)
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

    @Test
    fun openAppWithTargetContainingBackIsOpenApp() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("open back button"))
        assertEquals("open_app", command.tool)
        assertEquals("back button", command.args["target"])
    }

    @Test
    fun openAppWithTargetContainingHomeIsOpenApp() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("open home assistant"))
        assertEquals("open_app", command.tool)
        assertEquals("home assistant", command.args["target"])
    }

    @Test
    fun tapTargetContainingHomeIsTap() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("tap home shortcut"))
        assertEquals("tap", command.tool)
        assertEquals("home shortcut", command.args["text"])
    }

    @Test
    fun tapTargetContainingScrollIsTap() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("tap scroll bar"))
        assertEquals("tap", command.tool)
        assertEquals("scroll bar", command.args["text"])
    }

    @Test
    fun tapTargetContainingBackIsTap() {
        val command = assertIs<IntentDecision.ExactCommand>(gate.classify("tap back arrow"))
        assertEquals("tap", command.tool)
        assertEquals("back arrow", command.args["text"])
    }

    @Test
    fun pressBackPhraseRequiresWordBoundary() {
        // "feedback" contains the substring "back" but is not a navigation
        // intent. Without a word boundary the bare check would misroute it.
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("give feedback"))
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("comeback"))
    }

    @Test
    fun pressHomePhraseRequiresWordBoundary() {
        // "homemade" contains the substring "home" but is not a navigation
        // intent. The bare check would misroute it to press_home.
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("buy homemade pizza"))
    }

    @Test
    fun scrollPhraseRequiresWordBoundary() {
        // "scrollbar" contains the substring "scroll" but is not a navigation
        // intent. The bare check would misroute it to the scroll tool.
        assertIs<IntentDecision.LocalModelNeeded>(gate.classify("the scrollbar is broken"))
    }
}
