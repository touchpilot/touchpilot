package dev.touchpilot.app.navigation

enum class SettingsPanel(val label: String, val intro: String) {
    COMPATIBILITY(
        "Compatibility",
        "Validate device readiness for real-device onboarding, checklist, and known limitations before first run."
    ),
    PERMISSIONS(
        "Permissions",
        "Review Android tool, skill, and extension permissions separately. Revoke any grant and inspect the audit log."
    ),
    SKILLS(
        "Skills",
        "Skills bundle the tools and prompts TouchPilot uses for a kind of task."
    ),
    TOOLS(
        "Tools",
        "Inspect and use the built-in Android tools from one place."
    ),
    HELP(
        "Help",
        "Real-device troubleshooting, known limitations, and reporting guidance."
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
    ),
    RECORDING(
        "Recording",
        "Capture tool calls and screen context for each agent action as a demonstration."
    )
}
