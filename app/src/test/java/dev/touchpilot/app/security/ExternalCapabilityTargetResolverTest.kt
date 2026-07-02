package dev.touchpilot.app.security

import dev.touchpilot.app.mcp.LocalExtensionTool
import dev.touchpilot.app.mcp.PluginApiManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExternalCapabilityTargetResolverTest {
    @Test
    fun resolvesMcpServerWhenNoExtensionMatchesEndpoint() {
        val store = store()
        val policy = ExternalCapabilityPolicy(store)

        val invocation = ExternalCapabilityTargetResolver.resolve(
            endpoint = "http://localhost:8080",
            extensions = emptyList(),
            policy = policy,
            action = ExternalCapabilityAction.LIST_TOOLS,
            permissionStore = store,
        )

        val ready = assertIs<ExternalCapabilityInvocation.Ready>(invocation)
        assertEquals(ExternalCapabilityKind.MCP_SERVER, ready.target.kind)
        assertTrue(ready.requiredFeatureFlags.isEmpty())
    }

    @Test
    fun resolvesExtensionTargetAndRequiredFlagsForMatchingEndpoint() {
        val store = store()
        val policy = ExternalCapabilityPolicy(store)
        val extension = weatherExtension()

        val invocation = ExternalCapabilityTargetResolver.resolve(
            endpoint = "http://localhost:9090",
            extensions = listOf(extension),
            policy = policy,
            action = ExternalCapabilityAction.CALL_TOOL,
            permissionStore = store,
        )

        val ready = assertIs<ExternalCapabilityInvocation.Ready>(invocation)
        assertEquals(ExternalCapabilityKind.LOCAL_EXTENSION, ready.target.kind)
        assertEquals("weather", ready.target.name)
        assertEquals(setOf("network_access"), ready.requiredFeatureFlags)
    }

    @Test
    fun resolvesAmbiguousWhenMultipleExtensionsShareEndpointWithoutUniqueGrant() {
        val store = store()
        val policy = ExternalCapabilityPolicy(store)
        val extensions = listOf(
            weatherExtension(name = "weather"),
            weatherExtension(name = "forecast"),
        )

        val invocation = ExternalCapabilityTargetResolver.resolve(
            endpoint = "http://localhost:9090",
            extensions = extensions,
            policy = policy,
            action = ExternalCapabilityAction.LIST_TOOLS,
            permissionStore = store,
        )

        val ambiguous = assertIs<ExternalCapabilityInvocation.Ambiguous>(invocation)
        assertEquals(listOf("forecast", "weather"), ambiguous.extensionNames)
    }

    @Test
    fun resolvesUniqueGrantedExtensionAmongSharedEndpoint() {
        val store = store()
        val policy = ExternalCapabilityPolicy(store)
        val weather = weatherExtension(name = "weather")
        val forecast = weatherExtension(name = "forecast")
        store.grant(
            ExternalCapabilityTarget(
                kind = ExternalCapabilityKind.LOCAL_EXTENSION,
                endpoint = weather.endpoint,
                name = weather.name,
            ),
            setOf(ExternalCapabilityAction.LIST_TOOLS),
        )

        val invocation = ExternalCapabilityTargetResolver.resolve(
            endpoint = "http://localhost:9090",
            extensions = listOf(weather, forecast),
            policy = policy,
            action = ExternalCapabilityAction.LIST_TOOLS,
            permissionStore = store,
        )

        val ready = assertIs<ExternalCapabilityInvocation.Ready>(invocation)
        assertEquals("weather", ready.target.name)
    }

    private fun store(): ExternalCapabilityPermissionStore {
        var backing = ""
        return ExternalCapabilityPermissionStore(
            readJson = { backing },
            writeJson = { backing = it },
        )
    }

    private fun weatherExtension(name: String = "weather"): LocalExtensionTool {
        return LocalExtensionTool(
            PluginApiManifest(
                apiVersion = PluginApiManifest.SUPPORTED_API_VERSION,
                name = name,
                description = "Show weather",
                endpoint = "http://localhost:9090",
                featureFlags = mapOf("network_access" to true),
            )
        )
    }
}
