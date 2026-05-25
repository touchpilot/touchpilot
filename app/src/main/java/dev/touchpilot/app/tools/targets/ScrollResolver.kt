package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode

/**
 * Resolves a scroll request against the current [ScreenContext] for issue #80.
 *
 * Two flows:
 *
 * 1. **Targeted scroll** — when the caller passes a [TargetSelector] the
 *    resolver narrows the candidate set to scrollable nodes only and reuses
 *    [TargetResolver] for the existing identity/text/bounds scoring. This
 *    keeps the scoring consistent with tap/type_text and avoids re-inventing
 *    ranking rules.
 *
 * 2. **Direction-only fallback** — when the selector is empty the resolver
 *    inspects [ScreenContext.scrollableNodes] only to decide whether the
 *    existing direction-only behavior can run at all. If no scrollable node
 *    is visible it returns [ScrollResolution.NoScrollable] so the tool layer
 *    can surface an explicit failure rather than silently scrolling nothing.
 */
class ScrollResolver(
    private val targetResolver: TargetResolver = TargetResolver(),
) {
    fun resolve(context: ScreenContext, selector: TargetSelector?): ScrollResolution {
        val scrollables = context.scrollableNodes

        if (selector == null || selector.isEmpty) {
            return if (scrollables.isEmpty()) {
                ScrollResolution.NoScrollable(
                    reason = "No scrollable container is visible on the current screen.",
                    debugContext = directionOnlyDebug(context),
                )
            } else {
                ScrollResolution.DirectionOnly(scrollables.size)
            }
        }

        if (scrollables.isEmpty()) {
            return ScrollResolution.NoScrollable(
                reason = "No scrollable container is visible on the current screen.",
                debugContext = directionOnlyDebug(context),
            )
        }

        val scrollableContext = context.copy(nodes = scrollables)
        return when (val resolution = targetResolver.resolve(scrollableContext, selector)) {
            is TargetResolutionResult.Resolved -> ScrollResolution.Resolved(
                node = resolution.candidate.node,
                selector = resolution.candidate.selector,
                confidence = resolution.candidate.confidence,
                matchReasons = resolution.candidate.matchReasons,
            )
            is TargetResolutionResult.Ambiguous -> ScrollResolution.Ambiguous(
                reason = resolution.reason,
                candidates = resolution.candidates,
            )
            is TargetResolutionResult.NotFound -> ScrollResolution.NotFound(
                reason = resolution.reason,
                debugContext = resolution.debugContext,
                rejectedCandidates = resolution.rejectedCandidates,
            )
        }
    }

    private fun directionOnlyDebug(context: ScreenContext): String {
        val screenName = context.appLabel ?: context.windowTitle ?: context.packageName ?: "current screen"
        return "No scrollable nodes among ${context.nodes.size} node(s) on $screenName."
    }
}

sealed class ScrollResolution {
    /** A specific scrollable node matched the selector and should be used. */
    data class Resolved(
        val node: ScreenNode,
        val selector: TargetSelector,
        val confidence: Float,
        val matchReasons: List<String>,
    ) : ScrollResolution()

    /** Multiple scrollable nodes tied at the same confidence. */
    data class Ambiguous(
        val reason: String,
        val candidates: List<TargetCandidate>,
    ) : ScrollResolution()

    /** A selector was provided but no scrollable node matched. */
    data class NotFound(
        val reason: String,
        val debugContext: String,
        val rejectedCandidates: List<TargetCandidate> = emptyList(),
    ) : ScrollResolution()

    /** No selector provided and at least one scrollable container exists. */
    data class DirectionOnly(val scrollableCount: Int) : ScrollResolution()

    /** No scrollable node is visible — the tool layer must fail explicitly. */
    data class NoScrollable(
        val reason: String,
        val debugContext: String,
    ) : ScrollResolution()
}
