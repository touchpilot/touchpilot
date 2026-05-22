package dev.touchpilot.app.tools.targets

import dev.touchpilot.app.security.SensitiveTextRedactor
import org.json.JSONObject

/**
 * Text component of a [TargetSelector] paired with a redaction-safe view of
 * the same text.
 *
 * Mirrors `ScreenText` from the screen package on purpose: callers should be
 * able to reason about `selector.text` and `node.text` the same way. The
 * `displaySafe` view is what logs, traces, and agent events expose; the
 * `raw` view is reserved for tool-execution paths that need the literal
 * string (e.g. matching a label against a live Accessibility node).
 *
 * [isSensitive] is true whenever
 * [SensitiveTextRedactor.containsSensitiveText] matches the raw text — this is
 * the same heuristic the rest of the project already uses, so a "password"
 * label flagged here is consistent with what `ScreenContext` would flag.
 */
data class SelectorText(
    val raw: String,
    val displaySafe: String,
    val isSensitive: Boolean,
) {
    /**
     * Returns a copy whose [displaySafe] is `"[REDACTED]"` when the underlying
     * text is sensitive. Non-sensitive text is left untouched because the
     * factory in [of] already produced a redacted display view for it.
     */
    fun redactedCopy(): SelectorText {
        return if (isSensitive) copy(displaySafe = "[REDACTED]") else this
    }

    /**
     * Serialize as JSON. When [redacted] is true (the default), the `raw`
     * field is replaced with `displaySafe` so the output is safe to log.
     */
    fun toJson(redacted: Boolean = true): JSONObject {
        val view = if (redacted) redactedCopy() else this
        return JSONObject().apply {
            put("raw", if (redacted) view.displaySafe else view.raw)
            put("displaySafe", view.displaySafe)
            put("isSensitive", view.isSensitive)
        }
    }

    companion object {
        val Empty: SelectorText = SelectorText(raw = "", displaySafe = "", isSensitive = false)

        /**
         * Build a [SelectorText] from a raw label. The sensitivity check and
         * partial-redaction display string come from [SensitiveTextRedactor]
         * so the result matches what the rest of the agent would compute for
         * the same string.
         */
        fun of(raw: String): SelectorText {
            if (raw.isEmpty()) return Empty
            val sensitive = SensitiveTextRedactor.containsSensitiveText(raw)
            val displaySafe = if (sensitive) "[REDACTED]" else SensitiveTextRedactor.redact(raw)
            return SelectorText(raw = raw, displaySafe = displaySafe, isSensitive = sensitive)
        }

        fun fromJson(json: JSONObject): SelectorText {
            return SelectorText(
                raw = json.optString("raw", ""),
                displaySafe = json.optString("displaySafe", ""),
                isSensitive = json.optBoolean("isSensitive", false),
            )
        }
    }
}
