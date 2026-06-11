package dev.touchpilot.app

/**
 * Stores the latest MCP result string shown on the Settings MCP panel.
 *
 * This class is not thread-safe and is intended to be mutated from the UI
 * thread. The Settings screen re-reads the value on every rebuild, so a write
 * followed by a render reliably picks up the most recent result.
 */
internal class McpResultStore {
    private var result: String = Default

    fun forMcp(): String = result

    fun recordMcpResult(message: String) {
        result = message
    }

    companion object {
        const val Default: String = "No MCP call yet."
    }
}
