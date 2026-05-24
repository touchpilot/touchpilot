package dev.touchpilot.app.androidcontrol

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Resolves a tap target from the accessibility tree using ranked candidate
 * selection. Prefers exact matches over substring matches, and clickable
 * nodes over non-clickable ones. Detects ambiguity when multiple candidates
 * share the same rank.
 */
object TargetResolver {

    /**
     * Find all candidates matching [text] and return a [TargetResolution].
     *
     * Ranking (lower = better):
     *   0 — exact text/content-description match on a clickable node
     *   1 — exact text/content-description match on a non-clickable node
     *   2 — substring match on a clickable node
     *   3 — substring match on a non-clickable node
     *
     * If two or more candidates share the lowest rank the result is
     * [TargetResolution.Ambiguous].
     */
    fun resolveByText(
        root: AccessibilityNodeInfo,
        text: String
    ): TargetResolution {
        if (text.isBlank()) return TargetResolution.NotFound("text=\"\"")

        val candidates = mutableListOf<ResolvedCandidate>()
        collectCandidates(root, text, nodeId = "0", candidates)

        return resolve(text, candidates)
    }

    /**
     * Pure resolution logic — separated for testability.
     * Takes a pre-collected list of [FlatCandidate] items.
     */
    internal fun resolveFlat(
        text: String,
        flatCandidates: List<FlatCandidate>
    ): FlatResolution {
        if (text.isBlank()) return FlatResolution.NotFound("text=\"\"")
        if (flatCandidates.isEmpty()) return FlatResolution.NotFound("text=\"$text\"")

        val bestRank = flatCandidates.minOf { it.rank }
        val best = flatCandidates.filter { it.rank == bestRank }

        return if (best.size == 1) FlatResolution.Resolved(best.first())
        else FlatResolution.Ambiguous(best)
    }

    private fun resolve(text: String, candidates: List<ResolvedCandidate>): TargetResolution {
        if (candidates.isEmpty()) return TargetResolution.NotFound("text=\"$text\"")

        val bestRank = candidates.minOf { it.rank }
        val best = candidates.filter { it.rank == bestRank }

        return if (best.size == 1) TargetResolution.Resolved(best.first())
        else TargetResolution.Ambiguous(best)
    }

    private fun collectCandidates(
        node: AccessibilityNodeInfo,
        text: String,
        nodeId: String,
        out: MutableList<ResolvedCandidate>
    ) {
        val label = node.text?.toString()
            ?: node.contentDescription?.toString()
            ?: ""

        val bounds = Rect().also { node.getBoundsInScreen(it) }

        if (label.isNotBlank() && !bounds.isEmpty) {
            val exactMatch = label.equals(text, ignoreCase = true)
            val substringMatch = !exactMatch && label.contains(text, ignoreCase = true)

            if (exactMatch || substringMatch) {
                val rank = when {
                    exactMatch && node.isClickable -> 0
                    exactMatch -> 1
                    substringMatch && node.isClickable -> 2
                    else -> 3
                }
                out.add(ResolvedCandidate(node, nodeId, label, bounds, rank))
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectCandidates(child, text, "$nodeId.$index", out)
        }
    }

    /**
     * Build a human-readable summary of candidates for failure messages.
     * Labels are truncated to 40 chars to avoid leaking sensitive content.
     */
    fun summarizeCandidates(candidates: List<ResolvedCandidate>): String {
        return candidates.joinToString("; ") { c ->
            val truncated = c.label.take(40).let { if (c.label.length > 40) "$it…" else it }
            "node_id=${c.nodeId} label=\"$truncated\" bounds=${c.bounds.toShortString()}"
        }
    }

    fun summarizeFlatCandidates(candidates: List<FlatCandidate>): String {
        return candidates.joinToString("; ") { c ->
            val truncated = c.label.take(40).let { if (c.label.length > 40) "$it…" else it }
            "node_id=${c.nodeId} label=\"$truncated\""
        }
    }
}

/** Plain data class for unit-testable resolution without Android framework. */
data class FlatCandidate(
    val nodeId: String,
    val label: String,
    val isClickable: Boolean,
    val rank: Int = when {
        true -> 0 // computed below
        else -> 3
    }
) {
    companion object {
        fun of(nodeId: String, label: String, query: String, isClickable: Boolean): FlatCandidate {
            val exact = label.equals(query, ignoreCase = true)
            val rank = when {
                exact && isClickable -> 0
                exact -> 1
                isClickable -> 2
                else -> 3
            }
            return FlatCandidate(nodeId, label, isClickable, rank)
        }
    }
}

sealed class FlatResolution {
    data class Resolved(val candidate: FlatCandidate) : FlatResolution()
    data class Ambiguous(val candidates: List<FlatCandidate>) : FlatResolution()
    data class NotFound(val selector: String) : FlatResolution()
}
