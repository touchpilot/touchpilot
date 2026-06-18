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
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.workflow.WorkflowTrace
import dev.touchpilot.app.workflow.WorkflowTraceStore
import dev.touchpilot.app.ui.chat.ApprovalState
import dev.touchpilot.app.ui.chat.ChatEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
) {
    private var pendingClarification: PendingClarification? = null
    private var cancellationSignal: AtomicBoolean = AtomicBoolean(false)
    private val mutableRunHistory = mutableListOf<AgentRunRecord>()
    private val workflowTraceStore = WorkflowTraceStore()

    var runState: AgentRunState = AgentRunState.IDLE
        private set

    val runHistory: List<AgentRunRecord>
        get() = mutableRunHistory

    /** Workflow traces captured from successful runs this session (issue #289). */
    val workflowTraces: List<WorkflowTrace>
        get() = workflowTraceStore.all()

    fun startFromChat(task: String) {
        val pending = pendingClarification
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

        Thread {
            val timelineBuilder = AgentStepTimelineBuilder()
            var skillCardAdded = false
            val runOutcome = runCatching {
                reasoningCore.run(
                    task = agentTask,
                    timeline = timelineBuilder,
                    listener = AgentEventListener { event ->
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
                if (cancellationSignal.get()) {
                    setRunState(AgentRunState.CANCELLED)
                } else if (runOutcome.isFailure ||
                    (record.result?.events?.any { it is AgentEvent.ToolFailed || it is AgentEvent.PolicyBlocked } == true)
                ) {
                    setRunState(AgentRunState.FAILED)
                    rejectPendingApprovals()
                } else {
                    setRunState(AgentRunState.COMPLETED)
                }
                refreshStepTimeline(stepTimeline, steps, true)
                finishAgentChatRun(
                    record = record,
                    runOutcome = runOutcome,
                    workingIndex = workingIndex,
                    resumeOriginalTask = originalTask,
                    timelineSteps = steps,
                )
            }
        }.start()
    }

    fun cancelRun() {
        cancellationSignal.set(true)
        setRunState(AgentRunState.CANCELLED)
        rejectPendingApprovals()
        conversation += ChatEvent.Agent("Run cancelled.", "Stopped by user request.")
        showChat()
    }

    fun approveTool(request: ToolApprovalRequest): Boolean {
        val latch = CountDownLatch(1)
        val approved = AtomicBoolean(false)

        runOnUiThread {
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

        return latch.await(APPROVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS) && approved.get()
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
    ) {
        removeWorkingIndicator(workingIndex)
        mutableRunHistory += record
        ToolExecutionLog.recordAction(
            name = "agent_run_finished",
            result = record.errorMessage ?: AgentRunDetailFormatter.compactSummary(record),
            status = if (runOutcome.isSuccess) "complete" else "fail",
            source = currentProviderMode().toLogSource(),
            details = "run_id=${record.id}"
        )
        refreshExecutionLog()
        refreshStatus()

        val result = runOutcome.getOrNull()
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
            result?.stopReason == AgentStepStopReason.COMPLETED &&
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
            runOutcome.isSuccess -> {
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
                val finalAnswer = result?.finalAnswer
                val doneDetail = when {
                    finalAnswer != null -> finalAnswer
                    timelineSteps.isEmpty() -> "No steps recorded."
                    else -> "Tap the timeline card to inspect tool calls, verification, and stop reason."
                }
                conversation += ChatEvent.Agent("Done.", doneDetail)
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = doneDetail,
                    status = "complete"
                )
                captureWorkflowTrace(record)
            }
            else -> {
                conversation += ChatEvent.Agent(
                    "Run failed.",
                    record.errorMessage ?: "Unknown agent error"
                )
                ToolExecutionLog.recordChat(
                    name = "assistant_message",
                    actor = "TouchPilot",
                    message = record.errorMessage ?: "Unknown agent error",
                    status = "fail"
                )
            }
        }
        showChat()
    }

    /**
     * Captures a successful run as a reusable [WorkflowTrace] (issue #289).
     * Non-successful runs (errors, blocks, no tool actions) yield no trace, so
     * this is a no-op for them. The trace stays in-memory for the session.
     */
    private fun captureWorkflowTrace(record: AgentRunRecord) {
        val trace = WorkflowTrace.from(record) ?: return
        workflowTraceStore.record(trace)
        val recorded = AgentEvent.TraceRecorded(runId = trace.runId, stepCount = trace.steps.size)
        ToolExecutionLog.recordAction(
            name = "workflow_trace_recorded",
            result = "Captured a ${trace.steps.size}-step workflow trace.",
            status = "complete",
            source = currentProviderMode().toLogSource(),
            details = recorded.toJson(redactSensitive = true).toString(),
        )
        conversation += ChatEvent.Agent(
            "Workflow captured.",
            "${trace.steps.size} step(s) recorded — this run can be saved as a workflow.",
        )
    }

    private fun setRunState(state: AgentRunState) {
        runState = state
        showChat()
    }

    private fun rejectPendingApprovals() {
        conversation.forEach { event ->
            if (event is ChatEvent.ApprovalPrompt && event.state == ApprovalState.PENDING) {
                event.state = ApprovalState.REJECTED
            }
        }
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
