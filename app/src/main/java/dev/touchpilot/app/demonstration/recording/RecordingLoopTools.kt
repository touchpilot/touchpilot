package dev.touchpilot.app.demonstration.recording

import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.demonstration.DemonstrationCapturePhase
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec

/**
 * Decorator around [LocalAgentLoopTools] that notifies a demonstration recorder
 * when tools execute, enabling per-step screen capture in the agent loop.
 */
class RecordingLoopTools(
    private val delegate: LocalAgentLoopTools,
    private val listener: ToolExecutionRecordingListener,
    private val source: ToolSource,
    private val observeContext: () -> dev.touchpilot.app.screen.ScreenContext,
) : LocalAgentLoopTools {
    override fun observeScreen(): String = delegate.observeScreen()

    override fun foregroundApp(): ForegroundAppInfo = delegate.foregroundApp()

    override fun validate(name: String, args: Map<String, String>): String? {
        return delegate.validate(name, args)
    }

    override fun findTool(name: String): ToolSpec? = delegate.findTool(name)

    override fun execute(
        name: String,
        args: Map<String, String>,
        source: ToolSource,
        foregroundApp: ForegroundAppInfo,
    ): ToolResult {
        val before = observeContext()
        listener.onBeforeExecution(name, args, source, before)
        val result = delegate.execute(name, args, source, foregroundApp)
        val after = if (name == "observe_screen" || name == "observe_screen_context") {
            before
        } else {
            observeContext()
        }
        listener.onAfterExecution(name, args, source, before, after, result)
        return result
    }

    override fun recordExecution(name: String, args: String, ok: Boolean, message: String) {
        delegate.recordExecution(name, args, ok, message)
    }
}
