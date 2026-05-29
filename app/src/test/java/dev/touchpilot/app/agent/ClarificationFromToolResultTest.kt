package dev.touchpilot.app.agent

import dev.touchpilot.app.tools.ToolResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClarificationFromToolResultTest {
    @Test
    fun parsesAmbiguousToolResultWithCandidateLabels() {
        val result = ToolResult(
            ok = false,
            message = "Ambiguous input target: multiple matches",
            data = mapOf(
                "clarification_needed" to "true",
                "candidate_count" to "2",
                "candidate_label_0" to "Save",
                "candidate_label_1" to "password=hunter2",
            )
        )

        val prompt = assertNotNull(ClarificationFromToolResult.parse(result))
        assertEquals("Which field should I use?", prompt.question)
        assertEquals(listOf("Save", "password=[REDACTED]"), prompt.choices)
        assertFalse(prompt.choices.any { it.contains("hunter2") })
    }

    @Test
    fun ignoresNonAmbiguousFailures() {
        val result = ToolResult(ok = false, message = "Input target not found")
        assertNull(ClarificationFromToolResult.parse(result))
    }

    @Test
    fun dataFromCandidateLabelsRoundTrips() {
        val data = ClarificationFromToolResult.dataFromCandidateLabels(listOf("A", "B"))
        val parsed = ClarificationFromToolResult.parse(
            ToolResult(ok = false, message = "Ambiguous scroll target: tied", data = data)
        )
        assertNotNull(parsed)
        assertEquals(listOf("A", "B"), parsed.choices)
    }

    @Test
    fun redactsSensitiveChoiceLabels() {
        val data = ClarificationFromToolResult.dataFromCandidateLabels(listOf("password=secret-value"))
        val parsed = assertNotNull(
            ClarificationFromToolResult.parse(
                ToolResult(ok = false, message = "Ambiguous input target: tied", data = data)
            )
        )
        assertFalse(parsed.choices.any { it.contains("secret-value") })
    }
}
