package dev.touchpilot.app.localinference

import dev.touchpilot.app.memory.Skill
import kotlin.math.max

data class LocalModelBenchmarkScenario(
    val name: String,
    val task: String,
    val context: String = "",
    val skill: Skill? = null
)

data class LocalModelBenchmarkScenarioResult(
    val name: String,
    val task: String,
    val iterations: Int,
    val averageInferenceMs: Double,
    val minInferenceMs: Double,
    val maxInferenceMs: Double,
    val averageHeapDeltaKb: Long,
    val sampleOutput: String
)

data class LocalModelBenchmarkSummary(
    val available: Boolean,
    val runtime: String,
    val version: String,
    val modelAsset: String,
    val loadTimeMs: Double,
    val loadHeapDeltaKb: Long,
    val iterationsPerScenario: Int,
    val scenarios: List<LocalModelBenchmarkScenarioResult>,
    val notes: List<String>
) {
    fun toConsoleSummary(): String {
        return buildString {
            appendLine("Local model benchmark summary")
            appendLine(
                "runtime=$runtime version=$version asset=$modelAsset available=$available"
            )
            appendLine(
                "load_ms=${loadTimeMs.formatBenchmarkMs()} load_heap_delta_kb=$loadHeapDeltaKb"
            )
            if (scenarios.isNotEmpty()) {
                appendLine("iterations_per_scenario=$iterationsPerScenario")
                scenarios.forEach { scenario ->
                    appendLine(
                        "- ${scenario.name}: avg_ms=${scenario.averageInferenceMs.formatBenchmarkMs()} " +
                            "min_ms=${scenario.minInferenceMs.formatBenchmarkMs()} " +
                            "max_ms=${scenario.maxInferenceMs.formatBenchmarkMs()} " +
                            "avg_heap_delta_kb=${scenario.averageHeapDeltaKb} " +
                            "sample=${scenario.sampleOutput}"
                    )
                }
            }
            if (notes.isNotEmpty()) {
                appendLine("notes=${notes.joinToString(" | ")}")
            }
        }.trimEnd()
    }
}

object LocalModelBenchmarks {
    val DefaultScenarios = listOf(
        LocalModelBenchmarkScenario(
            name = "back",
            task = "go back"
        ),
        LocalModelBenchmarkScenario(
            name = "open_settings",
            task = "open settings"
        ),
        LocalModelBenchmarkScenario(
            name = "scroll_down",
            task = "scroll down"
        ),
        LocalModelBenchmarkScenario(
            name = "tap_wifi",
            task = "tap wi-fi"
        )
    )

    fun run(
        runtimeFactory: () -> LocalCommandModelRuntime,
        scenarios: List<LocalModelBenchmarkScenario> = DefaultScenarios,
        iterationsPerScenario: Int = 5,
        nanoTime: () -> Long = System::nanoTime,
        usedHeapBytes: () -> Long = { currentUsedHeapBytes() }
    ): LocalModelBenchmarkSummary {
        require(iterationsPerScenario > 0) {
            "iterationsPerScenario must be greater than zero."
        }

        val notes = mutableListOf(
            "static local examples only",
            "no Android tool execution",
            "no network requests"
        )

        val loadHeapBefore = usedHeapBytes()
        val loadStart = nanoTime()
        val runtime = runtimeFactory()
        val status = runtime.status()
        val loadEnd = nanoTime()
        val loadHeapAfter = usedHeapBytes()

        if (!status.available) {
            notes += status.message
            return LocalModelBenchmarkSummary(
                available = false,
                runtime = status.runtime,
                version = status.version,
                modelAsset = status.modelAsset,
                loadTimeMs = nanosToMillis(loadEnd - loadStart),
                loadHeapDeltaKb = bytesToKilobytes(loadHeapAfter - loadHeapBefore),
                iterationsPerScenario = iterationsPerScenario,
                scenarios = emptyList(),
                notes = notes
            )
        }

        val scenarioResults = scenarios.map { scenario ->
            var sampleOutput = ""
            val durationsMs = mutableListOf<Double>()
            val heapDeltasKb = mutableListOf<Long>()

            repeat(iterationsPerScenario) {
                val heapBefore = usedHeapBytes()
                val start = nanoTime()
                val output = runtime.route(scenario.task, scenario.context, scenario.skill)
                val end = nanoTime()
                val heapAfter = usedHeapBytes()

                if (sampleOutput.isEmpty()) {
                    sampleOutput = output
                }
                durationsMs += nanosToMillis(end - start)
                heapDeltasKb += bytesToKilobytes(heapAfter - heapBefore)
            }

            LocalModelBenchmarkScenarioResult(
                name = scenario.name,
                task = scenario.task,
                iterations = iterationsPerScenario,
                averageInferenceMs = durationsMs.average(),
                minInferenceMs = durationsMs.minOrNull() ?: 0.0,
                maxInferenceMs = durationsMs.maxOrNull() ?: 0.0,
                averageHeapDeltaKb = heapDeltasKb.average().toLong(),
                sampleOutput = sampleOutput
            )
        }

        return LocalModelBenchmarkSummary(
            available = true,
            runtime = status.runtime,
            version = status.version,
            modelAsset = status.modelAsset,
            loadTimeMs = nanosToMillis(loadEnd - loadStart),
            loadHeapDeltaKb = bytesToKilobytes(loadHeapAfter - loadHeapBefore),
            iterationsPerScenario = iterationsPerScenario,
            scenarios = scenarioResults,
            notes = notes
        )
    }

    private fun currentUsedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun nanosToMillis(value: Long): Double = value / 1_000_000.0

    private fun bytesToKilobytes(value: Long): Long = max(0L, value) / 1024L

}

private fun Double.formatBenchmarkMs(): String = String.format("%.2f", this)
