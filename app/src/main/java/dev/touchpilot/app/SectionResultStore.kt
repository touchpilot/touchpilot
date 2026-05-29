package dev.touchpilot.app

/**
 * Per-section storage for the "latest result" cards rendered on the Tools,
 * MCP, and Logs tabs.
 *
 * Each section owns its own latest-result string so that switching tabs cannot
 * leak the output of one section into another card. Writes only update the
 * section that produced the result; the other sections continue to render
 * whatever they last recorded, falling back to a per-section default message
 * when no result has been recorded yet.
 *
 * This class is not thread-safe and is intended to be mutated from the UI
 * thread, matching every call site in [MainActivity]. The renderers re-read
 * the latest value on every `showSection(...)` rebuild, so a write followed by
 * a section render reliably picks up the most recent result for that section.
 */
internal class SectionResultStore {
    private var toolsResult: String = ToolsDefault
    private var mcpResult: String = McpDefault
    private var logsResult: String = LogsDefault

    fun forTools(): String = toolsResult

    fun forMcp(): String = mcpResult

    fun forLogs(): String = logsResult

    fun recordToolsResult(message: String) {
        toolsResult = message
    }

    fun recordMcpResult(message: String) {
        mcpResult = message
    }

    fun recordLogsResult(message: String) {
        logsResult = message
    }

    companion object {
        const val ToolsDefault: String = "No tool run yet."
        const val McpDefault: String = "No MCP call yet."
        const val LogsDefault: String = "No debug trace exported yet."
    }
}
