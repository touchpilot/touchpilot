package dev.touchpilot.app.tools.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetBoundsTest {
    @Test
    fun nonEmptyRectComputesWidthHeightAndCenter() {
        val b = TargetBounds(left = 100, top = 200, right = 300, bottom = 500)
        assertEquals(200, b.width)
        assertEquals(300, b.height)
        assertEquals(200, b.centerX)
        assertEquals(350, b.centerY)
        assertFalse(b.isEmpty)
    }

    @Test
    fun zeroAreaIsEmpty() {
        assertTrue(TargetBounds(0, 0, 0, 0).isEmpty)
        assertTrue(TargetBounds(50, 50, 50, 100).isEmpty)
        assertTrue(TargetBounds(50, 50, 100, 50).isEmpty)
    }

    @Test
    fun negativeAreaIsEmpty() {
        // Bottom above top, right left of left -> meaningless rect.
        assertTrue(TargetBounds(100, 200, 50, 100).isEmpty)
    }

    @Test
    fun toBoundsArgRoundTripsThroughParse() {
        val original = TargetBounds(10, 20, 30, 40)
        val parsed = TargetBounds.parse(original.toBoundsArg())
        assertEquals(original, parsed)
    }

    @Test
    fun parseAcceptsAndroidStyleBracketFormat() {
        assertEquals(
            TargetBounds(0, 0, 100, 200),
            TargetBounds.parse("[0,0][100,200]"),
        )
    }

    @Test
    fun parseRejectsTooFewIntegers() {
        assertNull(TargetBounds.parse("1,2,3"))
        assertNull(TargetBounds.parse(""))
        assertNull(TargetBounds.parse("nope"))
    }

    @Test
    fun parseRejectsExtraIntegers() {
        assertNull(TargetBounds.parse("1,2,3,4,5"))
    }

    @Test
    fun jsonRoundTrip() {
        val original = TargetBounds(5, 10, 15, 20)
        val restored = TargetBounds.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun unknownIsEmpty() {
        assertTrue(TargetBounds.Unknown.isEmpty)
    }
}
