package dev.touchpilot.app.workflow

import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk

/**
 * Converts a captured [WorkflowTrace] (#295) into a portable [WorkflowDefinition].
 *
 * The serializer infers parameter slots when a tool argument value appears in
 * the redacted task string, replaces those literals with `{parameter}`
 * placeholders, and derives expected-state predicates from step arguments and
 * verification outcomes. Full screen text is intentionally not read from the
 * trace — #295 stores only coarse screen signals for privacy.
 */
object WorkflowTraceSerializer {
    fun toDefinition(
        trace: WorkflowTrace,
        workflowId: String = slugify(trace.task),
        title: String = trace.task,
    ): WorkflowDefinition {
        val inferredParameters = inferParameters(trace)
        val parameterNames = inferredParameters.map { it.name }.toSet()

        val steps = trace.steps.mapIndexed { index, step ->
            val args = parameterizeArgs(step.args, trace.task, parameterNames, inferredParameters)
            WorkflowStep(
                id = "step-${index + 1}",
                tool = step.tool,
                args = args,
                expectedState = expectedStateFromStep(step),
                policy = policyFromTraceStep(step),
                description = "Replay step ${index + 1}: ${step.tool}",
            )
        }

        val skillScope = if (trace.skillId != null || trace.allowedTools.isNotEmpty()) {
            WorkflowSkillScope(
                skillId = trace.skillId,
                allowedTools = trace.allowedTools,
            )
        } else {
            null
        }

        return WorkflowDefinition(
            id = workflowId,
            title = title,
            description = trace.task,
            parameters = inferredParameters,
            skillScope = skillScope,
            steps = steps,
        )
    }

    private fun inferParameters(trace: WorkflowTrace): List<WorkflowParameter> {
        val task = trace.task
        if (task.isBlank()) return emptyList()

        val seen = linkedSetOf<String>()
        val parameters = mutableListOf<WorkflowParameter>()

        trace.steps.forEach { step ->
            step.args.values.forEach { value ->
                if (value.isBlank() || WorkflowParameters.isPlaceholder(value)) return@forEach
                if (!task.contains(value, ignoreCase = true)) return@forEach

                val name = uniqueParameterName(slugifyArgValue(value), seen)
                seen += name
                parameters += WorkflowParameter(
                    name = name,
                    description = "Value inferred from the original task",
                    default = value,
                    required = true,
                )
            }
        }

        return parameters
    }

    private fun parameterizeArgs(
        args: Map<String, String>,
        task: String,
        knownNames: Set<String>,
        parameters: List<WorkflowParameter>,
    ): Map<String, String> {
        if (task.isBlank() && parameters.isEmpty()) return args

        val valueToName = parameters.associate { it.default.orEmpty() to it.name }
        return args.mapValues { (_, value) ->
            val paramName = valueToName[value]
            if (paramName != null && paramName in knownNames) {
                WorkflowParameters.placeholder(paramName)
            } else {
                value
            }
        }
    }

    private fun expectedStateFromStep(step: WorkflowTraceStep): WorkflowExpectedState? {
        val tappedText = step.args["text"]?.takeIf { it.isNotBlank() }
        val verificationReason = step.verification?.reason?.takeIf { it.isNotBlank() }

        val elementPredicates = tappedText?.let {
            listOf(
                WorkflowElementPredicate(
                    text = it,
                    match = WorkflowTextMatch.CONTAINS,
                ),
            )
        }.orEmpty()

        val screenText = buildList {
            tappedText?.let { add(it) }
            verificationReason?.let { add(it) }
        }.distinct()

        if (elementPredicates.isEmpty() && screenText.isEmpty()) return null

        return WorkflowExpectedState(
            screenTextContains = screenText,
            elementPresent = elementPredicates,
        )
    }

    private fun policyFromTraceStep(step: WorkflowTraceStep): WorkflowStepPolicy? {
        val requiresApproval = step.requiresApproval ?: toolRequiresApproval(step.tool)
        val workflowClass = step.workflowClass
        if (!requiresApproval && workflowClass == null) return null
        return WorkflowStepPolicy(
            requiresApproval = requiresApproval,
            workflowClass = workflowClass,
        )
    }

    private fun toolRequiresApproval(tool: String): Boolean {
        val risk = AndroidToolCatalog.find(tool)?.risk ?: return false
        return risk == ToolRisk.MEDIUM || risk == ToolRisk.HIGH
    }

    private fun uniqueParameterName(base: String, used: Set<String>): String {
        if (base !in used) return base
        var index = 2
        while ("${base}_$index" in used) {
            index += 1
        }
        return "${base}_$index"
    }

    private fun slugifyArgValue(value: String): String {
        val normalized = value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val candidate = normalized.ifBlank { "value" }
        return if (WorkflowParameters.isValidName(candidate)) {
            candidate
        } else {
            "param_$candidate".filter { it.isLetterOrDigit() || it == '_' }
                .trim('_')
                .ifBlank { "value" }
        }
    }

    fun slugify(value: String): String {
        return value
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "workflow" }
    }
}
