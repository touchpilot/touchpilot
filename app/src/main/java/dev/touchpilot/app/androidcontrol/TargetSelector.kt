package dev.touchpilot.app.androidcontrol

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * A resolved candidate node from the accessibility tree.
 *
 * @param node     The matched accessibility node.
 * @param nodeId   The dot-path id used in observe_screen (e.g. "0.2.1").
 * @param label    The visible text or content description used for matching.
 * @param bounds   Screen bounds of the node.
 * @param rank     Lower is better. Stable ids rank highest.
 */
data class ResolvedCandidate(
    val node: AccessibilityNodeInfo,
    val nodeId: String,
    val label: String,
    val bounds: Rect,
    val rank: Int
)

sealed class TargetResolution {
    /** Exactly one usable candidate found. */
    data class Resolved(val candidate: ResolvedCandidate) : TargetResolution()

    /** Multiple plausible candidates — unsafe to guess. */
    data class Ambiguous(val candidates: List<ResolvedCandidate>) : TargetResolution()

    /** No candidate found for the given selector. */
    data class NotFound(val selector: String) : TargetResolution()
}
