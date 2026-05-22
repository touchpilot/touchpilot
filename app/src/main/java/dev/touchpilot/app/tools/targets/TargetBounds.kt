package dev.touchpilot.app.tools.targets

import org.json.JSONObject

/**
 * Rectangular target bounds in absolute screen coordinates. Kept self-
 * contained so the selector package does not reach back into
 * `dev.touchpilot.app.screen` for primitives.
 *
 * Coordinates follow the Android convention: `left,top` is the top-left of
 * the rectangle and `right,bottom` is the bottom-right (exclusive). A
 * rectangle is [isEmpty] when either dimension is non-positive — same
 * semantics as `android.graphics.Rect.isEmpty()` — so callers can refuse to
 * dispatch a tap against a zero-area target without recomputing the check
 * themselves.
 */
data class TargetBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    fun toJson(): JSONObject = JSONObject().apply {
        put("left", left)
        put("top", top)
        put("right", right)
        put("bottom", bottom)
    }

    /**
     * `"left,top,right,bottom"` — matches the format the existing `tap` tool
     * accepts in its loose-args `bounds` argument, so this representation can
     * round-trip cleanly through legacy callers.
     */
    fun toBoundsArg(): String = "$left,$top,$right,$bottom"

    companion object {
        val Unknown: TargetBounds = TargetBounds(left = 0, top = 0, right = 0, bottom = 0)

        fun fromJson(json: JSONObject): TargetBounds = TargetBounds(
            left = json.optInt("left", 0),
            top = json.optInt("top", 0),
            right = json.optInt("right", 0),
            bottom = json.optInt("bottom", 0),
        )

        /**
         * Parse the loose `bounds` argument the current `tap` tool accepts:
         * `"left,top,right,bottom"`, optionally wrapped in `[ ]` and tolerant
         * of whitespace. Returns null if the string does not yield exactly
         * four integers.
         */
        fun parse(boundsText: String): TargetBounds? {
            val values = boundsText
                .split(",", " ", "[", "]")
                .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() }
            if (values.size != 4) return null
            return TargetBounds(values[0], values[1], values[2], values[3])
        }
    }
}
