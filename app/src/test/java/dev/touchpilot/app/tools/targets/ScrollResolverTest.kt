package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScrollResolverTest {
    private val resolver = ScrollResolver()

    @Test
    fun directionOnlyWhenSelectorIsNullAndScrollableExists() {
        val context = screen(scrollable("list-1"))
        val result = resolver.resolve(context, selector = null)
        val direction = assertIs<ScrollResolution.DirectionOnly>(result)
        assertEquals(1, direction.scrollableCount)
    }

    @Test
    fun directionOnlyWhenSelectorIsEmpty() {
        val context = screen(scrollable("list-1"))
        val result = resolver.resolve(context, selector = TargetSelector())
        assertIs<ScrollResolution.DirectionOnly>(result)
    }

    @Test
    fun noScrollableFailsExplicitlyWhenNoSelectorPresent() {
        val context = screen(button("btn-1", "Save"))
        val result = resolver.resolve(context, selector = null)
        val none = assertIs<ScrollResolution.NoScrollable>(result)
        assertTrue(none.reason.contains("No scrollable container"))
        assertTrue(none.debugContext.isNotBlank())
    }

    @Test
    fun noScrollableFailsExplicitlyWhenSelectorPresent() {
        val context = screen(button("btn-1", "Save"))
        val selector = TargetSelector(text = SelectorText.of("Inbox"), role = TargetRole.SCROLLABLE)
        val result = resolver.resolve(context, selector)
        assertIs<ScrollResolution.NoScrollable>(result)
    }

    @Test
    fun singleScrollableContainerResolvesByNodeId() {
        val context = screen(scrollable("list-1"))
        val selector = TargetSelector(nodeId = "list-1", source = SelectorSource.AGENT)
        val resolved = assertIs<ScrollResolution.Resolved>(resolver.resolve(context, selector))
        assertEquals("list-1", resolved.node.nodeId)
        assertTrue(resolved.confidence >= 0.5f)
        assertTrue("node_id" in resolved.matchReasons)
    }

    @Test
    fun singleScrollableContainerResolvesByText() {
        val context = screen(scrollable("list-1", text = "Inbox"))
        val selector = TargetSelector(
            text = SelectorText.of("Inbox"),
            role = TargetRole.SCROLLABLE,
            source = SelectorSource.AGENT,
        )
        val resolved = assertIs<ScrollResolution.Resolved>(resolver.resolve(context, selector))
        assertEquals("list-1", resolved.node.nodeId)
    }

    @Test
    fun multipleScrollableContainersAreAmbiguousWhenSelectorIsAmbiguous() {
        val context = screen(
            scrollable("list-1", text = "Inbox"),
            scrollable("list-2", text = "Inbox"),
        )
        val selector = TargetSelector(
            text = SelectorText.of("Inbox"),
            role = TargetRole.SCROLLABLE,
            source = SelectorSource.AGENT,
        )
        val ambiguous = assertIs<ScrollResolution.Ambiguous>(resolver.resolve(context, selector))
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun multipleScrollableContainersResolveWhenSelectorPicksOne() {
        val context = screen(
            scrollable("list-1", text = "Inbox"),
            scrollable("list-2", text = "Drafts"),
        )
        val selector = TargetSelector(
            text = SelectorText.of("Drafts"),
            role = TargetRole.SCROLLABLE,
            source = SelectorSource.AGENT,
        )
        val resolved = assertIs<ScrollResolution.Resolved>(resolver.resolve(context, selector))
        assertEquals("list-2", resolved.node.nodeId)
    }

    @Test
    fun selectorThatDoesNotMatchAnyScrollableIsNotFound() {
        val context = screen(scrollable("list-1", text = "Inbox"))
        val selector = TargetSelector(
            text = SelectorText.of("Nowhere"),
            role = TargetRole.SCROLLABLE,
            source = SelectorSource.AGENT,
        )
        val notFound = assertIs<ScrollResolution.NotFound>(resolver.resolve(context, selector))
        assertTrue(notFound.debugContext.isNotBlank())
    }

    @Test
    fun nonScrollableNodesAreNeverConsideredEvenIfTheyMatchByText() {
        // A button labeled "Inbox" exists but only a different scrollable
        // container is present — the resolver must not pick the button.
        val context = screen(
            button("btn-1", "Inbox"),
            scrollable("list-1", text = "Drafts"),
        )
        val selector = TargetSelector(
            text = SelectorText.of("Inbox"),
            role = TargetRole.SCROLLABLE,
            source = SelectorSource.AGENT,
        )
        assertIs<ScrollResolution.NotFound>(resolver.resolve(context, selector))
    }

    private fun screen(vararg nodes: ScreenNode): ScreenContext {
        return ScreenContext(
            appLabel = "Test",
            packageName = "dev.test",
            nodes = nodes.toList(),
        )
    }

    private fun scrollable(
        id: String,
        text: String = "",
        bounds: NodeBounds = NodeBounds(left = 0, top = 0, right = 1000, bottom = 1000),
    ): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.SCROLLABLE,
            text = if (text.isBlank()) ScreenText.Empty else ScreenText.of(text),
            bounds = bounds,
            scrollable = true,
        )
    }

    private fun button(id: String, text: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.BUTTON,
            text = ScreenText.of(text),
            clickable = true,
        )
    }
}
