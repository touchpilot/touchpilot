package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LongPressResolutionTest {
    private val resolver = TargetResolver()

    @Test
    fun viewIdArgBuildsSelectorFromLegacyArgs() {
        val selector = TargetSelectorBuilder.fromLegacyArgs(
            mapOf("view_id" to "com.example.launcher:id/icon")
        )

        assertEquals("com.example.launcher:id/icon", selector.viewIdResourceName)
        assertTrue(selector.isValid())
    }

    @Test
    fun longPressResolvesByViewIdThroughSharedResolver() {
        val result = resolver.resolve(
            context = context(
                node(
                    nodeId = "0.0",
                    text = "Calendar",
                    viewId = "com.example.launcher:id/calendar"
                ),
                node(
                    nodeId = "0.1",
                    text = "Settings",
                    viewId = "com.example.launcher:id/settings"
                ),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(
                mapOf("view_id" to "com.example.launcher:id/settings")
            ),
        )

        val resolved = assertIs<TargetResolutionResult.Resolved>(result)
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertContains(resolved.candidate.matchReasons, "view_id")
    }

    @Test
    fun ambiguousLongPressTargetsFailSafely() {
        val result = resolver.resolve(
            context = context(
                node(nodeId = "0.0", text = "Archive"),
                node(nodeId = "0.1", text = "Archive"),
            ),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Archive")),
        )

        val ambiguous = assertIs<TargetResolutionResult.Ambiguous>(result)
        assertEquals(2, ambiguous.candidates.size)
        assertContains(ambiguous.reason, "Multiple visible nodes")
    }

    @Test
    fun missingLongPressTargetIncludesDebugContext() {
        val result = resolver.resolve(
            context = context(node(nodeId = "0.0", text = "Display")),
            selector = TargetSelectorBuilder.fromLegacyArgs(mapOf("text" to "Bluetooth")),
        )

        val notFound = assertIs<TargetResolutionResult.NotFound>(result)
        assertEquals("No visible node matched the target selector.", notFound.reason)
        assertContains(notFound.debugContext, "Bluetooth")
    }

    private fun context(vararg nodes: ScreenNode): ScreenContext {
        return ScreenContext(
            appLabel = "Launcher",
            packageName = "com.example.launcher",
            windowTitle = "Home",
            nodes = nodes.toList(),
        )
    }

    private fun node(
        nodeId: String,
        text: String,
        viewId: String? = null,
        role: NodeRole = NodeRole.BUTTON,
        bounds: NodeBounds = NodeBounds(0, 0, 100, 100),
        enabled: Boolean = true,
        longClickable: Boolean = true,
    ): ScreenNode {
        return ScreenNode(
            nodeId = nodeId,
            role = role,
            text = ScreenText.of(text),
            bounds = bounds,
            enabled = enabled,
            longClickable = longClickable,
            viewIdResourceName = viewId,
        )
    }
}
