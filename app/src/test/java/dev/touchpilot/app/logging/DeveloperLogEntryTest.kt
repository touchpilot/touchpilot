package dev.touchpilot.app.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperLogEntryTest {
    @Test
    fun detailSections_excludesHeaderAndMetadataAlreadyShownInDialog() {
        val entry = DeveloperLogEntry(
            type = "chat",
            actor = "User",
            name = "user_message",
            status = "info",
            source = "user_action",
            result = "hi",
            payloadSummary = "hi",
            details = "chat_activity=user_message"
        )

        val sections = entry.detailSections()

        assertEquals(2, sections.size)
        assertEquals("Message", sections[0].title)
        assertEquals("hi", sections[0].body)
        assertEquals("Log details", sections[1].title)
        assertEquals("chat_activity=user_message", sections[1].body)
        assertTrue(entry.detailText().contains("Message"))
        assertTrue(!entry.detailText().contains("Metadata"))
    }

    @Test
    fun detailSections_includesTargetAndPolicyDecisionForCapabilityAudit() {
        val entry = DeveloperLogEntry(
            type = "capability",
            actor = "TouchPilot",
            name = "call_tool",
            status = "ok",
            source = "mcp",
            result = "Called echo",
            payloadSummary = "tool=echo",
            target = "http://localhost:8080",
            policyDecision = "allow: Granted call tool",
        )

        val sections = entry.detailSections()

        assertTrue(sections.any { it.title == "Target" && it.body.contains("localhost") })
        assertTrue(sections.any { it.title == "Policy decision" && it.body.startsWith("allow") })
    }

    @Test
    fun detailSections_includesDistinctPayloadAndErrorSections() {
        val entry = DeveloperLogEntry(
            type = "tool",
            actor = "TouchPilot",
            name = "tap",
            status = "fail",
            source = "local_router",
            result = "tap failed",
            errorDetails = "node not found",
            payloadSummary = "{\"node_id\":\"42\"}"
        )

        val sections = entry.detailSections()

        assertEquals(3, sections.size)
        assertEquals("Message", sections[0].title)
        assertEquals("Payload", sections[1].title)
        assertEquals("Error details", sections[2].title)
    }
}
