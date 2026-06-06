package dev.touchpilot.app.agent

import org.json.JSONObject

object AgentCommandParser {
    fun parse(raw: String): AgentCommand {
        val jsonText = extractJsonObject(raw)
        val json = JSONObject(jsonText)

        val finalAnswer = json.optString("final", "").ifBlank { null }
        val tool = json.optString("tool", "").ifBlank { null }
        val argsJson = json.optJSONObject("args")
        val args = buildMap {
            if (argsJson != null) {
                for (key in argsJson.keys()) {
                    // A JSON null arrives as JSONObject.NULL (a non-null
                    // sentinel), so `?.toString()` does NOT short-circuit —
                    // it yields the literal "null". Treat it as empty/absent,
                    // matching optString()'s handling for `tool`/`final` above.
                    val value = if (argsJson.isNull(key)) "" else argsJson.opt(key)?.toString().orEmpty()
                    put(key, value)
                }
            }
        }

        return AgentCommand(
            tool = tool,
            args = args,
            finalAnswer = finalAnswer
        )
    }

    private fun extractJsonObject(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed

        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (fenced != null && fenced.startsWith("{") && fenced.endsWith("}")) {
            return fenced
        }

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1)
        }

        error("Model did not return a JSON object: $raw")
    }
}
