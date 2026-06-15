package dev.touchpilot.app.eval

import dev.touchpilot.app.tools.targets.TargetRankingEvalFixtureParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalModelEvalRunnerTest {
    @Test
    fun committedFixturesPassMinimalLocalModelEvalSet() {
        val report = LocalModelEvalRunner().run(
            targetRankingCases = loadTargetRankingCases(),
            commandRoutingCases = loadCommandRoutingCases(),
        )

        println(report.formatSummary())

        assertTrue(report.totalCases > 0, report.formatSummary())
        assertEquals(0, report.failedCases, report.formatSummary())
        assertTrue(report.allPassed, report.formatSummary())
    }

    @Test
    fun parsesCommittedCommandRoutingFixtures() {
        val cases = loadCommandRoutingCases()

        assertTrue(cases.isNotEmpty())
        assertEquals("go_back", cases.first().id)
    }

    private fun loadTargetRankingCases() =
        TargetRankingEvalFixtureParser.parse(loadFixtureText("target-ranking/fixtures.json"))

    private fun loadCommandRoutingCases() =
        CommandRoutingEvalFixtureParser.parse(loadFixtureText("command-routing/fixtures.json"))

    private fun loadFixtureText(relativePath: String): String {
        val candidates = listOf(
            File("src/test/resources/$relativePath"),
            File("app/src/test/resources/$relativePath"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing fixture file: $relativePath")
        return file.readText()
    }
}
