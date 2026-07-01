package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalCapabilityPolicyTest {
    @Test
    fun deniesByDefaultWithoutGrant() {
        val policy = policy()

        val decision = policy.evaluate(
            action = ExternalCapabilityAction.LIST_TOOLS,
            target = mcpTarget(),
        )

        assertFalse(decision.isAllowed)
        assertEquals(ExternalCapabilityPolicyOutcome.DENY, decision.outcome)
    }

    @Test
    fun allowsGrantedMcpAction() {
        val store = store()
        store.grant(mcpTarget(), setOf(ExternalCapabilityAction.LIST_TOOLS))
        val policy = ExternalCapabilityPolicy(store)

        val decision = policy.evaluate(
            action = ExternalCapabilityAction.LIST_TOOLS,
            target = mcpTarget(),
        )

        assertTrue(decision.isAllowed)
    }

    @Test
    fun deniesCallToolWhenOnlyListToolsGranted() {
        val store = store()
        store.grant(mcpTarget(), setOf(ExternalCapabilityAction.LIST_TOOLS))
        val policy = ExternalCapabilityPolicy(store)

        val decision = policy.evaluate(
            action = ExternalCapabilityAction.CALL_TOOL,
            target = mcpTarget(),
        )

        assertFalse(decision.isAllowed)
    }

    @Test
    fun deniesExtensionCallWhenRequiredFeatureFlagNotGranted() {
        val store = store()
        val target = ExternalCapabilityTarget(
            kind = ExternalCapabilityKind.LOCAL_EXTENSION,
            endpoint = "http://localhost:9090",
            name = "weather",
        )
        store.grant(target, setOf(ExternalCapabilityAction.CALL_TOOL), featureFlags = emptySet())
        val policy = ExternalCapabilityPolicy(store)

        val decision = policy.evaluate(
            action = ExternalCapabilityAction.CALL_TOOL,
            target = target,
            requiredFeatureFlags = setOf("network_access"),
        )

        assertFalse(decision.isAllowed)
        assertTrue(decision.reason.contains("network_access"))
    }

    private fun policy(): ExternalCapabilityPolicy = ExternalCapabilityPolicy(store())

    private fun store(): ExternalCapabilityPermissionStore {
        var backing = ""
        return ExternalCapabilityPermissionStore(
            readJson = { backing },
            writeJson = { backing = it },
        )
    }

    private fun mcpTarget() = ExternalCapabilityTarget(
        kind = ExternalCapabilityKind.MCP_SERVER,
        endpoint = "http://localhost:8080",
    )
}
