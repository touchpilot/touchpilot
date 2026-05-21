package dev.touchpilot.app.screen.ocr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OcrFallbackTest {
    @Test
    fun noOpFallbackReturnsUnavailable() {
        val fallback: OcrFallback = NoOpOcrFallback()

        val result = fallback.attempt(
            OcrRequest(reason = WeakReason.NO_VISIBLE_TEXT, packageName = "com.example")
        )

        val unavailable = assertIs<OcrFallbackResult.Unavailable>(result)
        assertTrue("OCR runtime not configured" in unavailable.reason)
        assertTrue("future fallback" in unavailable.reason)
    }

    @Test
    fun recognizedResultRejectsOutOfRangeConfidence() {
        assertFailsWith<IllegalArgumentException> {
            OcrFallbackResult.Recognized(text = listOf("hello"), confidence = 1.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            OcrFallbackResult.Recognized(text = listOf("hello"), confidence = -0.1f)
        }
    }

    @Test
    fun resultTypesAreDistinguishable() {
        // Sanity check: callers can pattern-match on the sealed hierarchy without
        // a default branch.
        val results: List<OcrFallbackResult> = listOf(
            OcrFallbackResult.NotAttempted,
            OcrFallbackResult.Unavailable("no runtime"),
            OcrFallbackResult.Recognized(listOf("Settings"), 0.42f),
        )

        val labels = results.map { result ->
            when (result) {
                OcrFallbackResult.NotAttempted -> "not-attempted"
                is OcrFallbackResult.Unavailable -> "unavailable"
                is OcrFallbackResult.Recognized -> "recognized"
            }
        }

        assertEquals(listOf("not-attempted", "unavailable", "recognized"), labels)
    }
}
