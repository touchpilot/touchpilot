package dev.touchpilot.app.mcp

import org.json.JSONObject

data class McpTool(
    val name: String,
    val description: String
)

data class McpToolCallResult(
    val ok: Boolean,
    val message: String
)

interface McpClient {
    fun initialize(): String
    fun listTools(): List<McpTool>
    fun callTool(name: String, args: JSONObject): McpToolCallResult
}
