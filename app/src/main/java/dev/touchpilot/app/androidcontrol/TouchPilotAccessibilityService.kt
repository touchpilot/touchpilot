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
        val root = rootInActiveWindow ?: return "No active window is available."
        return buildString {
            appendLine("TouchPilot screen snapshot")
            appendNode(root, depth = 0, maxDepth = 8, nodeId = "0")
        }
    }

    fun getForegroundApp(): ForegroundAppInfo {
        val root = rootInActiveWindow
        val rootPackage = root?.packageName?.toString()
        val packageName = rootPackage?.takeIf { it.isNotBlank() } ?: lastWindowPackage
        val windowTitle = root?.window?.title?.toString()?.takeIf { it.isNotBlank() }
        val activityClass = lastWindowActivity
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { lastWindowPackage == null || lastWindowPackage == packageName }
            ?: root?.className?.toString()?.takeIf { it.isNotBlank() }
        val appLabel = packageName?.let { loadAppLabel(it) }
        return ForegroundAppInfo(
            packageName = packageName,
            appLabel = appLabel,
            windowTitle = windowTitle,
            activityClass = activityClass,
            accessibilityConnected = true,
        )
    }

    private fun loadAppLabel(packageName: String): String? {
        return runCatching {
            val pm = applicationContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    fun observeScreenContext(): ScreenContext {
        val root = rootInActiveWindow ?: return ScreenContext.Empty
        val snapshot = AccessibilityNodeSnapshotAdapter.from(root)
        return ScreenContextBuilder().build(
            root = snapshot,
            packageName = root.packageName?.toString(),
            windowTitle = root.window?.title?.toString()
        )
    }

    fun tapByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNode(root) { candidate ->
            val label = candidate.text?.toString()
                ?: candidate.contentDescription?.toString()
                ?: ""
            label.contains(text, ignoreCase = true)
        } ?: return false

        return clickNodeOrParent(node)
    }

    fun tapByNodeId(nodeId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeById(root, nodeId) ?: return false
        return clickNodeOrParent(node) || tapNodeCenter(node)
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
        val root = rootInActiveWindow ?: return null
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return null
        return NodeBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
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

    fun longPressByNodeId(nodeId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeById(root, nodeId) ?: return false
        return longClickNodeOrParent(node) || longPressNodeCenter(node)
    }

    fun longPressByBounds(boundsText: String): Boolean {
        val bounds = parseBounds(boundsText) ?: return false
        if (bounds.isEmpty) return false
        return longPress(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }

    fun typeIntoFocusedField(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findNode(root) { it.isFocused }
            ?: return false

        return setNodeText(focused, text)
    }

    fun typeIntoNode(nodeId: String, text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeById(root, nodeId) ?: return false
        if (!node.isEnabled || !node.isEditableTarget()) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return setNodeText(node, text)
    }

    fun clearFocusedField(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: findNode(root) { it.isFocused }
            ?: return false
        if (!focused.isEnabled || !focused.isEditableTarget()) return false
        return setNodeText(focused, "")
    }

    fun clearNode(nodeId: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeById(root, nodeId) ?: return false
        if (!node.isEnabled || !node.isEditableTarget()) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return setNodeText(node, "")
    }

    fun focusInput(text: String, nodeId: String, bounds: String, viewId: String): FocusResult {
        val root = rootInActiveWindow ?: return FocusResult(false, "No active window is available.")

        val candidates: List<AccessibilityNodeInfo> = when {
            nodeId.isNotBlank() -> {
                val node = findNodeById(root, nodeId)
                    ?: return FocusResult(false, "No matching input target found.")
                listOf(node)
            }
            bounds.isNotBlank() -> {
                val targetBounds = parseBounds(bounds)
                    ?: return FocusResult(false, "Invalid bounds format.")
                findAllNodes(root) { candidate ->
                    val b = Rect()
                    candidate.getBoundsInScreen(b)
                    b == targetBounds
                }
            }
            viewId.isNotBlank() -> {
                findAllNodes(root) { candidate ->
                    candidate.viewIdResourceName == viewId
                }
            }
            else -> {
                findAllNodes(root) { candidate ->
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
                }
            }
        }

        if (candidates.isEmpty()) return FocusResult(false, "No matching input target found.")

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
        val root = rootInActiveWindow ?: return false
        val action = scrollAction(forward)

        val scrollable = findNode(root) { candidate ->
            candidate.isScrollable || candidate.actionList.any { it.id == action }
        } ?: return false

        return scrollable.performAction(action)
    }

    fun scrollNode(nodeId: String, forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findNodeById(root, nodeId) ?: return false
        val action = scrollAction(forward)
        // Walk up the tree until we find an ancestor that actually accepts the
        // scroll action. Some apps mark a list item as scrollable through its
        // parent ListView/RecyclerView rather than on the item itself.
        var current: AccessibilityNodeInfo? = target
        while (current != null) {
            if (current.isScrollable || current.actionList.any { it.id == action }) {
                return current.performAction(action)
            }
            current = current.parent
        }
        return false
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
        return DismissKeyboardOutcome.Hidden
    }

    fun waitForText(text: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(250L, 30_000L)
        while (System.currentTimeMillis() < deadline) {
            val root = rootInActiveWindow
            if (root != null && containsText(root, text)) {
                return true
            }
            Thread.sleep(150L)
        }
        return false
    }

    fun waitForIdle(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(250L, 5_000L)
        var previousSnapshot = ""
        var stableCount = 0

        while (System.currentTimeMillis() < deadline) {
            val snapshot = rootInActiveWindow?.let { root ->
                buildString { appendNode(root, depth = 0, maxDepth = 4, nodeId = "0") }
            }.orEmpty()

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
        }
    }

    private fun findNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findNode(child, predicate)
            if (found != null) return found
        }

        return null
    }

    private fun findAllNodes(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        collectNodes(node, predicate, result)
        return result
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (predicate(node)) result.add(node)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectNodes(child, predicate, result)
        }
    }

    private fun findNodeById(root: AccessibilityNodeInfo, nodeId: String): AccessibilityNodeInfo? {
        if (!nodeId.matches(Regex("\\d+(?:\\.\\d+)*"))) return null

        var current: AccessibilityNodeInfo = root
        val path = nodeId.split(".").mapNotNull { it.toIntOrNull() }
        if (path.firstOrNull() != 0) return null

        for (index in path.drop(1)) {
            current = current.getChild(index) ?: return null
        }

        return current
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun longClickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            val supportsLongClick = current.isLongClickable ||
                current.actionList.any { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
            if (supportsLongClick && current.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
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

    private fun tap(x: Float, y: Float): Boolean {
        return dispatchPressGesture(x = x, y = y, durationMs = 60L, timeoutMs = 1_000L)
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

    private fun containsText(node: AccessibilityNodeInfo, text: String): Boolean {
        return findNode(node) { candidate ->
            val label = candidate.text?.toString()
                ?: candidate.contentDescription?.toString()
                ?: ""
            label.contains(text, ignoreCase = true)
        } != null
    }

    private companion object {
        /** Lower bound so a swipe still registers as a gesture rather than a tap. */
        const val MinSwipeDurationMs = 50L

        /** Upper bound so a runaway duration cannot hold the gesture indefinitely. */
        const val MaxSwipeDurationMs = 5_000L

        /** Extra time beyond the gesture duration to wait for the result callback. */
        const val GestureCallbackTimeoutMs = 1_000L
    }
}
