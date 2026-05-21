package dev.touchpilot.app.screen

import dev.touchpilot.app.security.SensitiveTextRedactor

/**
 * Normalized snapshot of the active Android screen, suitable as input to
 * deterministic screen summaries, suggested-action generation, local-model
 * prompts, and trace export.
 *
 * The model is intentionally independent from the Accessibility tree
 * implementation. The builder defined in issue #40 maps raw Accessibility
 * data to [ScreenContext]; downstream code (summaries, suggestions, local
 * model contracts, log exports) should depend only on this shape.
 */
data class ScreenContext(
    val appLabel: String? = null,
    val packageName: String? = null,
    val windowTitle: String? = null,
    val nodes: List<ScreenNode> = emptyList()
) {
    val clickableNodes: List<ScreenNode>
        get() = nodes.filter { it.clickable }

    val inputFields: List<ScreenNode>
        get() = nodes.filter { it.isInputField }

    val scrollableNodes: List<ScreenNode>
        get() = nodes.filter { it.scrollable }

    val containsSensitiveContent: Boolean
        get() = nodes.any { it.sensitive || it.text.isSensitive }

    companion object {
        val Empty: ScreenContext = ScreenContext()
    }
}

data class ScreenNode(
    val nodeId: String? = null,
    val role: NodeRole = NodeRole.OTHER,
    val text: ScreenText = ScreenText.Empty,
    val bounds: NodeBounds = NodeBounds.Unknown,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val checked: Boolean? = null,
    val isInputField: Boolean = false,
    /**
     * Explicit sensitivity flag, used when a builder knows from context (e.g.
     * `TYPE_TEXT_VARIATION_PASSWORD`) that the node holds sensitive data
     * even if the text itself does not trip the redactor's heuristics.
     */
    val sensitive: Boolean = false,
    val viewIdResourceName: String? = null,
    val className: String? = null
)

enum class NodeRole {
    BUTTON,
    LINK,
    INPUT,
    TEXT,
    IMAGE,
    CONTAINER,
    SCROLLABLE,
    HEADING,
    OTHER
}

data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2

    companion object {
        val Unknown: NodeBounds = NodeBounds(left = 0, top = 0, right = 0, bottom = 0)
    }
}

/**
 * A node's text plus a redaction-safe view of the same text.
 *
 * Use [displaySafe] for local summaries, log exports, agent event payloads,
 * and any UI surface that may be observed by a third party. Use [raw] only on
 * tool-execution paths that need the literal label (e.g. tap-by-text).
 *
 * [isSensitive] is true whenever [SensitiveTextRedactor.containsSensitiveText]
 * matches the raw text. Builders can additionally set
 * [ScreenNode.sensitive] when they know the underlying view is a password or
 * secret field — that flag participates in
 * [ScreenContext.containsSensitiveContent] independently of [isSensitive].
 */
data class ScreenText(
    val raw: String,
    val displaySafe: String,
    val isSensitive: Boolean
) {
    companion object {
        val Empty: ScreenText = ScreenText(raw = "", displaySafe = "", isSensitive = false)

        fun of(raw: String): ScreenText {
            if (raw.isEmpty()) return Empty
            val sensitive = SensitiveTextRedactor.containsSensitiveText(raw)
            val displaySafe = if (sensitive) "[REDACTED]" else SensitiveTextRedactor.redact(raw)
            return ScreenText(raw = raw, displaySafe = displaySafe, isSensitive = sensitive)
        }
    }
}
