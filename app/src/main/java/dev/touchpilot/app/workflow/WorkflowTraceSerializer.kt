package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentScreenRecord
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolRisk

/**
 * Converts a successful [WorkflowTrace] into a portable [WorkflowDefinition].
 *
 * The serializer infers parameter slots when a tool argument value appears in
 * the original user task, replaces those literals with `{parameter}` placeholders,
 * and derives expected-state predicates from post-step screen snapshots.
 */
object WorkflowTraceSerializer {
    private const val MaxScreenTextSnippets = 5
    private const val MaxElementPredicates = 3

    fun toDefinition(
        trace: WorkflowTrace,
        workflowId: String = slugify(trace.title.ifBlank { trace.id }),
    ): WorkflowDefinition {
        val inferredParameters = inferParameters(trace)
        val parameterNames = inferredParameters.map { it.name }.toSet()

        val steps = trace.steps.mapIndexed { index, step ->
            val args = parameterizeArgs(step.args, trace.task, parameterNames, inferredParameters)
            WorkflowStep(
                id = "step-${index + 1}",
                tool = step.tool,
                args = args,
                expectedState = step.screenAfter?.let { expectedStateFromScreen(it, step) },
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
            title = trace.title,
            description = trace.description.ifBlank { trace.task },
            parameters = inferredParameters,
            skillScope = skillScope,
            steps = steps,
            expectedForegroundPackage = trace.steps.lastOrNull()?.screenAfter?.packageName,
        )
    }

    fun traceFromAgentRun(
        record: AgentRunRecord,
        steps: List<AgentStep>,
        title: String = record.task,
        workflowId: String = slugify(title),
        skillId: String? = null,
        allowedTools: List<String> = emptyList(),
    ): WorkflowTrace {
        val actSteps = steps.filter { it.type == AgentStepType.ACT && it.toolCall != null }
        val screenBySequence = record.screenRecords.associateBy { it.sequenceNumber }

        val traceSteps = actSteps.map { step ->
            val toolCall = step.toolCall!!
            val screenAfter = screenAfterActStep(step.sequenceNumber, screenBySequence)
            WorkflowTraceStep(
                sequenceNumber = step.sequenceNumber,
                tool = toolCall.tool,
                args = toolCall.args,
                source = toolCall.source,
                screenAfter = screenAfter,
                requiresApproval = toolRequiresApproval(toolCall.tool),
            )
        }

        return WorkflowTrace(
            id = workflowId,
            title = title,
            description = record.task,
            task = record.task,
            steps = traceSteps,
            skillId = skillId,
            allowedTools = allowedTools,
            startedAtMillis = record.startedAtMillis,
            completedAtMillis = record.completedAtMillis,
        )
    }

    private fun screenAfterActStep(
        actSequence: Int,
        screens: Map<Int, AgentScreenRecord>,
    ): ScreenContext? {
        val candidate = screens.entries
            .filter { (sequence, record) ->
                sequence > actSequence && record.phase.equals("after_tool", ignoreCase = true)
            }
            .minByOrNull { it.key }
            ?: screens.entries
                .filter { (sequence, _) -> sequence > actSequence }
                .minByOrNull { it.key }

        return candidate?.let { parseScreenRecord(it.value) }
    }

    private fun parseScreenRecord(record: AgentScreenRecord): ScreenContext? {
        return try {
            ScreenContext.fromJson(org.json.JSONObject(record.contextJson))
        } catch (_: Exception) {
            null
        }
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

    private fun expectedStateFromScreen(
        screen: ScreenContext,
        step: WorkflowTraceStep,
    ): WorkflowExpectedState {
        val tappedText = step.args["text"]?.takeIf { it.isNotBlank() }
        val elementPredicates = buildList {
            tappedText?.let {
                add(
                    WorkflowElementPredicate(
                        text = it,
                        match = WorkflowTextMatch.CONTAINS,
                    ),
                )
            }
            screen.nodes
                .asSequence()
                .filter { node ->
                    !node.sensitive &&
                        !node.text.isSensitive &&
                        node.text.displaySafe.isNotBlank()
                }
                .filter { node -> tappedText == null || node.text.displaySafe != tappedText }
                .sortedByDescending { node ->
                    when {
                        node.clickable -> 3
                        node.isInputField -> 2
                        node.role.name == "TEXT" -> 1
                        else -> 0
                    }
                }
                .take(MaxElementPredicates - size)
                .forEach { node ->
                    add(
                        WorkflowElementPredicate(
                            text = node.text.displaySafe,
                            match = WorkflowTextMatch.CONTAINS,
                        ),
                    )
                }
        }

        val screenText = screen.nodes
            .asSequence()
            .filter { node -> !node.sensitive && !node.text.isSensitive }
            .map { it.text.displaySafe }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MaxScreenTextSnippets)
            .toList()

        return WorkflowExpectedState(
            packageName = screen.packageName,
            windowTitle = screen.windowTitle,
            screenTextContains = screenText,
            elementPresent = elementPredicates,
        )
    }

    private fun policyFromTraceStep(step: WorkflowTraceStep): WorkflowStepPolicy? {
        val requiresApproval = step.requiresApproval
        val workflowClass = step.workflowClass
        if (requiresApproval == null && workflowClass == null) return null
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
