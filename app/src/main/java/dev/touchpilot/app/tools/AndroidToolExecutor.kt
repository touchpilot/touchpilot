package dev.touchpilot.app.tools

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.ResolveInfo
import android.net.Uri
import dev.touchpilot.app.agent.ClarificationFromToolResult
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.androidcontrol.DismissKeyboardOutcome
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.tools.targets.ClearTextTarget
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
import dev.touchpilot.app.tools.targets.TargetSelectorBuilder
import dev.touchpilot.app.tools.targets.displayLabels
import dev.touchpilot.app.tools.targets.TypeTextTarget

class AndroidToolExecutor(
    private val context: Context,
    private val policy: DefaultActionPolicy = DefaultActionPolicy(),
    private val approvalProvider: ToolApprovalProvider? = null,
    private val targetResolver: TargetResolver = TargetResolver(),
    private val scrollResolver: ScrollResolver = ScrollResolver(targetResolver),
    private val retryPolicy: AndroidToolRetryPolicy = AndroidToolRetryPolicy(),
    private val verifier: ToolVerifier = ToolVerifier(),
    private val findElementMatcher: FindElementMatcher = FindElementMatcher(),
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private var recordingListener: dev.touchpilot.app.demonstration.recording.ToolExecutionRecordingListener =
        dev.touchpilot.app.demonstration.recording.NoOpToolExecutionRecordingListener,
) {
    private val executionSource = ThreadLocal.withInitial { ToolSource.DIRECT_DEBUG }

    fun setRecordingListener(listener: dev.touchpilot.app.demonstration.recording.ToolExecutionRecordingListener) {
        recordingListener = listener
    }

    fun execute(
        name: String,
        args: Map<String, String>,
        source: ToolSource = ToolSource.DIRECT_DEBUG,
        foregroundApp: ForegroundAppInfo? = null
    ): ToolResult {
        val resolvedForegroundApp = foregroundApp ?: AccessibilityBridge.getForegroundApp()
        val previousSource = executionSource.get()
        executionSource.set(source)
        try {
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
                when (val decision = policy.evaluate(
                    ToolPolicyRequest(
                        tool = spec,
                        args = args,
                        source = source,
                        activeScreen = observeScreen(),
                        foregroundApp = resolvedForegroundApp
                    )
                )) {
                    is PolicyDecision.Block -> {
                        record(name, "policy=block", false, decision.userMessage)
                        return ToolResult(false, decision.userMessage)
                    }
                    is PolicyDecision.Deny -> {
                        record(name, "policy=deny", false, decision.userMessage)
                        return ToolResult(false, decision.userMessage)
                    }
                    is PolicyDecision.Allow -> Unit
                    is PolicyDecision.RequireApproval -> {
                        val approvalRequest = ToolApprovalRequest(spec, args, decision)
                        val approved = approvalProvider?.approve(approvalRequest) ?: false
                        if (!approved) {
                            record(name, "policy=require_approval", false, decision.userMessage)
                            return ToolResult(false, decision.userMessage)
                        }
                    }
                }
            }

            val result = executeWithRetry(name, args)
            val retryConfig = retryPolicy.configFor(name)
            if (result.ok && retryConfig.waitForIdleAfterSuccess && retryConfig.idleTimeoutMs > 0L) {
                AccessibilityBridge.waitForIdle(retryConfig.idleTimeoutMs)
            }
            return result
        } finally {
            executionSource.set(previousSource)
        }
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
        val source = executionSource.get() ?: ToolSource.DIRECT_DEBUG
        recordingListener.onBeforeExecution(name, args, source, before)
        val result = executeOnce(name, args)
        val after = if (name == "observe_screen" || name == "observe_screen_context") {
            before
        } else {
            AccessibilityBridge.observeScreenContext()
        }
        recordingListener.onAfterExecution(name, args, source, before, after, result)
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
            "observe_screen_context" -> {
                val connected = AccessibilityBridge.isConnected()
                val screenContext = if (connected) {
                    AccessibilityBridge.observeScreenContext()
                } else {
                    ScreenContext.Empty
                }
                val outcome = ObserveScreenContext.outcome(connected, screenContext)
                record(name, "", connected, outcome.logMessage)
                outcome.result
            }
            "open_app" -> {
                val target = args["target"].orEmpty()
                val ok = openApp(target)
                record(name, "target=\"$target\"", ok, "openApp")
                ToolResult(ok, "openApp")
            }
            "open_settings_panel" -> {
                executeOpenSettingsPanel(args)
            }
            "tap" -> {
                executeTap(args)
            }
            "long_press" -> {
                executeLongPress(args)
            }
            "type_text" -> {
                executeTypeText(args)
            }
            "scroll" -> {
                executeScroll(args)
            }
            "scroll_to_element" -> {
                executeScrollToElement(args)
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
            "recent_apps" -> {
                val ok = AccessibilityBridge.openRecents()
                record(name, "", ok, "openRecents")
                ToolResult(ok, "openRecents")
            }
            "wait_for_ui" -> {
                val text = args["text"].orEmpty()
                val timeout = args["timeout_ms"]?.toLongOrNull() ?: WaitForUi.DefaultTimeoutMs
                val ok = AccessibilityBridge.waitForText(text, timeout)
                record(name, WaitForUi.logArgs(text, timeout), ok, "waitForText")
                ToolResult(ok, "waitForText")
            }
            "wait_for_idle" -> {
                executeWaitForIdle(args)
            }
            "wait_for_app" -> {
                executeWaitForApp(args)
            }
            "wait_for_element" -> {
                executeWaitForElement(args)
            }
            "focus_input" -> {
                val text = args["text"].orEmpty()
                val nodeId = args["node_id"].orEmpty()
                val bounds = args["bounds"].orEmpty()
                val viewId = args["view_id"].orEmpty()
                val result = AccessibilityBridge.focusInput(text, nodeId, bounds, viewId)
                val selectorLog = when {
                    nodeId.isNotBlank() -> "node_id=\"$nodeId\""
                    bounds.isNotBlank() -> "bounds=\"$bounds\""
                    viewId.isNotBlank() -> "view_id=\"$viewId\""
                    else -> "text_length=${text.length}"
                }
                record(name, selectorLog, result.ok, result.message)
                ToolResult(result.ok, result.message)
            }
            "dismiss_keyboard" -> {
                executeDismissKeyboard(args)
            }
            "get_foreground_app" -> {
                val info = AccessibilityBridge.getForegroundApp()
                val ok = info.accessibilityConnected
                record(
                    name,
                    "package=\"${info.packageName.orEmpty()}\", connected=${info.accessibilityConnected}",
                    ok,
                    info.summarize()
                )
                ToolResult(
                    ok = ok,
                    message = info.summarize(),
                    data = foregroundAppData(info)
                )
            }
            "find_element" -> {
                executeFindElement(args)
            }
            "clear_text" -> {
                executeClearText(args)
            }
            else -> {
                record(name, args.toString(), false, "unhandled tool")
                ToolResult(false, "Unhandled tool: $name")
            }
        }
    }

    private fun executeFindElement(args: Map<String, String>): ToolResult {
        val query = FindElementQuery(
            text = args["text"]?.takeIf { it.isNotBlank() },
            contentDescription = args["content_description"]?.takeIf { it.isNotBlank() },
            nodeId = args["node_id"]?.takeIf { it.isNotBlank() },
            className = args["class_name"]?.takeIf { it.isNotBlank() },
            match = MatchMode.fromWire(args["match"]) ?: MatchMode.Default,
            limit = args["limit"]?.toIntOrNull() ?: FindElementQuery.DefaultLimit,
        )
        val screen = AccessibilityBridge.observeScreenContext()
        val candidates = findElementMatcher.match(screen, query)
        val payload = FindElementResultEncoder.encode(candidates, query)
        val message = "find_element matched ${candidates.size} candidate(s)"
        val logArgs = "match=\"${query.match.wireName}\", limit=${query.effectiveLimit()}, " +
            "filters=${findElementFilterSummary(query)}"
        record("find_element", logArgs, true, message)
        return ToolResult(
            ok = true,
            message = message,
            data = mapOf(
                "count" to candidates.size.toString(),
                "candidates_json" to payload,
                "match_mode" to query.match.wireName,
                "limit" to query.effectiveLimit().toString(),
            )
        )
    }

    private fun findElementFilterSummary(query: FindElementQuery): String {
        val parts = mutableListOf<String>()
        if (!query.text.isNullOrBlank()) parts += "text_length=${query.text.length}"
        if (!query.contentDescription.isNullOrBlank()) {
            parts += "content_description_length=${query.contentDescription.length}"
        }
        if (!query.nodeId.isNullOrBlank()) parts += "node_id=\"${query.nodeId}\""
        if (!query.className.isNullOrBlank()) parts += "class_name=\"${query.className}\""
        return parts.joinToString(prefix = "[", postfix = "]")
    }

    private fun executeWaitForIdle(args: Map<String, String>): ToolResult {
        val result = WaitForIdle.waitUntilIdle(
            args = args,
            observe = { AccessibilityBridge.observeScreenContext() },
            sleeper = sleeper
        )
        record(
            "wait_for_idle",
            "stable_ms=${WaitForIdle.stableMs(args)}, timeout_ms=${WaitForIdle.timeoutMs(args)}, " +
                "include_bounds=${WaitForIdle.includeBounds(args)}",
            result.ok,
            result.message
        )
        return result
    }

    private fun executeWaitForApp(args: Map<String, String>): ToolResult {
        val timeout = WaitForApp.timeoutMs(args)
        val deadline = System.currentTimeMillis() + timeout
        var latest = AccessibilityBridge.getForegroundApp()

        while (System.currentTimeMillis() <= deadline) {
            val match = WaitForApp.matches(args, latest)
            if (match.matched) {
                record(
                    "wait_for_app",
                    "${WaitForApp.expectedSummary(args)}, timeout_ms=$timeout",
                    true,
                    "waitForApp"
                )
                return WaitForApp.successResult(args, latest, match.matchedBy)
            }
            val remainingMs = deadline - System.currentTimeMillis()
            if (remainingMs <= 0L) break
            sleeper(minOf(150L, remainingMs))
            latest = AccessibilityBridge.getForegroundApp()
        }

        val result = WaitForApp.timeoutResult(args, latest, timeout)
        record(
            "wait_for_app",
            "${WaitForApp.expectedSummary(args)}, timeout_ms=$timeout",
            false,
            result.message
        )
        return result
    }

    private fun executeWaitForElement(args: Map<String, String>): ToolResult {
        val query = WaitForElement.queryFromArgs(args)
        val timeout = WaitForElement.timeoutMs(args)
        val logArgs = WaitForElement.logArgs(args, timeout)
        val deadline = System.currentTimeMillis() + timeout
        var latest = AccessibilityBridge.observeScreenContext()

        while (System.currentTimeMillis() <= deadline) {
            val matches = findElementMatcher.match(latest, query)
            if (matches.isNotEmpty()) {
                record("wait_for_element", logArgs, true, "waitForElement")
                return WaitForElement.successResult(args, matches.size)
            }
            val remainingMs = deadline - System.currentTimeMillis()
            if (remainingMs <= 0L) break
            sleeper(minOf(150L, remainingMs))
            latest = AccessibilityBridge.observeScreenContext()
        }

        val result = WaitForElement.timeoutResult(args, timeout)
        record("wait_for_element", logArgs, false, result.message)
        return result
    }

    private fun executeTap(args: Map<String, String>): ToolResult {
        return executeResolvedPress(
            toolName = "tap",
            args = args,
            dispatchByNodeId = AccessibilityBridge::tapByNodeId,
            dispatchByBounds = AccessibilityBridge::tapByBounds,
            successMessage = "tap",
            failureVerb = "tap"
        )
    }

    private fun executeLongPress(args: Map<String, String>): ToolResult {
        return executeResolvedPress(
            toolName = "long_press",
            args = args,
            dispatchByNodeId = AccessibilityBridge::longPressByNodeId,
            dispatchByBounds = AccessibilityBridge::longPressByBounds,
            successMessage = "longPress",
            failureVerb = "long-press"
        )
    }

    private fun executeResolvedPress(
        toolName: String,
        args: Map<String, String>,
        dispatchByNodeId: (String) -> Boolean,
        dispatchByBounds: (String) -> Boolean,
        successMessage: String,
        failureVerb: String,
    ): ToolResult {
        val selector = TargetSelectorBuilder.fromLegacyArgs(args)
        val resolution = targetResolver.resolve(
            context = AccessibilityBridge.observeScreenContext(),
            selector = selector
        )

        return when (resolution) {
            is TargetResolutionResult.Resolved -> {
                val candidate = resolution.candidate
                val nodeId = candidate.node.nodeId
                val boundsArg = candidate.selector.bounds?.toBoundsArg()
                val ok = when {
                    !nodeId.isNullOrBlank() -> dispatchByNodeId(nodeId)
                    boundsArg != null -> dispatchByBounds(boundsArg)
                    else -> false
                }
                val message = when {
                    ok -> successMessage
                    nodeId.isNullOrBlank() && boundsArg == null -> "Resolved $failureVerb target has no stable node id or bounds"
                    else -> "Unable to perform $failureVerb on resolved target"
                }
                record(toolName, targetLogArgs(candidate.selector), ok, message)
                ToolResult(
                    ok = ok,
                    message = message,
                    data = buildMap {
                        put("selector", targetLogArgs(candidate.selector))
                        if (!nodeId.isNullOrBlank()) put("node_id", nodeId)
                        put("confidence", candidate.confidence.toString())
                        put("match_reasons", candidate.matchReasons.joinToString(","))
                    }
                )
            }
            is TargetResolutionResult.Ambiguous -> {
                val message = "Ambiguous $failureVerb target: ${resolution.reason}"
                record(toolName, "target=ambiguous", false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = ClarificationFromToolResult.dataFromCandidateLabels(
                        resolution.candidates.displayLabels()
                    )
                )
            }
            is TargetResolutionResult.NotFound -> {
                val message = "${failureVerb.replaceFirstChar { it.uppercase() }} target not found: ${resolution.reason}"
                record(toolName, "target=not_found", false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = mapOf("debug_context" to resolution.debugContext)
                )
            }
        }
    }

    private fun targetLogArgs(selector: TargetSelector): String {
        val label = selector.text?.displaySafe?.takeIf { it.isNotBlank() }
            ?: selector.contentDescription?.displaySafe?.takeIf { it.isNotBlank() }
            ?: selector.nodeId?.let { "node_id=$it" }
            ?: selector.viewIdResourceName?.let { "view_id=$it" }
            ?: selector.bounds?.let { "bounds=${it.toBoundsArg()}" }
            ?: "resolved"
        return "target=\"$label\""
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
                    data = ClarificationFromToolResult.dataFromCandidateLabels(
                        resolution.candidates.displayLabels()
                    )
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

    private fun executeScrollToElement(args: Map<String, String>): ToolResult {
        val query = ScrollToElement.queryFromArgs(args)
        val maxScrolls = ScrollToElement.maxScrolls(args)
        val logArgs = ScrollToElement.logArgs(args)
        val scrollArgs = ScrollToElement.scrollArgs(args)

        // Already visible — no scroll needed.
        if (findElementMatcher.match(AccessibilityBridge.observeScreenContext(), query).isNotEmpty()) {
            record("scroll_to_element", logArgs, true, "Element already visible")
            return ScrollToElement.successResult(args, scrolls = 0, matchCount = 1)
        }

        var performed = 0
        repeat(maxScrolls) {
            val scrollResult = executeScroll(scrollArgs)
            performed += 1
            val screenChanged = scrollResult.data["screen_changed"]?.toBooleanStrictOrNull()
                ?: scrollResult.ok
            val matches = findElementMatcher.match(AccessibilityBridge.observeScreenContext(), query)
            if (matches.isNotEmpty()) {
                record("scroll_to_element", logArgs, true, "Scrolled to element after $performed step(s)")
                return ScrollToElement.successResult(args, performed, matches.size)
            }
            if (!screenChanged) {
                val result = ScrollToElement.notFoundResult(
                    args,
                    performed,
                    "Reached end of scrollable content before the element appeared",
                )
                record("scroll_to_element", logArgs, false, result.message)
                return result
            }
        }

        val result = ScrollToElement.notFoundResult(
            args,
            performed,
            "Element not found within $maxScrolls scroll step(s)",
        )
        record("scroll_to_element", logArgs, false, result.message)
        return result
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
                val accessibilityScrolled = AccessibilityBridge.scrollNode(nodeId, forward)
                var verification = verifyScreenChanged(beforeContext, accessibilityScrolled)
                var usedGesture = false
                if (!verification.changed) {
                    val area = node.bounds.toTargetBounds().takeIf { !it.isEmpty }
                        ?: AccessibilityBridge.activeWindowBounds()?.toTargetBounds()
                    if (area != null && scrollByGesture(area, forward)) {
                        usedGesture = true
                        verification = verifyScreenChanged(beforeContext, true)
                    }
                }
                val attempted = accessibilityScrolled || usedGesture
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
                        "method" to if (usedGesture) "gesture" else "accessibility",
                    )
                )
            }
            is ScrollResolution.Ambiguous -> {
                val message = "Ambiguous scroll target: ${resolution.reason}"
                record("scroll", scrollLogArgs(direction, "ambiguous"), false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = ClarificationFromToolResult.dataFromCandidateLabels(
                        resolution.candidates.displayLabels()
                    )
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
                val accessibilityScrolled = if (backward) {
                    AccessibilityBridge.scrollBackward()
                } else {
                    AccessibilityBridge.scrollForward()
                }
                var verification = verifyScreenChanged(beforeContext, accessibilityScrolled)
                var usedGesture = false
                if (!verification.changed) {
                    val area = AccessibilityBridge.activeWindowBounds()?.toTargetBounds()
                    if (area != null && scrollByGesture(area, forward)) {
                        usedGesture = true
                        verification = verifyScreenChanged(beforeContext, true)
                    }
                }
                val attempted = accessibilityScrolled || usedGesture
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
                        "method" to if (usedGesture) "gesture" else "accessibility",
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

    /**
     * Fallback for scrolls where the accessibility `ACTION_SCROLL_*` path did
     * not move the screen (issue #218): some emulators and canvas/WebView
     * surfaces ignore node scroll actions while `dispatchGesture` swipes still
     * register. Plans a swipe over [area] in the scroll travel direction and
     * dispatches it, returning true when the gesture was accepted. Gesture
     * planning lives in [SwipeGesture] so it stays unit-testable.
     */
    private fun scrollByGesture(area: TargetBounds, forward: Boolean): Boolean {
        if (area.isEmpty) return false
        val request = SwipeGesture.forScroll(forward, area)
        return AccessibilityBridge.swipe(
            request.startX,
            request.startY,
            request.endX,
            request.endY,
            request.durationMs,
        )
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

    private fun executeDismissKeyboard(args: Map<String, String>): ToolResult {
        val timeoutMs = args["timeout_ms"]?.toLongOrNull() ?: 500L
        val outcome = AccessibilityBridge.dismissKeyboard(timeoutMs)
        val ok = when (outcome) {
            is DismissKeyboardOutcome.Hidden,
            is DismissKeyboardOutcome.AlreadyHidden -> true
            is DismissKeyboardOutcome.NotConnected -> false
        }
        val message = when (outcome) {
            is DismissKeyboardOutcome.Hidden -> "dismissKeyboard"
            is DismissKeyboardOutcome.AlreadyHidden -> "Keyboard already hidden"
            is DismissKeyboardOutcome.NotConnected -> "TouchPilot Control is not enabled."
        }
        record(
            "dismiss_keyboard",
            "timeout_ms=$timeoutMs",
            ok,
            "${outcome.javaClass.simpleName}",
        )
        return ToolResult(
            ok = ok,
            message = message,
            data = mapOf(
                "was_visible_before" to outcome.wasVisibleBefore.toString(),
                "still_visible_after" to outcome.stillVisibleAfter.toString(),
            )
        )
    }

    private fun foregroundAppData(info: ForegroundAppInfo): Map<String, String> {
        return buildMap {
            put("accessibility_connected", info.accessibilityConnected.toString())
            info.packageName?.takeIf { it.isNotBlank() }?.let { put("package_name", it) }
            info.appLabel?.takeIf { it.isNotBlank() }?.let { put("app_label", it) }
            info.windowTitle?.takeIf { it.isNotBlank() }?.let { put("window_title", it) }
            info.activityClass?.takeIf { it.isNotBlank() }?.let { put("activity_class", it) }
            put("json", info.toJson().toString())
        }
    }

    private fun executeClearText(args: Map<String, String>): ToolResult {
        if (!ClearTextTarget.hasTarget(args)) {
            val ok = AccessibilityBridge.clearFocusedField()
            record("clear_text", "target=focused", ok, if (ok) "clearFocusedField" else "No editable focused input is available")
            return ToolResult(ok, if (ok) "clearFocusedField" else "No editable focused input is available")
        }

        val selector = ClearTextTarget.selectorFromArgs(args)
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
                    record("clear_text", clearTextLogArgs(resolution.candidate.selector), false, message)
                    ToolResult(
                        ok = false,
                        message = message,
                        data = mapOf(
                            "target" to resolution.candidate.selector.toRedactedJson(),
                            "confidence" to resolution.candidate.confidence.toString(),
                        )
                    )
                } else {
                    val ok = AccessibilityBridge.clearNode(nodeId)
                    val message = if (ok) "clearResolvedInput" else "Unable to focus or clear resolved input"
                    record("clear_text", clearTextLogArgs(resolution.candidate.selector), ok, message)
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
                record("clear_text", "target=ambiguous", false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = ClarificationFromToolResult.dataFromCandidateLabels(
                        resolution.candidates.displayLabels()
                    )
                )
            }
            is TargetResolutionResult.NotFound -> {
                val message = "Input target not found: ${resolution.reason}"
                record("clear_text", "target=not_found", false, message)
                ToolResult(
                    ok = false,
                    message = message,
                    data = mapOf("debug_context" to resolution.debugContext)
                )
            }
        }
    }

    private fun clearTextLogArgs(selector: TargetSelector): String {
        val label = selector.text?.displaySafe
            ?: selector.contentDescription?.displaySafe
            ?: selector.nodeId?.let { "node_id=$it" }
            ?: selector.viewIdResourceName?.let { "view_id=$it" }
            ?: selector.bounds?.let { "bounds=${it.toBoundsArg()}" }
            ?: "resolved"
        return "target=\"$label\""
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

    private fun executeOpenSettingsPanel(args: Map<String, String>): ToolResult {
        val panel = args[SettingsPanelIntent.PanelArg].orEmpty()
        val spec = SettingsPanelIntent.resolve(panel)
            ?: return ToolResult(false, SettingsPanelIntent.unsupportedMessage(panel))
        val packageName = context.packageName
        val intent = Intent(spec.action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            spec.dataUri
                ?.replace(SettingsPanelIntent.AppPackagePlaceholder, packageName)
                ?.let { data = Uri.parse(it) }
            spec.extras.forEach { (key, value) ->
                putExtra(key, value.replace(SettingsPanelIntent.AppPackagePlaceholder, packageName))
            }
        }
        return try {
            context.startActivity(intent)
            record(
                "open_settings_panel",
                "panel=\"${spec.panel}\"",
                true,
                "opened ${spec.panel} settings panel"
            )
            ToolResult(
                ok = true,
                message = "Opened ${spec.panel} settings panel",
                data = mapOf(
                    "panel" to spec.panel,
                    "action" to spec.action,
                )
            )
        } catch (_: ActivityNotFoundException) {
            val message = "No settings activity found for panel: ${spec.panel}"
            record("open_settings_panel", "panel=\"${spec.panel}\"", false, message)
            ToolResult(false, message, data = mapOf("panel" to spec.panel, "action" to spec.action))
        }
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
        ToolExecutionLog.record(
            name = name,
            args = args,
            ok = ok,
            message = message,
            source = (executionSource.get() ?: ToolSource.DIRECT_DEBUG).name.lowercase()
        )
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
