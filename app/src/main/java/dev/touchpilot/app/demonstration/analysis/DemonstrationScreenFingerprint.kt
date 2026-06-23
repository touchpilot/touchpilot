package dev.touchpilot.app.demonstration.analysis

import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode

/**
 * Builds a compact fingerprint of a [ScreenContext] for quick comparison
 * during demonstration recording without storing full tree payloads.
 */
object DemonstrationScreenFingerprint {
    fun compute(context: ScreenContext): String {
        val roleCounts = context.nodes
            .groupingBy { it.role }
            .eachCount()
            .entries
            .sortedBy { it.key.name }
            .joinToString(",") { "${it.key.name}:${it.value}" }

        val interactiveCount = context.nodes.count { it.clickable || it.scrollable }
        val inputCount = context.inputFields.size
        val textSample = context.nodes
            .mapNotNull { it.text.displaySafe.trim().takeIf { t -> t.isNotBlank() } }
            .sorted()
            .take(5)
            .joinToString("|")

        return buildString {
            append(context.packageName.orEmpty())
            append("::")
            append(context.windowTitle.orEmpty())
            append("::")
            append("nodes=${context.nodes.size}")
            append("::")
            append("roles=[$roleCounts]")
            append("::")
            append("interactive=$interactiveCount")
            append("::")
            append("inputs=$inputCount")
            append("::")
            append("text=[$textSample]")
            append("::")
            append("sensitive=${context.containsSensitiveContent}")
        }
    }

    fun similarity(before: ScreenContext, after: ScreenContext): Double {
        val beforeFp = tokenize(compute(before))
        val afterFp = tokenize(compute(after))
        if (beforeFp.isEmpty() && afterFp.isEmpty()) return 1.0
        val intersection = beforeFp intersect afterFp
        val union = beforeFp union afterFp
        return intersection.size.toDouble() / union.size.coerceAtLeast(1)
    }

    fun roleDistribution(context: ScreenContext): Map<NodeRole, Int> {
        return context.nodes.groupingBy { it.role }.eachCount()
    }

    private fun tokenize(fingerprint: String): Set<String> {
        return fingerprint.split("::").filter { it.isNotBlank() }.toSet()
    }
}
