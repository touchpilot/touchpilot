package dev.touchpilot.app.mcp

import dev.touchpilot.app.security.ExternalCapabilityAction
import dev.touchpilot.app.security.ExternalCapabilityKind
import dev.touchpilot.app.security.ExternalCapabilityPermissionStore
import dev.touchpilot.app.security.ExternalCapabilityPolicy
import dev.touchpilot.app.security.ExternalCapabilityTarget
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExternalCapabilityInvokerTest {
    @Test
    fun listToolsDeniedWithoutGrant() {
        val invoker = invoker(grants = emptySet())

        val result = invoker.listTools(mcpTarget())

        assertIs<ExternalCapabilityInvokeResult.Denied>(result)
    }

    @Test
    fun listToolsSucceedsWithGrantUsingInjectedClient() {
        val invoker = invoker(
            grants = setOf(ExternalCapabilityAction.LIST_TOOLS),
            client = FakeMcpClient(
                tools = listOf(McpTool("echo", "Echo input")),
            ),
        )

        val result = invoker.listTools(mcpTarget())

        assertIs<ExternalCapabilityInvokeResult.Success>(result)
        assertTrue(result.message.contains("echo"))
    }

    @Test
    fun callToolDeniedWithoutGrant() {
        val invoker = invoker(grants = setOf(ExternalCapabilityAction.LIST_TOOLS))

        val result = invoker.callTool(mcpTarget(), "echo", JSONObject())

        assertIs<ExternalCapabilityInvokeResult.Denied>(result)
    }

    private fun invoker(
        grants: Set<ExternalCapabilityAction>,
        client: McpClient? = null,
    ): ExternalCapabilityInvoker {
        var backing = ""
        val store = ExternalCapabilityPermissionStore(
            readJson = { backing },
            writeJson = { backing = it },
        )
        if (grants.isNotEmpty()) {
            store.grant(mcpTarget(), grants)
        }
        return ExternalCapabilityInvoker(
            policy = ExternalCapabilityPolicy(store),
            clientFactory = { client ?: McpHttpClient(it) },
        )
    }

    private fun mcpTarget() = ExternalCapabilityTarget(
        kind = ExternalCapabilityKind.MCP_SERVER,
        endpoint = "http://localhost:8080",
    )

    private class FakeMcpClient(
        private val tools: List<McpTool> = emptyList(),
    ) : McpClient {
        override fun initialize(): String = """{"protocolVersion":"test"}"""

        override fun listTools(): List<McpTool> = tools

        override fun callTool(name: String, args: JSONObject): McpToolCallResult {
            return McpToolCallResult(ok = true, message = "ok")
        }
    }
}
