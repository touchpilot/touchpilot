package dev.touchpilot.app.workflow

import dev.touchpilot.app.tools.AndroidToolCatalog
import org.json.JSONObject

/**
 * Parses workflow JSON files into [WorkflowDefinition] models.
 *
 * Validation fails closed: unknown tools, invalid arguments, schema version
 * mismatches, and dangling parameter placeholders all produce
 * [WorkflowParseResult.Invalid] with every problem collected.
 */
object WorkflowDefinitionParser {
    private val IdPattern = Regex("^[a-z0-9-]+$")
    private val knownTools: Set<String> = AndroidToolCatalog.initialTools.map { it.name }.toSet()

    fun parse(json: JSONObject): WorkflowParseResult {
        val id = json.optString("id", "unknown")
        val errors = mutableListOf<String>()

        val version = json.optInt("version", WorkflowDefinition.CURRENT_VERSION)
        if (version != WorkflowDefinition.CURRENT_VERSION) {
            errors += "unsupported version: $version (expected ${WorkflowDefinition.CURRENT_VERSION})"
        }

        if (json.optString("id").isBlank()) {
            errors += "missing required field: id"
        } else if (!IdPattern.matches(json.getString("id"))) {
            errors += "id '${json.getString("id")}' must use only a-z, 0-9, and '-'"
        }

        if (json.optString("title").isBlank()) {
            errors += "missing required field: title"
        }

        val stepsArray = json.optJSONArray("steps")
        if (stepsArray == null || stepsArray.length() == 0) {
            errors += "missing or empty required field: steps"
        }

        if (errors.isNotEmpty()) {
            return WorkflowParseResult.Invalid(id, errors)
        }

        return try {
            val definition = WorkflowDefinition.fromJson(json)
            validateDefinition(definition)
        } catch (error: IllegalArgumentException) {
            WorkflowParseResult.Invalid(id, listOf(error.message ?: error.toString()))
        }
    }

    fun parse(jsonText: String): WorkflowParseResult {
        return try {
            parse(JSONObject(jsonText))
        } catch (error: Exception) {
            WorkflowParseResult.Invalid(
                id = "unknown",
                errors = listOf("invalid JSON: ${error.message ?: error.toString()}"),
            )
        }
    }

    private fun validateDefinition(definition: WorkflowDefinition): WorkflowParseResult {
        val errors = mutableListOf<String>()
        val parameterNames = definition.parameters.map { it.name }.toSet()

        definition.skillScope?.allowedTools?.forEach { tool ->
            if (tool !in knownTools) {
                errors += "unknown tool in skill_scope.allowed_tools: $tool"
            }
        }

        definition.steps.forEach { step ->
            if (step.tool !in knownTools) {
                errors += "step '${step.id}': unknown tool '${step.tool}'"
            }

            step.args.forEach { (argName, argValue) ->
                val placeholder = WorkflowParameters.placeholderName(argValue)
                if (placeholder != null) {
                    if (placeholder !in parameterNames) {
                        errors += "step '${step.id}': arg '$argName' references undefined parameter '$placeholder'"
                    }
                }
            }

            val resolvedArgs = resolveArgsForValidation(step.args, definition.parameters)
            AndroidToolCatalog.validate(step.tool, resolvedArgs)?.let { message ->
                errors += "step '${step.id}': $message"
            }
        }

        return if (errors.isEmpty()) {
            WorkflowParseResult.Valid(definition)
        } else {
            WorkflowParseResult.Invalid(definition.id, errors)
        }
    }

    private fun resolveArgsForValidation(
        args: Map<String, String>,
        parameters: List<WorkflowParameter>,
    ): Map<String, String> {
        val defaults = parameters.mapNotNull { param ->
            param.default?.let { param.name to it }
        }.toMap()
        return WorkflowParameters.substitute(args, defaults)
    }
}
