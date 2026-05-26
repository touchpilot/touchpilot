package dev.touchpilot.app.tools

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.tools.targets.ScrollResolution
import dev.touchpilot.app.tools.targets.ScrollResolver
import dev.touchpilot.app.tools.targets.ScrollTarget
import dev.touchpilot.app.tools.targets.SwipeDirection
import dev.touchpilot.app.tools.targets.SwipeGesture
import dev.touchpilot.app.tools.targets.SwipeRequest
import dev.touchpilot.app.tools.targets.SwipeTarget
import dev.touchpilot.app.tools.targets.TargetBounds
import dev.touchpilot.app.tools.targets.TargetResolutionResult
import dev.touchpilot.app.tools.targets.TargetResolver
import dev.touchpilot.app.tools.targets.TargetSelector
import dev.touchpilot.app.tools.targets.TypeTextTarget

class AndroidToolExecutor(
    private val context: Context,
    private val policy: DefaultActionPolicy = DefaultActionPolicy(),
    private val targetResolver: TargetResolver = TargetResolver(),
    private val scrollResolver: ScrollResolver = ScrollResolver(targetResolver),
    private val retryPolicy: AndroidToolRetryPolicy = AndroidToolRetryPolicy(),
    private val verifier: ToolVerifier = ToolVerifier(),
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
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
        val retryConfig = retryPolicy.configFor(name)
        if (result.ok && retryConfig.waitForIdleAfterSuccess && retryConfig.idleTimeoutMs > 0L) {
            AccessibilityBridge.waitForIdle(retryConfig.idleTimeoutMs)
        }
        return result
    }

    private fun executeWithRetry(name: String, args: Map<String, String>): ToolResult {
        var lastResult: ToolResult? = null
        val config = retryPolicy.configFor(name)

        repeat(config.maxAttempts) { attemptIndex ->
            val result = executeOnceVerified(name, args)
            val attempt = attemptIndex + 1
            val decision = retryPolicy.shouldRetry(name, result, attemptIndex)
            if (result.ok || !decision.retry) {
                return result.withAttemptData(
                    attempt = attempt,
                    category = decision.category,
                    retryReason = decision.reason,
                )
            }

            lastResult = result.withAttemptData(
                attempt = attempt,
                category = decision.category,
                retryReason = decision.reason,
            )
            record(name, "attempt=$attempt, retry_reason=\"${decision.reason}\"", false, "retrying")
            sleeper(decision.delayMs)
        }

        val failed = lastResult ?: ToolResult(false, "Tool did not run")
        return failed.copy(
            message = "${failed.message} after ${config.maxAttempts} attempt(s)",
            data = failed.data + ("attempts" to config.maxAttempts.toString())
        )
    }

    private fun executeOnceVerified(name: String, args: Map<String, String>): ToolResult {
        val before = AccessibilityBridge.observeScreenContext()
        val result = executeOnce(name, args)
        val after = if (name == "observe_screen") {
            before
        } else {
            AccessibilityBridge.observeScreenContext()
        }
        val verification = verifier.verify(
            toolName = name,
            args = args,
            result = result,
            before = before,
            after = after,
        )
        val verified = result.withVerification(verification)
        return if (result.ok && verification is ToolVerificationResult.Failed) {
            verified.copy(ok = false, message = "Verification failed: ${verification.reason}")
        } else {
            verified
        }
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
                executeScroll(args)
            }
            "swipe" -> {
                executeSwipe(args)
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

    private fun executeScroll(args: Map<String, String>): ToolResult {
        val direction = args[ScrollTarget.DirectionArg].orEmpty()
        val backward = ScrollTarget.isBackward(direction)
        val forward = !backward
        val hasTarget = ScrollTarget.hasTarget(args)
        val selector = if (hasTarget) ScrollTarget.selectorFromArgs(args) else null

        val beforeContext = AccessibilityBridge.observeScreenContext()
        val resolution = scrollResolver.resolve(beforeContext, selector)

        return when (resolution) {
            is ScrollResolution.Resolved -> {
                val node = resolution.node
                val nodeId = node.nodeId
                if (nodeId.isNullOrBlank()) {
                    val message = "Resolved scroll target has no stable node id"
                    record("scroll", scrollLogArgs(direction, "resolved_no_id"), false, message)
                    return ToolResult(
                        ok = false,
                        message = message,
                        data = mapOf("target" to resolution.selector.toRedactedJson())
                    )
                }
                val attempted = AccessibilityBridge.scrollNode(nodeId, forward)
                val verification = verifyScreenChanged(beforeContext, attempted)
                val message = when {
                    !attempted -> "Unable to perform scroll on resolved container"
                    !verification.changed -> "Scroll performed but screen did not change"
                    else -> "scroll"
                }
                val ok = attempted && verification.changed
                record(
                    "scroll",
                    scrollLogArgs(direction, "node_id=$nodeId"),
                    ok,
                    message
                )
                ToolResult(
                    ok = ok,
                    message = message,
                    data = mapOf(
                        "node_id" to nodeId,
                        "direction" to if (forward) "forward" else "backward",
                        "confidence" to resolution.confidence.toString(),
                        "screen_changed" to verification.changed.toString(),
                    )
                )
            }
            is ScrollResolution.Ambiguous -> {
                val message = "Ambiguous scroll target: ${resolution.reason}"
                record("scroll", scrollLogArgs(direction, "ambiguous"), false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = mapOf("candidate_count" to resolution.candidates.size.toString())
                )
            }
            is ScrollResolution.NotFound -> {
                val message = "Scroll target not found: ${resolution.reason}"
                record("scroll", scrollLogArgs(direction, "not_found"), false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = mapOf("debug_context" to resolution.debugContext)
                )
            }
            is ScrollResolution.NoScrollable -> {
                record("scroll", scrollLogArgs(direction, "no_scrollable"), false, resolution.reason)
                ToolResult(
                    ok = false,
                    message = resolution.reason,
                    data = mapOf("debug_context" to resolution.debugContext)
                )
            }
            is ScrollResolution.DirectionOnly -> {
                val attempted = if (backward) {
                    AccessibilityBridge.scrollBackward()
                } else {
                    AccessibilityBridge.scrollForward()
                }
                val verification = verifyScreenChanged(beforeContext, attempted)
                val message = when {
                    !attempted -> "Unable to perform direction-only scroll"
                    !verification.changed -> "Scroll performed but screen did not change"
                    else -> "scroll"
                }
                val ok = attempted && verification.changed
                record(
                    "scroll",
                    scrollLogArgs(direction, "direction_only=${resolution.scrollableCount}"),
                    ok,
                    message
                )
                ToolResult(
                    ok = ok,
                    message = message,
                    data = mapOf(
                        "direction" to if (forward) "forward" else "backward",
                        "scrollable_count" to resolution.scrollableCount.toString(),
                        "screen_changed" to verification.changed.toString(),
                    )
                )
            }
        }
    }

    private fun executeSwipe(args: Map<String, String>): ToolResult {
        val before = AccessibilityBridge.observeScreenContext()

        val explicit = SwipeTarget.explicitCoordinates(args)
        if (explicit != null) {
            val window = AccessibilityBridge.activeWindowBounds()?.toTargetBounds()
            if (window != null && !window.isEmpty && !explicit.within(window)) {
                val message = "Swipe coordinates (${explicit.startX},${explicit.startY})->" +
                    "(${explicit.endX},${explicit.endY}) are outside the screen bounds ${window.toBoundsArg()}"
                record("swipe", swipeLogArgs("coordinates", explicit), false, message)
                return ToolResult(false, message, mapOf("screen_bounds" to window.toBoundsArg()))
            }
            return dispatchSwipe(explicit, before, "coordinates")
        }

        val direction = SwipeDirection.parse(args[SwipeTarget.DirectionArg])
            ?: return ToolResult(false, "Invalid swipe direction")
        val duration = SwipeTarget.durationOrDefault(args)

        val area = if (SwipeTarget.hasTarget(args)) {
            when (val resolved = resolveSwipeContainer(args, before)) {
                is SwipeArea.Resolved -> resolved.bounds
                is SwipeArea.Failure -> return resolved.result
            }
        } else {
            val window = AccessibilityBridge.activeWindowBounds()?.toTargetBounds()
            if (window == null || window.isEmpty) {
                val message = "No active window is available to swipe."
                record("swipe", swipeLogArgs(direction.label(), null), false, message)
                return ToolResult(false, message)
            }
            window
        }

        val request = SwipeGesture.forDirection(direction, area, duration)
        return dispatchSwipe(request, before, direction.label())
    }

    private fun resolveSwipeContainer(
        args: Map<String, String>,
        context: dev.touchpilot.app.screen.ScreenContext,
    ): SwipeArea {
        val selector = SwipeTarget.selectorFromArgs(args)
        return when (val resolution = targetResolver.resolve(context, selector)) {
            is TargetResolutionResult.Resolved -> {
                val bounds = resolution.candidate.node.bounds.toTargetBounds()
                if (bounds.isEmpty) {
                    val message = "Resolved swipe container has no usable bounds"
                    record("swipe", "target=resolved_no_bounds", false, message)
                    SwipeArea.Failure(
                        ToolResult(
                            ok = false,
                            message = message,
                            data = mapOf("target" to resolution.candidate.selector.toRedactedJson())
                        )
                    )
                } else {
                    SwipeArea.Resolved(bounds)
                }
            }
            is TargetResolutionResult.Ambiguous -> {
                val message = "Ambiguous swipe target: ${resolution.reason}"
                record("swipe", "target=ambiguous", false, message)
                SwipeArea.Failure(
                    ToolResult(
                        ok = false,
                        message = message,
                        data = mapOf("candidate_count" to resolution.candidates.size.toString())
                    )
                )
            }
            is TargetResolutionResult.NotFound -> {
                val message = "Swipe target not found: ${resolution.reason}"
                record("swipe", "target=not_found", false, message)
                SwipeArea.Failure(
                    ToolResult(
                        ok = false,
                        message = message,
                        data = mapOf("debug_context" to resolution.debugContext)
                    )
                )
            }
        }
    }

    private fun dispatchSwipe(
        request: SwipeRequest,
        beforeContext: dev.touchpilot.app.screen.ScreenContext,
        label: String,
    ): ToolResult {
        val attempted = AccessibilityBridge.swipe(
            request.startX,
            request.startY,
            request.endX,
            request.endY,
            request.durationMs,
        )
        val verification = verifyScreenChanged(
            beforeContext = beforeContext,
            scrollAttempted = attempted,
            idleTimeoutMs = retryPolicy.configFor("swipe").idleTimeoutMs,
        )
        val message = when {
            !attempted -> "Unable to perform swipe"
            !verification.changed -> "Swipe performed but screen did not change"
            else -> "swipe"
        }
        val ok = attempted && verification.changed
        record("swipe", swipeLogArgs(label, request), ok, message)
        return ToolResult(
            ok = ok,
            message = message,
            data = mapOf(
                "gesture" to label,
                "start" to "${request.startX},${request.startY}",
                "end" to "${request.endX},${request.endY}",
                "duration_ms" to request.durationMs.toString(),
                "screen_changed" to verification.changed.toString(),
            )
        )
    }

    private fun swipeLogArgs(label: String, request: SwipeRequest?): String {
        if (request == null) return "gesture=\"$label\""
        return "gesture=\"$label\", start=${request.startX},${request.startY}, " +
            "end=${request.endX},${request.endY}, duration_ms=${request.durationMs}"
    }

    private fun SwipeDirection.label(): String = name.lowercase()

    private fun NodeBounds.toTargetBounds(): TargetBounds {
        return TargetBounds(left = left, top = top, right = right, bottom = bottom)
    }

    private sealed class SwipeArea {
        data class Resolved(val bounds: TargetBounds) : SwipeArea()
        data class Failure(val result: ToolResult) : SwipeArea()
    }

    private fun verifyScreenChanged(
        beforeContext: dev.touchpilot.app.screen.ScreenContext,
        scrollAttempted: Boolean,
        idleTimeoutMs: Long = retryPolicy.configFor("scroll").idleTimeoutMs,
    ): ScrollVerification {
        if (!scrollAttempted) return ScrollVerification(changed = false)
        AccessibilityBridge.waitForIdle(idleTimeoutMs)
        val after = AccessibilityBridge.observeScreenContext()
        val changed = !sameSnapshot(beforeContext, after)
        return ScrollVerification(changed = changed)
    }

    private fun sameSnapshot(
        a: dev.touchpilot.app.screen.ScreenContext,
        b: dev.touchpilot.app.screen.ScreenContext
    ): Boolean {
        if (a.nodes.size != b.nodes.size) return false
        return a.toRedactedJson() == b.toRedactedJson()
    }

    private fun scrollLogArgs(direction: String, target: String): String {
        return "direction=\"$direction\", target=$target"
    }

    private data class ScrollVerification(val changed: Boolean)

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

    private fun ResolveInfo.launcherLabel(): String {
        return loadLabel(context.packageManager)?.toString().orEmpty()
    }

    private fun ToolResult.withAttemptData(
        attempt: Int,
        category: ToolFailureCategory,
        retryReason: String,
    ): ToolResult {
        if (attempt <= 1 && category == ToolFailureCategory.NONE) return this
        return copy(
            data = data + mapOf(
                "attempt" to attempt.toString(),
                "failure_category" to category.wireName,
                "retry_reason" to retryReason,
            )
        )
    }

    private fun record(name: String, args: String, ok: Boolean, message: String) {
        ToolExecutionLog.record(name, args, ok, message)
    }

    private fun ToolResult.withVerification(verification: ToolVerificationResult): ToolResult {
        return copy(
            data = data + verification.data + mapOf(
                "verification_status" to verification.status.wireName,
                "verification_reason" to SensitiveTextRedactor.redact(verification.reason),
            )
        )
    }
}
