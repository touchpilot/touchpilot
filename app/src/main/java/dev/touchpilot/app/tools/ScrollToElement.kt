package dev.touchpilot.app.tools

import dev.touchpilot.app.tools.targets.ScrollTarget
import dev.touchpilot.app.tools.targets.TargetBounds

/**
 * Argument parsing, validation, and result shaping for the `scroll_to_element`
 * Android tool.
 *
 * `scroll_to_element` is a composite tool: it repeatedly drives the existing
 * `scroll` path (optionally within a resolved container) and, after each
 * scroll, runs the `find_element` matcher against the new screen until a node
 * matching a structured query becomes visible, the scroll stops making
 * progress (end of content), or a bounded scroll budget is exhausted.
 *
 * The element query reuses the `find_element` filter names (`text`,
 * `content_description`, `node_id`, `class_name`, `match`). The optional scroll
 * container reuses the `scroll` target selector names (`target_*`) plus
 * `direction`, so the two argument groups never collide. Raw query text never
 * leaves this helper: log and result data use lengths or counts only.
 */
object ScrollToElement {
    const val TextArg = "text"
    const val ContentDescriptionArg = "content_description"
    const val NodeIdArg = "node_id"
    const val ClassNameArg = "class_name"
    const val MatchArg = "match"
    const val DirectionArg = ScrollTarget.DirectionArg
    const val MaxScrollsArg = "max_scrolls"

    const val DefaultMaxScrolls = 8
    const val MinMaxScrolls = 1
    const val MaxMaxScrolls = 30
    const val DefaultDirection = "forward"

    private val FilterArgs = listOf(TextArg, ContentDescriptionArg, NodeIdArg, ClassNameArg)

    fun validate(args: Map<String, String>): String? {
        val filters = FilterArgs.filter { args[it].isNullOrBlank().not() }
        if (filters.isEmpty()) {
            return "scroll_to_element requires at least one filter: " +
                "text, content_description, node_id, or class_name"
        }
        val match = args[MatchArg]
        if (!match.isNullOrBlank() && MatchMode.fromWire(match) == null) {
            return "scroll_to_element match must be one of: exact, contains, semantic"
        }
        val direction = args[DirectionArg]
        if (!direction.isNullOrBlank() &&
            !direction.equals("forward", ignoreCase = true) &&
            !direction.equals("backward", ignoreCase = true)
        ) {
            return "Invalid scroll direction: $direction"
        }
        val maxScrolls = args[MaxScrollsArg]
        if (!maxScrolls.isNullOrBlank()) {
            val parsed = maxScrolls.toIntOrNull()
            if (parsed == null || parsed < MinMaxScrolls || parsed > MaxMaxScrolls) {
                return "max_scrolls must be an integer between $MinMaxScrolls and $MaxMaxScrolls"
            }
        }
        val malformedBounds = args[ScrollTarget.TargetBoundsArg]
            ?.takeIf { it.isNotBlank() }
            ?.let { TargetBounds.parse(it) == null }
            ?: false
        if (malformedBounds) {
            return "target_bounds must be left,top,right,bottom"
        }
        return null
    }

    fun matchMode(args: Map<String, String>): MatchMode {
        return MatchMode.fromWire(args[MatchArg]) ?: MatchMode.Default
    }

    fun queryFromArgs(args: Map<String, String>): FindElementQuery {
        return FindElementQuery(
            text = args[TextArg]?.takeIf { it.isNotBlank() },
            contentDescription = args[ContentDescriptionArg]?.takeIf { it.isNotBlank() },
            nodeId = args[NodeIdArg]?.takeIf { it.isNotBlank() },
            className = args[ClassNameArg]?.takeIf { it.isNotBlank() },
            match = matchMode(args),
        )
    }

    fun maxScrolls(args: Map<String, String>): Int {
        return (args[MaxScrollsArg]?.toIntOrNull() ?: DefaultMaxScrolls)
            .coerceIn(MinMaxScrolls, MaxMaxScrolls)
    }

    fun direction(args: Map<String, String>): String {
        val direction = args[DirectionArg]?.takeIf { it.isNotBlank() } ?: DefaultDirection
        return if (direction.equals("backward", ignoreCase = true)) "backward" else "forward"
    }

    /**
     * Arguments for the reused `scroll` execution: the resolved direction plus
     * any container target selector the caller supplied. Element-query keys are
     * intentionally excluded so they are never mistaken for a scroll container.
     */
    fun scrollArgs(args: Map<String, String>): Map<String, String> {
        return buildMap {
            put(ScrollTarget.DirectionArg, direction(args))
            ScrollTarget.selectorArgs.forEach { key ->
                args[key]?.takeIf { it.isNotBlank() }?.let { put(key, it) }
            }
        }
    }

    fun successResult(args: Map<String, String>, scrolls: Int, matchCount: Int): ToolResult {
        return ToolResult(
            ok = true,
            message = if (scrolls == 0) "Element already visible" else "Scrolled to element",
            data = resultData(args) + mapOf(
                "found" to "true",
                "scrolls_performed" to scrolls.toString(),
                "match_count" to matchCount.toString(),
            )
        )
    }

    fun notFoundResult(args: Map<String, String>, scrolls: Int, reason: String): ToolResult {
        return ToolResult(
            ok = false,
            message = reason,
            data = resultData(args) + mapOf(
                "found" to "false",
                "scrolls_performed" to scrolls.toString(),
            )
        )
    }

    /** Log-safe description: filter text is reduced to lengths. */
    fun logArgs(args: Map<String, String>): String {
        return "filters=${filterSummary(args)}, match=${matchMode(args).wireName}, " +
            "direction=${direction(args)}, max_scrolls=${maxScrolls(args)}"
    }

    private fun filterSummary(args: Map<String, String>): String {
        val parts = mutableListOf<String>()
        args[TextArg]?.takeIf { it.isNotBlank() }?.let { parts += "text_length=${it.length}" }
        args[ContentDescriptionArg]?.takeIf { it.isNotBlank() }
            ?.let { parts += "content_description_length=${it.length}" }
        args[NodeIdArg]?.takeIf { it.isNotBlank() }?.let { parts += "node_id=\"$it\"" }
        args[ClassNameArg]?.takeIf { it.isNotBlank() }?.let { parts += "class_name=\"$it\"" }
        return parts.joinToString(prefix = "[", postfix = "]")
    }

    private fun resultData(args: Map<String, String>): Map<String, String> {
        return mapOf(
            "match_mode" to matchMode(args).wireName,
            "direction" to direction(args),
        )
    }
}
