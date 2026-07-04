package dev.touchpilot.app.screen

import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONArray
import org.json.JSONObject

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

    fun toJson(redacted: Boolean = true): JSONObject {
        val contextToSerialize = if (redacted) redactedCopy() else this
        return JSONObject().apply {
            put("appLabel", contextToSerialize.appLabel)
            put("packageName", contextToSerialize.packageName)
            put("windowTitle", contextToSerialize.windowTitle)
            put("nodes", JSONArray().apply {
                contextToSerialize.nodes.forEach { put(it.toJson(redacted)) }
            })
            put("containsSensitiveContent", contextToSerialize.containsSensitiveContent)
        }
    }

    fun toRedactedJson(): String {
        return toJson(redacted = true).toString(2)
    }

    fun redactedCopy(): ScreenContext {
        return copy(
            nodes = nodes.map { it.redactedCopy() }
        )
    }

    companion object {
        val Empty: ScreenContext = ScreenContext()

        fun fromJson(json: JSONObject): ScreenContext {
            val nodesArray = json.optJSONArray("nodes") ?: JSONArray()
            val nodes = mutableListOf<ScreenNode>()
            for (i in 0 until nodesArray.length()) {
                nodes.add(ScreenNode.fromJson(nodesArray.getJSONObject(i)))
            }

            return ScreenContext(
                appLabel = if (json.has("appLabel") && !json.isNull("appLabel")) json.getString("appLabel") else null,
                packageName = if (json.has("packageName") && !json.isNull("packageName")) json.getString("packageName") else null,
                windowTitle = if (json.has("windowTitle") && !json.isNull("windowTitle")) json.getString("windowTitle") else null,
                nodes = nodes
            )
        }
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
    val className: String? = null,
    /**
     * Raw accessibility `contentDescription` kept separate from [text] so
     * tools like `find_element` can query and report the two independently.
     * The builder still falls back to `contentDescription` when populating
     * [text] so existing downstream callers continue to see a usable label.
     */
    val contentDescription: ScreenText? = null
) {
    fun toJson(redacted: Boolean = true): JSONObject {
        val nodeToSerialize = if (redacted) redactedCopy() else this
        return JSONObject().apply {
            put("nodeId", nodeToSerialize.nodeId)
            put("role", nodeToSerialize.role.name)
            put("text", nodeToSerialize.text.toJson(redacted))
            put("bounds", nodeToSerialize.bounds.toJson())
            put("clickable", nodeToSerialize.clickable)
            put("longClickable", nodeToSerialize.longClickable)
            put("scrollable", nodeToSerialize.scrollable)
            put("enabled", nodeToSerialize.enabled)
            put("focused", nodeToSerialize.focused)
            put("checked", nodeToSerialize.checked)
            put("isInputField", nodeToSerialize.isInputField)
            put("sensitive", nodeToSerialize.sensitive)
            put("viewIdResourceName", nodeToSerialize.viewIdResourceName)
            put("className", nodeToSerialize.className)
            put("contentDescription", nodeToSerialize.contentDescription?.toJson(redacted))
        }
    }

    fun redactedCopy(): ScreenNode {
        return copy(
            text = redactText(text),
            contentDescription = contentDescription?.let { redactText(it) }
        )
    }

    private fun redactText(field: ScreenText): ScreenText {
        return if (sensitive && !field.isSensitive) {
            field.copy(displaySafe = "[REDACTED]", isSensitive = true)
        } else {
            field.redactedCopy()
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ScreenNode {
            val roleString = json.optString("role", "OTHER")
            val role = try {
                NodeRole.valueOf(roleString)
            } catch (e: IllegalArgumentException) {
                NodeRole.OTHER
            }

            return ScreenNode(
                nodeId = if (json.has("nodeId") && !json.isNull("nodeId")) json.getString("nodeId") else null,
                role = role,
                text = ScreenText.fromJson(json.getJSONObject("text")),
                bounds = NodeBounds.fromJson(json.getJSONObject("bounds")),
                clickable = json.optBoolean("clickable", false),
                longClickable = json.optBoolean("longClickable", false),
                scrollable = json.optBoolean("scrollable", false),
                enabled = json.optBoolean("enabled", true),
                focused = json.optBoolean("focused", false),
                checked = if (json.has("checked") && !json.isNull("checked")) json.getBoolean("checked") else null,
                isInputField = json.optBoolean("isInputField", false),
                sensitive = json.optBoolean("sensitive", false),
                viewIdResourceName = if (json.has("viewIdResourceName") && !json.isNull("viewIdResourceName")) json.getString("viewIdResourceName") else null,
                className = if (json.has("className") && !json.isNull("className")) json.getString("className") else null,
                contentDescription = if (json.has("contentDescription") && !json.isNull("contentDescription"))
                    ScreenText.fromJson(json.getJSONObject("contentDescription")) else null
            )
        }
    }
}

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

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("left", left)
            put("top", top)
            put("right", right)
            put("bottom", bottom)
        }
    }

    companion object {
        val Unknown: NodeBounds = NodeBounds(left = 0, top = 0, right = 0, bottom = 0)

        fun fromJson(json: JSONObject): NodeBounds {
            return NodeBounds(
                left = json.optInt("left", 0),
                top = json.optInt("top", 0),
                right = json.optInt("right", 0),
                bottom = json.optInt("bottom", 0)
            )
        }
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
 * matches the raw text or the caller passes [forceSensitive] because the
 * source view is a password or secret field. Builders can additionally set
 * [ScreenNode.sensitive] when they know the underlying view is a password or
 * secret field — that flag participates in
 * [ScreenContext.containsSensitiveContent] independently of [isSensitive].
 */
data class ScreenText(
    val raw: String,
    val displaySafe: String,
    val isSensitive: Boolean
) {
    fun toJson(redacted: Boolean = true): JSONObject {
        val textToSerialize = if (redacted) redactedCopy() else this
        return JSONObject().apply {
            put("raw", if (redacted) textToSerialize.displaySafe else textToSerialize.raw)
            put("displaySafe", textToSerialize.displaySafe)
            put("isSensitive", textToSerialize.isSensitive)
        }
    }

    fun redactedCopy(): ScreenText {
        return if (isSensitive) {
            copy(displaySafe = "[REDACTED]")
        } else {
            this
        }
    }

    companion object {
        val Empty: ScreenText = ScreenText(raw = "", displaySafe = "", isSensitive = false)

        fun of(raw: String, forceSensitive: Boolean = false): ScreenText {
            if (raw.isEmpty()) return Empty
            val sensitive = forceSensitive || SensitiveTextRedactor.containsSensitiveText(raw)
            val displaySafe = if (sensitive) "[REDACTED]" else SensitiveTextRedactor.redact(raw)
            return ScreenText(raw = raw, displaySafe = displaySafe, isSensitive = sensitive)
        }

        fun fromJson(json: JSONObject): ScreenText {
            return ScreenText(
                raw = json.optString("raw", ""),
                displaySafe = json.optString("displaySafe", ""),
                isSensitive = json.optBoolean("isSensitive", false)
            )
        }
    }
}
