package dev.touchpilot.app.tools.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Argument parsing and validation helpers for the `drag_and_drop` tool.
 */
class DragTargetTest {
    @Test
    fun detectsSourceDestinationAndCoordinatePresence() {
        val selectorArgs = mapOf(
            DragTarget.SourceTextArg to "Groceries",
            DragTarget.DestinationTextArg to "Work",
        )
        assertTrue(DragTarget.hasSource(selectorArgs))
        assertTrue(DragTarget.hasDestination(selectorArgs))
        assertFalse(DragTarget.hasAnyCoordinate(selectorArgs))

        val coordinateArgs = mapOf(
            DragTarget.StartXArg to "10",
            DragTarget.EndYArg to "40",
        )
        assertTrue(DragTarget.hasAnyCoordinate(coordinateArgs))
        assertFalse(DragTarget.hasSource(coordinateArgs))
    }

    @Test
    fun buildsSelectorsFromEachSide() {
        val args = mapOf(
            DragTarget.SourceNodeIdArg to "0.1.2",
            DragTarget.DestinationBoundsArg to "0,0,100,50",
        )
        val source = DragTarget.sourceSelector(args)
        val destination = DragTarget.destinationSelector(args)

        assertEquals("0.1.2", source.nodeId)
        assertNull(source.bounds)
        assertNotNull(destination.bounds)
        assertEquals(TargetBounds(0, 0, 100, 50), destination.bounds)
    }

    @Test
    fun explicitCoordinatesRequireAllFour() {
        val partial = mapOf(
            DragTarget.StartXArg to "10",
            DragTarget.StartYArg to "20",
            DragTarget.EndXArg to "30",
        )
        assertNull(DragTarget.explicitCoordinates(partial))

        val complete = mapOf(
            DragTarget.StartXArg to "10",
            DragTarget.StartYArg to "20",
            DragTarget.EndXArg to "30",
            DragTarget.EndYArg to "40",
        )
        val request = assertNotNull(DragTarget.explicitCoordinates(complete))
        assertEquals(10, request.startX)
        assertEquals(40, request.endY)
    }

    @Test
    fun holdAndDurationFallBackToDefaults() {
        assertEquals(DragGesture.DefaultHoldMs, DragTarget.holdOrDefault(emptyMap()))
        assertEquals(DragGesture.DefaultMoveMs, DragTarget.durationOrDefault(emptyMap()))
        assertEquals(900L, DragTarget.holdOrDefault(mapOf(DragTarget.HoldArg to "900")))
        assertEquals(250L, DragTarget.durationOrDefault(mapOf(DragTarget.DurationArg to "250")))
    }

    @Test
    fun validateCoordinatesRejectsPartialMalformedAndZeroLength() {
        assertEquals(
            "drag_and_drop coordinates require all of start_x, start_y, end_x, end_y",
            DragTarget.validateCoordinates(mapOf(DragTarget.StartXArg to "1", DragTarget.StartYArg to "2")),
        )
        assertEquals(
            "drag_and_drop coordinates must be integers",
            DragTarget.validateCoordinates(
                mapOf(
                    DragTarget.StartXArg to "x",
                    DragTarget.StartYArg to "2",
                    DragTarget.EndXArg to "3",
                    DragTarget.EndYArg to "4",
                )
            ),
        )
        assertEquals(
            "drag_and_drop start and end coordinates must differ",
            DragTarget.validateCoordinates(
                mapOf(
                    DragTarget.StartXArg to "5",
                    DragTarget.StartYArg to "5",
                    DragTarget.EndXArg to "5",
                    DragTarget.EndYArg to "5",
                )
            ),
        )
        assertNull(DragTarget.validateCoordinates(emptyMap()))
    }

    @Test
    fun validateTimingsRejectsNonPositiveValues() {
        assertEquals("hold_ms must be a positive number", DragTarget.validateTimings(mapOf(DragTarget.HoldArg to "0")))
        assertEquals("duration_ms must be a positive number", DragTarget.validateTimings(mapOf(DragTarget.DurationArg to "-5")))
        assertNull(DragTarget.validateTimings(mapOf(DragTarget.HoldArg to "500", DragTarget.DurationArg to "400")))
    }

    @Test
    fun validateBoundsRejectsMalformedRectangles() {
        assertEquals(
            "source_bounds must be left,top,right,bottom",
            DragTarget.validateBounds(mapOf(DragTarget.SourceBoundsArg to "nope")),
        )
        assertEquals(
            "destination_bounds must be left,top,right,bottom",
            DragTarget.validateBounds(mapOf(DragTarget.DestinationBoundsArg to "1,2,3")),
        )
        assertNull(DragTarget.validateBounds(mapOf(DragTarget.SourceBoundsArg to "0,0,100,50")))
    }
}
