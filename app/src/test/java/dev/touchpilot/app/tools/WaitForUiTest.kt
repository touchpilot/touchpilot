package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WaitForUiTest {
    @Test
    fun waitForUiIsRegisteredAsLowRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("wait_for_ui"))
        assertEquals(ToolRisk.LOW, spec.risk)
        assertEquals(setOf("text", "timeout_ms"), spec.arguments.keys)
        assertEquals(setOf("text"), spec.requiredArguments)
    }

    @Test
    fun logArgsDoesNotLeakRawQueryText() {
        val logArgs = WaitForUi.logArgs("secret-token-value", 5_000L)
        assertFalse(logArgs.contains("secret-token-value"))
        assertTrue(logArgs.contains("text_length=18"))
        assertTrue(logArgs.contains("timeout_ms=5000"))
    }
}
