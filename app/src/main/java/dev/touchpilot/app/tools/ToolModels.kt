package dev.touchpilot.app.tools

import dev.touchpilot.app.tools.targets.ClearTextTarget
import dev.touchpilot.app.tools.targets.ScrollTarget
import dev.touchpilot.app.tools.targets.SwipeDirection
import dev.touchpilot.app.tools.targets.SwipeTarget
import dev.touchpilot.app.tools.targets.TargetBounds
import dev.touchpilot.app.tools.targets.TypeTextTarget

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
            description = "Type text into the focused input field, or into a resolved visible input target.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf(
                TypeTextTarget.TextArg to "Text to enter.",
                TypeTextTarget.TargetTextArg to "Visible input label to focus before typing.",
                TypeTextTarget.TargetNodeIdArg to "Stable input node_id from observe_screen.",
                TypeTextTarget.TargetBoundsArg to "Input bounds from observe_screen as left,top,right,bottom.",
                TypeTextTarget.TargetViewIdArg to "Input viewIdResourceName from observe_screen.",
                TypeTextTarget.TargetContentDescriptionArg to "Input content description to focus before typing.",
            ),
            requiredArguments = setOf(TypeTextTarget.TextArg)
        ),
        ToolSpec(
            name = "scroll",
            description = "Scroll a specific container or the active screen forward or backward.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf(
                "direction" to "forward or backward.",
                ScrollTarget.TargetTextArg to "Visible text of the scrollable container to scroll.",
                ScrollTarget.TargetNodeIdArg to "Container node_id from observe_screen.",
                ScrollTarget.TargetBoundsArg to "Container bounds from observe_screen as left,top,right,bottom.",
                ScrollTarget.TargetViewIdArg to "Container viewIdResourceName from observe_screen.",
                ScrollTarget.TargetContentDescriptionArg to "Container content description.",
            ),
            requiredArguments = setOf("direction")
        ),
        ToolSpec(
            name = "swipe",
            description = "Swipe a gesture surface (pager, carousel, drawer, map) by direction, or between explicit start/end coordinates.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf(
                SwipeTarget.DirectionArg to "left, right, up, or down (direction the finger travels).",
                SwipeTarget.StartXArg to "Optional gesture start x in screen pixels.",
                SwipeTarget.StartYArg to "Optional gesture start y in screen pixels.",
                SwipeTarget.EndXArg to "Optional gesture end x in screen pixels.",
                SwipeTarget.EndYArg to "Optional gesture end y in screen pixels.",
                SwipeTarget.DurationArg to "Optional gesture duration in milliseconds.",
                SwipeTarget.TargetTextArg to "Visible text of the container to swipe within.",
                SwipeTarget.TargetNodeIdArg to "Container node_id from observe_screen.",
                SwipeTarget.TargetBoundsArg to "Container bounds from observe_screen as left,top,right,bottom.",
                SwipeTarget.TargetViewIdArg to "Container viewIdResourceName from observe_screen.",
                SwipeTarget.TargetContentDescriptionArg to "Container content description.",
            ),
            requiredArguments = emptySet()
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
        ),
        ToolSpec(
            name = "wait_for_idle",
            description = "Wait until the redacted screen context remains stable for a bounded window.",
            risk = ToolRisk.LOW,
            arguments = mapOf(
                WaitForIdle.StableArg to "Milliseconds the screen context must remain stable.",
                WaitForIdle.TimeoutArg to "Maximum wait time in milliseconds.",
                WaitForIdle.IncludeBoundsArg to "true if bounds changes should count as instability."
            ),
            requiredArguments = emptySet()
        ),
        ToolSpec(
            name = "wait_for_app",
            description = "Wait until a package name or launcher label becomes the foreground app.",
            risk = ToolRisk.LOW,
            arguments = mapOf(
                WaitForApp.PackageArg to "Expected Android package name.",
                WaitForApp.LabelArg to "Expected launcher/app label.",
                WaitForApp.TimeoutArg to "Maximum wait time in milliseconds."
            ),
            requiredArguments = emptySet()
        ),
        ToolSpec(
            name = "focus_input",
            description = "Focus a visible editable input field without typing text.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf(
                "text" to "Visible text or content description of the input field.",
                "node_id" to "Stable node_id from observe_screen.",
                "bounds" to "Bounds from observe_screen as left,top,right,bottom.",
                "view_id" to "Resource ID of the view (e.g., dev.example.app:id/search_input)."
            ),
            requiredArguments = emptySet()
        ),
        ToolSpec(
            name = "get_foreground_app",
            description = "Return the foreground app's package, label, window title, and activity for post-action verification.",
            risk = ToolRisk.LOW,
            arguments = emptyMap()
        ),
        ToolSpec(
            name = "find_element",
            description = "Search the current screen for elements matching a structured query and return ranked candidates.",
            risk = ToolRisk.LOW,
            arguments = mapOf(
                "text" to "Visible text query.",
                "content_description" to "Accessibility content description query.",
                "node_id" to "Stable node_id from observe_screen.",
                "class_name" to "Android class name filter (e.g. android.widget.Button).",
                "match" to "Match mode: exact, contains, or semantic. Defaults to contains.",
                "limit" to "Maximum number of candidates to return (1-25). Defaults to 5."
            ),
            requiredArguments = emptySet()
        ),
        ToolSpec(
            name = "clear_text",
            description = "Clear the focused or resolved editable input field.",
            risk = ToolRisk.MEDIUM,
            arguments = mapOf(
                ClearTextTarget.TargetTextArg to "Visible input label to focus and clear.",
                ClearTextTarget.TargetNodeIdArg to "Stable input node_id from observe_screen.",
                ClearTextTarget.TargetBoundsArg to "Input bounds from observe_screen as left,top,right,bottom.",
                ClearTextTarget.TargetViewIdArg to "Input viewIdResourceName from observe_screen.",
                ClearTextTarget.TargetContentDescriptionArg to "Input content description to focus and clear.",
            ),
            requiredArguments = emptySet()
        ),
        ToolSpec(
            name = "dismiss_keyboard",
            description = "Hide the soft keyboard when it is visible. No-op when it is already hidden.",
            risk = ToolRisk.LOW,
            arguments = mapOf(
                "timeout_ms" to "Maximum wait for the keyboard to disappear after dismissal."
            ),
            requiredArguments = emptySet()
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

        if (name == "type_text") {
            val malformedBounds = args[TypeTextTarget.TargetBoundsArg]
                ?.takeIf { it.isNotBlank() }
                ?.let { dev.touchpilot.app.tools.targets.TargetBounds.parse(it) == null }
                ?: false
            if (malformedBounds) {
                return "target_bounds must be left,top,right,bottom"
            }
        }

        if (name == "focus_input") {
            val selectors = listOf("text", "node_id", "bounds", "view_id")
                .filter { args[it].isNullOrBlank().not() }
            if (selectors.size != 1) {
                return "focus_input requires exactly one selector: text, node_id, bounds, or view_id"
            }
        }

        if (name == "wait_for_idle") {
            return WaitForIdle.validate(args)
        }

        if (name == "clear_text") {
            val selectors = ClearTextTarget.selectorArgs.filter { args[it].isNullOrBlank().not() }
            if (selectors.size > 1) {
                return "clear_text requires at most one selector: target_text, target_node_id, target_bounds, target_view_id, or target_content_description"
            }
            val malformedBounds = args[ClearTextTarget.TargetBoundsArg]
                ?.takeIf { it.isNotBlank() }
                ?.let { dev.touchpilot.app.tools.targets.TargetBounds.parse(it) == null }
                ?: false
            if (malformedBounds) {
                return "target_bounds must be left,top,right,bottom"
            }
        }

        if (name == "wait_for_app") {
            val selectors = listOf(WaitForApp.PackageArg, WaitForApp.LabelArg)
                .filter { args[it].isNullOrBlank().not() }
            if (selectors.isEmpty()) {
                return "wait_for_app requires package or label"
            }
        }

        if (name == "scroll") {
            val direction = args["direction"].orEmpty()
            if (!direction.equals("forward", ignoreCase = true) &&
                !direction.equals("backward", ignoreCase = true)
            ) {
                return "Invalid scroll direction: $direction"
            }
            val malformedBounds = args[ScrollTarget.TargetBoundsArg]
                ?.takeIf { it.isNotBlank() }
                ?.let { dev.touchpilot.app.tools.targets.TargetBounds.parse(it) == null }
                ?: false
            if (malformedBounds) {
                return "target_bounds must be left,top,right,bottom"
            }
        }

        if (name == "swipe") {
            val direction = args[SwipeTarget.DirectionArg]?.takeIf { it.isNotBlank() }
            val hasCoordinate = SwipeTarget.hasAnyCoordinate(args)
            if (direction == null && !hasCoordinate) {
                return "swipe requires a direction (left, right, up, down) or explicit start/end coordinates"
            }
            if (direction != null && SwipeDirection.parse(direction) == null) {
                return "Invalid swipe direction: $direction. Use left, right, up, or down."
            }
            SwipeTarget.validateCoordinates(args)?.let { return it }
            SwipeTarget.validateDuration(args)?.let { return it }
            val malformedBounds = args[SwipeTarget.TargetBoundsArg]
                ?.takeIf { it.isNotBlank() }
                ?.let { TargetBounds.parse(it) == null }
                ?: false
            if (malformedBounds) {
                return "target_bounds must be left,top,right,bottom"
        if (name == "find_element") {
            val filters = listOf("text", "content_description", "node_id", "class_name")
                .filter { args[it].isNullOrBlank().not() }
            if (filters.isEmpty()) {
                return "find_element requires at least one filter: text, content_description, node_id, or class_name"
            }
            val match = args["match"]
            if (!match.isNullOrBlank() && MatchMode.fromWire(match) == null) {
                return "find_element match must be one of: exact, contains, semantic"
            }
            val limit = args["limit"]
            if (!limit.isNullOrBlank()) {
                val parsed = limit.toIntOrNull()
                if (parsed == null || parsed < 1 || parsed > FindElementQuery.MaxLimit) {
                    return "find_element limit must be an integer between 1 and ${FindElementQuery.MaxLimit}"
                }
            }
        }

        val timeout = args["timeout_ms"]
        if (timeout != null && timeout.toLongOrNull() == null) {
            return "timeout_ms must be a number"
        }

        return null
    }
}
