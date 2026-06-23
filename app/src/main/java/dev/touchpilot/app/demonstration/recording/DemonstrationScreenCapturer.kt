package dev.touchpilot.app.demonstration.recording

import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.demonstration.DemonstrationCapturePhase
import dev.touchpilot.app.demonstration.DemonstrationScreenFrame
import dev.touchpilot.app.demonstration.analysis.DemonstrationScreenFingerprint
import dev.touchpilot.app.screen.ScreenContext

/**
 * Captures redacted screen context frames for demonstration recording.
 */
class DemonstrationScreenCapturer(
    private val observeScreen: () -> ScreenContext = { AccessibilityBridge.observeScreenContext() },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var sequenceCounter = 0

    fun reset() {
        sequenceCounter = 0
    }

    fun capture(phase: DemonstrationCapturePhase): DemonstrationScreenFrame {
        val context = observeScreen()
        val fingerprint = DemonstrationScreenFingerprint.compute(context)
        return DemonstrationScreenFrame.capture(
            sequenceNumber = nextSequence(),
            phase = phase,
            timestampMillis = clock(),
            context = context,
            fingerprint = fingerprint,
        )
    }

    fun captureFromContext(phase: DemonstrationCapturePhase, context: ScreenContext): DemonstrationScreenFrame {
        return DemonstrationScreenFrame.capture(
            sequenceNumber = nextSequence(),
            phase = phase,
            timestampMillis = clock(),
            context = context,
            fingerprint = DemonstrationScreenFingerprint.compute(context),
        )
    }

    private fun nextSequence(): Int {
        sequenceCounter += 1
        return sequenceCounter
    }
}
