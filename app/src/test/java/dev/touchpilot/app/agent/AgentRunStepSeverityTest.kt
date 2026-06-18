package dev.touchpilot.app.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AgentRunStepSeverityTest {
    @Test
    fun mapsEachStatusToExpectedSeverity() {
        assertEquals(AgentRunStepSeverity.POSITIVE, AgentRunStepStatus.SUCCESS.severity)
        assertEquals(AgentRunStepSeverity.POSITIVE, AgentRunStepStatus.COMPLETE.severity)
        assertEquals(AgentRunStepSeverity.NEGATIVE, AgentRunStepStatus.FAILED.severity)
        assertEquals(AgentRunStepSeverity.CAUTION, AgentRunStepStatus.BLOCKED.severity)
        assertEquals(AgentRunStepSeverity.IN_PROGRESS, AgentRunStepStatus.RUNNING.severity)
        assertEquals(AgentRunStepSeverity.IN_PROGRESS, AgentRunStepStatus.WAITING.severity)
        assertEquals(AgentRunStepSeverity.IN_PROGRESS, AgentRunStepStatus.PENDING.severity)
        assertEquals(AgentRunStepSeverity.NEUTRAL, AgentRunStepStatus.INFO.severity)
    }

    @Test
    fun failedAndBlockedAreDistinctAndNotNeutral() {
        // The core #273 fix: a tool failure and a policy/safety block must be
        // highlighted distinctly, and neither may fall back to the neutral
        // (info) treatment the way they did before.
        assertNotEquals(AgentRunStepStatus.FAILED.severity, AgentRunStepStatus.BLOCKED.severity)
        assertNotEquals(AgentRunStepSeverity.NEUTRAL, AgentRunStepStatus.FAILED.severity)
        assertNotEquals(AgentRunStepSeverity.NEUTRAL, AgentRunStepStatus.BLOCKED.severity)
    }

    @Test
    fun everyStatusHasASeverity() {
        // Guards the mapping if a new status is added to the enum.
        AgentRunStepStatus.entries.forEach { it.severity }
    }
}
