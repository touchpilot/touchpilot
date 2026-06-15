package dev.touchpilot.app.eval

import dev.touchpilot.app.agent.AgentCommandParser
import dev.touchpilot.app.agent.LocalRouterCommandProvider
import dev.touchpilot.app.memory.Skill
import org.json.JSONArray
import org.json.JSONObject

data class CommandRoutingEvalCase(
    val id: String,
    val description: String,
    val task: String,
    val expectedTool: String? = null,
    val expectedArgs: Map<String, String> = emptyMap(),
    val expectedFinalContains: String? = null,
    val skill: Skill? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(task.isNotBlank()) { "task must not be blank" }
        require(expectedTool != null || expectedFinalContains != null) {
            "case must expect either a tool or a final answer fragment"
        }
    }
}

data class CommandRoutingEvalResult(
    val caseId: String,
    val description: String,
    val task: String,
    val expectedTool: String?,
    val expectedArgs: Map<String, String>,
    val expectedFinalContains: String?,
    val actualTool: String?,
    val actualArgs: Map<String, String>,
    val actualFinal: String?,
    val rawOutput: String,
    val passed: Boolean,
) {
    fun summaryLine(): String {
        val status = if (passed) "PASS" else "FAIL"
        return when {
            expectedTool != null -> {
                val actualArgsText = actualArgs.entries.joinToString(",") { "${it.key}=${it.value}" }
                val expectedArgsText = expectedArgs.entries.joinToString(",") { "${it.key}=${it.value}" }
                "$status $caseId expected_tool=$expectedTool args={$expectedArgsText} " +
                    "actual_tool=${actualTool ?: "none"} args={$actualArgsText}"
            }
            else -> {
                "$status $caseId expected_final_contains=${expectedFinalContains ?: ""} " +
                    "actual_final=${actualFinal ?: "none"}"
            }
        }
    }

    fun failureDetail(): String? {
        if (passed) return null
        return buildString {
            append(summaryLine())
            append(" raw=")
            append(rawOutput)
        }
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
        val provider = LocalRouterCommandProvider(case.task, case.skill)
        val rawOutput = provider.complete(systemPrompt = "", context = "")
        val parsed = runCatching { AgentCommandParser.parse(rawOutput) }.getOrNull()

        val actualTool = parsed?.tool
        val actualArgs = parsed?.args.orEmpty()
        val actualFinal = parsed?.finalAnswer

        val passed = when {
            case.expectedTool != null -> {
                actualTool == case.expectedTool && actualArgs == case.expectedArgs
            }
            else -> {
                actualFinal?.contains(case.expectedFinalContains.orEmpty()) == true
            }
        }

        return CommandRoutingEvalResult(
            caseId = case.id,
            description = case.description,
            task = case.task,
            expectedTool = case.expectedTool,
            expectedArgs = case.expectedArgs,
            expectedFinalContains = case.expectedFinalContains,
            actualTool = actualTool,
            actualArgs = actualArgs,
            actualFinal = actualFinal,
            rawOutput = rawOutput,
            passed = passed,
        )
    }

    fun evaluate(cases: List<CommandRoutingEvalCase>): CommandRoutingEvalReport {
        return CommandRoutingEvalReport(results = cases.map(::evaluateCase))
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
        val expectation = json.getJSONObject("expectation")
        val expectedTool = expectation.optNullableString("tool")
        val expectedArgs = parseArgs(expectation.optJSONObject("args"))
        val expectedFinalContains = expectation.optNullableString("final_contains")
        return CommandRoutingEvalCase(
            id = json.getString("id"),
            description = json.optString("description", ""),
            task = json.getString("task"),
            expectedTool = expectedTool,
            expectedArgs = expectedArgs,
            expectedFinalContains = expectedFinalContains,
            skill = json.optJSONObject("skill")?.let(::parseSkill),
        )
    }

    private fun parseSkill(json: JSONObject): Skill {
        val allowedTools = json.optJSONArray("allowed_tools") ?: JSONArray()
        return Skill(
            id = json.optString("id", "eval-skill"),
            title = json.optString("title", "Eval skill"),
            markdown = json.optString("markdown", ""),
            allowedTools = buildSet {
                for (index in 0 until allowedTools.length()) {
                    add(allowedTools.getString(index))
                }
            }
        )
    }

    private fun parseArgs(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        return buildMap {
            for (key in json.keys()) {
                put(key, json.getString(key))
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }
}
