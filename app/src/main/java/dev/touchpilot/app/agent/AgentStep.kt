package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolVerificationResult
import dev.touchpilot.app.tools.ToolVerificationStatus
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured record of a single iteration in the local agent's
 * observe-decide-act loop. One [AgentStep] is produced per loop pass; the
 * runner, tests, chat timeline, and run-detail UI all consume the same shape.
 *
 * The existing [AgentEvent] stream remains the per-message wire format the UI
 * renders inline; [AgentStep] is the *step-grouped* projection that lets a
 * caller answer "what did the agent try at step 3 and how did it end?"
 * without re-stitching events. The two layers coexist deliberately so the
 * chat surface can keep streaming events while a run-detail view consumes
 * steps.
 *
 * All display-facing fields ([inputSummary], [outputSummary], nested tool
 * args / messages, clarification text) are stored already-redacted via
 * [SensitiveTextRedactor]; the factory functions in [AgentStepFactory] are
 * the supported entry points and apply redaction on construction so no
 * downstream surface needs to re-run it.
 */
data class AgentStep(
    val sequenceNumber: Int,
    val type: AgentStepType,
    val status: AgentStepStatus,
    val inputSummary: String,
    val outputSummary: String,
    val toolCall: AgentStepToolCall? = null,
    val verification: AgentStepVerification? = null,
    val clarification: AgentStepClarification? = null,
    val stopReason: AgentStepStopReason? = null,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
) {
    init {
        require(sequenceNumber >= 0) {
            "sequenceNumber must be non-negative, got $sequenceNumber"
        }
        if (endedAtMillis != null) {
            require(endedAtMillis >= startedAtMillis) {
                "endedAtMillis ($endedAtMillis) must be >= startedAtMillis ($startedAtMillis)"
            }
        }
        if (type == AgentStepType.CLARIFY) {
            requireNotNull(clarification) {
                "AgentStepType.CLARIFY requires clarification metadata"
            }
        }
        if (type == AgentStepType.STOP) {
            requireNotNull(stopReason) {
                "AgentStepType.STOP requires stopReason"
            }
        }
        if (type == AgentStepType.ACT) {
            requireNotNull(toolCall) {
                "AgentStepType.ACT requires toolCall metadata"
            }
        }
    }

    val isTerminal: Boolean
        get() = type == AgentStepType.STOP || type == AgentStepType.CLARIFY

    val durationMillis: Long?
        get() = endedAtMillis?.let { it - startedAtMillis }

    fun toJson(redactSensitive: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("sequence_number", sequenceNumber)
            put("type", type.wireName)
            put("status", status.wireName)
            put("input_summary", inputSummary.redactedIfNeeded(redactSensitive))
            put("output_summary", outputSummary.redactedIfNeeded(redactSensitive))
            put("started_at_millis", startedAtMillis)
            put("ended_at_millis", endedAtMillis ?: JSONObject.NULL)
            put("duration_millis", durationMillis ?: JSONObject.NULL)
            put("tool_call", toolCall?.toJson(redactSensitive) ?: JSONObject.NULL)
            put("verification", verification?.toJson(redactSensitive) ?: JSONObject.NULL)
            put("clarification", clarification?.toJson(redactSensitive) ?: JSONObject.NULL)
            put("stop_reason", stopReason?.wireName ?: JSONObject.NULL)
        }
    }

    /**
     * Mark the step as ended at [endedAtMillis] (defaulting to now) and copy
     * with [endedAtMillis] / [status] filled in. Callers may use this from a
     * runner to close out an in-flight step.
     */
    fun completed(
        status: AgentStepStatus,
        endedAtMillis: Long = System.currentTimeMillis(),
        outputSummary: String? = null,
    ): AgentStep {
        return copy(
            status = status,
            endedAtMillis = endedAtMillis,
            outputSummary = outputSummary?.let { SensitiveTextRedactor.redact(it) }
                ?: this.outputSummary,
        )
    }
}

/**
 * Step categories for the observe-decide-act loop. Mirrors the six entries
 * listed in the acceptance criteria of issue #126.
 */
enum class AgentStepType(val wireName: String) {
    OBSERVE("observe"),
    DECIDE("decide"),
    ACT("act"),
    VERIFY("verify"),
    CLARIFY("clarify"),
    STOP("stop");

    companion object {
        fun fromWire(value: String?): AgentStepType? {
            if (value == null) return null
            return values().firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Lifecycle status for an [AgentStep]. `RUNNING` is the in-flight state when
 * a runner records a step before its result is known; the terminal states
 * land on the step once it finishes.
 */
enum class AgentStepStatus(val wireName: String) {
    PENDING("pending"),
    RUNNING("running"),
    OK("ok"),
    FAILED("failed"),
    BLOCKED("blocked"),
    CLARIFIED("clarified"),
    STOPPED("stopped");

    companion object {
        fun fromWire(value: String?): AgentStepStatus? {
            if (value == null) return null
            return values().firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Why an agent loop ended. Used on [AgentStepType.STOP] steps to record
 * whether the loop terminated normally or because of a specific condition
 * (policy block, parser failure, max-steps reached, etc.).
 *
 * Kept distinct from [AgentStepClarification.reason] because a stop is the
 * end of the loop, not the safe "stop and ask" fallback.
 */
enum class AgentStepStopReason(val wireName: String) {
    COMPLETED("completed"),
    MAX_STEPS("max_steps"),
    REPEATED_TOOL_FAILURE("repeated_tool_failure"),
    POLICY_BLOCKED("policy_blocked"),
    APPROVAL_DENIED("approval_denied"),
    USER_CANCELLED("user_cancelled"),
    CLARIFICATION_NEEDED("clarification_needed"),
    PARSER_ERROR("parser_error"),
    EXECUTOR_ERROR("executor_error"),
    NO_VALID_ACTION("no_valid_action"),
    VERIFICATION_FAILED("verification_failed");

    companion object {
        fun fromWire(value: String?): AgentStepStopReason? {
            if (value == null) return null
            return values().firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

data class AgentStepToolCall(
    val tool: String,
    val args: Map<String, String>,
    val source: String,
    val result: AgentStepToolResult? = null,
) {
    fun toJson(redactSensitive: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("tool", tool)
            put("source", source)
            put("args", JSONObject(args.redactedIfNeeded(redactSensitive)))
            put("result", result?.toJson(redactSensitive) ?: JSONObject.NULL)
        }
    }
}

data class AgentStepToolResult(
    val ok: Boolean,
    val message: String,
    val data: Map<String, String> = emptyMap(),
) {
    fun toJson(redactSensitive: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("ok", ok)
            put("message", message.redactedIfNeeded(redactSensitive))
            put("data", JSONObject(data.redactedIfNeeded(redactSensitive)))
        }
    }

    companion object {
        fun of(result: ToolResult, redactOnConstruct: Boolean = true): AgentStepToolResult {
            return AgentStepToolResult(
                ok = result.ok,
                message = if (redactOnConstruct) SensitiveTextRedactor.redact(result.message) else result.message,
                data = if (redactOnConstruct) SensitiveTextRedactor.redact(result.data) else result.data,
            )
        }
    }
}

data class AgentStepVerification(
    val status: String,
    val reason: String,
    val data: Map<String, String> = emptyMap(),
) {
    fun toJson(redactSensitive: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("status", status)
            put("reason", reason.redactedIfNeeded(redactSensitive))
            put("data", JSONObject(data.redactedIfNeeded(redactSensitive)))
        }
    }

    companion object {
        fun of(result: ToolVerificationResult, redactOnConstruct: Boolean = true): AgentStepVerification {
            val rawReason = result.reason
            val rawData = result.data
            return AgentStepVerification(
                status = result.status.wireName,
                reason = if (redactOnConstruct) SensitiveTextRedactor.redact(rawReason) else rawReason,
                data = if (redactOnConstruct) SensitiveTextRedactor.redact(rawData) else rawData,
            )
        }

        fun of(status: ToolVerificationStatus, reason: String): AgentStepVerification {
            return AgentStepVerification(
                status = status.wireName,
                reason = SensitiveTextRedactor.redact(reason),
            )
        }
    }
}

data class AgentStepClarification(
    val reason: AgentStepClarificationReason,
    val question: String,
    val detail: String = "",
    val candidateLabels: List<String> = emptyList(),
) {
    fun toJson(redactSensitive: Boolean = true): JSONObject {
        return JSONObject().apply {
            put("reason", reason.wireName)
            put("question", question.redactedIfNeeded(redactSensitive))
            put("detail", detail.redactedIfNeeded(redactSensitive))
            put("candidate_labels", JSONArray().apply {
                candidateLabels.forEach { put(it.redactedIfNeeded(redactSensitive)) }
            })
        }
    }
}

/**
 * Reasons a clarify step was emitted. Mirrors the cases enumerated by the
 * runtime clarification path so a step record can be rendered with the same
 * meaning the UI shows on a live event.
 */
enum class AgentStepClarificationReason(val wireName: String) {
    MULTIPLE_TARGETS("multiple_targets"),
    MISSING_TARGET("missing_target"),
    AMBIGUOUS_REQUEST("ambiguous_request"),
    LOW_CONFIDENCE("low_confidence"),
    NEEDS_USER_CHOICE("needs_user_choice");

    companion object {
        fun fromWire(value: String?): AgentStepClarificationReason? {
            if (value == null) return null
            return values().firstOrNull { it.wireName.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Convenience factory for the six well-known step shapes. Each helper applies
 * [SensitiveTextRedactor] to display strings on construction so callers do
 * not have to remember to redact.
 */
object AgentStepFactory {
    fun observe(
        sequenceNumber: Int,
        inputSummary: String,
        outputSummary: String,
        startedAtMillis: Long = System.currentTimeMillis(),
        endedAtMillis: Long? = null,
        status: AgentStepStatus = AgentStepStatus.OK,
    ): AgentStep = AgentStep(
        sequenceNumber = sequenceNumber,
        type = AgentStepType.OBSERVE,
        status = status,
        inputSummary = SensitiveTextRedactor.redact(inputSummary),
        outputSummary = SensitiveTextRedactor.redact(outputSummary),
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
    )

    fun decide(
        sequenceNumber: Int,
        inputSummary: String,
        outputSummary: String,
        startedAtMillis: Long = System.currentTimeMillis(),
        endedAtMillis: Long? = null,
        status: AgentStepStatus = AgentStepStatus.OK,
    ): AgentStep = AgentStep(
        sequenceNumber = sequenceNumber,
        type = AgentStepType.DECIDE,
        status = status,
        inputSummary = SensitiveTextRedactor.redact(inputSummary),
        outputSummary = SensitiveTextRedactor.redact(outputSummary),
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
    )

    fun act(
        sequenceNumber: Int,
        tool: String,
        args: Map<String, String>,
        source: String,
        result: ToolResult? = null,
        inputSummary: String = "tool=$tool",
        outputSummary: String = result?.message.orEmpty(),
        startedAtMillis: Long = System.currentTimeMillis(),
        endedAtMillis: Long? = null,
        status: AgentStepStatus = result?.toStepStatus() ?: AgentStepStatus.RUNNING,
    ): AgentStep = AgentStep(
        sequenceNumber = sequenceNumber,
        type = AgentStepType.ACT,
        status = status,
        inputSummary = SensitiveTextRedactor.redact(inputSummary),
        outputSummary = SensitiveTextRedactor.redact(outputSummary),
        toolCall = AgentStepToolCall(
            tool = tool,
            args = SensitiveTextRedactor.redact(args),
            source = source,
            result = result?.let { AgentStepToolResult.of(it, redactOnConstruct = true) },
        ),
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
    )

    fun verify(
        sequenceNumber: Int,
        verification: ToolVerificationResult,
        inputSummary: String = "verifying previous tool",
        startedAtMillis: Long = System.currentTimeMillis(),
        endedAtMillis: Long? = null,
    ): AgentStep {
        val converted = AgentStepVerification.of(verification, redactOnConstruct = true)
        val status = when (verification.status) {
            ToolVerificationStatus.PASSED -> AgentStepStatus.OK
            ToolVerificationStatus.FAILED -> AgentStepStatus.FAILED
            ToolVerificationStatus.SKIPPED -> AgentStepStatus.PENDING
        }
        return AgentStep(
            sequenceNumber = sequenceNumber,
            type = AgentStepType.VERIFY,
            status = status,
            inputSummary = SensitiveTextRedactor.redact(inputSummary),
            outputSummary = converted.reason,
            verification = converted,
            startedAtMillis = startedAtMillis,
            endedAtMillis = endedAtMillis,
        )
    }

    fun clarify(
        sequenceNumber: Int,
        clarification: AgentStepClarification,
        inputSummary: String = "agent paused for clarification",
        startedAtMillis: Long = System.currentTimeMillis(),
        endedAtMillis: Long? = null,
    ): AgentStep = AgentStep(
        sequenceNumber = sequenceNumber,
        type = AgentStepType.CLARIFY,
        status = AgentStepStatus.CLARIFIED,
        inputSummary = SensitiveTextRedactor.redact(inputSummary),
        outputSummary = SensitiveTextRedactor.redact(clarification.question),
        clarification = AgentStepClarification(
            reason = clarification.reason,
            question = SensitiveTextRedactor.redact(clarification.question),
            detail = SensitiveTextRedactor.redact(clarification.detail),
            candidateLabels = clarification.candidateLabels.map { SensitiveTextRedactor.redact(it) },
        ),
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
    )

    fun stop(
        sequenceNumber: Int,
        reason: AgentStepStopReason,
        outputSummary: String,
        inputSummary: String = "agent loop ended",
        startedAtMillis: Long = System.currentTimeMillis(),
        endedAtMillis: Long? = startedAtMillis,
    ): AgentStep = AgentStep(
        sequenceNumber = sequenceNumber,
        type = AgentStepType.STOP,
        status = AgentStepStatus.STOPPED,
        inputSummary = SensitiveTextRedactor.redact(inputSummary),
        outputSummary = SensitiveTextRedactor.redact(outputSummary),
        stopReason = reason,
        startedAtMillis = startedAtMillis,
        endedAtMillis = endedAtMillis,
    )

    private fun ToolResult.toStepStatus(): AgentStepStatus = if (ok) AgentStepStatus.OK else AgentStepStatus.FAILED
}

private fun String.redactedIfNeeded(redact: Boolean): String =
    if (redact) SensitiveTextRedactor.redact(this) else this

private fun Map<String, String>.redactedIfNeeded(redact: Boolean): Map<String, String> =
    if (redact) SensitiveTextRedactor.redact(this) else this
