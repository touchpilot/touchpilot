package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenNode

/**
 * Factory helpers that build [TargetSelector] instances from the two
 * common sources of target data in the codebase:
 *
 * - **Legacy tool arguments** — the loose `Map<String, String>` shape the
 *   current `tap` tool accepts. [fromLegacyArgs] preserves backward
 *   compatibility so the typed selector can be introduced without changing
 *   any caller's call site in this PR (per issue #76 acceptance criterion 5).
 *
 * - **Observed screen nodes** — the structured `ScreenNode` produced by
 *   `ScreenContextBuilder`. [fromScreenNode] is the path the future resolver
 *   in issue #77 will use when emitting a selector for a resolved candidate.
 */
object TargetSelectorBuilder {

    /**
     * Build a selector from the loose `text`/`node_id`/`bounds` arguments
     * accepted by the existing `tap` tool. Recognised keys mirror
     * `AndroidToolCatalog`'s `tap` argument set.
     *
     * - `text` -> wrapped in [SelectorText.of] so sensitive labels are
     *   flagged and any later log emission uses the redacted view.
     * - `node_id` -> propagated verbatim.
     * - `bounds` -> parsed via [TargetBounds.parse]; malformed input is
     *   dropped silently so the resulting selector is simply less specific
     *   rather than invalid.
     *
     * The returned selector's [TargetSelector.source] is [SelectorSource.USER]
     * because legacy args come from agent / model / user-typed input that
     * the existing system treats as user-supplied.
     */
    fun fromLegacyArgs(args: Map<String, String>): TargetSelector {
        val text = args["text"]?.takeIf { it.isNotBlank() }?.let { SelectorText.of(it) }
        val nodeId = args["node_id"]?.takeIf { it.isNotBlank() }
        val bounds = args["bounds"]?.takeIf { it.isNotBlank() }?.let { TargetBounds.parse(it) }

        return TargetSelector(
            text = text,
            nodeId = nodeId,
            bounds = bounds,
            source = SelectorSource.USER,
        )
    }

    /**
     * Build a selector from a captured [ScreenNode].
     *
     * The node's raw text feeds [SelectorText.of] so sensitivity is computed
     * consistently with how the rest of the agent treats screen text. Explicit
     * node sensitivity is also carried onto the selector so password fields
     * remain sensitive even when their visible label is generic.
     *
     * Bounds with zero area are dropped — they cannot identify a target and
     * the [TargetSelector] is still valid via [TargetSelector.nodeId] /
     * [TargetSelector.viewIdResourceName] / text dimensions.
     */
    fun fromScreenNode(
        node: ScreenNode,
        packageName: String? = null,
        windowTitle: String? = null,
        confidence: Float? = null,
        source: SelectorSource = SelectorSource.OBSERVATION,
    ): TargetSelector {
        val sensitive = node.sensitive || node.text.isSensitive
        val text = node.text.raw
            .takeIf { it.isNotBlank() }
            ?.let { SelectorText.of(it, forceSensitive = sensitive) }
        val bounds = TargetBounds(
            left = node.bounds.left,
            top = node.bounds.top,
            right = node.bounds.right,
            bottom = node.bounds.bottom,
        ).takeUnless { it.isEmpty }

        return TargetSelector(
            text = text,
            contentDescription = null,
            nodeId = node.nodeId,
            bounds = bounds,
            viewIdResourceName = node.viewIdResourceName,
            role = node.role.toTargetRole(),
            packageName = packageName,
            windowTitle = windowTitle,
            confidence = confidence,
            sensitive = sensitive,
            source = source,
        )
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
}
