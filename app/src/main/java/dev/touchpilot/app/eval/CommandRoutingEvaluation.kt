package dev.touchpilot.app.eval

import dev.touchpilot.app.localinference.CommandRouteClassifier
import org.json.JSONArray
import org.json.JSONObject

data class CommandRoutingEvalCase(
    val id: String,
    val description: String,
    val task: String,
    val context: String = "",
    val expectedTool: String? = null,
    val expectedArgs: Map<String, String> = emptyMap(),
    val expectNoRoute: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(task.isNotBlank()) { "task must not be blank" }
        if (expectNoRoute) {
            require(expectedTool == null) { "expectNoRoute cases must not set expectedTool" }
        } else {
            require(!expectedTool.isNullOrBlank()) { "expectedTool must be set unless expectNoRoute is true" }
        }
    }
}

data class CommandRoutingEvalResult(
    val caseId: String,
    val description: String,
    val task: String,
    val expectedTool: String?,
    val expectedArgs: Map<String, String>,
    val expectNoRoute: Boolean,
    val actualTool: String?,
    val actualArgs: Map<String, String>,
    val passed: Boolean,
) {
    fun summaryLine(): String {
        val status = if (passed) "PASS" else "FAIL"
        val expected = if (expectNoRoute) {
            "no_route"
        } else {
            "tool=$expectedTool args=$expectedArgs"
        }
        val actual = actualTool?.let { tool -> "tool=$tool args=$actualArgs" } ?: "no_route"
        return "$status $caseId expected=$expected actual=$actual"
    }

    fun failureDetail(): String? {
        if (passed) return null
        return summaryLine()
    }
}

data class CommandRoutingEvalReport(
    val results: List<CommandRoutingEvalResult>,
) {
    val totalCases: Int get() = results.size
    val passedCases: Int get() = results.count { it.passed }
    val failedCases: Int get() = totalCases - passedCases

    fun formatSummary(): String {
        return buildString {
            appendLine("Command routing eval")
            appendLine("cases=$totalCases passed=$passedCases failed=$failedCases")
            results.forEach { appendLine(it.summaryLine()) }
            results.mapNotNull { it.failureDetail() }.forEach { appendLine(it) }
        }.trim()
    }
}

class CommandRoutingEvaluator {
    fun evaluateCase(case: CommandRoutingEvalCase): CommandRoutingEvalResult {
        val dispatched = extractDispatchedTools(case.context)
        val route = CommandRouteClassifier.classify(case.task, dispatched)
        val actualTool = route?.tool
        val actualArgs = route?.args.orEmpty()
        val passed = if (case.expectNoRoute) {
            route == null
        } else {
            actualTool == case.expectedTool && actualArgs == case.expectedArgs
        }
        return CommandRoutingEvalResult(
            caseId = case.id,
            description = case.description,
            task = case.task,
            expectedTool = case.expectedTool,
            expectedArgs = case.expectedArgs,
            expectNoRoute = case.expectNoRoute,
            actualTool = actualTool,
            actualArgs = actualArgs,
            passed = passed,
        )
    }

    fun evaluate(cases: List<CommandRoutingEvalCase>): CommandRoutingEvalReport {
        return CommandRoutingEvalReport(results = cases.map(::evaluateCase))
    }

    private fun extractDispatchedTools(context: String): Set<String> {
        return Regex(""""tool"\s*:\s*"([^"]+)"""")
            .findAll(context)
            .map { it.groupValues[1] }
            .toSet()
    }
}

object CommandRoutingEvalFixtureParser {
    fun parse(json: String): List<CommandRoutingEvalCase> {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        require(version == 1) { "Unsupported command routing fixture version: $version" }
        val cases = root.optJSONArray("cases") ?: JSONArray()
        return buildList {
            for (index in 0 until cases.length()) {
                add(parseCase(cases.getJSONObject(index)))
            }
        }
    }

    private fun parseCase(json: JSONObject): CommandRoutingEvalCase {
        val expectNoRoute = json.optBoolean("expect_no_route", false)
        return CommandRoutingEvalCase(
            id = json.getString("id"),
            description = json.optString("description", ""),
            task = json.getString("task"),
            context = json.optString("context", ""),
            expectedTool = if (expectNoRoute) null else json.getString("expected_tool"),
            expectedArgs = parseArgs(json.optJSONObject("expected_args")),
            expectNoRoute = expectNoRoute,
        )
    }

    private fun parseArgs(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        return buildMap {
            json.keys().forEach { key ->
                put(key, json.getString(key))
            }
        }
    }
}
