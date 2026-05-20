package dev.touchpilot.app.screen.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ContextQualityDetectorTest {
    private val detector = ContextQualityDetector()

    @Test
    fun emptyTreeClassifiesAsEmpty() {
        val quality = detector.classify(ObservedScreenSignals.EMPTY)
        assertSame(ContextQuality.Empty, quality)
    }

    @Test
    fun nodesWithoutReadableTextAreWeakNoVisibleText() {
        val signals = ObservedScreenSignals(
            totalNodeCount = 40,
            visibleTextCount = 0,
            clickableNodeCount = 4,
            inputFieldCount = 0,
            maxTreeDepth = 6,
        )

        val weak = assertIs<ContextQuality.Weak>(detector.classify(signals))
        assertEquals(WeakReason.NO_VISIBLE_TEXT, weak.reason)
    }

    @Test
    fun textWithoutClickablesIsWeakNoClickableNodes() {
        val signals = ObservedScreenSignals(
            totalNodeCount = 30,
            visibleTextCount = 8,
            clickableNodeCount = 0,
            inputFieldCount = 0,
            maxTreeDepth = 5,
        )

        val weak = assertIs<ContextQuality.Weak>(detector.classify(signals))
        assertEquals(WeakReason.NO_CLICKABLE_NODES, weak.reason)
    }

    @Test
    fun inputFieldCountsAsAffordance() {
        // An input-only screen (e.g., password field) should not be flagged as
        // NO_CLICKABLE_NODES — the user can still type into it.
        val signals = ObservedScreenSignals(
            totalNodeCount = 10,
            visibleTextCount = 2,
            clickableNodeCount = 0,
            inputFieldCount = 1,
            maxTreeDepth = 4,
        )

        assertSame(ContextQuality.Strong, detector.classify(signals))
    }

    @Test
    fun shallowTreeIsWeakShallowTree() {
        val signals = ObservedScreenSignals(
            totalNodeCount = 5,
            visibleTextCount = 2,
            clickableNodeCount = 1,
            inputFieldCount = 0,
            maxTreeDepth = 1,
        )

        val weak = assertIs<ContextQuality.Weak>(detector.classify(signals))
        assertEquals(WeakReason.SHALLOW_TREE, weak.reason)
    }

    @Test
    fun lowReadableRatioIsWeakMostlyEmpty() {
        // 100 nodes but only ~5% have any readable signal — typical of a surface
        // rendered through Canvas/SurfaceView where Accessibility sees scaffolding.
        val signals = ObservedScreenSignals(
            totalNodeCount = 100,
            visibleTextCount = 3,
            clickableNodeCount = 2,
            inputFieldCount = 0,
            maxTreeDepth = 6,
        )

        val weak = assertIs<ContextQuality.Weak>(detector.classify(signals))
        assertEquals(WeakReason.MOSTLY_EMPTY, weak.reason)
    }

    @Test
    fun typicalSettingsScreenIsStrong() {
        val signals = ObservedScreenSignals(
            totalNodeCount = 50,
            visibleTextCount = 18,
            clickableNodeCount = 9,
            inputFieldCount = 0,
            maxTreeDepth = 7,
            packageName = "com.android.settings",
        )

        assertSame(ContextQuality.Strong, detector.classify(signals))
    }

    @Test
    fun customThresholdsLetCallersTighten() {
        val strict = ContextQualityDetector(
            ContextQualityDetector.Thresholds(
                minVisibleText = 10,
                minClickable = 5,
                minTreeDepth = 4,
                minReadableRatio = 0.30,
            )
        )

        val borderline = ObservedScreenSignals(
            totalNodeCount = 30,
            visibleTextCount = 6,
            clickableNodeCount = 3,
            inputFieldCount = 0,
            maxTreeDepth = 6,
        )

        val weak = assertIs<ContextQuality.Weak>(strict.classify(borderline))
        assertEquals(WeakReason.NO_VISIBLE_TEXT, weak.reason)
    }

    @Test
    fun firstFailingCheckWinsToKeepReasonStable() {
        // No visible text AND no clickables AND shallow — detector should report
        // the first weak reason (NO_VISIBLE_TEXT) so callers can act without
        // having to enumerate every possible cause.
        val signals = ObservedScreenSignals(
            totalNodeCount = 4,
            visibleTextCount = 0,
            clickableNodeCount = 0,
            inputFieldCount = 0,
            maxTreeDepth = 1,
        )

        val weak = assertIs<ContextQuality.Weak>(detector.classify(signals))
        assertEquals(WeakReason.NO_VISIBLE_TEXT, weak.reason)
    }
}
