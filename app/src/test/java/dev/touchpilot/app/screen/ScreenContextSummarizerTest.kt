package dev.touchpilot.app.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenContextSummarizerTest {
    private val summarizer = ScreenContextSummarizer()

    @Test
    fun settingsLikeScreenSummariesAppLabelAndTopActions() {
        val context = ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            nodes = listOf(
                clickable("0.0", "Network & internet"),
                clickable("0.1", "Connected devices"),
                clickable("0.2", "Apps"),
                clickable("0.3", "Notifications"),
                clickable("0.4", "Battery")
            )
        )

        val summary = summarizer.summarize(context)
        assertTrue(summary.sentence.startsWith("I see the Settings screen."))
        assertTrue(summary.sentence.contains("Network & internet"))
        assertTrue(summary.sentence.contains("Battery"))
        assertTrue(summary.sentence.contains("go back or home"))

        val tapLabels = summary.suggestedActions.filter { it.tool == "tap" }.map { it.label }
        assertTrue("Tap Network & internet" in tapLabels)
        // 5 clickables would exceed the default suggestion cap once back/home
        // are reserved; the first few entries are kept in order.
        assertTrue(tapLabels.size in 3..5)
        // Always-safe navigation is always included.
        assertTrue(summary.suggestedActions.any { it.tool == "press_back" })
        assertTrue(summary.suggestedActions.any { it.tool == "press_home" })
        assertTrue(summary.suggestedActions.size <= 6)
    }

    @Test
    fun launcherLikeScreenWithManyItemsTruncatesToTopFew() {
        val nodes = (1..10).map { i -> clickable("0.$i", "App $i") }
        val context = ScreenContext(appLabel = "Pixel Launcher", nodes = nodes)
        val summary = summarizer.summarize(context)

        // Sentence mentions at most maxNamedItems (default 5).
        val mentioned = (1..10).count { i -> "App $i" in summary.sentence }
        assertEquals(5, mentioned)

        // Top-of-list app should appear.
        assertTrue(summary.sentence.contains("App 1"))
    }

    @Test
    fun inputFieldScreenSurfacesTypeAction() {
        val emailInput = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.INPUT,
            text = ScreenText.of("Email"),
            isInputField = true
        )
        val signIn = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Sign in"),
            clickable = true
        )
        val context = ScreenContext(
            appLabel = "Mail",
            nodes = listOf(emailInput, signIn)
        )
        val summary = summarizer.summarize(context)

        assertTrue(summary.suggestedActions.any { it.tool == "tap" && it.label == "Tap Sign in" })
        val type = summary.suggestedActions.firstOrNull { it.tool == "type_text" }
        assertTrue(type != null, "expected a type_text suggestion")
        assertTrue(type!!.label.startsWith("Type into"))
    }

    @Test
    fun passwordFieldIsNeverSurfacedInSuggestions() {
        val passwordInput = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.INPUT,
            text = ScreenText.of("Password"),
            isInputField = true,
            sensitive = true
        )
        val visibleButton = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Continue"),
            clickable = true
        )
        val context = ScreenContext(
            appLabel = "Login",
            nodes = listOf(passwordInput, visibleButton)
        )
        val summary = summarizer.summarize(context)

        // Password field must not appear as a type_text suggestion.
        assertFalse(summary.suggestedActions.any { it.tool == "type_text" })
        // And the literal "Password" label must not leak into the sentence.
        assertFalse(summary.sentence.contains("Password"))
        // Continue button is still actionable.
        assertTrue(summary.suggestedActions.any { it.label == "Tap Continue" })
    }

    @Test
    fun sensitiveClickableLabelIsFilteredFromSuggestions() {
        val emailItem = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.BUTTON,
            text = ScreenText.of("user@example.com"),
            clickable = true,
            sensitive = true
        )
        val benignItem = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Sign out"),
            clickable = true
        )
        val summary = summarizer.summarize(
            ScreenContext(appLabel = "Account", nodes = listOf(emailItem, benignItem))
        )

        assertFalse(summary.suggestedActions.any { it.label.contains("@example.com") })
        assertTrue(summary.suggestedActions.any { it.label == "Tap Sign out" })
    }

    @Test
    fun textSensitiveNodesAreFilteredEvenWithoutNodeSensitiveFlag() {
        val emailInput = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.INPUT,
            text = ScreenText.of("user@example.com"),
            isInputField = true
        )
        val emailClickable = ScreenNode(
            nodeId = "0.1",
            role = NodeRole.BUTTON,
            text = ScreenText.of("user@example.com"),
            clickable = true
        )
        val continueButton = ScreenNode(
            nodeId = "0.2",
            role = NodeRole.BUTTON,
            text = ScreenText.of("Continue"),
            clickable = true
        )

        val summary = summarizer.summarize(
            ScreenContext(appLabel = "Account", nodes = listOf(emailInput, emailClickable, continueButton))
        )

        assertFalse(summary.sentence.contains("@example.com"))
        assertFalse(summary.suggestedActions.any { it.tool == "type_text" })
        assertFalse(summary.suggestedActions.any { it.label.contains("@example.com") })
        assertTrue(summary.suggestedActions.any { it.label == "Tap Continue" })
    }

    @Test
    fun emptyContextReturnsWeakScreenMessageAndNoSuggestions() {
        val summary = summarizer.summarize(ScreenContext.Empty)
        assertEquals(ScreenContextSummarizer.WeakScreenMessage, summary.sentence)
        assertTrue(summary.suggestedActions.isEmpty())
    }

    @Test
    fun scrollableContainerAddsScrollSuggestion() {
        val list = ScreenNode(
            nodeId = "0.0",
            role = NodeRole.SCROLLABLE,
            scrollable = true
        )
        val item = clickable("0.1", "Item one")
        val summary = summarizer.summarize(
            ScreenContext(appLabel = "Feed", nodes = listOf(list, item))
        )
        assertTrue(summary.suggestedActions.any { it.tool == "scroll" })
        assertTrue(summary.sentence.contains("scroll"))
    }

    @Test
    fun windowTitleFallbackUsedWhenAppLabelMissing() {
        val summary = summarizer.summarize(
            ScreenContext(
                windowTitle = "Wi-Fi",
                nodes = listOf(clickable("0.0", "Add network"))
            )
        )
        assertTrue(summary.sentence.startsWith("I see the Wi-Fi screen."))
    }

    @Test
    fun packageNameFallbackUsedWhenLabelAndTitleMissing() {
        val summary = summarizer.summarize(
            ScreenContext(
                packageName = "com.example.app",
                nodes = listOf(clickable("0.0", "Continue"))
            )
        )
        assertTrue(summary.sentence.startsWith("I see the com.example.app screen."))
    }

    @Test
    fun maxSuggestionsCapEnforced() {
        val many = (1..20).map { clickable("0.$it", "Action $it") }
        val summary = summarizer.summarize(ScreenContext(appLabel = "Many", nodes = many))
        assertEquals(6, summary.suggestedActions.size)
        // press_back and press_home are always retained.
        assertTrue(summary.suggestedActions.any { it.tool == "press_back" })
        assertTrue(summary.suggestedActions.any { it.tool == "press_home" })
    }

    @Test
    fun navigationPairSurvivesWhenTapsInputAndScrollFillTheCap() {
        val nodes = buildList {
            (1..4).forEach { add(clickable("0.$it", "Action $it")) }
            add(
                ScreenNode(
                    nodeId = "0.input",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("Search"),
                    isInputField = true
                )
            )
            add(
                ScreenNode(
                    nodeId = "0.scroll",
                    role = NodeRole.SCROLLABLE,
                    scrollable = true
                )
            )
        }
        val summary = summarizer.summarize(ScreenContext(appLabel = "Mixed", nodes = nodes))

        // The documented invariant: back/home are always retained, even when
        // taps + type_text + scroll would otherwise fill the suggestion cap.
        assertTrue(summary.suggestedActions.any { it.tool == "press_back" })
        assertTrue(summary.suggestedActions.any { it.tool == "press_home" })
        assertTrue(summary.suggestedActions.size <= 6)
        // The distinct capabilities still surface alongside navigation.
        assertTrue(summary.suggestedActions.any { it.tool == "type_text" })
        assertTrue(summary.suggestedActions.any { it.tool == "scroll" })
    }

    private fun clickable(id: String, label: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.BUTTON,
            text = ScreenText.of(label),
            clickable = true
        )
    }
}
