package dev.touchpilot.app

internal class SectionResultStore {
    private var mcpResult: String = "No MCP call yet."

    fun forMcp(): String = mcpResult

    fun recordMcpResult(message: String) {
        mcpResult = message
    }
}
