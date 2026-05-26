package dev.touchpilot.app.tools.targets

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectorTextTest {
    @Test
    fun emptyFactoryReturnsSentinel() {
        assertEquals(SelectorText.Empty, SelectorText.of(""))
    }

    @Test
    fun nonSensitiveTextIsPreserved() {
        val t = SelectorText.of("Settings")
        assertEquals("Settings", t.raw)
        assertEquals("Settings", t.displaySafe)
        assertFalse(t.isSensitive)
    }

    @Test
    fun sensitiveWordIsFlaggedAndRedactedForDisplay() {
        val t = SelectorText.of("Enter your password")
        assertTrue(t.isSensitive)
        assertEquals("Enter your password", t.raw)
        assertEquals("[REDACTED]", t.displaySafe)
    }

    @Test
    fun forceSensitiveRedactsBenignLookingLabel() {
        val t = SelectorText.of("PIN", forceSensitive = true)
        assertTrue(t.isSensitive)
        assertEquals("PIN", t.raw)
        assertEquals("[REDACTED]", t.displaySafe)
    }

    @Test
    fun sensitiveAssignmentValueIsRedactedInDisplay() {
        val t = SelectorText.of("api_key=sk-test-abc123")
        assertTrue(t.isSensitive)
        assertEquals("api_key=sk-test-abc123", t.raw)
        // The display view uses the redactor's partial-replacement output.
        assertTrue("[REDACTED]" in t.displaySafe)
        assertFalse("sk-test-abc123" in t.displaySafe)
    }

    @Test
    fun redactedCopyReplacesSensitiveDisplayWithRedactedSentinel() {
        val t = SelectorText.of("Enter your password")
        val r = t.redactedCopy()
        assertEquals("[REDACTED]", r.displaySafe)
    }

    @Test
    fun redactedCopyIsNoOpOnNonSensitiveText() {
        val t = SelectorText.of("Battery")
        assertEquals(t, t.redactedCopy())
    }

    @Test
    fun toJsonRedactedUsesDisplaySafeForRaw() {
        val t = SelectorText.of("Enter your password")
        val json = t.toJson(redacted = true)
        assertEquals("[REDACTED]", json.getString("raw"))
        assertEquals("[REDACTED]", json.getString("displaySafe"))
        assertTrue(json.getBoolean("isSensitive"))
    }

    @Test
    fun toJsonRawExposesRawTextWhenRequested() {
        val t = SelectorText.of("Enter your password")
        val json = t.toJson(redacted = false)
        assertEquals("Enter your password", json.getString("raw"))
        assertTrue(json.getBoolean("isSensitive"))
    }

    @Test
    fun jsonRoundTripPreservesFields() {
        val original = SelectorText.of("Settings")
        val json = original.toJson(redacted = false)
        val restored = SelectorText.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun fromJsonHandlesMissingFields() {
        val restored = SelectorText.fromJson(JSONObject())
        assertEquals(SelectorText.Empty, restored)
    }
}
