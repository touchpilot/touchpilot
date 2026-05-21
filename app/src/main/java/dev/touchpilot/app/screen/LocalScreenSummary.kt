package dev.touchpilot.app.screen

/**
 * Output of [LocalScreenSummarizer]: a short, redaction-safe description of
 * the current screen plus a conservative set of [SuggestedAction]s the agent
 * or UI can offer to the user.
 *
 * This is *not* execution approval — every suggested action must still flow
 * through normal validation and policy before being executed.
 */
data class LocalScreenSummary(
    val summary: String,
    val suggestedActions: List<SuggestedAction>,
    val sensitiveScreen: Boolean
)

/**
 * Reference to a tool the user could invoke from the current screen.
 *
 * [tool] and [args] use the same names as [dev.touchpilot.app.tools.AndroidToolCatalog].
 * When [readyToFire] is true the action is fully specified and validates against
 * the catalog; when false (e.g. `type_text`), the agent or UI still needs to
 * collect missing arguments from the user.
 */
data class SuggestedAction(
    val tool: String,
    val args: Map<String, String> = emptyMap(),
    val label: String,
    val readyToFire: Boolean = true
)
