package dev.touchpilot.app.agent

import dev.touchpilot.app.localinference.LocalCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelCommandProvider
import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.screen.ScreenContext
import dev.touchpilot.app.screen.ScreenContextSummarizer
import dev.touchpilot.app.screen.ScreenSummary
import dev.touchpilot.app.security.ActionPolicy
import dev.touchpilot.app.security.DefaultActionPolicy
import dev.touchpilot.app.security.ToolApprovalProvider
import dev.touchpilot.app.security.ToolSource
import dev.touchpilot.app.tools.AndroidToolExecutor

/**
 * Single entry point the UI uses to ask the local-first runtime to handle a
 * user request. The UI does not own command production or tool execution — it
 * only renders the resulting AgentEvent stream and supplies an approval
 * channel.
 */
interface LocalReasoningCore {
    fun run(task: String, listener: AgentEventListener = AgentEventListener {}): AgentRunResult
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
    fun invoke(task: String, context: LocalReasoningContext): AgentRunResult
}

class DefaultLocalReasoningCore(
    private val invocation: AgentRunInvocation,
    private val sessionContext: () -> LocalReasoningContext,
    private val intents: ConversationalIntents = ConversationalGate,
    private val intentClassifier: IntentClassifier = IntentGate(),
    private val availableSkills: () -> List<Skill> = { emptyList() },
    private val screenContextProvider: () -> ScreenContext = { ScreenContext.Empty },
    private val screenSummarizer: ScreenContextSummarizer = ScreenContextSummarizer()
) : LocalReasoningCore {

    override fun run(task: String, listener: AgentEventListener): AgentRunResult {
        intents.respond(task)?.let { canned ->
            val userEvent = AgentEvent.UserMessage(task)
            val finalEvent = AgentEvent.FinalAnswer(canned.message)
            listener.onEvent(userEvent)
            listener.onEvent(finalEvent)
            return AgentRunResult(
                transcript = "Conversational reply",
                finalAnswer = canned.message,
                events = listOf(userEvent, finalEvent)
            )
        }

        val baseCtx = sessionContext()
        val skills = availableSkills()
        val intent = intentClassifier.classify(task, skills)

        when (intent) {
            is IntentDecision.UnsafeRequest -> return blockUnsafe(task, intent, listener)
            is IntentDecision.ClarificationNeeded -> return askForClarification(task, intent, listener)
            is IntentDecision.ScreenInquiry -> return answerScreenInquiry(task, intent, listener)
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
                        listener = listener
                    )
                }
                baseCtx.copy(skill = matchedSkill)
            }
            is IntentDecision.LocalModelNeeded -> baseCtx
            is IntentDecision.UnsafeRequest,
            is IntentDecision.ClarificationNeeded,
            is IntentDecision.ScreenInquiry -> error("handled above")
        }

        val result = invocation.invoke(task, effectiveCtx)
        result.events.forEach(listener::onEvent)
        return result
    }

    private fun blockUnsafe(
        task: String,
        intent: IntentDecision.UnsafeRequest,
        listener: AgentEventListener
    ): AgentRunResult {
        val userEvent = AgentEvent.UserMessage(task)
        val blocked = AgentEvent.PolicyBlocked(
            tool = null,
            reason = intent.reason,
            userMessage = intent.userMessage
        )
        val finalEvent = AgentEvent.FinalAnswer(intent.userMessage)
        listener.onEvent(userEvent)
        listener.onEvent(blocked)
        listener.onEvent(finalEvent)
        return AgentRunResult(
            transcript = "Blocked by intent gate: ${intent.reason}",
            finalAnswer = intent.userMessage,
            events = listOf(userEvent, blocked, finalEvent)
        )
    }

    private fun askForClarification(
        task: String,
        intent: IntentDecision.ClarificationNeeded,
        listener: AgentEventListener
    ): AgentRunResult {
        val userEvent = AgentEvent.UserMessage(task)
        val assistant = AgentEvent.AssistantMessage(
            text = intent.clarification,
            detail = intent.reason
        )
        listener.onEvent(userEvent)
        listener.onEvent(assistant)
        return AgentRunResult(
            transcript = "Clarification requested: ${intent.reason}",
            finalAnswer = intent.clarification,
            events = listOf(userEvent, assistant)
        )
    }

    private fun answerScreenInquiry(
        task: String,
        intent: IntentDecision.ScreenInquiry,
        listener: AgentEventListener
    ): AgentRunResult {
        val context = screenContextProvider()
        val summary = screenSummarizer.summarize(context)
        val userEvent = AgentEvent.UserMessage(task)
        val suggestionBlock = renderSuggestions(summary)
        val assistant = AgentEvent.AssistantMessage(
            text = summary.sentence,
            detail = suggestionBlock
        )
        val finalEvent = AgentEvent.FinalAnswer(summary.sentence)
        listener.onEvent(userEvent)
        listener.onEvent(assistant)
        listener.onEvent(finalEvent)
        val transcript = if (suggestionBlock.isBlank()) {
            summary.sentence
        } else {
            summary.sentence + "\n\n" + suggestionBlock
        }
        return AgentRunResult(
            transcript = transcript,
            finalAnswer = summary.sentence,
            events = listOf(userEvent, assistant, finalEvent)
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
): AgentRunInvocation = AgentRunInvocation { task, context ->
    buildDefaultAgentRunner(
        task = task,
        context = context,
        toolExecutor = toolExecutor,
        approvalProvider = approvalProvider,
        localModelRuntime = localModelRuntime,
        policy = policy
    ).run(task)
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
    policy: ActionPolicy = DefaultActionPolicy()
): AgentRunner {
    val commandProvider = buildCommandProvider(task, context, localModelRuntime)
    return AgentRunner(
        toolExecutor = toolExecutor,
        approvalProvider = approvalProvider,
        commandProvider = commandProvider,
        skill = context.skill,
        source = context.providerMode.toToolSource(),
        policy = policy
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
    val localRouter = LocalRouterCommandProvider(task, context.skill)
    return when (context.providerMode) {
        AgentProviderMode.LOCAL_MODEL -> LocalModelCommandProvider(
            runtime = localModelRuntime,
            fallback = localRouter,
            task = task,
            skill = context.skill
        )
        AgentProviderMode.LOCAL_ROUTER -> localRouter
    }
}

private fun AgentProviderMode.toToolSource(): ToolSource = when (this) {
    AgentProviderMode.LOCAL_MODEL -> ToolSource.LOCAL_MODEL
    AgentProviderMode.LOCAL_ROUTER -> ToolSource.LOCAL_ROUTER
}
