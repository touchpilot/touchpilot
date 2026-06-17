package dev.touchpilot.app.workflow

import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.screen.ScreenContext

/**
 * Testable facade over accessibility primitives used for workflow state
 * verification.
 */
interface WorkflowObservation {
    fun waitForText(text: String, timeoutMs: Long): Boolean
    fun isKeyboardVisible(): Boolean
    fun observeScreenContext(): ScreenContext
}

object AccessibilityWorkflowObservation : WorkflowObservation {
    override fun waitForText(text: String, timeoutMs: Long): Boolean {
        return AccessibilityBridge.waitForText(text, timeoutMs)
    }

    override fun isKeyboardVisible(): Boolean {
        return AccessibilityBridge.isKeyboardVisible()
    }

    override fun observeScreenContext(): ScreenContext {
        return AccessibilityBridge.observeScreenContext()
    }
}
