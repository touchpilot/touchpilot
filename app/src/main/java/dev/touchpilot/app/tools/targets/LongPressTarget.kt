package dev.touchpilot.app.tools.targets

/**
 * Argument-name conventions for the `long_press` tool (issue #85).
 *
 * Unlike [TypeTextTarget] and [ScrollTarget], `long_press` carries no primary
 * non-target argument (there is no `text` payload to type and no `direction`),
 * so its selector arguments use the same bare names the `tap` tool already
 * exposes (`text`, `node_id`, `bounds`) plus `view_id`. This keeps the
 * long_press contract familiar to any caller already emitting tap selectors
 * while still resolving through the shared [TargetResolver] path.
 *
 * No [TargetRole] is set on the produced selector: long-press can legitimately
 * target any element (buttons, list items, plain text, images), so constraining
 * by role would reject valid targets.
 */
object LongPressTarget {
    const val TextArg = "text"
    const val NodeIdArg = "node_id"
    const val BoundsArg = "bounds"
    const val ViewIdArg = "view_id"

    val selectorArgs: List<String> = listOf(
        TextArg,
        NodeIdArg,
        BoundsArg,
        ViewIdArg,
    )

    fun hasTarget(args: Map<String, String>): Boolean {
        return selectorArgs.any { !args[it].isNullOrBlank() }
    }

    fun selectorFromArgs(args: Map<String, String>): TargetSelector {
        return TargetSelector(
            text = args[TextArg]?.takeIf { it.isNotBlank() }?.let { SelectorText.of(it) },
            nodeId = args[NodeIdArg]?.takeIf { it.isNotBlank() },
            bounds = args[BoundsArg]?.takeIf { it.isNotBlank() }?.let { TargetBounds.parse(it) },
            viewIdResourceName = args[ViewIdArg]?.takeIf { it.isNotBlank() },
            source = SelectorSource.AGENT,
        )
    }
}
