package dev.touchpilot.app.androidcontrol

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAccessibilityNodeInfo

/**
 * Regression tests for depth-first accessibility tree walks that must recycle
 * discarded [AccessibilityNodeInfo] children during agent automation traversals.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AccessibilityNodeLifecycleTest {

    @Test
    fun findNodeRecycling_deepTree_noMatch_recyclesAllDiscardedChildren() {
        val root = wideDeepTree(branchingFactor = 3, depth = 8)

        val found = root.findNodeRecycling { it.text?.toString() == "missing" }

        assertNull(found)
        root.recycleSafely()
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(false))
    }

    @Test
    fun findNodeRecycling_deepTree_match_recyclesNonMatchingSiblingBranches() {
        val root = wideDeepTree(branchingFactor = 3, depth = 8, targetPath = listOf(1, 0, 1))

        val found = root.findNodeRecycling { it.text?.toString() == "target" }

        assertNotNull(found)
        assertEquals("target", found?.text?.toString())
        recyclePath(root, listOf(1, 0, 1))
        root.recycleSafely()
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(false))
    }

    @Test
    fun collectNodesRecycling_deepTree_recyclesDiscardedSubtrees() {
        val root = wideDeepTree(branchingFactor = 3, depth = 8, targetPath = listOf(2, 1, 0))

        val matches = mutableListOf<AccessibilityNodeInfo>()
        root.collectNodesRecycling(
            predicate = { it.text?.toString() == "target" },
            result = matches,
        )

        assertEquals(1, matches.size)
        recyclePath(root, listOf(2, 1, 0))
        root.recycleSafely()
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(false))
    }

    /**
     * Builds a tree with [branchingFactor] children per level for [depth] levels.
     * When [targetPath] is supplied, the leaf at that child index path is labeled
     * `"target"` so recursive find/collect paths can be exercised deterministically.
     */
    private fun wideDeepTree(
        branchingFactor: Int,
        depth: Int,
        targetPath: List<Int>? = null,
    ): AccessibilityNodeInfo {
        return buildLevel(
            currentDepth = 0,
            maxDepth = depth,
            branchingFactor = branchingFactor,
            remainingTargetPath = targetPath,
        )
    }

    private fun buildLevel(
        currentDepth: Int,
        maxDepth: Int,
        branchingFactor: Int,
        remainingTargetPath: List<Int>?,
    ): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain()
        val shadow = shadowOf(node) as ShadowAccessibilityNodeInfo

        if (currentDepth == maxDepth) {
            node.text = if (remainingTargetPath != null) "target" else "leaf"
            return node
        }

        repeat(branchingFactor) { index ->
            val childRemaining = when (remainingTargetPath) {
                null -> null
                else -> when {
                    remainingTargetPath.isEmpty() ->
                        if (index == 0) emptyList() else null
                    index == remainingTargetPath.first() -> remainingTargetPath.drop(1)
                    else -> null
                }
            }
            shadow.addChild(
                buildLevel(
                    currentDepth = currentDepth + 1,
                    maxDepth = maxDepth,
                    branchingFactor = branchingFactor,
                    remainingTargetPath = childRemaining,
                )
            )
        }
        return node
    }

    private fun recyclePath(root: AccessibilityNodeInfo, path: List<Int>) {
        var current = root
        val intermediates = mutableListOf<AccessibilityNodeInfo>()
        for (index in path) {
            val child = current.getChild(index) ?: return
            intermediates += child
            current = child
        }
        intermediates.asReversed().forEach { it.recycleSafely() }
    }
}
