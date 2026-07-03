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

        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```")
            .find(trimmed)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

        // Strict pass first: well-formed model output is parsed unchanged.
        if (fenced != null) {
            findBestJsonObject(fenced)?.let { return it }
        }
        findBestJsonObject(trimmed)?.let { return it }

        // Lenient fallback: repair the recoverable defects models emit around a
        // valid command (smart quotes, trailing commas, comments) and retry.
        if (fenced != null) {
            findBestJsonObject(LenientJson.repair(fenced))?.let { return it }
        }
        findBestJsonObject(LenientJson.repair(trimmed))?.let { return it }

        error("Model did not return a JSON object: $raw")
    }

    private fun findBestJsonObject(text: String): String? {
        val parsedCandidates = mutableListOf<Pair<String, JSONObject>>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val end = findMatchingCloseBrace(text, i)
                if (end < 0) {
                    i++
                    continue
                }
                val candidate = text.substring(i, end + 1)
                runCatching { JSONObject(candidate) }
                    .getOrNull()
                    ?.let { parsedCandidates += candidate to it }
                i = end + 1
            } else {
                i++
            }
        }
        return parsedCandidates
            .lastOrNull { (_, json) -> json.has("tool") || json.has("final") }
            ?.first
            ?: parsedCandidates.lastOrNull()?.first
    }

    private fun findMatchingCloseBrace(text: String, startIndex: Int): Int {
        var depth = 0
        var i = startIndex
        var inString = false
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                when (c) {
                    '\\' -> if (i + 1 < text.length) i++
                    '"' -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            i++
        }
        return -1
    }
}
