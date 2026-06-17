package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentCommand
import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepFactory
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.userMessage
import dev.touchpilot.app.agent.AgentStepVerification
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult

data class WorkflowReplayResult(
    val workflowId: String,
    val workflowTitle: String,
    val events: List<AgentEvent>,
    val steps: List<AgentStep>,
    val stopReason: AgentStepStopReason,
    val stopMessage: String,
    val completedStepCount: Int,
    val verificationCards: List<StepVerificationCardModel> = emptyList(),
)

/**
 * Deterministic workflow replay with per-step [ExpectedState] verification.
 * Aborts on the first failed tool call or unverified step.
 */
class WorkflowReplayEngine(
    private val tools: LocalAgentLoopTools,
    private val approvalProvider: ToolApprovalProvider,
    private val verifier: WorkflowStepVerifier = WorkflowStateVerifier(),
    private val source: ToolSource = ToolSource.LOCAL_ROUTER,
    private val policy: ActionPolicy = DefaultActionPolicy(),
    private val cancellationSignal: java.util.concurrent.atomic.AtomicBoolean =
        java.util.concurrent.atomic.AtomicBoolean(false),
) {
    fun replay(
        workflow: WorkflowDefinition,
        listener: AgentEventListener = AgentEventListener {},
        onStepsUpdated: ((List<AgentStep>) -> Unit)? = null,
    ): WorkflowReplayResult {
        val events = mutableListOf<AgentEvent>()
        val steps = mutableListOf<AgentStep>()
        val verificationCards = mutableListOf<StepVerificationCardModel>()
        var completedSteps = 0

        fun emit(event: AgentEvent) {
            events += event
            listener.onEvent(event)
        }

        fun publishSteps() {
            onStepsUpdated?.invoke(steps.toList())
        }

        emit(
            AgentEvent.UserMessage(
                text = "Replay workflow: ${workflow.title}"
            )
        )
        publishSteps()

        var currentScreen = tools.observeScreen()

        workflow.steps.forEachIndexed { index, step ->
            val stepNumber = index + 1
            if (cancellationSignal.get()) {
                val stop = stopStep(
                    stepNumber = stepNumber,
                    reason = AgentStepStopReason.USER_CANCELLED,
                    message = "Replay cancelled by user.",
                )
                steps += stop
                emit(AgentEvent.RunCancelled(reason = "Cancelled by user"))
                publishSteps()
                return result(
                    workflow = workflow,
                    events = events,
                    steps = steps,
                    stopReason = AgentStepStopReason.USER_CANCELLED,
                    completedStepCount = completedSteps,
                    verificationCards = verificationCards,
                )
            }

            val command = AgentCommand(tool = step.tool, args = step.args, finalAnswer = null)
            val redactedArgs = SensitiveTextRedactor.redact(step.args)
            steps += AgentStepFactory.act(
                sequenceNumber = stepNumber,
                tool = step.tool,
                args = redactedArgs,
                source = source.name.lowercase(),
                inputSummary = "workflow step $stepNumber",
                outputSummary = "Tool selected.",
                status = AgentStepStatus.PENDING,
            )
            AgentEvent.toolRequested(command, source)?.let(::emit)
            publishSteps()

            val validationError = tools.validate(step.tool, step.args)
            val spec = tools.findTool(step.tool)
            if (validationError != null) {
                steps.replaceLastAct(
                    status = AgentStepStatus.FAILED,
                    message = validationError,
                    stopReason = AgentStepStopReason.NO_VALID_ACTION,
                )
                publishSteps()
                return result(
                    workflow = workflow,
                    events = events,
                    steps = steps + stopStep(
                        stepNumber = stepNumber + 1,
                        reason = AgentStepStopReason.NO_VALID_ACTION,
                        message = validationError,
                    ),
                    stopReason = AgentStepStopReason.NO_VALID_ACTION,
                    completedStepCount = completedSteps,
                    verificationCards = verificationCards,
                )
            }

            if (spec != null) {
                val decision = policy.evaluate(
                    ToolPolicyRequest(
                        tool = spec,
                        args = step.args,
                        source = source,
                        activeScreen = currentScreen,
                    )
                )
                when (decision) {
                    is PolicyDecision.Allow -> Unit
                    is PolicyDecision.RequireApproval -> {
                        val approvalRequest = ToolApprovalRequest(spec, step.args, decision)
                        emit(AgentEvent.approvalRequired(approvalRequest))
                        publishSteps()
                        if (!approvalProvider.approve(approvalRequest)) {
                            emit(
                                AgentEvent.PolicyBlocked(
                                    tool = step.tool,
                                    reason = "denied by user: ${decision.reason}",
                                    userMessage = "The user did not approve ${step.tool}.",
                                )
                            )
                            steps.replaceLastAct(
                                status = AgentStepStatus.BLOCKED,
                                message = "The user did not approve ${step.tool}.",
                                stopReason = AgentStepStopReason.USER_CANCELLED,
                            )
                            publishSteps()
                            return result(
                                workflow = workflow,
                                events = events,
                                steps = steps + stopStep(
                                    stepNumber = stepNumber + 1,
                                    reason = AgentStepStopReason.USER_CANCELLED,
                                    message = "Replay stopped because approval was denied.",
                                ),
                                stopReason = AgentStepStopReason.USER_CANCELLED,
                                completedStepCount = completedSteps,
                                verificationCards = verificationCards,
                            )
                        }
                    }
                    is PolicyDecision.Deny, is PolicyDecision.Block -> {
                        AgentEvent.policyBlocked(step.tool, decision)?.let(::emit)
                        steps.replaceLastAct(
                            status = AgentStepStatus.BLOCKED,
                            message = decision.userMessage,
                            stopReason = AgentStepStopReason.POLICY_BLOCKED,
                        )
                        publishSteps()
                        return result(
                            workflow = workflow,
                            events = events,
                            steps = steps + stopStep(
                                stepNumber = stepNumber + 1,
                                reason = AgentStepStopReason.POLICY_BLOCKED,
                                message = decision.userMessage,
                            ),
                            stopReason = AgentStepStopReason.POLICY_BLOCKED,
                            completedStepCount = completedSteps,
                            verificationCards = verificationCards,
                        )
                    }
                }
            }

            steps.replaceLastAct(status = AgentStepStatus.RUNNING, message = "Tool is running.")
            publishSteps()
            AgentEvent.toolRunning(command, source)?.let(::emit)

            val result = runCatching {
                tools.execute(step.tool, step.args, source)
            }.getOrElse { error ->
                val message = error.message ?: "Tool execution error"
                emit(AgentEvent.ToolFailed(tool = step.tool, message = message))
                steps.replaceLastAct(
                    status = AgentStepStatus.FAILED,
                    message = message,
                    stopReason = AgentStepStopReason.EXECUTOR_ERROR,
                )
                publishSteps()
                return replayResult(
                    workflow = workflow,
                    events = events,
                    steps = steps + stopStep(
                        stepNumber = stepNumber + 1,
                        reason = AgentStepStopReason.EXECUTOR_ERROR,
                        message = message,
                    ),
                    stopReason = AgentStepStopReason.EXECUTOR_ERROR,
                    completedStepCount = completedSteps,
                    verificationCards = verificationCards,
                )
            }

            emit(AgentEvent.toolResult(step.tool, result))
            currentScreen = if (step.tool == "observe_screen" || step.tool == "observe_screen_context") {
                result.message
            } else {
                tools.observeScreen()
            }

            if (!result.ok) {
                steps.replaceLastAct(
                    status = AgentStepStatus.FAILED,
                    message = result.message,
                    stopReason = AgentStepStopReason.REPEATED_TOOL_FAILURE,
                    toolResult = result,
                )
                publishSteps()
                return replayResult(
                    workflow = workflow,
                    events = events,
                    steps = steps + stopStep(
                        stepNumber = stepNumber + 1,
                        reason = AgentStepStopReason.REPEATED_TOOL_FAILURE,
                        message = result.message,
                    ),
                    stopReason = AgentStepStopReason.REPEATED_TOOL_FAILURE,
                    completedStepCount = completedSteps,
                    verificationCards = verificationCards,
                )
            }

            steps.replaceLastAct(
                status = AgentStepStatus.OK,
                message = result.message,
                toolResult = result,
            )
            publishSteps()
            completedSteps += 1

            val expectedState = step.expectedState?.toExpectedState()
            if (expectedState != null) {
                val verifySequence = steps.size + 1
                steps += AgentStep(
                    sequenceNumber = verifySequence,
                    type = dev.touchpilot.app.agent.AgentStepType.VERIFY,
                    status = AgentStepStatus.RUNNING,
                    inputSummary = SensitiveTextRedactor.redact("Verifying workflow step $stepNumber"),
                    outputSummary = "",
                    startedAtMillis = System.currentTimeMillis(),
                )
                publishSteps()

                val verification = verifier.verify(expectedState, step.timeoutMs)
                if (verification.passed) {
                    val passedEvent = AgentEvent.WorkflowStepVerificationPassed(
                        stepIndex = stepNumber,
                        tool = step.tool,
                        expectedSummary = verification.expectedSummary,
                        observedSummary = verification.observedSummary,
                    )
                    emit(passedEvent)
                    verificationCards += StepVerificationCardModel.from(passedEvent)
                    steps.replaceLastVerify(
                        status = AgentStepStatus.OK,
                        verification = AgentStepVerification(
                            status = "passed",
                            reason = verification.reason,
                            data = mapOf(
                                "expected" to verification.expectedSummary,
                                "observed" to verification.observedSummary,
                            ),
                        ),
                        outputSummary = verification.reason,
                    )
                    publishSteps()
                } else {
                    val failedEvent = AgentEvent.WorkflowStepVerificationFailed(
                        stepIndex = stepNumber,
                        tool = step.tool,
                        expectedSummary = verification.expectedSummary,
                        observedSummary = verification.observedSummary,
                        reason = verification.reason,
                    )
                    emit(failedEvent)
                    verificationCards += StepVerificationCardModel.from(failedEvent)
                    steps.replaceLastVerify(
                        status = AgentStepStatus.FAILED,
                        verification = AgentStepVerification(
                            status = "failed",
                            reason = verification.reason,
                            data = mapOf(
                                "expected" to verification.expectedSummary,
                                "observed" to verification.observedSummary,
                            ),
                        ),
                        outputSummary = verification.reason,
                    )
                    val stop = stopStep(
                        stepNumber = verifySequence + 1,
                        reason = AgentStepStopReason.VERIFICATION_FAILED,
                        message = "Step $stepNumber verification failed: ${verification.reason}",
                    )
                    steps += stop
                    publishSteps()
                    return replayResult(
                        workflow = workflow,
                        events = events,
                        steps = steps,
                        stopReason = AgentStepStopReason.VERIFICATION_FAILED,
                        completedStepCount = completedSteps,
                        verificationCards = verificationCards,
                        stopMessage = failedEvent.userMessage,
                    )
                }
            }
        }

        val finalMessage = "Workflow \"${workflow.title}\" replayed successfully."
        emit(AgentEvent.FinalAnswer(finalMessage))
        steps += AgentStepFactory.stop(
            sequenceNumber = steps.size + 1,
            reason = AgentStepStopReason.COMPLETED,
            outputSummary = finalMessage,
        )
        publishSteps()
        return replayResult(
            workflow = workflow,
            events = events,
            steps = steps,
            stopReason = AgentStepStopReason.COMPLETED,
            completedStepCount = completedSteps,
            verificationCards = verificationCards,
            stopMessage = finalMessage,
        )
    }

    private fun stopStep(
        stepNumber: Int,
        reason: AgentStepStopReason,
        message: String,
    ): AgentStep {
        return AgentStepFactory.stop(
            sequenceNumber = stepNumber,
            reason = reason,
            outputSummary = message,
        )
    }

    private fun replayResult(
        workflow: WorkflowDefinition,
        events: List<AgentEvent>,
        steps: List<AgentStep>,
        stopReason: AgentStepStopReason,
        completedStepCount: Int,
        verificationCards: List<StepVerificationCardModel>,
        stopMessage: String = stopReason.userMessage,
    ): WorkflowReplayResult {
        return WorkflowReplayResult(
            workflowId = workflow.id,
            workflowTitle = workflow.title,
            events = events,
            steps = steps,
            stopReason = stopReason,
            stopMessage = stopMessage,
            completedStepCount = completedStepCount,
            verificationCards = verificationCards,
        )
    }

    private fun result(
        workflow: WorkflowDefinition,
        events: List<AgentEvent>,
        steps: List<AgentStep>,
        stopReason: AgentStepStopReason,
        completedStepCount: Int,
        verificationCards: List<StepVerificationCardModel>,
    ): WorkflowReplayResult {
        return replayResult(
            workflow = workflow,
            events = events,
            steps = steps,
            stopReason = stopReason,
            completedStepCount = completedStepCount,
            verificationCards = verificationCards,
        )
    }

    private fun MutableList<AgentStep>.replaceLastAct(
        status: AgentStepStatus,
        message: String,
        stopReason: AgentStepStopReason? = null,
        toolResult: ToolResult? = null,
    ) {
        val last = removeAt(lastIndex)
        add(
            last.copy(
                status = status,
                outputSummary = SensitiveTextRedactor.redact(message),
                stopReason = stopReason,
                toolCall = last.toolCall?.copy(
                    result = toolResult?.let {
                        dev.touchpilot.app.agent.AgentStepToolResult.of(it, redactOnConstruct = true)
                    } ?: last.toolCall.result,
                ),
            )
        )
    }

    private fun MutableList<AgentStep>.replaceLastVerify(
        status: AgentStepStatus,
        verification: AgentStepVerification,
        outputSummary: String,
    ) {
        val last = removeAt(lastIndex)
        add(
            last.copy(
                status = status,
                verification = verification,
                outputSummary = SensitiveTextRedactor.redact(outputSummary),
                endedAtMillis = System.currentTimeMillis(),
            )
        )
    }
}
