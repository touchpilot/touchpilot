package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TargetResolverTest {
    private val resolver = TargetResolver()

    @Test
    fun resolvesExactNodeIdSelector() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Wi-Fi"),
                node(nodeId = "0.1", text = "Battery"),
            ),
            selector = TargetSelector(nodeId = "0.1"),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "node_id")
        assertTrue(resolved.candidate.confidence >= 0.9f)
    }

    @Test
    fun resolvesExactBoundsSelector() {
        val targetBounds = TargetBounds(10, 20, 110, 120)
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Network", bounds = NodeBounds(0, 0, 50, 50)),
                node(nodeId = "0.1", text = "Display", bounds = NodeBounds(10, 20, 110, 120)),
            ),
            selector = TargetSelector(bounds = targetBounds),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("Display", resolved.candidate.node.text.raw)
        assertContains(resolved.candidate.matchReasons, "bounds")
    }

    @Test
    fun resolvesViewIdSelector() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Network", viewId = "com.android.settings:id/network"),
                node(nodeId = "0.1", text = "Battery", viewId = "com.android.settings:id/battery"),
            ),
            selector = TargetSelector(viewIdResourceName = "com.android.settings:id/battery"),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "view_id")
    }

    @Test
    fun resolvesNormalizedTextSelector() {
        val result = resolver.resolve(
            context = context(node(nodeId = "0.0", text = "Network & internet")),
            selector = TargetSelector(text = SelectorText.of("network internet")),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("Network & internet", resolved.candidate.node.text.raw)
        assertContains(resolved.candidate.matchReasons, "text_normalized")
    }

    @Test
    fun resolvesContentDescriptionSelectorAgainstObservedText() {
        val result = resolver.resolve(
            context = context(node(nodeId = "0.0", text = "Search apps")),
            selector = TargetSelector(contentDescription = SelectorText.of("search apps")),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("Search apps", resolved.candidate.node.text.raw)
        assertContains(resolved.candidate.matchReasons, "content_description_normalized")
    }

    @Test
    fun roleSelectorDisambiguatesTextMatches() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Search", role = NodeRole.TEXT, clickable = false),
                node(nodeId = "0.1", text = "Search", role = NodeRole.BUTTON),
            ),
            selector = TargetSelector(
                text = SelectorText.of("Search"),
                role = TargetRole.BUTTON,
            ),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "role")
    }

    @Test
    fun returnsAmbiguousWhenMultipleCandidatesTie() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Settings"),
                node(nodeId = "0.1", text = "Settings"),
            ),
            selector = TargetSelector(text = SelectorText.of("Settings")),
        )

        val ambiguous = assertIs<TargetResolutionResult.Ambiguous>(result)
        assertEquals(2, ambiguous.candidates.size)
        assertTrue(ambiguous.reason.contains("Multiple visible nodes"))
        assertTrue(ambiguous.candidates.all { it.selector.text?.displaySafe == "Settings" })
    }

    @Test
    fun exactTextBeatsNormalizedCandidate() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Wi Fi"),
                node(nodeId = "0.1", text = "Wi-Fi"),
            ),
            selector = TargetSelector(text = SelectorText.of("Wi-Fi")),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "text_exact")
    }

    @Test
    fun returnsNotFoundWhenOnlyMatchingNodeIsDisabled() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Continue", enabled = false),
            ),
            selector = TargetSelector(text = SelectorText.of("Continue")),
        )

        val notFound = assertIs<TargetResolutionResult.NotFound>(result)
        assertEquals("Only disabled nodes matched the target selector.", notFound.reason)
        assertEquals(1, notFound.rejectedCandidates.size)
        assertContains(notFound.rejectedCandidates.single().matchReasons, "disabled")
    }

    @Test
    fun returnsNotFoundWithUsefulContextWhenNoNodeMatches() {
        val result = resolver.resolve(
            context = context(node(nodeId = "0.0", text = "Display")),
            selector = TargetSelector(text = SelectorText.of("Bluetooth")),
        )

        val notFound = assertIs<TargetResolutionResult.NotFound>(result)
        assertEquals("No visible node matched the target selector.", notFound.reason)
        assertContains(notFound.debugContext, "Bluetooth")
        assertContains(notFound.debugContext, "Settings")
        assertFalse(notFound.debugContext.contains("user@example.com"))
    }

    @Test
    fun returnsNotFoundForEmptySelector() {
        val result = resolver.resolve(
            context = context(node(nodeId = "0.0", text = "Display")),
            selector = TargetSelector.Empty,
        )

        val notFound = assertIs<TargetResolutionResult.NotFound>(result)
        assertEquals("Target selector is empty.", notFound.reason)
    }

    @Test
    fun candidatesUseRedactionSafeSelectorText() {
        val result = resolver.resolve(
            context = context(
                ScreenNode(
                    nodeId = "0.0",
                    role = NodeRole.INPUT,
                    text = ScreenText.of("PIN"),
                    bounds = NodeBounds(0, 0, 100, 100),
                    isInputField = true,
                    sensitive = true,
                ),
            ),
            selector = TargetSelector(nodeId = "0.0"),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertTrue(resolved.candidate.selector.sensitive)
        assertEquals("[REDACTED]", resolved.candidate.selector.text?.displaySafe)
        assertTrue(resolved.candidate.selector.containsSensitiveText)
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
        viewId: String? = null,
    ): ScreenNode {
        return ScreenNode(
            nodeId = nodeId,
            role = role,
            text = ScreenText.of(text),
            bounds = bounds,
            clickable = clickable,
            enabled = enabled,
            viewIdResourceName = viewId,
        )
    }
}
