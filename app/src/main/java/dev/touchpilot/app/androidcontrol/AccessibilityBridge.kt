package dev.touchpilot.app.androidcontrol

import dev.touchpilot.app.screen.NodeBounds
import dev.touchpilot.app.screen.ScreenContext

data class FocusResult(val ok: Boolean, val message: String)

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

    fun getForegroundApp(): ForegroundAppInfo {
        return service?.getForegroundApp() ?: ForegroundAppInfo.Disconnected
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

    fun longPressByNodeId(nodeId: String): Boolean {
        if (nodeId.isBlank()) return false
        return service?.longPressByNodeId(nodeId) ?: false
    }

    fun longPressByBounds(bounds: String): Boolean {
        if (bounds.isBlank()) return false
        return service?.longPressByBounds(bounds) ?: false
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

    fun isKeyboardVisible(): Boolean {
        return service?.isKeyboardVisible() ?: false
    }

    fun dismissKeyboard(timeoutMs: Long): DismissKeyboardOutcome {
        return service?.dismissKeyboard(timeoutMs) ?: DismissKeyboardOutcome.NotConnected
    }

    fun waitForText(text: String, timeoutMs: Long): Boolean {
        if (text.isBlank()) return false
        return service?.waitForText(text, timeoutMs) ?: false
    }

    fun waitForIdle(timeoutMs: Long): Boolean {
        return service?.waitForIdle(timeoutMs) ?: false
    }

    fun focusInput(text: String, nodeId: String, bounds: String, viewId: String): FocusResult {
        return service?.focusInput(text, nodeId, bounds, viewId)
            ?: FocusResult(false, "TouchPilot Control is not enabled.")
    }

    fun clearFocusedField(): Boolean {
        return service?.clearFocusedField() ?: false
    }

    fun clearNode(nodeId: String): Boolean {
        if (nodeId.isBlank()) return false
        return service?.clearNode(nodeId) ?: false
    }
}
