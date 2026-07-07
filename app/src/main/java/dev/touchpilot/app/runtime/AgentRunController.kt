package dev.touchpilot.app.runtime

import dev.touchpilot.app.agent.AgentEvent
import dev.touchpilot.app.agent.AgentEventListener
import dev.touchpilot.app.agent.AgentProviderMode
import dev.touchpilot.app.agent.AgentRunDetailFormatter
import dev.touchpilot.app.agent.AgentRunIds
import dev.touchpilot.app.agent.AgentRunRecord
import dev.touchpilot.app.agent.AgentRunResult
import dev.touchpilot.app.agent.AgentRunState
import dev.touchpilot.app.agent.AgentScreenRecord
import dev.touchpilot.app.agent.AgentStep
import dev.touchpilot.app.agent.AgentStepStopReason
import dev.touchpilot.app.agent.AgentStepTimelineBuilder
import dev.touchpilot.app.agent.ConversationalGate
import dev.touchpilot.app.agent.LocalReasoningCore
import dev.touchpilot.app.agent.SkillUseCardModel
import dev.touchpilot.app.agent.ToolCallCardModel
import dev.touchpilot.app.androidcontrol.AccessibilityBridge
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.ToolApprovalRequest
import dev.touchpilot.app.demonstration.DemonstrationSessionManager
import dev.touchpilot.app.demonstration.formatting.DemonstrationSummaryFormatter
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowTrace
import dev.touchpilot.app.workflow.WorkflowTraceStore
import dev.touchpilot.app.workflow.WorkflowTraceSummarizer
import dev.touchpilot.app.ui.chat.ChatEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

class AgentRunController(
    private val reasoningCore: LocalReasoningCore,
    private val conversation: MutableList<ChatEvent>,
    private val currentProviderMode: () -> AgentProviderMode,
    private val runtimeWorkingDetail: () -> String,
    private val runOnUiThread: (() -> Unit) -> Unit,
    private val showChat: () -> Unit,
    private val refreshExecutionLog: () -> Unit,
    private val refreshStatus: () -> Unit,
    private val refreshStepTimeline: (ChatEvent.StepTimeline, List<AgentStep>, Boolean) -> Unit,
    private val demonstrationManager: DemonstrationSessionManager? = null,
    private val workflowTraceStore: WorkflowTraceStore,
) {
    private var pendingClarification: PendingClarification? = null
    private var cancellationSignal: AtomicBoolean = AtomicBoolean(false)
    private val mutableRunHistory = mutableListOf<AgentRunRecord>()
    var runState: AgentRunState = AgentRunState.IDLE
        private set

    val runHistory: List<AgentRunRecord>
        get() = mutableRunHistory

    /** Workflow traces captured from successful runs this session (issue #289). */
    val workflowTraces: List<WorkflowTrace>
        get() = workflowTraceStore.all()

    /** Demonstration sessions captured when recording mode is enabled (issue #302). */
    val demonstrationSessions
        get() = demonstrationManager?.sessions.orEmpty()

    val isDemonstrationRecording: Boolean
        get() = demonstrationManager?.isRecording == true

    fun startFromChat(task: String) {
        val pending = pendingClarification
        if (pending == null && isAgentRunInProgress(runState)) {
            rejectOverlappingRunStart()
            return
        }

        val originalTask = pending?.originalTask
        val agentTask = if (pending != null) {
            pendingClarification = null
            "${pending.originalTask}\n\nUser clarification: $task"
        } else {
            task
        }

        conversation += ChatEvent.User(task)
        ToolExecutionLog.recordChat(
            name = if (pending != null) "clarification_reply" else "user_message",
            actor = "User",
            message = task
        )

        val conversationalResponse = ConversationalGate.respond(agentTask)
        if (conversationalResponse != null) {
            conversation += ChatEvent.Agent(conversationalResponse.message, "")
            ToolExecutionLog.recordChat(
                name = "assistant_message",
                actor = "TouchPilot",
                message = conversationalResponse.message
            )
            showChat()
            return
        }

        val workingIndex = conversation.size
        cancellationSignal.set(false)
        setRunState(AgentRunState.RUNNING)
        conversation += ChatEvent.Working("Working on it.", runtimeWorkingDetail())
        val stepTimeline = ChatEvent.StepTimeline()
        conversation += stepTimeline
        showChat()

        val runId = AgentRunIds.next()
        val startedAtMillis = System.currentTimeMillis()
        val taskForRecord = originalTask ?: task
        ToolExecutionLog.recordAction(
            name = "agent_run_started",
            result = taskForRecord,
            status = "running",
            source = currentProviderMode().toLogSource()
        )
        val initialScreenRecord = AgentScreenRecord.capture(
            sequenceNumber = 0,
            phase = "initial",
            timestampMillis = startedAtMillis,
            context = AccessibilityBridge.observeScreenContext()
        )

        val demoStarted = demonstrationManager?.beginRun(
            runId = runId,
            task = taskForRecord,
            providerMode = currentProviderMode().name.lowercase(),
        )

        Thread {
            val timelineBuilder = AgentStepTimelineBuilder()
            var skillCardAdded = false
            val runOutcome = runCatching {
                reasoningCore.run(
                    task = agentTask,
                    timeline = timelineBuilder,
                    listener = AgentEventListener { event ->
                        demonstrationManager?.onAgentEvent(event)
                        if (event is AgentEvent.SkillActive && !skillCardAdded) {
                            skillCardAdded = true
                            runOnUiThread {
                                val insertIndex = conversation.indexOfFirst { it is ChatEvent.User }
                                    .let { if (it >= 0) it + 1 else conversation.size }
                                conversation.add(
                                    insertIndex,
                                    ChatEvent.SkillUse(SkillUseCardModel.from(event))
                                )
                                showChat()
                            }
                        }
                        runOnUiThread {
                            refreshStepTimeline(stepTimeline, timelineBuilder.snapshot, false)
                        }
                    },
                    cancellationSignal = cancellationSignal
                )
            }
            val completedAtMillis = System.currentTimeMillis()
            val screenRecords = listOf(
                initialScreenRecord,
                AgentScreenRecord.capture(
                    sequenceNumber = 1,
                    phase = "final",
                    timestampMillis = completedAtMillis,
                    context = AccessibilityBridge.observeScreenContext()
                )
            )
            val record = if (runOutcome.isSuccess) {
                AgentRunRecord(
                    id = runId,
                    task = taskForRecord,
                    startedAtMillis = startedAtMillis,
                    completedAtMillis = completedAtMillis,
                    result = runOutcome.getOrThrow(),
                    screenRecords = screenRecords
                )
            } else {
                AgentRunRecord(
                    id = runId,
                    task = taskForRecord,
                    startedAtMillis = startedAtMillis,
                    completedAtMillis = completedAtMillis,
                    result = null,
                    errorMessage = runOutcome.exceptionOrNull()?.message ?: "Unknown agent error",
                    screenRecords = screenRecords
                )
            }

            runOnUiThread {
                val steps = if (runOutcome.isFailure) {
                    timelineBuilder.snapshot + timelineBuilder.failureStop(
                        "Agent failed: ${runOutcome.exceptionOrNull()?.message.orEmpty()}"
                    )
                } else {
                    timelineBuilder.snapshot
                }
                val stopReason = record.result?.stopReason
                val demoCompletion = demonstrationManager?.completeRun(
                    stopReason = stopReason,
                    errorMessage = record.errorMessage,
                )
                val runFailed = chatRunFailed(
                    runOutcomeFailed = runOutcome.isFailure,
                    resultEvents = record.result?.events,
                )
                val terminalState = resolveChatRunTerminalState(
                    cancelled = cancellationSignal.get(),
                    runFailed = runFailed,
                    stopReason = stopReason,
                )
                setRunState(terminalState)
                if (terminalState == AgentRunState.FAILED) {
                    rejectPendingApprovals()
                }
                refreshStepTimeline(stepTimeline, steps, true)
                finishAgentChatRun(
                    record = record,
                    runOutcome = runOutcome,
                    workingIndex = workingIndex,
                    resumeOriginalTask = originalTask,
                    timelineSteps = steps,
                    demoCompletion = demoCompletion,
                )
            }
        }.start()

        if (demoStarted != null) {
            runOnUiThread {
                conversation += ChatEvent.DemonstrationRecording(
                    sessionId = demoStarted.sessionId,
                    active = true,
                )
                showChat()
            }
        }
    }

    fun startWorkflowReplay(
        definition: WorkflowDefinition,
        parameters: Map<String, String> = emptyMap(),
        captureWorkflowTrace: Boolean = false,
        onFinished: ((success: Boolean, message: String, result: AgentRunResult?) -> Unit)? = null,
    ) {
        if (isAgentRunInProgress(runState)) {
            val message = overlappingRunStartMessage()
            onFinished?.invoke(false, message, null)
            rejectOverlappingRunStart()
            return
        }

        cancellationSignal.set(false)
        setRunState(AgentRunState.RUNNING)

        conversation += ChatEvent.User("Replay workflow: ${definition.title}")
        ToolExecutionLog.recordChat(
            name = "workflow_replay_request",
            actor = "User",
            message = definition.title
        )

        val workingIndex = conversation.size
        conversation += ChatEvent.Working("Replaying workflow.", runtimeWorkingDetail())
        val stepTimeline = ChatEvent.StepTimeline()
        conversation += stepTimeline
        showChat()

        val runId = AgentRunIds.next()
        val startedAtMillis = System.currentTimeMillis()
        ToolExecutionLog.recordAction(
            name = "workflow_replay_started",
            result = definition.title,
            status = "running",
            source = currentProviderMode().toLogSource()
        )
        val initialScreenRecord = AgentScreenRecord.capture(
            sequenceNumber = 0,
            phase = "initial",
            timestampMillis = startedAtMillis,
            context = AccessibilityBridge.observeScreenContext()
        )

        Thread {
            val timelineBuilder = AgentStepTimelineBuilder()
            val runOutcome = runCatching {
                reasoningCore.replayWorkflow(
                    definition = definition,
                    parameters = parameters,
                    timeline = timelineBuilder,
                    listener = AgentEventListener { event ->
                        runOnUiThread {
                            refreshStepTimeline(stepTimeline, timelineBuilder.snapshot, false)
                        }
                    },
                    cancellationSignal = cancellationSignal,
                )
            }
            val completedAtMillis = System.currentTimeMillis()
            val screenRecords = listOf(
                initialScreenRecord,
                AgentScreenRecord.capture(
                    sequenceNumber = 1,
                    phase = "final",
                    timestampMillis = completedAtMillis,
                    context = AccessibilityBridge.observeScreenContext()
                )
            )
            val record = if (runOutcome.isSuccess) {
                AgentRunRecord(
                    id = runId,
                    task = definition.title,
                    startedAtMillis = startedAtMillis,
                    completedAtMillis = completedAtMillis,
                    result = runOutcome.getOrThrow(),
                    screenRecords = screenRecords
                )
            } else {
                AgentRunRecord(
                    id = runId,
                    task = definition.title,
                    startedAtMillis = startedAtMillis,
                    completedAtMillis = completedAtMillis,
                    result = null,
                    errorMessage = runOutcome.exceptionOrNull()?.message ?: "Unknown workflow error",
                    screenRecords = screenRecords
                )
            }

            runOnUiThread {
                val steps = if (runOutcome.isFailure) {
                    timelineBuilder.snapshot + timelineBuilder.failureStop(
                        "Workflow failed: ${runOutcome.exceptionOrNull()?.message.orEmpty()}"
                    )
                } else {
                    timelineBuilder.snapshot
                }
                val result = runOutcome.getOrNull()
                val completedSuccessfully = result?.stopReason == AgentStepStopReason.COMPLETED
                setRunState(
                    resolveWorkflowReplayTerminalState(
                        cancelled = cancellationSignal.get(),
                        runFailed = runOutcome.isFailure,
                        stopReason = result?.stopReason,
                    )
                )
                refreshStepTimeline(stepTimeline, steps, true)
                finishAgentChatRun(
                    record = record,
                    runOutcome = runOutcome,
                    workingIndex = workingIndex,
                    resumeOriginalTask = null,
                    timelineSteps = steps,
                    shouldCaptureWorkflowTrace = captureWorkflowTrace && completedSuccessfully,
                )
                onFinished?.invoke(
                    completedSuccessfully,
                    result?.stopMessage ?: record.errorMessage.orEmpty(),
                    result,
                )
            }
        }.start()
    }

    fun cancelRun() {
        cancellationSignal.set(true)
        demonstrationManager?.cancelRun()
        pendingClarification = null
        setRunState(AgentRunState.CANCELLED)
        rejectPendingApprovals()
        conversation += ChatEvent.Agent("Run cancelled.", "Stopped by user request.")
        showChat()
    }

    fun approveTool(request: ToolApprovalRequest): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)

        runOnUiThread {
            setRunState(AgentRunState.WAITING_APPROVAL)
            val prompt = ChatEvent.ApprovalPrompt(
                request = request,
                onDecision = { decision ->
                    approved.set(decision)
                    latch.countDown()
                }
            )
            conversation += prompt
            showChat()
        }

        val approvedByUser = latch.await(APPROVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS) && approved.get()
        runOnUiThread {
            if (!cancellationSignal.get() && runState == AgentRunState.WAITING_APPROVAL) {
                setRunState(AgentRunState.RUNNING)
            }
        }
        return approvedByUser
    }

    fun findRun(runId: String): AgentRunRecord? {
        return mutableRunHistory.lastOrNull { it.id == runId }
    }

    private fun finishAgentChatRun(
        record: AgentRunRecord,
        runOutcome: Result<AgentRunResult>,
        workingIndex: Int,
        resumeOriginalTask: String?,
        timelineSteps: List<AgentStep> = emptyList(),
        demoCompletion: DemonstrationSessionManager.DemonstrationCompletion? = null,
        shouldCaptureWorkflowTrace: Boolean = true,
    ) {
        removeWorkingIndicator(workingIndex)
        mutableRunHistory += record
        val result = runOutcome.getOrNull()
        val completedSuccessfully = result?.stopReason == AgentStepStopReason.COMPLETED
        ToolExecutionLog.recordAction(
            name = "agent_run_finished",
            result = record.errorMessage ?: AgentRunDetailFormatter.compactSummary(record),
            status = if (completedSuccessfully) "complete" else "fail",
            source = currentProviderMode().toLogSource(),
            details = "run_id=${record.id}"
        )
        refreshExecutionLog()
        refreshStatus()

        if (result?.stopReason == AgentStepStopReason.USER_CANCELLED && cancellationSignal.get()) {
            showChat()
            return
        }

        when {
            result?.stopReason == AgentStepStopReason.CLARIFICATION_NEEDED -> {
                val structured = result.events.filterIsInstance<AgentEvent.Clarification>().lastOrNull()
                val assistant = result.events.filterIsInstance<AgentEvent.AssistantMessage>().lastOrNull()
                val prompt = when {
                    structured != null -> ClarificationChatPrompt(
                        question = structured.question,
                        detail = structured.detail,
                        choices = structured.candidates.map {
                            SensitiveTextRedactor.redact(it.displayLabel)
                        },
                    )
                    assistant != null -> ClarificationChatPrompt(
                        question = assistant.text,
                        detail = assistant.detail,
                        choices = assistant.choices,
                    )
                    else -> null
                }
                if (prompt != null) {
                    val originalTask = resumeOriginalTask ?: record.task
                    pendingClarification = PendingClarification(originalTask = originalTask)
                    ToolExecutionLog.recordChat(
                        name = "clarification_prompt",
                        actor = "TouchPilot",
                        message = "${prompt.question}\n${prompt.detail}"
                    )
                    conversation += ChatEvent.ClarificationPrompt(
                        question = prompt.question,
                        detail = prompt.detail,
                        choices = prompt.choices,
                        onAnswer = { answer -> startFromChat(answer) }
                    )
                } else {
                    conversation += ChatEvent.Agent(
                        "TouchPilot needs clarification before continuing.",
                        result.stopMessage
                    )
                    ToolExecutionLog.recordChat(
                        name = "assistant_message",
                        actor = "TouchPilot",
                        message = result.stopMessage
                    )
                }
            }
            result != null &&
                completedSuccessfully &&
                isInformationalAssistantRun(result) -> {
                val assistant = result.events.filterIsInstance<AgentEvent.AssistantMessage>().last()
                conversation += ChatEvent.ScreenSummary(
                    summary = assistant.text,
                    suggestions = assistant.suggestions,
                )
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = "${assistant.text}\n${assistant.detail}"
                )
            }
            else -> {
                record.result?.events
                    ?.let(ToolCallCardModel::fromEvents)
                    ?.forEach { card ->
                        conversation += ChatEvent.ToolCall(card)
                    }
                conversation += ChatEvent.CompletionSummary(
                    summary = AgentRunDetailFormatter.buildCompletionSummary(record, timelineSteps),
                    runId = record.id,
                )
                conversation += ChatEvent.Timeline(
                    title = "Action timeline",
                    body = AgentRunDetailFormatter.compactSummary(record),
                    runId = record.id
                )
                val detail = when {
                    completedSuccessfully -> {
                        val finalAnswer = result?.finalAnswer
                        when {
                            finalAnswer != null -> finalAnswer
                            timelineSteps.isEmpty() -> "No steps recorded."
                            else -> "Tap the timeline card to inspect tool calls, verification, and stop reason."
                        }
                    }
                    result != null -> result.stopMessage.ifBlank {
                        "Replay stopped before completion."
                    }
                    else -> record.errorMessage ?: "Unknown agent error"
                }
                conversation += ChatEvent.Agent(if (completedSuccessfully) "Done." else "Run failed.", detail)
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = detail,
                    status = if (completedSuccessfully) "complete" else "fail"
                )
                if (completedSuccessfully && shouldCaptureWorkflowTrace) {
                    captureWorkflowTrace(record)
                }
                captureDemonstration(demoCompletion, record.id)
            }
        }
        showChat()
    }

    /**
     * Captures a successful run as a reusable [WorkflowTrace] (issue #289) and
     * offers to save it as a workflow definition (issue #381). Non-successful
     * runs (errors, blocks, no tool actions) yield no trace, so this is a
     * no-op for them. The trace is also persisted for demonstration recording
     * history.
     */
    private fun captureWorkflowTrace(record: AgentRunRecord) {
        val trace = WorkflowTrace.from(record) ?: return
        val summary = WorkflowTraceSummarizer.summarize(trace)
        workflowTraceStore.record(trace)
        val recorded = AgentEvent.TraceRecorded(runId = trace.runId, stepCount = trace.steps.size)
        ToolExecutionLog.recordAction(
            name = "workflow_trace_recorded",
            result = summary.overview,
            status = "complete",
            source = currentProviderMode().toLogSource(),
            details = JSONObject()
                .put("trace_recorded", recorded.toJson(redactSensitive = true))
                .put("summary", summary.toJson())
                .toString(),
        )
        conversation += ChatEvent.WorkflowCaptureOffer(
            runId = trace.runId,
            title = trace.task.ifBlank { "Captured workflow" },
            stepCount = trace.steps.size,
            sensitiveStepCount = summary.stepSummaries.count { it.requiresApproval },
            overview = summary.overview,
        )
    }

    private fun captureDemonstration(
        completion: DemonstrationSessionManager.DemonstrationCompletion?,
        runId: String,
    ) {
        val session = completion?.session ?: return
        if (session.steps.isEmpty()) return
        ToolExecutionLog.recordAction(
            name = "demonstration_recorded",
            result = DemonstrationSummaryFormatter.completionMessage(session),
            status = "complete",
            source = currentProviderMode().toLogSource(),
            details = "session_id=${session.sessionId}; steps=${session.steps.size}",
        )
        conversation += ChatEvent.DemonstrationRecording(
            sessionId = session.sessionId,
            active = false,
            stepCount = session.steps.size,
            summary = DemonstrationSummaryFormatter.compact(session),
            runId = runId,
        )
        conversation += ChatEvent.Agent(
            "Demonstration captured.",
            DemonstrationSummaryFormatter.completionMessage(session),
        )
    }

    private fun rejectOverlappingRunStart() {
        val detail = overlappingRunStartMessage()
        conversation += ChatEvent.Agent("Run already in progress.", detail)
        ToolExecutionLog.recordChat(
            name = "run_start_rejected",
            actor = "TouchPilot",
            message = detail,
            status = "blocked",
        )
        showChat()
    }

    private fun overlappingRunStartMessage(): String {
        return "Wait for the current run to finish or tap Stop before starting another one."
    }

    private fun setRunState(state: AgentRunState) {
        runState = state
        showChat()
    }

    private fun rejectPendingApprovals() {
        rejectPendingApprovalPrompts(conversation)
    }

    private fun removeWorkingIndicator(workingIndex: Int) {
        if (workingIndex in conversation.indices &&
            conversation[workingIndex] is ChatEvent.Working
        ) {
            conversation.removeAt(workingIndex)
        }
    }

    private fun isInformationalAssistantRun(result: AgentRunResult): Boolean {
        val hasAssistant = result.events.any { it is AgentEvent.AssistantMessage }
        val invokedTools = result.events.any {
            it is AgentEvent.ToolRequested || it is AgentEvent.ToolRunning
        }
        return hasAssistant && !invokedTools
    }

    private fun AgentProviderMode.toLogSource(): String {
        return when (this) {
            AgentProviderMode.LOCAL_MODEL -> "local_model"
            AgentProviderMode.LOCAL_ROUTER -> "local_router"
        }
    }

    private data class PendingClarification(
        val originalTask: String,
    )

    private data class ClarificationChatPrompt(
        val question: String,
        val detail: String,
        val choices: List<String>,
    )

    private companion object {
        const val APPROVAL_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
