package dev.touchpilot.app.tools

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitForAppTest {
    @Test
    fun waitForAppIsRegisteredAsLowRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("wait_for_app"))

        assertEquals(ToolRisk.LOW, spec.risk)
        assertEquals(
            setOf("package", "label", "timeout_ms"),
            spec.arguments.keys
        )
        assertTrue(spec.requiredArguments.isEmpty())
    }

    @Test
    fun validateRequiresPackageOrLabel() {
        assertEquals(
            "wait_for_app requires package or label",
            AndroidToolCatalog.validate("wait_for_app", emptyMap())
        )
        assertNull(AndroidToolCatalog.validate("wait_for_app", mapOf("package" to "com.android.settings")))
        assertNull(AndroidToolCatalog.validate("wait_for_app", mapOf("label" to "Settings")))
    }

    @Test
    fun validateRejectsBadTimeoutAndUnknownArgs() {
        assertEquals(
            "timeout_ms must be a number",
            AndroidToolCatalog.validate("wait_for_app", mapOf("label" to "Settings", "timeout_ms" to "soon"))
        )

        val unknown = AndroidToolCatalog.validate("wait_for_app", mapOf("target" to "Settings"))
        assertNotNull(unknown)
        assertTrue(unknown.startsWith("Unknown argument(s) for wait_for_app"))
    }

    @Test
    fun matchesPackageNameOrLabel() {
        val info = ForegroundAppInfo(
            packageName = "com.android.settings",
            appLabel = "Settings",
            accessibilityConnected = true,
        )

        val packageMatch = WaitForApp.matches(mapOf("package" to "COM.ANDROID.SETTINGS"), info)
        assertTrue(packageMatch.matched)
        assertEquals("package", packageMatch.matchedBy)

        val labelMatch = WaitForApp.matches(mapOf("label" to "sett"), info)
        assertTrue(labelMatch.matched)
        assertEquals("label", labelMatch.matchedBy)

        assertFalse(WaitForApp.matches(mapOf("label" to "Calculator"), info).matched)
    }

    @Test
    fun successResultIncludesForegroundAppData() {
        val result = WaitForApp.successResult(
            args = mapOf("package" to "com.android.settings"),
            info = ForegroundAppInfo(
                packageName = "com.android.settings",
                appLabel = "Settings",
                windowTitle = "Settings",
                activityClass = "com.android.settings.Settings",
                accessibilityConnected = true,
            ),
            matchedBy = "package"
        )

        assertTrue(result.ok)
        assertEquals("waitForApp", result.message)
        assertEquals("package", result.data["matched_by"])
        assertEquals("com.android.settings", result.data["package_name"])
        assertEquals("Settings", result.data["app_label"])
        assertEquals("true", result.data["accessibility_connected"])
    }

    @Test
    fun timeoutResultIncludesClearFailureAndLastForegroundApp() {
        val result = WaitForApp.timeoutResult(
            args = mapOf("label" to "Calculator"),
            info = ForegroundAppInfo(
                packageName = "com.android.settings",
                appLabel = "Settings",
                accessibilityConnected = true,
            ),
            timeoutMs = 1_000L
        )

        assertFalse(result.ok)
        assertEquals("Timed out waiting for foreground app: label=Calculator", result.message)
        assertEquals("true", result.data["timed_out"])
        assertEquals("1000", result.data["timeout_ms"])
        assertEquals("Calculator", result.data["expected_label"])
        assertEquals("Settings", result.data["app_label"])
    }

    @Test
    fun retryPolicyTreatsWaitForAppAsSingleBoundedWait() {
        val config = AndroidToolRetryPolicy().configFor("wait_for_app")

        assertEquals(1, config.maxAttempts)
        assertFalse(config.retryable)
        assertFalse(config.waitForIdleAfterSuccess)
    }
}
