package dev.touchpilot.app.tools

/**
 * Argument parsing, validation, and result shaping for the `wait_for_element`
 * Android tool.
 *
 * `wait_for_element` polls the current [dev.touchpilot.app.screen.ScreenContext]
 * until a node matching a structured query appears, or a bounded timeout
 * elapses. It is the structured counterpart of `wait_for_ui` (which only waits
 * for a text substring) and the polling counterpart of `find_element` (which
 * inspects a single snapshot). It executes no Accessibility action, so it is a
 * LOW-risk, read-only wait.
 *
 * The query is intentionally the same shape as `find_element`, so both tools
 * share [FindElementMatcher] and the same redaction/sensitivity semantics. Raw
 * query text never leaves this helper: log and result data use lengths or
 * redacted forms only.
 */
object WaitForElement {
    const val TextArg = "text"
    const val ContentDescriptionArg = "content_description"
    const val NodeIdArg = "node_id"
    const val ClassNameArg = "class_name"
    const val MatchArg = "match"
    const val TimeoutArg = "timeout_ms"

    const val DefaultTimeoutMs = 5_000L
    const val MinTimeoutMs = 250L
    const val MaxTimeoutMs = 30_000L

    private val FilterArgs = listOf(TextArg, ContentDescriptionArg, NodeIdArg, ClassNameArg)

    fun validate(args: Map<String, String>): String? = validateQuery(args, "wait_for_element")

    /**
     * Shared filter + match-mode validation for `wait_for_element` and its
     * variants (e.g. `wait_for_element_gone`). [toolName] names the tool in the
     * returned error message so each variant reports against itself.
     */
    fun validateQuery(args: Map<String, String>, toolName: String): String? {
        val filters = FilterArgs.filter { args[it].isNullOrBlank().not() }
        if (filters.isEmpty()) {
            return "$toolName requires at least one filter: " +
                "text, content_description, node_id, or class_name"
        }
        val match = args[MatchArg]
        if (!match.isNullOrBlank() && MatchMode.fromWire(match) == null) {
            return "$toolName match must be one of: exact, contains, semantic"
        }
        return null
    }

    fun timeoutMs(args: Map<String, String>): Long {
        return (args[TimeoutArg]?.toLongOrNull() ?: DefaultTimeoutMs)
            .coerceIn(MinTimeoutMs, MaxTimeoutMs)
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

    fun successResult(args: Map<String, String>, matchCount: Int): ToolResult {
        return ToolResult(
            ok = true,
            message = "waitForElement",
            data = resultData(args) + mapOf(
                "matched" to "true",
                "count" to matchCount.toString(),
            )
        )
    }

    fun timeoutResult(args: Map<String, String>, timeoutMs: Long): ToolResult {
        return ToolResult(
            ok = false,
            message = "Timed out waiting for element matching ${expectedSummary(args)}",
            data = resultData(args) + mapOf(
                "matched" to "false",
                "timed_out" to "true",
                "timeout_ms" to timeoutMs.toString(),
            )
        )
    }

    /**
     * Log-safe description of the query. Filter text is reduced to lengths so
     * sensitive query content is never written to the tool execution log.
     */
    fun logArgs(args: Map<String, String>, timeoutMs: Long): String {
        return "filters=${filterSummary(args)}, match=${matchMode(args).wireName}, timeout_ms=$timeoutMs"
    }

    private fun expectedSummary(args: Map<String, String>): String {
        return "${filterSummary(args)} (match=${matchMode(args).wireName})"
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
        return buildMap {
            put("match_mode", matchMode(args).wireName)
            put("filter_count", FilterArgs.count { args[it].isNullOrBlank().not() }.toString())
        }
    }
}
