package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import org.json.JSONArray
import org.json.JSONObject

data class TargetRankingEvalCase(
    val id: String,
    val description: String,
    val screen: ScreenContext,
    val selector: TargetSelector,
    val expectedNodeId: String,
    val expectedMaxRank: Int = 1,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(expectedNodeId.isNotBlank()) { "expectedNodeId must not be blank" }
        require(expectedMaxRank > 0) { "expectedMaxRank must be >= 1" }
    }
}

data class TargetRankingEvalResult(
    val caseId: String,
    val description: String,
    val expectedNodeId: String,
    val expectedMaxRank: Int,
    val actualRank: Int?,
    val topNodeId: String?,
    val rankedNodeIds: List<String>,
    val topConfidence: Float?,
    val passed: Boolean,
) {
    fun summaryLine(): String {
        val rank = actualRank?.toString() ?: "missing"
        val top = topNodeId ?: "none"
        val status = if (passed) "PASS" else "FAIL"
        return "$status $caseId expected=$expectedNodeId max_rank=$expectedMaxRank actual_rank=$rank top=$top"
    }
}

data class TargetRankingEvalReport(
    val results: List<TargetRankingEvalResult>,
) {
    val totalCases: Int get() = results.size
    val passedCases: Int get() = results.count { it.passed }
    val failedCases: Int get() = totalCases - passedCases

    fun formatSummary(): String {
        return buildString {
            appendLine("Target ranking eval")
            appendLine("cases=$totalCases passed=$passedCases failed=$failedCases")
            results.forEach { appendLine(it.summaryLine()) }
        }.trim()
    }
}

class TargetRankingEvaluator(
    private val resolver: TargetResolver = TargetResolver(),
) {
    fun evaluateCase(case: TargetRankingEvalCase): TargetRankingEvalResult {
        val ranked = resolver.rankCandidates(case.screen, case.selector)
            .filter { it.node.enabled }
        val rankedNodeIds = ranked.mapNotNull { it.node.nodeId }
        val actualRank = rankedNodeIds.indexOf(case.expectedNodeId)
            .takeIf { it >= 0 }
            ?.plus(1)
        return TargetRankingEvalResult(
            caseId = case.id,
            description = case.description,
            expectedNodeId = case.expectedNodeId,
            expectedMaxRank = case.expectedMaxRank,
            actualRank = actualRank,
            topNodeId = rankedNodeIds.firstOrNull(),
            rankedNodeIds = rankedNodeIds,
            topConfidence = ranked.firstOrNull()?.confidence,
            passed = actualRank != null && actualRank <= case.expectedMaxRank,
        )
    }

    fun evaluate(cases: List<TargetRankingEvalCase>): TargetRankingEvalReport {
        return TargetRankingEvalReport(results = cases.map(::evaluateCase))
    }
}

object TargetRankingEvalFixtureParser {
    fun parse(json: String): List<TargetRankingEvalCase> {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        require(version == 1) { "Unsupported target ranking fixture version: $version" }
        val cases = root.optJSONArray("cases") ?: JSONArray()
        return buildList {
            for (index in 0 until cases.length()) {
                add(parseCase(cases.getJSONObject(index)))
            }
        }
    }

    private fun parseCase(json: JSONObject): TargetRankingEvalCase {
        return TargetRankingEvalCase(
            id = json.getString("id"),
            description = json.optString("description", ""),
            screen = parseScreen(json.getJSONObject("screen")),
            selector = parseSelector(json.getJSONObject("selector")),
            expectedNodeId = json.getString("expected_node_id"),
            expectedMaxRank = json.optInt("expected_max_rank", 1),
        )
    }

    private fun parseScreen(json: JSONObject): ScreenContext {
        val nodes = json.optJSONArray("nodes") ?: JSONArray()
        return ScreenContext(
            appLabel = json.optNullableString("app_label"),
            packageName = json.optNullableString("package_name"),
            windowTitle = json.optNullableString("window_title"),
            nodes = buildList {
                for (index in 0 until nodes.length()) {
                    add(parseNode(nodes.getJSONObject(index)))
                }
            }
        )
    }

    private fun parseNode(json: JSONObject): ScreenNode {
        val sensitive = json.optBoolean("sensitive", false)
        return ScreenNode(
            nodeId = json.optNullableString("node_id"),
            role = parseNodeRole(json.optString("role", NodeRole.OTHER.name)),
            text = json.optNullableString("text")?.let { parseScreenText(it, sensitive) } ?: ScreenText.Empty,
            bounds = json.optString("bounds").takeIf { it.isNotBlank() }?.let(::parseBounds) ?: NodeBounds.Unknown,
            clickable = json.optBoolean("clickable", false),
            longClickable = json.optBoolean("long_clickable", false),
            scrollable = json.optBoolean("scrollable", false),
            enabled = json.optBoolean("enabled", true),
            focused = json.optBoolean("focused", false),
            checked = if (json.has("checked") && !json.isNull("checked")) json.getBoolean("checked") else null,
            isInputField = json.optBoolean("is_input_field", false),
            sensitive = sensitive,
            viewIdResourceName = json.optNullableString("view_id_resource_name"),
            className = json.optNullableString("class_name"),
            contentDescription = json.optNullableString("content_description")?.let { parseScreenText(it, sensitive) }
        )
    }

    private fun parseSelector(json: JSONObject): TargetSelector {
        return TargetSelector(
            text = json.optNullableString("text")?.let { SelectorText.of(it) },
            contentDescription = json.optNullableString("content_description")?.let { SelectorText.of(it) },
            nodeId = json.optNullableString("node_id"),
            bounds = json.optString("bounds").takeIf { it.isNotBlank() }?.let(TargetBounds::parse),
            viewIdResourceName = json.optNullableString("view_id_resource_name"),
            role = json.optNullableString("role")?.let(::parseTargetRole),
            packageName = json.optNullableString("package_name"),
            windowTitle = json.optNullableString("window_title"),
            source = SelectorSource.UNSPECIFIED,
        )
    }

    private fun parseBounds(value: String): NodeBounds {
        val parts = value.split(',').map { it.trim().toInt() }
        require(parts.size == 4) { "bounds must be 'left,top,right,bottom', got: $value" }
        return NodeBounds(parts[0], parts[1], parts[2], parts[3])
    }

    private fun parseNodeRole(value: String): NodeRole {
        return runCatching { NodeRole.valueOf(value) }.getOrDefault(NodeRole.OTHER)
    }

    private fun parseTargetRole(value: String): TargetRole {
        return runCatching { TargetRole.valueOf(value) }.getOrDefault(TargetRole.OTHER)
    }

    private fun parseScreenText(raw: String, sensitive: Boolean): ScreenText {
        val parsed = ScreenText.of(raw)
        return if (sensitive && !parsed.isSensitive) {
            ScreenText(raw = raw, displaySafe = "[REDACTED]", isSensitive = true)
        } else {
            parsed
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}
