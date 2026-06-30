package dev.touchpilot.app.mcp

import org.json.JSONArray
import org.json.JSONObject

data class LocalExtensionTool(
    val name: String,
    val description: String,
    val endpoint: String,
)

class LocalExtensionToolStore(
    private val readJson: () -> String,
    private val writeJson: (String) -> Unit,
) {
    fun all(): List<LocalExtensionTool> {
        val raw = readJson().trim()
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val name = obj.optString("name").trim()
                val description = obj.optString("description").trim()
                val endpoint = obj.optString("endpoint").trim()
                if (name.isNotBlank() && endpoint.isNotBlank()) {
                    add(LocalExtensionTool(name, description, endpoint))
                }
            }
        }
    }

    fun add(tool: LocalExtensionTool) {
        val tools = all().toMutableList()
        tools.removeAll { it.name == tool.name && it.endpoint == tool.endpoint }
        tools += tool
        save(tools)
    }

    fun remove(name: String, endpoint: String): Boolean {
        val tools = all().toMutableList()
        val removed = tools.removeAll { it.name == name && it.endpoint == endpoint }
        if (removed) save(tools)
        return removed
    }

    private fun save(tools: List<LocalExtensionTool>) {
        val json = JSONArray().apply {
            tools.forEach { tool ->
                put(
                    JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("endpoint", tool.endpoint)
                    }
                )
            }
        }
        writeJson(json.toString())
    }
}
