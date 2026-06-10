package dev.touchpilot.app.mcp

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong

class McpHttpClient(
    private val endpoint: String
) {
    private val nextId = AtomicLong(1L)
    private var sessionId: String? = null

    fun initialize(): String {
        val result = request(
            method = "initialize",
            params = JSONObject()
                .put("protocolVersion", ProtocolVersion)
                .put(
                    "capabilities",
                    JSONObject()
                )
                .put(
                    "clientInfo",
                    JSONObject()
                        .put("name", "TouchPilot")
                        .put("version", "0.1.0")
                )
        )

        notifyInitialized()
        return result.toString(2)
    }

    fun listTools(): List<McpTool> {
        val result = request("tools/list", JSONObject())
        val tools = result.optJSONArray("tools") ?: JSONArray()
        return buildList {
            for (index in 0 until tools.length()) {
                val tool = tools.optJSONObject(index) ?: continue
                add(
                    McpTool(
                        name = tool.optString("name"),
                        description = tool.optString("description")
                    )
                )
            }
        }
    }

    fun callTool(name: String, args: JSONObject): McpToolCallResult {
        val result = request(
            method = "tools/call",
            params = JSONObject()
                .put("name", name)
                .put("arguments", args)
        )

        val isError = result.optBoolean("isError", false)
        val content = result.optJSONArray("content")
        val message = if (content == null) {
            result.toString(2)
        } else {
            renderContent(content)
        }

        return McpToolCallResult(ok = !isError, message = message)
    }

    private fun notifyInitialized() {
        runCatching {
            send(
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("method", "notifications/initialized")
                    .put("params", JSONObject())
            )
        }
    }

    private fun request(method: String, params: JSONObject): JSONObject {
        val id = nextId.getAndIncrement()
        val response = send(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params)
        )

        response.optJSONObject("error")?.let { error ->
            error("MCP $method failed: ${error.optString("message", error.toString())}")
        }

        return response.optJSONObject("result") ?: JSONObject()
    }

    private fun send(body: JSONObject): JSONObject {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        sessionId?.let { connection.setRequestProperty(SessionHeader, it) }

        OutputStreamWriter(connection.outputStream).use { writer ->
            writer.write(body.toString())
        }

        val responseText = if (connection.responseCode in 200..299) {
            connection.getHeaderField(SessionHeader)?.trim()?.takeIf { it.isNotBlank() }?.let {
                sessionId = it
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("MCP HTTP ${connection.responseCode}: $errorText")
        }

        return parseResponse(responseText)
    }

    internal fun parseResponse(responseText: String): JSONObject {
        val trimmed = responseText.trim()
        if (trimmed.startsWith("{")) return JSONObject(trimmed)

        // SSE events are separated by a blank line. Within an event the `data:`
        // lines belong to that event only and are joined with `\n` — they must
        // not be merged with the next event's data, or a leading notification
        // would silently swallow the actual JSON-RPC response.
        val events = trimmed.split(Regex("\\r?\\n\\r?\\n"))
            .mapNotNull { block ->
                val payload = block.lineSequence()
                    .filter { it.startsWith("data:") }
                    .joinToString(separator = "\n") { it.removePrefix("data:").trim() }
                    .trim()
                if (payload.startsWith("{")) {
                    runCatching { JSONObject(payload) }.getOrNull()
                } else {
                    null
                }
            }

        // Prefer the JSON-RPC response event (carries `result` or `error`);
        // notifications such as `notifications/progress` have neither and must
        // not shadow the real response.
        val response = events.firstOrNull { it.has("result") || it.has("error") }
            ?: events.firstOrNull()

        return response ?: error("MCP response did not contain a JSON object")
    }

    private fun renderContent(content: JSONArray): String {
        return buildString {
            for (index in 0 until content.length()) {
                val item = content.optJSONObject(index) ?: continue
                if (isNotEmpty()) appendLine()
                append(
                    when (item.optString("type")) {
                        "text" -> item.optString("text")
                        else -> item.toString()
                    }
                )
            }
        }
    }

    private companion object {
        const val ProtocolVersion = "2025-11-25"
        const val SessionHeader = "Mcp-Session-Id"
    }
}
