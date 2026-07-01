package dev.touchpilot.app.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalCapabilityPermissionStoreTest {
    @Test
    fun defaultsToDenyWhenNoGrantExists() {
        val store = store()

        assertNull(
            store.findGrant(
                ExternalCapabilityTarget(
                    kind = ExternalCapabilityKind.MCP_SERVER,
                    endpoint = "http://localhost:8080",
                )
            )
        )
    }

    @Test
    fun grantsAndRevokesMcpServerPermissions() {
        val store = store()
        val target = ExternalCapabilityTarget(
            kind = ExternalCapabilityKind.MCP_SERVER,
            endpoint = "http://localhost:8080",
        )

        val grant = store.grant(
            target = target,
            actions = setOf(ExternalCapabilityAction.LIST_TOOLS, ExternalCapabilityAction.CALL_TOOL),
        )

        assertTrue(grant.allows(ExternalCapabilityAction.LIST_TOOLS))
        assertTrue(grant.allows(ExternalCapabilityAction.CALL_TOOL))
        assertNotNull(store.findGrant(target))
        assertTrue(store.revoke(target))
        assertNull(store.findGrant(target))
    }

    @Test
    fun storesExtensionGrantsWithFeatureFlagsSeparately() {
        val store = store()
        val target = ExternalCapabilityTarget(
            kind = ExternalCapabilityKind.LOCAL_EXTENSION,
            endpoint = "http://localhost:9090",
            name = "weather",
        )

        store.grant(
            target = target,
            actions = setOf(ExternalCapabilityAction.CALL_TOOL),
            featureFlags = setOf("network_access", "file_system"),
        )

        val grant = store.findGrant(target)
        assertNotNull(grant)
        assertFalse(grant.allows(ExternalCapabilityAction.LIST_TOOLS))
        assertTrue(grant.allows(ExternalCapabilityAction.CALL_TOOL))
        assertTrue(grant.allowsFeature("network_access"))
        assertTrue(grant.allowsFeature("file_system"))
    }

    @Test
    fun revokeAllForEndpointRemovesMatchingGrants() {
        val store = store()
        store.grant(
            ExternalCapabilityTarget(ExternalCapabilityKind.MCP_SERVER, "http://localhost:8080"),
            setOf(ExternalCapabilityAction.LIST_TOOLS),
        )
        store.grant(
            ExternalCapabilityTarget(
                ExternalCapabilityKind.LOCAL_EXTENSION,
                "http://localhost:8080",
                "weather",
            ),
            setOf(ExternalCapabilityAction.CALL_TOOL),
        )
        store.grant(
            ExternalCapabilityTarget(ExternalCapabilityKind.MCP_SERVER, "http://other:8080"),
            setOf(ExternalCapabilityAction.LIST_TOOLS),
        )

        assertEquals(2, store.revokeAllForEndpoint("http://localhost:8080"))
        assertEquals(1, store.allGrants().size)
    }

    private fun store(): ExternalCapabilityPermissionStore {
        var backing = ""
        return ExternalCapabilityPermissionStore(
            readJson = { backing },
            writeJson = { backing = it },
        )
    }
}
