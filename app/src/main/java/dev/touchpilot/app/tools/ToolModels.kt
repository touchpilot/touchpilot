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
            name = "observe_screen_context",
            description = "Serialize the current screen as a normalized ScreenContext " +
                "(app metadata, node roles, bounds, action flags) with sensitive text " +
                "redacted. Prefer this over observe_screen for agent decisions.",
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
            description = "Tap a visible UI target by semantic text, node_id, or bounds.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf(
                "text" to "Visible text or content description to tap.",
                "node_id" to "Stable node_id from observe_screen.",
                "bounds" to "Bounds from observe_screen as left,top,right,bottom."
            ),
            requiredArguments = emptySet()
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

    fun validate(name: String, args: Map<String, String>): String? {
        val spec = find(name) ?: return "Unknown tool: $name"
        return spec.validateArgs(args)
    }

    private fun ToolSpec.validateArgs(args: Map<String, String>): String? {
        val unknownArgs = args.keys - arguments.keys
        if (unknownArgs.isNotEmpty()) {
            return "Unknown argument(s) for $name: ${unknownArgs.joinToString()}"
        }

        val missingArgs = requiredArguments.filter { args[it].isNullOrBlank() }
        if (missingArgs.isNotEmpty()) {
            return "Missing required argument(s) for $name: ${missingArgs.joinToString()}"
        }

        if (name == "tap") {
            val selectors = listOf("text", "node_id", "bounds")
                .filter { args[it].isNullOrBlank().not() }
            if (selectors.size != 1) {
                return "tap requires exactly one selector: text, node_id, or bounds"
            }
        }

        if (name == "scroll") {
            val direction = args["direction"].orEmpty()
            if (!direction.equals("forward", ignoreCase = true) &&
                !direction.equals("backward", ignoreCase = true)
            ) {
                return "Invalid scroll direction: $direction"
            }
        }

        val timeout = args["timeout_ms"]
        if (timeout != null && timeout.toLongOrNull() == null) {
            return "timeout_ms must be a number"
        }

        return null
    }
}
