package dev.touchpilot.app.demonstration

import dev.touchpilot.app.demonstration.analysis.DemonstrationScreenDeltaCalculator
import dev.touchpilot.app.demonstration.analysis.DemonstrationScreenFingerprint
import dev.touchpilot.app.demonstration.recording.DemonstrationRecorder
import dev.touchpilot.app.demonstration.recording.DemonstrationScreenCapturer
import dev.touchpilot.app.demonstration.serialization.DemonstrationJsonCodec
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemonstrationRecorderTest {
    private val screenA = ScreenContext(
        packageName = "com.example",
        windowTitle = "Home",
        nodes = listOf(
            node("btn-1", "Settings", NodeRole.BUTTON),
            node("txt-1", "Welcome", NodeRole.TEXT),
        ),
    )
    private val screenB = ScreenContext(
        packageName = "com.example",
        windowTitle = "Settings",
        nodes = listOf(
            node("btn-2", "Wi-Fi", NodeRole.BUTTON),
            node("txt-2", "Network", NodeRole.TEXT),
        ),
    )

    @Test
    fun startsAndFinishesSessionWhenEnabled() {
        var seq = 0
        val capturer = DemonstrationScreenCapturer(
            observeScreen = { if (seq++ % 2 == 0) screenA else screenB },
            clock = { 1_000L + seq },
        )
        val recorder = DemonstrationRecorder(
            config = DemonstrationRecordingConfig(enabled = true),
            capturer = capturer,
            clock = { 5_000L },
        )

        val started = recorder.startSession("run-1", "open settings")
        assertNotNull(started)
        assertTrue(recorder.isRecording)

        val listener = recorder.toolExecutionListener
        listener.onBeforeExecution("tap", mapOf("text" to "Settings"), ToolSource.LOCAL_ROUTER, screenA)
        listener.onAfterExecution(
            "tap", mapOf("text" to "Settings"), ToolSource.LOCAL_ROUTER,
            screenA, screenB, ToolResult(ok = true, message = "tapped"),
        )

        val finished = recorder.finishSession(DemonstrationStatus.COMPLETED, stopReason = "COMPLETED")
        assertNotNull(finished)
        assertFalse(recorder.isRecording)
        assertEquals(1, finished.steps.size)
        assertEquals("tap", finished.steps[0].action.tool)
        assertNotNull(finished.steps[0].screenDelta)
    }

    @Test
    fun doesNotStartWhenDisabled() {
        val recorder = DemonstrationRecorder(config = DemonstrationRecordingConfig(enabled = false))
        assertEquals(null, recorder.startSession("run-1", "task"))
        assertFalse(recorder.isRecording)
    }

    @Test
    fun roundTripsThroughJsonCodec() {
        val capturer = DemonstrationScreenCapturer(observeScreen = { screenA }, clock = { 1_000L })
        val recorder = DemonstrationRecorder(
            config = DemonstrationRecordingConfig(enabled = true),
            capturer = capturer,
        )
        recorder.startSession("run-2", "tap wifi")
        recorder.toolExecutionListener.onAfterExecution(
            "tap", mapOf("text" to "Wi-Fi"), ToolSource.LOCAL_ROUTER,
            screenA, screenB, ToolResult(ok = true, message = "ok"),
        )
        val session = recorder.finishSession(DemonstrationStatus.COMPLETED)!!

        val json = DemonstrationJsonCodec.encode(session)
        val restored = DemonstrationJsonCodec.decode(json)

        assertEquals(session.sessionId, restored.sessionId)
        assertEquals(session.steps.size, restored.steps.size)
        assertEquals("tap", restored.steps[0].action.tool)
    }

    @Test
    fun fingerprintDiffersBetweenScreens() {
        val fpA = DemonstrationScreenFingerprint.compute(screenA)
        val fpB = DemonstrationScreenFingerprint.compute(screenB)
        assertFalse(fpA == fpB)
        val similarity = DemonstrationScreenFingerprint.similarity(screenA, screenB)
        assertTrue(similarity < 1.0)
    }

    @Test
    fun deltaCalculatorDetectsChanges() {
        val before = DemonstrationScreenFrame.capture(0, DemonstrationCapturePhase.BEFORE_ACTION, 1L, screenA)
        val after = DemonstrationScreenFrame.capture(1, DemonstrationCapturePhase.AFTER_ACTION, 2L, screenB)
        val delta = DemonstrationScreenDeltaCalculator.compute(before, after)
        assertTrue(delta.hasChanges)
        assertTrue(delta.windowTitleChanged)
    }

    private fun node(id: String, text: String, role: NodeRole): ScreenNode {
        return ScreenNode(
            nodeId = id,
            role = role,
            text = ScreenText.of(text),
            clickable = role == NodeRole.BUTTON,
        )
    }
}
