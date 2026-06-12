package dev.touchpilot.app.androidcontrol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DismissKeyboardOutcomeTest {
    @Test
    fun hiddenCarriesMeasuredStillVisibleValue() {
        // Hidden is the only outcome that runs the hide path, so it must be
        // able to report either result of the post-restore re-measurement.
        assertTrue(DismissKeyboardOutcome.Hidden(stillVisibleAfter = true).stillVisibleAfter)
        assertFalse(DismissKeyboardOutcome.Hidden(stillVisibleAfter = false).stillVisibleAfter)
    }

    @Test
    fun hiddenAlwaysReportsWasVisibleBefore() {
        assertTrue(DismissKeyboardOutcome.Hidden(stillVisibleAfter = true).wasVisibleBefore)
        assertTrue(DismissKeyboardOutcome.Hidden(stillVisibleAfter = false).wasVisibleBefore)
    }

    @Test
    fun noDispatchOutcomesAreNeverVisibleAfter() {
        // AlreadyHidden and NotConnected never run the hide path, so a
        // hard-coded false is genuinely accurate for them.
        assertFalse(DismissKeyboardOutcome.AlreadyHidden.wasVisibleBefore)
        assertFalse(DismissKeyboardOutcome.AlreadyHidden.stillVisibleAfter)
        assertFalse(DismissKeyboardOutcome.NotConnected.wasVisibleBefore)
        assertFalse(DismissKeyboardOutcome.NotConnected.stillVisibleAfter)
    }
}
