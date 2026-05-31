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
 * Confirms `swipe` container selectors flow through the shared [TargetResolver]
 * and resolve gesture surfaces that are not accessibility-scrollable (issue #86).
 * Because swipe imposes no role constraint it must resolve such containers and
 * still surface explicit ambiguity / not-found failures.
 */
class SwipeResolutionTest {
    private val resolver = TargetResolver()

    @Test
    fun resolvesNonScrollableContainerByText() {
        val context = screen(
            ScreenNode(
                nodeId = "0.1",
                role = NodeRole.CONTAINER,
                text = ScreenText.of("Photo carousel"),
                bounds = NodeBounds(0, 100, 1080, 700),
                // Deliberately not scrollable: a ViewPager often exposes no
                // accessibility scroll action but is still a valid swipe surface.
                scrollable = false,
            )
        )
        val selector = SwipeTarget.selectorFromArgs(mapOf(SwipeTarget.TargetTextArg to "Photo carousel"))

        val resolved = assertIs<TargetResolutionResult.Resolved>(resolver.resolve(context, selector))
        assertEquals("0.1", resolved.candidate.node.nodeId)
    }

    @Test
    fun reportsAmbiguityForEquallyMatchingContainers() {
        val context = screen(
            container("0.0", "Pager"),
            container("0.1", "Pager"),
        )
        val selector = SwipeTarget.selectorFromArgs(mapOf(SwipeTarget.TargetTextArg to "Pager"))

        val ambiguous = assertIs<TargetResolutionResult.Ambiguous>(resolver.resolve(context, selector))
        assertEquals(2, ambiguous.candidates.size)
    }

    @Test
    fun reportsNotFoundWithDebugContextWhenNothingMatches() {
        val context = screen(container("0.0", "Pager"))
        val selector = SwipeTarget.selectorFromArgs(mapOf(SwipeTarget.TargetTextArg to "Nowhere"))

        val notFound = assertIs<TargetResolutionResult.NotFound>(resolver.resolve(context, selector))
        assertTrue(notFound.debugContext.isNotBlank())
    }

    private fun screen(vararg nodes: ScreenNode): ScreenContext {
        return ScreenContext(appLabel = "Gallery", packageName = "dev.test", nodes = nodes.toList())
    }

    private fun container(id: String, text: String): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = NodeRole.CONTAINER,
            text = ScreenText.of(text),
            bounds = NodeBounds(0, 0, 600, 600),
        )
    }
}
