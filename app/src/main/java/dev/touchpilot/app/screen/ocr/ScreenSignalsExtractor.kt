package dev.touchpilot.app.screen.ocr

/**
 * Parses the text dump produced by `AccessibilityBridge.observeScreen()` into an
 * [ObservedScreenSignals]. The dump format is the per-line tree representation
 * emitted by `TouchPilotAccessibilityService.appendNode`, e.g.:
 *
 * ```
 * TouchPilot screen snapshot
 * - FrameLayout node_id="0" bounds="0,0,1080,2400"
 *   - LinearLayout node_id="0.0" clickable bounds="..."
 *     - EditText node_id="0.0.1" text="user@example.com" focused bounds="..."
 * ```
 *
 * The extractor is platform-free so it can run in JVM unit tests and in
 * instrumented tests, and so a future caller can feed it from either the live
 * Accessibility dump or a captured fixture.
 */
object ScreenSignalsExtractor {
    private val NODE_LINE = Regex("^( *)- (.*)$")
    private val CLASS_NAME = Regex("^(\\S+)")
    private val TEXT_ATTR = Regex("\\btext=\"([^\"]*)\"")
    private val DESC_ATTR = Regex("\\bdesc=\"([^\"]*)\"")

    private val INPUT_CLASS_HINTS = listOf(
        "EditText",
        "AutoCompleteTextView",
        "TextInputEditText",
        "SearchAutoComplete",
    )

    fun fromObserveDump(dump: String, packageName: String? = null): ObservedScreenSignals {
        var totalNodes = 0
        var visibleText = 0
        var clickable = 0
        var inputFields = 0
        var maxDepth = 0

        for (rawLine in dump.lineSequence()) {
            val match = NODE_LINE.matchEntire(rawLine) ?: continue
            val indentSpaces = match.groupValues[1].length
            val body = match.groupValues[2]

            totalNodes += 1
            val depth = indentSpaces / 2
            if (depth > maxDepth) maxDepth = depth

            val hasText = TEXT_ATTR.find(body)?.groupValues?.get(1)?.isNotBlank() == true
            val hasDesc = DESC_ATTR.find(body)?.groupValues?.get(1)?.isNotBlank() == true
            if (hasText || hasDesc) visibleText += 1

            if (" clickable" in " $body") clickable += 1

            val className = CLASS_NAME.find(body)?.groupValues?.get(1).orEmpty()
            if (INPUT_CLASS_HINTS.any { className.contains(it, ignoreCase = true) }) {
                inputFields += 1
            }
        }

        return ObservedScreenSignals(
            totalNodeCount = totalNodes,
            visibleTextCount = visibleText,
            clickableNodeCount = clickable,
            inputFieldCount = inputFields,
            maxTreeDepth = maxDepth,
            packageName = packageName,
        )
    }
}
