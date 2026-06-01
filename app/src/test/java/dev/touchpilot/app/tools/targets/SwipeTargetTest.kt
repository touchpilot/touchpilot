package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwipeTargetTest {
    @Test
    fun directionParsingIsCaseInsensitiveAndRejectsUnknownValues() {
        assertEquals(SwipeDirection.LEFT, SwipeDirection.parse("left"))
        assertEquals(SwipeDirection.RIGHT, SwipeDirection.parse("RIGHT"))
        assertEquals(SwipeDirection.UP, SwipeDirection.parse(" Up "))
        assertEquals(SwipeDirection.DOWN, SwipeDirection.parse("down"))
        assertNull(SwipeDirection.parse("diagonal"))
        assertNull(SwipeDirection.parse(null))
        assertNull(SwipeDirection.parse(""))
    }

    @Test
    fun hasTargetAndHasCoordinateDetectPresentArgs() {
        assertFalse(SwipeTarget.hasTarget(mapOf(SwipeTarget.DirectionArg to "left")))
        assertTrue(SwipeTarget.hasTarget(mapOf(SwipeTarget.TargetNodeIdArg to "0.1")))
        assertFalse(SwipeTarget.hasAnyCoordinate(mapOf(SwipeTarget.DirectionArg to "left")))
        assertTrue(SwipeTarget.hasAnyCoordinate(mapOf(SwipeTarget.StartXArg to "10")))
    }

    @Test
    fun selectorFromArgsImposesNoRoleConstraint() {
        val selector = SwipeTarget.selectorFromArgs(
            mapOf(
                SwipeTarget.TargetTextArg to "Carousel",
                SwipeTarget.TargetNodeIdArg to "0.3",
                SwipeTarget.TargetViewIdArg to "com.x:id/pager",
                SwipeTarget.TargetBoundsArg to "0,0,500,500",
                SwipeTarget.TargetContentDescriptionArg to "Image carousel",
            )
        )
        // Swipe surfaces are not necessarily accessibility-scrollable, so no role
        // is imposed (unlike ScrollTarget).
        assertNull(selector.role)
        assertEquals(SelectorSource.AGENT, selector.source)
        assertEquals("0.3", selector.nodeId)
        assertEquals("Carousel", selector.text?.raw)
        assertTrue(selector.isValid())
    }

    @Test
    fun durationFallsBackToDefaultForMissingOrInvalidValues() {
        assertEquals(SwipeGesture.DefaultDurationMs, SwipeTarget.durationOrDefault(emptyMap()))
        assertEquals(SwipeGesture.DefaultDurationMs, SwipeTarget.durationOrDefault(mapOf(SwipeTarget.DurationArg to "abc")))
        assertEquals(750L, SwipeTarget.durationOrDefault(mapOf(SwipeTarget.DurationArg to "750")))
    }

    @Test
    fun explicitCoordinatesRequireAllFourValues() {
        assertNull(
            SwipeTarget.explicitCoordinates(
                mapOf(SwipeTarget.StartXArg to "10", SwipeTarget.StartYArg to "20")
            )
        )
        val request = SwipeTarget.explicitCoordinates(
            mapOf(
                SwipeTarget.StartXArg to "10",
                SwipeTarget.StartYArg to "20",
                SwipeTarget.EndXArg to "300",
                SwipeTarget.EndYArg to "20",
                SwipeTarget.DurationArg to "400",
            )
        )
        assertEquals(SwipeRequest(10, 20, 300, 20, 400L), request)
    }

    @Test
    fun catalogExposesSwipeAsMediumRiskWithoutRequiredArgs() {
        val spec = AndroidToolCatalog.find("swipe")
        assertTrue(spec != null)
        assertEquals(
            setOf(
                "direction",
                "start_x",
                "start_y",
                "end_x",
                "end_y",
                "duration_ms",
                "target_text",
                "target_node_id",
                "target_bounds",
                "target_view_id",
                "target_content_description",
            ),
            spec!!.arguments.keys,
        )
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validationRequiresDirectionOrCoordinates() {
        assertEquals(
            "swipe requires a direction (left, right, up, down) or explicit start/end coordinates",
            AndroidToolCatalog.validate("swipe", emptyMap()),
        )
    }

    @Test
    fun validationRejectsInvalidDirection() {
        assertEquals(
            "Invalid swipe direction: sideways. Use left, right, up, or down.",
            AndroidToolCatalog.validate("swipe", mapOf("direction" to "sideways")),
        )
    }

    @Test
    fun validationRejectsPartialCoordinates() {
        assertEquals(
            "swipe coordinates require all of start_x, start_y, end_x, end_y",
            AndroidToolCatalog.validate(
                "swipe",
                mapOf(SwipeTarget.StartXArg to "10", SwipeTarget.EndXArg to "200"),
            ),
        )
    }

    @Test
    fun validationRejectsNonIntegerCoordinates() {
        assertEquals(
            "swipe coordinates must be integers",
            AndroidToolCatalog.validate(
                "swipe",
                mapOf(
                    SwipeTarget.StartXArg to "10",
                    SwipeTarget.StartYArg to "20",
                    SwipeTarget.EndXArg to "x",
                    SwipeTarget.EndYArg to "40",
                ),
            ),
        )
    }

    @Test
    fun validationRejectsNegativeCoordinates() {
        assertEquals(
            "swipe coordinates must be non-negative",
            AndroidToolCatalog.validate(
                "swipe",
                mapOf(
                    SwipeTarget.StartXArg to "10",
                    SwipeTarget.StartYArg to "20",
                    SwipeTarget.EndXArg to "-5",
                    SwipeTarget.EndYArg to "40",
                ),
            ),
        )
    }

    @Test
    fun validationRejectsZeroLengthSwipe() {
        assertEquals(
            "swipe start and end coordinates must differ",
            AndroidToolCatalog.validate(
                "swipe",
                mapOf(
                    SwipeTarget.StartXArg to "10",
                    SwipeTarget.StartYArg to "20",
                    SwipeTarget.EndXArg to "10",
                    SwipeTarget.EndYArg to "20",
                ),
            ),
        )
    }

    @Test
    fun validationRejectsNonPositiveDuration() {
        assertEquals(
            "duration_ms must be a positive number",
            AndroidToolCatalog.validate(
                "swipe",
                mapOf("direction" to "left", SwipeTarget.DurationArg to "0"),
            ),
        )
    }

    @Test
    fun validationRejectsMalformedContainerBounds() {
        assertEquals(
            "target_bounds must be left,top,right,bottom",
            AndroidToolCatalog.validate(
                "swipe",
                mapOf("direction" to "left", SwipeTarget.TargetBoundsArg to "bad,bounds"),
            ),
        )
    }

    @Test
    fun validationAcceptsDirectionAndCompleteCoordinateSets() {
        assertNull(AndroidToolCatalog.validate("swipe", mapOf("direction" to "up")))
        assertNull(
            AndroidToolCatalog.validate(
                "swipe",
                mapOf(
                    SwipeTarget.StartXArg to "10",
                    SwipeTarget.StartYArg to "20",
                    SwipeTarget.EndXArg to "300",
                    SwipeTarget.EndYArg to "20",
                ),
            ),
        )
    }

    @Test
    fun validationRejectsUnknownArguments() {
        val error = AndroidToolCatalog.validate("swipe", mapOf("velocity" to "fast"))
        assertTrue(error != null && error.startsWith("Unknown argument"))
    }

    @Test
    fun mediumRiskRequiresApprovalForModelSource() {
        val spec = AndroidToolCatalog.find("swipe")!!
        val decision = DefaultActionPolicy().evaluate(
            ToolPolicyRequest(
                tool = spec,
                args = mapOf("direction" to "left"),
                source = ToolSource.LOCAL_MODEL,
            )
        )

        assertIs<PolicyDecision.RequireApproval>(decision)
    }
}
