package dev.touchpilot.app.agent

import dev.touchpilot.app.localinference.LocalCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelCommandProvider
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenContextSummarizer
import dev.touchpilot.app.screen.ScreenSummary
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.SensitiveTextRedactor
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolExecutor
import dev.touchpilot.app.workflow.WorkflowDefinition
import dev.touchpilot.app.workflow.WorkflowReplayEngine
import dev.touchpilot.app.workflow.buildWorkflowReplayEngine

/**
 * Single entry point the UI uses to ask the local-first runtime to handle a
 * user request. The UI does not own command production or tool execution — it
 * only renders the resulting AgentEvent stream and supplies an approval
 * channel.
 */
interface LocalReasoningCore {
    fun run(
        task: String,
        timeline: AgentStepTimelineBuilder? = null,
        listener: AgentEventListener = AgentEventListener {},
        cancellationSignal: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false)
    ): AgentRunResult

    fun replayWorkflow(
        definition: WorkflowDefinition,
        parameters: Map<String, String> = emptyMap(),
        timeline: AgentStepTimelineBuilder? = null,
        listener: AgentEventListener = AgentEventListener {},
        cancellationSignal: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false)
    ): AgentRunResult
}

/**
 * Convenience overload for LocalReasoningCore.run() without timeline or cancellation support.
 */
fun LocalReasoningCore.run(task: String, listener: AgentEventListener = AgentEventListener {}): AgentRunResult {
    return run(task, timeline = null, listener, java.util.concurrent.atomic.AtomicBoolean(false))
}

fun LocalReasoningCore.replayWorkflow(
    definition: WorkflowDefinition,
    parameters: Map<String, String> = emptyMap(),
    listener: AgentEventListener = AgentEventListener {},
): AgentRunResult {
    return replayWorkflow(
        definition = definition,
        parameters = parameters,
        timeline = null,
        listener = listener,
        cancellationSignal = java.util.concurrent.atomic.AtomicBoolean(false)
    )
}

fun interface AgentEventListener {
    fun onEvent(event: AgentEvent)
}

data class LocalReasoningContext(
    val skill: Skill?,
    val providerMode: AgentProviderMode,
    /**
     * When set, [buildCommandProvider] returns a [FixedCommandProvider] that
     * emits exactly this command on the next runner step. Populated by the
     * intent gate's [IntentDecision.ExactCommand] branch so the gate's
     * decided tool/args are what actually runs, with no second parser
     * deciding differently.
     */
    val exactCommand: AgentCommand? = null
)

/**
 * Runs the agent for a given task and context. Extracted so the core can be
 * unit-tested without an Android tool executor; production code uses
 * [defaultAgentRunInvocation] which builds and runs a real [AgentRunner].
 */
fun interface AgentRunInvocation {
    fun invoke(
        task: String,
        context: LocalReasoningContext,
        listener: AgentEventListener,
        timeline: AgentStepTimelineBuilder?,
        cancellationSignal: java.util.concurrent.atomic.AtomicBoolean
    ): AgentRunResult
}

/**
 * Convenience overload for invocation without timeline, listener, or cancellation support.
 */
fun AgentRunInvocation.invoke(task: String, context: LocalReasoningContext): AgentRunResult {
    return invoke(task, context, AgentEventListener {}, null, java.util.concurrent.atomic.AtomicBoolean(false))
}

class DefaultLocalReasoningCore(
    private val invocation: AgentRunInvocation,
    private val sessionContext: () -> LocalReasoningContext,
    private val workflowReplayEngineFactory: (java.util.concurrent.atomic.AtomicBoolean) -> WorkflowReplayEngine = {
        error("Workflow replay is not configured")
    },
    private val intents: ConversationalIntents = ConversationalGate,
    private val intentClassifier: IntentClassifier = IntentGate(),
    private val availableSkills: () -> List<Skill> = { emptyList() },
    private val screenContextProvider: () -> ScreenContext = { ScreenContext.Empty },
    private val screenSummarizer: ScreenContextSummarizer = ScreenContextSummarizer()
) : LocalReasoningCore {

override fun run(
    task: String,
    timeline: AgentStepTimelineBuilder?,
    listener: AgentEventListener,
    cancellationSignal: java.util.concurrent.atomic.AtomicBoolean
): AgentRunResult {
    val streamed = mutableSetOf<String>()
    fun forward(event: AgentEvent) {
        if (streamed.add(event.id)) {
            listener.onEvent(event)
            timeline?.onEvent(event)
        }
    }
        intents.respond(task)?.let { canned ->
            val userEvent = AgentEvent.UserMessage(task)
            val finalEvent = AgentEvent.FinalAnswer(canned.message)
            forward(userEvent)
            forward(finalEvent)
            return AgentRunResult(
                transcript = "Conversational reply",
                finalAnswer = canned.message,
                events = listOf(userEvent, finalEvent),
                stopReason = AgentStepStopReason.COMPLETED,
                stopMessage = AgentStepStopReason.COMPLETED.userMessage
            )
        }

        val baseCtx = sessionContext()
        val skills = availableSkills()
        val intent = intentClassifier.classify(task, skills)

        when (intent) {
            is IntentDecision.UnsafeRequest -> return blockUnsafe(task, intent, ::forward)
            is IntentDecision.ClarificationNeeded -> return askForClarification(task, intent, ::forward, timeline)
            is IntentDecision.ScreenInquiry -> return answerScreenInquiry(task, intent, ::forward)
            is IntentDecision.ExactCommand,
            is IntentDecision.KnownSkill,
            is IntentDecision.LocalModelNeeded -> Unit
        }

        val effectiveCtx = when (intent) {
            is IntentDecision.ExactCommand -> {
            // Run the gate's decided tool/args verbatim through a
            // FixedCommandProvider. Forcing LOCAL_ROUTER mode alone would let
            // the router's independent parser pick a different action (e.g.
            // "scroll back" -> press_back vs the gate's scroll/forward), so
            // we plumb the exact command into the context.
            baseCtx.copy(
                providerMode = AgentProviderMode.LOCAL_ROUTER,
                exactCommand = AgentCommand(
                    tool = intent.tool,
                    args = intent.args,
                    finalAnswer = null
                )
            )
            }
            is IntentDecision.KnownSkill -> {
                val matchedSkill = skills.firstOrNull { it.id == intent.skillId }
                if (matchedSkill == null) {
                    return askForClarification(
                        task = task,
                        intent = IntentDecision.ClarificationNeeded(
                            reason = "matched skill '${intent.skillId}' is unavailable",
                            clarification = "I couldn't load that skill. Please pick another skill or rephrase the request."
                        ),
                        forward = ::forward,
                        timeline = timeline
                    )
                }
                baseCtx.copy(skill = matchedSkill)
            }
            is IntentDecision.LocalModelNeeded -> baseCtx
            is IntentDecision.UnsafeRequest,
            is IntentDecision.ClarificationNeeded,
            is IntentDecision.ScreenInquiry -> error("handled above")
        }

        val skillScopeEvent = effectiveCtx.skill?.let { skill ->
            skillScopeEvent(skill, intent)
        }
        skillScopeEvent?.let(::forward)

        val result = invocation.invoke(
            task = task,
            context = effectiveCtx,
            listener = AgentEventListener(::forward),
            timeline = timeline,
            cancellationSignal = cancellationSignal
        )
        result.events.forEach(::forward)
        return if (skillScopeEvent != null) {
            result.copy(events = listOf(skillScopeEvent) + result.events)
        } else {
            result
        }
    }

    override fun replayWorkflow(
        definition: WorkflowDefinition,
        parameters: Map<String, String>,
        timeline: AgentStepTimelineBuilder?,
        listener: AgentEventListener,
        cancellationSignal: java.util.concurrent.atomic.AtomicBoolean
    ): AgentRunResult {
        val streamed = mutableSetOf<String>()
        fun forward(event: AgentEvent) {
            if (streamed.add(event.id)) {
                listener.onEvent(event)
                timeline?.onEvent(event)
            }
        }

        val result = workflowReplayEngineFactory(cancellationSignal).replay(
            definition = definition,
            parameters = parameters,
            listener = AgentEventListener(::forward),
            onStepsUpdated = timeline?.let { builder -> { steps -> builder.replaceAll(steps) } }
        )
        result.events.forEach(::forward)
        return result
    }

    private fun skillScopeEvent(skill: Skill, intent: IntentDecision): AgentEvent.SkillActive {
        val (source, reason) = when (intent) {
            is IntentDecision.KnownSkill -> SkillActivationSource.MATCHED to intent.reason
            else -> SkillActivationSource.MANUAL to "Active skill selected in Settings"
        }
        return AgentEvent.SkillActive(
            skillId = skill.id,
            title = skill.title,
            risk = skill.risk,
            allowedTools = skill.allowedTools,
            activationSource = source,
            reason = reason
        )
    }

    private fun blockUnsafe(
        task: String,
        intent: IntentDecision.UnsafeRequest,
        forward: (AgentEvent) -> Unit
    ): AgentRunResult {
        val userEvent = AgentEvent.UserMessage(task)
        val blocked = AgentEvent.PolicyBlocked(
            tool = null,
            reason = intent.reason,
            userMessage = intent.userMessage
        )
        val finalEvent = AgentEvent.FinalAnswer(intent.userMessage)
        forward(userEvent)
        forward(blocked)
        forward(finalEvent)
        return AgentRunResult(
            transcript = "Blocked by intent gate: ${intent.reason}",
            finalAnswer = intent.userMessage,
            events = listOf(userEvent, blocked, finalEvent),
            stopReason = AgentStepStopReason.POLICY_BLOCKED,
            stopMessage = blocked.userMessage
        )
    }

    private fun askForClarification(
        task: String,
        intent: IntentDecision.ClarificationNeeded,
        forward: (AgentEvent) -> Unit,
        timeline: AgentStepTimelineBuilder?
    ): AgentRunResult {
        val userEvent = AgentEvent.UserMessage(task)
        val assistant = AgentEvent.AssistantMessage(
            text = intent.clarification,
            detail = intent.reason,
            choices = intent.candidateLabels.map { SensitiveTextRedactor.redact(it) }
        )
        forward(userEvent)
        timeline?.requestClarification(intent.clarification)
        forward(assistant)
        return AgentRunResult(
            transcript = "Clarification requested: ${intent.reason}",
            finalAnswer = intent.clarification,
            events = listOf(userEvent, assistant),
            stopReason = AgentStepStopReason.CLARIFICATION_NEEDED,
            stopMessage = intent.clarification
        )
    }

    private fun answerScreenInquiry(
        task: String,
        intent: IntentDecision.ScreenInquiry,
        forward: (AgentEvent) -> Unit
    ): AgentRunResult {
        val context = screenContextProvider()
        val summary = screenSummarizer.summarize(context)
        val userEvent = AgentEvent.UserMessage(task)
        val suggestionBlock = renderSuggestions(summary)
        val assistant = AgentEvent.AssistantMessage(
            text = summary.sentence,
            detail = suggestionBlock,
            suggestions = summary.suggestedActions.map { it.label }
        )
        val finalEvent = AgentEvent.FinalAnswer(summary.sentence)
        forward(userEvent)
        forward(assistant)
        forward(finalEvent)
        val transcript = if (suggestionBlock.isBlank()) {
            summary.sentence
        } else {
            summary.sentence + "\n\n" + suggestionBlock
        }
        return AgentRunResult(
            transcript = transcript,
            finalAnswer = summary.sentence,
            events = listOf(userEvent, assistant, finalEvent),
            stopReason = AgentStepStopReason.COMPLETED,
            stopMessage = AgentStepStopReason.COMPLETED.userMessage
        )
    }

    private fun renderSuggestions(summary: ScreenSummary): String {
        if (summary.suggestedActions.isEmpty()) return ""
        return buildString {
            appendLine("Suggested actions:")
            summary.suggestedActions.forEachIndexed { index, action ->
                append(index + 1)
                append(". ")
                append(action.label)
                if (index < summary.suggestedActions.lastIndex) appendLine()
            }
        }
    }
}

/**
 * Production invocation: build the default [AgentRunner] and execute it.
 */
fun defaultAgentRunInvocation(
    toolExecutor: AndroidToolExecutor,
    approvalProvider: ToolApprovalProvider,
    localModelRuntime: LocalCommandModelRuntime,
    policy: ActionPolicy = DefaultActionPolicy()
): AgentRunInvocation = AgentRunInvocation { task, context, listener, timeline, cancellationSignal ->
    buildDefaultAgentRunner(
        task = task,
        context = context,
        toolExecutor = toolExecutor,
        approvalProvider = approvalProvider,
        localModelRuntime = localModelRuntime,
        policy = policy,
        cancellationSignal = cancellationSignal
    ).run(task, listener = listener, timeline = timeline)
}

/**
 * Builds the default AgentRunner: chooses the command provider based on the
 * session's provider mode, threads the same approval/policy/skill plumbing the
 * UI used to wire up directly, and returns a ready-to-run AgentRunner.
 */
fun buildDefaultAgentRunner(
    task: String,
    context: LocalReasoningContext,
    toolExecutor: AndroidToolExecutor,
    approvalProvider: ToolApprovalProvider,
    localModelRuntime: LocalCommandModelRuntime,
    policy: ActionPolicy = DefaultActionPolicy(),
    cancellationSignal: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false)
): AgentRunner {
    val commandProvider = buildCommandProvider(task, context, localModelRuntime)
    return AgentRunner(
        toolExecutor = toolExecutor,
        approvalProvider = approvalProvider,
        commandProvider = commandProvider,
        skill = context.skill,
        source = context.providerMode.toToolSource(),
        policy = policy,
        cancellationSignal = cancellationSignal
    )
}

fun buildCommandProvider(
    task: String,
    context: LocalReasoningContext,
    localModelRuntime: LocalCommandModelRuntime
): AgentCommandProvider {
    context.exactCommand?.let { command ->
        return FixedCommandProvider(command)
    }
    return when (context.providerMode) {
        AgentProviderMode.LOCAL_MODEL -> LocalModelCommandProvider(
            runtime = localModelRuntime,
            task = task,
            skill = context.skill
        )
        AgentProviderMode.LOCAL_ROUTER -> LocalRouterCommandProvider(task, context.skill)
    }
}

private fun AgentProviderMode.toToolSource(): ToolSource = when (this) {
    AgentProviderMode.LOCAL_MODEL -> ToolSource.LOCAL_MODEL
    AgentProviderMode.LOCAL_ROUTER -> ToolSource.LOCAL_ROUTER
}

fun defaultWorkflowReplayEngineFactory(
    toolExecutor: AndroidToolExecutor,
    approvalProvider: ToolApprovalProvider,
    policy: ActionPolicy = DefaultActionPolicy(),
): (java.util.concurrent.atomic.AtomicBoolean) -> WorkflowReplayEngine = { cancellationSignal ->
    buildWorkflowReplayEngine(
        toolExecutor = toolExecutor,
        approvalProvider = approvalProvider,
        policy = policy,
        cancellationSignal = cancellationSignal,
    )
}
