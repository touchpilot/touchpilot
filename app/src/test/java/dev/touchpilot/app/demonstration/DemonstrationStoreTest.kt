package dev.touchpilot.app.demonstration

import dev.touchpilot.app.demonstration.export.DemonstrationWorkflowConverter
import dev.touchpilot.app.demonstration.storage.DemonstrationStore
import dev.touchpilot.app.demonstration.storage.DemonstrationIndex
import dev.touchpilot.app.demonstration.storage.DemonstrationQuery
import dev.touchpilot.app.screen.NodeRole
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenNode
import dev.touchpilot.app.screen.ScreenText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemonstrationStoreTest {
    @Test
    fun storesAndRetrievesSessions() {
        val store = DemonstrationStore()
        val session = sampleSession("demo-1", "run-1")
        store.record(session)
        assertEquals(1, store.size)
        assertNotNull(store.find("demo-1"))
        assertNotNull(store.findByRunId("run-1"))
    }

    @Test
    fun trimsToMaxSessions() {
        val store = DemonstrationStore()
        repeat(5) { i ->
            store.record(sampleSession("demo-$i", "run-$i"), maxSessions = 3)
        }
        assertEquals(3, store.size)
    }

    @Test
    fun indexSearchFiltersByTool() {
        val index = DemonstrationIndex()
        val session = sampleSession("demo-1", "run-1")
        index.index(session)
        val results = index.search(DemonstrationQuery(tool = "tap"))
        assertEquals(1, results.size)
        assertEquals("demo-1", results[0].sessionId)
    }

    @Test
    fun validatorAcceptsCompleteSession() {
        val session = sampleSession("demo-1", "run-1")
        val result = DemonstrationValidator.validate(session)
        assertTrue(result.valid)
    }

    @Test
    fun statisticsAggregateSessions() {
        val sessions = listOf(
            sampleSession("demo-1", "run-1"),
            sampleSession("demo-2", "run-2"),
        )
        val stats = DemonstrationStatistics.compute(sessions)
        assertEquals(2, stats.sessionCount)
        assertEquals(2, stats.totalSteps)
        assertTrue(stats.toolUsageCounts.containsKey("tap"))
    }

    @Test
    fun convertsToWorkflowDefinition() {
        val session = sampleSession("demo-1", "run-1")
        val workflow = DemonstrationWorkflowConverter.toWorkflowDefinition(session)
        assertNotNull(workflow)
        assertEquals(1, workflow.steps.size)
        assertEquals("tap", workflow.steps[0].tool)
    }

    private fun sampleSession(sessionId: String, runId: String): DemonstrationSession {
        val screen = ScreenContext(
            packageName = "com.test",
            nodes = listOf(
                ScreenNode(nodeId = "n1", role = NodeRole.BUTTON, text = ScreenText.of("Go"), clickable = true),
            ),
        )
        val before = DemonstrationScreenFrame.capture(1, DemonstrationCapturePhase.BEFORE_ACTION, 100L, screen)
        val after = DemonstrationScreenFrame.capture(2, DemonstrationCapturePhase.AFTER_ACTION, 200L, screen)
        val step = DemonstrationStep(
            index = 1,
            action = DemonstrationToolAction(
                tool = "tap",
                args = mapOf("text" to "Go"),
                source = "local_router",
                succeeded = true,
                message = "ok",
            ),
            beforeFrame = before,
            afterFrame = after,
        )
        return DemonstrationSession.create(sessionId, runId, "tap Go", 100L)
            .copy(steps = listOf(step))
            .withCompleted(DemonstrationStatus.COMPLETED, 300L)
    }
}
