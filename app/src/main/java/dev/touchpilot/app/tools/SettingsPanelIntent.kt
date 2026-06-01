package dev.touchpilot.app.tools

import android.provider.Settings

data class SettingsPanelIntentSpec(
    val panel: String,
    val action: String,
    val dataUri: String? = null,
    val extras: Map<String, String> = emptyMap()
)

object SettingsPanelIntent {
    const val PanelArg = "panel"
    const val AppPackagePlaceholder = "{app_package}"

    private val panels = mapOf(
        "wifi" to SettingsPanelIntentSpec(
            panel = "wifi",
            action = Settings.ACTION_WIFI_SETTINGS,
        ),
        "bluetooth" to SettingsPanelIntentSpec(
            panel = "bluetooth",
            action = Settings.ACTION_BLUETOOTH_SETTINGS,
        ),
        "accessibility" to SettingsPanelIntentSpec(
            panel = "accessibility",
            action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
        ),
        "app_info" to SettingsPanelIntentSpec(
            panel = "app_info",
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            dataUri = "package:$AppPackagePlaceholder",
        ),
        "notifications" to SettingsPanelIntentSpec(
            panel = "notifications",
            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            extras = mapOf(Settings.EXTRA_APP_PACKAGE to AppPackagePlaceholder),
        ),
        "system_settings" to SettingsPanelIntentSpec(
            panel = "system_settings",
            action = Settings.ACTION_SETTINGS,
        ),
    )

    val supportedPanels: Set<String> = panels.keys

    fun resolve(panel: String): SettingsPanelIntentSpec? {
        return panels[panel.trim().lowercase()]
    }

    fun panelFromTask(normalizedTask: String): String? {
        if ("settings" !in normalizedTask) return null
        return when {
            "wi-fi" in normalizedTask || "wifi" in normalizedTask -> "wifi"
            "bluetooth" in normalizedTask -> "bluetooth"
            "accessibility" in normalizedTask -> "accessibility"
            "app info" in normalizedTask || "app details" in normalizedTask -> "app_info"
            "notification" in normalizedTask -> "notifications"
            "system settings" in normalizedTask -> "system_settings"
            else -> null
        }
    }

    fun unsupportedMessage(panel: String): String {
        return "Unsupported settings panel: $panel. Supported panels: ${supportedPanels.joinToString()}"
    }
}
