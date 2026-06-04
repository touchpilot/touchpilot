package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import dev.touchpilot.app.tools.AndroidToolCatalog
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the hardened `tap` flow (issue #78): the loose `text`/`node_id`/
 * `bounds` arguments are turned into a [TargetSelector] and resolved through
 * [TargetResolver] before any tap is dispatched. The executor's dispatch layer
 * (AccessibilityBridge) is an Android singleton, so these tests exercise the
 * deterministic resolution that gates it: success, ambiguity, not-found, and
 * sensitive-label redaction.
 */
class TapResolutionTest {
    private val resolver = TargetResolver()

    @Test
    fun textArgBuildsSelectorFromLegacyArgs() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Settings"))

        assertEquals("Settings", selector.text?.raw)
        assertEquals(SelectorSource.USER, selector.source)
        assertTrue(selector.isValid())
    }

    @Test
    fun boundsArgBuildsSelectorFromLegacyArgs() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("bounds" to "10,20,110,120"))

        assertEquals(TargetBounds(10, 20, 110, 120), selector.bounds)
        assertTrue(selector.isValid())
    }

    @Test
    fun tapResolvesSingleTextCandidate() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Wi-Fi"),
                node(nodeId = "0.1", text = "Bluetooth"),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Bluetooth")),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertTrue(resolved.candidate.node.nodeId != null)
    }

    @Test
    fun tapPrefersClickableTargetOverPlainLabel() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Submit", role = NodeRole.TEXT, clickable = false),
                node(nodeId = "0.1", text = "Submit", role = NodeRole.BUTTON, clickable = true),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Submit")),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "actionable")
    }

    @Test
    fun tapResolvesByBounds() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Network", bounds = NodeBounds(0, 0, 50, 50)),
                node(nodeId = "0.1", text = "Display", bounds = NodeBounds(10, 20, 110, 120)),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("bounds" to "10,20,110,120")),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "bounds")
        // The resolved candidate carries a tappable bounds string for fallback.
        assertEquals("10,20,110,120", resolved.candidate.selector.bounds?.toBoundsArg())
    }

    @Test
    fun ambiguousTapTargetFailsSafelyWithCandidateDetails() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Save"),
                node(nodeId = "0.1", text = "Save"),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Save")),
        )

        val ambiguous = assertIs<TargetResolutionResult.Ambiguous>(result)
        assertEquals(2, ambiguous.candidates.size)
        assertTrue(ambiguous.reason.contains("Multiple visible nodes"))
    }

    @Test
    fun notFoundTapTargetIncludesActionableContext() {
        val result = resolver.resolve(
            context = context(node(nodeId = "0.0", text = "Display")),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Bluetooth")),
        )

        val notFound = assertIs<TargetResolutionResult.NotFound>(result)
        assertEquals("No visible node matched the target selector.", notFound.reason)
        assertContains(notFound.debugContext, "Bluetooth")
    }

    @Test
    fun resolvedSensitiveTapTargetRedactsLabel() {
        val secretLabel = "Reveal saved password"
        val result = resolver.resolve(
            context = context(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.BUTTON,
                    text = ScreenText.of(secretLabel),
                    bounds = NodeBounds(0, 0, 100, 100),
                    clickable = true,
                    sensitive = true,
                ),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to secretLabel)),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertTrue(resolved.candidate.selector.sensitive)
        assertTrue(resolved.candidate.selector.containsSensitiveText)
        assertEquals("[REDACTED]", resolved.candidate.selector.text?.displaySafe)
        assertFalse(resolved.candidate.selector.toRedactedJson().contains(secretLabel))
    }

    @Test
    fun validationAcceptsEachSingleSelector() {
        assertNull(AndroidToolCatalog.validate("tap", mapOf("text" to "OK")))
        assertNull(AndroidToolCatalog.validate("tap", mapOf("node_id" to "0.1.2")))
        assertNull(AndroidToolCatalog.validate("tap", mapOf("bounds" to "0,0,100,50")))
    }

    @Test
    fun validationRejectsMultipleSelectors() {
        val error = AndroidToolCatalog.validate(
            "tap",
            mapOf("text" to "OK", "bounds" to "0,0,1,1"),
        )
        assertEquals("tap requires exactly one selector: text, node_id, or bounds", error)
    }

    @Test
    fun validationRejectsNoSelector() {
        val error = AndroidToolCatalog.validate("tap", emptyMap())
        assertEquals("tap requires exactly one selector: text, node_id, or bounds", error)
    }

    private fun context(vararg nodes: ScreenNode): ScreenContext {
        return ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Settings",
            nodes = nodes.toList(),
        )
    }

    private fun node(
        nodeId: String,
        text: String,
        role: NodeRole = NodeRole.BUTTON,
        bounds: NodeBounds = NodeBounds(0, 0, 100, 100),
        enabled: Boolean = true,
        clickable: Boolean = true,
    ): ScreenNode {
        return ScreenNode(
            nodeId = nodeId,
            role = role,
            text = ScreenText.of(text),
            bounds = bounds,
            clickable = clickable,
            enabled = enabled,
        )
    }
}
