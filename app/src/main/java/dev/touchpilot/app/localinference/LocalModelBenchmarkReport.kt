package dev.touchpilot.app.localinference

/**
 * Formats a [LocalModelBenchmarkResult] into a short, copy-pasteable summary
 * suitable for a pull request description. The output is intentionally compact:
 * a header, the load/inference aggregates, and one line per sample.
 */
object LocalModelBenchmarkReport {
    fun summarize(result: LocalModelBenchmarkResult): String {
        val lines = mutableListOf<String>()
        lines += "Local Model Benchmark"
        lines += "runtime: ${result.runtime} (${result.version})"
        lines += "model asset: ${result.modelAsset}"
        lines += "available: ${if (result.available) "yes" else "no"}"
        if (!result.available) {
            lines += "status: ${result.statusMessage}"
        }
        lines += "load time: ${formatMillis(result.loadNanos)}"
        lines += "heap delta: ${formatHeap(result.heapDeltaBytes)}"
        lines += "samples: ${result.samples.size} " +
            "(routed ${result.routedCount}, final ${result.finalCount}, " +
            "unparsed ${result.unparsedCount}, errors ${result.errorCount})"
        lines += "inference: ${formatInferenceAggregate(result)}"

        if (result.samples.isNotEmpty()) {
            lines += "per-sample:"
            val labelWidth = result.samples.maxOf { it.label.length }
            result.samples.forEach { sample ->
                val label = sample.label.padEnd(labelWidth)
                val detail = outcomeDetail(sample).padEnd(OutcomeWidth)
                lines += "  - $label  $detail  ${formatMillis(sample.inferenceNanos)}"
            }
        }

        return lines.joinToString(separator = "\n")
    }

    private fun formatInferenceAggregate(result: LocalModelBenchmarkResult): String {
        val min = result.minInferenceNanos ?: return "no samples"
        return "min ${formatMillis(min)}, " +
            "median ${formatMillis(result.medianInferenceNanos ?: min)}, " +
            "avg ${formatMillis(result.averageInferenceNanos ?: min)}, " +
            "max ${formatMillis(result.maxInferenceNanos ?: min)}"
    }

    private fun outcomeDetail(sample: LocalModelSampleTiming): String {
        return when (sample.outcome) {
            LocalModelBenchmarkOutcome.ROUTED -> "-> ${sample.tool}"
            LocalModelBenchmarkOutcome.FINAL -> "-> final"
            LocalModelBenchmarkOutcome.UNPARSED -> "-> unparsed"
            LocalModelBenchmarkOutcome.ERROR -> "-> error"
        }
    }

    private fun formatMillis(nanos: Long): String {
        val millis = nanos / 1_000_000.0
        // Two decimals is enough resolution for a PR-review summary.
        val rounded = (millis * 100).toLong()
        return "${rounded / 100}.${(rounded % 100).toString().padStart(2, '0')} ms"
    }

    private fun formatHeap(bytes: Long?): String {
        if (bytes == null) return "n/a"
        val mib = bytes / (1024.0 * 1024.0)
        val rounded = (mib * 10).toLong()
        return "${rounded / 10}.${rounded % 10} MB (approx)"
    }

    private const val OutcomeWidth = 12
}
