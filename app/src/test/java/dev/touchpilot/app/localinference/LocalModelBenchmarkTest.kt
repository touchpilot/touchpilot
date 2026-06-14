package dev.touchpilot.app.localinference

import dev.touchpilot.app.memory.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalModelBenchmarkTest {
    @Test
    fun reportsLoadAndInferenceTimingsForStaticSamples() {
        // Load measured across the first two clock reads (5 ms), then one pair
        // per sample giving 0.1..0.6 ms of inference time.
        val clock = FakeClock(
            listOf(
                0L, 5_000_000L,
                5_000_000L, 5_100_000L,
                5_100_000L, 5_300_000L,
                5_300_000L, 5_600_000L,
                5_600_000L, 6_000_000L,
                6_000_000L, 6_500_000L,
                6_500_000L, 7_100_000L
            )
        )
        val memory = FakeMemory(listOf(10L * MiB, 11L * MiB))
        val runtime = MappedRuntime(
            available = true,
            outputs = mapOf(
                "go back" to """{"tool":"press_back","args":{}}""",
                "go to the home screen" to """{"tool":"press_home","args":{}}""",
                "scroll up" to """{"tool":"scroll","args":{"direction":"backward"}}""",
                "scroll down the list" to """{"tool":"scroll","args":{"direction":"forward"}}""",
                "open Settings" to """{"tool":"open_app","args":{"target":"Settings"}}""",
                "tap Submit" to """{"tool":"tap","args":{"text":"Submit"}}"""
            )
        )

        val result = LocalModelBenchmark(clock, memory).run(runtime)

        assertTrue(result.available)
        assertEquals(5_000_000L, result.loadNanos)
        assertEquals(6, result.samples.size)
        assertEquals(6, result.routedCount)
        assertEquals(0, result.finalCount)
        assertEquals(0, result.errorCount)
        assertEquals("press_back", result.samples.first().tool)
        assertEquals(100_000L, result.minInferenceNanos)
        assertEquals(600_000L, result.maxInferenceNanos)
        assertEquals(350_000L, result.averageInferenceNanos)
        assertEquals(350_000L, result.medianInferenceNanos)
        assertEquals(MiB, result.heapDeltaBytes)

        val summary = LocalModelBenchmarkReport.summarize(result)
        println(summary)
        assertTrue(summary.contains("load time: 5.00 ms"), summary)
        assertTrue(summary.contains("min 0.10 ms"), summary)
        assertTrue(summary.contains("median 0.35 ms"), summary)
        assertTrue(summary.contains("avg 0.35 ms"), summary)
        assertTrue(summary.contains("max 0.60 ms"), summary)
        assertTrue(summary.contains("heap delta: 1.0 MB (approx)"), summary)
        assertTrue(summary.contains("go back") && summary.contains("press_back"), summary)
    }

    @Test
    fun classifiesFinalAndUnparsedAndErrorOutcomes() {
        val runtime = MappedRuntime(
            available = true,
            outputs = mapOf(
                "good" to """{"tool":"press_back","args":{}}""",
                "stop" to """{"final":"I cannot do that safely."}""",
                "garbage" to "not json"
            ),
            throwOn = setOf("boom")
        )
        val samples = listOf(
            LocalModelBenchmarkSample("routed", "good"),
            LocalModelBenchmarkSample("final", "stop"),
            LocalModelBenchmarkSample("unparsed", "garbage"),
            LocalModelBenchmarkSample("error", "boom")
        )

        val result = LocalModelBenchmark().run(runtime, samples)

        assertEquals(1, result.routedCount)
        assertEquals(1, result.finalCount)
        assertEquals(1, result.unparsedCount)
        assertEquals(1, result.errorCount)
        assertEquals(LocalModelBenchmarkOutcome.FINAL, result.samples[1].outcome)
        assertEquals(LocalModelBenchmarkOutcome.UNPARSED, result.samples[2].outcome)
        assertEquals(LocalModelBenchmarkOutcome.ERROR, result.samples[3].outcome)
    }

    @Test
    fun reportsUnavailableRuntimeWithoutCrashing() {
        val result = LocalModelBenchmark().run(MappedRuntime(available = false, outputs = emptyMap()))

        assertTrue(!result.available)
        assertEquals(result.samples.size, result.errorCount)
        assertTrue(result.samples.all { it.outcome == LocalModelBenchmarkOutcome.ERROR })

        val summary = LocalModelBenchmarkReport.summarize(result)
        assertTrue(summary.contains("available: no"), summary)
        assertTrue(summary.contains("status: "), summary)
    }

    @Test
    fun runsWithDefaultClockAndSamples() {
        val runtime = MappedRuntime(
            available = true,
            outputs = mapOf("any" to """{"final":"ok"}"""),
            default = """{"final":"ok"}"""
        )

        val result = LocalModelBenchmark().run(runtime)

        assertEquals(LocalModelBenchmarkSamples.defaultSamples().size, result.samples.size)
        assertTrue(result.loadNanos >= 0)
        assertTrue(result.samples.all { it.inferenceNanos >= 0 })
    }

    private class FakeClock(values: List<Long>) : () -> Long {
        private val iterator = values.iterator()
        override fun invoke(): Long = iterator.next()
    }

    private class FakeMemory(values: List<Long>) : () -> Long {
        private val iterator = values.iterator()
        override fun invoke(): Long = iterator.next()
    }

    private class MappedRuntime(
        private val available: Boolean,
        private val outputs: Map<String, String>,
        private val throwOn: Set<String> = emptySet(),
        private val default: String = """{"final":"Local model could not route this task safely."}"""
    ) : LocalCommandModelRuntime {
        override fun status(): LocalModelStatus = LocalModelStatus(
            available = available,
            runtime = "Test",
            modelAsset = "test.tflite",
            version = "bench-test",
            message = if (available) "test runtime loaded." else "test runtime unavailable."
        )

        override fun route(task: String, context: String, skill: Skill?): String {
            if (!available) error(status().message)
            if (task in throwOn) error("forced failure for $task")
            return outputs[task] ?: default
        }
    }

    private companion object {
        const val MiB = 1024L * 1024L
    }
}
