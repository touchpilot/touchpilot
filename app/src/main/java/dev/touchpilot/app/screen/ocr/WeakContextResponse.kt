package dev.touchpilot.app.screen.ocr

/**
 * Generates the honest, conservative response copy TouchPilot should produce when
 * Accessibility data is too thin to summarize a screen. Keeping this in one place
 * makes it straightforward to test phrasing and to keep the agent from inventing
 * detail it cannot see.
 */
object WeakContextResponse {
    private const val GENERIC_WEAK =
        "I can see this screen, but the app exposes limited readable UI data. " +
            "OCR support is needed for better understanding here."

    private const val EMPTY =
        "I cannot see any usable screen information right now. " +
            "I will not guess what is on screen."

    fun forWeak(quality: ContextQuality.Weak, fallback: OcrFallbackResult): String {
        return when (fallback) {
            OcrFallbackResult.NotAttempted -> GENERIC_WEAK
            is OcrFallbackResult.Unavailable -> GENERIC_WEAK
            is OcrFallbackResult.Recognized -> {
                if (fallback.text.isEmpty()) {
                    GENERIC_WEAK
                } else {
                    val snippet = fallback.text.joinToString(separator = " | ")
                        .take(160)
                    "I cannot read this screen reliably from Accessibility data " +
                        "(${quality.reason.name.lowercase().replace('_', ' ')}). " +
                        "OCR returned text I have not verified: \"$snippet\". " +
                        "I will not act on it without confirmation."
                }
            }
        }
    }

    fun forEmpty(): String = EMPTY
}
