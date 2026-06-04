package dev.touchpilot.app.ui.settings

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
