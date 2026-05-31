package dev.touchpilot.app.screen

/**
 * Builds a normalized [ScreenContext] from a raw [AccessibilityNodeSnapshot]
 * tree.
 *
 * The builder is deliberately small and deterministic:
 *
 * - Walks the snapshot tree depth-first up to [maxDepth].
 * - Emits a [ScreenNode] only for nodes that carry useful information
 *   (clickable, scrollable, editable, focused, or non-blank text/description).
 *   Empty layout containers are skipped so downstream summaries see signal
 *   rather than the entire view hierarchy.
 * - Maps text → [ScreenText] via [ScreenText.of], which routes through
 *   `SensitiveTextRedactor`; password fields are additionally flagged via
 *   [ScreenNode.sensitive].
 * - Classifies role from `className` and the node's capabilities.
 *
 * Raw observation data stays in the snapshot — the normalized [ScreenContext]
 * is the only thing downstream reasoning depends on. This satisfies the "keep
 * raw observation and normalized context separate" acceptance criterion in
 * issue #40.
 */
class ScreenContextBuilder(
    private val maxDepth: Int = DefaultMaxDepth
) {
    fun build(
        root: AccessibilityNodeSnapshot,
        appLabel: String? = null,
        packageName: String? = null,
        windowTitle: String? = null
    ): ScreenContext {
        val nodes = mutableListOf<ScreenNode>()
        collect(root, depth = 0, into = nodes)
        return ScreenContext(
            appLabel = appLabel,
            packageName = packageName,
            windowTitle = windowTitle,
            nodes = nodes
        )
    }

    private fun collect(
        node: AccessibilityNodeSnapshot,
        depth: Int,
        into: MutableList<ScreenNode>
    ) {
        if (depth > maxDepth) return
        if (carriesSignal(node)) {
            into += toScreenNode(node)
        }
        node.children.forEach { child -> collect(child, depth + 1, into) }
    }

    private fun carriesSignal(node: AccessibilityNodeSnapshot): Boolean {
        return node.clickable ||
            node.longClickable ||
            node.scrollable ||
            node.editable ||
            node.focused ||
            !node.text.isNullOrBlank() ||
            !node.contentDescription.isNullOrBlank()
    }

    private fun toScreenNode(node: AccessibilityNodeSnapshot): ScreenNode {
        val rawText = node.text?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.takeIf { it.isNotBlank() }
            ?: ""
        val rawContentDescription = node.contentDescription?.takeIf { it.isNotBlank() }
        return ScreenNode(
            nodeId = node.nodeId,
            role = roleFor(node),
            text = ScreenText.of(rawText),
            bounds = node.bounds,
            clickable = node.clickable,
            longClickable = node.longClickable,
            scrollable = node.scrollable,
            enabled = node.enabled,
            focused = node.focused,
            checked = if (node.checkable) node.checked else null,
            isInputField = node.editable,
            sensitive = node.password,
            viewIdResourceName = node.viewIdResourceName,
            className = node.className,
            contentDescription = rawContentDescription?.let { ScreenText.of(it) }
        )
    }

    private fun roleFor(node: AccessibilityNodeSnapshot): NodeRole {
        if (node.editable) return NodeRole.INPUT
        if (node.scrollable) return NodeRole.SCROLLABLE
        val cls = node.className?.lowercase().orEmpty()
        return when {
            "button" in cls || "imagebutton" in cls -> NodeRole.BUTTON
            "imageview" in cls -> NodeRole.IMAGE
            "edittext" in cls -> NodeRole.INPUT
            "textview" in cls -> NodeRole.TEXT
            node.clickable -> NodeRole.BUTTON
            node.children.isNotEmpty() -> NodeRole.CONTAINER
            else -> NodeRole.OTHER
        }
    }

    companion object {
        const val DefaultMaxDepth: Int = 8
    }
}
