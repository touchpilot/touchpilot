package dev.touchpilot.app.mcp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class PluginApiManifestTest {
    @Test
    fun bundledRuntimeManifestMatchesSupportedApiVersion() {
        val json = JSONObject(readBundledManifest())

        assertEquals(PluginApiManifest.SUPPORTED_API_VERSION, json.getString("api_version"))
        assertTrue(json.getJSONArray("supported_feature_flags").length() > 0)
        assertTrue(json.getJSONArray("privileged_feature_flags").length() > 0)
    }

    @Test
    fun parseReadsRequiredFieldsAndFeatureFlags() {
        val manifest = PluginApiManifest.parse(
            """
            {
              "api_version": "1.0.0",
              "name": "weather",
              "description": "Local weather tools",
              "endpoint": "http://localhost:8080",
              "feature_flags": {
                "network_access": true
              }
            }
            """.trimIndent()
        )

        assertEquals("1.0.0", manifest.apiVersion)
        assertEquals("weather", manifest.name)
        assertEquals("Local weather tools", manifest.description)
        assertEquals("http://localhost:8080", manifest.endpoint)
        assertEquals(mapOf("network_access" to true), manifest.featureFlags)
        assertTrue(manifest.isValid)
    }

    @Test
    fun missingApiVersionIsInvalid() {
        val manifest = PluginApiManifest.parse(
            """
            {
              "name": "weather",
              "description": "Local weather tools",
              "endpoint": "http://localhost:8080",
              "feature_flags": { "network_access": true }
            }
            """.trimIndent()
        )

        assertFalse(manifest.isValid)
        assertTrue(manifest.validationErrors().any { it.contains("api_version") })
    }

    @Test
    fun blankRequiredFieldsAreInvalid() {
        val manifest = valid().copy(name = "", endpoint = "", apiVersion = "")

        assertFalse(manifest.isValid)
        assertTrue(manifest.validationErrors().any { it.contains("name") })
        assertTrue(manifest.validationErrors().any { it.contains("endpoint") })
        assertTrue(manifest.validationErrors().any { it.contains("api_version") })
    }

    @Test
    fun apiVersionMustBeValidSemver() {
        val manifest = valid().copy(apiVersion = "not-semver")

        assertFalse(manifest.isValid)
        assertTrue(manifest.validationErrors().any { it.contains("semver") })
    }

    @Test
    fun newerApiVersionIsIncompatible() {
        val manifest = valid().copy(apiVersion = "2.0.0")

        assertFalse(manifest.isValid)
        assertTrue(manifest.compatibilityErrors().any { it.contains("newer TouchPilot") })
        assertTrue(manifest.recommendedAction()?.contains("Update TouchPilot") == true)
    }

    @Test
    fun olderMajorApiVersionIsIncompatible() {
        val manifest = valid().copy(apiVersion = "0.9.0")

        assertFalse(manifest.isValid)
        assertTrue(manifest.compatibilityErrors().any { it.contains("deprecated") })
    }

    @Test
    fun unknownFeatureFlagsAreRejected() {
        val manifest = valid().copy(featureFlags = mapOf("mystery_power" to true))

        assertFalse(manifest.isValid)
        assertTrue(manifest.validationErrors().any { it.contains("unknown feature flag") })
    }

    @Test
    fun privilegedFeatureFlagsRequireExplicitOptIn() {
        val manifest = valid().copy(featureFlags = mapOf("privileged_host" to true))

        assertTrue(manifest.isValid)
        assertTrue(manifest.featureFlags["privileged_host"] == true)
    }

    @Test
    fun omittedFeatureFlagsDoNotSilentlyEnablePrivilegedCapabilities() {
        val manifest = valid()

        assertTrue(manifest.featureFlags["privileged_host"] != true)
        assertTrue(manifest.featureFlags["file_system"] != true)
    }

    private fun valid() = PluginApiManifest(
        apiVersion = PluginApiManifest.SUPPORTED_API_VERSION,
        name = "weather",
        description = "Local weather tools",
        endpoint = "http://localhost:8080",
        featureFlags = mapOf("network_access" to true),
    )

    private fun readBundledManifest(): String {
        val candidates = listOf(
            File("src/main/assets/extensions/plugin-api-manifest.json"),
            File("app/src/main/assets/extensions/plugin-api-manifest.json"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing bundled plugin-api-manifest.json")
        return file.readText()
    }
}
