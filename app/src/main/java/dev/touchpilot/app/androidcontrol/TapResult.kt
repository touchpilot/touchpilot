package dev.touchpilot.app.androidcontrol

/**
 * Result of a tap operation with structured failure context.
 */
sealed class TapResult {
    /** Tap succeeded. [nodeId] is the dot-path id of the tapped node. */
    data class Success(val nodeId: String) : TapResult()

    /**
     * Tap failed.
     *
     * @param reason     Human-readable explanation (safe to surface in logs).
     * @param candidates Candidates that were found but could not be resolved
     *                   (empty for not-found failures).
     */
    data class Failure(
        val reason: String,
        val candidates: List<ResolvedCandidate> = emptyList()
    ) : TapResult()
}
