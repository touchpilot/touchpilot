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
    fun recognizedSensitiveTextIsRedactedBeforeSurfacing() {
        // OCR output is untrusted and may read secrets straight off the screen
        // (passwords, OTPs, tokens). The OcrFallback contract requires callers
        // to run Recognized text through the sensitive-text redactor before
        // surfacing or tracing it.
        val message = WeakContextResponse.forWeak(
            quality = ContextQuality.Weak(WeakReason.MOSTLY_EMPTY),
            fallback = OcrFallbackResult.Recognized(
                text = listOf("password: hunter2", "Your code is 123456"),
                confidence = 0.61f,
            ),
        )

        assertFalse("hunter2" in message)
        assertFalse("123456" in message)
        assertTrue("[REDACTED]" in message)
        // The untrusted framing must remain so the agent never acts on it.
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
