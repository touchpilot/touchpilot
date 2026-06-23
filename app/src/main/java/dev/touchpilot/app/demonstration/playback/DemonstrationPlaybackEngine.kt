package dev.touchpilot.app.demonstration.playback

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.demonstration.DemonstrationSession
import dev.touchpilot.app.demonstration.DemonstrationStep
import dev.touchpilot.app.demonstration.formatting.DemonstrationTimelineFormatter

/**
 * Replays a captured demonstration by emitting structured events for each step.
 * Does not re-execute tools — this is a review/playback mode for recorded demos.
 */
class DemonstrationPlaybackEngine(
    private val listener: AgentEventListener = AgentEventListener {},
    private val stepDelayMillis: Long = 0L,
    private val sleeper: (Long) -> Unit = { if (it > 0) Thread.sleep(it) },
) {
    fun play(session: DemonstrationSession): DemonstrationPlaybackResult {
        val startedAt = System.currentTimeMillis()
        val events = mutableListOf<AgentEvent>()

        listener.onEvent(
            AgentEvent.DemonstrationRecordingStarted(
                sessionId = session.sessionId,
                runId = session.runId,
            ).also { events += it }
        )

        session.steps.forEach { step ->
            if (stepDelayMillis > 0) sleeper(stepDelayMillis)
            events += emitStepEvents(step)
        }

        listener.onEvent(
            AgentEvent.DemonstrationRecordingFinished(
                sessionId = session.sessionId,
                runId = session.runId,
                stepCount = session.steps.size,
                status = session.metadata.status.wireName,
            ).also { events += it }
        )

        return DemonstrationPlaybackResult(
            sessionId = session.sessionId,
            stepsPlayed = session.steps.size,
            events = events,
            durationMillis = System.currentTimeMillis() - startedAt,
            timeline = DemonstrationTimelineFormatter.format(session),
        )
    }

    private fun emitStepEvents(step: DemonstrationStep): List<AgentEvent> {
        val events = mutableListOf<AgentEvent>()

        val requested = AgentEvent.ToolRequested(
            tool = step.action.tool,
            args = step.action.args,
            source = dev.touchpilot.app.security.ToolSource.LOCAL_ROUTER,
        )
        listener.onEvent(requested)
        events += requested

        val result = if (step.action.succeeded) {
            AgentEvent.ToolSucceeded(
                tool = step.action.tool,
                message = step.action.message,
                data = step.action.resultData,
            )
        } else {
            AgentEvent.ToolFailed(
                tool = step.action.tool,
                message = step.action.message,
                data = step.action.resultData,
            )
        }
        listener.onEvent(result)
        events += result

        val captured = AgentEvent.DemonstrationStepCaptured(
            sessionId = "",
            stepIndex = step.index,
            tool = step.action.tool,
            screenDeltaSummary = step.screenDelta?.summary,
            durationMillis = step.durationMillis,
        )
        listener.onEvent(captured)
        events += captured

        return events
    }
}

data class DemonstrationPlaybackResult(
    val sessionId: String,
    val stepsPlayed: Int,
    val events: List<AgentEvent>,
    val durationMillis: Long,
    val timeline: String,
)
