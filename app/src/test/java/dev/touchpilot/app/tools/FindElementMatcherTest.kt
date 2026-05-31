package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FindElementMatcherTest {
    private val matcher = FindElementMatcher()

    @Test
    fun emptyQueryReturnsNothing() {
        val ctx = context(
            node("a", text = "Settings", clickable = true)
        )
        val candidates = matcher.match(ctx, FindElementQuery())
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun exactMatchOnlyAcceptsFullStringEqualityCaseInsensitive() {
        val ctx = context(
            node("a", text = "Settings"),
            node("b", text = "Open Settings"),
        )
        val exact = matcher.match(ctx, FindElementQuery(text = "settings", match = MatchMode.EXACT))
        assertEquals(listOf("a"), exact.nodeIds())
    }

    @Test
    fun containsMatchAcceptsSubstrings() {
        val ctx = context(
            node("a", text = "Settings"),
            node("b", text = "Open Settings"),
            node("c", text = "Notifications"),
        )
        val contains = matcher.match(ctx, FindElementQuery(text = "set", match = MatchMode.CONTAINS))
        assertEquals(setOf("a", "b"), contains.nodeIds().toSet())
    }

    @Test
    fun semanticMatchAcceptsTokenSupersets() {
        val ctx = context(
            node("a", text = "Open Wi-Fi settings"),
            node("b", text = "Save changes"),
            node("c", text = "settings open"),
        )
        val semantic = matcher.match(
            ctx,
            FindElementQuery(text = "open settings", match = MatchMode.SEMANTIC)
        )
        assertEquals(setOf("a", "c"), semantic.nodeIds().toSet())
        assertFalse(semantic.nodeIds().contains("b"))
    }

    @Test
    fun limitClampsCandidateCount() {
        val ctx = context(
            *(1..10).map { node("n$it", text = "Item $it Settings") }.toTypedArray()
        )
        val limited = matcher.match(
            ctx,
            FindElementQuery(text = "Settings", match = MatchMode.CONTAINS, limit = 3)
        )
        assertEquals(3, limited.size)
    }

    @Test
    fun limitDefaultsToFiveWhenUnspecified() {
        val ctx = context(
            *(1..8).map { node("n$it", text = "Settings $it") }.toTypedArray()
        )
        val results = matcher.match(
            ctx,
            FindElementQuery(text = "Settings", match = MatchMode.CONTAINS)
        )
        assertEquals(FindElementQuery.DefaultLimit, results.size)
    }

    @Test
    fun limitIsClampedToMaxAllowedValue() {
        val ctx = context(
            *(1..30).map { node("n$it", text = "Settings $it") }.toTypedArray()
        )
        val results = matcher.match(
            ctx,
            FindElementQuery(text = "Settings", match = MatchMode.CONTAINS, limit = 999)
        )
        assertEquals(FindElementQuery.MaxLimit, results.size)
    }

    @Test
    fun nodeIdFilterMatchesOnlyExactNodeId() {
        val ctx = context(
            node("0.1", text = "First"),
            node("0.2", text = "Second"),
        )
        val results = matcher.match(ctx, FindElementQuery(nodeId = "0.2"))
        assertEquals(listOf("0.2"), results.nodeIds())
    }

    @Test
    fun classNameFilterMatchesShortAndFullyQualifiedNames() {
        val ctx = context(
            node("a", text = "Open", className = "android.widget.Button"),
            node("b", text = "Open", className = "android.widget.TextView"),
        )
        val short = matcher.match(ctx, FindElementQuery(className = "Button"))
        val full = matcher.match(ctx, FindElementQuery(className = "android.widget.Button"))
        assertEquals(listOf("a"), short.nodeIds())
        assertEquals(listOf("a"), full.nodeIds())
    }

    @Test
    fun contentDescriptionFilterMatchesIndependentlyOfText() {
        val ctx = context(
            node("a", text = "", contentDescription = "Compose new email", clickable = true),
            node("b", text = "Compose", clickable = true),
        )
        val results = matcher.match(
            ctx,
            FindElementQuery(contentDescription = "compose", match = MatchMode.CONTAINS)
        )
        assertEquals(listOf("a"), results.nodeIds())
    }

    @Test
    fun candidateJsonExposesDisplaySafeTextAndRedactsSensitiveLabels() {
        val ctx = context(
            node("pw", text = "password: hunter2", isInputField = true, sensitive = true)
        )
        val results = matcher.match(
            ctx,
            FindElementQuery(text = "password", match = MatchMode.CONTAINS)
        )
        assertEquals(1, results.size)
        val json = results.first().toRedactedJson()
        val textJson = json.getJSONObject("text")
        assertEquals("[REDACTED]", textJson.getString("displaySafe"))
        assertTrue(textJson.getBoolean("isSensitive"))
        assertTrue(json.getBoolean("sensitive"))
        val rendered = json.toString()
        assertFalse(rendered.contains("hunter2"), "raw sensitive text leaked: $rendered")
    }

    @Test
    fun encodedPayloadRedactsSensitiveQueryAndCandidateText() {
        val ctx = context(
            node("pw", text = "password: hunter2", isInputField = true, sensitive = true)
        )
        val query = FindElementQuery(text = "password: hunter2", match = MatchMode.CONTAINS)
        val results = matcher.match(ctx, query)
        val payload = FindElementResultEncoder.encode(results, query)
        assertFalse(payload.contains("hunter2"), "raw secret leaked into payload: $payload")
        val parsed = JSONObject(payload)
        assertEquals(1, parsed.getInt("count"))
        assertEquals("contains", parsed.getJSONObject("query").getString("match"))
    }

    @Test
    fun matchReasonsRecordTheModeThatMatched() {
        val ctx = context(node("a", text = "Open Settings", clickable = true))
        val results = matcher.match(
            ctx,
            FindElementQuery(text = "open", match = MatchMode.CONTAINS)
        )
        assertEquals(1, results.size)
        assertTrue(results.first().matchReasons.contains("text_contains"))
    }

    @Test
    fun clickableCandidatesRankAboveNonClickableOnTies() {
        val ctx = context(
            node("text", text = "Settings", clickable = false),
            node("button", text = "Settings", clickable = true),
        )
        val results = matcher.match(
            ctx,
            FindElementQuery(text = "Settings", match = MatchMode.EXACT)
        )
        assertEquals(listOf("button", "text"), results.nodeIds())
    }

    @Test
    fun matchModeFromWireFallsBackToContainsWhenBlank() {
        assertEquals(MatchMode.CONTAINS, MatchMode.fromWire(null))
        assertEquals(MatchMode.CONTAINS, MatchMode.fromWire(""))
        assertEquals(MatchMode.EXACT, MatchMode.fromWire("exact"))
        assertEquals(MatchMode.SEMANTIC, MatchMode.fromWire("SEMANTIC"))
    }

    private fun List<FindElementCandidate>.nodeIds(): List<String> =
        mapNotNull { it.node.nodeId }

    private fun context(vararg nodes: ScreenNode): ScreenContext {
        return ScreenContext(
            appLabel = "Test",
            packageName = "dev.test",
            windowTitle = "Test",
            nodes = nodes.toList()
        )
    }

    private fun node(
        id: String,
        text: String = "",
        contentDescription: String? = null,
        className: String? = null,
        clickable: Boolean = false,
        isInputField: Boolean = false,
        sensitive: Boolean = false,
        role: NodeRole = NodeRole.OTHER,
    ): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = role,
            text = ScreenText.of(text),
            bounds = NodeBounds(left = 0, top = 0, right = 100, bottom = 50),
            clickable = clickable,
            isInputField = isInputField,
            sensitive = sensitive,
            className = className,
            contentDescription = contentDescription?.let { ScreenText.of(it) }
        )
    }
}
