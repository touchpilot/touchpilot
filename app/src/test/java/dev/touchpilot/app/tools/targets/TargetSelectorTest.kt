package dev.touchpilot.app.tools.targets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetSelectorTest {
    // --- Construction + validation ---

    @Test
    fun emptyConstantHasNoIdentifyingDimensions() {
        assertTrue(TargetSelector.Empty.isEmpty)
        assertFalse(TargetSelector.Empty.isValid())
    }

    @Test
    fun selectorWithJustTextIsValid() {
        val s = TargetSelector(text = SelectorText.of("Settings"))
        assertFalse(s.isEmpty)
        assertTrue(s.isValid())
    }

    @Test
    fun selectorWithJustNodeIdIsValid() {
        val s = TargetSelector(nodeId = "0.2.1")
        assertTrue(s.isValid())
    }

    @Test
    fun selectorWithJustBoundsIsValid() {
        val s = TargetSelector(bounds = TargetBounds(0, 0, 100, 100))
        assertTrue(s.isValid())
    }

    @Test
    fun selectorWithJustViewIdIsValid() {
        val s = TargetSelector(viewIdResourceName = "com.example:id/save")
        assertTrue(s.isValid())
    }

    @Test
    fun selectorWithJustContentDescriptionIsValid() {
        val s = TargetSelector(contentDescription = SelectorText.of("Compose"))
        assertTrue(s.isValid())
    }

    @Test
    fun selectorWithMetadataOnlyIsStillInvalid() {
        // role / packageName / source by themselves cannot identify a node.
        val s = TargetSelector(
            role = TargetRole.BUTTON,
            packageName = "com.android.settings",
            source = SelectorSource.AGENT,
        )
        assertTrue(s.isEmpty)
        assertFalse(s.isValid())
    }

    // --- Confidence invariant ---

    @Test
    fun confidenceOutOfRangeRejected() {
        assertFailsWith<IllegalArgumentException> {
            TargetSelector(nodeId = "0", confidence = 1.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            TargetSelector(nodeId = "0", confidence = -0.1f)
        }
    }

    @Test
    fun confidenceInRangeAccepted() {
        TargetSelector(nodeId = "0", confidence = 0.0f)
        TargetSelector(nodeId = "0", confidence = 0.5f)
        TargetSelector(nodeId = "0", confidence = 1.0f)
    }

    // --- Sensitivity propagation ---

    @Test
    fun sensitiveTextLabelFlagsSelector() {
        val s = TargetSelector(text = SelectorText.of("Enter your password"))
        assertTrue(s.containsSensitiveText)
    }

    @Test
    fun sensitiveContentDescriptionFlagsSelector() {
        val s = TargetSelector(contentDescription = SelectorText.of("password field"))
        assertTrue(s.containsSensitiveText)
    }

    @Test
    fun nonSensitiveTextDoesNotFlagSelector() {
        val s = TargetSelector(text = SelectorText.of("Battery"))
        assertFalse(s.containsSensitiveText)
    }

    @Test
    fun nodeIdAndBoundsAloneDoNotFlagSensitivity() {
        val s = TargetSelector(nodeId = "0.1.2", bounds = TargetBounds(10, 20, 30, 40))
        assertFalse(s.containsSensitiveText)
    }

    // --- Redaction ---

    @Test
    fun redactedCopyHidesSensitiveTextInDisplaySafe() {
        val s = TargetSelector(
            text = SelectorText.of("Enter your password"),
            contentDescription = SelectorText.of("password field"),
            nodeId = "0.1",
        )
        val r = s.redactedCopy()
        assertEquals("[REDACTED]", r.text?.displaySafe)
        assertEquals("[REDACTED]", r.contentDescription?.displaySafe)
        assertEquals("0.1", r.nodeId, "non-text dimensions must survive redaction")
    }

    @Test
    fun redactedCopyIsNoOpOnNonSensitiveSelector() {
        val s = TargetSelector(
            text = SelectorText.of("Battery"),
            nodeId = "0.4",
            bounds = TargetBounds(0, 0, 100, 100),
        )
        assertEquals(s, s.redactedCopy())
    }

    // --- JSON serialization ---

    @Test
    fun toJsonRedactedHidesSensitiveText() {
        val s = TargetSelector(
            text = SelectorText.of("Enter your password"),
            nodeId = "0",
            source = SelectorSource.MODEL,
        )
        val json = s.toJson(redacted = true)
        val textObj = json.getJSONObject("text")
        assertEquals("[REDACTED]", textObj.getString("raw"))
        assertEquals("[REDACTED]", textObj.getString("displaySafe"))
        assertTrue(json.getBoolean("containsSensitiveText"))
        assertEquals("MODEL", json.getString("source"))
    }

    @Test
    fun toJsonRawExposesRawTextWhenRequested() {
        val s = TargetSelector(text = SelectorText.of("Enter your password"))
        val json = s.toJson(redacted = false)
        assertEquals("Enter your password", json.getJSONObject("text").getString("raw"))
    }

    @Test
    fun toJsonEmitsNullJsonForMissingFields() {
        val s = TargetSelector(text = SelectorText.of("Settings"))
        val json = s.toJson(redacted = true)
        assertTrue(json.isNull("nodeId"))
        assertTrue(json.isNull("bounds"))
        assertTrue(json.isNull("viewIdResourceName"))
        assertTrue(json.isNull("role"))
    }

    @Test
    fun jsonRoundTripPreservesEveryDimension() {
        val original = TargetSelector(
            text = SelectorText.of("Settings"),
            contentDescription = SelectorText.of("Open settings"),
            nodeId = "0.2.1",
            bounds = TargetBounds(10, 20, 110, 220),
            viewIdResourceName = "com.android.settings:id/dashboard",
            role = TargetRole.BUTTON,
            packageName = "com.android.settings",
            windowTitle = "Settings",
            confidence = 0.92f,
            source = SelectorSource.OBSERVATION,
        )
        val restored = TargetSelector.fromJson(original.toJson(redacted = false))
        assertEquals(original, restored)
    }

    @Test
    fun jsonRoundTripFallsBackToUnspecifiedSourceOnGarbage() {
        val s = TargetSelector(nodeId = "0").toJson(redacted = true)
        s.put("source", "NOT_A_REAL_SOURCE")
        val restored = TargetSelector.fromJson(s)
        assertEquals(SelectorSource.UNSPECIFIED, restored.source)
    }

    @Test
    fun jsonRoundTripDropsUnknownRoleValuesToNull() {
        val s = TargetSelector(nodeId = "0", role = TargetRole.BUTTON).toJson(redacted = true)
        s.put("role", "NOT_A_REAL_ROLE")
        val restored = TargetSelector.fromJson(s)
        assertNull(restored.role)
    }

    // --- toRedactedJson ---

    @Test
    fun toRedactedJsonIsPrettyPrintedAndContainsRedactedSentinel() {
        val s = TargetSelector(text = SelectorText.of("Enter your password"), nodeId = "0")
        val pretty = s.toRedactedJson()
        assertTrue(pretty.contains("[REDACTED]"))
        assertTrue(pretty.contains("\"source\""))
        assertTrue(pretty.contains("\n"))
    }

    // --- Equality + Empty ---

    @Test
    fun copyWithSameFieldsIsEqual() {
        val a = TargetSelector(nodeId = "0", source = SelectorSource.USER)
        val b = a.copy()
        assertEquals(a, b)
    }

    @Test
    fun differentSourcesAreNotEqual() {
        val a = TargetSelector(nodeId = "0", source = SelectorSource.USER)
        val b = TargetSelector(nodeId = "0", source = SelectorSource.MODEL)
        assertNotEquals(a, b)
    }
}
