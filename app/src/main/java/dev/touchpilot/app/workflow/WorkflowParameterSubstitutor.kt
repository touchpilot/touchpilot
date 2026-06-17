package dev.touchpilot.app.workflow

object WorkflowParameterSubstitutor {
    private val PLACEHOLDER = Regex("\\{([^}]+)}")

    fun resolveParameters(
        definition: WorkflowDefinition,
        supplied: Map<String, String>,
    ): Map<String, String> {
        val resolved = linkedMapOf<String, String>()
        for (parameter in definition.parameters) {
            val value = supplied[parameter.name] ?: parameter.default
            if (value == null && parameter.required) {
                error("Missing required workflow parameter: ${parameter.name}")
            }
            if (value != null) {
                resolved[parameter.name] = value
            }
        }
        supplied.forEach { (name, value) ->
            if (name !in resolved) {
                resolved[name] = value
            }
        }
        return resolved
    }

    fun substitute(value: String, parameters: Map<String, String>): String {
        return PLACEHOLDER.replace(value) { match ->
            parameters[match.groupValues[1]] ?: match.value
        }
    }

    fun substitute(args: Map<String, String>, parameters: Map<String, String>): Map<String, String> {
        return args.mapValues { (_, value) -> substitute(value, parameters) }
    }
}
