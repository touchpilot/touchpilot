package dev.touchpilot.app.androidcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenContextBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TouchPilotAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach(this)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The first spike is pull-based from the debug screen.
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

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }

        val scrollable = findNode(root) { candidate ->
            candidate.isScrollable || candidate.actionList.any { it.id == action }
        } ?: return false

        return scrollable.performAction(action)
    }

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
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

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
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
        latch.await(1_000L, TimeUnit.MILLISECONDS)
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
}
