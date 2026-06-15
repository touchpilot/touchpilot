package dev.touchpilot.app.eval

import dev.touchpilot.app.memory.Skill
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
    fun returnsFinalWhenRouteDoesNotMatch() {
        val result = CommandRoutingEvaluator().evaluateCase(
            CommandRoutingEvalCase(
                id = "unmatched",
                description = "",
                task = "Find the Wi-Fi toggle",
                expectedFinalContains = "Local router completed",
            )
        )

        assertTrue(result.passed)
        assertEquals(null, result.actualTool)
    }

    @Test
    fun skillAllowlistBlocksDisallowedTool() {
        val result = CommandRoutingEvaluator().evaluateCase(
            CommandRoutingEvalCase(
                id = "blocked",
                description = "",
                task = "go back",
                expectedFinalContains = "Local router completed",
                skill = Skill(
                    id = "tap_only",
                    title = "Tap only",
                    markdown = "",
                    allowedTools = setOf("tap"),
                ),
            )
        )

        assertTrue(result.passed)
    }

    @Test
    fun parsesCommittedFixtures() {
        val json = """
            {
              "version": 1,
              "cases": [
                {
                  "id": "go_back",
                  "task": "Go back",
                  "expectation": { "tool": "press_back" }
                }
              ]
            }
        """.trimIndent()

        val cases = CommandRoutingEvalFixtureParser.parse(json)

        assertEquals(1, cases.size)
        assertEquals("press_back", cases.first().expectedTool)
    }

    @Test
    fun committedFixturesMatchLocalRouterOutput() {
        val file = listOf(
            java.io.File("src/test/resources/command-routing/fixtures.json"),
            java.io.File("app/src/test/resources/command-routing/fixtures.json"),
        ).firstOrNull { it.isFile }
            ?: error("Missing command-routing fixtures.json")

        val report = CommandRoutingEvaluator().evaluate(
            CommandRoutingEvalFixtureParser.parse(file.readText())
        )

        println(report.formatSummary())

        assertEquals(0, report.failedCases, report.formatSummary())
    }
}
