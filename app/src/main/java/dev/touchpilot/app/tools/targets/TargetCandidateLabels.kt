package dev.touchpilot.app.tools.targets

/**
 * Display-safe labels for ambiguous target candidates shown in clarification UI.
 */
fun TargetCandidate.displayLabel(): String {
    return selector.text?.displaySafe?.takeIf { it.isNotBlank() }
        ?: selector.contentDescription?.displaySafe?.takeIf { it.isNotBlank() }
        ?: selector.viewIdResourceName?.substringAfterLast('/')
        ?: selector.nodeId?.let { "Node $it" }
        ?: "Option"
}

fun List<TargetCandidate>.displayLabels(): List<String> = map { it.displayLabel() }
