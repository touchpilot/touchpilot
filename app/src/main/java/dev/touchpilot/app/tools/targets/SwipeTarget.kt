package dev.touchpilot.app.tools.targets

/**
 * Argument-name conventions and input parsing for the `swipe` tool (issue #86).
 *
 * `swipe` has two input modes:
 *
 * 1. **Direction mode (primary)** — the caller passes [DirectionArg]
 *    (`left`/`right`/`up`/`down`). The gesture is planned within an optional
 *    container target (the [selectorArgs], mirroring [ScrollTarget]) or, when no
 *    container is given, the full active window.
 *
 * 2. **Coordinate mode** — the caller passes an explicit start/end point via
 *    [coordinateArgs]. All four coordinates are required together.
 *
 * Both modes accept an optional [DurationArg].
 *
 * Unlike [ScrollTarget], the container selector imposes **no** [TargetRole]:
 * swipe targets gesture surfaces (pagers, carousels, drawers, maps) that often
 * do not expose the accessibility `scrollable` flag, so constraining by role
 * would reject valid targets. `swipe` resolves through the shared
 * [TargetResolver] only to locate the container's bounds.
 */
object SwipeTarget {
    const val DirectionArg = "direction"

    const val StartXArg = "start_x"
    const val StartYArg = "start_y"
    const val EndXArg = "end_x"
    const val EndYArg = "end_y"
    const val DurationArg = "duration_ms"

    const val TargetTextArg = "target_text"
    const val TargetNodeIdArg = "target_node_id"
    const val TargetBoundsArg = "target_bounds"
    const val TargetViewIdArg = "target_view_id"
    const val TargetContentDescriptionArg = "target_content_description"

    val coordinateArgs: List<String> = listOf(StartXArg, StartYArg, EndXArg, EndYArg)

    val selectorArgs: List<String> = listOf(
        TargetTextArg,
        TargetNodeIdArg,
        TargetBoundsArg,
        TargetViewIdArg,
        TargetContentDescriptionArg,
    )

    fun hasTarget(args: Map<String, String>): Boolean {
        return selectorArgs.any { !args[it].isNullOrBlank() }
    }

    fun hasAnyCoordinate(args: Map<String, String>): Boolean {
        return coordinateArgs.any { !args[it].isNullOrBlank() }
    }

    fun selectorFromArgs(args: Map<String, String>): TargetSelector {
        return TargetSelector(
            text = args[TargetTextArg]?.takeIf { it.isNotBlank() }?.let { SelectorText.of(it) },
            contentDescription = args[TargetContentDescriptionArg]
                ?.takeIf { it.isNotBlank() }
                ?.let { SelectorText.of(it) },
            nodeId = args[TargetNodeIdArg]?.takeIf { it.isNotBlank() },
            bounds = args[TargetBoundsArg]?.takeIf { it.isNotBlank() }?.let { TargetBounds.parse(it) },
            viewIdResourceName = args[TargetViewIdArg]?.takeIf { it.isNotBlank() },
            source = SelectorSource.AGENT,
        )
    }

    /** Resolved duration, falling back to [SwipeGesture.DefaultDurationMs]. */
    fun durationOrDefault(args: Map<String, String>): Long {
        return args[DurationArg]?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            ?: SwipeGesture.DefaultDurationMs
    }

    /**
     * Build a coordinate-mode [SwipeRequest] when all four coordinates are
     * present and parse cleanly, otherwise null. Callers in direction mode rely
     * on null to fall through to direction planning.
     */
    fun explicitCoordinates(args: Map<String, String>): SwipeRequest? {
        if (coordinateArgs.any { args[it].isNullOrBlank() }) return null
        val sx = args[StartXArg]?.trim()?.toIntOrNull() ?: return null
        val sy = args[StartYArg]?.trim()?.toIntOrNull() ?: return null
        val ex = args[EndXArg]?.trim()?.toIntOrNull() ?: return null
        val ey = args[EndYArg]?.trim()?.toIntOrNull() ?: return null
        return SwipeRequest(sx, sy, ex, ey, durationOrDefault(args))
    }

    /**
     * Validate the optional coordinate inputs. Returns a useful error message
     * for partial, malformed, negative, or zero-length coordinate sets, or null
     * when coordinates are either absent or fully valid (issue #86 acceptance
     * criteria 3 and 4).
     */
    fun validateCoordinates(args: Map<String, String>): String? {
        val present = coordinateArgs.filter { !args[it].isNullOrBlank() }
        if (present.isEmpty()) return null
        if (present.size != coordinateArgs.size) {
            return "swipe coordinates require all of start_x, start_y, end_x, end_y"
        }
        val parsed = coordinateArgs.map { args[it]?.trim()?.toIntOrNull() }
        if (parsed.any { it == null }) {
            return "swipe coordinates must be integers"
        }
        val ints = parsed.map { it!! }
        if (ints.any { it < 0 }) {
            return "swipe coordinates must be non-negative"
        }
        val (sx, sy, ex, ey) = ints
        if (sx == ex && sy == ey) {
            return "swipe start and end coordinates must differ"
        }
        return null
    }

    fun validateDuration(args: Map<String, String>): String? {
        val duration = args[DurationArg]?.takeIf { it.isNotBlank() } ?: return null
        val parsed = duration.trim().toLongOrNull()
        if (parsed == null || parsed <= 0L) {
            return "duration_ms must be a positive number"
        }
        return null
    }
}
