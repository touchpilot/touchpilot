package dev.touchpilot.app.localinference

/**
 * Shared command-route classification for the LiteRT command router baseline.
 *
 * Priority mirrors [dev.touchpilot.app.agent.IntentGate] for the supported
 * command families so open/tap targets containing navigation words route
 * correctly, while word-boundary checks avoid accidental substring hits such
 * as "feedback", "homemade", or "scrollbar".
 */
object CommandRouteClassifier {
    const val RouteIndexBack = 0
    const val RouteIndexHome = 1
    const val RouteIndexScrollUp = 2
    const val RouteIndexScrollDown = 3
    const val RouteIndexOpenApp = 4
    const val RouteIndexTapText = 5

    val RouteLabels = listOf(
        "back",
        "home",
        "scroll_up",
        "scroll_down",
        "open_app",
        "tap_text"
    )

    data class CommandRoute(
        val tool: String,
        val args: Map<String, String> = emptyMap(),
        val labelIndex: Int
    )

    fun classify(task: String, dispatchedTools: Set<String> = emptySet()): CommandRoute? {
        val normalized = task.trim().lowercase()
        if (normalized.isBlank()) return null

        OpenAppPattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { target ->
                if ("open_app" !in dispatchedTools) {
                    return CommandRoute("open_app", mapOf("target" to target), RouteIndexOpenApp)
                }
            }

        TapTextPattern.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            ?.let { text ->
                if ("tap" !in dispatchedTools) {
                    return CommandRoute("tap", mapOf("text" to text), RouteIndexTapText)
                }
            }

        if (ScrollUpPattern.containsMatchIn(normalized) && "scroll" !in dispatchedTools) {
            return CommandRoute("scroll", mapOf("direction" to "backward"), RouteIndexScrollUp)
        }
        if (ScrollDownPattern.containsMatchIn(normalized) && "scroll" !in dispatchedTools) {
            return CommandRoute("scroll", mapOf("direction" to "forward"), RouteIndexScrollDown)
        }

        if (BackPattern.containsMatchIn(normalized) && "press_back" !in dispatchedTools) {
            return CommandRoute("press_back", labelIndex = RouteIndexBack)
        }
        if (HomePattern.containsMatchIn(normalized) && "press_home" !in dispatchedTools) {
            return CommandRoute("press_home", labelIndex = RouteIndexHome)
        }

        return null
    }

    fun buildFeatureVector(task: String, dispatchedTools: Set<String> = emptySet()): FloatArray {
        val features = FloatArray(RouteLabels.size)
        classify(task, dispatchedTools)?.let { route ->
            features[route.labelIndex] = 1f
        }
        return features
    }

    fun toCommandJson(route: CommandRoute): String {
        val argsJson = route.args.entries.joinToString(separator = ",") { (key, value) ->
            """"$key":"${escapeJson(value)}""""
        }
        return """{"tool":"${route.tool}","args":{$argsJson}}"""
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }

    private val OpenAppPattern = Regex("(?:open|launch|start)\\s+([\\w .-]+)")
    private val TapTextPattern = Regex("(?:tap|press|click)\\s+([\\w .-]+)")
    private val ScrollUpPattern = Regex("(?:\\bscroll\\s+up\\b|\\bswipe\\s+up\\b)")
    private val ScrollDownPattern = Regex(
        "(?:\\bscroll\\s+down\\b|\\bswipe\\s+down\\b|\\bscroll\\s+back\\b|\\bscroll\\b)"
    )
    private val BackPattern = Regex(
        "(?:\\bback\\b|\\bnavigate\\s+back\\b|\\bprevious\\s+(?:screen|page)\\b)"
    )
    private val HomePattern = Regex(
        "(?:\\bhome\\b|\\bgo\\s+home\\b|\\bhome\\s+screen\\b|\\btake\\s+me\\s+home\\b)"
    )
}
