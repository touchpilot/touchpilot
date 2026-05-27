package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DismissKeyboardCatalogTest {
    @Test
    fun dismissKeyboardIsRegisteredAsLowRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("dismiss_keyboard"))
        assertEquals(ToolRisk.LOW, spec.risk)
    }

    @Test
    fun dismissKeyboardExposesOnlyTimeoutArg() {
        val spec = AndroidToolCatalog.find("dismiss_keyboard")!!
        assertEquals(setOf("timeout_ms"), spec.arguments.keys)
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun dismissKeyboardDoesNotAcceptATextPayload() {
        // The tool only hides UI; accepting a text arg would suggest it
        // submits or modifies input content.
        val spec = AndroidToolCatalog.find("dismiss_keyboard")!!
        assertTrue(!spec.arguments.containsKey("text"))
    }

    @Test
    fun validateAcceptsEmptyArgs() {
        assertNull(AndroidToolCatalog.validate("dismiss_keyboard", emptyMap()))
    }

    @Test
    fun validateAcceptsTimeoutMs() {
        assertNull(
            AndroidToolCatalog.validate(
                "dismiss_keyboard",
                mapOf("timeout_ms" to "750")
            )
        )
    }

    @Test
    fun validateRejectsNonNumericTimeout() {
        val error = AndroidToolCatalog.validate(
            "dismiss_keyboard",
            mapOf("timeout_ms" to "soon")
        )
        assertNotNull(error)
        assertContains(error, "timeout_ms")
    }

    @Test
    fun validateRejectsUnknownArgs() {
        val error = AndroidToolCatalog.validate(
            "dismiss_keyboard",
            mapOf("force" to "true")
        )
        assertNotNull(error)
        assertContains(error, "Unknown argument")
    }

    @Test
    fun retryPolicyTreatsDismissKeyboardAsSingleAttempt() {
        val policy = AndroidToolRetryPolicy()
        val config = policy.configFor("dismiss_keyboard")
        // Single attempt is intentional: any second action could compound on
        // a stale window-list observation. The tool flips the IME show mode
        // via softKeyboardController, which is itself idempotent.
        assertEquals(1, config.maxAttempts)
        assertEquals(false, config.retryable)
    }
}
