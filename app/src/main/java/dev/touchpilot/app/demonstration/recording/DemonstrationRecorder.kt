package dev.touchpilot.app.demonstration.recording

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.demonstration.DemonstrationCapturePhase
import dev.touchpilot.app.demonstration.DemonstrationRecordingConfig
import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.DemonstrationSessionIds
import dev.touchpilot.app.demonstration.DemonstrationStatus
import dev.touchpilot.app.demonstration.DemonstrationStep
import dev.touchpilot.app.demonstration.analysis.DemonstrationScreenDeltaCalculator
import dev.touchpilot.app.security.SensitiveTextRedactor

/**
 * Core recorder for demonstration mode (issue #302). Captures each user/agent
 * action with tool calls, arguments, and screen state before/after each step.
 */
class DemonstrationRecorder(
    private val config: DemonstrationRecordingConfig,
    private val capturer: DemonstrationScreenCapturer = DemonstrationScreenCapturer(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var activeSession: DemonstrationSession? = null
    private val steps = mutableListOf<DemonstrationStep>()
    private var eventBridge: DemonstrationEventBridge? = null

    val isRecording: Boolean
        get() = activeSession != null

    val currentSession: DemonstrationSession?
        get() = activeSession

    val toolExecutionListener: ToolExecutionRecordingListener
        get() = eventBridge ?: NoOpToolExecutionRecordingListener

    fun startSession(
        runId: String,
        task: String,
        skillId: String? = null,
        providerMode: String? = null,
    ): DemonstrationSession? {
        if (!config.enabled) return null

        capturer.reset()
        steps.clear()

        val initialFrame = capturer.capture(DemonstrationCapturePhase.RUN_INITIAL)
        val session = DemonstrationSession.create(
            sessionId = DemonstrationSessionIds.next(),
            runId = runId,
            task = task,
            startedAtMillis = clock(),
            skillId = skillId,
            providerMode = providerMode,
            initialFrame = initialFrame,
        )
        activeSession = session

        eventBridge = DemonstrationEventBridge(
            capturer = capturer,
            onStepCaptured = { pending -> appendStep(pending) },
            includeFailedSteps = config.includeFailedSteps,
        )

        return session
    }

    fun onAgentEvent(event: AgentEvent) {
        if (!isRecording) return
        eventBridge?.onAgentEvent(event)
    }

    fun finishSession(
        status: DemonstrationStatus,
        stopReason: String? = null,
        errorMessage: String? = null,
    ): DemonstrationSession? {
        val session = activeSession ?: return null
        val finalFrame = capturer.capture(DemonstrationCapturePhase.RUN_FINAL)

        val completed = session.copy(
            steps = steps.toList(),
            finalFrame = finalFrame,
        ).withCompleted(
            status = status,
            completedAtMillis = clock(),
            stopReason = stopReason,
            errorMessage = errorMessage,
            finalFrame = finalFrame,
        )

        activeSession = null
        eventBridge?.reset()
        eventBridge = null
        steps.clear()

        return completed
    }

    fun cancelSession(): DemonstrationSession? {
        return finishSession(
            status = DemonstrationStatus.CANCELLED,
            stopReason = "cancelled",
            errorMessage = "Recording cancelled by user",
        )
    }

    private fun appendStep(pending: DemonstrationEventBridge.PendingStep) {
        val delta = if (config.captureScreenDelta) {
            DemonstrationScreenDeltaCalculator.compute(pending.beforeFrame, pending.afterFrame)
        } else {
            null
        }

        steps += DemonstrationStep(
            index = steps.size + 1,
            action = pending.action,
            beforeFrame = pending.beforeFrame,
            afterFrame = pending.afterFrame,
            screenDelta = delta,
            durationMillis = pending.durationMillis,
            eventIds = pending.eventIds,
        )
    }

    fun snapshot(): DemonstrationSession? {
        val session = activeSession ?: return null
        return session.copy(steps = steps.toList())
    }

    fun stepCount(): Int = steps.size

    fun redactedTask(): String {
        return activeSession?.metadata?.task?.let { SensitiveTextRedactor.redact(it) }.orEmpty()
    }
}
