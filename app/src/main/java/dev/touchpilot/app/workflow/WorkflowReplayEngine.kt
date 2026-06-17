package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentCommand
import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepFactory
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepType
import dev.touchpilot.app.agent.AgentStepVerification
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.agent.userMessage
import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec
import java.util.concurrent.atomic.AtomicBoolean

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
 * Replays a [WorkflowDefinition] step-by-step through the on-device tool
 * executor with live policy checks, optional preflight warnings, and
 * per-step [ExpectedState] verification.
 */
class WorkflowReplayEngine(
    private val tools: LocalAgentLoopTools,
    private val policy: ActionPolicy = DefaultActionPolicy(),
    private val approvalProvider: ToolApprovalProvider = ToolApprovalProvider { true },
    private val liveContextProvider: WorkflowLivePolicyContextProvider =
        DefaultWorkflowLivePolicyContextProvider,
    private val verifier: WorkflowStepVerifier = WorkflowStateVerifier(),
    private val source: ToolSource = ToolSource.WORKFLOW_REPLAY,
    private val cancellationSignal: AtomicBoolean = AtomicBoolean(false),
) {
    fun replay(
        definition: WorkflowDefinition,
        parameters: Map<String, String> = emptyMap(),
        listener: AgentEventListener = AgentEventListener {},
        onStepsUpdated: ((List<AgentStep>) -> Unit)? = null,
    ): AgentRunResult {
        val resolvedParameters = WorkflowParameters.resolveValues(definition, parameters)
        val transcript = StringBuilder()
        val events = mutableListOf<AgentEvent>()
        val steps = mutableListOf<AgentStep>()
        val totalSteps = definition.steps.size
        var completedSteps = 0

        fun emit(event: AgentEvent) {
            events += event
            listener.onEvent(event)
        }

        fun publishSteps() {
            onStepsUpdated?.invoke(steps.toList())
        }

        val task = "Replay workflow: ${definition.title}"
        emit(AgentEvent.UserMessage(task))

        val policyPreview = WorkflowPolicyPreview.preview(
            definition = definition,
            parameters = parameters,
            policy = policy,
            liveContext = liveContextProvider.current(),
            source = source,
        )
        emit(
            AgentEvent.WorkflowPolicyPreview(
                workflowId = definition.id,
                workflowTitle = definition.title,
                summary = WorkflowPolicyPreview.summaryLine(policyPreview),
                steps = policyPreview,
            )
        )

        when (val preflight = WorkflowPreflight.check(definition, liveContextProvider.current())) {
            is WorkflowPreflight.Result.Ok -> Unit
            is WorkflowPreflight.Result.Mismatch -> {
                val warning = preflight.userMessage()
                emit(
                    AgentEvent.WorkflowPreflightWarning(
                        workflowId = definition.id,
                        expectedPackage = preflight.expectedPackage,
                        expectedLabel = preflight.expectedLabel,
                        currentPackage = preflight.currentPackage,
                        currentLabel = preflight.currentLabel,
                        userMessage = warning,
                    )
                )
                emit(
                    AgentEvent.AssistantMessage(
                        text = warning,
                        detail = "Replay may act on the wrong app if you continue.",
                    )
                )
                if (!requestPreflightConfirmation(warning)) {
                    transcript.appendLine("Workflow replay cancelled: foreground app mismatch")
                    emit(AgentEvent.RunCancelled(reason = "Foreground app mismatch"))
                    steps += AgentStepFactory.stop(
                        sequenceNumber = 0,
                        reason = AgentStepStopReason.USER_CANCELLED,
                        outputSummary = "Cancelled before replay",
                        inputSummary = warning,
                    )
                    publishSteps()
                    return stopped(
                        transcript = transcript,
                        events = events,
                        steps = steps,
                        reason = AgentStepStopReason.USER_CANCELLED,
                        finalAnswer = "Workflow replay cancelled.",
                    )
                }
                transcript.appendLine("User confirmed replay despite foreground mismatch")
            }
        }

        publishSteps()

        definition.steps.forEachIndexed { index, step ->
            val stepIndex = index + 1
            if (cancellationSignal.get()) {
                transcript.appendLine("Workflow replay cancelled at step $stepIndex")
                emit(AgentEvent.RunCancelled(reason = "Cancelled by user"))
                steps += AgentStepFactory.stop(
                    sequenceNumber = stepIndex,
                    reason = AgentStepStopReason.USER_CANCELLED,
                    outputSummary = "Cancelled by user",
                    inputSummary = "workflow step $stepIndex of $totalSteps",
                )
                publishSteps()
                return stopped(
                    transcript = transcript,
                    events = events,
                    steps = steps,
                    reason = AgentStepStopReason.USER_CANCELLED,
                    finalAnswer = "Workflow replay cancelled.",
                )
            }

            val resolvedArgs = WorkflowParameters.substitute(step.args, resolvedParameters)
            val liveContext = liveContextProvider.current()
            transcript.appendLine("Step $stepIndex/$totalSteps: ${step.tool}")

            emit(
                AgentEvent.WorkflowStepStarted(
                    workflowId = definition.id,
                    workflowTitle = definition.title,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    args = resolvedArgs,
                )
            )

            val validationError = tools.validate(step.tool, resolvedArgs)
            if (validationError != null) {
                transcript.appendLine("Validation failed: $validationError")
                return failStep(
                    definition = definition,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = validationError,
                    transcript = transcript,
                    events = events,
                    steps = steps,
                    publishSteps = ::publishSteps,
                    emit = ::emit,
                )
            }

            val spec = tools.findTool(step.tool)
            if (spec != null) {
                when (
                    val decision = policy.evaluate(
                        ToolPolicyRequest(
                            tool = spec,
                            args = resolvedArgs,
                            source = source,
                            activeScreen = liveContext.activeScreen,
                            foregroundApp = liveContext.foregroundApp,
                        )
                    )
                ) {
                    is PolicyDecision.Allow -> {
                        transcript.appendLine("Policy allowed ${step.tool}: ${decision.reason}")
                    }
                    is PolicyDecision.RequireApproval -> {
                        transcript.appendLine("Approval requested for ${step.tool}: ${decision.reason}")
                        val approvalRequest = ToolApprovalRequest(spec, resolvedArgs, decision)
                        emit(AgentEvent.approvalRequired(approvalRequest))
                        publishSteps()
                        if (!approvalProvider.approve(approvalRequest)) {
                            transcript.appendLine("Tool denied by user: ${step.tool}")
                            tools.recordExecution(
                                name = step.tool,
                                args = "risk=${spec.risk}",
                                ok = false,
                                message = "denied by user: ${decision.reason}",
                            )
                            emit(
                                AgentEvent.PolicyBlocked(
                                    tool = step.tool,
                                    reason = "denied by user: ${decision.reason}",
                                    userMessage = "The user did not approve ${step.tool}.",
                                )
                            )
                            steps += AgentStepFactory.stop(
                                sequenceNumber = stepIndex,
                                reason = AgentStepStopReason.APPROVAL_DENIED,
                                outputSummary = "The user did not approve ${step.tool}.",
                                inputSummary = "workflow step $stepIndex of $totalSteps",
                            )
                            publishSteps()
                            return stopped(
                                transcript = transcript,
                                events = events,
                                steps = steps,
                                reason = AgentStepStopReason.APPROVAL_DENIED,
                                finalAnswer = "Workflow replay stopped: approval denied.",
                            )
                        }
                        transcript.appendLine("Tool approved by user: ${step.tool}")
                    }
                    is PolicyDecision.Deny -> {
                        transcript.appendLine("Policy denied ${step.tool}: ${decision.reason}")
                        tools.recordExecution(step.tool, "policy=deny", false, decision.userMessage)
                        AgentEvent.policyBlocked(step.tool, decision)?.let(::emit)
                        steps += AgentStepFactory.stop(
                            sequenceNumber = stepIndex,
                            reason = AgentStepStopReason.POLICY_BLOCKED,
                            outputSummary = decision.userMessage,
                            inputSummary = "workflow step $stepIndex of $totalSteps",
                        )
                        publishSteps()
                        return stopped(
                            transcript = transcript,
                            events = events,
                            steps = steps,
                            reason = AgentStepStopReason.POLICY_BLOCKED,
                            finalAnswer = decision.userMessage,
                        )
                    }
                    is PolicyDecision.Block -> {
                        transcript.appendLine("Policy blocked ${step.tool}: ${decision.reason}")
                        tools.recordExecution(step.tool, "policy=block", false, decision.userMessage)
                        AgentEvent.policyBlocked(step.tool, decision)?.let(::emit)
                        steps += AgentStepFactory.stop(
                            sequenceNumber = stepIndex,
                            reason = AgentStepStopReason.POLICY_BLOCKED,
                            outputSummary = decision.userMessage,
                            inputSummary = "workflow step $stepIndex of $totalSteps",
                        )
                        publishSteps()
                        return stopped(
                            transcript = transcript,
                            events = events,
                            steps = steps,
                            reason = AgentStepStopReason.POLICY_BLOCKED,
                            finalAnswer = decision.userMessage,
                        )
                    }
                }
            }

            val command = AgentCommand(tool = step.tool, args = resolvedArgs, finalAnswer = null)
            steps += AgentStepFactory.act(
                sequenceNumber = stepIndex,
                tool = step.tool,
                args = SensitiveTextRedactor.redact(resolvedArgs),
                source = source.name.lowercase(),
                inputSummary = "workflow step $stepIndex of $totalSteps",
                outputSummary = "Tool selected.",
                status = AgentStepStatus.PENDING,
            )
            AgentEvent.toolRequested(command, source)?.let(::emit)
            publishSteps()

            steps.replaceLast(status = AgentStepStatus.RUNNING, message = "Tool is running.")
            publishSteps()
            AgentEvent.toolRunning(command, source)?.let(::emit)

            val result = runCatching {
                tools.execute(step.tool, resolvedArgs, source, liveContext.foregroundApp)
            }.getOrElse { error ->
                val message = error.message ?: "Tool execution error"
                transcript.appendLine("Execution error: $message")
                emit(AgentEvent.ToolFailed(tool = step.tool, message = message))
                steps.replaceLast(status = AgentStepStatus.FAILED, message = message)
                publishSteps()
                return failStep(
                    definition = definition,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = message,
                    transcript = transcript,
                    events = events,
                    steps = steps,
                    publishSteps = ::publishSteps,
                    emit = ::emit,
                    alreadyEmittedToolFailure = true,
                )
            }

            emit(AgentEvent.toolResult(step.tool, result))
            transcript.appendLine(
                "Tool result: ${result.ok} ${SensitiveTextRedactor.redact(result.message)}"
            )

            if (!result.ok) {
                steps.replaceLast(status = AgentStepStatus.FAILED, message = result.message, result = result)
                publishSteps()
                return failStep(
                    definition = definition,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = result.message,
                    transcript = transcript,
                    events = events,
                    steps = steps,
                    publishSteps = ::publishSteps,
                    emit = ::emit,
                    alreadyEmittedToolFailure = true,
                )
            }

            steps.replaceLast(status = AgentStepStatus.OK, message = result.message, result = result)
            emit(
                AgentEvent.WorkflowStepCompleted(
                    workflowId = definition.id,
                    stepIndex = stepIndex,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    success = true,
                    message = result.message,
                )
            )
            completedSteps += 1
            publishSteps()

            val expectedState = step.expectedState?.toExpectedState()
            if (expectedState != null) {
                val verifySequence = steps.size + 1
                steps += AgentStep(
                    sequenceNumber = verifySequence,
                    type = AgentStepType.VERIFY,
                    status = AgentStepStatus.RUNNING,
                    inputSummary = SensitiveTextRedactor.redact("Verifying workflow step $stepIndex"),
                    outputSummary = "",
                    startedAtMillis = System.currentTimeMillis(),
                )
                publishSteps()

                val verification = verifier.verify(expectedState, step.timeoutMs)
                if (verification.passed) {
                    emit(
                        AgentEvent.WorkflowStepVerificationPassed(
                            stepIndex = stepIndex,
                            tool = step.tool,
                            expectedSummary = verification.expectedSummary,
                            observedSummary = verification.observedSummary,
                        )
                    )
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
                        stepIndex = stepIndex,
                        tool = step.tool,
                        expectedSummary = verification.expectedSummary,
                        observedSummary = verification.observedSummary,
                        reason = verification.reason,
                    )
                    emit(failedEvent)
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
                    publishSteps()
                    return failVerification(
                        definition = definition,
                        stepIndex = stepIndex,
                        totalSteps = totalSteps,
                        completedSteps = completedSteps - 1,
                        message = failedEvent.userMessage,
                        transcript = transcript,
                        events = events,
                        steps = steps,
                        publishSteps = ::publishSteps,
                        emit = ::emit,
                    )
                }
            }
        }

        val completionMessage = "Workflow \"${definition.title}\" completed successfully."
        transcript.appendLine(completionMessage)
        emit(
            AgentEvent.WorkflowReplayDone(
                workflowId = definition.id,
                title = definition.title,
                success = true,
                completedSteps = totalSteps,
                totalSteps = totalSteps,
                message = completionMessage,
            )
        )
        emit(AgentEvent.FinalAnswer(completionMessage))
        steps += AgentStepFactory.stop(
            sequenceNumber = totalSteps + 1,
            reason = AgentStepStopReason.COMPLETED,
            outputSummary = completionMessage,
            inputSummary = "workflow replay finished",
        )
        publishSteps()

        return AgentRunResult(
            transcript = transcript.toString(),
            finalAnswer = completionMessage,
            events = events,
            steps = steps,
            stopReason = AgentStepStopReason.COMPLETED,
            stopMessage = AgentStepStopReason.COMPLETED.userMessage,
        )
    }

    private fun requestPreflightConfirmation(warning: String): Boolean {
        val carrier = AndroidToolCatalog.find("observe_screen") ?: return false
        val decision = PolicyDecision.RequireApproval(
            reason = "foreground app mismatch",
            userMessage = "Proceed with workflow replay anyway?",
            dataAffected = warning,
            ifApproved = "TouchPilot will replay the workflow on the current screen.",
        )
        return approvalProvider.approve(ToolApprovalRequest(carrier, emptyMap(), decision))
    }

    private fun failStep(
        definition: WorkflowDefinition,
        stepIndex: Int,
        totalSteps: Int,
        tool: String,
        message: String,
        transcript: StringBuilder,
        events: MutableList<AgentEvent>,
        steps: MutableList<AgentStep>,
        publishSteps: () -> Unit,
        emit: (AgentEvent) -> Unit,
        alreadyEmittedToolFailure: Boolean = false,
    ): AgentRunResult {
        if (!alreadyEmittedToolFailure) {
            emit(AgentEvent.ToolFailed(tool = tool, message = message))
        }
        emit(
            AgentEvent.WorkflowStepCompleted(
                workflowId = definition.id,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                tool = tool,
                success = false,
                message = message,
            )
        )
        val failureMessage = "Workflow \"${definition.title}\" failed at step $stepIndex: $message"
        transcript.appendLine(failureMessage)
        emit(
            AgentEvent.WorkflowReplayDone(
                workflowId = definition.id,
                title = definition.title,
                success = false,
                completedSteps = stepIndex - 1,
                totalSteps = totalSteps,
                message = failureMessage,
            )
        )
        emit(AgentEvent.FinalAnswer(failureMessage))
        steps += AgentStepFactory.stop(
            sequenceNumber = stepIndex + 1,
            reason = AgentStepStopReason.EXECUTOR_ERROR,
            outputSummary = failureMessage,
            inputSummary = "workflow replay failed",
        )
        publishSteps()
        return stopped(
            transcript = transcript,
            events = events,
            steps = steps,
            reason = AgentStepStopReason.EXECUTOR_ERROR,
            finalAnswer = failureMessage,
        )
    }

    private fun failVerification(
        definition: WorkflowDefinition,
        stepIndex: Int,
        totalSteps: Int,
        completedSteps: Int,
        message: String,
        transcript: StringBuilder,
        events: MutableList<AgentEvent>,
        steps: MutableList<AgentStep>,
        publishSteps: () -> Unit,
        emit: (AgentEvent) -> Unit,
    ): AgentRunResult {
        val failureMessage = "Workflow \"${definition.title}\" failed at step $stepIndex: $message"
        transcript.appendLine(failureMessage)
        emit(
            AgentEvent.WorkflowReplayDone(
                workflowId = definition.id,
                title = definition.title,
                success = false,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                message = failureMessage,
            )
        )
        emit(AgentEvent.FinalAnswer(failureMessage))
        steps += AgentStepFactory.stop(
            sequenceNumber = steps.size + 1,
            reason = AgentStepStopReason.VERIFICATION_FAILED,
            outputSummary = failureMessage,
            inputSummary = "workflow replay failed verification",
        )
        publishSteps()
        return stopped(
            transcript = transcript,
            events = events,
            steps = steps,
            reason = AgentStepStopReason.VERIFICATION_FAILED,
            finalAnswer = failureMessage,
        )
    }

    private fun stopped(
        transcript: StringBuilder,
        events: List<AgentEvent>,
        steps: List<AgentStep>,
        reason: AgentStepStopReason,
        finalAnswer: String?,
    ): AgentRunResult {
        transcript.appendLine("Stopped: ${reason.name.lowercase()} - ${reason.userMessage}")
        return AgentRunResult(
            transcript = transcript.toString(),
            finalAnswer = finalAnswer,
            events = events,
            steps = steps,
            stopReason = reason,
            stopMessage = reason.userMessage,
        )
    }

    private fun MutableList<AgentStep>.replaceLast(
        status: AgentStepStatus,
        message: String,
        result: ToolResult? = null,
    ) {
        val last = removeAt(lastIndex)
        add(
            last.copy(
                status = status,
                outputSummary = SensitiveTextRedactor.redact(message),
                toolCall = last.toolCall?.copy(
                    result = result?.let {
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

fun AgentRunResult.toWorkflowReplayResult(definition: WorkflowDefinition): WorkflowReplayResult {
    val completed = events.filterIsInstance<AgentEvent.WorkflowStepCompleted>().count { it.success }
    val verificationCards = events.mapNotNull { event ->
        when (event) {
            is AgentEvent.WorkflowStepVerificationPassed -> StepVerificationCardModel.from(event)
            is AgentEvent.WorkflowStepVerificationFailed -> StepVerificationCardModel.from(event)
            else -> null
        }
    }
    return WorkflowReplayResult(
        workflowId = definition.id,
        workflowTitle = definition.title,
        events = events,
        steps = steps,
        stopReason = stopReason ?: AgentStepStopReason.EXECUTOR_ERROR,
        stopMessage = stopMessage.ifBlank { stopReason?.userMessage.orEmpty() },
        completedStepCount = completed,
        verificationCards = verificationCards,
    )
}

fun buildWorkflowReplayEngine(
    toolExecutor: AndroidToolExecutor,
    approvalProvider: ToolApprovalProvider,
    policy: ActionPolicy = DefaultActionPolicy(),
    liveContextProvider: WorkflowLivePolicyContextProvider =
        DefaultWorkflowLivePolicyContextProvider,
    verifier: WorkflowStepVerifier = WorkflowStateVerifier(),
    cancellationSignal: AtomicBoolean = AtomicBoolean(false),
): WorkflowReplayEngine {
    return WorkflowReplayEngine(
        tools = WorkflowReplayLoopTools(toolExecutor, liveContextProvider),
        policy = policy,
        approvalProvider = approvalProvider,
        liveContextProvider = liveContextProvider,
        verifier = verifier,
        cancellationSignal = cancellationSignal,
    )
}

private class WorkflowReplayLoopTools(
    private val toolExecutor: AndroidToolExecutor,
    private val liveContextProvider: WorkflowLivePolicyContextProvider,
) : LocalAgentLoopTools {
    override fun observeScreen(): String = liveContextProvider.current().activeScreen

    override fun foregroundApp(): ForegroundAppInfo = liveContextProvider.current().foregroundApp

    override fun validate(name: String, args: Map<String, String>): String? {
        return toolExecutor.validate(name, args)
    }

    override fun findTool(name: String): ToolSpec? = AndroidToolCatalog.find(name)

    override fun execute(
        name: String,
        args: Map<String, String>,
        source: ToolSource,
        foregroundApp: ForegroundAppInfo,
    ): ToolResult {
        return toolExecutor.execute(name, args, source, foregroundApp)
    }

    override fun recordExecution(name: String, args: String, ok: Boolean, message: String) {
        ToolExecutionLog.record(name, args, ok, message)
    }
}
