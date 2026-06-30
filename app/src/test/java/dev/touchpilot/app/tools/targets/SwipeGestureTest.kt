package dev.touchpilot.app.tools.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Execution-request construction for `swipe` (issue #86 acceptance criterion 5).
 *
 * Verifies that direction-based swipes produce a deterministic [SwipeRequest]
 * travelling along the correct axis and finger direction within the supplied
 * area, with an edge inset on both ends.
 */
class SwipeGestureTest {
    private val area = TargetBounds(left = 0, top = 0, right = 1000, bottom = 2000)

    @Test
    fun rightSwipeMovesFingerLeftToRightAlongVerticalCenter() {
        val request = SwipeGesture.forDirection(SwipeDirection.RIGHT, area)

        assertTrue(request.startX < request.endX)
        assertEquals(area.centerY, request.startY)
        assertEquals(area.centerY, request.endY)
        // 10% inset from each horizontal edge.
        assertEquals(100, request.startX)
        assertEquals(900, request.endX)
    }

    @Test
    fun leftSwipeMovesFingerRightToLeft() {
        val request = SwipeGesture.forDirection(SwipeDirection.LEFT, area)

        assertTrue(request.startX > request.endX)
        assertEquals(900, request.startX)
        assertEquals(100, request.endX)
        assertEquals(area.centerY, request.startY)
    }

    @Test
    fun downSwipeMovesFingerTopToBottomAlongHorizontalCenter() {
        val request = SwipeGesture.forDirection(SwipeDirection.DOWN, area)

        assertTrue(request.startY < request.endY)
        assertEquals(area.centerX, request.startX)
        assertEquals(area.centerX, request.endX)
        assertEquals(200, request.startY)
        assertEquals(1800, request.endY)
    }

    @Test
    fun upSwipeMovesFingerBottomToTop() {
        val request = SwipeGesture.forDirection(SwipeDirection.UP, area)

        assertTrue(request.startY > request.endY)
        assertEquals(1800, request.startY)
        assertEquals(200, request.endY)
        assertEquals(area.centerX, request.startX)
    }

    @Test
    fun forwardScrollSwipesUpToRevealContentBelow() {
        // Forward content scroll = drag the surface upward (issue #218 gesture fallback).
        val request = SwipeGesture.forScroll(forward = true, area = area)

        assertEquals(SwipeGesture.forDirection(SwipeDirection.UP, area), request)
        assertTrue(request.startY > request.endY)
        assertEquals(area.centerX, request.startX)
        assertEquals(area.centerX, request.endX)
    }

    @Test
    fun backwardScrollSwipesDownToRevealContentAbove() {
        val request = SwipeGesture.forScroll(forward = false, area = area)

        assertEquals(SwipeGesture.forDirection(SwipeDirection.DOWN, area), request)
        assertTrue(request.startY < request.endY)
        assertEquals(area.centerX, request.startX)
        assertEquals(area.centerX, request.endX)
    }

    @Test
    fun scrollSwipeEndpointsStayInsideArea() {
        listOf(true, false).forEach { forward ->
            val request = SwipeGesture.forScroll(forward, area)
            assertTrue(request.within(area), "forward=$forward endpoints should stay inside area")
        }
    }

    @Test
    fun directionSwipeUsesDefaultDurationUnlessOverridden() {
        assertEquals(
            SwipeGesture.DefaultDurationMs,
            SwipeGesture.forDirection(SwipeDirection.UP, area).durationMs,
        )
        assertEquals(
            750L,
            SwipeGesture.forDirection(SwipeDirection.UP, area, durationMs = 750L).durationMs,
        )
    }

    @Test
    fun planStaysWithinTheSuppliedArea() {
        for (direction in SwipeDirection.values()) {
            val request = SwipeGesture.forDirection(direction, area)
            assertTrue(request.within(area), "$direction swipe should stay within the area")
        }
    }

    @Test
    fun withinDetectsOutOfRangeEndpoints() {
        val request = SwipeRequest(startX = 10, startY = 10, endX = 1200, endY = 10, durationMs = 300L)
        assertFalse(request.within(area))
    }

    @Test
    fun swipeIsPlannedWithinAResolvedContainerRatherThanTheScreen() {
        val container = TargetBounds(left = 100, top = 400, right = 500, bottom = 600)
        val request = SwipeGesture.forDirection(SwipeDirection.RIGHT, container)

        assertTrue(request.within(container))
        assertEquals(container.centerY, request.startY)
        assertTrue(request.startX in container.left..container.right)
    }
}
