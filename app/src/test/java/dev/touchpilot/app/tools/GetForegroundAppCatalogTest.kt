package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetForegroundAppCatalogTest {
    @Test
    fun getForegroundAppIsRegisteredAsLowRiskWithNoArgs() {
        val spec = assertNotNull(AndroidToolCatalog.find("get_foreground_app"))
        assertEquals(ToolRisk.LOW, spec.risk)
        assertTrue(spec.arguments.isEmpty())
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validateAcceptsEmptyArgs() {
        assertNull(AndroidToolCatalog.validate("get_foreground_app", emptyMap()))
    }

    @Test
    fun validateRejectsUnknownArgs() {
        val error = AndroidToolCatalog.validate("get_foreground_app", mapOf("foo" to "bar"))
        assertNotNull(error)
        assertTrue(error.startsWith("Unknown argument(s) for get_foreground_app"))
    }

    @Test
    fun retryPolicyTreatsGetForegroundAppAsReadOnly() {
        val config = AndroidToolRetryPolicy().configFor("get_foreground_app")
        assertEquals(1, config.maxAttempts)
        assertEquals(false, config.retryable)
    }
}
