package dev.touchpilot.app.tools.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Execution-request construction for `drag_and_drop`.
 *
 * Verifies that selector-mode drags travel between the centers of the two
 * resolved rectangles, coordinate-mode drags preserve their explicit endpoints,
 * and the pickup dwell is clamped into a range that reliably triggers a
 * long-press pickup without stalling gesture dispatch.
 */
class DragGestureTest {
    private val source = TargetBounds(left = 0, top = 0, right = 200, bottom = 100)
    private val destination = TargetBounds(left = 400, top = 800, right = 600, bottom = 1000)

    @Test
    fun betweenTravelsFromSourceCenterToDestinationCenter() {
        val request = DragGesture.between(source, destination)

        assertEquals(source.centerX, request.startX)
        assertEquals(source.centerY, request.startY)
        assertEquals(destination.centerX, request.endX)
        assertEquals(destination.centerY, request.endY)
        assertEquals(DragGesture.DefaultHoldMs, request.holdMs)
        assertEquals(DragGesture.DefaultMoveMs, request.moveMs)
    }

    @Test
    fun betweenPointsPreservesExplicitEndpoints() {
        val request = DragGesture.betweenPoints(10, 20, 30, 40, holdMs = 700L, moveMs = 500L)

        assertEquals(10, request.startX)
        assertEquals(20, request.startY)
        assertEquals(30, request.endX)
        assertEquals(40, request.endY)
        assertEquals(700L, request.holdMs)
        assertEquals(500L, request.moveMs)
    }

    @Test
    fun holdIsClampedIntoTheReliablePickupRange() {
        val tooShort = DragGesture.between(source, destination, holdMs = 1L)
        assertEquals(DragGesture.MinHoldMs, tooShort.holdMs)

        val tooLong = DragGesture.between(source, destination, holdMs = 60_000L)
        assertEquals(DragGesture.MaxHoldMs, tooLong.holdMs)
    }

    @Test
    fun withinReportsWhetherEndpointsFallInsideAnArea() {
        val window = TargetBounds(left = 0, top = 0, right = 1080, bottom = 1920)
        val inside = DragGesture.betweenPoints(100, 100, 500, 1500)
        val outside = DragGesture.betweenPoints(100, 100, 2000, 1500)

        assertTrue(inside.within(window))
        assertFalse(outside.within(window))
    }

    @Test
    fun zeroLengthDragIsDetected() {
        assertTrue(DragGesture.betweenPoints(100, 100, 100, 100).isZeroLength)
        assertFalse(DragGesture.betweenPoints(100, 100, 100, 101).isZeroLength)
    }
}
