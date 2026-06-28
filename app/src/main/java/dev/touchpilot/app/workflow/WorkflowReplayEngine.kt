package dev.touchpilot.app.workflow

import dev.touchpilot.app.agent.AgentCommand
import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepFactory
import dev.touchpilot.app.agent.AgentStepStatus
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepVerification
import dev.touchpilot.app.agent.LocalAgentLoopTools
import dev.touchpilot.app.agent.StepVerificationCardModel
import dev.touchpilot.app.agent.userMessage
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
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
 * Deterministic workflow replay with per-step [ExpectedState] verification.
 * Aborts on the first failed tool call or unverified step.
 */
class WorkflowReplayEngine(
    private val tools: LocalAgentLoopTools,
    private val approvalProvider: ToolApprovalProvider,
    private val verifier: WorkflowStepVerifier = WorkflowStateVerifier(),
    private val source: ToolSource = ToolSource.WORKFLOW_REPLAY,
    private val policy: ActionPolicy = DefaultActionPolicy(),
    private val cancellationSignal: AtomicBoolean = AtomicBoolean(false),
) {
    fun replay(
        workflow: WorkflowDefinition,
        parameters: Map<String, String> = emptyMap(),
        listener: AgentEventListener = AgentEventListener {},
        onStepsUpdated: ((List<AgentStep>) -> Unit)? = null,
    ): WorkflowReplayResult {
        val resolvedParameters = WorkflowParameterSubstitutor.resolveParameters(workflow, parameters)
        val events = mutableListOf<AgentEvent>()
        val steps = mutableListOf<AgentStep>()
        val verificationCards = mutableListOf<StepVerificationCardModel>()
        var completedSteps = 0
        val totalSteps = workflow.steps.size

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
        if (!workflow.expectedForegroundPackage.isNullOrBlank()) {
            preflightForegroundWarning(workflow, tools.foregroundApp())?.let(::emit)
        }
        publishSteps()

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
                return terminalFailure(
                    workflow = workflow,
                    stepNumber = stepNumber,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = "Workflow \"${workflow.title}\" cancelled.",
                    stopReason = AgentStepStopReason.USER_CANCELLED,
                    completedStepCount = completedSteps,
                    events = events,
                    steps = steps,
                    verificationCards = verificationCards,
                    emit = ::emit,
                    emitStepCompleted = false,
                )
            }

            val resolvedArgs = WorkflowParameterSubstitutor.substitute(step.args, resolvedParameters)
            val currentScreen = tools.observeScreen()
            val foregroundApp = tools.foregroundApp()

            emit(
                AgentEvent.WorkflowStepStarted(
                    workflowId = workflow.id,
                    workflowTitle = workflow.title,
                    stepIndex = stepNumber,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    args = resolvedArgs,
                )
            )

            val command = AgentCommand(tool = step.tool, args = resolvedArgs, finalAnswer = null)
            val redactedArgs = SensitiveTextRedactor.redact(resolvedArgs)
            steps += AgentStepFactory.act(
                sequenceNumber = stepNumber,
                tool = step.tool,
                args = redactedArgs,
                source = source.name.lowercase(),
                inputSummary = "workflow step $stepNumber of $totalSteps",
                outputSummary = "Tool selected.",
                status = AgentStepStatus.PENDING,
            )
            AgentEvent.toolRequested(command, source)?.let(::emit)
            publishSteps()

            val validationError = tools.validate(step.tool, resolvedArgs)
            val spec = tools.findTool(step.tool)
            if (validationError != null) {
                steps.replaceLastAct(
                    status = AgentStepStatus.FAILED,
                    message = validationError,
                    stopReason = AgentStepStopReason.NO_VALID_ACTION,
                )
                publishSteps()
                return terminalFailure(
                    workflow = workflow,
                    stepNumber = stepNumber,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = validationError,
                    stopReason = AgentStepStopReason.NO_VALID_ACTION,
                    completedStepCount = completedSteps,
                    events = events,
                    steps = steps,
                    verificationCards = verificationCards,
                    emit = ::emit,
                    publishSteps = ::publishSteps,
                    extraSteps = listOf(
                        stopStep(
                            stepNumber = stepNumber + 1,
                            reason = AgentStepStopReason.NO_VALID_ACTION,
                            message = validationError,
                        )
                    ),
                )
            }

            if (spec != null) {
                val decision = policy.evaluate(
                    ToolPolicyRequest(
                        tool = spec,
                        args = resolvedArgs,
                        source = source,
                        activeScreen = currentScreen,
                        foregroundApp = foregroundApp,
                    )
                )
                when (decision) {
                    is PolicyDecision.Allow -> Unit
                    is PolicyDecision.RequireApproval -> {
                        val approvalRequest = ToolApprovalRequest(spec, resolvedArgs, decision)
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
                            return terminalFailure(
                                workflow = workflow,
                                stepNumber = stepNumber,
                                totalSteps = totalSteps,
                                tool = step.tool,
                                message = "Replay stopped because approval was denied.",
                                stopReason = AgentStepStopReason.USER_CANCELLED,
                                completedStepCount = completedSteps,
                                events = events,
                                steps = steps,
                                verificationCards = verificationCards,
                                emit = ::emit,
                                publishSteps = ::publishSteps,
                                extraSteps = listOf(
                                    stopStep(
                                        stepNumber = stepNumber + 1,
                                        reason = AgentStepStopReason.USER_CANCELLED,
                                        message = "Replay stopped because approval was denied.",
                                    )
                                ),
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
                        return terminalFailure(
                            workflow = workflow,
                            stepNumber = stepNumber,
                            totalSteps = totalSteps,
                            tool = step.tool,
                            message = decision.userMessage,
                            stopReason = AgentStepStopReason.POLICY_BLOCKED,
                            completedStepCount = completedSteps,
                            events = events,
                            steps = steps,
                            verificationCards = verificationCards,
                            emit = ::emit,
                            publishSteps = ::publishSteps,
                            extraSteps = listOf(
                                stopStep(
                                    stepNumber = stepNumber + 1,
                                    reason = AgentStepStopReason.POLICY_BLOCKED,
                                    message = decision.userMessage,
                                )
                            ),
                        )
                    }
                }
            }

            steps.replaceLastAct(status = AgentStepStatus.RUNNING, message = "Tool is running.")
            publishSteps()
            AgentEvent.toolRunning(command, source)?.let(::emit)

            val result = runCatching {
                tools.execute(step.tool, resolvedArgs, source, foregroundApp)
            }.getOrElse { error ->
                val message = error.message ?: "Tool execution error"
                emit(AgentEvent.ToolFailed(tool = step.tool, message = message))
                steps.replaceLastAct(
                    status = AgentStepStatus.FAILED,
                    message = message,
                    stopReason = AgentStepStopReason.EXECUTOR_ERROR,
                )
                publishSteps()
                return terminalFailure(
                    workflow = workflow,
                    stepNumber = stepNumber,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = message,
                    stopReason = AgentStepStopReason.EXECUTOR_ERROR,
                    completedStepCount = completedSteps,
                    events = events,
                    steps = steps,
                    verificationCards = verificationCards,
                    emit = ::emit,
                    publishSteps = ::publishSteps,
                    alreadyEmittedToolFailure = true,
                    extraSteps = listOf(
                        stopStep(
                            stepNumber = stepNumber + 1,
                            reason = AgentStepStopReason.EXECUTOR_ERROR,
                            message = message,
                        )
                    ),
                )
            }

            emit(AgentEvent.toolResult(step.tool, result))

            if (!result.ok) {
                steps.replaceLastAct(
                    status = AgentStepStatus.FAILED,
                    message = result.message,
                    stopReason = AgentStepStopReason.REPEATED_TOOL_FAILURE,
                    toolResult = result,
                )
                publishSteps()
                return terminalFailure(
                    workflow = workflow,
                    stepNumber = stepNumber,
                    totalSteps = totalSteps,
                    tool = step.tool,
                    message = result.message,
                    stopReason = AgentStepStopReason.REPEATED_TOOL_FAILURE,
                    completedStepCount = completedSteps,
                    events = events,
                    steps = steps,
                    verificationCards = verificationCards,
                    emit = ::emit,
                    publishSteps = ::publishSteps,
                    alreadyEmittedToolFailure = true,
                    extraSteps = listOf(
                        stopStep(
                            stepNumber = stepNumber + 1,
                            reason = AgentStepStopReason.REPEATED_TOOL_FAILURE,
                            message = result.message,
                        )
                    ),
                )
            }

            steps.replaceLastAct(
                status = AgentStepStatus.OK,
                message = result.message,
                toolResult = result,
            )
            publishSteps()

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
                    completedSteps += 1
                    emit(
                        AgentEvent.WorkflowStepCompleted(
                            workflowId = workflow.id,
                            stepIndex = stepNumber,
                            totalSteps = totalSteps,
                            tool = step.tool,
                            success = true,
                            message = result.message,
                        )
                    )
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
                    return terminalFailure(
                        workflow = workflow,
                        stepNumber = stepNumber,
                        totalSteps = totalSteps,
                        tool = step.tool,
                        message = failedEvent.userMessage,
                        stopReason = AgentStepStopReason.VERIFICATION_FAILED,
                        completedStepCount = completedSteps,
                        events = events,
                        steps = steps,
                        verificationCards = verificationCards,
                        emit = ::emit,
                        publishSteps = ::publishSteps,
                        stopMessage = failedEvent.userMessage,
                    )
                }
            } else {
                completedSteps += 1
                emit(
                    AgentEvent.WorkflowStepCompleted(
                        workflowId = workflow.id,
                        stepIndex = stepNumber,
                        totalSteps = totalSteps,
                        tool = step.tool,
                        success = true,
                        message = result.message,
                    )
                )
            }
        }

        val finalMessage = "Workflow \"${workflow.title}\" replayed successfully."
        emit(
            AgentEvent.WorkflowReplayDone(
                workflowId = workflow.id,
                title = workflow.title,
                success = true,
                completedSteps = completedSteps,
                totalSteps = totalSteps,
                message = finalMessage,
            )
        )
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

    private fun terminalFailure(
        workflow: WorkflowDefinition,
        stepNumber: Int,
        totalSteps: Int,
        tool: String,
        message: String,
        stopReason: AgentStepStopReason,
        completedStepCount: Int,
        events: MutableList<AgentEvent>,
        steps: MutableList<AgentStep>,
        verificationCards: List<StepVerificationCardModel>,
        emit: (AgentEvent) -> Unit,
        publishSteps: (() -> Unit)? = null,
        alreadyEmittedToolFailure: Boolean = false,
        emitStepCompleted: Boolean = true,
        extraSteps: List<AgentStep> = emptyList(),
        stopMessage: String = message,
    ): WorkflowReplayResult {
        if (!alreadyEmittedToolFailure) {
            emit(AgentEvent.ToolFailed(tool = tool, message = message))
        }
        if (emitStepCompleted) {
            emit(
                AgentEvent.WorkflowStepCompleted(
                    workflowId = workflow.id,
                    stepIndex = stepNumber,
                    totalSteps = totalSteps,
                    tool = tool,
                    success = false,
                    message = message,
                )
            )
        }
        val failureMessage = when (stopReason) {
            AgentStepStopReason.USER_CANCELLED -> message
            else -> "Workflow \"${workflow.title}\" failed at step $stepNumber: $message"
        }
        emit(
            AgentEvent.WorkflowReplayDone(
                workflowId = workflow.id,
                title = workflow.title,
                success = false,
                completedSteps = completedStepCount,
                totalSteps = totalSteps,
                message = failureMessage,
            )
        )
        extraSteps.forEach { steps += it }
        publishSteps?.invoke()
        return replayResult(
            workflow = workflow,
            events = events,
            steps = steps,
            stopReason = stopReason,
            completedStepCount = completedStepCount,
            verificationCards = verificationCards,
            stopMessage = stopMessage,
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

    private fun preflightForegroundWarning(
        workflow: WorkflowDefinition,
        foregroundApp: ForegroundAppInfo,
    ): AgentEvent.AssistantMessage? {
        val expectedPackage = workflow.expectedForegroundPackage?.takeIf { it.isNotBlank() } ?: return null
        val actualPackage = foregroundApp.packageName?.takeIf { it.isNotBlank() }
        if (actualPackage == expectedPackage) return null
        val actual = actualPackage ?: if (foregroundApp.accessibilityConnected) {
            "unknown foreground app"
        } else {
            "disconnected accessibility service"
        }
        return AgentEvent.AssistantMessage(
            text = "Workflow foreground check",
            detail = "This workflow expects $expectedPackage, but replay is starting from $actual. Policy checks will still use the live screen and foreground app for every step.",
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

fun buildWorkflowReplayEngine(
    toolExecutor: AndroidToolExecutor,
    approvalProvider: ToolApprovalProvider,
    cancellationSignal: AtomicBoolean = AtomicBoolean(false),
): WorkflowReplayEngine {
    return WorkflowReplayEngine(
        tools = AndroidLoopTools(toolExecutor),
        approvalProvider = approvalProvider,
        cancellationSignal = cancellationSignal,
    )
}

private class AndroidLoopTools(
    private val toolExecutor: AndroidToolExecutor,
) : LocalAgentLoopTools {
    override fun observeScreen(): String = toolExecutor.observeScreen()

    override fun foregroundApp(): ForegroundAppInfo = AccessibilityBridge.getForegroundApp()

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
