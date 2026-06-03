package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentAppsCatalogTest {
    @Test
    fun recentAppsIsRegisteredAsMediumRiskNavigation() {
        val spec = assertNotNull(AndroidToolCatalog.find("recent_apps"))
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertTrue(spec.arguments.isEmpty())
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validateAcceptsNoArguments() {
        assertNull(AndroidToolCatalog.validate("recent_apps", emptyMap()))
    }

    @Test
    fun validateRejectsUnknownArguments() {
        val error = AndroidToolCatalog.validate("recent_apps", mapOf("target" to "maps"))
        assertEquals("Unknown argument(s) for recent_apps: target", error)
    }

    @Test
    fun retryPolicyTreatsRecentAppsLikeNavigationAction() {
        val config = AndroidToolRetryPolicy().configFor("recent_apps")
        assertEquals(3, config.maxAttempts)
        assertTrue(config.retryable)
        assertTrue(config.waitForIdleAfterSuccess)
    }

    @Test
    fun retryPolicyRetriesTransientRecentAppsFailure() {
        val decision = AndroidToolRetryPolicy().shouldRetry(
            "recent_apps",
            ToolResult(ok = false, message = "Unable to perform recent apps"),
            attempt = 0
        )
        assertTrue(decision.retry)
        assertEquals(ToolFailureCategory.RETRYABLE_TRANSIENT, decision.category)
    }

    @Test
    fun retryPolicyDoesNotRetryNonRetryableRecentAppsFailure() {
        val decision = AndroidToolRetryPolicy().shouldRetry(
            "recent_apps",
            ToolResult(ok = false, message = "policy=block recent_apps blocked by policy"),
            attempt = 0
        )
        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.NON_RETRYABLE, decision.category)
        assertContains(decision.reason, "non_retryable")
    }
}
