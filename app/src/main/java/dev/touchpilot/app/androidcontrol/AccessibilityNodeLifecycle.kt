package dev.touchpilot.app.androidcontrol

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Helpers for releasing [AccessibilityNodeInfo] instances obtained from
 * [AccessibilityService.rootInActiveWindow], [AccessibilityNodeInfo.getChild],
 * [AccessibilityNodeInfo.findFocus], and [AccessibilityNodeInfo.parent].
 *
 * Each acquired node holds native Binder resources; failing to recycle them
 * during repeated agent tree walks causes memory pressure and can disconnect
 * the accessibility service.
 */
internal inline fun <T> AccessibilityService.useActiveRoot(
    block: (AccessibilityNodeInfo) -> T
): T? {
    val root = rootInActiveWindow ?: return null
    return try {
        block(root)
    } finally {
        root.recycleSafely()
    }
}

internal fun AccessibilityNodeInfo.recycleSafely() {
    runCatching { recycle() }
}

/**
 * Walk from [start] toward the root via [AccessibilityNodeInfo.parent],
 * recycling every parent node acquired along the way.
 */
internal inline fun walkUpFrom(
    start: AccessibilityNodeInfo,
    block: (AccessibilityNodeInfo) -> Boolean
): Boolean {
    var current: AccessibilityNodeInfo? = start
    val parentsToRecycle = mutableListOf<AccessibilityNodeInfo>()
    try {
        while (current != null) {
            if (block(current)) return true
            val parent = current.parent ?: break
            parentsToRecycle += parent
            current = parent
        }
        return false
    } finally {
        parentsToRecycle.forEach { it.recycleSafely() }
    }
}

/**
 * Depth-first search that recycles sibling branches that do not contain a match.
 */
internal fun AccessibilityNodeInfo.findNodeRecycling(
    predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    return depthFirstFindRecycling(
        node = this,
        childCount = { childCount },
        getChild = { index -> getChild(index) },
        recycle = { recycleSafely() },
        predicate = predicate,
    )
}

/**
 * Depth-first collection that recycles child subtrees with no matches.
 *
 * @return `true` when [this] node or any descendant matched [predicate].
 */
internal fun AccessibilityNodeInfo.collectNodesRecycling(
    predicate: (AccessibilityNodeInfo) -> Boolean,
    result: MutableList<AccessibilityNodeInfo>
): Boolean {
    return depthFirstCollectRecycling(
        node = this,
        childCount = { childCount },
        getChild = { index -> getChild(index) },
        recycle = { recycleSafely() },
        predicate = predicate,
        result = result,
    )
}

/**
 * Generic depth-first find used by [AccessibilityNodeInfo.findNodeRecycling].
 * Kept separate so the recycle-on-discard contract can be unit-tested on the JVM.
 */
internal fun <N> depthFirstFindRecycling(
    node: N,
    childCount: (N) -> Int,
    getChild: (N, Int) -> N?,
    recycle: (N) -> Unit,
    predicate: (N) -> Boolean,
): N? {
    if (predicate(node)) return node

    for (index in 0 until childCount(node)) {
        val child = getChild(node, index) ?: continue
        val found = try {
            depthFirstFindRecycling(child, childCount, getChild, recycle, predicate)
        } catch (e: Exception) {
            recycle(child)
            throw e
        }
        if (found != null) return found
        recycle(child)
    }

    return null
}

/**
 * Generic depth-first collect used by [AccessibilityNodeInfo.collectNodesRecycling].
 * Kept separate so the recycle-on-discard contract can be unit-tested on the JVM.
 */
internal fun <N> depthFirstCollectRecycling(
    node: N,
    childCount: (N) -> Int,
    getChild: (N, Int) -> N?,
    recycle: (N) -> Unit,
    predicate: (N) -> Boolean,
    result: MutableList<N>,
): Boolean {
    val matchesSelf = predicate(node)
    if (matchesSelf) result.add(node)

    var subtreeHasMatch = matchesSelf
    for (index in 0 until childCount(node)) {
        val child = getChild(node, index) ?: continue
        val childHasMatch = depthFirstCollectRecycling(
            child,
            childCount,
            getChild,
            recycle,
            predicate,
            result,
        )
        if (!childHasMatch) {
            recycle(child)
        }
        subtreeHasMatch = subtreeHasMatch || childHasMatch
    }
    return subtreeHasMatch
}
