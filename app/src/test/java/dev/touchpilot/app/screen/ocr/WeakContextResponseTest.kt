package dev.touchpilot.app.screen.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WeakContextResponseTest {
    @Test
    fun weakContextWithoutOcrProducesHonestMessage() {
        val message = WeakContextResponse.forWeak(
            quality = ContextQuality.Weak(WeakReason.NO_VISIBLE_TEXT),
            fallback = OcrFallbackResult.Unavailable("OCR runtime not configured."),
        )

        assertTrue("limited readable UI data" in message)
        assertTrue("OCR support is needed" in message)
    }

    @Test
    fun weakContextWithNotAttemptedFallbackUsesSameHonestMessage() {
        val message = WeakContextResponse.forWeak(
            quality = ContextQuality.Weak(WeakReason.SHALLOW_TREE),
            fallback = OcrFallbackResult.NotAttempted,
        )

        assertTrue("limited readable UI data" in message)
    }

    @Test
    fun recognizedTextIsSurfacedAsUntrustedAndNotActedOn() {
        val message = WeakContextResponse.forWeak(
            quality = ContextQuality.Weak(WeakReason.MOSTLY_EMPTY),
            fallback = OcrFallbackResult.Recognized(
                text = listOf("Confirm payment", "Cancel"),
                confidence = 0.55f,
            ),
        )

        assertTrue("have not verified" in message)
        assertTrue("Confirm payment" in message)
        assertTrue("will not act on it without confirmation" in message)
    }

    @Test
    fun recognizedEmptyTextFallsBackToGenericWeakMessage() {
        val message = WeakContextResponse.forWeak(
            quality = ContextQuality.Weak(WeakReason.NO_VISIBLE_TEXT),
            fallback = OcrFallbackResult.Recognized(
                text = emptyList(),
                confidence = 0.10f,
            ),
        )

        assertTrue("limited readable UI data" in message)
        assertFalse("have not verified" in message)
    }

    @Test
    fun recognizedTextIsTruncatedToAvoidLongMessages() {
        val longSnippet = "abcd".repeat(80) // 320 chars
        val message = WeakContextResponse.forWeak(
            quality = ContextQuality.Weak(WeakReason.MOSTLY_EMPTY),
            fallback = OcrFallbackResult.Recognized(listOf(longSnippet), 0.5f),
        )

        // The recognizer snippet inside the quotes must be at most 160 chars.
        val quoted = Regex("\"([^\"]*)\"").find(message)?.groupValues?.get(1)
            ?: error("no quoted snippet in: $message")
        assertTrue(quoted.length <= 160)
    }

    @Test
    fun emptyContextProducesRefusalNotGuess() {
        val message = WeakContextResponse.forEmpty()
        assertEquals(
            "I cannot see any usable screen information right now. " +
                "I will not guess what is on screen.",
            message,
        )
    }
}
