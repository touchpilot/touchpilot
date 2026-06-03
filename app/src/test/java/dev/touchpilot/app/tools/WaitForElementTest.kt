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

class WaitForElementTest {
    @Test
    fun waitForElementIsRegisteredAsLowRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("wait_for_element"))
        assertEquals(ToolRisk.LOW, spec.risk)
        assertEquals(
            setOf("text", "content_description", "node_id", "class_name", "match", "timeout_ms"),
            spec.arguments.keys
        )
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validateRequiresAtLeastOneFilter() {
        assertEquals(
            "wait_for_element requires at least one filter: " +
                "text, content_description, node_id, or class_name",
            AndroidToolCatalog.validate("wait_for_element", emptyMap())
        )
        assertEquals(
            "wait_for_element requires at least one filter: " +
                "text, content_description, node_id, or class_name",
            AndroidToolCatalog.validate("wait_for_element", mapOf("match" to "contains"))
        )
    }

    @Test
    fun validateAcceptsEachFilter() {
        assertNull(AndroidToolCatalog.validate("wait_for_element", mapOf("text" to "Continue")))
        assertNull(AndroidToolCatalog.validate("wait_for_element", mapOf("content_description" to "Submit")))
        assertNull(AndroidToolCatalog.validate("wait_for_element", mapOf("node_id" to "0.1")))
        assertNull(AndroidToolCatalog.validate("wait_for_element", mapOf("class_name" to "android.widget.Button")))
    }

    @Test
    fun validateRejectsUnknownMatchMode() {
        assertEquals(
            "wait_for_element match must be one of: exact, contains, semantic",
            AndroidToolCatalog.validate("wait_for_element", mapOf("text" to "Continue", "match" to "fuzzy"))
        )
    }

    @Test
    fun validateRejectsNonNumericTimeout() {
        assertEquals(
            "timeout_ms must be a number",
            AndroidToolCatalog.validate("wait_for_element", mapOf("text" to "Continue", "timeout_ms" to "soon"))
        )
    }

    @Test
    fun timeoutIsBoundedToSafeRange() {
        assertEquals(WaitForElement.DefaultTimeoutMs, WaitForElement.timeoutMs(emptyMap()))
        assertEquals(WaitForElement.MinTimeoutMs, WaitForElement.timeoutMs(mapOf("timeout_ms" to "1")))
        assertEquals(WaitForElement.MaxTimeoutMs, WaitForElement.timeoutMs(mapOf("timeout_ms" to "999999")))
    }

    @Test
    fun queryFromArgsBuildsStructuredQuery() {
        val query = WaitForElement.queryFromArgs(
            mapOf(
                "text" to "Continue",
                "class_name" to "android.widget.Button",
                "match" to "exact",
            )
        )
        assertEquals("Continue", query.text)
        assertEquals("android.widget.Button", query.className)
        assertEquals(MatchMode.EXACT, query.match)
        assertEquals(MatchMode.CONTAINS, WaitForElement.queryFromArgs(mapOf("text" to "x")).match)
    }

    @Test
    fun queryMatchesPresentNodeViaSharedMatcher() {
        val screen = screen(nodes = listOf(button("0", "Continue")))
        val matches = FindElementMatcher().match(
            screen,
            WaitForElement.queryFromArgs(mapOf("text" to "Continue"))
        )
        assertTrue(matches.isNotEmpty())
    }

    @Test
    fun queryDoesNotMatchAbsentNode() {
        val screen = screen(nodes = listOf(button("0", "Cancel")))
        val matches = FindElementMatcher().match(
            screen,
            WaitForElement.queryFromArgs(mapOf("text" to "Continue", "match" to "exact"))
        )
        assertTrue(matches.isEmpty())
    }

    @Test
    fun logArgsDoesNotLeakRawQueryText() {
        val logArgs = WaitForElement.logArgs(mapOf("text" to "secret-token-value"), 5_000L)
        assertFalse(logArgs.contains("secret-token-value"))
        assertTrue(logArgs.contains("text_length=18"))
        assertTrue(logArgs.contains("timeout_ms=5000"))
    }

    @Test
    fun retryPolicyTreatsWaitForElementAsSingleAttempt() {
        val config = AndroidToolRetryPolicy().configFor("wait_for_element")
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
