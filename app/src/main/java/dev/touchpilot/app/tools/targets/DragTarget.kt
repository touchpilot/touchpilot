package dev.touchpilot.app.tools.targets

/**
 * Argument-name conventions and input parsing for the `drag_and_drop` tool.
 *
 * `drag_and_drop` has two input modes, mirroring [SwipeTarget]:
 *
 * 1. **Selector mode (primary)** — the caller passes a source selector
 *    ([sourceArgs]) and a destination selector ([destinationArgs]). Each side is
 *    resolved through the shared [TargetResolver]; the gesture then travels from
 *    the center of the resolved source element to the center of the resolved
 *    destination element. This is how an agent reorders a list ("drag Groceries
 *    above Work") or drops one element onto another.
 *
 * 2. **Coordinate mode** — the caller passes an explicit start/end point via
 *    [coordinateArgs]. All four coordinates are required together. Useful for
 *    canvas/drawing surfaces that expose no addressable nodes.
 *
 * Both modes accept an optional [HoldArg] (pickup dwell) and [DurationArg]
 * (travel time). Unlike a swipe, the pickup dwell is what makes the surface
 * enter drag mode, so it is modeled explicitly.
 */
object DragTarget {
    const val SourceTextArg = "source_text"
    const val SourceNodeIdArg = "source_node_id"
    const val SourceBoundsArg = "source_bounds"
    const val SourceViewIdArg = "source_view_id"
    const val SourceContentDescriptionArg = "source_content_description"

    const val DestinationTextArg = "destination_text"
    const val DestinationNodeIdArg = "destination_node_id"
    const val DestinationBoundsArg = "destination_bounds"
    const val DestinationViewIdArg = "destination_view_id"
    const val DestinationContentDescriptionArg = "destination_content_description"

    const val StartXArg = "start_x"
    const val StartYArg = "start_y"
    const val EndXArg = "end_x"
    const val EndYArg = "end_y"

    const val HoldArg = "hold_ms"
    const val DurationArg = "duration_ms"

    val sourceArgs: List<String> = listOf(
        SourceTextArg,
        SourceNodeIdArg,
        SourceBoundsArg,
        SourceViewIdArg,
        SourceContentDescriptionArg,
    )

    val destinationArgs: List<String> = listOf(
        DestinationTextArg,
        DestinationNodeIdArg,
        DestinationBoundsArg,
        DestinationViewIdArg,
        DestinationContentDescriptionArg,
    )

    val coordinateArgs: List<String> = listOf(StartXArg, StartYArg, EndXArg, EndYArg)

    val allArgs: List<String> = sourceArgs + destinationArgs + coordinateArgs + listOf(HoldArg, DurationArg)

    fun hasSource(args: Map<String, String>): Boolean = sourceArgs.any { !args[it].isNullOrBlank() }

    fun hasDestination(args: Map<String, String>): Boolean =
        destinationArgs.any { !args[it].isNullOrBlank() }

    fun hasAnyCoordinate(args: Map<String, String>): Boolean =
        coordinateArgs.any { !args[it].isNullOrBlank() }

    fun sourceSelector(args: Map<String, String>): TargetSelector = selectorFor(
        args = args,
        textArg = SourceTextArg,
        contentDescriptionArg = SourceContentDescriptionArg,
        nodeIdArg = SourceNodeIdArg,
        boundsArg = SourceBoundsArg,
        viewIdArg = SourceViewIdArg,
    )

    fun destinationSelector(args: Map<String, String>): TargetSelector = selectorFor(
        args = args,
        textArg = DestinationTextArg,
        contentDescriptionArg = DestinationContentDescriptionArg,
        nodeIdArg = DestinationNodeIdArg,
        boundsArg = DestinationBoundsArg,
        viewIdArg = DestinationViewIdArg,
    )

    private fun selectorFor(
        args: Map<String, String>,
        textArg: String,
        contentDescriptionArg: String,
        nodeIdArg: String,
        boundsArg: String,
        viewIdArg: String,
    ): TargetSelector {
        return TargetSelector(
            text = args[textArg]?.takeIf { it.isNotBlank() }?.let { SelectorText.of(it) },
            contentDescription = args[contentDescriptionArg]
                ?.takeIf { it.isNotBlank() }
                ?.let { SelectorText.of(it) },
            nodeId = args[nodeIdArg]?.takeIf { it.isNotBlank() },
            bounds = args[boundsArg]?.takeIf { it.isNotBlank() }?.let { TargetBounds.parse(it) },
            viewIdResourceName = args[viewIdArg]?.takeIf { it.isNotBlank() },
            source = SelectorSource.AGENT,
        )
    }

    /** Resolved pickup dwell, falling back to [DragGesture.DefaultHoldMs]. */
    fun holdOrDefault(args: Map<String, String>): Long {
        return args[HoldArg]?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: DragGesture.DefaultHoldMs
    }

    /** Resolved travel duration, falling back to [DragGesture.DefaultMoveMs]. */
    fun durationOrDefault(args: Map<String, String>): Long {
        return args[DurationArg]?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: DragGesture.DefaultMoveMs
    }

    /**
     * Build a coordinate-mode [DragRequest] when all four coordinates are
     * present and parse cleanly, otherwise null. Callers in selector mode rely
     * on null to fall through to selector resolution.
     */
    fun explicitCoordinates(args: Map<String, String>): DragRequest? {
        if (coordinateArgs.any { args[it].isNullOrBlank() }) return null
        val sx = args[StartXArg]?.trim()?.toIntOrNull() ?: return null
        val sy = args[StartYArg]?.trim()?.toIntOrNull() ?: return null
        val ex = args[EndXArg]?.trim()?.toIntOrNull() ?: return null
        val ey = args[EndYArg]?.trim()?.toIntOrNull() ?: return null
        return DragGesture.betweenPoints(sx, sy, ex, ey, holdOrDefault(args), durationOrDefault(args))
    }

    /**
     * Validate the optional coordinate inputs. Returns a useful error message
     * for partial, malformed, negative, or zero-length coordinate sets, or null
     * when coordinates are either absent or fully valid.
     */
    fun validateCoordinates(args: Map<String, String>): String? {
        val present = coordinateArgs.filter { !args[it].isNullOrBlank() }
        if (present.isEmpty()) return null
        if (present.size != coordinateArgs.size) {
            return "drag_and_drop coordinates require all of start_x, start_y, end_x, end_y"
        }
        val parsed = coordinateArgs.map { args[it]?.trim()?.toIntOrNull() }
        if (parsed.any { it == null }) {
            return "drag_and_drop coordinates must be integers"
        }
        val ints = parsed.map { it!! }
        if (ints.any { it < 0 }) {
            return "drag_and_drop coordinates must be non-negative"
        }
        val (sx, sy, ex, ey) = ints
        if (sx == ex && sy == ey) {
            return "drag_and_drop start and end coordinates must differ"
        }
        return null
    }

    fun validateTimings(args: Map<String, String>): String? {
        args[HoldArg]?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = raw.trim().toLongOrNull()
            if (parsed == null || parsed <= 0L) {
                return "hold_ms must be a positive number"
            }
        }
        args[DurationArg]?.takeIf { it.isNotBlank() }?.let { raw ->
            val parsed = raw.trim().toLongOrNull()
            if (parsed == null || parsed <= 0L) {
                return "duration_ms must be a positive number"
            }
        }
        return null
    }

    fun validateBounds(args: Map<String, String>): String? {
        val malformedSource = args[SourceBoundsArg]
            ?.takeIf { it.isNotBlank() }
            ?.let { TargetBounds.parse(it) == null }
            ?: false
        if (malformedSource) return "source_bounds must be left,top,right,bottom"
        val malformedDestination = args[DestinationBoundsArg]
            ?.takeIf { it.isNotBlank() }
            ?.let { TargetBounds.parse(it) == null }
            ?: false
        if (malformedDestination) return "destination_bounds must be left,top,right,bottom"
        return null
    }
}
