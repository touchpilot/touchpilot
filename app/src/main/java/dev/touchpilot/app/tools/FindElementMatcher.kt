package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deterministic, local matcher for the `find_element` Android tool.
 *
 * `find_element` is a read-only lookup primitive: it inspects the current
 * normalized [ScreenContext] and returns ranked candidate nodes that match a
 * structured query. It does not execute any Accessibility action, so its
 * output is safe to expose to the local router and small model as a structured
 * alternative to parsing raw `observe_screen` output.
 *
 * Candidate fields preserve the same redaction and sensitivity semantics as
 * the rest of the screen model: a node's `displaySafe` text is what we emit,
 * a `sensitive` flag is forwarded from [ScreenNode.sensitive] / the text
 * heuristics, and password fields stay flagged even when their label is
 * harmless.
 */
class FindElementMatcher {

    fun match(context: ScreenContext, query: FindElementQuery): List<FindElementCandidate> {
        if (!query.hasAnyFilter()) return emptyList()
        if (context.nodes.isEmpty()) return emptyList()

        return context.nodes
            .mapNotNull { node -> scoreNode(node, query) }
            .sortedWith(
                compareByDescending<FindElementCandidate> { it.score }
                    .thenByDescending { it.node.clickable }
                    .thenBy { it.node.nodeId.orEmpty() }
            )
            .take(query.effectiveLimit())
    }

    private fun scoreNode(node: ScreenNode, query: FindElementQuery): FindElementCandidate? {
        var score = 0
        val reasons = mutableListOf<String>()

        if (query.nodeId != null) {
            if (node.nodeId == query.nodeId) {
                score += 100
                reasons += "node_id"
            } else {
                return null
            }
        }

        if (query.className != null) {
            val nodeClass = node.className.orEmpty()
            if (nodeClass.equals(query.className, ignoreCase = true) ||
                nodeClass.endsWith("." + query.className, ignoreCase = true)
            ) {
                score += 20
                reasons += "class_name"
            } else {
                return null
            }
        }

        if (query.text != null) {
            val textScore = scoreText(query.text, node.text.raw, query.match)
            if (textScore <= 0) return null
            score += textScore
            reasons += "text_${query.match.wireName}"
        }

        if (query.contentDescription != null) {
            val cdScore = scoreText(
                query.contentDescription,
                node.contentDescription?.raw.orEmpty(),
                query.match,
            )
            if (cdScore <= 0) return null
            score += cdScore
            reasons += "content_description_${query.match.wireName}"
        }

        if (score == 0) return null

        return FindElementCandidate(
            node = node,
            score = score,
            matchReasons = reasons,
        )
    }

    private fun scoreText(query: String, candidate: String, mode: MatchMode): Int {
        if (query.isBlank() || candidate.isBlank()) return 0
        val q = query.trim()
        val c = candidate.trim()

        if (q.equals(c, ignoreCase = true)) return 70

        return when (mode) {
            MatchMode.EXACT -> 0
            MatchMode.CONTAINS -> if (c.contains(q, ignoreCase = true)) 50 else 0
            MatchMode.SEMANTIC -> semanticScore(q, c)
        }
    }

    /**
     * Local "semantic" mode falls back to a token-set overlap. Real semantic
     * matching requires a local model; until that's wired in, we accept exact
     * matches, case-insensitive substring matches, and any candidate whose
     * normalized tokens are a superset of the query tokens. This keeps the
     * tool useful without making promises the runtime cannot keep.
     */
    private fun semanticScore(query: String, candidate: String): Int {
        if (candidate.contains(query, ignoreCase = true)) return 55
        val queryTokens = query.normalizedTokens()
        val candidateTokens = candidate.normalizedTokens()
        if (queryTokens.isEmpty() || candidateTokens.isEmpty()) return 0
        return if (candidateTokens.containsAll(queryTokens)) 40 else 0
    }

    private fun String.normalizedTokens(): Set<String> {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .toSet()
    }
}

enum class MatchMode(val wireName: String) {
    EXACT("exact"),
    CONTAINS("contains"),
    SEMANTIC("semantic");

    companion object {
        val Default: MatchMode = CONTAINS

        fun fromWire(value: String?): MatchMode? {
            if (value.isNullOrBlank()) return Default
            return values().firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

data class FindElementQuery(
    val text: String? = null,
    val contentDescription: String? = null,
    val nodeId: String? = null,
    val className: String? = null,
    val match: MatchMode = MatchMode.Default,
    val limit: Int = DefaultLimit,
) {
    fun hasAnyFilter(): Boolean {
        return !text.isNullOrBlank() ||
            !contentDescription.isNullOrBlank() ||
            !nodeId.isNullOrBlank() ||
            !className.isNullOrBlank()
    }

    fun effectiveLimit(): Int = limit.coerceIn(1, MaxLimit)

    companion object {
        const val DefaultLimit: Int = 5
        const val MaxLimit: Int = 25
    }
}

data class FindElementCandidate(
    val node: ScreenNode,
    val score: Int,
    val matchReasons: List<String>,
) {
    /**
     * Redacted, display-safe JSON view of this candidate. `text` and
     * `content_description` always use the `displaySafe` form so the tool
     * output is safe to log, trace, and surface to the local model.
     */
    fun toRedactedJson(): JSONObject {
        val sensitiveFlag = node.sensitive || node.text.isSensitive ||
            (node.contentDescription?.isSensitive == true)
        return JSONObject().apply {
            putOrNull("node_id", node.nodeId)
            putOrNull("class_name", node.className)
            putOrNull("view_id", node.viewIdResourceName)
            put("role", node.role.name)
            put("text", node.text.displayJson())
            put("content_description", node.contentDescription?.displayJson() ?: JSONObject.NULL)
            put("bounds", node.bounds.toJson())
            put("enabled", node.enabled)
            put("clickable", node.clickable)
            put("long_clickable", node.longClickable)
            put("scrollable", node.scrollable)
            put("focused", node.focused)
            put("is_input_field", node.isInputField)
            put("sensitive", sensitiveFlag)
            put("score", score)
            put("match_reasons", JSONArray(matchReasons))
        }
    }

    private fun ScreenText.displayJson(): JSONObject = JSONObject().apply {
        put("displaySafe", displaySafe)
        put("isSensitive", isSensitive)
    }

    private fun JSONObject.putOrNull(key: String, value: String?) {
        if (value.isNullOrEmpty()) put(key, JSONObject.NULL) else put(key, value)
    }
}

/**
 * Serializes the candidate list for the `find_element` tool result. The
 * top-level payload is always redacted; raw text never leaves the matcher.
 */
object FindElementResultEncoder {
    fun encode(
        candidates: List<FindElementCandidate>,
        query: FindElementQuery,
    ): String {
        val payload = JSONObject().apply {
            put("query", JSONObject().apply {
                put("text", SensitiveTextRedactor.redact(query.text.orEmpty()))
                put("content_description", SensitiveTextRedactor.redact(query.contentDescription.orEmpty()))
                put("node_id", query.nodeId.orEmpty())
                put("class_name", query.className.orEmpty())
                put("match", query.match.wireName)
                put("limit", query.effectiveLimit())
            })
            put("count", candidates.size)
            put("candidates", JSONArray().apply {
                candidates.forEach { put(it.toRedactedJson()) }
            })
        }
        return payload.toString()
    }
}

