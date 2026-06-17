package dev.touchpilot.app.localinference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandRouteClassifierTest {
    @Test
    fun classifiesSupportedCommandsFromFixtures() {
        val cases = listOf(
            Triple("Go back", "press_back", emptyMap()),
            Triple("navigate back", "press_back", emptyMap()),
            Triple("go home", "press_home", emptyMap()),
            Triple("scroll up", "scroll", mapOf("direction" to "backward")),
            Triple("scroll down", "scroll", mapOf("direction" to "forward")),
            Triple("scroll back", "scroll", mapOf("direction" to "forward")),
            Triple("open settings", "open_app", mapOf("target" to "settings")),
            Triple("click Wi-Fi", "tap", mapOf("text" to "wi-fi"))
        )

        cases.forEach { (task, expectedTool, expectedArgs) ->
            val route = CommandRouteClassifier.classify(task)
            assertEquals(expectedTool, route?.tool, task)
            assertEquals(expectedArgs, route?.args.orEmpty(), task)
        }
    }

    @Test
    fun rejectsAccidentalSubstringMatches() {
        assertNull(CommandRouteClassifier.classify("give feedback"))
        assertNull(CommandRouteClassifier.classify("buy homemade pizza"))
        assertNull(CommandRouteClassifier.classify("the scrollbar is broken"))
    }

    @Test
    fun buildFeatureVectorSetsSingleActiveLabel() {
        val features = CommandRouteClassifier.buildFeatureVector("open settings")
        assertEquals(1f, features[CommandRouteClassifier.RouteIndexOpenApp])
        assertEquals(0f, features.sum() - 1f)
    }
}
