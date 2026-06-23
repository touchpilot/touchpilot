package dev.touchpilot.app.demonstration

/**
 * Aggregated statistics over demonstration sessions for settings and logs UI.
 */
object DemonstrationStatistics {
    fun compute(sessions: List<DemonstrationSession>): Stats {
        if (sessions.isEmpty()) return Stats()

        val totalSteps = sessions.sumOf { it.steps.size }
        val totalFrames = sessions.sumOf { it.allFrames.size }
        val totalDuration = sessions.mapNotNull { session ->
            session.metadata.completedAtMillis?.let { it - session.metadata.startedAtMillis }
        }.sum()
        val toolCounts = sessions.flatMap { it.steps }.groupingBy { it.action.tool }.eachCount()
        val successRate = sessions.flatMap { it.steps }
            .let { steps ->
                if (steps.isEmpty()) 0.0
                else steps.count { it.action.succeeded }.toDouble() / steps.size
            }
        val sensitiveCount = sessions.count { it.containsSensitiveContent }
        val statusCounts = sessions.groupingBy { it.metadata.status }.eachCount()

        return Stats(
            sessionCount = sessions.size,
            totalSteps = totalSteps,
            totalFrames = totalFrames,
            totalDurationMillis = totalDuration,
            averageStepsPerSession = totalSteps.toDouble() / sessions.size,
            averageDurationMillis = if (sessions.isNotEmpty()) totalDuration / sessions.size else 0,
            toolUsageCounts = toolCounts,
            stepSuccessRate = successRate,
            sessionsWithSensitiveContent = sensitiveCount,
            statusCounts = statusCounts,
        )
    }

    data class Stats(
        val sessionCount: Int = 0,
        val totalSteps: Int = 0,
        val totalFrames: Int = 0,
        val totalDurationMillis: Long = 0,
        val averageStepsPerSession: Double = 0.0,
        val averageDurationMillis: Long = 0,
        val toolUsageCounts: Map<String, Int> = emptyMap(),
        val stepSuccessRate: Double = 0.0,
        val sessionsWithSensitiveContent: Int = 0,
        val statusCounts: Map<DemonstrationStatus, Int> = emptyMap(),
    ) {
        fun topTools(limit: Int = 5): List<Pair<String, Int>> {
            return toolUsageCounts.entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { it.key to it.value }
        }

        fun summaryLines(): List<String> {
            return buildList {
                add("$sessionCount demonstration(s)")
                add("$totalSteps total steps · $totalFrames screen frames")
                if (totalDurationMillis > 0) {
                    add("Total duration: ${formatDuration(totalDurationMillis)}")
                }
                add("Step success rate: ${(stepSuccessRate * 100).toInt()}%")
                if (sessionsWithSensitiveContent > 0) {
                    add("$sessionsWithSensitiveContent session(s) contain sensitive content")
                }
                topTools().forEach { (tool, count) ->
                    add("  $tool: $count")
                }
            }
        }

        private fun formatDuration(millis: Long): String {
            val seconds = millis / 1000
            return when {
                seconds < 60 -> "${seconds}s"
                else -> "${seconds / 60}m ${seconds % 60}s"
            }
        }
    }
}
