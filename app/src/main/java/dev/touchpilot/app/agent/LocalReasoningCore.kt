package dev.touchpilot.app.agent

import dev.touchpilot.app.localinference.LocalCommandModelRuntime
import dev.touchpilot.app.localinference.LocalModelCommandProvider
import dev.touchpilot.app.memory.Skill
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
    val providerConfig: ProviderConfig
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
    private val intents: ConversationalIntents = ConversationalGate
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

        val ctx = sessionContext()
        val result = invocation.invoke(task, ctx)
        result.events.forEach(listener::onEvent)
        return result
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
    val localRouter = LocalRouterCommandProvider(task, context.skill)
    return when (context.providerMode) {
        AgentProviderMode.CLOUD -> OpenAiAgentCommandProvider(context.providerConfig)
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
    AgentProviderMode.CLOUD -> ToolSource.CLOUD_FALLBACK
    AgentProviderMode.LOCAL_MODEL -> ToolSource.LOCAL_MODEL
    AgentProviderMode.LOCAL_ROUTER -> ToolSource.LOCAL_ROUTER
}
