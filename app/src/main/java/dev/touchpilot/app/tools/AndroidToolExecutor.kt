package dev.touchpilot.app.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import dev.touchpilot.app.androidcontrol.AccessibilityBridge

class AndroidToolExecutor(
    private val context: Context
) {
    fun execute(name: String, args: Map<String, String>): ToolResult {
        val validationError = validate(name, args)
        if (validationError != null) {
            val argsForLog = if (validationError.startsWith("Unknown tool")) {
                "args=${args.keys.joinToString()}"
            } else {
                "invalid args"
            }
            record(name, argsForLog, false, validationError)
            return ToolResult(false, validationError)
        }

        val result = executeWithRetry(name, args)
        if (result.ok && name !in setOf("observe_screen", "wait_for_ui")) {
            AccessibilityBridge.waitForIdle(ActionIdleTimeoutMs)
        }
        return result
    }

    private fun executeWithRetry(name: String, args: Map<String, String>): ToolResult {
        var lastResult: ToolResult? = null

        repeat(retryCountFor(name)) { attempt ->
            val result = executeOnce(name, args)
            if (result.ok || !shouldRetry(name)) {
                return result.withAttemptData(attempt + 1)
            }

            lastResult = result
            Thread.sleep(RetryDelayMs)
        }

        val failed = lastResult ?: ToolResult(false, "Tool did not run")
        return failed.copy(
            message = "${failed.message} after ${retryCountFor(name)} attempt(s)",
            data = failed.data + ("attempts" to retryCountFor(name).toString())
        )
    }

    private fun executeOnce(name: String, args: Map<String, String>): ToolResult {
        return when (name) {
            "observe_screen" -> {
                val snapshot = observeScreen()
                record(name, "", AccessibilityBridge.isConnected(), "snapshot length=${snapshot.length}")
                ToolResult(AccessibilityBridge.isConnected(), snapshot)
            }
            "open_app" -> {
                val target = args["target"].orEmpty()
                val ok = openApp(target)
                record(name, "target=\"$target\"", ok, "openApp")
                ToolResult(ok, "openApp")
            }
            "tap" -> {
                val text = args["text"].orEmpty()
                val nodeId = args["node_id"].orEmpty()
                val bounds = args["bounds"].orEmpty()
                val ok = when {
                    nodeId.isNotBlank() -> AccessibilityBridge.tapByNodeId(nodeId)
                    bounds.isNotBlank() -> AccessibilityBridge.tapByBounds(bounds)
                    else -> AccessibilityBridge.tapByText(text)
                }
                val selector = when {
                    nodeId.isNotBlank() -> "node_id=\"$nodeId\""
                    bounds.isNotBlank() -> "bounds=\"$bounds\""
                    else -> "text=\"$text\""
                }
                record(name, selector, ok, "tap")
                ToolResult(ok, "tap", mapOf("selector" to selector))
            }
            "type_text" -> {
                val text = args["text"].orEmpty()
                val ok = AccessibilityBridge.typeIntoFocusedField(text)
                record(name, "text_length=${text.length}", ok, "typeIntoFocusedField")
                ToolResult(ok, "typeIntoFocusedField")
            }
            "scroll" -> {
                val direction = args["direction"].orEmpty()
                val ok = if (direction.equals("backward", ignoreCase = true)) {
                    AccessibilityBridge.scrollBackward()
                } else {
                    AccessibilityBridge.scrollForward()
                }
                record(name, "direction=\"$direction\"", ok, "scroll")
                ToolResult(ok, "scroll")
            }
            "press_back" -> {
                val ok = AccessibilityBridge.pressBack()
                record(name, "", ok, "pressBack")
                ToolResult(ok, "pressBack")
            }
            "press_home" -> {
                val ok = AccessibilityBridge.pressHome()
                record(name, "", ok, "pressHome")
                ToolResult(ok, "pressHome")
            }
            "wait_for_ui" -> {
                val text = args["text"].orEmpty()
                val timeout = args["timeout_ms"]?.toLongOrNull() ?: 5_000L
                val ok = AccessibilityBridge.waitForText(text, timeout)
                record(name, "text=\"$text\", timeout_ms=$timeout", ok, "waitForText")
                ToolResult(ok, "waitForText")
            }
            else -> {
                record(name, args.toString(), false, "unhandled tool")
                ToolResult(false, "Unhandled tool: $name")
            }
        }
    }

    fun validate(name: String, args: Map<String, String>): String? {
        val spec = AndroidToolCatalog.find(name) ?: return "Unknown tool: $name"
        return validateArgs(spec, args)
    }

    fun observeScreen(): String {
        return AccessibilityBridge.observeScreen()
    }

    fun openApp(target: String): Boolean {
        if (target.isBlank()) return false

        val exactLaunchIntent = context.packageManager.getLaunchIntentForPackage(target)
        if (exactLaunchIntent != null) {
            context.startActivity(exactLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val matches = context.packageManager.queryIntentActivities(launcherIntent, 0)
        val match = matches.firstOrNull { info ->
            info.launcherLabel().equals(target, ignoreCase = true)
        } ?: matches.firstOrNull { info ->
            info.launcherLabel().contains(target, ignoreCase = true)
        } ?: return false

        val intent = context.packageManager.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun ResolveInfo.launcherLabel(): String {
        return loadLabel(context.packageManager)?.toString().orEmpty()
    }

    private fun validateArgs(spec: ToolSpec, args: Map<String, String>): String? {
        val unknownArgs = args.keys - spec.arguments.keys
        if (unknownArgs.isNotEmpty()) {
            return "Unknown argument(s) for ${spec.name}: ${unknownArgs.joinToString()}"
        }

        val missingArgs = spec.requiredArguments.filter { args[it].isNullOrBlank() }
        if (missingArgs.isNotEmpty()) {
            return "Missing required argument(s) for ${spec.name}: ${missingArgs.joinToString()}"
        }

        if (spec.name == "tap") {
            val selectors = listOf("text", "node_id", "bounds")
                .filter { args[it].isNullOrBlank().not() }
            if (selectors.size != 1) {
                return "tap requires exactly one selector: text, node_id, or bounds"
            }
        }

        if (spec.name == "scroll") {
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

    private fun shouldRetry(name: String): Boolean {
        return name in setOf("open_app", "tap", "type_text", "scroll", "press_back", "press_home", "wait_for_ui")
    }

    private fun retryCountFor(name: String): Int {
        return if (shouldRetry(name)) ActionRetryCount else 1
    }

    private fun ToolResult.withAttemptData(attempts: Int): ToolResult {
        return if (attempts <= 1) {
            this
        } else {
            copy(data = data + ("attempts" to attempts.toString()))
        }
    }

    private fun record(name: String, args: String, ok: Boolean, message: String) {
        ToolExecutionLog.record(name, args, ok, message)
    }

    private companion object {
        const val ActionRetryCount = 3
        const val RetryDelayMs = 250L
        const val ActionIdleTimeoutMs = 1_500L
    }
}
