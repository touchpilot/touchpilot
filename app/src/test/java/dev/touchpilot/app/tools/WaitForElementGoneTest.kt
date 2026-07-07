package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitForElementGoneTest {

    @Test
    fun registeredAsLowRiskWithFullQueryArgs() {
        val spec = assertNotNull(AndroidToolCatalog.find("wait_for_element_gone"))
        assertEquals(ToolRisk.LOW, spec.risk)
        assertEquals(
            setOf("text", "content_description", "node_id", "class_name", "match", "timeout_ms"),
            spec.arguments.keys
        )
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validationAcceptsEachFilter() {
        assertNull(AndroidToolCatalog.validate("wait_for_element_gone", mapOf("text" to "Loading")))
        assertNull(AndroidToolCatalog.validate("wait_for_element_gone", mapOf("content_description" to "Progress")))
        assertNull(AndroidToolCatalog.validate("wait_for_element_gone", mapOf("node_id" to "0.3")))
        assertNull(
            AndroidToolCatalog.validate("wait_for_element_gone", mapOf("class_name" to "android.widget.ProgressBar"))
        )
    }

    @Test
    fun validationRejectsNoFilterWithToolSpecificMessage() {
        val error = AndroidToolCatalog.validate("wait_for_element_gone", emptyMap())
        assertNotNull(error)
        assertEquals(
            "wait_for_element_gone requires at least one filter: " +
                "text, content_description, node_id, or class_name",
            error
        )
    }

    @Test
    fun validationRejectsBlankFilters() {
        val error = AndroidToolCatalog.validate(
            "wait_for_element_gone",
            mapOf("text" to "  ", "content_description" to "")
        )
        assertNotNull(error)
        assertContains(error, "at least one filter")
    }

    @Test
    fun validationRejectsUnknownMatchMode() {
        assertEquals(
            "wait_for_element_gone match must be one of: exact, contains, semantic",
            AndroidToolCatalog.validate("wait_for_element_gone", mapOf("text" to "Loading", "match" to "fuzzy"))
        )
    }

    @Test
    fun validationAcceptsExplicitMatchModes() {
        listOf("exact", "contains", "semantic").forEach { mode ->
            assertNull(
                AndroidToolCatalog.validate("wait_for_element_gone", mapOf("text" to "Loading", "match" to mode)),
                "match=$mode should validate"
            )
        }
    }

    @Test
    fun validationRejectsNonNumericTimeout() {
        assertEquals(
            "timeout_ms must be a number",
            AndroidToolCatalog.validate("wait_for_element_gone", mapOf("text" to "Loading", "timeout_ms" to "soon"))
        )
    }

    @Test
    fun validationRejectsUnknownArgs() {
        val error = AndroidToolCatalog.validate(
            "wait_for_element_gone",
            mapOf("text" to "Loading", "extra" to "nope")
        )
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun goneResultReportsElementRemoved() {
        val result = WaitForElementGone.goneResult(mapOf("text" to "Loading", "match" to "exact"))
        assertTrue(result.ok)
        assertEquals("true", result.data["gone"])
        assertEquals("exact", result.data["match_mode"])
    }

    @Test
    fun timeoutResultReportsStillPresent() {
        val result = WaitForElementGone.timeoutResult(mapOf("text" to "Loading"), timeoutMs = 5_000L, lastMatchCount = 2)
        assertFalse(result.ok)
        assertEquals("false", result.data["gone"])
        assertEquals("true", result.data["timed_out"])
        assertEquals("5000", result.data["timeout_ms"])
        assertEquals("2", result.data["last_match_count"])
        assertEquals("contains", result.data["match_mode"])
    }

    @Test
    fun timeoutResultMessageDoesNotLeakQueryText() {
        val result = WaitForElementGone.timeoutResult(
            mapOf("text" to "secret-token-value"),
            timeoutMs = 5_000L,
            lastMatchCount = 1,
        )
        assertFalse(result.message.contains("secret-token-value"))
    }

    @Test
    fun retryPolicyTreatsWaitForElementGoneAsSingleAttempt() {
        val config = AndroidToolRetryPolicy().configFor("wait_for_element_gone")
        assertEquals(1, config.maxAttempts)
        assertFalse(config.retryable)
    }
}
