package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.tools.targets.DragTarget
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DragAndDropCatalogTest {
    @Test
    fun dragAndDropIsRegisteredAsMediumRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("drag_and_drop"))
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertTrue(spec.requiredArguments.isEmpty())
        // Every accepted argument must be declared so validate() does not reject it as unknown.
        assertTrue(spec.arguments.keys.containsAll(DragTarget.allArgs.toSet()))
    }

    @Test
    fun validateAcceptsSelectorModeWithSourceAndDestination() {
        assertNull(
            AndroidToolCatalog.validate(
                "drag_and_drop",
                mapOf(
                    DragTarget.SourceTextArg to "Groceries",
                    DragTarget.DestinationTextArg to "Work",
                )
            )
        )
    }

    @Test
    fun validateAcceptsCoordinateMode() {
        assertNull(
            AndroidToolCatalog.validate(
                "drag_and_drop",
                mapOf(
                    DragTarget.StartXArg to "100",
                    DragTarget.StartYArg to "200",
                    DragTarget.EndXArg to "100",
                    DragTarget.EndYArg to "900",
                )
            )
        )
    }

    @Test
    fun validateRejectsSelectorModeMissingAnEndpoint() {
        val error = AndroidToolCatalog.validate(
            "drag_and_drop",
            mapOf(DragTarget.SourceTextArg to "Groceries"),
        )
        assertNotNull(error)
        assertContains(error, "requires a source and a destination selector")
    }

    @Test
    fun validateRejectsMixingCoordinatesAndSelectors() {
        val error = AndroidToolCatalog.validate(
            "drag_and_drop",
            mapOf(
                DragTarget.SourceTextArg to "Groceries",
                DragTarget.DestinationTextArg to "Work",
                DragTarget.StartXArg to "1",
                DragTarget.StartYArg to "2",
                DragTarget.EndXArg to "3",
                DragTarget.EndYArg to "4",
            ),
        )
        assertEquals(
            "drag_and_drop takes either coordinates or source/destination selectors, not both",
            error,
        )
    }

    @Test
    fun validateRejectsMalformedBoundsAndTimings() {
        assertEquals(
            "source_bounds must be left,top,right,bottom",
            AndroidToolCatalog.validate(
                "drag_and_drop",
                mapOf(DragTarget.SourceBoundsArg to "bad", DragTarget.DestinationTextArg to "Work"),
            ),
        )
        assertEquals(
            "hold_ms must be a positive number",
            AndroidToolCatalog.validate(
                "drag_and_drop",
                mapOf(
                    DragTarget.SourceTextArg to "Groceries",
                    DragTarget.DestinationTextArg to "Work",
                    DragTarget.HoldArg to "0",
                ),
            ),
        )
    }

    @Test
    fun validateRejectsUnknownArgument() {
        val error = AndroidToolCatalog.validate(
            "drag_and_drop",
            mapOf(DragTarget.SourceTextArg to "Groceries", "nonsense" to "1"),
        )
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun retryPolicyTreatsDragLikeNavigationAction() {
        val config = AndroidToolRetryPolicy().configFor("drag_and_drop")
        assertEquals(3, config.maxAttempts)
        assertTrue(config.retryable)
        assertTrue(config.waitForIdleAfterSuccess)
    }

    @Test
    fun retryPolicyDoesNotRetryAmbiguousDragFailures() {
        val decision = AndroidToolRetryPolicy().shouldRetry(
            "drag_and_drop",
            ToolResult(ok = false, message = "Ambiguous drag source: multiple matches"),
            attempt = 0,
        )
        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.NON_RETRYABLE, decision.category)
    }

    @Test
    fun verifierPassesWhenScreenChangedAndFailsOtherwise() {
        val verifier = ToolVerifier()
        val passed = verifier.verify(
            toolName = "drag_and_drop",
            args = emptyMap(),
            result = ToolResult(ok = true, message = "drag_and_drop", data = mapOf("screen_changed" to "true")),
            before = ScreenContext.Empty,
            after = ScreenContext.Empty,
        )
        assertIs<ToolVerificationResult.Passed>(passed)

        val failed = verifier.verify(
            toolName = "drag_and_drop",
            args = emptyMap(),
            result = ToolResult(ok = true, message = "drag_and_drop", data = mapOf("screen_changed" to "false")),
            before = ScreenContext.Empty,
            after = ScreenContext.Empty,
        )
        assertIs<ToolVerificationResult.Failed>(failed)
    }
}
