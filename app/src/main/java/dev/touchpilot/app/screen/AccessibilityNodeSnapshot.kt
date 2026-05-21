package dev.touchpilot.app.screen

/**
 * Minimal, platform-independent view of an `AccessibilityNodeInfo` subtree.
 *
 * [ScreenContextBuilder] consumes snapshots so it can be exercised in JVM
 * unit tests without instantiating the Android platform type. Production
 * code wraps `AccessibilityNodeInfo` into [AccessibilityNodeSnapshot] inside
 * the `androidcontrol` package; everything downstream of the model should
 * keep depending on [ScreenContext] / [ScreenNode], not on this snapshot.
 *
 * The snapshot keeps the raw observation shape separate from the normalized
 * [ScreenContext] (per issue #40 acceptance criteria) so debug tooling that
 * needs the unfiltered tree can still inspect it without affecting downstream
 * reasoning.
 */
data class AccessibilityNodeSnapshot(
    val nodeId: String,
    val className: String? = null,
    val viewIdResourceName: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val bounds: NodeBounds = NodeBounds.Unknown,
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val scrollable: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val editable: Boolean = false,
    /**
     * True when the underlying view is a password field
     * (`AccessibilityNodeInfo.isPassword`). The builder forwards this to
     * [ScreenNode.sensitive] so a password input is flagged even if it is
     * currently empty.
     */
    val password: Boolean = false,
    val children: List<AccessibilityNodeSnapshot> = emptyList()
)
