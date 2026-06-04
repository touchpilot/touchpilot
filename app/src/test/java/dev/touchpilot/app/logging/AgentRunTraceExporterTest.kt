package dev.touchpilot.app.logging

import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AgentRunTraceExporterTest {

    @Test
    fun buildContentContainsRunId() {
        val record = sampleRecord(id = "run-abc123")

        val content = AgentRunTraceExporter.buildContent(record)

        assertContains(content, "run_id=run-abc123")
    }

    @Test
    fun buildContentContainsTask() {
        val record = sampleRecord(task = "Open settings")

        val content = AgentRunTraceExporter.buildContent(record)

        assertContains(content, "task=Open settings")
    }

    @Test
    fun buildContentRedactsSensitiveTask() {
        val record = sampleRecord(task = "Submit form with password=hunter2 in the field")

        val content = AgentRunTraceExporter.buildContent(record)

        assertFalse(content.contains("hunter2"), "Sensitive text in task must be redacted")
    }

    @Test
    fun buildContentMatchesAgentRunDetailFormatterOutput() {
        val record = sampleRecord()

        val content = AgentRunTraceExporter.buildContent(record)

        assertEquals(AgentRunDetailFormatter.exportRedactedTrace(record), content)
    }

    @Test
    fun buildContentContainsHeader() {
        val record = sampleRecord()

        val content = AgentRunTraceExporter.buildContent(record)

        assertContains(content, "TouchPilot agent run trace")
    }

    private fun sampleRecord(
        id: String = "run-1",
        task: String = "Open settings"
    ): AgentRunRecord {
        return AgentRunRecord(
            id = id,
            task = task,
            startedAtMillis = 1_000L,
            completedAtMillis = 2_000L,
            result = AgentRunResult(
                transcript = "",
                finalAnswer = null,
                events = emptyList(),
                steps = emptyList(),
                stopReason = null,
                stopMessage = ""
            )
        )
    }
}
