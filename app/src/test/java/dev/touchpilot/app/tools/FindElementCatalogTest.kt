package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindElementCatalogTest {
    @Test
    fun findElementIsRegisteredAsLowRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("find_element"))
        assertEquals(ToolRisk.LOW, spec.risk)
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun findElementAdvertisesEveryQueryArgument() {
        val spec = AndroidToolCatalog.find("find_element")!!
        listOf("text", "content_description", "node_id", "class_name", "match", "limit")
            .forEach { arg ->
                assertTrue(spec.arguments.containsKey(arg), "missing arg: $arg")
            }
    }

    @Test
    fun validationAcceptsTextFilter() {
        assertNull(AndroidToolCatalog.validate("find_element", mapOf("text" to "Settings")))
    }

    @Test
    fun validationAcceptsContentDescriptionFilter() {
        assertNull(AndroidToolCatalog.validate("find_element", mapOf("content_description" to "Open menu")))
    }

    @Test
    fun validationAcceptsNodeIdFilter() {
        assertNull(AndroidToolCatalog.validate("find_element", mapOf("node_id" to "0.1.2")))
    }

    @Test
    fun validationAcceptsClassNameFilter() {
        assertNull(AndroidToolCatalog.validate("find_element", mapOf("class_name" to "android.widget.Button")))
    }

    @Test
    fun validationAcceptsExplicitMatchModes() {
        listOf("exact", "contains", "semantic").forEach { mode ->
            assertNull(
                AndroidToolCatalog.validate(
                    "find_element",
                    mapOf("text" to "Save", "match" to mode)
                ),
                "match=$mode should validate"
            )
        }
    }

    @Test
    fun validationAcceptsLimitAtBoundaries() {
        assertNull(AndroidToolCatalog.validate("find_element", mapOf("text" to "Save", "limit" to "1")))
        assertNull(AndroidToolCatalog.validate("find_element", mapOf("text" to "Save", "limit" to "25")))
    }

    @Test
    fun validationRejectsNoFilter() {
        val error = AndroidToolCatalog.validate("find_element", emptyMap())
        assertNotNull(error)
        assertContains(error, "at least one filter")
    }

    @Test
    fun validationRejectsBlankFilters() {
        val error = AndroidToolCatalog.validate(
            "find_element",
            mapOf("text" to "  ", "content_description" to "")
        )
        assertNotNull(error)
        assertContains(error, "at least one filter")
    }

    @Test
    fun validationRejectsUnknownMatchMode() {
        val error = AndroidToolCatalog.validate(
            "find_element",
            mapOf("text" to "Settings", "match" to "fuzzy")
        )
        assertNotNull(error)
        assertContains(error, "match must be one of")
    }

    @Test
    fun validationRejectsOutOfRangeLimit() {
        val tooLow = AndroidToolCatalog.validate(
            "find_element",
            mapOf("text" to "Save", "limit" to "0")
        )
        val tooHigh = AndroidToolCatalog.validate(
            "find_element",
            mapOf("text" to "Save", "limit" to "100")
        )
        val notNumber = AndroidToolCatalog.validate(
            "find_element",
            mapOf("text" to "Save", "limit" to "lots")
        )
        assertNotNull(tooLow)
        assertNotNull(tooHigh)
        assertNotNull(notNumber)
        assertContains(tooLow, "limit must be an integer")
        assertContains(tooHigh, "limit must be an integer")
        assertContains(notNumber, "limit must be an integer")
    }

    @Test
    fun validationRejectsUnknownArgs() {
        val error = AndroidToolCatalog.validate(
            "find_element",
            mapOf("text" to "Settings", "extra" to "nope")
        )
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun retryPolicyTreatsFindElementAsReadOnly() {
        val config = AndroidToolRetryPolicy().configFor("find_element")
        assertEquals(1, config.maxAttempts)
        assertEquals(false, config.retryable)
    }
}
