package dev.touchpilot.app.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.targets.TargetResolutionResult
import dev.touchpilot.app.tools.targets.TargetResolver
import dev.touchpilot.app.tools.targets.TargetSelector
import dev.touchpilot.app.tools.targets.TypeTextTarget

class AndroidToolExecutor(
    private val context: Context,
    private val policy: DefaultActionPolicy = DefaultActionPolicy(),
    private val targetResolver: TargetResolver = TargetResolver()
) {
    fun execute(
        name: String,
        args: Map<String, String>,
        source: ToolSource = ToolSource.DIRECT_DEBUG
    ): ToolResult {
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

        val spec = AndroidToolCatalog.find(name)
        if (spec != null) {
            when (val decision = policy.evaluate(ToolPolicyRequest(spec, args, source, observeScreen()))) {
                is PolicyDecision.Block -> {
                    record(name, "policy=block", false, decision.userMessage)
                    return ToolResult(false, decision.userMessage)
                }
                is PolicyDecision.Deny -> {
                    record(name, "policy=deny", false, decision.userMessage)
                    return ToolResult(false, decision.userMessage)
                }
                is PolicyDecision.Allow,
                is PolicyDecision.RequireApproval -> Unit
            }
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
                ToolResult(AccessibilityBridge.isConnected(), SensitiveTextRedactor.redact(snapshot))
            }
            "open_app" -> {
                val target = args["target"].orEmpty()
                val ok = openApp(target)
                record(name, "target=\"$target\"", ok, "openApp")
                ToolResult(ok, "openApp")
            }
            "open_settings_panel" -> {
                val panel = args["panel"].orEmpty()
                val ok = openSettingsPanel(panel)
                val message = if (ok) "openSettingsPanel" else "No settings activity for panel \"$panel\""
                record(name, "panel=\"$panel\"", ok, message)
                ToolResult(ok, message, mapOf("panel" to panel))
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
                executeTypeText(args)
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

    private fun executeTypeText(args: Map<String, String>): ToolResult {
        val text = args[TypeTextTarget.TextArg].orEmpty()
        if (!TypeTextTarget.hasTarget(args)) {
            val ok = AccessibilityBridge.typeIntoFocusedField(text)
            record("type_text", "text_length=${text.length}, target=focused", ok, "typeIntoFocusedField")
            return ToolResult(ok, if (ok) "typeIntoFocusedField" else "No editable focused input is available")
        }

        val selector = TypeTextTarget.selectorFromArgs(args)
        val resolution = targetResolver.resolve(
            context = AccessibilityBridge.observeScreenContext(),
            selector = selector
        )

        return when (resolution) {
            is TargetResolutionResult.Resolved -> {
                val node = resolution.candidate.node
                val nodeId = node.nodeId
                if (nodeId.isNullOrBlank() || !node.isInputField) {
                    val message = "Resolved target is not an editable input"
                    record("type_text", typeTextLogArgs(text, resolution.candidate.selector), false, message)
                    ToolResult(
                        ok = false,
                        message = message,
                        data = mapOf(
                            "target" to resolution.candidate.selector.toRedactedJson(),
                            "confidence" to resolution.candidate.confidence.toString(),
                        )
                    )
                } else {
                    val ok = AccessibilityBridge.typeIntoNode(nodeId, text)
                    val message = if (ok) "typeIntoResolvedInput" else "Unable to focus or type into resolved input"
                    record("type_text", typeTextLogArgs(text, resolution.candidate.selector), ok, message)
                    ToolResult(
                        ok = ok,
                        message = message,
                        data = mapOf(
                            "node_id" to nodeId,
                            "confidence" to resolution.candidate.confidence.toString(),
                        )
                    )
                }
            }
            is TargetResolutionResult.Ambiguous -> {
                val message = "Ambiguous input target: ${resolution.reason}"
                record("type_text", "text_length=${text.length}, target=ambiguous", false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = mapOf("candidate_count" to resolution.candidates.size.toString())
                )
            }
            is TargetResolutionResult.NotFound -> {
                val message = "Input target not found: ${resolution.reason}"
                record("type_text", "text_length=${text.length}, target=not_found", false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = mapOf("debug_context" to resolution.debugContext)
                )
            }
        }
    }

    private fun typeTextLogArgs(text: String, selector: TargetSelector): String {
        val label = selector.text?.displaySafe
            ?: selector.contentDescription?.displaySafe
            ?: selector.nodeId?.let { "node_id=$it" }
            ?: selector.viewIdResourceName?.let { "view_id=$it" }
            ?: selector.bounds?.let { "bounds=${it.toBoundsArg()}" }
            ?: "resolved"
        return "text_length=${text.length}, target=\"$label\""
    }

    fun validate(name: String, args: Map<String, String>): String? {
        return AndroidToolCatalog.validate(name, args)
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

    /**
     * Open a Settings panel from the [SupportedSettingsPanels] allowlist using a native
     * Settings intent. Returns false for unsupported panels or when no Settings activity
     * can handle the intent; this tool never toggles a setting, it only navigates there.
     */
    fun openSettingsPanel(panel: String): Boolean {
        val intent = settingsIntentFor(panel) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun settingsIntentFor(panel: String): Intent? {
        return when (panel) {
            "wifi" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "app_info" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            "system_settings" -> Intent(Settings.ACTION_SETTINGS)
            else -> null
        }
    }

    private fun ResolveInfo.launcherLabel(): String {
        return loadLabel(context.packageManager)?.toString().orEmpty()
    }

    private fun shouldRetry(name: String): Boolean {
        return name in setOf(
            "open_app",
            "open_settings_panel",
            "tap",
            "type_text",
            "scroll",
            "press_back",
            "press_home",
            "wait_for_ui"
        )
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
