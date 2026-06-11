package dev.touchpilot.app.localinference

import dev.touchpilot.app.agent.AgentCommand
import dev.touchpilot.app.agent.AgentCommandParser
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk
import org.json.JSONArray
import org.json.JSONObject

data class LocalModelRequest(
    val task: String,
    val context: String,
    val activeSkillId: String?,
    val tools: List<LocalModelToolContract>,
    val skill: LocalModelSkillContext? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
            .put("task", task)
            .put("context", context)
            .put("active_skill_id", activeSkillId)
            .put(
                "tools",
                JSONArray(
                    tools.map { tool ->
                        JSONObject()
                            .put("name", tool.name)
                            .put("description", tool.description)
                            .put("risk", tool.risk.name.lowercase())
                            .put("arguments", JSONObject(tool.arguments))
                            .put("required_arguments", JSONArray(tool.requiredArguments.toList()))
                    }
                )
            )
        skill?.let { json.put("skill", it.toJson()) }
        return json
    }

    companion object {
        fun from(task: String, context: String, skill: Skill?): LocalModelRequest {
            val tools = AndroidToolCatalog.initialTools
                .filter { tool -> skill == null || tool.name in skill.allowedTools }
                .map { tool ->
                    LocalModelToolContract(
                        name = tool.name,
                        description = tool.description,
                        risk = tool.risk,
                        arguments = tool.arguments,
                        requiredArguments = tool.requiredArguments
                    )
                }
            return LocalModelRequest(
                task = task,
                context = context,
                activeSkillId = skill?.id,
                tools = tools,
                skill = skill?.let { LocalModelSkillContext.from(it) }
            )
        }
    }
}

/**
 * Advisory skill context handed to the local model alongside the tool
 * contracts. It describes what the active skill is for and what success looks
 * like; it does not enforce anything. The allowlist, validation, approvals, and
 * policy layers remain the mandatory enforcement boundaries.
 */
data class LocalModelSkillContext(
    val id: String,
    val title: String,
    val description: String,
    val risk: String,
    val allowedTools: List<String>,
    val examples: List<String>,
    val successCriteria: List<String>,
    /** Human-written skill guidance (the SKILL.md body), mirroring the prompt path. */
    val instructions: String
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("description", description)
            .put("risk", risk)
            .put("allowed_tools", JSONArray(allowedTools))
            .put("examples", JSONArray(examples))
            .put("success_criteria", JSONArray(successCriteria))
            .put("instructions", instructions)
    }

    companion object {
        fun from(skill: Skill): LocalModelSkillContext {
            return LocalModelSkillContext(
                id = skill.id,
                title = skill.title,
                description = skill.description,
                risk = skill.risk.name.lowercase(),
                allowedTools = skill.allowedTools.toList(),
                examples = skill.examples,
                successCriteria = skill.successCriteria,
                instructions = skill.markdown
            )
        }
    }
}

data class LocalModelToolContract(
    val name: String,
    val description: String,
    val risk: ToolRisk,
    val arguments: Map<String, String>,
    val requiredArguments: Set<String>
)

sealed class LocalModelOutput {
    abstract fun toCommandJson(): String

    data class ToolCall(
        val tool: String,
        val args: Map<String, String>
    ) : LocalModelOutput() {
        override fun toCommandJson(): String {
            val argsJson = args.entries.joinToString(separator = ",") { (key, value) ->
                """"$key":"${escapeJson(value)}""""
            }
            return """{"tool":"$tool","args":{$argsJson}}"""
        }
    }

    data class FinalAnswer(
        val text: String
    ) : LocalModelOutput() {
        override fun toCommandJson(): String {
            return """{"final":"${escapeJson(text)}"}"""
        }
    }

    protected fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
    }
}

object LocalModelOutputParser {
    fun parse(raw: String): LocalModelOutput {
        val command = AgentCommandParser.parse(raw)
        return command.toLocalModelOutput()
    }

    fun AgentCommand.toLocalModelOutput(): LocalModelOutput {
        return when {
            finalAnswer != null && tool == null -> LocalModelOutput.FinalAnswer(finalAnswer)
            finalAnswer == null && tool != null -> LocalModelOutput.ToolCall(tool, args)
            finalAnswer != null && tool != null -> error("Local model output cannot include both tool and final.")
            else -> error("Local model output must include either tool or final.")
        }
    }
}

sealed class LocalModelOutputValidation {
    data class Valid(val output: LocalModelOutput) : LocalModelOutputValidation()
    data class Invalid(val reason: String) : LocalModelOutputValidation()
}

object LocalModelOutputValidator {
    fun validate(output: LocalModelOutput, skill: Skill?): LocalModelOutputValidation {
        if (output is LocalModelOutput.FinalAnswer) {
            return LocalModelOutputValidation.Valid(output)
        }

        val toolCall = output as LocalModelOutput.ToolCall
        val validationError = AndroidToolCatalog.validate(toolCall.tool, toolCall.args)
        if (validationError != null) {
            return LocalModelOutputValidation.Invalid(validationError)
        }

        val spec = AndroidToolCatalog.find(toolCall.tool)
        if (spec?.risk == ToolRisk.BLOCKED) {
            return LocalModelOutputValidation.Invalid("${toolCall.tool} is blocked by tool catalog.")
        }

        if (skill != null && toolCall.tool !in skill.allowedTools) {
            return LocalModelOutputValidation.Invalid("${toolCall.tool} is not allowed by ${skill.title}.")
        }

        return LocalModelOutputValidation.Valid(output)
    }
}
