package dev.touchpilot.app.agent

import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AgentEventTest {
    @Test
    fun serializesUserAndAssistantMessages() {
        val event = AgentEvent.UserMessage("Hello")
        val json = event.toJson()

        assertEquals("user_message", json.getString("type"))
        assertEquals("Hello", json.getJSONObject("payload").getString("text"))

        val assistant = AgentEvent.AssistantMessage("Done.", "Local router")
        val assistantJson = assistant.toJson()

        assertEquals("assistant_message", assistantJson.getString("type"))
        assertEquals("Done.", assistantJson.getJSONObject("payload").getString("text"))
        assertEquals("Local router", assistantJson.getJSONObject("payload").getString("detail"))

        val withChoices = AgentEvent.AssistantMessage(
            text = "Which item should I tap?",
            detail = "ambiguous",
            choices = listOf("Save", "Cancel")
        )
        val choicesJson = withChoices.toJson().getJSONObject("payload").getJSONArray("choices")
        assertEquals(2, choicesJson.length())
        assertEquals("Save", choicesJson.getString(0))
        assertEquals("Cancel", choicesJson.getString(1))
    }

    @Test
    fun representsToolRequestAndRunningEventsFromCommand() {
        val command = AgentCommand(
            tool = "open_app",
            args = mapOf("target" to "Settings"),
            finalAnswer = null
        )

        val requested = assertNotNull(AgentEvent.toolRequested(command, ToolSource.LOCAL_ROUTER))
        val running = assertNotNull(AgentEvent.toolRunning(command, ToolSource.LOCAL_ROUTER))

        assertEquals(AgentEventType.TOOL_REQUESTED, requested.type)
        assertEquals(AgentEventType.TOOL_RUNNING, running.type)
        assertEquals("open_app", requested.toJson().getJSONObject("payload").getString("tool"))
        assertEquals("Settings", requested.toJson().getJSONObject("payload").getJSONObject("args").getString("target"))
    }

    @Test
    fun representsToolResultAsSuccessOrFailureEvent() {
        val success = AgentEvent.toolResult(
            tool = "open_app",
            result = ToolResult(ok = true, message = "openApp", data = mapOf("target" to "Settings"))
        )
        val failure = AgentEvent.toolResult(
            tool = "tap",
            result = ToolResult(ok = false, message = "Target not found")
        )

        assertIs<AgentEvent.ToolSucceeded>(success)
        assertEquals("tool_succeeded", success.toJson().getString("type"))
        assertIs<AgentEvent.ToolFailed>(failure)
        assertEquals("tool_failed", failure.toJson().getString("type"))
    }

    @Test
    fun representsApprovalAndPolicyBlockEvents() {
        val spec = assertNotNull(AndroidToolCatalog.find("open_app"))
        val approval = PolicyDecision.RequireApproval(
            reason = "medium risk Android action",
            userMessage = "Approval required.",
            dataAffected = "The current Android app may change.",
            ifApproved = "TouchPilot will open Settings."
        )
        val approvalEvent = AgentEvent.approvalRequired(
            ToolApprovalRequest(
                tool = spec,
                args = mapOf("target" to "Settings"),
                policy = approval
            )
        )

        assertEquals("approval_required", approvalEvent.toJson().getString("type"))
        assertEquals("open_app", approvalEvent.toJson().getJSONObject("payload").getString("tool"))

        val blocked = AgentEvent.policyBlocked(
            tool = "type_text",
            decision = PolicyDecision.Block(
                reason = "password or secret entry is blocked",
                userMessage = "TouchPilot will not enter secrets."
            )
        )

        assertNotNull(blocked)
        assertEquals("policy_blocked", blocked.toJson().getString("type"))
        assertEquals("type_text", blocked.toJson().getJSONObject("payload").getString("tool"))
    }

    @Test
    fun finalAnswerEventCanBeCreatedFromCommand() {
        val event = assertNotNull(
            AgentEvent.finalAnswer(
                AgentCommand(tool = null, args = emptyMap(), finalAnswer = "Done.")
            )
        )

        assertEquals(AgentEventType.FINAL_ANSWER, event.type)
        assertEquals("Done.", event.toJson().getJSONObject("payload").getString("text"))
    }

    @Test
    fun serializationRedactsSensitivePayloadByDefault() {
        val event = AgentEvent.ToolRequested(
            tool = "type_text",
            args = mapOf("text" to "password=hunter2"),
            source = ToolSource.LOCAL_MODEL
        )

        val redacted = event.toJson().getJSONObject("payload").getJSONObject("args").getString("text")
        val raw = event.toJson(redactSensitive = false).getJSONObject("payload").getJSONObject("args").getString("text")

        assertEquals("[REDACTED]", redacted)
        assertEquals("password=hunter2", raw)
        assertFalse("hunter2" in event.toJson().toString())
        assertTrue("hunter2" in event.toJson(redactSensitive = false).toString())
    }

    @Test
    fun runCancelledEventSerializesCorrectly() {
        val event = AgentEvent.RunCancelled(reason = "Cancelled by user")
        val json = event.toJson()

        assertEquals("run_cancelled", json.getString("type"))
        assertEquals("Cancelled by user", json.getJSONObject("payload").getString("reason"))
        assertEquals(AgentEventType.RUN_CANCELLED, event.type)
    }
}
