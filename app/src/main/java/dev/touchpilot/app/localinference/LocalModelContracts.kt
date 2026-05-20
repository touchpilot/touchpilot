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
    val tools: List<LocalModelToolContract>
) {
    fun toJson(): JSONObject {
        return JSONObject()
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
                tools = tools
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
