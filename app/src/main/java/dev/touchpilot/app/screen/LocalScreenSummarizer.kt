package dev.touchpilot.app.screen

/**
 * Deterministic, local-only summarizer that turns a [ScreenContext] into a
 * short natural-language description plus a set of [SuggestedAction]s the
 * agent or UI can present.
 *
 * No cloud or local model inference — pure rule-based logic over the
 * accessibility-derived context, suitable for Milestone 3 (#41).
 */
object LocalScreenSummarizer {

    private const val MAX_VISIBLE_LABELS = 5

    fun summarize(context: ScreenContext): LocalScreenSummary {
        if (context.isEmpty()) {
            return LocalScreenSummary(
                summary = "I don't have a clear view of the current screen yet.",
                suggestedActions = listOf(
                    SuggestedAction(
                        tool = "observe_screen",
                        label = "Re-read the current screen"
                    ),
                    SuggestedAction(
                        tool = "press_home",
                        label = "Go to the home screen"
                    ),
                    SuggestedAction(
                        tool = "press_back",
                        label = "Go back"
                    )
                ),
                sensitiveScreen = false
            )
        }

        if (context.containsSensitiveContent) {
            return summarizeSensitive(context)
        }

        val screenName = context.screenName()
        val visibleLabels = context.visibleClickableLabels()
        val hasInput = context.inputFields.any { it.text.displaySafe.isNotBlank() || it.focused }
        val hasScrollable = context.scrollableNodes.isNotEmpty()

        val summary = buildString {
            append("I see the ")
            append(screenName)
            append(" screen.")
            if (visibleLabels.isNotEmpty()) {
                append(" Visible actions include ")
                append(visibleLabels.joinToString(", "))
                append('.')
            }
            if (hasInput) {
                append(" You can also type into the visible input.")
            }
            append(" I can tap one of these, ")
            if (hasScrollable) append("scroll, ")
            append("or go back.")
        }

        val actions = mutableListOf<SuggestedAction>()
        for (node in context.clickableNodes) {
            val label = node.text.displaySafe.trim()
            if (label.isEmpty()) continue
            if (actions.count { it.tool == "tap" } >= MAX_VISIBLE_LABELS) break
            actions += SuggestedAction(
                tool = "tap",
                args = mapOf("text" to label),
                label = "Tap '$label'"
            )
        }

        for (input in context.inputFields) {
            val hint = input.text.displaySafe.trim()
            val label = if (hint.isNotEmpty()) "Type into '$hint'" else "Type into the focused input"
            actions += SuggestedAction(
                tool = "type_text",
                args = emptyMap(),
                label = label,
                readyToFire = false
            )
        }

        if (hasScrollable) {
            actions += SuggestedAction(
                tool = "scroll",
                args = mapOf("direction" to "forward"),
                label = "Scroll down"
            )
            actions += SuggestedAction(
                tool = "scroll",
                args = mapOf("direction" to "backward"),
                label = "Scroll up"
            )
        }

        actions += SuggestedAction(tool = "press_back", label = "Go back")

        return LocalScreenSummary(
            summary = summary,
            suggestedActions = actions,
            sensitiveScreen = false
        )
    }

    private fun summarizeSensitive(context: ScreenContext): LocalScreenSummary {
        val screenName = context.screenName()
        val summary = "I see a $screenName screen that may contain sensitive information, so I'm keeping details private. I can go back, scroll, or return home."
        val actions = mutableListOf<SuggestedAction>()
        if (context.scrollableNodes.isNotEmpty()) {
            actions += SuggestedAction(
                tool = "scroll",
                args = mapOf("direction" to "forward"),
                label = "Scroll down"
            )
            actions += SuggestedAction(
                tool = "scroll",
                args = mapOf("direction" to "backward"),
                label = "Scroll up"
            )
        }
        actions += SuggestedAction(tool = "press_back", label = "Go back")
        actions += SuggestedAction(tool = "press_home", label = "Go to the home screen")
        return LocalScreenSummary(
            summary = summary,
            suggestedActions = actions,
            sensitiveScreen = true
        )
    }

    private fun ScreenContext.isEmpty(): Boolean {
        return appLabel.isNullOrBlank() &&
            packageName.isNullOrBlank() &&
            windowTitle.isNullOrBlank() &&
            nodes.isEmpty()
    }

    private fun ScreenContext.screenName(): String {
        val app = appLabel?.takeIf { it.isNotBlank() }
        val title = windowTitle?.takeIf { it.isNotBlank() }
        return when {
            app != null && title != null -> "$app — $title"
            app != null -> app
            title != null -> title
            packageName != null -> packageName
            else -> "current"
        }
    }

    private fun ScreenContext.visibleClickableLabels(): List<String> {
        return clickableNodes
            .asSequence()
            .map { it.text.displaySafe.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_VISIBLE_LABELS)
            .toList()
    }
}
