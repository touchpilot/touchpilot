package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.ScreenContext

object WaitForIdle {
    const val StableArg = "stable_ms"
    const val TimeoutArg = "timeout_ms"
    const val IncludeBoundsArg = "include_bounds"

    const val DefaultStableMs = 500L
    const val DefaultTimeoutMs = 5_000L
    const val MinStableMs = 0L
    const val MaxStableMs = 5_000L
    const val MinTimeoutMs = 100L
    const val MaxTimeoutMs = 15_000L
    const val PollIntervalMs = 100L

    fun stableMs(args: Map<String, String>): Long {
        return args[StableArg]?.toLongOrNull() ?: DefaultStableMs
    }

    fun timeoutMs(args: Map<String, String>): Long {
        return args[TimeoutArg]?.toLongOrNull() ?: DefaultTimeoutMs
    }

    fun includeBounds(args: Map<String, String>): Boolean {
        return args[IncludeBoundsArg]?.toBooleanStrictOrNull() ?: false
    }

    fun validate(args: Map<String, String>): String? {
        val stable = args[StableArg]?.toLongOrNull()
        if (args[StableArg] != null && stable == null) return "$StableArg must be a number"
        if (stable != null && stable !in MinStableMs..MaxStableMs) {
            return "$StableArg must be between $MinStableMs and $MaxStableMs"
        }

        val timeout = args[TimeoutArg]?.toLongOrNull()
        if (args[TimeoutArg] != null && timeout == null) return "$TimeoutArg must be a number"
        if (timeout != null && timeout !in MinTimeoutMs..MaxTimeoutMs) {
            return "$TimeoutArg must be between $MinTimeoutMs and $MaxTimeoutMs"
        }

        val includeBounds = args[IncludeBoundsArg]
        if (includeBounds != null && includeBounds.toBooleanStrictOrNull() == null) {
            return "$IncludeBoundsArg must be true or false"
        }

        val effectiveStable = stable ?: DefaultStableMs
        val effectiveTimeout = timeout ?: DefaultTimeoutMs
        if (effectiveStable > effectiveTimeout) {
            return "$StableArg must not exceed $TimeoutArg"
        }

        return null
    }

    fun waitUntilIdle(
        args: Map<String, String>,
        observe: () -> ScreenContext,
        nowMs: () -> Long = { System.currentTimeMillis() },
        sleeper: (Long) -> Unit = { Thread.sleep(it) }
    ): ToolResult {
        val stableMs = stableMs(args)
        val timeoutMs = timeoutMs(args)
        val includeBounds = includeBounds(args)
        val startedAt = nowMs()
        val deadline = startedAt + timeoutMs
        var sampleCount = 1
        var lastSignature = signature(observe(), includeBounds)
        var stableSince = startedAt

        while (nowMs() <= deadline) {
            val elapsedStableMs = nowMs() - stableSince
            if (elapsedStableMs >= stableMs) {
                return ToolResult(
                    ok = true,
                    message = "waitForIdle",
                    data = mapOf(
                        "stable_ms" to elapsedStableMs.toString(),
                        "required_stable_ms" to stableMs.toString(),
                        "timeout_ms" to timeoutMs.toString(),
                        "include_bounds" to includeBounds.toString(),
                        "sample_count" to sampleCount.toString(),
                    )
                )
            }

            val remainingMs = deadline - nowMs()
            if (remainingMs <= 0L) break
            sleeper(minOf(PollIntervalMs, remainingMs))

            val now = nowMs()
            val currentSignature = signature(observe(), includeBounds)
            sampleCount += 1
            if (currentSignature != lastSignature) {
                lastSignature = currentSignature
                stableSince = now
            }
        }

        val observedMs = maxOf(0L, nowMs() - stableSince)
        return ToolResult(
            ok = false,
            message = "Timed out waiting for idle screen: stable_ms=$stableMs, timeout_ms=$timeoutMs",
            data = mapOf(
                "timed_out" to "true",
                "stable_ms" to observedMs.toString(),
                "required_stable_ms" to stableMs.toString(),
                "timeout_ms" to timeoutMs.toString(),
                "include_bounds" to includeBounds.toString(),
                "sample_count" to sampleCount.toString(),
            )
        )
    }

    internal fun signature(context: ScreenContext, includeBounds: Boolean): String {
        val comparable = if (includeBounds) {
            context
        } else {
            context.copy(nodes = context.nodes.map { it.copy(bounds = NodeBounds.Unknown) })
        }
        return comparable.toRedactedJson()
    }
}
