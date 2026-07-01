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
                        errors = listOf("local extension storage is not valid JSON"),
                        recommendedAction = "Re-register extension tools from Settings > MCP.",
                    )
                ),
            )

        val valid = mutableListOf<LocalExtensionTool>()
        val invalid = mutableListOf<LocalExtensionParseResult.Invalid>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            when (val result = parseEntry(obj)) {
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
        val tools = all().toMutableList()
        val removed = tools.removeAll { it.name == name && it.endpoint == endpoint }
        if (removed) save(tools)
        return removed
    }

    private fun parseEntry(obj: JSONObject): LocalExtensionParseResult {
        val manifest = PluginApiManifest.parse(obj)
        val errors = manifest.validationErrors() + manifest.compatibilityErrors()
        if (errors.isNotEmpty()) {
            return LocalExtensionParseResult.Invalid(
                name = manifest.name.ifBlank { "(unnamed)" },
                errors = errors,
                recommendedAction = manifest.recommendedAction(),
            )
        }
        return LocalExtensionParseResult.Valid(LocalExtensionTool(manifest))
    }

    private fun save(tools: List<LocalExtensionTool>) {
        val json = JSONArray().apply {
            tools.forEach { tool ->
                put(
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
        }
        writeJson(json.toString())
    }
}
