package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode

/**
 * Resolves a requested [TargetSelector] against the normalized current
 * [ScreenContext].
 *
 * This is intentionally deterministic and local. It does not execute tools,
 * call Accessibility APIs, or ask a model. Android control tools use the
 * result to either execute the single best target or stop with a clear
 * ambiguity/not-found message.
 */
class TargetResolver(
    private val minConfidence: Float = 0.55f,
) {
    init {
        require(minConfidence in 0.0f..1.0f) {
            "minConfidence must be in [0.0, 1.0], got $minConfidence"
        }
    }

    fun resolve(context: ScreenContext, selector: TargetSelector): TargetResolutionResult {
        if (!selector.isValid()) {
            return TargetResolutionResult.NotFound(
                selector = selector,
                reason = "Target selector is empty.",
                debugContext = "Provide at least one selector dimension: text, contentDescription, nodeId, bounds, or viewIdResourceName.",
            )
        }

        if (context.nodes.isEmpty()) {
            return TargetResolutionResult.NotFound(
                selector = selector,
                reason = "Screen context has no nodes.",
                debugContext = "Observe the current screen again before resolving a target.",
            )
        }

        val allMatches = rankCandidates(context, selector)

        val actionable = allMatches.filter { it.node.enabled }
        if (actionable.isEmpty()) {
            return TargetResolutionResult.NotFound(
                selector = selector,
                reason = if (allMatches.isEmpty()) {
                    "No visible node matched the target selector."
                } else {
                    "Only disabled nodes matched the target selector."
                },
                debugContext = debugContext(selector, context),
                rejectedCandidates = allMatches.take(MaxReturnedCandidates),
            )
        }

        val best = actionable.first()
        if (best.confidence < minConfidence) {
            return TargetResolutionResult.NotFound(
                selector = selector,
                reason = "No candidate was confident enough to resolve.",
                debugContext = debugContext(selector, context),
                rejectedCandidates = actionable.take(MaxReturnedCandidates),
            )
        }

        val tied = actionable.filter { sameConfidence(it.confidence, best.confidence) }
        if (tied.size > 1) {
            return TargetResolutionResult.Ambiguous(
                selector = selector,
                reason = "Multiple visible nodes matched the target selector with equal confidence.",
                candidates = tied.take(MaxReturnedCandidates),
            )
        }

        return TargetResolutionResult.Resolved(best)
    }

    /**
     * Read-only ranking path for Milestone 8 evaluation. This exposes the
     * same deterministic candidate ordering that runtime resolution uses,
     * without executing any Android action or changing resolver behavior.
     */
    fun rankCandidates(context: ScreenContext, selector: TargetSelector): List<TargetCandidate> {
        if (!selector.isValid() || context.nodes.isEmpty()) return emptyList()
        return context.nodes.mapNotNull { node ->
            scoreNode(
                node = node,
                context = context,
                selector = selector,
            )
        }.sortedWith(compareByDescending<TargetCandidate> { it.confidence }.thenBy { it.node.nodeId.orEmpty() })
    }

    private fun scoreNode(
        node: ScreenNode,
        context: ScreenContext,
        selector: TargetSelector,
    ): TargetCandidate? {
        val reasons = mutableListOf<String>()
        var score = 0
        var exactIdentityMatch = false

        if (selector.nodeId != null && selector.nodeId == node.nodeId) {
            score += 100
            exactIdentityMatch = true
            reasons += "node_id"
        }

        val nodeBounds = node.bounds.toTargetBounds().takeUnless { it.isEmpty }
        if (selector.bounds != null && selector.bounds == nodeBounds) {
            score += 90
            exactIdentityMatch = true
            reasons += "bounds"
        }

        if (selector.viewIdResourceName != null &&
            selector.viewIdResourceName == node.viewIdResourceName
        ) {
            score += 85
            exactIdentityMatch = true
            reasons += "view_id"
        }

        val roleMatches = selector.role == null || selector.role == node.role.toTargetRole()
        if (!roleMatches && !exactIdentityMatch) return null
        if (selector.role != null && roleMatches) {
            score += 10
            reasons += "role"
        }

        score += scoreText(
            requested = selector.text?.raw,
            nodeText = node.text.raw,
            exactReason = "text_exact",
            normalizedReason = "text_normalized",
            containsReason = "text_contains",
            reasons = reasons,
            exactPoints = 75,
            normalizedPoints = 65,
            containsPoints = 55,
        )

        score += scoreText(
            requested = selector.contentDescription?.raw,
            nodeText = node.text.raw,
            exactReason = "content_description_exact",
            normalizedReason = "content_description_normalized",
            containsReason = "content_description_contains",
            reasons = reasons,
            exactPoints = 70,
            normalizedPoints = 60,
            containsPoints = 55,
        )

        if (score == 0) return null

        if (node.enabled) {
            score += 5
            reasons += "enabled"
        } else {
            reasons += "disabled"
        }

        if (node.clickable || node.longClickable || node.isInputField || node.scrollable) {
            score += 5
            reasons += "actionable"
        }

        val confidence = (score.coerceAtMost(100) / 100f)
        return TargetCandidate(
            node = node,
            selector = TargetSelectorBuilder.fromScreenNode(
                node = node,
                packageName = context.packageName,
                windowTitle = context.windowTitle,
                confidence = confidence,
                source = SelectorSource.OBSERVATION,
            ),
            confidence = confidence,
            matchReasons = reasons,
        )
    }

    private fun scoreText(
        requested: String?,
        nodeText: String,
        exactReason: String,
        normalizedReason: String,
        containsReason: String,
        reasons: MutableList<String>,
        exactPoints: Int,
        normalizedPoints: Int,
        containsPoints: Int,
    ): Int {
        val query = requested?.takeIf { it.isNotBlank() } ?: return 0
        if (nodeText.isBlank()) return 0
        if (query == nodeText) {
            reasons += exactReason
            return exactPoints
        }

        if (query.normalizedForResolve() == nodeText.normalizedForResolve()) {
            reasons += normalizedReason
            return normalizedPoints
        }

        val normalizedQuery = query.normalizedForResolve()
        val normalizedNodeText = nodeText.normalizedForResolve()
        if (normalizedQuery.isNotBlank() && normalizedQuery in normalizedNodeText) {
            reasons += containsReason
            return containsPoints
        }

        return 0
    }

    private fun debugContext(selector: TargetSelector, context: ScreenContext): String {
        val screenName = context.appLabel ?: context.windowTitle ?: context.packageName ?: "current screen"
        val selectorSummary = listOfNotNull(
            selector.nodeId?.let { "nodeId=$it" },
            selector.bounds?.let { "bounds=${it.toBoundsArg()}" },
            selector.viewIdResourceName?.let { "viewId=$it" },
            selector.text?.displaySafe?.takeIf { it.isNotBlank() }?.let { "text=$it" },
            selector.contentDescription?.displaySafe?.takeIf { it.isNotBlank() }?.let { "contentDescription=$it" },
            selector.role?.let { "role=${it.name}" },
        ).joinToString(", ")
        return "Searched ${context.nodes.size} node(s) on $screenName for $selectorSummary."
    }

    private fun sameConfidence(a: Float, b: Float): Boolean {
        return kotlin.math.abs(a - b) < ConfidenceEpsilon
    }

    private fun String.normalizedForResolve(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun NodeBounds.toTargetBounds(): TargetBounds {
        return TargetBounds(left = left, top = top, right = right, bottom = bottom)
    }

    private fun NodeRole.toTargetRole(): TargetRole = when (this) {
        NodeRole.BUTTON -> TargetRole.BUTTON
        NodeRole.LINK -> TargetRole.LINK
        NodeRole.INPUT -> TargetRole.INPUT
        NodeRole.TEXT -> TargetRole.TEXT
        NodeRole.IMAGE -> TargetRole.IMAGE
        NodeRole.CONTAINER -> TargetRole.CONTAINER
        NodeRole.SCROLLABLE -> TargetRole.SCROLLABLE
        NodeRole.HEADING -> TargetRole.HEADING
        NodeRole.OTHER -> TargetRole.OTHER
    }

    private companion object {
        const val ConfidenceEpsilon = 0.0001f
        const val MaxReturnedCandidates = 5
    }
}

sealed class TargetResolutionResult {
    data class Resolved(
        val candidate: TargetCandidate,
    ) : TargetResolutionResult()

    data class Ambiguous(
        val selector: TargetSelector,
        val reason: String,
        val candidates: List<TargetCandidate>,
    ) : TargetResolutionResult()

    data class NotFound(
        val selector: TargetSelector,
        val reason: String,
        val debugContext: String,
        val rejectedCandidates: List<TargetCandidate> = emptyList(),
    ) : TargetResolutionResult()
}

data class TargetCandidate(
    val node: ScreenNode,
    val selector: TargetSelector,
    val confidence: Float,
    val matchReasons: List<String>,
)
