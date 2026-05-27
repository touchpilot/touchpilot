package dev.touchpilot.app.tools

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitForIdleTest {
    @Test
    fun waitForIdleIsRegisteredAsLowRiskObservationTool() {
        val spec = assertNotNull(AndroidToolCatalog.find("wait_for_idle"))

        assertEquals(ToolRisk.LOW, spec.risk)
        assertEquals(setOf("stable_ms", "timeout_ms", "include_bounds"), spec.arguments.keys)
        assertTrue(spec.requiredArguments.isEmpty())
        assertNull(AndroidToolCatalog.validate("wait_for_idle", emptyMap()))
    }

    @Test
    fun validateRejectsBadDurationsAndBoolean() {
        assertEquals(
            "stable_ms must be a number",
            AndroidToolCatalog.validate("wait_for_idle", mapOf("stable_ms" to "soon"))
        )
        assertEquals(
            "timeout_ms must be between 100 and 15000",
            AndroidToolCatalog.validate("wait_for_idle", mapOf("timeout_ms" to "99"))
        )
        assertEquals(
            "include_bounds must be true or false",
            AndroidToolCatalog.validate("wait_for_idle", mapOf("include_bounds" to "yes"))
        )
        assertEquals(
            "stable_ms must not exceed timeout_ms",
            AndroidToolCatalog.validate("wait_for_idle", mapOf("stable_ms" to "1000", "timeout_ms" to "500"))
        )
    }

    @Test
    fun succeedsWhenScreenIsAlreadyStableForWindow() {
        val clock = FakeClock()

        val result = WaitForIdle.waitUntilIdle(
            args = mapOf("stable_ms" to "200", "timeout_ms" to "1000"),
            observe = { screen("Settings") },
            nowMs = { clock.now },
            sleeper = clock::sleep
        )

        assertTrue(result.ok)
        assertEquals("waitForIdle", result.message)
        assertEquals("200", result.data["stable_ms"])
        assertEquals("3", result.data["sample_count"])
    }

    @Test
    fun resetsStableWindowWhileScreenKeepsChanging() {
        val clock = FakeClock()
        val snapshots = mutableListOf(screen("Loading"), screen("Settings"), screen("Settings"), screen("Settings"))

        val result = WaitForIdle.waitUntilIdle(
            args = mapOf("stable_ms" to "200", "timeout_ms" to "1000"),
            observe = { snapshots.removeFirstOrNull() ?: screen("Settings") },
            nowMs = { clock.now },
            sleeper = clock::sleep
        )

        assertTrue(result.ok)
        assertEquals("200", result.data["stable_ms"])
        assertEquals("4", result.data["sample_count"])
    }

    @Test
    fun timesOutWhenScreenNeverStaysStableLongEnough() {
        val clock = FakeClock()
        var index = 0

        val result = WaitForIdle.waitUntilIdle(
            args = mapOf("stable_ms" to "200", "timeout_ms" to "250"),
            observe = { screen("State ${index++}") },
            nowMs = { clock.now },
            sleeper = clock::sleep
        )

        assertFalse(result.ok)
        assertEquals("Timed out waiting for idle screen: stable_ms=200, timeout_ms=250", result.message)
        assertEquals("true", result.data["timed_out"])
    }

    @Test
    fun boundsOnlyChangesAreOptionalInstability() {
        val first = screen("Settings", bounds = NodeBounds(0, 0, 100, 40))
        val second = screen("Settings", bounds = NodeBounds(0, 2, 100, 42))

        assertEquals(
            WaitForIdle.signature(first, includeBounds = false),
            WaitForIdle.signature(second, includeBounds = false)
        )
        assertNotEquals(
            WaitForIdle.signature(first, includeBounds = true),
            WaitForIdle.signature(second, includeBounds = true)
        )
    }

    @Test
    fun retryPolicyTreatsWaitForIdleAsSingleBoundedWait() {
        val config = AndroidToolRetryPolicy().configFor("wait_for_idle")

        assertEquals(1, config.maxAttempts)
        assertFalse(config.retryable)
        assertFalse(config.waitForIdleAfterSuccess)
    }

    private fun screen(
        text: String,
        bounds: NodeBounds = NodeBounds(0, 0, 100, 40)
    ): ScreenContext {
        return ScreenContext(
            appLabel = "Settings",
            packageName = "com.android.settings",
            windowTitle = "Settings",
            nodes = listOf(
                ScreenNode(
                    nodeId = "0",
                    role = NodeRole.TEXT,
                    text = ScreenText.of(text),
                    bounds = bounds,
                )
            )
        )
    }

    private class FakeClock {
        var now: Long = 0L

        fun sleep(ms: Long) {
            now += ms
        }
    }
}
