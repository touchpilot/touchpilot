package dev.touchpilot.app.demonstration.analysis

import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode

/**
 * Extracts structured context features from screen snapshots for demonstration
 * recording summaries and workflow conversion.
 */
object DemonstrationContextExtractor {
    fun extractFeatures(context: ScreenContext): ContextFeatures {
        val clickableLabels = context.clickableNodes
            .mapNotNull { labelFor(it) }
            .distinct()
            .take(MAX_LABELS)

        val inputLabels = context.inputFields
            .mapNotNull { labelFor(it) }
            .distinct()
            .take(MAX_LABELS)

        val scrollableCount = context.scrollableNodes.size
        val roleDistribution = context.nodes.groupingBy { it.role }.eachCount()

        return ContextFeatures(
            packageName = context.packageName,
            appLabel = context.appLabel,
            windowTitle = context.windowTitle,
            nodeCount = context.nodes.size,
            clickableCount = context.clickableNodes.size,
            inputFieldCount = context.inputFields.size,
            scrollableCount = scrollableCount,
            clickableLabels = clickableLabels,
            inputLabels = inputLabels,
            roleDistribution = roleDistribution,
            containsSensitiveContent = context.containsSensitiveContent,
            fingerprint = DemonstrationScreenFingerprint.compute(context),
        )
    }

    fun headline(context: ScreenContext): String {
        val features = extractFeatures(context)
        val app = features.appLabel ?: features.packageName ?: "unknown app"
        val title = features.windowTitle?.let { " — $it" }.orEmpty()
        return "$app$title (${features.nodeCount} nodes, ${features.clickableCount} tappable)"
    }

    fun suggestNextActions(context: ScreenContext, limit: Int = 5): List<String> {
        return context.clickableNodes
            .mapNotNull { labelFor(it) }
            .distinct()
            .take(limit)
    }

    private fun labelFor(node: ScreenNode): String? {
        return node.text.displaySafe.trim().takeIf { it.isNotBlank() }
            ?: node.contentDescription?.displaySafe?.trim()?.takeIf { it.isNotBlank() }
            ?: roleFallback(node.role)
    }

    private fun roleFallback(role: NodeRole): String? {
        return when (role) {
            NodeRole.BUTTON -> "button"
            NodeRole.LINK -> "link"
            NodeRole.INPUT -> "input field"
            NodeRole.IMAGE -> "image"
            NodeRole.HEADING -> "heading"
            NodeRole.SCROLLABLE -> "scrollable region"
            NodeRole.CONTAINER -> "container"
            NodeRole.TEXT -> "text"
            NodeRole.OTHER -> null
        }
    }

    private const val MAX_LABELS = 30
}

data class ContextFeatures(
    val packageName: String?,
    val appLabel: String?,
    val windowTitle: String?,
    val nodeCount: Int,
    val clickableCount: Int,
    val inputFieldCount: Int,
    val scrollableCount: Int,
    val clickableLabels: List<String>,
    val inputLabels: List<String>,
    val roleDistribution: Map<NodeRole, Int>,
    val containsSensitiveContent: Boolean,
    val fingerprint: String,
) {
    fun toSummaryLines(): List<String> {
        return buildList {
            add("App: ${appLabel ?: packageName ?: "unknown"}")
            windowTitle?.let { add("Window: $it") }
            add("Nodes: $nodeCount (${clickableCount} tappable, $inputFieldCount inputs)")
            if (scrollableCount > 0) add("Scrollable regions: $scrollableCount")
            if (clickableLabels.isNotEmpty()) {
                add("Tappable: ${clickableLabels.take(5).joinToString(", ")}")
            }
            if (containsSensitiveContent) add("Contains sensitive content")
        }
    }
}
