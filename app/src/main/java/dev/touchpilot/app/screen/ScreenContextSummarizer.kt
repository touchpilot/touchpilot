package dev.touchpilot.app.screen

/**
 * Produces a deterministic local [ScreenSummary] from a [ScreenContext].
 *
 * Rules (in order):
 *
 * - If the context has no nodes at all, return a "weak screen" message.
 * - Otherwise build a one-sentence summary using the app label / package /
 *   window title when available, plus the first few visible clickable items.
 * - Emit suggested actions from the visible controls, capped at
 *   [maxSuggestions]. Sensitive nodes (e.g. password fields) are filtered out
 *   of suggestions and never mentioned by raw text in the summary.
 * - Always include the universal back/home pair if the screen has any
 *   content, since those are always-safe navigation aids.
 *
 * The summarizer never executes a tool, calls a network, or touches an
 * approval; it is a pure function of the context.
 */
class ScreenContextSummarizer(
    private val maxNamedItems: Int = 5,
    private val maxSuggestions: Int = 6
) {
    fun summarize(context: ScreenContext): ScreenSummary {
        if (context.nodes.isEmpty()) {
            return ScreenSummary(
                sentence = WeakScreenMessage,
                suggestedActions = emptyList()
            )
        }

        val visibleClickables = context.clickableNodes.filter { it.isSafeToSurface }

        val sentence = buildSentence(context, visibleClickables)
        val suggestions = buildSuggestions(context, visibleClickables)

        return ScreenSummary(sentence = sentence, suggestedActions = suggestions)
    }

    private fun buildSentence(
        context: ScreenContext,
        visibleClickables: List<ScreenNode>
    ): String {
        val screenName = context.appLabel?.takeIf { it.isNotBlank() }
            ?: context.windowTitle?.takeIf { it.isNotBlank() }
            ?: context.packageName?.takeIf { it.isNotBlank() }

        val opener = if (screenName != null) {
            "I see the $screenName screen."
        } else {
            "I can see the current screen."
        }

        val namedLabels = visibleClickables
            .take(maxNamedItems)
            .map { it.text.displaySafe }
            .filter { it.isNotBlank() }

        val visibleClause = when {
            namedLabels.isEmpty() && context.inputFields.isNotEmpty() ->
                " It has an input field."
            namedLabels.isEmpty() -> ""
            namedLabels.size == 1 -> " Visible actions include ${namedLabels[0]}."
            else -> " Visible actions include ${joinHumanReadable(namedLabels)}."
        }

        val tail = buildTail(context)
        return opener + visibleClause + tail
    }

    private fun buildTail(context: ScreenContext): String {
        val parts = mutableListOf<String>()
        if (context.scrollableNodes.isNotEmpty()) parts += "scroll"
        parts += "go back or home"
        return " I can " + joinHumanReadable(parts) + "."
    }

    private fun buildSuggestions(
        context: ScreenContext,
        visibleClickables: List<ScreenNode>
    ): List<SuggestedAction> {
        val firstInput = context.inputFields.firstOrNull { it.isSafeToSurface || it.text.raw.isBlank() }
            ?.takeUnless { it.sensitive || it.text.isSensitive }
        val hasScroll = context.scrollableNodes.isNotEmpty()

        // Reserve the always-safe navigation pair in the cap first, then a slot
        // each for the distinct type_text / scroll capabilities, and give the
        // remainder to visible taps. Counting type_text and scroll against the
        // dynamic budget up front (instead of appending them after a tap-only
        // budget and truncating the whole list at the end) is what guarantees
        // the back/home pair is never dropped — honoring the invariant above.
        val dynamicBudget = (maxSuggestions - SafeNavigationSlots).coerceAtLeast(0)
        val reservedForDistinct = (if (firstInput != null) 1 else 0) + (if (hasScroll) 1 else 0)
        val tapBudget = (dynamicBudget - reservedForDistinct).coerceAtLeast(0)

        val dynamic = mutableListOf<SuggestedAction>()

        visibleClickables.take(tapBudget).forEach { node ->
            dynamic += SuggestedAction(
                tool = "tap",
                args = mapOf("text" to node.text.raw),
                label = "Tap ${node.text.displaySafe}",
                reason = "visible clickable text"
            )
        }

        if (firstInput != null) {
            val descriptor = firstInput.text.displaySafe.ifBlank { "input field" }
            dynamic += SuggestedAction(
                tool = "type_text",
                args = emptyMap(),
                label = "Type into the $descriptor",
                reason = "visible input field"
            )
        }

        if (hasScroll) {
            dynamic += SuggestedAction(
                tool = "scroll",
                args = mapOf("direction" to "forward"),
                label = "Scroll down",
                reason = "scrollable container present"
            )
        }

        // Safe navigation slots are always included while content exists, so
        // the user always has a documented way to back out without re-reading
        // the visible UI. Appended after the budgeted dynamic suggestions so the
        // final list can never truncate them.
        val navigation = listOf(
            SuggestedAction(
                tool = "press_back",
                args = emptyMap(),
                label = "Go back",
                reason = "always-safe navigation"
            ),
            SuggestedAction(
                tool = "press_home",
                args = emptyMap(),
                label = "Go home",
                reason = "always-safe navigation"
            )
        )

        return dynamic.take(dynamicBudget) + navigation
    }

    private fun joinHumanReadable(items: List<String>): String {
        return when (items.size) {
            0 -> ""
            1 -> items[0]
            2 -> "${items[0]} and ${items[1]}"
            else -> items.dropLast(1).joinToString(", ") + ", and " + items.last()
        }
    }

    /**
     * A node is safe to mention by name only when neither the explicit
     * password/secret flag is set *and* the redactor has not classified its
     * text as sensitive. The combined check matches
     * [ScreenContext.containsSensitiveContent], so a node that contributes to
     * "this screen has sensitive content" never appears verbatim in the
     * sentence or the suggestion list.
     */
    private val ScreenNode.isSafeToSurface: Boolean
        get() = !sensitive && !text.isSensitive && text.raw.isNotBlank()

    companion object {
        const val WeakScreenMessage: String =
            "I can see this screen, but the app exposes limited readable UI data right now. " +
                "I can still go back or home, or you can switch apps first."

        // press_back + press_home reservations in the suggestion cap.
        private const val SafeNavigationSlots: Int = 2
    }
}
