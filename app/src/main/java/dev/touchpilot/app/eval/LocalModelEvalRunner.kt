package dev.touchpilot.app.eval

import dev.touchpilot.app.tools.targets.TargetRankingEvalCase
import dev.touchpilot.app.tools.targets.TargetRankingEvalReport
import dev.touchpilot.app.tools.targets.TargetRankingEvaluator

data class LocalModelEvalReport(
    val targetRanking: TargetRankingEvalReport?,
    val commandRouting: CommandRoutingEvalReport?,
) {
    val totalCases: Int
        get() = (targetRanking?.totalCases ?: 0) + (commandRouting?.totalCases ?: 0)

    val passedCases: Int
        get() = (targetRanking?.passedCases ?: 0) + (commandRouting?.passedCases ?: 0)

    val failedCases: Int
        get() = totalCases - passedCases

    val allPassed: Boolean
        get() = failedCases == 0 && totalCases > 0

    fun formatSummary(): String {
        return buildString {
            appendLine("Local model eval")
            appendLine("cases=$totalCases passed=$passedCases failed=$failedCases")
            targetRanking?.let {
                appendLine()
                appendLine(it.formatSummary())
            }
            commandRouting?.let {
                appendLine()
                appendLine(it.formatSummary())
            }
            val failures = buildList {
                targetRanking?.results?.filter { !it.passed }?.forEach { result ->
                    add("target-ranking ${result.summaryLine()}")
                }
                commandRouting?.results?.filter { !it.passed }?.forEach { result ->
                    result.failureDetail()?.let { add("command-routing $it") }
                }
            }
            if (failures.isNotEmpty()) {
                appendLine()
                appendLine("Failure details")
                failures.forEach { appendLine(it) }
            }
        }.trim()
    }
}

class LocalModelEvalRunner(
    private val targetRankingEvaluator: TargetRankingEvaluator = TargetRankingEvaluator(),
    private val commandRoutingEvaluator: CommandRoutingEvaluator = CommandRoutingEvaluator(),
) {
    fun run(
        targetRankingCases: List<TargetRankingEvalCase> = emptyList(),
        commandRoutingCases: List<CommandRoutingEvalCase> = emptyList(),
    ): LocalModelEvalReport {
        require(targetRankingCases.isNotEmpty() || commandRoutingCases.isNotEmpty()) {
            "At least one eval fixture set must be provided."
        }

        return LocalModelEvalReport(
            targetRanking = targetRankingCases.takeIf { it.isNotEmpty() }
                ?.let { targetRankingEvaluator.evaluate(it) },
            commandRouting = commandRoutingCases.takeIf { it.isNotEmpty() }
                ?.let { commandRoutingEvaluator.evaluate(it) },
        )
    }
}
