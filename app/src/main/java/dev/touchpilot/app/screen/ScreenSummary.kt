package dev.touchpilot.app.screen

/**
 * Deterministic local summary of a [ScreenContext] plus a small list of
 * suggested actions the user could ask TouchPilot to run next.
 *
 * The summary is *descriptive* only — generating it never invokes a tool, an
 * approval, or a network call. Each [SuggestedAction] references an existing
 * tool concept (tap / scroll / press_back / press_home / type_text) so the
 * agent runner can execute it through the normal validation, skill
 * allowlist, policy, approval, and logging path if the user later asks for
 * it.
 */
data class ScreenSummary(
    val sentence: String,
    val suggestedActions: List<SuggestedAction>
)

/**
 * A safe action the agent could perform next on the current screen.
 *
 * - [tool] is one of the tool names known to `AndroidToolCatalog`.
 * - [args] is a JSON-shaped argument map ready for the agent runner.
 * - [label] is the user-facing description (e.g. "Tap Wi-Fi").
 * - [reason] explains the heuristic that produced the suggestion ("visible
 *   clickable text", "scrollable container present", etc.).
 *
 * The intent gate / reasoning core surfaces these as text only. Nothing
 * executes until the user issues an explicit request that the runner
 * validates as usual.
 */
data class SuggestedAction(
    val tool: String,
    val args: Map<String, String>,
    val label: String,
    val reason: String
)
