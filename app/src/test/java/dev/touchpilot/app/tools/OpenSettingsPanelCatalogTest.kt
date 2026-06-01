package dev.touchpilot.app.tools

import android.provider.Settings
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenSettingsPanelCatalogTest {
    @Test
    fun openSettingsPanelIsRegisteredAsMediumRisk() {
        val spec = assertNotNull(AndroidToolCatalog.find("open_settings_panel"))
        assertEquals(ToolRisk.MEDIUM, spec.risk)
        assertEquals(setOf("panel"), spec.arguments.keys)
        assertEquals(setOf("panel"), spec.requiredArguments)
    }

    @Test
    fun supportsInitialSettingsPanelAllowlist() {
        assertEquals(
            setOf("wifi", "bluetooth", "accessibility", "app_info", "notifications", "system_settings"),
            SettingsPanelIntent.supportedPanels
        )
    }

    @Test
    fun mapsPanelsToAndroidSettingsIntents() {
        assertEquals(Settings.ACTION_WIFI_SETTINGS, SettingsPanelIntent.resolve("wifi")?.action)
        assertEquals(Settings.ACTION_BLUETOOTH_SETTINGS, SettingsPanelIntent.resolve("bluetooth")?.action)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, SettingsPanelIntent.resolve("accessibility")?.action)
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, SettingsPanelIntent.resolve("app_info")?.action)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, SettingsPanelIntent.resolve("notifications")?.action)
        assertEquals(Settings.ACTION_SETTINGS, SettingsPanelIntent.resolve("system_settings")?.action)
    }

    @Test
    fun appSpecificPanelsUseTouchPilotPackagePlaceholder() {
        val appInfo = assertNotNull(SettingsPanelIntent.resolve("app_info"))
        assertEquals("package:${SettingsPanelIntent.AppPackagePlaceholder}", appInfo.dataUri)

        val notifications = assertNotNull(SettingsPanelIntent.resolve("notifications"))
        assertEquals(
            SettingsPanelIntent.AppPackagePlaceholder,
            notifications.extras[Settings.EXTRA_APP_PACKAGE]
        )
    }

    @Test
    fun validateAcceptsSupportedPanelsCaseInsensitively() {
        assertNull(AndroidToolCatalog.validate("open_settings_panel", mapOf("panel" to "WiFi")))
        assertNull(AndroidToolCatalog.validate("open_settings_panel", mapOf("panel" to "bluetooth")))
    }

    @Test
    fun validateRejectsUnsupportedPanelsClearly() {
        val error = AndroidToolCatalog.validate("open_settings_panel", mapOf("panel" to "developer_options"))
        assertNotNull(error)
        assertContains(error, "Unsupported settings panel: developer_options")
        assertContains(error, "wifi")
        assertContains(error, "system_settings")
    }

    @Test
    fun retryPolicyTreatsOpenSettingsPanelLikeNavigation() {
        val config = AndroidToolRetryPolicy().configFor("open_settings_panel")
        assertEquals(3, config.maxAttempts)
        assertTrue(config.retryable)
        assertTrue(config.waitForIdleAfterSuccess)
    }
}
