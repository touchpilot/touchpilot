package dev.touchpilot.app.tools.targets

import org.json.JSONObject

/**
 * Provenance of a [TargetSelector] — useful for resolver output, ambiguity
 * reporting, and log review.
 *
 * - [USER]: typed by the user (e.g. "tap the Settings row").
 * - [AGENT]: produced by deterministic agent logic (intent gate, skill match).
 * - [MODEL]: emitted by a local model.
 * - [OBSERVATION]: derived directly from a captured screen node.
 * - [UNSPECIFIED]: source unknown / not recorded yet.
 */
enum class SelectorSource {
    USER,
    AGENT,
    MODEL,
    OBSERVATION,
    UNSPECIFIED,
}

/**
 * Role hint for a target. Parallels `NodeRole` in the screen package but kept
 * separate so the selector model can evolve without forcing changes to
 * `ScreenNode`. Resolver code (issue #77) will translate between the two.
 */
enum class TargetRole {
    BUTTON,
    LINK,
    INPUT,
    TEXT,
    IMAGE,
    CONTAINER,
    SCROLLABLE,
    HEADING,
    OTHER,
}

/**
 * Structured selector for an Android UI target.
 *
 * Replaces the current loose `Map<String, String>` argument shape (`text`,
 * `node_id`, `bounds`) used by the `tap` tool with a typed model that:
 *
 * - supports every dimension the current tools use (text, contentDescription,
 *   nodeId, bounds, viewIdResourceName),
 * - carries optional metadata (role, package, window) so the resolver in
 *   issue #77 can rank and explain ambiguity,
 * - keeps text fields wrapped in [SelectorText] so any log / trace / agent
 *   event renders the redacted view by default,
 * - exposes [isValid] so callers can refuse to dispatch a tap against an
 *   empty or under-specified selector (no more "blind taps").
 *
 * Per issue #76 acceptance criterion 5 this PR does **not** change runtime
 * tool behavior; the model lives alongside the existing args path and is
 * picked up by hardened tools in #78–#80.
 */
data class TargetSelector(
    val text: SelectorText? = null,
    val contentDescription: SelectorText? = null,
    val nodeId: String? = null,
    val bounds: TargetBounds? = null,
    val viewIdResourceName: String? = null,
    val role: TargetRole? = null,
    val packageName: String? = null,
    val windowTitle: String? = null,
    val confidence: Float? = null,
    val source: SelectorSource = SelectorSource.UNSPECIFIED,
) {
    init {
        val c = confidence
        if (c != null) {
            require(c in 0.0f..1.0f) { "confidence must be in [0.0, 1.0], got $c" }
        }
    }

    /**
     * True when no identifying dimension is set. An empty selector cannot
     * resolve to any node and should not be dispatched to a tool.
     */
    val isEmpty: Boolean
        get() = text == null &&
            contentDescription == null &&
            nodeId == null &&
            bounds == null &&
            viewIdResourceName == null

    /**
     * Whether the selector identifies at least one target dimension. The
     * resolver in #77 uses this as the first gate before scoring candidates.
     */
    fun isValid(): Boolean = !isEmpty

    /**
     * True when the selector carries any text component flagged as sensitive
     * — used to decide whether the selector itself should be redacted in
     * surfaces the user did not opt into (logs, traces, agent events).
     */
    val containsSensitiveText: Boolean
        get() = (text?.isSensitive == true) || (contentDescription?.isSensitive == true)

    /**
     * Copy that replaces sensitive text fields with their `[REDACTED]`
     * display-safe view. All non-text dimensions are preserved because they
     * are not user-visible labels.
     */
    fun redactedCopy(): TargetSelector = copy(
        text = text?.redactedCopy(),
        contentDescription = contentDescription?.redactedCopy(),
    )

    /**
     * Serialize as JSON. When [redacted] is true (the default), text fields
     * use their `displaySafe` view so the output can be written to logs and
     * traces without leaking sensitive labels.
     */
    fun toJson(redacted: Boolean = true): JSONObject {
        val view = if (redacted) redactedCopy() else this
        return JSONObject().apply {
            putOrNull("text", view.text?.toJson(redacted))
            putOrNull("contentDescription", view.contentDescription?.toJson(redacted))
            putOrNull("nodeId", view.nodeId)
            putOrNull("bounds", view.bounds?.toJson())
            putOrNull("viewIdResourceName", view.viewIdResourceName)
            putOrNull("role", view.role?.name)
            putOrNull("packageName", view.packageName)
            putOrNull("windowTitle", view.windowTitle)
            putOrNull("confidence", view.confidence)
            put("source", view.source.name)
            put("containsSensitiveText", view.containsSensitiveText)
        }
    }

    /**
     * Pretty-printed redacted JSON. Convenience for traces and PR proofs.
     */
    fun toRedactedJson(): String = toJson(redacted = true).toString(2)

    private fun JSONObject.putOrNull(key: String, value: Any?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    companion object {
        val Empty: TargetSelector = TargetSelector()

        fun fromJson(json: JSONObject): TargetSelector {
            return TargetSelector(
                text = if (json.has("text") && !json.isNull("text"))
                    SelectorText.fromJson(json.getJSONObject("text")) else null,
                contentDescription = if (json.has("contentDescription") && !json.isNull("contentDescription"))
                    SelectorText.fromJson(json.getJSONObject("contentDescription")) else null,
                nodeId = if (json.has("nodeId") && !json.isNull("nodeId")) json.getString("nodeId") else null,
                bounds = if (json.has("bounds") && !json.isNull("bounds"))
                    TargetBounds.fromJson(json.getJSONObject("bounds")) else null,
                viewIdResourceName = if (json.has("viewIdResourceName") && !json.isNull("viewIdResourceName"))
                    json.getString("viewIdResourceName") else null,
                role = if (json.has("role") && !json.isNull("role"))
                    runCatching { TargetRole.valueOf(json.getString("role")) }.getOrNull() else null,
                packageName = if (json.has("packageName") && !json.isNull("packageName"))
                    json.getString("packageName") else null,
                windowTitle = if (json.has("windowTitle") && !json.isNull("windowTitle"))
                    json.getString("windowTitle") else null,
                confidence = if (json.has("confidence") && !json.isNull("confidence"))
                    json.getDouble("confidence").toFloat() else null,
                source = runCatching {
                    SelectorSource.valueOf(json.optString("source", SelectorSource.UNSPECIFIED.name))
                }.getOrDefault(SelectorSource.UNSPECIFIED),
            )
        }
    }
}
