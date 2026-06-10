package dev.touchpilot.app.agent

import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.memory.Skill

object AgentPrompts {
    fun systemPrompt(skill: Skill?): String {
        val availableTools = AndroidToolCatalog.initialTools
            .filter { tool -> skill == null || tool.name in skill.allowedTools }
        val tools = availableTools.joinToString(separator = "\n") { tool ->
            "- ${tool.name}: ${tool.description} risk=${tool.risk} args=${tool.arguments}"
        }
        val skillContext = skill?.let { "\n\n" + skillContextBlock(it) }.orEmpty()

        return """
            You are TouchPilot, an Android phone-control agent.
            Return exactly one JSON object and no extra prose.

            If you need to act, return:
            {"tool":"tool_name","args":{"key":"value"}}

            If the task is complete or cannot be done safely, return:
            {"final":"short answer"}

            Available tools:
            $tools
            $skillContext

            Constraints:
            - Use observe_screen when you need current UI state.
            - Prefer stable node_id selectors from observe_screen when tapping exact UI nodes.
            - Use bounds only when text and node_id are not reliable enough.
            - After each action, inspect the verification screen before deciding the next step.
            - Do not send messages, buy things, enter passwords, or change sensitive settings.
            - Use one tool call at a time.
        """.trimIndent()
    }

    /**
     * Renders the active skill as advisory context for the command producer:
     * description, risk, allowed tools, examples, success criteria, and the
     * human-readable instructions. The content guides the model but enforces
     * nothing — tool validation, the skill allowlist, approvals, and policy
     * remain the mandatory boundaries. Empty optional fields are omitted so a
     * skill that carries no v2 metadata still produces a clean block.
     */
    private fun skillContextBlock(skill: Skill): String {
        val lines = mutableListOf<String>()
        lines += "Active skill: ${skill.title}"
        if (skill.description.isNotBlank()) {
            lines += "Description: ${skill.description}"
        }
        lines += "Risk: ${skill.risk.name.lowercase()}"
        if (skill.allowedTools.isNotEmpty()) {
            lines += "Allowed tools: ${skill.allowedTools.joinToString(", ")}"
        }
        if (skill.examples.isNotEmpty()) {
            lines += "Example requests:"
            skill.examples.forEach { lines += "- $it" }
        }
        if (skill.successCriteria.isNotEmpty()) {
            lines += "Success criteria:"
            skill.successCriteria.forEach { lines += "- $it" }
        }
        if (skill.markdown.isNotBlank()) {
            lines += "Instructions:"
            lines += skill.markdown
        }
        lines += "Only use tools allowed by the active skill allowlist."
        return lines.joinToString(separator = "\n")
    }
}
