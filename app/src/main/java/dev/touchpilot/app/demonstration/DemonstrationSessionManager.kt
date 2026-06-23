package dev.touchpilot.app.demonstration

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.demonstration.export.DemonstrationExporter
import dev.touchpilot.app.demonstration.recording.DemonstrationRecorder
import dev.touchpilot.app.demonstration.storage.DemonstrationStore
import java.io.File

/**
 * Manages the lifecycle of demonstration recording sessions, coordinating the
 * recorder, in-memory store, and optional auto-export.
 */
class DemonstrationSessionManager(
    private var config: DemonstrationRecordingConfig,
    private val store: DemonstrationStore = DemonstrationStore(),
    private val exporter: DemonstrationExporter? = null,
) {
    private val recorder = DemonstrationRecorder(config = config)

    val isEnabled: Boolean
        get() = config.enabled

    val isRecording: Boolean
        get() = recorder.isRecording

    val toolExecutionListener
        get() = recorder.toolExecutionListener

    val sessions: List<DemonstrationSession>
        get() = store.all()

    fun updateConfig(newConfig: DemonstrationRecordingConfig) {
        config = newConfig
    }

    fun beginRun(
        runId: String,
        task: String,
        skillId: String? = null,
        providerMode: String? = null,
    ): AgentEvent.DemonstrationRecordingStarted? {
        if (!config.enabled) return null
        val session = recorder.startSession(runId, task, skillId, providerMode) ?: return null
        return AgentEvent.DemonstrationRecordingStarted(
            sessionId = session.sessionId,
            runId = runId,
        )
    }

    fun onAgentEvent(event: AgentEvent) {
        recorder.onAgentEvent(event)
    }

    fun completeRun(
        stopReason: AgentStepStopReason?,
        errorMessage: String? = null,
    ): DemonstrationCompletion? {
        if (!recorder.isRecording) return null

        val status = when {
            errorMessage != null -> DemonstrationStatus.FAILED
            stopReason == AgentStepStopReason.USER_CANCELLED -> DemonstrationStatus.CANCELLED
            stopReason == AgentStepStopReason.COMPLETED -> DemonstrationStatus.COMPLETED
            else -> DemonstrationStatus.FAILED
        }

        val session = recorder.finishSession(
            status = status,
            stopReason = stopReason?.name,
            errorMessage = errorMessage,
        ) ?: return null

        store.record(session, config.maxStoredSessions)

        val exportFile = if (config.autoExport) {
            exporter?.let { runCatching { it.export(session) }.getOrNull() }
        } else {
            null
        }

        return DemonstrationCompletion(
            session = session,
            exportFile = exportFile,
        )
    }

    fun cancelRun(): DemonstrationSession? {
        val session = recorder.cancelSession() ?: return null
        store.record(session, config.maxStoredSessions)
        return session
    }

    fun findSession(sessionId: String): DemonstrationSession? = store.find(sessionId)
    fun findByRunId(runId: String): DemonstrationSession? = store.findByRunId(runId)

    fun exportSession(sessionId: String): File? {
        val session = store.find(sessionId) ?: return null
        return exporter?.export(session)
    }

    data class DemonstrationCompletion(
        val session: DemonstrationSession,
        val exportFile: File?,
    )
}
