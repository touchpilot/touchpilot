package dev.touchpilot.app.androidcontrol

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForegroundAppInfoTest {
    @Test
    fun disconnectedSummaryFlagsTheServiceState() {
        val info = ForegroundAppInfo.Disconnected
        assertFalse(info.accessibilityConnected)
        assertFalse(info.hasContent)
        assertEquals("TouchPilot Accessibility service is not connected.", info.summarize())
    }

    @Test
    fun connectedButEmptyInfoReportsUnknownForeground() {
        val info = ForegroundAppInfo(accessibilityConnected = true)
        assertTrue(info.accessibilityConnected)
        assertFalse(info.hasContent)
        assertEquals("Foreground app is unknown.", info.summarize())
    }

    @Test
    fun summarizeUsesAppLabelWhenAvailable() {
        val info = ForegroundAppInfo(
            packageName = "com.android.settings",
            appLabel = "Settings",
            windowTitle = "Settings",
            accessibilityConnected = true,
        )
        assertEquals("Foreground: Settings", info.summarize())
    }

    @Test
    fun summarizeAppendsWindowTitleWhenItDiffersFromAppLabel() {
        val info = ForegroundAppInfo(
            packageName = "com.android.settings",
            appLabel = "Settings",
            windowTitle = "Network & internet",
            accessibilityConnected = true,
        )
        assertEquals("Foreground: Settings (Network & internet)", info.summarize())
    }

    @Test
    fun summarizeFallsBackToPackageNameWithoutLabel() {
        val info = ForegroundAppInfo(
            packageName = "dev.touchpilot.app",
            accessibilityConnected = true,
        )
        assertEquals("Foreground: dev.touchpilot.app", info.summarize())
    }

    @Test
    fun jsonRendersNullForBlankFields() {
        val info = ForegroundAppInfo(
            packageName = "com.android.settings",
            appLabel = "Settings",
            accessibilityConnected = true,
        )
        val json: JSONObject = info.toJson()
        assertEquals("com.android.settings", json.getString("packageName"))
        assertEquals("Settings", json.getString("appLabel"))
        assertTrue(json.isNull("windowTitle"))
        assertTrue(json.isNull("activityClass"))
        assertTrue(json.getBoolean("accessibilityConnected"))
    }

    @Test
    fun jsonRendersDisconnectedBitWhenNotConnected() {
        val json = ForegroundAppInfo.Disconnected.toJson()
        assertFalse(json.getBoolean("accessibilityConnected"))
        assertTrue(json.isNull("packageName"))
    }

    @Test
    fun hasContentDistinguishesEachIdentifyingField() {
        assertTrue(ForegroundAppInfo(packageName = "pkg").hasContent)
        assertTrue(ForegroundAppInfo(appLabel = "Label").hasContent)
        assertTrue(ForegroundAppInfo(windowTitle = "Title").hasContent)
        assertTrue(ForegroundAppInfo(activityClass = "Class").hasContent)
        assertFalse(ForegroundAppInfo(packageName = " ").hasContent)
    }
}
