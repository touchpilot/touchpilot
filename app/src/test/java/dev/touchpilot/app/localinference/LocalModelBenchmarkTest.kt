package dev.touchpilot.app.localinference

import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalModelBenchmarkTest {
    @Test
    fun summarizesLoadAndInferenceMetrics() {
        val runtime = StubRuntime(
            status = LocalModelStatus(
                available = true,
                runtime = "LiteRT",
                modelAsset = "models/command_router/model.tflite",
                version = "tiny-router-2",
                message = "loaded"
            ),
            outputs = listOf(
                """{"tool":"press_back","args":{}}""",
                """{"tool":"press_back","args":{}}""",
                """{"tool":"open_app","args":{"target":"settings"}}""",
                """{"tool":"open_app","args":{"target":"settings"}}"""
            )
        )

        val time = SequenceLongs(
            0L,
            4_000_000L,
            10_000_000L,
            11_000_000L,
            20_000_000L,
            22_000_000L,
            30_000_000L,
            33_000_000L,
            40_000_000L,
            44_000_000L
        )
        val heap = SequenceLongs(
            1_024_000L,
            1_536_000L,
            2_048_000L,
            2_304_000L,
            2_560_000L,
            2_816_000L,
            3_072_000L,
            3_328_000L,
            3_584_000L,
            3_840_000L
        )

        val summary = LocalModelBenchmarks.run(
            runtimeFactory = { runtime },
            scenarios = listOf(
                LocalModelBenchmarkScenario("back", "go back"),
                LocalModelBenchmarkScenario("open_settings", "open settings")
            ),
            iterationsPerScenario = 2,
            nanoTime = time::next,
            usedHeapBytes = heap::next
        )

        assertTrue(summary.available)
        assertEquals(4.0, summary.loadTimeMs)
        assertEquals(500L, summary.loadHeapDeltaKb)
        assertEquals(2, summary.scenarios.size)
        assertEquals(1.5, summary.scenarios[0].averageInferenceMs)
        assertEquals(250L, summary.scenarios[0].averageHeapDeltaKb)
        assertTrue(summary.toConsoleSummary().contains("Local model benchmark summary"))
        assertTrue(summary.toConsoleSummary().contains("iterations_per_scenario=2"))
        assertTrue(summary.toConsoleSummary().contains("no Android tool execution"))
    }

    @Test
    fun reportsUnavailableRuntimeWithoutInferenceSamples() {
        val summary = LocalModelBenchmarks.run(
            runtimeFactory = {
                StubRuntime(
                    status = LocalModelStatus(
                        available = false,
                        runtime = "LiteRT",
                        modelAsset = "missing.tflite",
                        version = "tiny-router-2",
                        message = "LiteRT model asset is missing."
                    ),
                    outputs = emptyList()
                )
            },
            scenarios = listOf(LocalModelBenchmarkScenario("back", "go back")),
            iterationsPerScenario = 1,
            nanoTime = SequenceLongs(0L, 2_000_000L)::next,
            usedHeapBytes = SequenceLongs(10_240L, 12_288L)::next
        )

        assertEquals(false, summary.available)
        assertEquals(0, summary.scenarios.size)
        assertTrue(summary.notes.any { it.contains("missing") })
    }

    private class StubRuntime(
        private val status: LocalModelStatus,
        private val outputs: List<String>
    ) : LocalCommandModelRuntime {
        private var outputIndex = 0

        override fun status(): LocalModelStatus = status

        override fun route(task: String, context: String, skill: Skill?): String {
            val output = outputs.getOrNull(outputIndex)
                ?: error("No stub output left for $task")
            outputIndex += 1
            return output
        }
    }

    private class SequenceLongs(private vararg val values: Long) {
        private var index = 0

        fun next(): Long {
            return values.getOrElse(index++) {
                error("No more values available at index ${index - 1}")
            }
        }
    }
}
