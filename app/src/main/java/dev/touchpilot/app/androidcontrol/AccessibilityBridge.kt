package dev.touchpilot.app.androidcontrol

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.ScreenContext

object AccessibilityBridge {
    @Volatile
    private var service: TouchPilotAccessibilityService? = null

    fun attach(service: TouchPilotAccessibilityService) {
        this.service = service
    }

    fun detach(service: TouchPilotAccessibilityService) {
        if (this.service === service) {
            this.service = null
        }
    }

    fun isConnected(): Boolean = service != null

    fun observeScreen(): String {
        return service?.observeScreen() ?: "TouchPilot Control is not enabled."
    }

    fun observeScreenContext(): ScreenContext {
        return service?.observeScreenContext() ?: ScreenContext.Empty
    }

    fun tapByText(text: String): Boolean {
        if (text.isBlank()) return false
        return service?.tapByText(text) ?: false
    }

    fun tapByNodeId(nodeId: String): Boolean {
        if (nodeId.isBlank()) return false
        return service?.tapByNodeId(nodeId) ?: false
    }

    fun tapByBounds(bounds: String): Boolean {
        if (bounds.isBlank()) return false
        return service?.tapByBounds(bounds) ?: false
    }

    fun activeWindowBounds(): NodeBounds? {
        return service?.activeWindowBounds()
    }

    fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long): Boolean {
        return service?.swipe(startX, startY, endX, endY, durationMs) ?: false
    }

    fun typeIntoFocusedField(text: String): Boolean {
        if (text.isBlank()) return false
        return service?.typeIntoFocusedField(text) ?: false
    }

    fun typeIntoNode(nodeId: String, text: String): Boolean {
        if (nodeId.isBlank() || text.isBlank()) return false
        return service?.typeIntoNode(nodeId, text) ?: false
    }

    fun scrollForward(): Boolean {
        return service?.scroll(forward = true) ?: false
    }

    fun scrollBackward(): Boolean {
        return service?.scroll(forward = false) ?: false
    }

    fun scrollNode(nodeId: String, forward: Boolean): Boolean {
        if (nodeId.isBlank()) return false
        return service?.scrollNode(nodeId, forward) ?: false
    }

    fun pressBack(): Boolean {
        return service?.pressBack() ?: false
    }

    fun pressHome(): Boolean {
        return service?.pressHome() ?: false
    }

    fun waitForText(text: String, timeoutMs: Long): Boolean {
        if (text.isBlank()) return false
        return service?.waitForText(text, timeoutMs) ?: false
    }

    fun waitForIdle(timeoutMs: Long): Boolean {
        return service?.waitForIdle(timeoutMs) ?: false
    }
}
