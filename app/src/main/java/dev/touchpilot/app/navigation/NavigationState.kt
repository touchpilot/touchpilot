package dev.touchpilot.app.navigation

import androidx.annotation.DrawableRes
import dev.touchpilot.app.R

enum class Section(val label: String, @DrawableRes val iconRes: Int) {
    CHAT("Chat", R.drawable.ic_chat),
    TOOLS("Tools", R.drawable.ic_tools),
    LOGS("Logs", R.drawable.ic_logs),
    SETTINGS("Settings", R.drawable.ic_settings)
}

enum class SettingsPanel(val label: String, val intro: String) {
    SKILLS(
        "Skills",
        "Skills bundle the tools and prompts TouchPilot uses for a kind of task."
    ),
    MCP(
        "MCP",
        "Connect TouchPilot to an external MCP HTTP JSON-RPC server to call its tools."
    ),
    CLOUD(
        "Cloud API",
        "Optional cloud agent endpoint. Used only when explicitly selected as the runtime."
    ),
    RUNTIME(
        "Runtime",
        "Choose how TouchPilot reasons about your requests on this device."
    )
}

data class NavigationState(
    val activeSection: Section = Section.CHAT,
    val activeSettingsPanel: SettingsPanel? = null,
    val activeRunDetailId: String? = null,
    val pendingSettingsAnimationDirection: Int = 0,
)
