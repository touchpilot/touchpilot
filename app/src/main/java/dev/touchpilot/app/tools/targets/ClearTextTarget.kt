package dev.touchpilot.app.tools.targets

object ClearTextTarget {
    const val TargetTextArg = "target_text"
    const val TargetNodeIdArg = "target_node_id"
    const val TargetBoundsArg = "target_bounds"
    const val TargetViewIdArg = "target_view_id"
    const val TargetContentDescriptionArg = "target_content_description"

    val selectorArgs = listOf(
        TargetTextArg,
        TargetNodeIdArg,
        TargetBoundsArg,
        TargetViewIdArg,
        TargetContentDescriptionArg,
    )

    fun hasTarget(args: Map<String, String>): Boolean {
        return selectorArgs.any { !args[it].isNullOrBlank() }
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
            role = TargetRole.INPUT,
            source = SelectorSource.AGENT,
        )
    }
}
