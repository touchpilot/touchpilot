package dev.touchpilot.app.eval

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandRoutingEvaluatorTest {
    @Test
    fun routesPressBackForGoBackTask() {
        val result = CommandRoutingEvaluator().evaluateCase(
            CommandRoutingEvalCase(
                id = "go_back",
                description = "",
                task = "Go back",
                expectedTool = "press_back",
            )
        )

        assertTrue(result.passed)
        assertEquals("press_back", result.actualTool)
    }

    @Test
    fun parsesCommittedFixtures() {
        val cases = loadFixtureCases()

        assertTrue(cases.isNotEmpty())
        assertEquals("go_back", cases.first().id)
    }

    @Test
    fun committedFixturesPassDeterministicRoutingEval() {
        val report = CommandRoutingEvaluator().evaluate(loadFixtureCases())

        println(report.formatSummary())

        assertEquals(25, report.totalCases)
        assertEquals(0, report.failedCases, report.formatSummary())
        assertTrue(report.results.all { it.passed }, report.formatSummary())
    }

    private fun loadFixtureCases(): List<CommandRoutingEvalCase> {
        val candidates = listOf(
            File("src/test/resources/command-routing/fixtures.json"),
            File("app/src/test/resources/command-routing/fixtures.json")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing command-routing fixtures.json")
        return CommandRoutingEvalFixtureParser.parse(file.readText())
    }
}
