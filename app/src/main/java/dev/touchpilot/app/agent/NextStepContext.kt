package dev.touchpilot.app.agent

import dev.touchpilot.app.screen.ScreenSummary
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolVerificationResult

/**
 * Structured carry-through between agent steps. The agent loop builds one
 * after every step and hands it to the local decider (router or small model)
 * for the next step.
 *
 * The runtime previously rebuilt a freeform string each step (current task +
 * transcript + verification screen). That worked for the model prompt, but
 * gave the local router and any structured decider no way to:
 *
 * - know which tool just ran and how it ended,
 * - know whether the verification step passed, failed, or was skipped,
 * - reason about candidate targets when a resolver returned ambiguity,
 * - tell apart a transient retry from a "stop and ask" situation.
 *
 * [NextStepContext] replaces that with a typed record. It is intentionally
 * additive — the existing prompt context still flows through the runner — so
 * a smaller local model can also consume the freeform string while structured
 * deciders read the typed fields.
 *
 * Every field that contains user-visible text is either already redacted by
 * the producer (tool result `data`, screen summary sentences) or is a code-
 * level reason string. [redactedSummary] is the convenience accessor for
 * surfaces that want a single safe-to-log description.
 */
data class NextStepContext(
    val task: String,
    val stepNumber: Int,
    val previousScreen: ScreenSummary? = null,
    val previousCommand: AgentCommand? = null,
    val previousToolResult: ToolResult? = null,
    val previousVerification: ToolVerificationResult? = null,
    val failureReason: String? = null,
    val candidateTargets: List<NextStepCandidate> = emptyList(),
) {
    init {
        require(stepNumber >= 0) { "stepNumber must be non-negative, got $stepNumber" }
    }

    /**
     * True iff a previous step is being summarized. Step 0 is the initial
     * step with no prior tool result yet — deciders should treat that as a
     * cold start, not as a continuation.
     */
    val hasPreviousStep: Boolean
        get() = stepNumber > 0 && previousCommand != null

    /**
     * Convenience flag — was the previous step's tool a success?
     */
    val previousSucceeded: Boolean
        get() = previousToolResult?.ok == true

    /**
     * One-line, log-safe description of the previous step. Useful for trace
     * exports and for the clarification debug field.
     */
    fun redactedSummary(): String {
        val tool = previousCommand?.tool ?: return "step $stepNumber — no prior tool"
        val status = when {
            previousToolResult == null -> "no result"
            previousToolResult.ok -> "ok"
            else -> "failed"
        }
        val message = previousToolResult?.message?.let { SensitiveTextRedactor.redact(it) }
            ?: failureReason
            ?: ""
        return buildString {
            append("step ").append(stepNumber)
            append(" — ").append(tool).append(' ').append(status)
            if (message.isNotBlank()) {
                append(" (")
                append(message.take(140))
                append(')')
            }
        }
    }

    companion object {
        /**
         * Initial context for the very first agent step.
         */
        fun initial(task: String): NextStepContext = NextStepContext(
            task = task,
            stepNumber = 0,
        )
    }
}

/**
 * Display-safe candidate node reference that a clarification question can
 * present back to the user. Built from the structured resolver candidates
 * (`TargetResolver`, `ScrollResolver`, `FindElementMatcher`) without
 * carrying raw sensitive text — everything here is already `displaySafe`.
 */
data class NextStepCandidate(
    val nodeId: String?,
    val displayLabel: String,
    val role: String? = null,
    val confidence: Float? = null,
    val sensitive: Boolean = false,
) {
    init {
        val c = confidence
        if (c != null) {
            require(c in 0.0f..1.0f) { "confidence must be in [0.0, 1.0], got $c" }
        }
    }
}
