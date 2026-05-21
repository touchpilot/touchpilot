package dev.touchpilot.app.screen.ocr

/**
 * Classifies an Accessibility observation as [ContextQuality.Strong], [ContextQuality.Weak],
 * or [ContextQuality.Empty]. The detector is intentionally conservative: weak detection
 * should bias toward marking OCR fallback rather than letting summarizers guess from
 * insufficient data.
 *
 * Thresholds are injectable so callers (or tests) can tune behavior per surface without
 * touching the detector itself.
 */
class ContextQualityDetector(
    private val thresholds: Thresholds = Thresholds.DEFAULT,
) {
    data class Thresholds(
        val minVisibleText: Int,
        val minClickable: Int,
        val minTreeDepth: Int,
        val minReadableRatio: Double,
    ) {
        init {
            require(minVisibleText >= 0) { "minVisibleText must be non-negative" }
            require(minClickable >= 0) { "minClickable must be non-negative" }
            require(minTreeDepth >= 0) { "minTreeDepth must be non-negative" }
            require(minReadableRatio in 0.0..1.0) {
                "minReadableRatio must be in [0.0, 1.0]"
            }
        }

        companion object {
            val DEFAULT: Thresholds = Thresholds(
                minVisibleText = 2,
                minClickable = 1,
                minTreeDepth = 2,
                minReadableRatio = 0.15,
            )
        }
    }

    fun classify(signals: ObservedScreenSignals): ContextQuality {
        if (signals.totalNodeCount == 0) return ContextQuality.Empty

        if (signals.visibleTextCount < thresholds.minVisibleText) {
            return ContextQuality.Weak(WeakReason.NO_VISIBLE_TEXT)
        }

        if (signals.clickableNodeCount < thresholds.minClickable &&
            signals.inputFieldCount < thresholds.minClickable
        ) {
            return ContextQuality.Weak(WeakReason.NO_CLICKABLE_NODES)
        }

        if (signals.maxTreeDepth < thresholds.minTreeDepth) {
            return ContextQuality.Weak(WeakReason.SHALLOW_TREE)
        }

        val readable = signals.visibleTextCount + signals.clickableNodeCount +
            signals.inputFieldCount
        val ratio = readable.toDouble() / signals.totalNodeCount.toDouble()
        if (ratio < thresholds.minReadableRatio) {
            return ContextQuality.Weak(WeakReason.MOSTLY_EMPTY)
        }

        return ContextQuality.Strong
    }
}
