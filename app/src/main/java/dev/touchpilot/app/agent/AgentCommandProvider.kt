package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.Skill

fun interface AgentCommandProvider {
    fun complete(systemPrompt: String, context: String): String
}

/**
 * Returns a pre-decided tool command on the first call and a final answer on
 * every call after. Used by [DefaultLocalReasoningCore] for
 * [IntentDecision.ExactCommand] so the tool/args picked by [IntentGate]
 * execute as-is — no second exact-command parser runs and the two cannot
 * drift.
 */
class FixedCommandProvider(
    private val command: AgentCommand,
    private val finalMessage: String = "Exact command completed."
) : AgentCommandProvider {
    private var emittedCommand = false

    override fun complete(systemPrompt: String, context: String): String {
        if (!emittedCommand) {
            emittedCommand = true
            return commandJson(command)
        }
        return """{"final":"${escapeJson(finalMessage)}"}"""
    }

    private fun commandJson(command: AgentCommand): String {
        val tool = command.tool ?: error("FixedCommandProvider requires command.tool")
        val argsJson = command.args.entries.joinToString(separator = ",") { (key, value) ->
            """"$key":"${escapeJson(value)}""""
        }
        return """{"tool":"$tool","args":{$argsJson}}"""
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}

class LocalRouterCommandProvider(
    private val task: String,
    private val skill: Skill?
) : AgentCommandProvider {
    private var hasObserved = false
    private var hasActed = false

    override fun complete(systemPrompt: String, context: String): String {
        if (!hasObserved) {
            hasObserved = true
            return """{"tool":"observe_screen","args":{}}"""
        }

        if (!hasActed) {
            val routed = routeTask(task)
            if (routed != null && isAllowed(routed.tool)) {
                hasActed = true
                return routed.toJson()
            }
        }

        return """{"final":"Local router completed its safe routing pass. Try a more specific request, a skill, or local model mode for ambiguous tasks."}"""
    }

    private fun routeTask(task: String): LocalRoute? {
        val normalized = task.trim().lowercase()
        if (normalized.isBlank()) return null

        if (normalized.contains("back")) return LocalRoute("press_back")
        if (normalized.contains("home")) return LocalRoute("press_home")
        if (normalized.contains("scroll up")) {
            return LocalRoute("scroll", mapOf("direction" to "backward"))
        }
        if (normalized.contains("scroll")) {
            return LocalRoute("scroll", mapOf("direction" to "forward"))
        }

        Regex("(?:open|launch)\\s+([\\w .-]+)")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { target ->
                return LocalRoute("open_app", mapOf("target" to target))
            }

        Regex("(?:tap|press)\\s+([\\w .-]+)")
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { text ->
                return LocalRoute("tap", mapOf("text" to text))
            }

        return null
    }

    private fun isAllowed(tool: String): Boolean {
        return skill == null || tool in skill.allowedTools
    }

    private data class LocalRoute(
        val tool: String,
        val args: Map<String, String> = emptyMap()
    ) {
        fun toJson(): String {
            val argsJson = args.entries.joinToString(separator = ",") { (key, value) ->
                """"$key":"${escapeJson(value)}""""
            }
            return """{"tool":"$tool","args":{$argsJson}}"""
        }

        private fun escapeJson(value: String): String {
            return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
        }
    }
}
