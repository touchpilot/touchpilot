package dev.touchpilot.app.tools

enum class ToolRisk {
    LOW,
    MEDIUM,
    HIGH,
    BLOCKED
}

data class ToolSpec(
    val name: String,
    val description: String,
    val risk: ToolRisk,
    val arguments: Map<String, String>,
    val requiredArguments: Set<String> = arguments.keys
)

data class ToolResult(
    val ok: Boolean,
    val message: String,
    val data: Map<String, String> = emptyMap()
)

object AndroidToolCatalog {
    val initialTools = listOf(
        ToolSpec(
            name = "observe_screen",
            description = "Serialize the current Android accessibility tree.",
            risk = ToolRisk.LOW,
            arguments = emptyMap()
        ),
        ToolSpec(
            name = "open_app",
            description = "Launch an installed app by package name or visible label.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf("target" to "Package name or launcher label.")
        ),
        ToolSpec(
            name = "tap",
            description = "Tap a visible UI target by semantic text.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf("text" to "Visible text or content description to tap.")
        ),
        ToolSpec(
            name = "type_text",
            description = "Type text into the currently focused input field.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf("text" to "Text to enter.")
        ),
        ToolSpec(
            name = "scroll",
            description = "Scroll the active screen forward or backward.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf("direction" to "forward or backward.")
        ),
        ToolSpec(
            name = "press_back",
            description = "Send Android back.",
            risk = ToolRisk.MEDIUM,
            arguments = emptyMap()
        ),
        ToolSpec(
            name = "press_home",
            description = "Return to the Android launcher.",
            risk = ToolRisk.MEDIUM,
            arguments = emptyMap()
        ),
        ToolSpec(
            name = "wait_for_ui",
            description = "Wait until text appears in the current accessibility tree.",
            risk = ToolRisk.LOW,
            arguments = mapOf(
                "text" to "Expected visible text.",
                "timeout_ms" to "Maximum wait time in milliseconds."
            ),
            requiredArguments = setOf("text")
        )
    )

    fun find(name: String): ToolSpec? {
        return initialTools.firstOrNull { it.name == name }
    }
}
