package dev.touchpilot.app.tools.targets

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetRankingEvaluatorTest {
    @Test
    fun parsesCommittedFixtures() {
        val cases = loadFixtureCases()

        assertEquals(4, cases.size)
        assertEquals("settings_exact_text", cases.first().id)
    }

    @Test
    fun committedFixturesPassDeterministicRankingEval() {
        val report = TargetRankingEvaluator().evaluate(loadFixtureCases())

        println(report.formatSummary())

        assertEquals(4, report.totalCases)
        assertEquals(0, report.failedCases, report.formatSummary())
        assertTrue(report.results.all { it.rankedNodeIds.isNotEmpty() }, report.formatSummary())
    }

    private fun loadFixtureCases(): List<TargetRankingEvalCase> {
        val candidates = listOf(
            File("src/test/resources/target-ranking/fixtures.json"),
            File("app/src/test/resources/target-ranking/fixtures.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing target-ranking fixtures.json")
        return TargetRankingEvalFixtureParser.parse(file.readText())
    }
}
