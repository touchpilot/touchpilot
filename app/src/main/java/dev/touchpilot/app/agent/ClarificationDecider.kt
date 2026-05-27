package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor

/**
 * Reasons the local router can ask the user a clarifying question instead of
 * executing the next step. Each reason maps to one of the five cases listed
 * in the acceptance criteria of issue #118.
 *
 * Clarification is a *non-execution* fallback. It is deliberately distinct
 * from a policy block: the policy layer (`DefaultActionPolicy`,
 * `IntentDecision.UnsafeRequest`) stops the loop because an action is unsafe;
 * clarification stops the loop because the agent is unsure which safe action
 * to take.
 */
enum class ClarificationReason(val wireName: String) {
    /** The resolver returned multiple equally-good candidate targets. */
    MULTIPLE_TARGETS("multiple_targets"),

    /** The resolver could not find a matching target on the current screen. */
    MISSING_TARGET("missing_target"),

    /** The user request itself is ambiguous (matches no deterministic route). */
    AMBIGUOUS_REQUEST("ambiguous_request"),

    /** The local decider produced a candidate, but its confidence is too low. */
    LOW_CONFIDENCE("low_confidence"),

    /** The proposed action requires a user choice the agent cannot make safely. */
    NEEDS_USER_CHOICE("needs_user_choice"),
}

/**
 * Outcome of a clarification check.
 *
 * `Continue` lets the runner dispatch the next command normally.
 * `Clarify` tells the runner to emit a clarification event and stop the loop
 *  cleanly — no tool dispatch, no approval prompt, no policy event.
 */
sealed class ClarificationDecision {
    abstract val reason: String

    object Continue : ClarificationDecision() {
        override val reason: String = "continue"
    }

    data class Clarify(
        val clarificationReason: ClarificationReason,
        val question: String,
        val detail: String,
        val candidates: List<NextStepCandidate> = emptyList(),
    ) : ClarificationDecision() {
        override val reason: String = clarificationReason.wireName
    }
}

/**
 * Deterministic local check that decides whether the agent loop should pause
 * to ask the user a clarifying question before the next step. Runs purely
 * over the structured [NextStepContext] from the previous step and the
 * proposed next [AgentCommand]; never touches the tool executor, the
 * accessibility service, or the local model.
 *
 * The decider is intentionally narrow: it only fires on the cases enumerated
 * by [ClarificationReason]. Anything outside those cases is `Continue` so the
 * runner's existing path (validation, policy, approval, retry) keeps running.
 *
 * Crucially, this decider does not classify policy-blocked actions as
 * clarifications. Those go through `PolicyDecision.Block` / `Deny` and emit
 * `AgentEvent.PolicyBlocked`. A clarification is the *safe-but-uncertain*
 * fallback, not the *unsafe* one.
 */
class ClarificationDecider(
    private val lowConfidenceThreshold: Float = DefaultLowConfidenceThreshold,
) {
    init {
        require(lowConfidenceThreshold in 0.0f..1.0f) {
            "lowConfidenceThreshold must be in [0.0, 1.0], got $lowConfidenceThreshold"
        }
    }

    fun decide(
        previous: NextStepContext?,
        nextCommand: AgentCommand,
    ): ClarificationDecision {
        // No-valid-action: the provider returned neither a tool nor a final
        // answer. Existing runner just silently returned; we now stop with a
        // clarification so the user knows the agent ran out of safe moves.
        if (nextCommand.tool.isNullOrBlank() && nextCommand.finalAnswer.isNullOrBlank()) {
            return ClarificationDecision.Clarify(
                clarificationReason = ClarificationReason.NEEDS_USER_CHOICE,
                question = "I'm not sure what to do next. Could you tell me which action to take?",
                detail = previous?.redactedSummary()
                    ?: "router returned no tool and no final answer",
            )
        }

        val previousStep = previous?.takeIf { it.hasPreviousStep }
            ?: return ClarificationDecision.Continue
        val previousResult = previousStep.previousToolResult
            ?: return ClarificationDecision.Continue

        if (previousResult.ok) {
            // A succeeded step may still want clarification if the resolver
            // tagged the chosen candidate as low-confidence. The tool result
            // surfaces confidence in `data["confidence"]` for the relevant
            // tools (type_text, scroll).
            val confidence = previousResult.data["confidence"]?.toFloatOrNull()
            if (confidence != null && confidence < lowConfidenceThreshold) {
                return ClarificationDecision.Clarify(
                    clarificationReason = ClarificationReason.LOW_CONFIDENCE,
                    question = "I picked a target but I'm not very sure — should I continue or try something else?",
                    detail = "previous step resolved with confidence=$confidence",
                    candidates = previousStep.candidateTargets,
                )
            }
            return ClarificationDecision.Continue
        }

        val message = previousResult.message
        val lowered = message.lowercase()

        if ("ambiguous" in lowered) {
            return ClarificationDecision.Clarify(
                clarificationReason = ClarificationReason.MULTIPLE_TARGETS,
                question = renderTargetQuestion(previousStep, multiple = true),
                detail = SensitiveTextRedactor.redact(message),
                candidates = previousStep.candidateTargets,
            )
        }

        if ("not found" in lowered) {
            return ClarificationDecision.Clarify(
                clarificationReason = ClarificationReason.MISSING_TARGET,
                question = renderTargetQuestion(previousStep, multiple = false),
                detail = SensitiveTextRedactor.redact(message),
            )
        }

        // The "Ambiguous user request" entry-point arrives via
        // IntentDecision.ClarificationNeeded today; preserve its surface here
        // so a local model can also produce it via tool=null+failure_reason.
        if (previousStep.failureReason?.lowercase()?.contains("ambiguous request") == true) {
            return ClarificationDecision.Clarify(
                clarificationReason = ClarificationReason.AMBIGUOUS_REQUEST,
                question = "Could you describe what you would like me to do more specifically?",
                detail = SensitiveTextRedactor.redact(previousStep.failureReason),
            )
        }

        return ClarificationDecision.Continue
    }

    private fun renderTargetQuestion(previous: NextStepContext, multiple: Boolean): String {
        val tool = previous.previousCommand?.tool ?: "action"
        val labels = previous.candidateTargets
            .take(MaxCandidateLabels)
            .map { it.displayLabel }
            .filter { it.isNotBlank() }

        if (labels.isEmpty()) {
            return if (multiple) {
                "Several targets match. Which one should I $tool?"
            } else {
                "I couldn't find what to $tool. Which item do you mean?"
            }
        }
        val list = labels.joinToString(", ")
        return if (multiple) {
            "Several targets match — which should I $tool: $list?"
        } else {
            "I couldn't find what to $tool. Did you mean one of: $list?"
        }
    }

    companion object {
        const val DefaultLowConfidenceThreshold: Float = 0.55f
        private const val MaxCandidateLabels: Int = 5
    }
}
