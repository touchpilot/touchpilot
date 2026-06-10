package dev.touchpilot.app.runtime

interface ToolExecutionCallbacks {
    fun recordToolsResult(message: String)
    fun refreshDeveloperLogs()
    fun refreshToolsScreen()
}
