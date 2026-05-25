package dev.touchpilot.app.tools.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScrollTargetTest {
    @Test
    fun hasTargetIsFalseForDirectionOnlyArgs() {
        assertFalse(ScrollTarget.hasTarget(mapOf("direction" to "forward")))
    }

    @Test
    fun hasTargetIsTrueWhenAnySelectorArgIsPresent() {
        assertTrue(ScrollTarget.hasTarget(mapOf(ScrollTarget.TargetNodeIdArg to "list-1")))
        assertTrue(ScrollTarget.hasTarget(mapOf(ScrollTarget.TargetTextArg to "Inbox")))
        assertTrue(ScrollTarget.hasTarget(mapOf(ScrollTarget.TargetViewIdArg to "com.x:id/list")))
        assertTrue(ScrollTarget.hasTarget(mapOf(ScrollTarget.TargetBoundsArg to "0,0,100,100")))
        assertTrue(ScrollTarget.hasTarget(mapOf(ScrollTarget.TargetContentDescriptionArg to "Inbox list")))
    }

    @Test
    fun blankSelectorValuesDoNotCountAsTarget() {
        assertFalse(ScrollTarget.hasTarget(mapOf(ScrollTarget.TargetNodeIdArg to "  ")))
    }

    @Test
    fun selectorFromArgsRoutesSelectorRoleAsScrollable() {
        val selector = ScrollTarget.selectorFromArgs(
            mapOf(
                ScrollTarget.TargetTextArg to "Inbox",
                ScrollTarget.TargetNodeIdArg to "list-1",
                ScrollTarget.TargetBoundsArg to "0,0,500,1000",
                ScrollTarget.TargetViewIdArg to "com.x:id/list",
                ScrollTarget.TargetContentDescriptionArg to "Inbox list",
            )
        )
        assertEquals(TargetRole.SCROLLABLE, selector.role)
        assertEquals(SelectorSource.AGENT, selector.source)
        assertEquals("list-1", selector.nodeId)
        assertEquals("com.x:id/list", selector.viewIdResourceName)
        assertEquals("Inbox", selector.text?.raw)
        assertEquals("Inbox list", selector.contentDescription?.raw)
        assertNotNull(selector.bounds)
        assertTrue(selector.isValid())
    }

    @Test
    fun isBackwardOnlyMatchesBackwardCaseInsensitive() {
        assertTrue(ScrollTarget.isBackward("backward"))
        assertTrue(ScrollTarget.isBackward("Backward"))
        assertFalse(ScrollTarget.isBackward("forward"))
        assertFalse(ScrollTarget.isBackward(""))
        assertFalse(ScrollTarget.isBackward(null))
    }
}
