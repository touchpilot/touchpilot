package dev.touchpilot.app.tools

import dev.touchpilot.app.androidcontrol.FlatCandidate
import dev.touchpilot.app.androidcontrol.FlatResolution
import dev.touchpilot.app.androidcontrol.TargetResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TargetResolverTest {

    private fun candidate(
        nodeId: String,
        label: String,
        query: String,
        isClickable: Boolean = true
    ) = FlatCandidate.of(nodeId, label, query, isClickable)

    @Test
    fun `resolves single exact clickable match`() {
        val candidates = listOf(candidate("0.0", "Settings", "Settings"))
        val result = TargetResolver.resolveFlat("Settings", candidates)
        assertIs<FlatResolution.Resolved>(result)
        assertEquals("0.0", result.candidate.nodeId)
    }

    @Test
    fun `returns ambiguous when two exact matches at same rank`() {
        val candidates = listOf(
            candidate("0.0", "Settings", "Settings"),
            candidate("0.1", "Settings", "Settings")
        )
        val result = TargetResolver.resolveFlat("Settings", candidates)
        assertIs<FlatResolution.Ambiguous>(result)
        assertEquals(2, result.candidates.size)
    }

    @Test
    fun `returns not found when list is empty`() {
        val result = TargetResolver.resolveFlat("Settings", emptyList())
        assertIs<FlatResolution.NotFound>(result)
        assertTrue("Settings" in result.selector)
    }

    @Test
    fun `prefers exact clickable over substring clickable`() {
        val candidates = listOf(
            candidate("0.0", "Settings and Privacy", "Settings"),
            candidate("0.1", "Settings", "Settings")
        )
        val result = TargetResolver.resolveFlat("Settings", candidates)
        assertIs<FlatResolution.Resolved>(result)
        assertEquals("Settings", result.candidate.label)
    }

    @Test
    fun `prefers exact clickable over exact non-clickable`() {
        val candidates = listOf(
            candidate("0.0", "Settings", "Settings", isClickable = false),
            candidate("0.1", "Settings", "Settings", isClickable = true)
        )
        val result = TargetResolver.resolveFlat("Settings", candidates)
        assertIs<FlatResolution.Resolved>(result)
        assertEquals("0.1", result.candidate.nodeId)
    }

    @Test
    fun `blank text returns not found`() {
        val result = TargetResolver.resolveFlat("", emptyList())
        assertIs<FlatResolution.NotFound>(result)
    }

    @Test
    fun `summarizeFlatCandidates truncates long labels`() {
        val longLabel = "A".repeat(60)
        val c = FlatCandidate.of("0.0", longLabel, "A", true)
        val summary = TargetResolver.summarizeFlatCandidates(listOf(c))
        assertTrue("…" in summary)
    }

    @Test
    fun `sensitive label is redacted in candidate summary`() {
        val sensitiveLabel = "password=hunter2 api_key=sk-secret-12345678"
        val c = FlatCandidate.of("0.0", sensitiveLabel, "password", true)
        val summary = TargetResolver.summarizeFlatCandidates(listOf(c))
        // Sensitive values must be replaced by [REDACTED], not merely truncated
        assertTrue("[REDACTED]" in summary, "Expected [REDACTED] in summary: $summary")
        assertTrue("hunter2" !in summary, "Raw secret leaked into summary: $summary")
        assertTrue("sk-secret-12345678" !in summary, "Raw api_key leaked into summary: $summary")
    }
}
