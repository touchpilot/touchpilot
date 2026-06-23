package dev.touchpilot.app.demonstration

import dev.touchpilot.app.demonstration.analysis.DemonstrationContextExtractor
import dev.touchpilot.app.demonstration.analysis.DemonstrationNodeChangeTracker
import dev.touchpilot.app.demonstration.formatting.DemonstrationSummaryFormatter
import dev.touchpilot.app.demonstration.formatting.DemonstrationTimelineFormatter
import dev.touchpilot.app.demonstration.playback.DemonstrationPlaybackEngine
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemonstrationAnalysisTest {
    private val screenA = ScreenContext(
        packageName = "com.example",
        appLabel = "Example",
        windowTitle = "Home",
        nodes = listOf(
            ScreenNode(nodeId = "1", role = NodeRole.BUTTON, text = ScreenText.of("Settings"), clickable = true),
            ScreenNode(nodeId = "2", role = NodeRole.TEXT, text = ScreenText.of("Hello")),
        ),
    )

    private val screenB = ScreenContext(
        packageName = "com.example",
        appLabel = "Example",
        windowTitle = "Settings",
        nodes = listOf(
            ScreenNode(nodeId = "3", role = NodeRole.BUTTON, text = ScreenText.of("Wi-Fi"), clickable = true),
            ScreenNode(nodeId = "4", role = NodeRole.TEXT, text = ScreenText.of("Network")),
        ),
    )

    @Test
    fun contextExtractorBuildsFeatures() {
        val features = DemonstrationContextExtractor.extractFeatures(screenA)
        assertEquals("com.example", features.packageName)
        assertEquals(2, features.nodeCount)
        assertEquals(1, features.clickableCount)
        assertTrue(features.clickableLabels.contains("Settings"))
    }

    @Test
    fun nodeChangeTrackerDetectsAdditions() {
        val tracker = DemonstrationNodeChangeTracker()
        tracker.record(screenA, "before")
        val report = tracker.record(screenB, "after")
        assertTrue(report.hasChanges)
        assertEquals(2, report.added.size)
        assertEquals(2, report.removed.size)
    }

    @Test
    fun timelineFormatterProducesReadableOutput() {
        val session = buildSampleSession()
        val timeline = DemonstrationTimelineFormatter.format(session)
        assertTrue(timeline.contains("Step 1: tap"))
        assertTrue(timeline.contains("Demonstration timeline"))
    }

    @Test
    fun summaryFormatterProducesCompletionMessage() {
        val session = buildSampleSession()
        val message = DemonstrationSummaryFormatter.completionMessage(session)
        assertTrue(message.contains("1 step"))
    }

    @Test
    fun playbackEngineEmitsEvents() {
        val session = buildSampleSession()
        val result = DemonstrationPlaybackEngine(stepDelayMillis = 0).play(session)
        assertEquals(1, result.stepsPlayed)
        assertTrue(result.events.any { it is dev.touchpilot.app.agent.AgentEvent.ToolSucceeded })
    }

    private fun buildSampleSession(): DemonstrationSession {
        val before = DemonstrationScreenFrame.capture(1, DemonstrationCapturePhase.BEFORE_ACTION, 100L, screenA)
        val after = DemonstrationScreenFrame.capture(2, DemonstrationCapturePhase.AFTER_ACTION, 200L, screenB)
        return DemonstrationSession.create("demo-1", "run-1", "open settings", 100L)
            .copy(
                steps = listOf(
                    DemonstrationStep(
                        index = 1,
                        action = DemonstrationToolAction(
                            tool = "tap",
                            args = mapOf("text" to "Settings"),
                            source = "local_router",
                            succeeded = true,
                            message = "ok",
                        ),
                        beforeFrame = before,
                        afterFrame = after,
                        durationMillis = 100L,
                    ),
                ),
            )
            .withCompleted(DemonstrationStatus.COMPLETED, 300L)
    }
}
