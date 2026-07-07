package dev.touchpilot.app.tools

/**
 * Argument parsing, validation, and result shaping for the `wait_for_element_gone`
 * Android tool.
 *
 * `wait_for_element_gone` polls the current
 * [dev.touchpilot.app.screen.ScreenContext] until NO node matches a structured
 * query — that is, until a previously visible element (a loading spinner,
 * progress dialog, toast, or transient banner) has disappeared — or a bounded
 * timeout elapses. It is the inverse of `wait_for_element`: where that tool waits
 * for an element to appear, this one waits for one to go away, which an agent
 * needs before acting on the screen that a transition settles into.
 *
 * It reuses [WaitForElement]'s query shape and [FindElementMatcher], executes no
 * Accessibility action, and is therefore a LOW-risk, read-only wait. Raw query
 * text never leaves this helper: results carry only the match mode and counts.
 */
object WaitForElementGone {
    const val ToolName = "wait_for_element_gone"

    fun validate(args: Map<String, String>): String? =
        WaitForElement.validateQuery(args, ToolName)

    /** Result when no matching element remains (the element has gone). */
    fun goneResult(args: Map<String, String>): ToolResult {
        return ToolResult(
            ok = true,
            message = "waitForElementGone",
            data = resultData(args) + mapOf("gone" to "true"),
        )
    }

    /** Result when a matching element is still present at the deadline. */
    fun timeoutResult(args: Map<String, String>, timeoutMs: Long, lastMatchCount: Int): ToolResult {
        return ToolResult(
            ok = false,
            message = "Timed out waiting for element to disappear",
            data = resultData(args) + mapOf(
                "gone" to "false",
                "timed_out" to "true",
                "timeout_ms" to timeoutMs.toString(),
                "last_match_count" to lastMatchCount.toString(),
            ),
        )
    }

    private fun resultData(args: Map<String, String>): Map<String, String> {
        return mapOf("match_mode" to WaitForElement.matchMode(args).wireName)
    }
}
