package dev.touchpilot.app.localinference

import dev.touchpilot.app.memory.Skill

/**
 * A single static benchmark example. The [task] is a fixed local string handed
 * to the command runtime; running it only exercises the routing/inference path
 * and never executes an Android action or touches the network.
 */
data class LocalModelBenchmarkSample(
    val label: String,
    val task: String,
    val context: String = "",
    val skill: Skill? = null
)

/** How a single sample resolved, used for the per-sample summary line. */
enum class LocalModelBenchmarkOutcome {
    /** The runtime emitted a tool command. */
    ROUTED,

    /** The runtime emitted a final answer instead of a tool. */
    FINAL,

    /** The runtime emitted output that could not be parsed as a command. */
    UNPARSED,

    /** The runtime threw while routing (for example, the model was unavailable). */
    ERROR
}

/** Timing and outcome captured for one benchmark sample. */
data class LocalModelSampleTiming(
    val label: String,
    val outcome: LocalModelBenchmarkOutcome,
    val inferenceNanos: Long,
    /** Tool name for [LocalModelBenchmarkOutcome.ROUTED], else null. */
    val tool: String? = null,
    /** Error message when [outcome] is [LocalModelBenchmarkOutcome.ERROR]. */
    val error: String? = null
)

/**
 * Result of one benchmark run. Holds raw timings; aggregate helpers derive the
 * concise numbers a reviewer cares about so the report formatter stays simple.
 */
data class LocalModelBenchmarkResult(
    val runtime: String,
    val modelAsset: String,
    val version: String,
    val available: Boolean,
    val statusMessage: String,
    val loadNanos: Long,
    val samples: List<LocalModelSampleTiming>,
    /**
     * Best-effort heap-used delta across the run in bytes, or null when the
     * sampled delta is not meaningful (negative because of GC). This is a single
     * coarse signal, not a memory profile.
     */
    val heapDeltaBytes: Long?
) {
    val routedCount: Int get() = samples.count { it.outcome == LocalModelBenchmarkOutcome.ROUTED }
    val finalCount: Int get() = samples.count { it.outcome == LocalModelBenchmarkOutcome.FINAL }
    val unparsedCount: Int get() = samples.count { it.outcome == LocalModelBenchmarkOutcome.UNPARSED }
    val errorCount: Int get() = samples.count { it.outcome == LocalModelBenchmarkOutcome.ERROR }

    private val inferenceNanos: List<Long> get() = samples.map { it.inferenceNanos }

    val minInferenceNanos: Long? get() = inferenceNanos.minOrNull()
    val maxInferenceNanos: Long? get() = inferenceNanos.maxOrNull()
    val averageInferenceNanos: Long? get() =
        inferenceNanos.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

    val medianInferenceNanos: Long? get() {
        val sorted = inferenceNanos.sorted()
        if (sorted.isEmpty()) return null
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}

/**
 * Minimal local benchmark for the on-device command runtime. It measures the
 * cold load time (the first [LocalCommandModelRuntime.status] call forces the
 * lazy model load) and the per-sample inference time over a fixed set of static
 * examples, then leaves formatting to [LocalModelBenchmarkReport].
 *
 * The benchmark is deliberately narrow: it runs against the
 * [LocalCommandModelRuntime] contract only, never executes Android actions, and
 * never makes network calls. Clock and memory sampling are injectable so tests
 * stay deterministic.
 */
class LocalModelBenchmark(
    private val nanoTime: () -> Long = System::nanoTime,
    private val usedMemoryBytes: () -> Long = ::defaultUsedMemoryBytes
) {
    fun run(
        runtime: LocalCommandModelRuntime,
        samples: List<LocalModelBenchmarkSample> = LocalModelBenchmarkSamples.defaultSamples()
    ): LocalModelBenchmarkResult {
        val heapBefore = usedMemoryBytes()

        val loadStart = nanoTime()
        val status = runtime.status()
        val loadNanos = (nanoTime() - loadStart).coerceAtLeast(0)

        val timings = samples.map { sample -> timeSample(runtime, sample) }

        val heapDelta = (usedMemoryBytes() - heapBefore).takeIf { it > 0 }

        return LocalModelBenchmarkResult(
            runtime = status.runtime,
            modelAsset = status.modelAsset,
            version = status.version,
            available = status.available,
            statusMessage = status.message,
            loadNanos = loadNanos,
            samples = timings,
            heapDeltaBytes = heapDelta
        )
    }

    private fun timeSample(
        runtime: LocalCommandModelRuntime,
        sample: LocalModelBenchmarkSample
    ): LocalModelSampleTiming {
        val start = nanoTime()
        val routed = runCatching { runtime.route(sample.task, sample.context, sample.skill) }
        val nanos = (nanoTime() - start).coerceAtLeast(0)

        return routed.fold(
            onSuccess = { raw -> classify(sample.label, raw, nanos) },
            onFailure = { error ->
                LocalModelSampleTiming(
                    label = sample.label,
                    outcome = LocalModelBenchmarkOutcome.ERROR,
                    inferenceNanos = nanos,
                    error = error.message ?: error::class.simpleName
                )
            }
        )
    }

    private fun classify(label: String, raw: String, nanos: Long): LocalModelSampleTiming {
        val output = runCatching { LocalModelOutputParser.parse(raw) }.getOrNull()
            ?: return LocalModelSampleTiming(label, LocalModelBenchmarkOutcome.UNPARSED, nanos)

        return when (output) {
            is LocalModelOutput.ToolCall -> LocalModelSampleTiming(
                label = label,
                outcome = LocalModelBenchmarkOutcome.ROUTED,
                inferenceNanos = nanos,
                tool = output.tool
            )

            is LocalModelOutput.FinalAnswer -> LocalModelSampleTiming(
                label = label,
                outcome = LocalModelBenchmarkOutcome.FINAL,
                inferenceNanos = nanos
            )
        }
    }

    private companion object {
        fun defaultUsedMemoryBytes(): Long {
            val runtime = Runtime.getRuntime()
            return runtime.totalMemory() - runtime.freeMemory()
        }
    }
}

/**
 * Fixed, static benchmark examples covering the command routes the current
 * local model path supports. These are plain task strings; they describe an
 * intent but never run an Android action.
 */
object LocalModelBenchmarkSamples {
    fun defaultSamples(): List<LocalModelBenchmarkSample> = listOf(
        LocalModelBenchmarkSample(label = "go back", task = "go back"),
        LocalModelBenchmarkSample(label = "go home", task = "go to the home screen"),
        LocalModelBenchmarkSample(label = "scroll up", task = "scroll up"),
        LocalModelBenchmarkSample(label = "scroll down", task = "scroll down the list"),
        LocalModelBenchmarkSample(label = "open app", task = "open Settings"),
        LocalModelBenchmarkSample(label = "tap text", task = "tap Submit")
    )
}
