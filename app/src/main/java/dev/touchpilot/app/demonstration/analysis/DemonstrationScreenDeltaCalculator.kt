package dev.touchpilot.app.demonstration.analysis

import dev.touchpilot.app.demonstration.DemonstrationScreenDelta
import dev.touchpilot.app.demonstration.DemonstrationScreenFrame
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import org.json.JSONObject

/**
 * Computes structural and textual deltas between two screen frames captured
 * during demonstration recording (issue #302).
 */
object DemonstrationScreenDeltaCalculator {
    fun compute(before: DemonstrationScreenFrame, after: DemonstrationScreenFrame): DemonstrationScreenDelta {
        val beforeContext = parseContext(before.contextJson)
        val afterContext = parseContext(after.contextJson)
        return compute(beforeContext, afterContext, before, after)
    }

    fun compute(
        before: ScreenContext,
        after: ScreenContext,
        beforeFrame: DemonstrationScreenFrame? = null,
        afterFrame: DemonstrationScreenFrame? = null,
    ): DemonstrationScreenDelta {
        val beforeNodes = indexNodes(before.nodes)
        val afterNodes = indexNodes(after.nodes)

        val beforeIds = beforeNodes.keys
        val afterIds = afterNodes.keys

        val addedIds = afterIds - beforeIds
        val removedIds = beforeIds - afterIds
        val commonIds = beforeIds intersect afterIds

        var changedCount = 0
        commonIds.forEach { id ->
            if (beforeNodes[id] != afterNodes[id]) changedCount++
        }

        val beforeTexts = extractVisibleTexts(before.nodes)
        val afterTexts = extractVisibleTexts(after.nodes)
        val addedTexts = (afterTexts - beforeTexts).sorted().take(MAX_TEXT_CHANGES)
        val removedTexts = (beforeTexts - afterTexts).sorted().take(MAX_TEXT_CHANGES)

        val packageChanged = before.packageName != after.packageName
        val windowTitleChanged = before.windowTitle != after.windowTitle

        val summary = buildSummary(
            addedCount = addedIds.size,
            removedCount = removedIds.size,
            changedCount = changedCount,
            packageChanged = packageChanged,
            windowTitleChanged = windowTitleChanged,
            addedTexts = addedTexts,
            removedTexts = removedTexts,
        )

        return DemonstrationScreenDelta(
            addedNodeCount = addedIds.size,
            removedNodeCount = removedIds.size,
            changedNodeCount = changedCount,
            packageChanged = packageChanged,
            windowTitleChanged = windowTitleChanged,
            addedTexts = addedTexts,
            removedTexts = removedTexts,
            summary = summary,
        )
    }

    private fun parseContext(json: String): ScreenContext {
        return runCatching { ScreenContext.fromJson(JSONObject(json)) }
            .getOrDefault(ScreenContext.Empty)
    }

    private fun indexNodes(nodes: List<ScreenNode>): Map<String, NodeSignature> {
        return nodes.mapNotNull { node ->
            val id = node.nodeId ?: return@mapNotNull null
            id to NodeSignature.from(node)
        }.toMap()
    }

    private fun extractVisibleTexts(nodes: List<ScreenNode>): Set<String> {
        return nodes.mapNotNull { node ->
            val text = node.text.displaySafe.trim().takeIf { it.isNotBlank() }
            text ?: node.contentDescription?.displaySafe?.trim()?.takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun buildSummary(
        addedCount: Int,
        removedCount: Int,
        changedCount: Int,
        packageChanged: Boolean,
        windowTitleChanged: Boolean,
        addedTexts: List<String>,
        removedTexts: List<String>,
    ): String {
        val parts = mutableListOf<String>()
        if (packageChanged) parts += "foreground app changed"
        if (windowTitleChanged) parts += "window title changed"
        if (addedCount > 0) parts += "$addedCount node(s) added"
        if (removedCount > 0) parts += "$removedCount node(s) removed"
        if (changedCount > 0) parts += "$changedCount node(s) changed"
        if (addedTexts.isNotEmpty()) {
            parts += "new text: ${addedTexts.take(3).joinToString(", ")}"
        }
        if (removedTexts.isNotEmpty()) {
            parts += "removed text: ${removedTexts.take(3).joinToString(", ")}"
        }
        return parts.joinToString("; ").ifBlank { "no visible changes" }
    }

    private data class NodeSignature(
        val role: NodeRole,
        val clickable: Boolean,
        val scrollable: Boolean,
        val textHash: Int,
    ) {
        companion object {
            fun from(node: ScreenNode): NodeSignature {
                val text = node.text.displaySafe + (node.contentDescription?.displaySafe.orEmpty())
                return NodeSignature(
                    role = node.role,
                    clickable = node.clickable,
                    scrollable = node.scrollable,
                    textHash = text.hashCode(),
                )
            }
        }
    }

    private const val MAX_TEXT_CHANGES = 20
}
