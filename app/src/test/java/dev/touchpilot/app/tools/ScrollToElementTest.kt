package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollToElementTest {
    @Test
    fun scrollToElementIsRegisteredAsMediumRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("scroll_to_element"))
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertTrue(spec.requiredArguments.isEmpty())
        assertTrue(spec.arguments.containsKey("text"))
        assertTrue(spec.arguments.containsKey("max_scrolls"))
        assertTrue(spec.arguments.containsKey("target_node_id"))
    }

    @Test
    fun validateRequiresAtLeastOneFilter() {
        assertEquals(
            "scroll_to_element requires at least one filter: " +
                "text, content_description, node_id, or class_name",
            AndroidToolCatalog.validate("scroll_to_element", mapOf("direction" to "forward"))
        )
    }

    @Test
    fun validateAcceptsFilterWithOptionalArgs() {
        assertNull(
            AndroidToolCatalog.validate(
                "scroll_to_element",
                mapOf("text" to "Sign out", "direction" to "backward", "max_scrolls" to "5")
            )
        )
    }

    @Test
    fun validateRejectsBadDirection() {
        assertEquals(
            "Invalid scroll direction: sideways",
            AndroidToolCatalog.validate("scroll_to_element", mapOf("text" to "x", "direction" to "sideways"))
        )
    }

    @Test
    fun validateRejectsBadMatchMode() {
        assertEquals(
            "scroll_to_element match must be one of: exact, contains, semantic",
            AndroidToolCatalog.validate("scroll_to_element", mapOf("text" to "x", "match" to "fuzzy"))
        )
    }

    @Test
    fun validateRejectsOutOfRangeMaxScrolls() {
        val message = "max_scrolls must be an integer between 1 and 30"
        assertEquals(message, AndroidToolCatalog.validate("scroll_to_element", mapOf("text" to "x", "max_scrolls" to "0")))
        assertEquals(message, AndroidToolCatalog.validate("scroll_to_element", mapOf("text" to "x", "max_scrolls" to "31")))
        assertEquals(message, AndroidToolCatalog.validate("scroll_to_element", mapOf("text" to "x", "max_scrolls" to "lots")))
    }

    @Test
    fun validateRejectsMalformedContainerBounds() {
        assertEquals(
            "target_bounds must be left,top,right,bottom",
            AndroidToolCatalog.validate("scroll_to_element", mapOf("text" to "x", "target_bounds" to "nope"))
        )
    }

    @Test
    fun maxScrollsIsBoundedWithDefault() {
        assertEquals(ScrollToElement.DefaultMaxScrolls, ScrollToElement.maxScrolls(emptyMap()))
        assertEquals(1, ScrollToElement.maxScrolls(mapOf("max_scrolls" to "1")))
        assertEquals(30, ScrollToElement.maxScrolls(mapOf("max_scrolls" to "1000")))
    }

    @Test
    fun directionDefaultsToForward() {
        assertEquals("forward", ScrollToElement.direction(emptyMap()))
        assertEquals("backward", ScrollToElement.direction(mapOf("direction" to "BACKWARD")))
    }

    @Test
    fun scrollArgsKeepDirectionAndContainerButDropElementQuery() {
        val scrollArgs = ScrollToElement.scrollArgs(
            mapOf(
                "text" to "Sign out",
                "class_name" to "android.widget.Button",
                "direction" to "backward",
                "target_node_id" to "0.2",
            )
        )
        assertEquals("backward", scrollArgs["direction"])
        assertEquals("0.2", scrollArgs["target_node_id"])
        assertFalse(scrollArgs.containsKey("text"))
        assertFalse(scrollArgs.containsKey("class_name"))
    }

    @Test
    fun queryMatchesPresentNodeViaSharedMatcher() {
        val screen = screen(nodes = listOf(button("0", "Sign out")))
        val matches = FindElementMatcher().match(
            screen,
            ScrollToElement.queryFromArgs(mapOf("text" to "Sign out"))
        )
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun logArgsDoesNotLeakRawQueryText() {
        val logArgs = ScrollToElement.logArgs(mapOf("text" to "secret-token-value"))
        assertFalse(logArgs.contains("secret-token-value"))
        assertTrue(logArgs.contains("text_length=18"))
        assertTrue(logArgs.contains("max_scrolls=8"))
    }

    @Test
    fun retryPolicyTreatsScrollToElementAsSingleAttempt() {
        val config = AndroidToolRetryPolicy().configFor("scroll_to_element")
        assertEquals(1, config.maxAttempts)
        assertFalse(config.retryable)
    }

    private fun screen(nodes: List<ScreenNode>): ScreenContext {
        return ScreenContext(
            appLabel = "TouchPilot",
            packageName = "dev.touchpilot.app",
            nodes = nodes,
        )
    }

    private fun button(id: String, text: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.BUTTON,
            text = ScreenText.of(text),
            bounds = NodeBounds(0, 0, 100, 100),
            clickable = true,
        )
    }
}
