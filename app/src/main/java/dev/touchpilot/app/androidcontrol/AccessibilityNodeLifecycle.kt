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
 * Depth-first search that runs [block] on the first matching node, then recycles
 * every [getChild] node on the winning path (including the match when it is not
 * [this] root).
 */
internal fun <T> AccessibilityNodeInfo.useFoundNode(
    predicate: (AccessibilityNodeInfo) -> Boolean,
    block: (AccessibilityNodeInfo) -> T
): T? {
    val pathToRecycle = mutableListOf<AccessibilityNodeInfo>()
    val found = findNodeWithAcquisitionPath(predicate, pathToRecycle) ?: return null
    return try {
        block(found)
    } finally {
        pathToRecycle.forEach { it.recycleSafely() }
    }
}

/**
 * Resolve a dotted node id (for example `"0.2.1"`), run [block] on the target,
 * then recycle every intermediate [getChild] node acquired along the path.
 */
internal fun <T> AccessibilityNodeInfo.useNodeById(
    nodeId: String,
    block: (AccessibilityNodeInfo) -> T
): T? {
    if (!nodeId.matches(Regex("\\d+(?:\\.\\d+)*"))) return null

    val path = nodeId.split(".").mapNotNull { it.toIntOrNull() }
    if (path.firstOrNull() != 0) return null

    var current: AccessibilityNodeInfo = this
    val acquiredPath = mutableListOf<AccessibilityNodeInfo>()

    for (index in path.drop(1)) {
        var next: AccessibilityNodeInfo? = null
        for (childIndex in 0 until current.childCount) {
            val child = current.getChild(childIndex) ?: continue
            if (childIndex == index) {
                next = child
            } else {
                child.recycleSafely()
            }
        }
        if (next == null) {
            acquiredPath.forEach { it.recycleSafely() }
            if (current !== this) current.recycleSafely()
            return null
        }
        if (current !== this) {
            acquiredPath += current
        }
        current = next
    }

    return try {
        block(current)
    } finally {
        acquiredPath.forEach { it.recycleSafely() }
        if (current !== this) current.recycleSafely()
    }
}

/**
 * Collect all nodes matching [predicate], run [block] on the matches, then
 * recycle every [getChild] node retained during the traversal.
 */
internal fun <T> AccessibilityNodeInfo.useMatchingNodes(
    predicate: (AccessibilityNodeInfo) -> Boolean,
    block: (List<AccessibilityNodeInfo>) -> T
): T {
    val matches = mutableListOf<AccessibilityNodeInfo>()
    val retained = mutableListOf<AccessibilityNodeInfo>()
    collectNodesForUse(predicate, matches, retained)
    return try {
        block(matches)
    } finally {
        retained.forEach { it.recycleSafely() }
    }
}

/**
 * Walk from [start] toward the root via [AccessibilityNodeInfo.parent],
 * recycling every parent node acquired along the way. [start] itself is owned
 * by the caller (typically released by [useFoundNode] or [useNodeById]).
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

private fun AccessibilityNodeInfo.findNodeWithAcquisitionPath(
    predicate: (AccessibilityNodeInfo) -> Boolean,
    pathToRecycle: MutableList<AccessibilityNodeInfo>,
): AccessibilityNodeInfo? {
    if (predicate(this)) return this

    for (index in 0 until childCount) {
        val child = getChild(index) ?: continue
        val found = try {
            child.findNodeWithAcquisitionPath(predicate, pathToRecycle)
        } catch (e: Exception) {
            child.recycleSafely()
            throw e
        }
        if (found != null) {
            pathToRecycle += child
            return found
        }
        child.recycleSafely()
    }

    return null
}

private fun AccessibilityNodeInfo.collectNodesForUse(
    predicate: (AccessibilityNodeInfo) -> Boolean,
    matches: MutableList<AccessibilityNodeInfo>,
    retained: MutableList<AccessibilityNodeInfo>,
): Boolean {
    val matchesSelf = predicate(this)
    if (matchesSelf) matches.add(this)

    var subtreeHasMatch = matchesSelf
    for (index in 0 until childCount) {
        val child = getChild(index) ?: continue
        retained += child
        val childHasMatch = child.collectNodesForUse(predicate, matches, retained)
        if (!childHasMatch) {
            child.recycleSafely()
            retained.removeAll { it === child }
        }
        subtreeHasMatch = subtreeHasMatch || childHasMatch
    }
    return subtreeHasMatch
}

/**
 * Depth-first search that recycles sibling branches that do not contain a match.
 *
 * Prefer [useFoundNode] at call sites so the winning path is also released after
 * the match is consumed.
 */
internal fun AccessibilityNodeInfo.findNodeRecycling(
    predicate: (AccessibilityNodeInfo) -> Boolean
): AccessibilityNodeInfo? {
    return depthFirstFindRecycling(
        node = this,
        childCount = { it.childCount },
        getChild = { _, index -> getChild(index) },
        recycle = { recycleSafely() },
        predicate = predicate,
    )
}

/**
 * Depth-first collection that recycles child subtrees with no matches.
 *
 * Prefer [useMatchingNodes] at call sites so matched nodes and their ancestor
 * chains are released after the result is consumed.
 */
internal fun AccessibilityNodeInfo.collectNodesRecycling(
    predicate: (AccessibilityNodeInfo) -> Boolean,
    result: MutableList<AccessibilityNodeInfo>
): Boolean {
    return depthFirstCollectRecycling(
        node = this,
        childCount = { it.childCount },
        getChild = { _, index -> getChild(index) },
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

/**
 * Generic counterpart to [AccessibilityNodeInfo.useFoundNode] for JVM tests.
 */
internal fun <N, T> useFoundNodeGeneric(
    root: N,
    childCount: (N) -> Int,
    getChild: (N, Int) -> N?,
    recycle: (N) -> Unit,
    predicate: (N) -> Boolean,
    block: (N) -> T,
): T? {
    val pathToRecycle = mutableListOf<N>()
    val found = depthFirstFindWithPath(
        node = root,
        childCount = childCount,
        getChild = getChild,
        recycle = recycle,
        predicate = predicate,
        pathToRecycle = pathToRecycle,
    ) ?: return null
    return try {
        block(found)
    } finally {
        pathToRecycle.forEach(recycle)
    }
}

/**
 * Generic counterpart to [AccessibilityNodeInfo.useMatchingNodes] for JVM tests.
 */
internal fun <N, T> useMatchingNodesGeneric(
    root: N,
    childCount: (N) -> Int,
    getChild: (N, Int) -> N?,
    recycle: (N) -> Unit,
    predicate: (N) -> Boolean,
    block: (List<N>) -> T,
): T {
    val matches = mutableListOf<N>()
    val retained = mutableListOf<N>()
    depthFirstCollectForUse(
        node = root,
        childCount = childCount,
        getChild = getChild,
        recycle = recycle,
        predicate = predicate,
        matches = matches,
        retained = retained,
    )
    return try {
        block(matches)
    } finally {
        retained.forEach(recycle)
    }
}

private fun <N> depthFirstFindWithPath(
    node: N,
    childCount: (N) -> Int,
    getChild: (N, Int) -> N?,
    recycle: (N) -> Unit,
    predicate: (N) -> Boolean,
    pathToRecycle: MutableList<N>,
): N? {
    if (predicate(node)) return node

    for (index in 0 until childCount(node)) {
        val child = getChild(node, index) ?: continue
        val found = try {
            depthFirstFindWithPath(
                node = child,
                childCount = childCount,
                getChild = getChild,
                recycle = recycle,
                predicate = predicate,
                pathToRecycle = pathToRecycle,
            )
        } catch (e: Exception) {
            recycle(child)
            throw e
        }
        if (found != null) {
            pathToRecycle += child
            return found
        }
        recycle(child)
    }

    return null
}

private fun <N> depthFirstCollectForUse(
    node: N,
    childCount: (N) -> Int,
    getChild: (N, Int) -> N?,
    recycle: (N) -> Unit,
    predicate: (N) -> Boolean,
    matches: MutableList<N>,
    retained: MutableList<N>,
): Boolean {
    val matchesSelf = predicate(node)
    if (matchesSelf) matches.add(node)

    var subtreeHasMatch = matchesSelf
    for (index in 0 until childCount(node)) {
        val child = getChild(node, index) ?: continue
        retained += child
        val childHasMatch = depthFirstCollectForUse(
            node = child,
            childCount = childCount,
            getChild = getChild,
            recycle = recycle,
            predicate = predicate,
            matches = matches,
            retained = retained,
        )
        if (!childHasMatch) {
            recycle(child)
            retained.removeAll { it === child }
        }
        subtreeHasMatch = subtreeHasMatch || childHasMatch
    }
    return subtreeHasMatch
}
