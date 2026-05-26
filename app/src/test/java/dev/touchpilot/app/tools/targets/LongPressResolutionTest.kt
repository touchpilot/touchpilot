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

/**
 * Confirms `long_press` selectors flow through the shared [TargetResolver]
 * (acceptance criteria #2 and #4 of issue #85). Because long-press imposes no
 * role constraint it must resolve against arbitrary node roles — not only
 * buttons — and still surface explicit ambiguity / not-found failures.
 */
class LongPressResolutionTest {
    private val resolver = TargetResolver()

    @Test
    fun resolvesNonButtonNodeByText() {
        val context = screen(
            ScreenNode(
                nodeId = "0.1",
                role = NodeRole.TEXT,
                text = ScreenText.of("Draft message"),
                bounds = NodeBounds(0, 0, 400, 80),
                longClickable = true,
            )
        )
        val selector = LongPressTarget.selectorFromArgs(mapOf(LongPressTarget.TextArg to "Draft message"))

        val resolved = assertIs<TargetResolutionResult.Resolved>(resolver.resolve(context, selector))
        assertEquals("0.1", resolved.candidate.node.nodeId)
        assertTrue("text_exact" in resolved.candidate.matchReasons)
    }

    @Test
    fun resolvesByNodeId() {
        val context = screen(
            node("0.0", "Photo"),
            node("0.1", "Photo"),
        )
        val selector = LongPressTarget.selectorFromArgs(mapOf(LongPressTarget.NodeIdArg to "0.1"))

        val resolved = assertIs<TargetResolutionResult.Resolved>(resolver.resolve(context, selector))
        assertEquals("0.1", resolved.candidate.node.nodeId)
    }

    @Test
    fun reportsAmbiguityForEquallyMatchingTargets() {
        val context = screen(
            node("0.0", "Photo"),
            node("0.1", "Photo"),
        )
        val selector = LongPressTarget.selectorFromArgs(mapOf(LongPressTarget.TextArg to "Photo"))

        val ambiguous = assertIs<TargetResolutionResult.Ambiguous>(resolver.resolve(context, selector))
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun reportsNotFoundWithDebugContextWhenNothingMatches() {
        val context = screen(node("0.0", "Photo"))
        val selector = LongPressTarget.selectorFromArgs(mapOf(LongPressTarget.TextArg to "Nowhere"))

        val notFound = assertIs<TargetResolutionResult.NotFound>(resolver.resolve(context, selector))
        assertTrue(notFound.debugContext.isNotBlank())
    }

    private fun screen(vararg nodes: ScreenNode): ScreenContext {
        return ScreenContext(appLabel = "Gallery", packageName = "dev.test", nodes = nodes.toList())
    }

    private fun node(id: String, text: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.IMAGE,
            text = ScreenText.of(text),
            bounds = NodeBounds(0, 0, 200, 200),
            clickable = true,
            longClickable = true,
        )
    }
}
