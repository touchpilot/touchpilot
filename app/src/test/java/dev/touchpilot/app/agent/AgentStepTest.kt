package dev.touchpilot.app.agent

import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolVerificationResult
import dev.touchpilot.app.tools.ToolVerificationStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentStepTest {

    @Test
    fun observeStepRedactsInputAndOutputOnConstruction() {
        val step = AgentStepFactory.observe(
            sequenceNumber = 0,
            inputSummary = "user task",
            outputSummary = "snapshot password: hunter2 found",
            startedAtMillis = 1_000L,
            endedAtMillis = 1_100L,
        )
        assertEquals(AgentStepType.OBSERVE, step.type)
        assertEquals(AgentStepStatus.OK, step.status)
        assertFalse(step.outputSummary.contains("hunter2"))
        assertEquals(100L, step.durationMillis)
    }

    @Test
    fun decideStepProducesDecideTypeWithOkStatusByDefault() {
        val step = AgentStepFactory.decide(
            sequenceNumber = 1,
            inputSummary = "screen summary",
            outputSummary = "chose open_app",
            startedAtMillis = 2_000L,
        )
        assertEquals(AgentStepType.DECIDE, step.type)
        assertEquals(AgentStepStatus.OK, step.status)
        assertNull(step.endedAtMillis)
        assertNull(step.durationMillis)
    }

    @Test
    fun actStepCarriesToolCallAndRedactsArgs() {
        val step = AgentStepFactory.act(
            sequenceNumber = 2,
            tool = "type_text",
            args = mapOf("text" to "password: hunter2"),
            source = "local_router",
            result = ToolResult(ok = true, message = "typed", data = emptyMap()),
            startedAtMillis = 3_000L,
            endedAtMillis = 3_500L,
        )
        assertEquals(AgentStepType.ACT, step.type)
        assertEquals(AgentStepStatus.OK, step.status)
        val call = assertNotNull(step.toolCall)
        assertEquals("type_text", call.tool)
        assertFalse(call.args.values.any { it.contains("hunter2") },
            "raw secret leaked into tool args: ${call.args}")
        assertEquals(true, call.result?.ok)
    }

    @Test
    fun actStepWithoutResultStaysRunning() {
        val step = AgentStepFactory.act(
            sequenceNumber = 0,
            tool = "tap",
            args = mapOf("text" to "Save"),
            source = "local_router",
            startedAtMillis = 4_000L,
        )
        assertEquals(AgentStepStatus.RUNNING, step.status)
        assertNull(step.toolCall?.result)
    }

    @Test
    fun actStepWithFailedResultMarksFailed() {
        val step = AgentStepFactory.act(
            sequenceNumber = 0,
            tool = "tap",
            args = mapOf("text" to "Save"),
            source = "local_router",
            result = ToolResult(ok = false, message = "Ambiguous input target", data = mapOf("candidate_count" to "3")),
        )
        assertEquals(AgentStepStatus.FAILED, step.status)
        assertEquals(false, step.toolCall?.result?.ok)
        assertEquals("3", step.toolCall?.result?.data?.get("candidate_count"))
    }

    @Test
    fun verifyStepMirrorsToolVerificationStatus() {
        val passed = AgentStepFactory.verify(
            sequenceNumber = 3,
            verification = ToolVerificationResult.Passed("screen changed"),
        )
        val failed = AgentStepFactory.verify(
            sequenceNumber = 4,
            verification = ToolVerificationResult.Failed("nothing changed"),
        )
        val skipped = AgentStepFactory.verify(
            sequenceNumber = 5,
            verification = ToolVerificationResult.Skipped("read-only"),
        )
        assertEquals(AgentStepStatus.OK, passed.status)
        assertEquals(AgentStepStatus.FAILED, failed.status)
        assertEquals(AgentStepStatus.PENDING, skipped.status)
        assertEquals(ToolVerificationStatus.PASSED.wireName, passed.verification?.status)
    }

    @Test
    fun clarifyStepRequiresClarificationMetadata() {
        assertFails {
            AgentStep(
                sequenceNumber = 0,
                type = AgentStepType.CLARIFY,
                status = AgentStepStatus.CLARIFIED,
                inputSummary = "",
                outputSummary = "",
                startedAtMillis = 0L,
            )
        }
    }

    @Test
    fun clarifyFactoryStoresReasonAndRedactsQuestion() {
        val step = AgentStepFactory.clarify(
            sequenceNumber = 6,
            clarification = AgentStepClarification(
                reason = AgentStepClarificationReason.MULTIPLE_TARGETS,
                question = "Which Save did you mean: Save or Save and exit?",
                detail = "Ambiguous input target: 3 candidates with password: hunter2",
                candidateLabels = listOf("Save", "Save and exit"),
            ),
        )
        assertEquals(AgentStepType.CLARIFY, step.type)
        assertEquals(AgentStepStatus.CLARIFIED, step.status)
        assertTrue(step.isTerminal)
        val clarification = assertNotNull(step.clarification)
        assertEquals(AgentStepClarificationReason.MULTIPLE_TARGETS, clarification.reason)
        assertFalse(clarification.detail.contains("hunter2"))
        assertEquals(listOf("Save", "Save and exit"), clarification.candidateLabels)
    }

    @Test
    fun stopFactorySetsReasonAndStatus() {
        val step = AgentStepFactory.stop(
            sequenceNumber = 7,
            reason = AgentStepStopReason.MAX_STEPS,
            outputSummary = "ran out of steps",
            startedAtMillis = 5_000L,
            endedAtMillis = 5_010L,
        )
        assertEquals(AgentStepType.STOP, step.type)
        assertEquals(AgentStepStatus.STOPPED, step.status)
        assertEquals(AgentStepStopReason.MAX_STEPS, step.stopReason)
        assertTrue(step.isTerminal)
        assertEquals(10L, step.durationMillis)
    }

    @Test
    fun stopRequiresStopReason() {
        assertFails {
            AgentStep(
                sequenceNumber = 0,
                type = AgentStepType.STOP,
                status = AgentStepStatus.STOPPED,
                inputSummary = "",
                outputSummary = "",
                startedAtMillis = 0L,
            )
        }
    }

    @Test
    fun actRequiresToolCallMetadata() {
        assertFails {
            AgentStep(
                sequenceNumber = 0,
                type = AgentStepType.ACT,
                status = AgentStepStatus.RUNNING,
                inputSummary = "",
                outputSummary = "",
                startedAtMillis = 0L,
            )
        }
    }

    @Test
    fun negativeSequenceNumberIsRejected() {
        assertFails {
            AgentStepFactory.observe(
                sequenceNumber = -1,
                inputSummary = "",
                outputSummary = "",
            )
        }
    }

    @Test
    fun endedBeforeStartedIsRejected() {
        assertFails {
            AgentStepFactory.observe(
                sequenceNumber = 0,
                inputSummary = "",
                outputSummary = "",
                startedAtMillis = 2_000L,
                endedAtMillis = 1_000L,
            )
        }
    }

    @Test
    fun completedMutatesEndAndStatus() {
        val running = AgentStepFactory.act(
            sequenceNumber = 0,
            tool = "open_app",
            args = mapOf("target" to "Settings"),
            source = "local_router",
            startedAtMillis = 100L,
        )
        val done = running.completed(
            status = AgentStepStatus.OK,
            endedAtMillis = 200L,
            outputSummary = "opened settings with password: hunter2",
        )
        assertEquals(AgentStepStatus.OK, done.status)
        assertEquals(200L, done.endedAtMillis)
        assertFalse(done.outputSummary.contains("hunter2"))
    }

    @Test
    fun jsonShapeIncludesAllRequiredFields() {
        val step = AgentStepFactory.act(
            sequenceNumber = 0,
            tool = "tap",
            args = mapOf("text" to "Save"),
            source = "local_router",
            result = ToolResult(ok = true, message = "tap", data = mapOf("selector" to "text=Save")),
            startedAtMillis = 1_000L,
            endedAtMillis = 1_050L,
        )
        val json = step.toJson(redactSensitive = true)
        assertEquals(0, json.getInt("sequence_number"))
        assertEquals("act", json.getString("type"))
        assertEquals("ok", json.getString("status"))
        assertEquals(50L, json.getLong("duration_millis"))
        val toolJson = json.getJSONObject("tool_call")
        assertEquals("tap", toolJson.getString("tool"))
        val args = toolJson.getJSONObject("args")
        assertEquals("Save", args.getString("text"))
        val resultJson = toolJson.getJSONObject("result")
        assertEquals(true, resultJson.getBoolean("ok"))
    }

    @Test
    fun jsonRedactionHidesSensitiveFields() {
        val step = AgentStepFactory.act(
            sequenceNumber = 0,
            tool = "type_text",
            args = mapOf("text" to "password: hunter2"),
            source = "local_router",
            result = ToolResult(ok = true, message = "typed password: hunter2", data = mapOf()),
        )
        val redacted = step.toJson(redactSensitive = true).toString()
        assertFalse(redacted.contains("hunter2"), "secret leaked: $redacted")
    }

    @Test
    fun stopReasonAppearsInJson() {
        val step = AgentStepFactory.stop(
            sequenceNumber = 1,
            reason = AgentStepStopReason.POLICY_BLOCKED,
            outputSummary = "blocked",
        )
        val json = step.toJson()
        assertEquals("policy_blocked", json.getString("stop_reason"))
        assertEquals("stop", json.getString("type"))
    }

    @Test
    fun clarificationJsonIncludesCandidateLabels() {
        val step = AgentStepFactory.clarify(
            sequenceNumber = 2,
            clarification = AgentStepClarification(
                reason = AgentStepClarificationReason.MISSING_TARGET,
                question = "I couldn't find that — did you mean Settings or Wi-Fi?",
                candidateLabels = listOf("Settings", "Wi-Fi"),
            ),
        )
        val json = step.toJson()
        val clarification = json.getJSONObject("clarification")
        assertEquals("missing_target", clarification.getString("reason"))
        val labels = clarification.getJSONArray("candidate_labels")
        assertEquals(2, labels.length())
        assertEquals("Settings", labels.getString(0))
    }

    @Test
    fun typeStatusAndStopReasonWireNamesRoundTrip() {
        AgentStepType.values().forEach { type ->
            assertEquals(type, AgentStepType.fromWire(type.wireName))
        }
        AgentStepStatus.values().forEach { status ->
            assertEquals(status, AgentStepStatus.fromWire(status.wireName))
        }
        AgentStepStopReason.values().forEach { reason ->
            assertEquals(reason, AgentStepStopReason.fromWire(reason.wireName))
        }
        AgentStepClarificationReason.values().forEach { reason ->
            assertEquals(reason, AgentStepClarificationReason.fromWire(reason.wireName))
        }
    }

    @Test
    fun unknownWireValuesParseAsNull() {
        assertNull(AgentStepType.fromWire("nope"))
        assertNull(AgentStepStatus.fromWire(null))
        assertNull(AgentStepStopReason.fromWire(""))
        assertNull(AgentStepClarificationReason.fromWire("invalid"))
    }

    @Test
    fun isTerminalIsTrueOnlyForStopAndClarify() {
        AgentStepType.values().forEach { type ->
            val terminal = when (type) {
                AgentStepType.STOP, AgentStepType.CLARIFY -> true
                else -> false
            }
            val step = buildMinimalStep(type)
            assertEquals(terminal, step.isTerminal, "isTerminal mismatch for $type")
        }
    }

    @Test
    fun summariesAreRedactedEvenWhenAlreadyRedactedAtSource() {
        // Defense in depth: even if the caller passed a non-redacted string,
        // factory functions apply the redactor on construction.
        val step = AgentStepFactory.observe(
            sequenceNumber = 0,
            inputSummary = "leaked api_key=ABC123XYZ098",
            outputSummary = "leaked password: ABC123XYZ098",
        )
        assertContains(step.inputSummary, "[REDACTED]")
        assertContains(step.outputSummary, "[REDACTED]")
        assertFalse(step.inputSummary.contains("ABC123XYZ098"))
        assertFalse(step.outputSummary.contains("ABC123XYZ098"))
    }

    private fun buildMinimalStep(type: AgentStepType): AgentStep = when (type) {
        AgentStepType.OBSERVE -> AgentStepFactory.observe(0, "", "")
        AgentStepType.DECIDE -> AgentStepFactory.decide(0, "", "")
        AgentStepType.ACT -> AgentStepFactory.act(
            sequenceNumber = 0,
            tool = "tap",
            args = emptyMap(),
            source = "local_router",
        )
        AgentStepType.VERIFY -> AgentStepFactory.verify(
            sequenceNumber = 0,
            verification = ToolVerificationResult.Passed("ok"),
        )
        AgentStepType.CLARIFY -> AgentStepFactory.clarify(
            sequenceNumber = 0,
            clarification = AgentStepClarification(
                reason = AgentStepClarificationReason.NEEDS_USER_CHOICE,
                question = "ask",
            ),
        )
        AgentStepType.STOP -> AgentStepFactory.stop(
            sequenceNumber = 0,
            reason = AgentStepStopReason.COMPLETED,
            outputSummary = "done",
        )
    }
}
