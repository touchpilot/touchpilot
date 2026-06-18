package dev.touchpilot.app.workflow

import dev.touchpilot.app.security.PolicyWorkflowClass
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable, parameterized description of a repeatable Android task workflow.
 *
 * Workflow files are local-first JSON documents (see docs/WORKFLOWS.md). They
 * capture the ordered tool sequence, optional expected screen predicates after
 * each step, parameter slots for replay, and per-step policy hints for the
 * replay engine.
 */
data class WorkflowDefinition(
    val version: Int = CURRENT_VERSION,
    val id: String,
    val title: String,
    val description: String = "",
    val parameters: List<WorkflowParameter> = emptyList(),
    val skillScope: WorkflowSkillScope? = null,
    val steps: List<WorkflowStep>,
    val expectedForegroundPackage: String? = null,
) {
    init {
        require(version == CURRENT_VERSION) {
            "unsupported workflow version: $version (expected $CURRENT_VERSION)"
        }
        require(id.isNotBlank()) { "workflow id must not be blank" }
        require(title.isNotBlank()) { "workflow title must not be blank" }
        require(steps.isNotEmpty()) { "workflow must contain at least one step" }
        val duplicateStepIds = steps.groupingBy { it.id }.eachCount().filter { it.value > 1 }.keys
        require(duplicateStepIds.isEmpty()) {
            "duplicate workflow step ids: ${duplicateStepIds.joinToString()}"
        }
        val duplicateParams = parameters.groupingBy { it.name }.eachCount().filter { it.value > 1 }.keys
        require(duplicateParams.isEmpty()) {
            "duplicate workflow parameter names: ${duplicateParams.joinToString()}"
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("version", version)
            put("id", id)
            put("title", title)
            put("description", description)
            put("parameters", JSONArray().apply { parameters.forEach { put(it.toJson()) } })
            put("skill_scope", skillScope?.toJson() ?: JSONObject.NULL)
            put("steps", JSONArray().apply { steps.forEach { put(it.toJson()) } })
            put("expected_foreground_package", expectedForegroundPackage ?: JSONObject.NULL)
        }
    }

    companion object {
        const val CURRENT_VERSION = 1

        fun fromJson(json: JSONObject): WorkflowDefinition {
            val version = json.optInt("version", CURRENT_VERSION)
            val parameters = mutableListOf<WorkflowParameter>()
            json.optJSONArray("parameters")?.let { array ->
                for (i in 0 until array.length()) {
                    parameters += WorkflowParameter.fromJson(array.getJSONObject(i))
                }
            }

            val steps = mutableListOf<WorkflowStep>()
            val stepsArray = json.getJSONArray("steps")
            for (i in 0 until stepsArray.length()) {
                steps += WorkflowStep.fromJson(stepsArray.getJSONObject(i))
            }

            val skillScope = json.optJSONObject("skill_scope")?.let { WorkflowSkillScope.fromJson(it) }

            return WorkflowDefinition(
                version = version,
                id = json.getString("id"),
                title = json.getString("title"),
                description = json.optString("description", ""),
                parameters = parameters,
                skillScope = skillScope,
                steps = steps,
                expectedForegroundPackage = json.optString("expected_foreground_package").takeIf { it.isNotBlank() },
            )
        }
    }
}

data class WorkflowParameter(
    val name: String,
    val description: String = "",
    val default: String? = null,
    val required: Boolean = false,
) {
    init {
        require(WorkflowParameters.isValidName(name)) {
            "parameter name '$name' must match [a-z][a-z0-9_]*"
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("description", description)
            put("default", default ?: JSONObject.NULL)
            put("required", required)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowParameter {
            return WorkflowParameter(
                name = json.getString("name"),
                description = json.optString("description", ""),
                default = json.optString("default").takeIf { it.isNotBlank() },
                required = json.optBoolean("required", false),
            )
        }
    }
}

data class WorkflowSkillScope(
    val skillId: String? = null,
    val allowedTools: List<String> = emptyList(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("skill_id", skillId ?: JSONObject.NULL)
            put("allowed_tools", JSONArray().apply { allowedTools.forEach { put(it) } })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowSkillScope {
            val tools = mutableListOf<String>()
            json.optJSONArray("allowed_tools")?.let { array ->
                for (i in 0 until array.length()) {
                    tools += array.getString(i)
                }
            }
            return WorkflowSkillScope(
                skillId = json.optString("skill_id").takeIf { it.isNotBlank() },
                allowedTools = tools,
            )
        }
    }
}

data class WorkflowStep(
    val id: String,
    val tool: String,
    val args: Map<String, String> = emptyMap(),
    val expectedState: WorkflowExpectedState? = null,
    val policy: WorkflowStepPolicy? = null,
    val description: String = "",
) {
    init {
        require(id.isNotBlank()) { "workflow step id must not be blank" }
        require(tool.isNotBlank()) { "workflow step tool must not be blank" }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("tool", tool)
            put("args", JSONObject(args))
            put("expected_state", expectedState?.toJson() ?: JSONObject.NULL)
            put("policy", policy?.toJson() ?: JSONObject.NULL)
            put("description", description)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowStep {
            val args = mutableMapOf<String, String>()
            json.optJSONObject("args")?.let { objectArgs ->
                objectArgs.keys().forEach { key ->
                    args[key] = objectArgs.getString(key)
                }
            }
            return WorkflowStep(
                id = json.getString("id"),
                tool = json.getString("tool"),
                args = args,
                expectedState = json.optJSONObject("expected_state")?.let { WorkflowExpectedState.fromJson(it) },
                policy = json.optJSONObject("policy")?.let { WorkflowStepPolicy.fromJson(it) },
                description = json.optString("description", ""),
            )
        }
    }
}

/**
 * Screen predicates the replay engine can evaluate after a step completes.
 */
data class WorkflowExpectedState(
    val packageName: String? = null,
    val windowTitle: String? = null,
    val screenTextContains: List<String> = emptyList(),
    val elementPresent: List<WorkflowElementPredicate> = emptyList(),
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("package_name", packageName ?: JSONObject.NULL)
            put("window_title", windowTitle ?: JSONObject.NULL)
            put("screen_text_contains", JSONArray().apply { screenTextContains.forEach { put(it) } })
            put(
                "element_present",
                JSONArray().apply { elementPresent.forEach { put(it.toJson()) } },
            )
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowExpectedState {
            val screenText = mutableListOf<String>()
            json.optJSONArray("screen_text_contains")?.let { array ->
                for (i in 0 until array.length()) {
                    screenText += array.getString(i)
                }
            }
            val elements = mutableListOf<WorkflowElementPredicate>()
            json.optJSONArray("element_present")?.let { array ->
                for (i in 0 until array.length()) {
                    elements += WorkflowElementPredicate.fromJson(array.getJSONObject(i))
                }
            }
            return WorkflowExpectedState(
                packageName = json.optString("package_name").takeIf { it.isNotBlank() },
                windowTitle = json.optString("window_title").takeIf { it.isNotBlank() },
                screenTextContains = screenText,
                elementPresent = elements,
            )
        }
    }
}

data class WorkflowElementPredicate(
    val text: String? = null,
    val contentDescription: String? = null,
    val nodeId: String? = null,
    val viewId: String? = null,
    val match: WorkflowTextMatch = WorkflowTextMatch.CONTAINS,
) {
    init {
        val selectors = listOfNotNull(
            text?.takeIf { it.isNotBlank() },
            contentDescription?.takeIf { it.isNotBlank() },
            nodeId?.takeIf { it.isNotBlank() },
            viewId?.takeIf { it.isNotBlank() },
        )
        require(selectors.isNotEmpty()) {
            "element_present predicate requires at least one of text, content_description, node_id, or view_id"
        }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("text", text ?: JSONObject.NULL)
            put("content_description", contentDescription ?: JSONObject.NULL)
            put("node_id", nodeId ?: JSONObject.NULL)
            put("view_id", viewId ?: JSONObject.NULL)
            put("match", match.wireName)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowElementPredicate {
            return WorkflowElementPredicate(
                text = json.optString("text").takeIf { it.isNotBlank() },
                contentDescription = json.optString("content_description").takeIf { it.isNotBlank() },
                nodeId = json.optString("node_id").takeIf { it.isNotBlank() },
                viewId = json.optString("view_id").takeIf { it.isNotBlank() },
                match = WorkflowTextMatch.fromWire(json.optString("match", WorkflowTextMatch.CONTAINS.wireName))
                    ?: WorkflowTextMatch.CONTAINS,
            )
        }
    }
}

enum class WorkflowTextMatch(val wireName: String) {
    EXACT("exact"),
    CONTAINS("contains");

    companion object {
        fun fromWire(value: String): WorkflowTextMatch? {
            return entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Per-step policy hints for workflow replay. These do not override the global
 * policy engine; they tell the replay path which steps should be treated as
 * approval-sensitive or classified under a workflow risk bucket.
 */
data class WorkflowStepPolicy(
    val requiresApproval: Boolean? = null,
    val workflowClass: PolicyWorkflowClass? = null,
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("requires_approval", requiresApproval ?: JSONObject.NULL)
            put(
                "workflow_class",
                workflowClass?.let { it.name.lowercase() } ?: JSONObject.NULL,
            )
        }
    }

    companion object {
        fun fromJson(json: JSONObject): WorkflowStepPolicy {
            val rawClass = json.optString("workflow_class").takeIf { it.isNotBlank() }
            return WorkflowStepPolicy(
                requiresApproval = if (json.has("requires_approval") && !json.isNull("requires_approval")) {
                    json.getBoolean("requires_approval")
                } else {
                    null
                },
                workflowClass = rawClass?.let { parseWorkflowClass(it) },
            )
        }

        private fun parseWorkflowClass(value: String): PolicyWorkflowClass? {
            val normalized = value.trim().uppercase().replace('-', '_')
            return PolicyWorkflowClass.entries.firstOrNull { it.name == normalized }
        }
    }
}

/** Helpers for `{parameter}` placeholders in workflow argument values. */
object WorkflowParameters {
    private val NamePattern = Regex("^[a-z][a-z0-9_]*$")
    private val PlaceholderPattern = Regex("^\\{([a-z][a-z0-9_]*)\\}$")

    fun isValidName(name: String): Boolean = NamePattern.matches(name)

    fun isPlaceholder(value: String): Boolean = PlaceholderPattern.matches(value)

    fun placeholderName(value: String): String? =
        PlaceholderPattern.matchEntire(value)?.groupValues?.get(1)

    fun placeholder(name: String): String {
        require(isValidName(name)) { "invalid parameter name: $name" }
        return "{$name}"
    }

    fun substitute(args: Map<String, String>, values: Map<String, String>): Map<String, String> {
        if (values.isEmpty()) return args
        return args.mapValues { (_, value) ->
            placeholderName(value)?.let { values[it] } ?: value
        }
    }
}

/** Result of parsing a workflow JSON document. */
sealed class WorkflowParseResult {
    abstract val id: String

    data class Valid(val definition: WorkflowDefinition) : WorkflowParseResult() {
        override val id: String get() = definition.id
    }

    data class Invalid(
        override val id: String,
        val errors: List<String>,
    ) : WorkflowParseResult()
}
