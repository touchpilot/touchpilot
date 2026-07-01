package dev.touchpilot.app.mcp

import dev.touchpilot.app.security.ExternalCapabilityAction
import dev.touchpilot.app.security.ExternalCapabilityAuditRecord
import dev.touchpilot.app.security.ExternalCapabilityKind
import dev.touchpilot.app.security.ExternalCapabilityPolicy
import dev.touchpilot.app.security.ExternalCapabilityPolicyDecision
import dev.touchpilot.app.security.ExternalCapabilityTarget
import dev.touchpilot.app.tools.ToolExecutionLog
import org.json.JSONObject

sealed class ExternalCapabilityInvokeResult {
    data class Success(val message: String) : ExternalCapabilityInvokeResult()
    data class Denied(val decision: ExternalCapabilityPolicyDecision) : ExternalCapabilityInvokeResult()
    data class Failed(val message: String) : ExternalCapabilityInvokeResult()
}

class ExternalCapabilityInvoker(
    private val policy: ExternalCapabilityPolicy,
    private val clientFactory: (String) -> McpClient = { endpoint -> McpHttpClient(endpoint) },
) {
    fun listTools(
        target: ExternalCapabilityTarget,
        requiredFeatureFlags: Set<String> = emptySet(),
    ): ExternalCapabilityInvokeResult {
        val decision = policy.evaluate(
            action = ExternalCapabilityAction.LIST_TOOLS,
            target = target,
            requiredFeatureFlags = requiredFeatureFlags,
        )
        if (!decision.isAllowed) {
            audit(
                action = ExternalCapabilityAction.LIST_TOOLS,
                target = target,
                parameters = emptyMap(),
                decision = decision,
                ok = false,
                message = decision.reason,
            )
            return ExternalCapabilityInvokeResult.Denied(decision)
        }

        return runCatching {
            val client = clientFactory(target.endpoint)
            val initialized = client.initialize()
            val tools = client.listTools()
            val message = buildString {
                appendLine("MCP initialized:")
                appendLine(initialized)
                appendLine()
                appendLine("Tools:")
                if (tools.isEmpty()) {
                    appendLine("No tools returned.")
                } else {
                    tools.forEach { tool ->
                        appendLine("- ${tool.name}: ${tool.description}")
                    }
                }
            }.trim()
            audit(
                action = ExternalCapabilityAction.LIST_TOOLS,
                target = target,
                parameters = mapOf("initialized" to "true", "tool_count" to tools.size.toString()),
                decision = decision,
                ok = true,
                message = "Listed ${tools.size} MCP tool(s)",
            )
            ExternalCapabilityInvokeResult.Success(message)
        }.getOrElse { error ->
            audit(
                action = ExternalCapabilityAction.LIST_TOOLS,
                target = target,
                parameters = emptyMap(),
                decision = decision,
                ok = false,
                message = error.message.orEmpty(),
            )
            ExternalCapabilityInvokeResult.Failed(error.message.orEmpty())
        }
    }

    fun callTool(
        target: ExternalCapabilityTarget,
        toolName: String,
        args: JSONObject,
        requiredFeatureFlags: Set<String> = emptySet(),
    ): ExternalCapabilityInvokeResult {
        val parameters = mapOf(
            "tool" to toolName,
            "arguments" to args.toString(),
        )
        val decision = policy.evaluate(
            action = ExternalCapabilityAction.CALL_TOOL,
            target = target,
            requiredFeatureFlags = requiredFeatureFlags,
        )
        if (!decision.isAllowed) {
            audit(
                action = ExternalCapabilityAction.CALL_TOOL,
                target = target,
                parameters = parameters,
                decision = decision,
                ok = false,
                message = decision.reason,
            )
            return ExternalCapabilityInvokeResult.Denied(decision)
        }

        return runCatching {
            val client = clientFactory(target.endpoint)
            client.initialize()
            val callResult = client.callTool(toolName, args)
            audit(
                action = ExternalCapabilityAction.CALL_TOOL,
                target = target,
                parameters = parameters,
                decision = decision,
                ok = callResult.ok,
                message = if (callResult.ok) {
                    "Called $toolName"
                } else {
                    callResult.message
                },
            )
            val body = "MCP $toolName -> ${callResult.ok}\n${callResult.message}"
            if (callResult.ok) {
                ExternalCapabilityInvokeResult.Success(body)
            } else {
                ExternalCapabilityInvokeResult.Failed(body)
            }
        }.getOrElse { error ->
            audit(
                action = ExternalCapabilityAction.CALL_TOOL,
                target = target,
                parameters = parameters,
                decision = decision,
                ok = false,
                message = error.message.orEmpty(),
            )
            ExternalCapabilityInvokeResult.Failed(error.message.orEmpty())
        }
    }

    private fun audit(
        action: ExternalCapabilityAction,
        target: ExternalCapabilityTarget,
        parameters: Map<String, String>,
        decision: ExternalCapabilityPolicyDecision,
        ok: Boolean,
        message: String,
    ) {
        val record = ExternalCapabilityAuditRecord(
            action = action,
            target = target,
            parameters = parameters,
            policyDecision = decision,
            ok = ok,
            message = message,
        )
        ToolExecutionLog.recordExternalCapability(record)
    }

    companion object {
        fun targetForEndpoint(
            endpoint: String,
            kind: ExternalCapabilityKind = ExternalCapabilityKind.MCP_SERVER,
            name: String = "",
        ): ExternalCapabilityTarget {
            return ExternalCapabilityTarget(kind = kind, endpoint = endpoint.trim(), name = name.trim())
        }
    }
}
