package dev.touchpilot.app.androidcontrol

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.touchpilot.app.screen.AccessibilityNodeSnapshot
import dev.touchpilot.app.screen.NodeBounds

/**
 * Maps a live [AccessibilityNodeInfo] subtree to the platform-independent
 * [AccessibilityNodeSnapshot] consumed by `ScreenContextBuilder`.
 *
 * Kept thin on purpose — the adapter only reads fields. All normalization,
 * filtering, and classification happens in the builder, where it can be
 * exercised in JVM unit tests.
 *
 * Children are limited to [maxDepth] levels to mirror the existing
 * `observeScreen()` snapshot, so the builder's traversal cost is bounded the
 * same way the legacy debug serializer is.
 */
internal object AccessibilityNodeSnapshotAdapter {
    const val DefaultMaxDepth: Int = 8

    fun from(
        node: AccessibilityNodeInfo,
        nodeId: String = "0",
        depth: Int = 0,
        maxDepth: Int = DefaultMaxDepth
    ): AccessibilityNodeSnapshot {
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val children = if (depth < maxDepth) {
            buildList {
                for (index in 0 until node.childCount) {
                    val child = node.getChild(index) ?: continue
                    add(from(child, "$nodeId.$index", depth + 1, maxDepth))
                }
            }
        } else {
            emptyList()
        }
        return AccessibilityNodeSnapshot(
            nodeId = nodeId,
            className = node.className?.toString(),
            viewIdResourceName = node.viewIdResourceName,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            bounds = NodeBounds(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            ),
            clickable = node.isClickable,
            longClickable = node.isLongClickable,
            scrollable = node.isScrollable,
            enabled = node.isEnabled,
            focused = node.isFocused,
            checkable = node.isCheckable,
            checked = node.isChecked,
            editable = node.isEditable,
            password = node.isPassword,
            children = children
        )
    }
}
