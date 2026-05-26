package dev.touchpilot.app.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidToolRetryPolicyTest {
    private val policy = AndroidToolRetryPolicy()

    @Test
    fun defaultPolicyDefinesToolSpecificAttemptsAndIdleWaits() {
        assertEquals(1, policy.configFor("observe_screen").maxAttempts)
        assertFalse(policy.configFor("observe_screen").retryable)

        val tap = policy.configFor("tap")
        assertEquals(3, tap.maxAttempts)
        assertEquals(250L, tap.retryDelayMs)
        assertEquals(1_500L, tap.idleTimeoutMs)
        assertTrue(tap.waitForIdleAfterSuccess)

        val longPress = policy.configFor("long_press")
        assertEquals(3, longPress.maxAttempts)
        assertTrue(longPress.retryable)
        assertTrue(longPress.waitForIdleAfterSuccess)

        assertFalse(policy.configFor("wait_for_ui").waitForIdleAfterSuccess)
    }

    @Test
    fun unableToPerformLongPressIsRetryableTransient() {
        val result = ToolResult(ok = false, message = "Unable to perform long-press on resolved target")

        val decision = policy.shouldRetry("long_press", result, attempt = 0)

        assertTrue(decision.retry)
        assertEquals(ToolFailureCategory.RETRYABLE_TRANSIENT, decision.category)
    }

    @Test
    fun ambiguousLongPressTargetIsNotRetryable() {
        val result = ToolResult(ok = false, message = "Ambiguous long-press target: two matches")

        val decision = policy.shouldRetry("long_press", result, attempt = 0)

        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.NON_RETRYABLE, decision.category)
    }

    @Test
    fun retryableTransientFailuresCanRetryWithinBound() {
        val result = ToolResult(ok = false, message = "Unable to perform scroll on resolved container")

        val decision = policy.shouldRetry("scroll", result, attempt = 0)

        assertTrue(decision.retry)
        assertEquals(ToolFailureCategory.RETRYABLE_TRANSIENT, decision.category)
        assertEquals("retryable retryable_transient failure", decision.reason)
        assertEquals(2, decision.nextAttempt)
    }

    @Test
    fun retryStopsAtMaxAttempts() {
        val result = ToolResult(ok = false, message = "Unable to perform scroll on resolved container")

        val decision = policy.shouldRetry("scroll", result, attempt = 2)

        assertFalse(decision.retry)
        assertEquals("max attempts reached", decision.reason)
    }

    @Test
    fun nonRetryableFailuresDoNotRetry() {
        val result = ToolResult(ok = false, message = "Ambiguous input target: multiple nodes")

        val decision = policy.shouldRetry("type_text", result, attempt = 0)

        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.NON_RETRYABLE, decision.category)
        assertEquals("failure is non_retryable", decision.reason)
    }

    @Test
    fun unknownFailuresDoNotRetry() {
        val result = ToolResult(ok = false, message = "Something unexpected happened")

        val decision = policy.shouldRetry("tap", result, attempt = 0)

        assertFalse(decision.retry)
        assertEquals(ToolFailureCategory.UNKNOWN, decision.category)
    }

    @Test
    fun nonRetryableToolsNeverRetry() {
        val result = ToolResult(ok = false, message = "No active window is available")

        val decision = policy.shouldRetry("observe_screen", result, attempt = 0)

        assertFalse(decision.retry)
        assertEquals("tool is not retryable", decision.reason)
    }
}
