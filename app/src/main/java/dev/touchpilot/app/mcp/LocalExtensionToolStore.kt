package dev.touchpilot.app.mcp

import org.json.JSONArray
import org.json.JSONObject

data class LocalExtensionTool(
    val manifest: PluginApiManifest,
) {
    val name: String get() = manifest.name
    val description: String get() = manifest.description
    val endpoint: String get() = manifest.endpoint
}

sealed class LocalExtensionParseResult {
    abstract val name: String

    data class Valid(val tool: LocalExtensionTool) : LocalExtensionParseResult() {
        override val name: String get() = tool.name
    }

    data class Invalid(
        override val name: String,
        val endpoint: String?,
        val storageIndex: Int?,
        val errors: List<String>,
        val recommendedAction: String?,
    ) : LocalExtensionParseResult()
}

data class LocalExtensionLoad(
    val tools: List<LocalExtensionTool>,
    val invalid: List<LocalExtensionParseResult.Invalid>,
)

class LocalExtensionToolStore(
    private val readJson: () -> String,
    private val writeJson: (String) -> Unit,
) {
    fun load(): LocalExtensionLoad {
        val raw = readJson().trim()
        if (raw.isBlank()) return LocalExtensionLoad(emptyList(), emptyList())
        val array = runCatching { JSONArray(raw) }.getOrNull()
            ?: return LocalExtensionLoad(
                tools = emptyList(),
                invalid = listOf(
                    LocalExtensionParseResult.Invalid(
                        name = "(storage)",
                        endpoint = null,
                        storageIndex = null,
                        errors = listOf("local extension storage is not valid JSON"),
                        recommendedAction = "Clear local extension storage from Settings > MCP and re-register tools.",
                    )
                ),
            )

        val valid = mutableListOf<LocalExtensionTool>()
        val invalid = mutableListOf<LocalExtensionParseResult.Invalid>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            when (val result = parseEntry(obj, index)) {
                is LocalExtensionParseResult.Valid -> valid += result.tool
                is LocalExtensionParseResult.Invalid -> invalid += result
            }
        }
        return LocalExtensionLoad(valid, invalid)
    }

    fun all(): List<LocalExtensionTool> = load().tools

    fun add(tool: LocalExtensionTool): LocalExtensionParseResult {
        val errors = tool.manifest.validationErrors() + tool.manifest.compatibilityErrors()
        if (errors.isNotEmpty()) {
            return LocalExtensionParseResult.Invalid(
                name = tool.name.ifBlank { "(unnamed)" },
                endpoint = tool.endpoint.takeIf { it.isNotBlank() },
                storageIndex = null,
                errors = errors,
                recommendedAction = tool.manifest.recommendedAction(),
            )
        }

        val tools = all().toMutableList()
        tools.removeAll { it.name == tool.name && it.endpoint == tool.endpoint }
        tools += tool
        save(tools)
        return LocalExtensionParseResult.Valid(tool)
    }

    fun remove(name: String, endpoint: String): Boolean {
        return removeStoredEntry { obj ->
            obj.optString("name").trim() == name && obj.optString("endpoint").trim() == endpoint
        }
    }

    fun removeAt(index: Int): Boolean {
        val raw = readJson().trim()
        if (raw.isBlank()) return false
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        if (index < 0 || index >= array.length()) return false
        val updated = JSONArray()
        for (entryIndex in 0 until array.length()) {
            if (entryIndex != index) {
                updated.put(array.get(entryIndex))
            }
        }
        writeJson(updated.toString())
        return true
    }

    fun clearStorage(): Boolean {
        if (readJson().trim().isBlank()) return false
        writeJson("")
        return true
    }

    private fun parseEntry(obj: JSONObject, storageIndex: Int): LocalExtensionParseResult {
        val manifest = PluginApiManifest.parse(obj)
        val errors = manifest.validationErrors() + manifest.compatibilityErrors()
        if (errors.isNotEmpty()) {
            return LocalExtensionParseResult.Invalid(
                name = manifest.name.ifBlank { "(unnamed)" },
                endpoint = manifest.endpoint.takeIf { it.isNotBlank() },
                storageIndex = storageIndex,
                errors = errors,
                recommendedAction = manifest.recommendedAction(),
            )
        }
        return LocalExtensionParseResult.Valid(LocalExtensionTool(manifest))
    }

    private fun removeStoredEntry(matches: (JSONObject) -> Boolean): Boolean {
        val raw = readJson().trim()
        if (raw.isBlank()) return false
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return false
        val updated = JSONArray()
        var removed = false
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            if (matches(obj)) {
                removed = true
            } else {
                updated.put(obj)
            }
        }
        if (removed) writeJson(updated.toString())
        return removed
    }

    private fun save(tools: List<LocalExtensionTool>) {
        // Preserve any existing stored entries that are not being replaced by the
        // new list of valid tools. This avoids silently dropping incompatible or
        // legacy manifests that the user may want to inspect or remove.
        val raw = readJson().trim()
        val existing = runCatching { JSONArray(raw) }.getOrNull() ?: JSONArray()

        // Keys are name||endpoint. Use trimmed values for robust matching.
        val replaceKeys = tools.map { it.name.trim() + "||" + it.endpoint.trim() }.toSet()

        val merged = JSONArray()
        for (i in 0 until existing.length()) {
            val obj = existing.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val endpoint = obj.optString("endpoint").trim()
            val key = name + "||" + endpoint
            // If this existing entry matches one of the valid tools, skip it now
            // so the authoritative (possibly updated) tool JSON is appended below.
            if (name.isNotBlank() && endpoint.isNotBlank() && key in replaceKeys) {
                continue
            }
            merged.put(obj)
        }

        // Append authoritative JSON objects for the valid tools.
        tools.forEach { tool ->
            merged.put(
                JSONObject().apply {
                    put("api_version", tool.manifest.apiVersion)
                    put("name", tool.manifest.name)
                    put("description", tool.manifest.description)
                    put("endpoint", tool.manifest.endpoint)
                    put(
                        "feature_flags",
                        JSONObject().apply {
                            tool.manifest.featureFlags.forEach { (flag, enabled) ->
                                put(flag, enabled)
                            }
                        }
                    )
                }
            )
        }

        writeJson(merged.toString())
    }
}
