package dev.touchpilot.app.workflow

import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo

/**
 * Live device signals used for policy evaluation during workflow replay.
 * Values are read at dispatch time — never from captured workflow metadata.
 */
data class WorkflowLivePolicyContext(
    val foregroundApp: ForegroundAppInfo = ForegroundAppInfo.Disconnected,
    val activeScreen: String = "",
) {
    val foregroundPackage: String?
        get() = foregroundApp.packageName

    val foregroundAppLabel: String?
        get() = foregroundApp.appLabel
}

fun interface WorkflowLivePolicyContextProvider {
    fun current(): WorkflowLivePolicyContext
}

object DefaultWorkflowLivePolicyContextProvider : WorkflowLivePolicyContextProvider {
    override fun current(): WorkflowLivePolicyContext {
        return WorkflowLivePolicyContext(
            foregroundApp = AccessibilityBridge.getForegroundApp(),
            activeScreen = AccessibilityBridge.observeScreen(),
        )
    }
}

fun ForegroundAppInfo.toWorkflowLivePolicyContext(activeScreen: String): WorkflowLivePolicyContext {
    return WorkflowLivePolicyContext(
        foregroundApp = this,
        activeScreen = activeScreen,
    )
}
