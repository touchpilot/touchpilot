package dev.touchpilot.app.androidcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.touchpilot.app.screen.NodeBounds
import android.view.accessibility.AccessibilityWindowInfo
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenContextBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TouchPilotAccessibilityService : AccessibilityService() {
    @Volatile
    private var lastWindowPackage: String? = null

    @Volatile
    private var lastWindowActivity: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // package + class on TYPE_WINDOW_STATE_CHANGED reflect the
            // foreground activity. We cache the class because
            // rootInActiveWindow alone cannot recover it later.
            val pkg = event.packageName?.toString()
            val cls = event.className?.toString()
            if (!pkg.isNullOrBlank()) lastWindowPackage = pkg
            if (!cls.isNullOrBlank()) lastWindowActivity = cls
        }
    }

    override fun onInterrupt() {
        // No long-running gesture is started in the initial spike.
    }

    fun observeScreen(): String {
        return useActiveRoot { root ->
            buildString {
                appendLine("TouchPilot screen snapshot")
                appendNode(root, depth = 0, maxDepth = 8, nodeId = "0")
            }
        } ?: "No active window is available."
    }

    fun getForegroundApp(): ForegroundAppInfo {
        val root = rootInActiveWindow
        return try {
            val rootPackage = root?.packageName?.toString()
            val packageName = rootPackage?.takeIf { it.isNotBlank() } ?: lastWindowPackage
            val windowTitle = root?.window?.title?.toString()?.takeIf { it.isNotBlank() }
            val activityClass = lastWindowActivity
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { lastWindowPackage == null || lastWindowPackage == packageName }
                ?: root?.className?.toString()?.takeIf { it.isNotBlank() }
            val appLabel = packageName?.let { loadAppLabel(it) }
            ForegroundAppInfo(
                packageName = packageName,
                appLabel = appLabel,
                windowTitle = windowTitle,
                activityClass = activityClass,
                accessibilityConnected = true,
            )
        } finally {
            root?.recycleSafely()
        }
    }

    private fun loadAppLabel(packageName: String): String? {
        return runCatching {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun observeScreenContext(): ScreenContext {
        return useActiveRoot { root ->
            val snapshot = AccessibilityNodeSnapshotAdapter.from(root)
            ScreenContextBuilder().build(
                root = snapshot,
                packageName = root.packageName?.toString(),
                windowTitle = root.window?.title?.toString()
            )
        } ?: ScreenContext.Empty
    }

    fun tapByText(text: String): Boolean {
        return useActiveRoot { root ->
            root.useFoundNode({ candidate ->
                val label = candidate.text?.toString()
                    ?: candidate.contentDescription?.toString()
                    ?: ""
                label.contains(text, ignoreCase = true)
            }) { node ->
                clickNodeOrParent(node)
            } ?: false
        } ?: false
    }

    fun tapByNodeId(nodeId: String): Boolean {
        return useActiveRoot { root ->
            root.useNodeById(nodeId) { node ->
                clickNodeOrParent(node) || tapNodeCenter(node)
            } ?: false
        } ?: false
    }

    fun tapByBounds(boundsText: String): Boolean {
        val bounds = parseBounds(boundsText) ?: return false
        if (bounds.isEmpty) return false
        return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    /**
     * Bounds of the active window in screen pixels, used by the `swipe` tool to
     * plan a full-screen direction swipe when no container target is given.
     */
    fun activeWindowBounds(): NodeBounds? {
        return useActiveRoot { root ->
            val bounds = Rect()
            root.getBoundsInScreen(bounds)
            if (bounds.isEmpty) return@useActiveRoot null
            NodeBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
    }

    /**
     * Dispatch a swipe: a single drag stroke from (startX, startY) to
     * (endX, endY) held for [durationMs]. Unlike `scroll`, this is a raw gesture
     * and works on surfaces that expose no accessibility scroll action (pagers,
     * carousels, drawers, maps).
     */
    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
        val duration = durationMs.coerceIn(MinSwipeDurationMs, MaxSwipeDurationMs)
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            .build()
        val completed = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed.set(true)
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed.set(false)
                    latch.countDown()
                }
            },
            null
        )

        if (!dispatched) return false
        latch.await(duration + GestureCallbackTimeoutMs, TimeUnit.MILLISECONDS)
        return completed.get()
    }

    /**
     * Double-tap a node's center. Unlike [tapByNodeId] this never uses the
     * accessibility `ACTION_CLICK` — there is no double-click accessibility
     * action — so it always dispatches the real two-tap gesture at the node
     * center, which is what surfaces such as image/map zoom, "double-tap to
     * like", and word selection listen for.
     */
    fun doubleTapByNodeId(nodeId: String): Boolean {
        return useActiveRoot { root ->
            root.useNodeById(nodeId) { node -> doubleTapNodeCenter(node) } ?: false
        } ?: false
    }

    fun doubleTapByBounds(boundsText: String): Boolean {
        val bounds = parseBounds(boundsText) ?: return false
        if (bounds.isEmpty) return false
        return doubleTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    fun longPressByNodeId(nodeId: String): Boolean {
        return useActiveRoot { root ->
            root.useNodeById(nodeId) { node ->
                longClickNodeOrParent(node) || longPressNodeCenter(node)
            } ?: false
        } ?: false
    }

    fun longPressByBounds(boundsText: String): Boolean {
        val bounds = parseBounds(boundsText) ?: return false
        if (bounds.isEmpty) return false
        return longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    fun typeIntoFocusedField(text: String): Boolean {
        return useActiveRoot { root ->
            val focusedFromFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedFromFocus != null) {
                try {
                    setNodeText(focusedFromFocus, text)
                } finally {
                    focusedFromFocus.recycleSafely()
                }
            } else {
                root.useFoundNode({ it.isFocused }) { focused ->
                    setNodeText(focused, text)
                } ?: false
            }
        } ?: false
    }

    fun typeIntoNode(nodeId: String, text: String): Boolean {
        return useActiveRoot { root ->
            root.useNodeById(nodeId) { node ->
                if (!node.isEnabled || !node.isEditableTarget()) return@useNodeById false
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                setNodeText(node, text)
            } ?: false
        } ?: false
    }

    fun clearFocusedField(): Boolean {
        return useActiveRoot { root ->
            val focusedFromFocus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedFromFocus != null) {
                try {
                    if (!focusedFromFocus.isEnabled || !focusedFromFocus.isEditableTarget()) {
                        return@useActiveRoot false
                    }
                    setNodeText(focusedFromFocus, "")
                } finally {
                    focusedFromFocus.recycleSafely()
                }
            } else {
                root.useFoundNode({ it.isFocused }) { focused ->
                    if (!focused.isEnabled || !focused.isEditableTarget()) return@useFoundNode false
                    setNodeText(focused, "")
                } ?: false
            }
        } ?: false
    }

    fun clearNode(nodeId: String): Boolean {
        return useActiveRoot { root ->
            root.useNodeById(nodeId) { node ->
                if (!node.isEnabled || !node.isEditableTarget()) return@useNodeById false
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                setNodeText(node, "")
            } ?: false
        } ?: false
    }

    fun focusInput(text: String, nodeId: String, bounds: String, viewId: String): FocusResult {
        return useActiveRoot { root ->
            when {
                nodeId.isNotBlank() -> root.useNodeById(nodeId) { node ->
                    focusInputFromCandidates(listOf(node))
                } ?: FocusResult(false, "No matching input target found.")
                bounds.isNotBlank() -> {
                    val targetBounds = parseBounds(bounds)
                        ?: return@useActiveRoot FocusResult(false, "Invalid bounds format.")
                    root.useMatchingNodes({ candidate ->
                        val b = Rect()
                        candidate.getBoundsInScreen(b)
                        b == targetBounds
                    }) { candidates ->
                        focusInputFromCandidates(candidates)
                    }
                }
                viewId.isNotBlank() -> root.useMatchingNodes({ candidate ->
                    candidate.viewIdResourceName == viewId
                }) { candidates ->
                    focusInputFromCandidates(candidates)
                }
                else -> root.useMatchingNodes({ candidate ->
                    if (candidate.isEditable) {
                        // Match editable fields by hint/label, not current typed content.
                        val hint = candidate.hintText?.toString() ?: ""
                        val desc = candidate.contentDescription?.toString() ?: ""
                        hint.contains(text, ignoreCase = true) || desc.contains(text, ignoreCase = true)
                    } else {
                        val label = candidate.text?.toString()
                            ?: candidate.contentDescription?.toString()
                            ?: ""
                        label.contains(text, ignoreCase = true)
                    }
                }) { candidates ->
                    focusInputFromCandidates(candidates)
                }
            }
        } ?: FocusResult(false, "No active window is available.")
    }

    private fun focusInputFromCandidates(candidates: List<AccessibilityNodeInfo>): FocusResult {
        if (candidates.isEmpty()) {
            return FocusResult(false, "No matching input target found.")
        }

        val editable = candidates.filter { it.isEditable }

        return when {
            editable.isEmpty() -> FocusResult(false, "Target is not an editable input field.")
            editable.size > 1 -> FocusResult(false, "Ambiguous input target.")
            else -> {
                val node = editable[0]
                val ok = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS) ||
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                    tapNodeCenter(node)
                FocusResult(ok, if (ok) "focusInput" else "Failed to focus input.")
            }
        }
    }

    fun scroll(forward: Boolean): Boolean {
        return useActiveRoot { root ->
            val action = scrollAction(forward)
            root.useFoundNode({ candidate ->
                candidate.isScrollable || candidate.actionList.any { it.id == action }
            }) { scrollable ->
                scrollable.performAction(action)
            } ?: false
        } ?: false
    }

    fun scrollNode(nodeId: String, forward: Boolean): Boolean {
        return useActiveRoot { root ->
            root.useNodeById(nodeId) { target ->
                val action = scrollAction(forward)
                // Walk up the tree until we find an ancestor that actually accepts the
                // scroll action. Some apps mark a list item as scrollable through its
                // parent ListView/RecyclerView rather than on the item itself.
                walkUpFrom(target) { current ->
                    (current.isScrollable || current.actionList.any { it.id == action }) &&
                        current.performAction(action)
                }
            } ?: false
        } ?: false
    }

    private fun scrollAction(forward: Boolean): Int {
        return if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
    }

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun openRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun isKeyboardVisible(): Boolean {
        // flagRetrieveInteractiveWindows is already declared in the service
        // config, so getWindows() surfaces the IME window when it is showing.
        return windows?.any { it?.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
    }

    /**
     * Hides the soft keyboard when it is visible. The implementation
     * deliberately avoids [GLOBAL_ACTION_BACK]: a stale window-list
     * observation paired with Back would silently navigate the foreground
     * app. Instead the soft keyboard show mode is flipped to
     * [AccessibilityService.SHOW_MODE_HIDDEN], which routes through
     * InputMethodManagerService and cannot navigate.
     *
     * The previous show mode is restored after a brief settle so subsequent
     * taps on an editable field bring the keyboard back as usual.
     *
     * Note: we deliberately do not gate success on [getWindows] returning a
     * cleared window list. `TYPE_INPUT_METHOD` can linger in that list for
     * seconds after the IME has visibly hidden — especially on emulators —
     * so polling it would produce false negatives. The show-mode change
     * itself is synchronous in IMMS, so we trust the action.
     */
    fun dismissKeyboard(timeoutMs: Long): DismissKeyboardOutcome {
        if (!isKeyboardVisible()) {
            return DismissKeyboardOutcome.AlreadyHidden
        }
        val controller = softKeyboardController
        val previousMode = controller.showMode
        controller.showMode = SHOW_MODE_HIDDEN
        // Brief settle so the IME hide animation can complete before the
        // tool returns and the agent inspects the screen.
        val settle = timeoutMs.coerceIn(50L, 5_000L).coerceAtMost(500L)
        Thread.sleep(settle)
        controller.showMode = previousMode
        // Restoring the previous show mode can re-trigger the platform
        // auto-show while a focused editable field is still on screen. Give the
        // IME a brief window to settle, then measure the real visibility rather
        // than assuming the hide stuck.
        Thread.sleep(settle)
        return DismissKeyboardOutcome.Hidden(stillVisibleAfter = isKeyboardVisible())
    }

    fun waitForText(text: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(250L, 30_000L)
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            val found = if (root != null) {
                try {
                    containsText(root, text)
                } finally {
                    root.recycleSafely()
                }
            } else {
                false
            }
            if (found) return true
            Thread.sleep(150L)
        }
        return false
    }

    fun waitForIdle(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(250L, 5_000L)
        var previousSnapshot = ""
        var stableCount = 0

        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            val snapshot = if (root != null) {
                try {
                    buildString { appendNode(root, depth = 0, maxDepth = 4, nodeId = "0") }
                } finally {
                    root.recycleSafely()
                }
            } else {
                ""
            }

            if (snapshot.isNotBlank() && snapshot == previousSnapshot) {
                stableCount += 1
                if (stableCount >= 2) return true
            } else {
                stableCount = 0
                previousSnapshot = snapshot
            }

            Thread.sleep(150L)
        }

        return false
    }

    private fun StringBuilder.appendNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        maxDepth: Int,
        nodeId: String
    ) {
        if (depth > maxDepth) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        append("  ".repeat(depth))
        append("- ")
        append(node.className ?: "Unknown")
        append(" node_id=\"$nodeId\"")

        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty()

        if (text.isNotBlank()) append(" text=\"$text\"")
        if (description.isNotBlank()) append(" desc=\"$description\"")
        if (viewId.isNotBlank()) append(" id=\"$viewId\"")
        if (node.isClickable) append(" clickable")
        if (node.isFocused) append(" focused")
        append(" bounds=\"${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}\"")
        appendLine()

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            appendNode(child, depth + 1, maxDepth, "$nodeId.$index")
            child.recycleSafely()
        }
    }

    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
        return node.useFoundNode({ candidate ->
            val label = candidate.text?.toString()
                ?: candidate.contentDescription?.toString()
                ?: ""
            label.contains(text, ignoreCase = true)
        }) { true } != null
    }
    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        return walkUpFrom(node) { current ->
            current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    private fun longClickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        return walkUpFrom(node) { current ->
            val supportsLongClick = current.isLongClickable ||
                current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
            supportsLongClick && current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEnabled || !node.isEditableTarget()) return false
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun AccessibilityNodeInfo.isEditableTarget(): Boolean {
        return isEditable || actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        return tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun longPressNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        return longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun doubleTapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        return doubleTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    private fun tap(x: Float, y: Float): Boolean {
        return dispatchPressGesture(x = x, y = y, durationMs = 60L, timeoutMs = 1_000L)
    }

    /**
     * Dispatch a double-tap at (x, y): two brief taps at the same point,
     * separated by a gap that keeps the pair inside the platform double-tap
     * timeout so surfaces recognize it as a double-tap rather than two
     * independent taps. Both taps are strokes in a single [GestureDescription],
     * the second offset by its start time.
     */
    private fun doubleTap(x: Float, y: Float): Boolean {
        val firstPath = Path().apply { moveTo(x, y) }
        val secondPath = Path().apply { moveTo(x, y) }
        val secondStart = DoubleTapTapMs + DoubleTapGapMs
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(firstPath, 0L, DoubleTapTapMs))
            .addStroke(GestureDescription.StrokeDescription(secondPath, secondStart, DoubleTapTapMs))
            .build()
        val completed = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed.set(true)
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed.set(false)
                    latch.countDown()
                }
            },
            null
        )

        if (!dispatched) return false
        latch.await(secondStart + DoubleTapTapMs + GestureCallbackTimeoutMs, TimeUnit.MILLISECONDS)
        return completed.get()
    }

    private fun longPress(x: Float, y: Float): Boolean {
        return dispatchPressGesture(x = x, y = y, durationMs = 650L, timeoutMs = 1_750L)
    }

    private fun dispatchPressGesture(
        x: Float,
        y: Float,
        durationMs: Long,
        timeoutMs: Long
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        val completed = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    completed.set(true)
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    completed.set(false)
                    latch.countDown()
                }
            },
            null
        )

        if (!dispatched) return false
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return completed.get()
    }

    private fun parseBounds(boundsText: String): Rect? {
        val values = boundsText
            .split(",", " ", "[", "]")
            .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toIntOrNull() }
        if (values.size != 4) return null
        return Rect(values[0], values[1], values[2], values[3])
    }

    private companion object {
        /** Lower bound so a swipe still registers as a gesture rather than a tap. */
        const val MinSwipeDurationMs = 50L

        /** Upper bound so a runaway duration cannot hold the gesture indefinitely. */
        const val MaxSwipeDurationMs = 5_000L

        /** Duration of each individual tap within a double-tap. */
        const val DoubleTapTapMs = 50L

        /**
         * Gap between the two taps of a double-tap. Kept short so the pair
         * lands inside the platform double-tap timeout (~300 ms) and registers
         * as a double-tap rather than two separate taps.
         */
        const val DoubleTapGapMs = 120L

        /** Extra time beyond the gesture duration to wait for the result callback. */
        const val GestureCallbackTimeoutMs = 1_000L
    }
}
