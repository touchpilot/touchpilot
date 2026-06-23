package dev.touchpilot.app.demonstration.recording

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.demonstration.DemonstrationCapturePhase
import dev.touchpilot.app.demonstration.DemonstrationScreenFrame
import dev.touchpilot.app.demonstration.DemonstrationToolAction
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridges [AgentEvent] stream and tool execution callbacks into structured
 * demonstration step records.
 */
class DemonstrationEventBridge(
    private val capturer: DemonstrationScreenCapturer,
    private val onStepCaptured: (PendingStep) -> Unit,
    private val includeFailedSteps: Boolean = true,
) : ToolExecutionRecordingListener {
    private val pendingTools = ConcurrentHashMap<String, PendingToolExecution>()
    private val eventIdsByTool = ConcurrentHashMap<String, MutableList<String>>()

    fun onAgentEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.ToolRequested -> {
                eventIdsByTool.compute(event.tool + event.timestampMillis) { _, list ->
                    (list ?: mutableListOf()).also { it += event.id }
                }
            }
            is AgentEvent.ToolRunning -> {
                val key = toolKey(event.tool, event.timestampMillis)
                pendingTools[key] = PendingToolExecution(
                    tool = event.tool,
                    args = event.args,
                    source = event.source,
                    startedAtMillis = event.timestampMillis,
                    eventIds = eventIdsByTool.remove(key)?.toList() ?: listOf(event.id),
                )
            }
            is AgentEvent.ToolSucceeded -> handleToolResult(event.tool, event.message, event.data, true, event.id, event.timestampMillis)
            is AgentEvent.ToolFailed -> handleToolResult(event.tool, event.message, event.data, false, event.id, event.timestampMillis)
            else -> Unit
        }
    }

    override fun onBeforeExecution(
        tool: String,
        args: Map<String, String>,
        source: ToolSource,
        before: ScreenContext,
    ) {
        val key = activeKey(tool)
        pendingTools.compute(key) { _, existing ->
            existing ?: PendingToolExecution(
                tool = tool,
                args = args,
                source = source,
                startedAtMillis = System.currentTimeMillis(),
                beforeFrame = capturer.captureFromContext(DemonstrationCapturePhase.BEFORE_ACTION, before),
            )
        }?.let { pending ->
            if (pending.beforeFrame == null) {
                pendingTools[key] = pending.copy(
                    beforeFrame = capturer.captureFromContext(DemonstrationCapturePhase.BEFORE_ACTION, before),
                )
            }
        }
    }

    override fun onAfterExecution(
        tool: String,
        args: Map<String, String>,
        source: ToolSource,
        before: ScreenContext,
        after: ScreenContext,
        result: ToolResult,
    ) {
        val key = activeKey(tool)
        val pending = pendingTools.remove(key) ?: PendingToolExecution(
            tool = tool,
            args = args,
            source = source,
            startedAtMillis = System.currentTimeMillis(),
            beforeFrame = capturer.captureFromContext(DemonstrationCapturePhase.BEFORE_ACTION, before),
        )

        if (!result.ok && !includeFailedSteps) return

        val beforeFrame = pending.beforeFrame
            ?: capturer.captureFromContext(DemonstrationCapturePhase.BEFORE_ACTION, before)
        val afterFrame = capturer.captureFromContext(DemonstrationCapturePhase.AFTER_ACTION, after)

        val verificationStatus = result.data["verification_status"]
        val verificationReason = result.data["verification_reason"]

        val action = DemonstrationToolAction(
            tool = tool,
            args = SensitiveTextRedactor.redact(args),
            source = source.name.lowercase(),
            succeeded = result.ok,
            message = SensitiveTextRedactor.redact(result.message),
            resultData = SensitiveTextRedactor.redact(result.data),
            verificationStatus = verificationStatus,
            verificationReason = verificationReason?.let { SensitiveTextRedactor.redact(it) },
        )

        onStepCaptured(
            PendingStep(
                action = action,
                beforeFrame = beforeFrame,
                afterFrame = afterFrame,
                durationMillis = (System.currentTimeMillis() - pending.startedAtMillis).coerceAtLeast(0),
                eventIds = pending.eventIds,
            )
        )
    }

    private fun handleToolResult(
        tool: String,
        message: String,
        data: Map<String, String>,
        succeeded: Boolean,
        eventId: String,
        timestampMillis: Long,
    ) {
        val key = toolKey(tool, timestampMillis)
        val pending = pendingTools.remove(key) ?: return
        if (!succeeded && !includeFailedSteps) return

        val beforeFrame = pending.beforeFrame ?: capturer.capture(DemonstrationCapturePhase.BEFORE_ACTION)
        val afterFrame = capturer.capture(DemonstrationCapturePhase.AFTER_ACTION)

        val action = DemonstrationToolAction(
            tool = tool,
            args = SensitiveTextRedactor.redact(pending.args),
            source = pending.source.name.lowercase(),
            succeeded = succeeded,
            message = SensitiveTextRedactor.redact(message),
            resultData = SensitiveTextRedactor.redact(data),
            verificationStatus = data["verification_status"],
            verificationReason = data["verification_reason"]?.let { SensitiveTextRedactor.redact(it) },
        )

        onStepCaptured(
            PendingStep(
                action = action,
                beforeFrame = beforeFrame,
                afterFrame = afterFrame,
                durationMillis = (timestampMillis - pending.startedAtMillis).coerceAtLeast(0),
                eventIds = pending.eventIds + eventId,
            )
        )
    }

    fun reset() {
        pendingTools.clear()
        eventIdsByTool.clear()
    }

    private fun toolKey(tool: String, timestampMillis: Long): String = "$tool::$timestampMillis"
    private fun activeKey(tool: String): String = "$tool::active"

    data class PendingStep(
        val action: DemonstrationToolAction,
        val beforeFrame: DemonstrationScreenFrame,
        val afterFrame: DemonstrationScreenFrame,
        val durationMillis: Long,
        val eventIds: List<String>,
    )

    private data class PendingToolExecution(
        val tool: String,
        val args: Map<String, String>,
        val source: ToolSource,
        val startedAtMillis: Long,
        val beforeFrame: DemonstrationScreenFrame? = null,
        val eventIds: List<String> = emptyList(),
    )
}
