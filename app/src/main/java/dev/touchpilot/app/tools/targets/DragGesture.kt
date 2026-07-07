package dev.touchpilot.app.tools.targets

/**
 * A fully resolved drag-and-drop: absolute start/end screen coordinates plus
 * the pickup dwell and travel durations. Like [SwipeRequest], this is the
 * deterministic "execution request" the tool layer builds and hands to the
 * Accessibility service — the service only ever sees plain coordinates, so the
 * gesture-planning logic stays in the tools layer (and unit-testable) without
 * inverting the androidcontrol → tools dependency direction.
 *
 * A drag differs from a [SwipeRequest] in that it holds at the start point for
 * [holdMs] before travelling. That dwell is what makes long-press-to-reorder
 * lists, drag handles, and drag-to-target surfaces enter drag mode; a plain
 * swipe fires too quickly to pick the item up.
 */
data class DragRequest(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val holdMs: Long,
    val moveMs: Long,
) {
    /** True when both endpoints fall inside (inclusive) the given [area]. */
    fun within(area: TargetBounds): Boolean {
        return startX in area.left..area.right &&
            endX in area.left..area.right &&
            startY in area.top..area.bottom &&
            endY in area.top..area.bottom
    }

    /** True when the start and end points are the same pixel (nothing to drag). */
    val isZeroLength: Boolean get() = startX == endX && startY == endY
}

/**
 * Plans the concrete [DragRequest] for a drag-and-drop gesture (issue: no
 * accessibility action reorders list items or moves widgets — only a dispatched
 * press-hold-move gesture does).
 *
 * Selector-mode drags travel from the center of the resolved source element to
 * the center of the resolved destination element. Coordinate-mode drags use the
 * caller's explicit points verbatim. Both modes share the same pickup dwell so
 * behavior is identical regardless of how the endpoints were located.
 */
object DragGesture {
    /**
     * Pickup dwell before travel begins. Long enough to cross the platform
     * long-press threshold (~500 ms on most surfaces) so reorderable rows and
     * drag handles register the pickup, short enough to keep the whole gesture
     * responsive.
     */
    const val DefaultHoldMs = 550L

    /** Travel duration once the item has been picked up. */
    const val DefaultMoveMs = 400L

    /** Shortest dwell that still reliably triggers a long-press pickup. */
    const val MinHoldMs = 300L

    /** Upper dwell bound; anything longer risks a gesture-dispatch timeout. */
    const val MaxHoldMs = 3_000L

    /** Center-to-center drag between two resolved element rectangles. */
    fun between(
        source: TargetBounds,
        destination: TargetBounds,
        holdMs: Long = DefaultHoldMs,
        moveMs: Long = DefaultMoveMs,
    ): DragRequest {
        return DragRequest(
            startX = source.centerX,
            startY = source.centerY,
            endX = destination.centerX,
            endY = destination.centerY,
            holdMs = holdMs.coerceIn(MinHoldMs, MaxHoldMs),
            moveMs = moveMs,
        )
    }

    /** Coordinate-mode drag between two explicit screen points. */
    fun betweenPoints(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        holdMs: Long = DefaultHoldMs,
        moveMs: Long = DefaultMoveMs,
    ): DragRequest {
        return DragRequest(
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            holdMs = holdMs.coerceIn(MinHoldMs, MaxHoldMs),
            moveMs = moveMs,
        )
    }
}
