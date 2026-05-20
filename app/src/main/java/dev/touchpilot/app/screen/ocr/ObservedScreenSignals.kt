package dev.touchpilot.app.screen.ocr

/**
 * Minimal counts derived from one Accessibility observation. Kept platform-free so
 * detector logic can run as a JVM unit test, and so a future caller can build it from
 * either an `AccessibilityNodeInfo` walk or a higher-level screen context model.
 */
data class ObservedScreenSignals(
    val totalNodeCount: Int,
    val visibleTextCount: Int,
    val clickableNodeCount: Int,
    val inputFieldCount: Int,
    val maxTreeDepth: Int,
    val packageName: String? = null,
) {
    init {
        require(totalNodeCount >= 0) { "totalNodeCount must be non-negative" }
        require(visibleTextCount >= 0) { "visibleTextCount must be non-negative" }
        require(clickableNodeCount >= 0) { "clickableNodeCount must be non-negative" }
        require(inputFieldCount >= 0) { "inputFieldCount must be non-negative" }
        require(maxTreeDepth >= 0) { "maxTreeDepth must be non-negative" }
    }

    companion object {
        val EMPTY: ObservedScreenSignals = ObservedScreenSignals(
            totalNodeCount = 0,
            visibleTextCount = 0,
            clickableNodeCount = 0,
            inputFieldCount = 0,
            maxTreeDepth = 0,
        )
    }
}
