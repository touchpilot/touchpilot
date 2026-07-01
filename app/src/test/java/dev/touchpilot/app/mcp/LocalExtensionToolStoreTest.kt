package dev.touchpilot.app.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocalExtensionToolStoreTest {
    @Test
    fun storesAndReloadsToolsWithManifest() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        val result = store.add(validTool())

        assertIs<LocalExtensionParseResult.Valid>(result)
        assertTrue(backing.contains("api_version"))
        assertEquals(1, store.load().tools.size)
        assertEquals("weather", store.load().tools.first().name)
    }

    @Test
    fun rejectsIncompatibleManifestOnAdd() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        val result = store.add(
            LocalExtensionTool(
                PluginApiManifest(
                    apiVersion = "2.0.0",
                    name = "weather",
                    description = "Show weather",
                    endpoint = "http://localhost:8080",
                    featureFlags = mapOf("network_access" to true),
                )
            )
        )

        assertIs<LocalExtensionParseResult.Invalid>(result)
        assertEquals(0, store.load().tools.size)
        assertTrue(backing.isBlank())
    }

    @Test
    fun loadSeparatesValidAndInvalidEntries() {
        var backing = """
            [
              {
                "api_version": "1.0.0",
                "name": "weather",
                "description": "Show weather",
                "endpoint": "http://localhost:8080",
                "feature_flags": { "network_access": true }
              },
              {
                "api_version": "2.0.0",
                "name": "broken",
                "description": "Too new",
                "endpoint": "http://localhost:9090",
                "feature_flags": { "network_access": true }
              }
            ]
        """.trimIndent()
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        val load = store.load()

        assertEquals(1, load.tools.size)
        assertEquals("weather", load.tools.first().name)
        assertEquals(1, load.invalid.size)
        assertEquals("broken", load.invalid.first().name)
        assertEquals("http://localhost:9090", load.invalid.first().endpoint)
    }

    @Test
    fun addValidDoesNotDropInvalidStoredEntries() {
        var backing = """
            [
              {
                "api_version": "2.0.0",
                "name": "broken",
                "description": "Too new",
                "endpoint": "http://localhost:9090",
                "feature_flags": { "network_access": true }
              }
            ]
        """.trimIndent()
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        val result = store.add(validTool())
        assertIs<LocalExtensionParseResult.Valid>(result)

        val load = store.load()
        assertEquals(1, load.tools.size)
        assertEquals(1, load.invalid.size)
        assertEquals("broken", load.invalid.first().name)
    }

    @Test
    fun removesIncompatibleStoredEntry() {
        var backing = """
            [
              {
                "api_version": "2.0.0",
                "name": "broken",
                "description": "Too new",
                "endpoint": "http://localhost:9090",
                "feature_flags": { "network_access": true }
              }
            ]
        """.trimIndent()
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        assertEquals(1, store.load().invalid.size)
        assertTrue(store.remove("broken", "http://localhost:9090"))
        assertEquals(0, store.load().invalid.size)
        assertTrue(backing.isBlank() || backing == "[]")
    }

    @Test
    fun rejectsLegacyEntryMissingApiVersion() {
        var backing = """
            [
              {
                "name": "legacy",
                "description": "Old format",
                "endpoint": "http://localhost:8080"
              }
            ]
        """.trimIndent()
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        val load = store.load()

        assertEquals(0, load.tools.size)
        assertEquals(1, load.invalid.size)
        assertTrue(load.invalid.first().errors.any { it.contains("api_version") })
    }

    @Test
    fun removesToolsByName() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        store.add(validTool())
        assertTrue(store.remove("weather", "http://localhost:8080"))
        assertEquals(0, store.load().tools.size)
    }

    @Test
    fun keepsSameNamedToolsOnDifferentEndpoints() {
        var backing = ""
        val store = LocalExtensionToolStore(
            readJson = { backing },
            writeJson = { backing = it },
        )

        store.add(validTool())
        store.add(
            LocalExtensionTool(
                PluginApiManifest(
                    apiVersion = PluginApiManifest.SUPPORTED_API_VERSION,
                    name = "weather",
                    description = "Remote",
                    endpoint = "http://localhost:9090",
                    featureFlags = mapOf("network_access" to true),
                )
            )
        )

        assertEquals(2, store.load().tools.size)
        assertTrue(store.remove("weather", "http://localhost:8080"))
        assertEquals(listOf("http://localhost:9090"), store.load().tools.map { it.endpoint })
    }

    private fun validTool() = LocalExtensionTool(
        PluginApiManifest(
            apiVersion = PluginApiManifest.SUPPORTED_API_VERSION,
            name = "weather",
            description = "Show weather",
            endpoint = "http://localhost:8080",
            featureFlags = mapOf("network_access" to true),
        )
    )
}
