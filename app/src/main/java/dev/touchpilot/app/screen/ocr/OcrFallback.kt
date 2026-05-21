package dev.touchpilot.app.screen.ocr

/**
 * Boundary interface for an on-device OCR pass. Milestone 3 defines this contract
 * but does not pick a runtime: an OCR implementation can be added behind this
 * interface when a local OCR/VLM runtime is selected.
 *
 * Implementations MUST:
 * - run fully on device (no network calls)
 * - treat their own output as untrusted and noisy
 * - return [OcrFallbackResult.Unavailable] rather than throw when the runtime is
 *   not present
 *
 * Callers MUST run any [OcrFallbackResult.Recognized] text through the existing
 * sensitive-text redactor before logging or tracing it.
 */
interface OcrFallback {
    fun attempt(request: OcrRequest): OcrFallbackResult
}

/**
 * Request to the OCR boundary. Carries only the metadata an OCR runtime needs to
 * decide whether to run; pixel data is intentionally not part of this contract and
 * must be sourced by a future runtime-side capture step under the same local-only
 * constraints.
 */
data class OcrRequest(
    val reason: WeakReason,
    val packageName: String? = null,
)

/**
 * Placeholder result type for the OCR boundary. New variants must keep the
 * "all output is untrusted" property: nothing here is treated as a tool argument
 * without passing through the standard validation and policy pipeline.
 */
sealed interface OcrFallbackResult {
    /** No OCR pass was performed; the caller should fall back to a conservative path. */
    object NotAttempted : OcrFallbackResult

    /** OCR runtime is not configured or could not run. */
    data class Unavailable(val reason: String) : OcrFallbackResult

    /**
     * OCR returned text. The list is the raw, untrusted recognizer output. Confidence
     * is a 0.0-1.0 scalar reported by the runtime; callers should still redact and
     * treat low confidence as additional evidence of weak context.
     */
    data class Recognized(val text: List<String>, val confidence: Float) : OcrFallbackResult {
        init {
            require(confidence in 0.0f..1.0f) { "confidence must be in [0.0, 1.0]" }
        }
    }
}

/**
 * Default boundary implementation used when no local OCR runtime is wired up.
 * Always returns [OcrFallbackResult.Unavailable] so callers degrade to honest
 * "I can see this screen but cannot read it" copy.
 */
class NoOpOcrFallback : OcrFallback {
    override fun attempt(request: OcrRequest): OcrFallbackResult =
        OcrFallbackResult.Unavailable(
            "OCR runtime not configured. Local OCR/VLM is a future fallback."
        )
}
