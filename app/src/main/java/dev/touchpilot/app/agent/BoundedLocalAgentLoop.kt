package dev.touchpilot.app.agent

import dev.touchpilot.app.androidcontrol.ForegroundAppInfo
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.PolicyDecision
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolPolicyRequest
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.ToolResult
import dev.touchpilot.app.tools.ToolSpec

class BoundedLocalAgentLoop(
    private val tools: LocalAgentLoopTools,
    private val approvalProvider: ToolApprovalProvider,
    private val commandProvider: AgentCommandProvider,
    private val skill: Skill? = null,
    private val source: ToolSource = ToolSource.LOCAL_ROUTER,
    private val policy: ActionPolicy = DefaultActionPolicy(),
    private val cancellationSignal: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false),
    private val clarificationDecider: ClarificationDecider = ClarificationDecider()
) {
    fun run(
        task: String,
        limits: AgentRunLimits = AgentRunLimits(),
        listener: AgentEventListener = AgentEventListener {},
        onStepsUpdated: ((List<AgentStep>) -> Unit)? = null
    ): AgentRunResult {
        val transcript = StringBuilder()
        val events = mutableListOf<AgentEvent>()
        val steps = mutableListOf<AgentStep>()

        fun emit(event: AgentEvent) {
            events += event
            listener.onEvent(event)
        }

        fun publishSteps() {
            onStepsUpdated?.invoke(steps.toList())
        }

        emit(AgentEvent.UserMessage(task))
        publishSteps()

        var currentScreen = tools.observeScreen()
        var context = initialContext(task, currentScreen)
        var nextStepContext = NextStepContext.initial(task)
        var consecutiveFailures = 0

        repeat(limits.maxSteps) { step ->
            val stepIndex = step + 1
            if (cancellationSignal.get()) {
                transcript.appendLine("Run cancelled by user at step $stepIndex")
                events += AgentEvent.RunCancelled(reason = "Cancelled by user")
                steps += AgentStepFactory.stop(
                    sequenceNumber = stepIndex,
                    reason = AgentStepStopReason.USER_CANCELLED,
                    outputSummary = "Cancelled by user",
                    inputSummary = "observed ${currentScreen.length} character(s)"
                )
                return stopped(transcript, events, steps, AgentStepStopReason.USER_CANCELLED)
            }
            transcript.appendLine("Step $stepIndex")
            val raw = runCatching {
                commandProvider.complete(AgentPrompts.systemPrompt(skill), context)
            }.getOrElse { error ->
                transcript.appendLine("Command provider error: ${error.message}")
                steps += invalidStep(stepIndex, currentScreen, "Command provider error: ${error.message}")
                publishSteps()
                return stopped(transcript, events, steps, AgentStepStopReason.NO_VALID_ACTION)
            }
            transcript.appendLine("Model: $raw")

            val command = runCatching {
                AgentCommandParser.parse(raw)
            }.getOrElse { error ->
                transcript.appendLine("Command parse error: ${error.message}")
                steps += invalidStep(stepIndex, currentScreen, "Command parse error: ${error.message}")
                publishSteps()
                return stopped(transcript, events, steps, AgentStepStopReason.NO_VALID_ACTION)
            }
            if (command.finalAnswer != null) {
                transcript.appendLine("Final: ${command.finalAnswer}")
                emit(AgentEvent.FinalAnswer(command.finalAnswer))
                steps += AgentStepFactory.stop(
                    sequenceNumber = stepIndex,
                    reason = AgentStepStopReason.COMPLETED,
                    outputSummary = command.finalAnswer,
                    inputSummary = "final answer produced"
                )
                publishSteps()
                return AgentRunResult(
                    transcript = transcript.toString(),
                    finalAnswer = command.finalAnswer,
                    events = events,
                    steps = steps,
                    stopReason = AgentStepStopReason.COMPLETED,
                    stopMessage = AgentStepStopReason.COMPLETED.userMessage
                )
            }

            when (val clarification = clarificationDecider.decide(nextStepContext, command)) {
                is ClarificationDecision.Clarify -> {
                    transcript.appendLine(
                        "Clarification: ${clarification.clarificationReason.wireName} - ${clarification.question}"
                    )
                    events += AgentEvent.Clarification(
                        reason = clarification.clarificationReason,
                        question = clarification.question,
                        detail = clarification.detail,
                        candidates = clarification.candidates,
                        tool = command.tool
                    )
                    steps += AgentStepFactory.clarify(
                        sequenceNumber = stepIndex,
                        clarification = AgentStepClarification(
                            reason = clarification.clarificationReason.toAgentStepReason(),
                            question = clarification.question,
                            detail = clarification.detail,
                            candidateLabels = clarification.candidates.map { it.displayLabel }
                        ),
                        inputSummary = "agent paused before dispatching the next action",
                        endedAtMillis = System.currentTimeMillis()
                    )
                    return AgentRunResult(
                        transcript = transcript.toString(),
                        finalAnswer = clarification.question,
                        events = events,
                        steps = steps,
                        stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
                        stopMessage = clarification.question
                    )
                }
                ClarificationDecision.Continue -> Unit
            }

            val toolName = command.tool
            if (toolName == null) {
                transcript.appendLine("No tool or final answer returned.")
                steps += invalidStep(stepIndex, currentScreen, "No tool or final answer returned.")
                publishSteps()
                return stopped(transcript, events, steps, AgentStepStopReason.NO_VALID_ACTION)
            }
            val redactedArgs = SensitiveTextRedactor.redact(command.args)
            steps += AgentStepFactory.act(
                sequenceNumber = stepIndex,
                tool = toolName,
                args = redactedArgs,
                source = source.name.lowercase(),
                inputSummary = "observed ${currentScreen.length} character(s)",
                outputSummary = "Tool selected.",
                status = AgentStepStatus.PENDING
            )
            AgentEvent.toolRequested(command, source)?.let(::emit)
            publishSteps()

            val validationError = tools.validate(toolName, command.args)
            val spec = tools.findTool(toolName)
            val foregroundApp = tools.foregroundApp()
            val allowlistError = validateSkillAllowlist(toolName)
            if (allowlistError != null) {
                transcript.appendLine("Skill allowlist denied tool: $allowlistError")
                tools.recordExecution(
                    name = toolName,
                    args = "skill=${skill?.id.orEmpty()}",
                    ok = false,
                    message = "denied by skill allowlist"
                )
                emit(
                    AgentEvent.PolicyBlocked(
                        tool = toolName,
                        reason = "denied by skill allowlist",
                        userMessage = allowlistError
                    )
                )
                steps.replaceLast(
                    status = AgentStepStatus.BLOCKED,
                    message = allowlistError,
                    stopReason = AgentStepStopReason.POLICY_BLOCKED
                )
                publishSteps()
                return stopped(transcript, events, steps, AgentStepStopReason.POLICY_BLOCKED)
            } else if (validationError != null) {
                transcript.appendLine("Tool validation failed: $validationError")
                steps.replaceLast(
                    status = AgentStepStatus.FAILED,
                    message = validationError,
                    stopReason = AgentStepStopReason.NO_VALID_ACTION
                )
                publishSteps()
                return stopped(transcript, events, steps, AgentStepStopReason.NO_VALID_ACTION)
            } else if (spec != null) {
                val decision = policy.evaluate(
                    ToolPolicyRequest(
                        tool = spec,
                        args = command.args,
                        source = source,
                        activeScreen = currentScreen,
                        foregroundApp = foregroundApp,
                        activeSkillId = skill?.id,
                        activeSkillTitle = skill?.title,
                        activeSkillRisk = skill?.risk
                    )
                )
                when (decision) {
                    is PolicyDecision.Allow -> {
                        transcript.appendLine("Policy allowed $toolName: ${decision.reason}")
                    }
                    is PolicyDecision.RequireApproval -> {
                        transcript.appendLine("Approval requested for $toolName: ${decision.reason}")
                        val approvalRequest = ToolApprovalRequest(spec, command.args, decision)
                        emit(AgentEvent.approvalRequired(approvalRequest))
                        publishSteps()
                        val approved = approvalProvider.approve(approvalRequest)
                        if (!approved) {
                            transcript.appendLine("Tool denied by user: $toolName")
                            tools.recordExecution(
                                name = toolName,
                                args = "risk=${spec.risk}",
                                ok = false,
                                message = "denied by user: ${decision.reason}"
                            )
                            emit(
                                AgentEvent.PolicyBlocked(
                                    tool = toolName,
                                    reason = "denied by user: ${decision.reason}",
                                    userMessage = "The user did not approve $toolName."
                                )
                            )
                            steps.replaceLast(
                                status = AgentStepStatus.BLOCKED,
                                message = "The user did not approve $toolName.",
                                stopReason = AgentStepStopReason.USER_CANCELLED
                            )
                            publishSteps()
                            return stopped(transcript, events, steps, AgentStepStopReason.USER_CANCELLED)
                        }
                        transcript.appendLine("Tool approved by user: $toolName")
                    }
                    is PolicyDecision.Deny -> {
                        transcript.appendLine("Policy denied $toolName: ${decision.reason}")
                        tools.recordExecution(toolName, "policy=deny", false, decision.userMessage)
                        AgentEvent.policyBlocked(toolName, decision)?.let(::emit)
                        steps.replaceLast(
                            status = AgentStepStatus.BLOCKED,
                            message = decision.userMessage,
                            stopReason = AgentStepStopReason.POLICY_BLOCKED
                        )
                        publishSteps()
                        return stopped(transcript, events, steps, AgentStepStopReason.POLICY_BLOCKED)
                    }
                    is PolicyDecision.Block -> {
                        transcript.appendLine("Policy blocked $toolName: ${decision.reason}")
                        tools.recordExecution(toolName, "policy=block", false, decision.userMessage)
                        AgentEvent.policyBlocked(toolName, decision)?.let(::emit)
                        steps.replaceLast(
                            status = AgentStepStatus.BLOCKED,
                            message = decision.userMessage,
                            stopReason = AgentStepStopReason.POLICY_BLOCKED
                        )
                        publishSteps()
                        return stopped(transcript, events, steps, AgentStepStopReason.POLICY_BLOCKED)
                    }
                }
            }

            steps.replaceLast(status = AgentStepStatus.RUNNING, message = "Tool is running.")
            publishSteps()
            AgentEvent.toolRunning(command, source)?.let(::emit)
            val result = runCatching {
                tools.execute(toolName, command.args, source, foregroundApp)
            }.getOrElse { error ->
                val message = error.message ?: "Tool execution error"
                transcript.appendLine("Tool execution error: $message")
                emit(AgentEvent.ToolFailed(tool = toolName, message = message))
                consecutiveFailures += 1
                steps.replaceLast(
                    status = AgentStepStatus.FAILED,
                    message = message,
                    stopReason = if (consecutiveFailures >= limits.maxConsecutiveFailures) {
                        AgentStepStopReason.REPEATED_TOOL_FAILURE
                    } else {
                        null
                    }
                )
                publishSteps()
                if (consecutiveFailures >= limits.maxConsecutiveFailures) {
                    return stopped(transcript, events, steps, AgentStepStopReason.REPEATED_TOOL_FAILURE)
                }
                currentScreen = tools.observeScreen()
                context = recoveryContext(task, transcript.toString(), currentScreen)
                nextStepContext = NextStepContext(
                    task = task,
                    stepNumber = stepIndex,
                    previousCommand = command,
                    previousToolResult = ToolResult(ok = false, message = message),
                    failureReason = message
                )
                return@repeat
            }
            emit(AgentEvent.toolResult(toolName, result))
            transcript.appendLine("Tool result: ${result.ok} ${SensitiveTextRedactor.redact(result.message)}")
            if (result.data.isNotEmpty()) {
                transcript.appendLine("Tool data: ${SensitiveTextRedactor.redact(result.data)}")
            }

            val verificationScreen = if (toolName == "observe_screen") {
                result.message
            } else {
                tools.observeScreen()
            }
            transcript.appendLine("Verification screen length: ${verificationScreen.length}")

            if (result.ok) {
                consecutiveFailures = 0
                steps.replaceLast(
                    status = AgentStepStatus.OK,
                    message = result.message,
                    verificationStatus = result.data["verification_status"],
                    result = result
                )
            } else {
                consecutiveFailures += 1
                steps.replaceLast(
                    status = AgentStepStatus.FAILED,
                    message = result.message,
                    verificationStatus = result.data["verification_status"],
                    result = result,
                    stopReason = if (consecutiveFailures >= limits.maxConsecutiveFailures) {
                        AgentStepStopReason.REPEATED_TOOL_FAILURE
                    } else {
                        null
                    }
                )
                if (consecutiveFailures >= limits.maxConsecutiveFailures) {
                    publishSteps()
                    return stopped(transcript, events, steps, AgentStepStopReason.REPEATED_TOOL_FAILURE)
                }
            }
            publishSteps()

            currentScreen = verificationScreen
            context = nextContext(task, transcript.toString(), verificationScreen)
            nextStepContext = NextStepContext(
                task = task,
                stepNumber = stepIndex,
                previousCommand = command,
                previousToolResult = result,
                failureReason = if (result.ok) null else result.message,
                candidateTargets = if (result.ok) {
                    emptyList()
                } else {
                    ClarificationFromToolResult.candidatesFromResult(result)
                },
            )
        }

        publishSteps()
        return stopped(transcript, events, steps, AgentStepStopReason.MAX_STEPS)
    }

    private fun stopped(
        transcript: StringBuilder,
        events: List<AgentEvent>,
        steps: List<AgentStep>,
        reason: AgentStepStopReason
    ): AgentRunResult {
        transcript.appendLine("Stopped: ${reason.name.lowercase()} - ${reason.userMessage}")
        return AgentRunResult(
            transcript = transcript.toString(),
            finalAnswer = null,
            events = events,
            steps = steps,
            stopReason = reason,
            stopMessage = reason.userMessage
        )
    }

    private fun initialContext(task: String, screen: String): String {
        return "User task: $task\n\nCurrent screen:\n${SensitiveTextRedactor.redact(screen)}"
    }

    private fun nextContext(task: String, transcript: String, verificationScreen: String): String {
        return buildString {
            appendLine("User task: $task")
            appendLine("Previous transcript:")
            appendLine(transcript)
            appendLine("Verification screen:")
            appendLine(SensitiveTextRedactor.redact(verificationScreen))
        }
    }

    private fun recoveryContext(task: String, transcript: String, currentScreen: String): String {
        return buildString {
            appendLine("User task: $task")
            appendLine("Previous transcript:")
            appendLine(transcript)
            appendLine("Current screen:")
            appendLine(SensitiveTextRedactor.redact(currentScreen))
            appendLine("Recover from the last error or return a final answer if recovery is unsafe.")
        }
    }

    private fun validateSkillAllowlist(toolName: String): String? {
        val activeSkill = skill ?: return null
        if (toolName in activeSkill.allowedTools) return null
        return "$toolName is not allowed by ${activeSkill.title}"
    }

    private fun invalidStep(stepIndex: Int, screen: String, message: String): AgentStep {
        return AgentStepFactory.stop(
            sequenceNumber = stepIndex,
            reason = AgentStepStopReason.NO_VALID_ACTION,
            outputSummary = message,
            inputSummary = "observed ${screen.length} character(s)"
        )
    }

    private fun MutableList<AgentStep>.replaceLast(
        status: AgentStepStatus,
        message: String,
        verificationStatus: String? = null,
        stopReason: AgentStepStopReason? = null,
        result: ToolResult? = null
    ) {
        val last = removeAt(lastIndex)
        val redactedMessage = SensitiveTextRedactor.redact(message)
        add(
            last.copy(
                status = status,
                outputSummary = redactedMessage,
                toolCall = last.toolCall?.copy(
                    result = result?.let { AgentStepToolResult.of(it, redactOnConstruct = true) }
                        ?: last.toolCall.result
                ),
                verification = verificationStatus?.let {
                    AgentStepVerification(
                        status = SensitiveTextRedactor.redact(it),
                        reason = redactedMessage
                    )
                } ?: last.verification,
                stopReason = stopReason
            )
        )
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
}

interface LocalAgentLoopTools {
    fun observeScreen(): String
    fun foregroundApp(): ForegroundAppInfo = ForegroundAppInfo.Disconnected
    fun validate(name: String, args: Map<String, String>): String?
    fun findTool(name: String): ToolSpec?
    fun execute(
        name: String,
        args: Map<String, String>,
        source: ToolSource,
        foregroundApp: ForegroundAppInfo = ForegroundAppInfo.Disconnected
    ): ToolResult
    fun recordExecution(name: String, args: String, ok: Boolean, message: String) = Unit
}
