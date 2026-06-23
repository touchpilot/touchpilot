package dev.touchpilot.app.demonstration.recording

import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult

/**
 * Callback invoked by [dev.touchpilot.app.tools.AndroidToolExecutor] around each
 * tool execution so demonstration recording can capture before/after screen state.
 */
interface ToolExecutionRecordingListener {
    fun onBeforeExecution(tool: String, args: Map<String, String>, source: ToolSource, before: ScreenContext)
    fun onAfterExecution(
        tool: String,
        args: Map<String, String>,
        source: ToolSource,
        before: ScreenContext,
        after: ScreenContext,
        result: ToolResult,
    )
}

object NoOpToolExecutionRecordingListener : ToolExecutionRecordingListener {
    override fun onBeforeExecution(
        tool: String,
        args: Map<String, String>,
        source: ToolSource,
        before: ScreenContext,
    ) = Unit

    override fun onAfterExecution(
        tool: String,
        args: Map<String, String>,
        source: ToolSource,
        before: ScreenContext,
        after: ScreenContext,
        result: ToolResult,
    ) = Unit
}
