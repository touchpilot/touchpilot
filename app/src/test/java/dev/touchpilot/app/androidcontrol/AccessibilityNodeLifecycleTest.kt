package dev.touchpilot.app.androidcontrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for depth-first accessibility tree walks that must recycle
 * discarded child nodes during agent automation traversals.
 */
class AccessibilityNodeLifecycleTest {

    @Test
    fun depthFirstFindRecycling_deepTree_noMatch_recyclesDiscardedBranches() {
        val recycled = mutableSetOf<String>()
        val root = FakeNode.buildWideTree(branchingFactor = 3, depth = 8)

        val found = depthFirstFindRecycling(
            node = root,
            childCount = { it.children.size },
            getChild = { node, index -> node.children.getOrNull(index) },
            recycle = { recycled += it.id },
            predicate = { it.label == "missing" },
        )

        assertNull(found)
        assertFalse(recycled.contains("root"))
        assertEquals(wideTreeNodeCount(branchingFactor = 3, depth = 8) - 1, recycled.size)
    }

    @Test
    fun depthFirstFindRecycling_deepTree_match_keepsOnlyMatchingBranchAlive() {
        val recycled = mutableSetOf<String>()
        val targetPath = listOf(1, 0, 1)
        val root = FakeNode.buildWideTree(
            branchingFactor = 3,
            depth = 8,
            targetPath = targetPath,
        )

        val found = depthFirstFindRecycling(
            node = root,
            childCount = { it.children.size },
            getChild = { node, index -> node.children.getOrNull(index) },
            recycle = { recycled += it.id },
            predicate = { it.label == "target" },
        )

        assertEquals("target", found?.label)
        assertFalse(recycled.contains("root"))
        assertFalse(recycled.contains(found!!.id))
        assertTrue(recycled.isNotEmpty())
        assertTrue(recycled.size < wideTreeNodeCount(branchingFactor = 3, depth = 8) - 1)
    }

    @Test
    fun depthFirstCollectRecycling_deepTree_recyclesOnlyNonMatchingSubtrees() {
        val recycled = mutableSetOf<String>()
        val targetPath = listOf(2, 1, 0)
        val root = FakeNode.buildWideTree(
            branchingFactor = 3,
            depth = 8,
            targetPath = targetPath,
        )

        val matches = mutableListOf<FakeNode>()
        depthFirstCollectRecycling(
            node = root,
            childCount = { it.children.size },
            getChild = { node, index -> node.children.getOrNull(index) },
            recycle = { recycled += it.id },
            predicate = { it.label == "target" },
            result = matches,
        )

        assertEquals(1, matches.size)
        assertEquals("target", matches.single().label)
        assertFalse(recycled.contains("root"))
        assertFalse(recycled.contains(matches.single().id))
        assertTrue(recycled.isNotEmpty())
    }

    private fun wideTreeNodeCount(branchingFactor: Int, depth: Int): Int {
        var total = 0
        var levelSize = 1
        repeat(depth + 1) {
            total += levelSize
            levelSize *= branchingFactor
        }
        return total
    }

    private data class FakeNode(
        val id: String,
        val label: String,
        val children: List<FakeNode>,
    ) {
        companion object {
            fun buildWideTree(
                branchingFactor: Int,
                depth: Int,
                targetPath: List<Int>? = null,
            ): FakeNode {
                return buildLevel(
                    id = "root",
                    currentDepth = 0,
                    maxDepth = depth,
                    branchingFactor = branchingFactor,
                    remainingTargetPath = targetPath,
                )
            }

            private fun buildLevel(
                id: String,
                currentDepth: Int,
                maxDepth: Int,
                branchingFactor: Int,
                remainingTargetPath: List<Int>?,
            ): FakeNode {
                if (currentDepth == maxDepth) {
                    return FakeNode(
                        id = id,
                        label = if (remainingTargetPath != null) "target" else "leaf",
                        children = emptyList(),
                    )
                }

                val children = List(branchingFactor) { index ->
                    val childRemaining = when (remainingTargetPath) {
                        null -> null
                        else -> when {
                            remainingTargetPath.isEmpty() ->
                                if (index == 0) emptyList() else null
                            index == remainingTargetPath.first() -> remainingTargetPath.drop(1)
                            else -> null
                        }
                    }
                    buildLevel(
                        id = "$id.$index",
                        currentDepth = currentDepth + 1,
                        maxDepth = maxDepth,
                        branchingFactor = branchingFactor,
                        remainingTargetPath = childRemaining,
                    )
                }
                return FakeNode(id = id, label = "internal", children = children)
            }
        }
    }
}
