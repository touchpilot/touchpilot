package dev.touchpilot.app.demonstration.analysis

import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode

/**
 * Tracks node-level changes between consecutive screen observations during
 * demonstration recording for detailed step analysis.
 */
class DemonstrationNodeChangeTracker {
    private val history = mutableListOf<Snapshot>()

    fun record(context: ScreenContext, label: String): NodeChangeReport {
        val snapshot = Snapshot(
            label = label,
            nodes = context.nodes.associateBy { it.nodeId ?: it.hashCode().toString() },
            packageName = context.packageName,
            windowTitle = context.windowTitle,
            timestampMillis = System.currentTimeMillis(),
        )
        val previous = history.lastOrNull()
        history += snapshot
        return if (previous == null) {
            NodeChangeReport(
                label = label,
                added = snapshot.nodes.values.toList(),
                removed = emptyList(),
                changed = emptyList(),
                unchanged = snapshot.nodes.values.toList(),
            )
        } else {
            computeReport(previous, snapshot)
        }
    }

    fun reset() {
        history.clear()
    }

    val snapshotCount: Int
        get() = history.size

    private fun computeReport(before: Snapshot, after: Snapshot): NodeChangeReport {
        val beforeIds = before.nodes.keys
        val afterIds = after.nodes.keys

        val addedIds = afterIds - beforeIds
        val removedIds = beforeIds - afterIds
        val commonIds = beforeIds intersect afterIds

        val changed = mutableListOf<NodeChange>()
        val unchanged = mutableListOf<ScreenNode>()

        commonIds.forEach { id ->
            val beforeNode = before.nodes[id]!!
            val afterNode = after.nodes[id]!!
            if (beforeNode != afterNode) {
                changed += NodeChange(before = beforeNode, after = afterNode)
            } else {
                unchanged += afterNode
            }
        }

        return NodeChangeReport(
            label = after.label,
            added = addedIds.mapNotNull { after.nodes[it] },
            removed = removedIds.mapNotNull { before.nodes[it] },
            changed = changed,
            unchanged = unchanged,
            packageChanged = before.packageName != after.packageName,
            windowTitleChanged = before.windowTitle != after.windowTitle,
        )
    }

    private data class Snapshot(
        val label: String,
        val nodes: Map<String, ScreenNode>,
        val packageName: String?,
        val windowTitle: String?,
        val timestampMillis: Long,
    )
}

data class NodeChangeReport(
    val label: String,
    val added: List<ScreenNode>,
    val removed: List<ScreenNode>,
    val changed: List<NodeChange>,
    val unchanged: List<ScreenNode>,
    val packageChanged: Boolean = false,
    val windowTitleChanged: Boolean = false,
) {
    val totalChanges: Int
        get() = added.size + removed.size + changed.size

    val hasChanges: Boolean
        get() = totalChanges > 0 || packageChanged || windowTitleChanged

    fun summary(): String {
        val parts = mutableListOf<String>()
        if (packageChanged) parts += "app changed"
        if (windowTitleChanged) parts += "title changed"
        if (added.isNotEmpty()) parts += "${added.size} added"
        if (removed.isNotEmpty()) parts += "${removed.size} removed"
        if (changed.isNotEmpty()) parts += "${changed.size} changed"
        return parts.joinToString(", ").ifBlank { "unchanged" }
    }

    fun addedByRole(): Map<NodeRole, Int> = added.groupingBy { it.role }.eachCount()
    fun removedByRole(): Map<NodeRole, Int> = removed.groupingBy { it.role }.eachCount()
}

data class NodeChange(
    val before: ScreenNode,
    val after: ScreenNode,
) {
    val roleChanged: Boolean get() = before.role != after.role
    val clickableChanged: Boolean get() = before.clickable != after.clickable
    val textChanged: Boolean get() = before.text.displaySafe != after.text.displaySafe
}
