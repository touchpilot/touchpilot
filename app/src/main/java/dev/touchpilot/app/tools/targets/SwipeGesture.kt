package dev.touchpilot.app.tools.targets

import kotlin.math.roundToInt

/**
 * Direction of a `swipe` gesture, expressed as the direction the finger
 * travels (issue #86).
 *
 * "swipe left" means the contact point moves from right to left — the gesture a
 * user makes to advance a horizontal pager to the next page or close a drawer.
 * This is the natural reading of the spoken instruction and is kept distinct
 * from content movement to avoid the scroll-vs-swipe ambiguity called out in
 * the issue.
 */
enum class SwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN;

    companion object {
        fun parse(raw: String?): SwipeDirection? {
            return when (raw?.trim()?.lowercase()) {
                "left" -> LEFT
                "right" -> RIGHT
                "up" -> UP
                "down" -> DOWN
                else -> null
            }
        }
    }
}

/**
 * A fully resolved swipe: absolute start/end screen coordinates plus the
 * gesture duration. This is the deterministic "execution request" the tool
 * layer builds and hands to the Accessibility service — the service only ever
 * sees plain coordinates, so the gesture-planning logic stays in the tools
 * layer (and unit-testable) without inverting the androidcontrol → tools
 * dependency direction.
 */
data class SwipeRequest(
    val startX: Int,
    val startY: Int,
    val endX: Int,
    val endY: Int,
    val durationMs: Long,
) {
    /** True when both endpoints fall inside (inclusive) the given [area]. */
    fun within(area: TargetBounds): Boolean {
        return startX in area.left..area.right &&
            endX in area.left..area.right &&
            startY in area.top..area.bottom &&
            endY in area.top..area.bottom
    }
}

/**
 * Plans the concrete [SwipeRequest] for a swipe.
 *
 * Direction-based swipes travel along the center axis of the supplied [area]
 * (the resolved container, or the active window when no container target was
 * given), keeping an [EdgeInsetFraction] margin from each edge so the gesture
 * starts and ends well inside the surface rather than on a system edge gesture
 * zone.
 */
object SwipeGesture {
    /** Typical fling-length swipe; long enough to register, short enough to feel responsive. */
    const val DefaultDurationMs = 300L

    /** Fraction of each edge left untouched so the stroke avoids edge gesture zones. */
    const val EdgeInsetFraction = 0.1f

    fun forDirection(
        direction: SwipeDirection,
        area: TargetBounds,
        durationMs: Long = DefaultDurationMs,
    ): SwipeRequest {
        val insetX = (area.width * EdgeInsetFraction).roundToInt()
        val insetY = (area.height * EdgeInsetFraction).roundToInt()
        val left = area.left + insetX
        val right = area.right - insetX
        val top = area.top + insetY
        val bottom = area.bottom - insetY
        val centerX = area.centerX
        val centerY = area.centerY

        return when (direction) {
            SwipeDirection.LEFT -> SwipeRequest(right, centerY, left, centerY, durationMs)
            SwipeDirection.RIGHT -> SwipeRequest(left, centerY, right, centerY, durationMs)
            SwipeDirection.UP -> SwipeRequest(centerX, bottom, centerX, top, durationMs)
            SwipeDirection.DOWN -> SwipeRequest(centerX, top, centerX, bottom, durationMs)
        }
    }
}
