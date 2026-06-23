package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DemonstrationAgentEventTest {
    @Test
    fun serializesDemonstrationRecordingEvents() {
        val started = AgentEvent.DemonstrationRecordingStarted(
            sessionId = "demo-session-1",
            runId = "run-1",
        )
        val startedJson = started.toJson()
        assertEquals("demonstration_recording_started", startedJson.getString("type"))
        assertEquals("demo-session-1", startedJson.getJSONObject("payload").getString("session_id"))

        val captured = AgentEvent.DemonstrationStepCaptured(
            sessionId = "demo-session-1",
            stepIndex = 1,
            tool = "tap",
            screenDeltaSummary = "2 nodes added",
            durationMillis = 150L,
        )
        val capturedJson = captured.toJson().getJSONObject("payload")
        assertEquals(1, capturedJson.getInt("step_index"))
        assertEquals("tap", capturedJson.getString("tool"))

        val finished = AgentEvent.DemonstrationRecordingFinished(
            sessionId = "demo-session-1",
            runId = "run-1",
            stepCount = 3,
            status = "completed",
        )
        assertIs<AgentEvent.DemonstrationRecordingFinished>(finished)
        assertEquals(3, finished.toJson().getJSONObject("payload").getInt("step_count"))
    }
}
