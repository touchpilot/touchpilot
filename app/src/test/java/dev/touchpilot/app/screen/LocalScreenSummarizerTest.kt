package dev.touchpilot.app.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalScreenSummarizerTest {

    @Test
    fun settingsLikeScreenSummarizesAppAndVisibleClickables() {
        val context = ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Network & internet",
            nodes = listOf(
                clickable("wifi", "Wi-Fi"),
                clickable("mobile", "Mobile network"),
                clickable("hotspot", "Hotspot & tethering")
            )
        )

        val result = LocalScreenSummarizer.summarize(context)

        assertFalse(result.sensitiveScreen)
        assertTrue(result.summary.contains("Settings"), "summary should name the app: ${result.summary}")
        assertTrue(
            result.summary.contains("Wi-Fi") &&
                result.summary.contains("Mobile network") &&
                result.summary.contains("Hotspot & tethering"),
            "summary should mention visible actions: ${result.summary}"
        )

        val tapActions = result.suggestedActions.filter { it.tool == "tap" }
        assertEquals(3, tapActions.size, "one tap suggestion per visible clickable")
        assertEquals(
            setOf("Wi-Fi", "Mobile network", "Hotspot & tethering"),
            tapActions.mapNotNull { it.args["text"] }.toSet()
        )

        val tools = result.suggestedActions.map { it.tool }
        assertTrue("press_back" in tools, "press_back should be suggested")
    }

    @Test
    fun launcherLikeScreenSuggestsOpeningVisibleApps() {
        val context = ScreenContext(
            appLabel = "Launcher",
            packageName = "com.android.launcher",
            windowTitle = null,
            nodes = listOf(
                clickable("camera", "Camera"),
                clickable("chrome", "Chrome"),
                clickable("messages", "Messages")
            )
        )

        val result = LocalScreenSummarizer.summarize(context)

        assertFalse(result.sensitiveScreen)
        assertTrue(result.summary.contains("Camera"))
        assertTrue(result.summary.contains("Chrome"))
        val tapTargets = result.suggestedActions
            .filter { it.tool == "tap" }
            .mapNotNull { it.args["text"] }
            .toSet()
        assertEquals(setOf("Camera", "Chrome", "Messages"), tapTargets)
    }

    @Test
    fun inputFieldScreenSuggestsTyping() {
        val context = ScreenContext(
            appLabel = "Browser",
            windowTitle = "New tab",
            nodes = listOf(
                ScreenNode(
                    nodeId = "address",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("Search or type URL"),
                    isInputField = true,
                    focused = true
                ),
                clickable("go", "Go")
            )
        )

        val result = LocalScreenSummarizer.summarize(context)

        assertFalse(result.sensitiveScreen)
        val typeActions = result.suggestedActions.filter { it.tool == "type_text" }
        assertEquals(1, typeActions.size, "should suggest typing into the visible input")
        assertTrue(
            result.summary.contains("input", ignoreCase = true) ||
                result.summary.contains("type", ignoreCase = true),
            "summary should mention input/typing: ${result.summary}"
        )
    }

    @Test
    fun emptyContextProducesFallbackSummary() {
        val result = LocalScreenSummarizer.summarize(ScreenContext.Empty)

        assertFalse(result.sensitiveScreen)
        assertTrue(
            result.summary.contains("don't", ignoreCase = true) ||
                result.summary.contains("no clear", ignoreCase = true) ||
                result.summary.contains("can't see", ignoreCase = true),
            "fallback summary should acknowledge missing context: ${result.summary}"
        )
        val tools = result.suggestedActions.map { it.tool }
        assertTrue("observe_screen" in tools, "should suggest re-observing the screen")
        assertTrue("press_home" in tools)
    }

    @Test
    fun weakContextWithUnlabeledClickablesStillReturnsSafeSuggestions() {
        val context = ScreenContext(
            appLabel = null,
            windowTitle = null,
            nodes = listOf(
                ScreenNode(nodeId = "n1", clickable = true, text = ScreenText.Empty),
                ScreenNode(nodeId = "n2", clickable = true, text = ScreenText.Empty)
            )
        )

        val result = LocalScreenSummarizer.summarize(context)

        // No tap-by-text suggestions when there is no readable label.
        assertTrue(
            result.suggestedActions.none { it.tool == "tap" && it.args.containsKey("text") },
            "should not synthesize tap-by-text without a label"
        )
        assertTrue("press_back" in result.suggestedActions.map { it.tool })
    }

    @Test
    fun sensitiveScreenProducesConservativeSummaryAndNoTapByText() {
        val context = ScreenContext(
            appLabel = "Bank",
            windowTitle = "Sign in",
            nodes = listOf(
                ScreenNode(
                    nodeId = "pw",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("password"),
                    isInputField = true,
                    sensitive = true
                ),
                clickable("submit", "Sign in")
            )
        )

        val result = LocalScreenSummarizer.summarize(context)

        assertTrue(result.sensitiveScreen, "should flag the screen as sensitive")
        assertFalse(
            result.summary.contains("password", ignoreCase = true),
            "summary must avoid sensitive labels: ${result.summary}"
        )
        // Conservative: no tap-by-text suggestions on sensitive screens.
        assertTrue(
            result.suggestedActions.none { it.tool == "tap" },
            "should not suggest tap targets on sensitive screen"
        )
        assertTrue(
            result.suggestedActions.none { it.tool == "type_text" },
            "should not suggest typing on sensitive screen"
        )
        val tools = result.suggestedActions.map { it.tool }
        assertTrue("press_back" in tools)
    }

    @Test
    fun scrollableContentProducesScrollSuggestion() {
        val context = ScreenContext(
            appLabel = "Settings",
            windowTitle = "Apps",
            nodes = listOf(
                ScreenNode(nodeId = "list", role = NodeRole.SCROLLABLE, scrollable = true),
                clickable("calc", "Calculator")
            )
        )

        val result = LocalScreenSummarizer.summarize(context)
        val scrolls = result.suggestedActions.filter { it.tool == "scroll" }
        assertTrue(scrolls.any { it.args["direction"] == "forward" }, "should suggest scroll forward")
    }

    @Test
    fun concreteSuggestedActionsValidateAgainstToolCatalog() {
        val context = ScreenContext(
            appLabel = "Settings",
            windowTitle = "Network & internet",
            nodes = listOf(
                clickable("wifi", "Wi-Fi"),
                ScreenNode(nodeId = "list", role = NodeRole.SCROLLABLE, scrollable = true)
            )
        )

        val result = LocalScreenSummarizer.summarize(context)

        // Concrete actions (those marked ready-to-fire) must validate against the
        // existing tool catalog so the agent/UI path can hand them straight back
        // to the runtime without re-validating the schema.
        val concrete = result.suggestedActions.filter { it.readyToFire }
        assertTrue(concrete.isNotEmpty(), "expected at least one ready-to-fire action")
        for (action in concrete) {
            val error = dev.touchpilot.app.tools.AndroidToolCatalog.validate(action.tool, action.args)
            assertEquals(null, error, "ready-to-fire $action failed catalog validation: $error")
        }
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
