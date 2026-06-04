package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LongPressCatalogTest {
    @Test
    fun longPressIsRegisteredAsMediumRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("long_press"))
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertEquals(setOf("text", "node_id", "bounds", "view_id"), spec.arguments.keys)
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validateAcceptsEachSingleSelector() {
        assertNull(AndroidToolCatalog.validate("long_press", mapOf("text" to "App info")))
        assertNull(AndroidToolCatalog.validate("long_press", mapOf("node_id" to "0.1.2")))
        assertNull(AndroidToolCatalog.validate("long_press", mapOf("bounds" to "0,0,100,50")))
        assertNull(AndroidToolCatalog.validate("long_press", mapOf("view_id" to "com.example:id/item")))
    }

    @Test
    fun validateRejectsMissingOrMultipleSelectors() {
        assertEquals(
            "long_press requires exactly one selector: text, node_id, bounds, or view_id",
            AndroidToolCatalog.validate("long_press", emptyMap())
        )

        val error = AndroidToolCatalog.validate(
            "long_press",
            mapOf("text" to "App info", "view_id" to "com.example:id/item")
        )
        assertEquals("long_press requires exactly one selector: text, node_id, bounds, or view_id", error)
    }

    @Test
    fun validateRejectsMalformedBounds() {
        val error = AndroidToolCatalog.validate("long_press", mapOf("bounds" to "bad"))
        assertEquals("bounds must be left,top,right,bottom", error)
    }

    @Test
    fun retryPolicyTreatsLongPressLikeNavigationAction() {
        val config = AndroidToolRetryPolicy().configFor("long_press")
        assertEquals(3, config.maxAttempts)
        assertTrue(config.retryable)
        assertTrue(config.waitForIdleAfterSuccess)
    }

    @Test
    fun retryPolicyDoesNotRetryAmbiguousLongPressFailures() {
        val decision = AndroidToolRetryPolicy().shouldRetry(
            "long_press",
            ToolResult(ok = false, message = "Ambiguous long-press target: multiple matches"),
            attempt = 0
        )
        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.NON_RETRYABLE, decision.category)
        assertContains(decision.reason, "non_retryable")
    }
}
