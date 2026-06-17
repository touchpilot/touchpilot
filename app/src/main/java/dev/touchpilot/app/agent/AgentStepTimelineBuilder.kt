package dev.touchpilot.app.agent

import dev.touchpilot.app.security.SensitiveTextRedactor

/**
 * Builds a structured agent step timeline from runtime events and runner
 * lifecycle callbacks. Produces [AgentStep] records compatible with
 * [AgentStepFactory]; all summaries are redaction-safe.
 */
class AgentStepTimelineBuilder {
    private val steps = mutableListOf<AgentStep>()
    private var sequence = 0
    private var activeDecideSeq: Int? = null
    private var activeToolStepSeq: Int? = null
    private var activeVerifySeq: Int? = null

    val snapshot: List<AgentStep>
        get() = steps.toList()

    val isEmpty: Boolean
        get() = steps.isEmpty()

    /** Replaces the timeline with steps produced by [BoundedLocalAgentLoop]. */
    fun replaceAll(runnerSteps: List<AgentStep>) {
        steps.clear()
        steps.addAll(runnerSteps)
        activeDecideSeq = null
        activeToolStepSeq = null
        activeVerifySeq = null
    }

    fun onEvent(event: AgentEvent) {
        when (event) {
            is AgentEvent.UserMessage -> Unit
            is AgentEvent.AssistantMessage -> Unit
            is AgentEvent.ToolRequested -> onToolRequested(event)
            is AgentEvent.ToolRunning -> onToolRunning(event)
            is AgentEvent.ToolSucceeded -> onToolSucceeded(event)
            is AgentEvent.ToolFailed -> onToolFailed(event)
            is AgentEvent.ApprovalRequired -> onApprovalRequired(event)
            is AgentEvent.PolicyBlocked -> onPolicyBlocked(event)
            is AgentEvent.FinalAnswer -> onFinalAnswer(event)
            is AgentEvent.Clarification -> onClarification(event)
            is AgentEvent.RunCancelled -> onRunCancelled(event)
            is AgentEvent.SkillActive -> Unit
            is AgentEvent.TraceRecorded -> Unit
            is AgentEvent.WorkflowStepVerificationPassed -> onWorkflowStepVerificationPassed(event)
            is AgentEvent.WorkflowStepVerificationFailed -> onWorkflowStepVerificationFailed(event)
        }
    }

    fun startDecide(stepNumber: Int) {
        completeActiveDecide(success = false, outputSummary = "Superseded by next step")
        val step = AgentStepFactory.decide(
            sequenceNumber = nextSequence(),
            inputSummary = "Step $stepNumber: choosing next action",
            outputSummary = "",
            status = AgentStepStatus.RUNNING,
            endedAtMillis = null,
        )
        activeDecideSeq = step.sequenceNumber
        append(step)
    }

    fun completeDecide(outputSummary: String) {
        val seq = activeDecideSeq ?: return
        updateCompleted(seq, AgentStepStatus.OK, outputSummary)
        activeDecideSeq = null
    }

    fun failDecide(outputSummary: String) {
        val seq = activeDecideSeq
        if (seq == null) {
            append(
                AgentStepFactory.decide(
                    sequenceNumber = nextSequence(),
                    inputSummary = "Choosing next action",
                    outputSummary = outputSummary,
                    status = AgentStepStatus.FAILED,
                    endedAtMillis = System.currentTimeMillis(),
                )
            )
        } else {
            updateCompleted(seq, AgentStepStatus.FAILED, outputSummary)
        }
        activeDecideSeq = null
    }

    fun startVerify() {
        val step = AgentStep(
            sequenceNumber = nextSequence(),
            type = AgentStepType.VERIFY,
            status = AgentStepStatus.RUNNING,
            inputSummary = SensitiveTextRedactor.redact("Checking screen after action"),
            outputSummary = "",
            startedAtMillis = System.currentTimeMillis(),
            endedAtMillis = null,
        )
        activeVerifySeq = step.sequenceNumber
        append(step)
    }

    fun completeVerify(outputSummary: String = "Screen checked") {
        val seq = activeVerifySeq ?: return
        updateCompleted(seq, AgentStepStatus.OK, outputSummary)
        activeVerifySeq = null
    }

    fun skipVerify(outputSummary: String) {
        val seq = activeVerifySeq ?: return
        updateCompleted(seq, AgentStepStatus.FAILED, outputSummary)
        activeVerifySeq = null
    }

    fun requestClarification(question: String) {
        append(
            AgentStepFactory.clarify(
                sequenceNumber = nextSequence(),
                clarification = AgentStepClarification(
                    reason = AgentStepClarificationReason.AMBIGUOUS_REQUEST,
                    question = question,
                ),
            )
        )
    }

    fun failureStop(message: String): AgentStep {
        return AgentStepFactory.stop(
            sequenceNumber = nextSequence(),
            reason = AgentStepStopReason.EXECUTOR_ERROR,
            outputSummary = message,
        ).copy(status = AgentStepStatus.FAILED)
    }

    private fun onToolRequested(event: AgentEvent.ToolRequested) {
        completeActiveDecide(success = true, outputSummary = "Selected ${event.tool}")
        val type = if (event.tool == OBSERVE_TOOL) AgentStepType.OBSERVE else AgentStepType.ACT
        val summary = toolLabel(event.tool, event.args)
        val step = when (type) {
            AgentStepType.OBSERVE -> AgentStep(
                sequenceNumber = nextSequence(),
                type = AgentStepType.OBSERVE,
                status = AgentStepStatus.PENDING,
                inputSummary = SensitiveTextRedactor.redact(summary),
                outputSummary = "",
                startedAtMillis = System.currentTimeMillis(),
            )
            AgentStepType.ACT -> AgentStep(
                sequenceNumber = nextSequence(),
                type = AgentStepType.ACT,
                status = AgentStepStatus.PENDING,
                inputSummary = SensitiveTextRedactor.redact(summary),
                outputSummary = "",
                toolCall = AgentStepToolCall(
                    tool = event.tool,
                    args = SensitiveTextRedactor.redact(event.args),
                    source = event.source.name.lowercase(),
                ),
                startedAtMillis = System.currentTimeMillis(),
            )
            else -> error("unexpected tool step type: $type")
        }
        activeToolStepSeq = step.sequenceNumber
        append(step)
    }

    private fun onToolRunning(event: AgentEvent.ToolRunning) {
        val seq = activeToolStepSeq ?: return
        updateRunning(seq, toolLabel(event.tool, event.args))
    }

    private fun onToolSucceeded(event: AgentEvent.ToolSucceeded) {
        val seq = activeToolStepSeq ?: return
        updateCompleted(
            seq,
            AgentStepStatus.OK,
            summarizeToolResult(event.tool, event.message, success = true),
        )
        activeToolStepSeq = null
    }

    private fun onToolFailed(event: AgentEvent.ToolFailed) {
        val seq = activeToolStepSeq ?: return
        updateCompleted(
            seq,
            AgentStepStatus.FAILED,
            summarizeToolResult(event.tool, event.message, success = false),
        )
        activeToolStepSeq = null
    }

    private fun onApprovalRequired(event: AgentEvent.ApprovalRequired) {
        val seq = activeToolStepSeq ?: return
        updateRunning(
            seq,
            "${toolLabel(event.tool, event.args)} — waiting for approval",
        )
        updateStatus(seq, AgentStepStatus.PENDING)
    }

    private fun onPolicyBlocked(event: AgentEvent.PolicyBlocked) {
        val toolSeq = activeToolStepSeq
        if (toolSeq != null) {
            updateCompleted(toolSeq, AgentStepStatus.BLOCKED, event.userMessage)
            activeToolStepSeq = null
        } else {
            append(
                AgentStepFactory.stop(
                    sequenceNumber = nextSequence(),
                    reason = AgentStepStopReason.POLICY_BLOCKED,
                    outputSummary = event.userMessage,
                ).copy(status = AgentStepStatus.BLOCKED)
            )
        }
        activeDecideSeq = null
        activeVerifySeq = null
    }

    private fun onClarification(event: AgentEvent.Clarification) {
        append(
            AgentStepFactory.clarify(
                sequenceNumber = nextSequence(),
                clarification = AgentStepClarification(
                    reason = event.reason.toAgentStepReason(),
                    question = event.question,
                    detail = event.detail,
                    candidateLabels = event.candidates.map { it.displayLabel },
                ),
            )
        )
    }

    private fun onRunCancelled(event: AgentEvent.RunCancelled) {
        completeActiveDecide(success = false, outputSummary = "Cancelled by user")
        activeVerifySeq?.let { updateCompleted(it, AgentStepStatus.BLOCKED, "Cancelled") }
        activeVerifySeq = null
        activeToolStepSeq?.let { updateCompleted(it, AgentStepStatus.BLOCKED, "Cancelled") }
        activeToolStepSeq = null
        append(
            AgentStepFactory.stop(
                sequenceNumber = nextSequence(),
                reason = AgentStepStopReason.USER_CANCELLED,
                outputSummary = event.reason,
            ).copy(status = AgentStepStatus.BLOCKED)
        )
    }

    private fun onFinalAnswer(event: AgentEvent.FinalAnswer) {
        completeActiveDecide(success = true, outputSummary = "Task complete")
        activeVerifySeq?.let { updateCompleted(it, AgentStepStatus.OK, "Screen checked") }
        activeVerifySeq = null
        append(
            AgentStepFactory.stop(
                sequenceNumber = nextSequence(),
                reason = AgentStepStopReason.COMPLETED,
                outputSummary = event.text,
            )
        )
    }

    private fun onWorkflowStepVerificationPassed(event: AgentEvent.WorkflowStepVerificationPassed) {
        if (activeVerifySeq == null) {
            startVerify()
        }
        completeVerify("Step ${event.stepIndex} verified: ${event.expectedSummary}")
    }

    private fun onWorkflowStepVerificationFailed(event: AgentEvent.WorkflowStepVerificationFailed) {
        if (activeVerifySeq == null) {
            startVerify()
        }
        val seq = activeVerifySeq ?: return
        val index = steps.indexOfFirst { it.sequenceNumber == seq }
        if (index >= 0) {
            steps[index] = steps[index].copy(
                status = AgentStepStatus.FAILED,
                outputSummary = SensitiveTextRedactor.redact(event.userMessage),
                verification = AgentStepVerification(
                    status = "failed",
                    reason = SensitiveTextRedactor.redact(event.reason),
                    data = mapOf(
                        "expected" to SensitiveTextRedactor.redact(event.expectedSummary),
                        "observed" to SensitiveTextRedactor.redact(event.observedSummary),
                    ),
                ),
                endedAtMillis = System.currentTimeMillis(),
            )
        }
        activeVerifySeq = null
        append(
            AgentStepFactory.stop(
                sequenceNumber = nextSequence(),
                reason = AgentStepStopReason.VERIFICATION_FAILED,
                outputSummary = event.userMessage,
            ).copy(status = AgentStepStatus.FAILED)
        )
    }

    private fun completeActiveDecide(success: Boolean, outputSummary: String) {
        val seq = activeDecideSeq ?: return
        updateCompleted(
            seq,
            if (success) AgentStepStatus.OK else AgentStepStatus.FAILED,
            outputSummary,
        )
        activeDecideSeq = null
    }

    private fun toolLabel(tool: String, args: Map<String, String>): String {
        val redactedArgs = SensitiveTextRedactor.redact(args)
        val argSummary = if (redactedArgs.isEmpty()) {
            ""
        } else {
            redactedArgs.entries.joinToString(", ") { "${it.key}=${it.value.take(40)}" }
        }
        return if (argSummary.isBlank()) tool else "$tool ($argSummary)"
    }

    private fun summarizeToolResult(tool: String, message: String, success: Boolean): String {
        val redacted = SensitiveTextRedactor.redact(message).take(120)
        val prefix = if (success) "Completed" else "Failed"
        return if (redacted.isBlank()) {
            "$prefix $tool"
        } else {
            "$prefix $tool: $redacted"
        }
    }

    private fun nextSequence(): Int = sequence++

    private fun append(step: AgentStep): AgentStep {
        steps += step
        return step
    }

    private fun updateCompleted(sequenceNumber: Int, status: AgentStepStatus, outputSummary: String) {
        val index = steps.indexOfFirst { it.sequenceNumber == sequenceNumber }
        if (index < 0) return
        steps[index] = steps[index].completed(status, outputSummary = outputSummary)
    }

    private fun updateRunning(sequenceNumber: Int, outputSummary: String) {
        val index = steps.indexOfFirst { it.sequenceNumber == sequenceNumber }
        if (index < 0) return
        steps[index] = steps[index].copy(
            status = AgentStepStatus.RUNNING,
            outputSummary = SensitiveTextRedactor.redact(outputSummary),
        )
    }

    private fun updateStatus(sequenceNumber: Int, status: AgentStepStatus) {
        val index = steps.indexOfFirst { it.sequenceNumber == sequenceNumber }
        if (index < 0) return
        steps[index] = steps[index].copy(status = status)
    }

    private companion object {
        const val OBSERVE_TOOL = "observe_screen"
    }
}

private fun ClarificationReason.toAgentStepReason(): AgentStepClarificationReason {
    return when (this) {
        ClarificationReason.MULTIPLE_TARGETS -> AgentStepClarificationReason.MULTIPLE_TARGETS
        ClarificationReason.MISSING_TARGET -> AgentStepClarificationReason.MISSING_TARGET
        ClarificationReason.AMBIGUOUS_REQUEST -> AgentStepClarificationReason.AMBIGUOUS_REQUEST
        ClarificationReason.LOW_CONFIDENCE -> AgentStepClarificationReason.LOW_CONFIDENCE
        ClarificationReason.NEEDS_USER_CHOICE -> AgentStepClarificationReason.NEEDS_USER_CHOICE
    }
}
