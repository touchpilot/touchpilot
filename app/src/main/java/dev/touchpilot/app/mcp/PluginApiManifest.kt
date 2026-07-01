package dev.touchpilot.app.mcp

import org.json.JSONObject

/**
 * Static, local metadata for one registered local extension tool. Extension
 * authors declare the plugin API version, required identity fields, endpoint,
 * and explicit feature flags. Parsing is pure (no Android) so manifests can be
 * validated from unit tests and at registration time without fetching remote
 * metadata.
 */
data class PluginApiManifest(
    val apiVersion: String,
    val name: String,
    val description: String,
    val endpoint: String,
    val featureFlags: Map<String, Boolean>,
) {
    /** Problems with this manifest before runtime compatibility checks. */
    fun validationErrors(): List<String> {
        val errors = mutableListOf<String>()
        if (apiVersion.isBlank()) {
            errors += "api_version must not be blank"
        } else if (SemVer.parse(apiVersion) == null) {
            errors += "api_version '$apiVersion' must be valid semver (major.minor.patch)"
        }
        if (name.isBlank()) errors += "name must not be blank"
        if (endpoint.isBlank()) errors += "endpoint must not be blank"
        errors += featureFlagErrors()
        return errors
    }

    /** Whether this manifest is understood by the current TouchPilot build. */
    fun compatibilityErrors(): List<String> {
        val errors = mutableListOf<String>()
        val declared = SemVer.parse(apiVersion) ?: return errors
        val supported = SemVer.parse(SUPPORTED_API_VERSION)
            ?: error("SUPPORTED_API_VERSION must be valid semver")

        when {
            declared.major > supported.major -> {
                errors += "api_version $apiVersion requires a newer TouchPilot build (supported: $SUPPORTED_API_VERSION)"
            }
            declared.major < supported.major -> {
                errors += "api_version $apiVersion uses a deprecated plugin API major version (supported: $SUPPORTED_API_VERSION)"
            }
            declared > supported -> {
                errors += "api_version $apiVersion is newer than supported $SUPPORTED_API_VERSION"
            }
        }
        return errors
    }

    /** Actionable guidance when [compatibilityErrors] or [validationErrors] is non-empty. */
    fun recommendedAction(): String? {
        val declared = SemVer.parse(apiVersion) ?: return "Set api_version to $SUPPORTED_API_VERSION in the extension manifest."
        val supported = SemVer.parse(SUPPORTED_API_VERSION) ?: return null
        return when {
            declared.major > supported.major || declared > supported ->
                "Update TouchPilot or publish the extension for api_version $SUPPORTED_API_VERSION."
            declared.major < supported.major ->
                "Re-publish the extension manifest with api_version $SUPPORTED_API_VERSION."
            validationErrors().any { it.contains("feature flag") } ->
                "Declare only supported feature flags explicitly; privileged flags must be set to true to opt in."
            else -> null
        }
    }

    val isValid: Boolean get() = validationErrors().isEmpty() && compatibilityErrors().isEmpty()

    private fun featureFlagErrors(): List<String> {
        val errors = mutableListOf<String>()
        for ((flag, _) in featureFlags) {
            if (flag.isBlank()) {
                errors += "feature flag names must not be blank"
                continue
            }
            if (flag !in AllKnownFeatureFlags) {
                errors += "unknown feature flag '$flag' (supported: ${AllKnownFeatureFlags.sorted().joinToString()})"
            }
        }
        return errors
    }

    companion object {
        /** Highest plugin API version this build of TouchPilot understands. */
        const val SUPPORTED_API_VERSION = "1.0.0"

        val KnownFeatureFlags = setOf("network_access")
        val PrivilegedFeatureFlags = setOf("privileged_host", "file_system")
        val AllKnownFeatureFlags: Set<String> = KnownFeatureFlags + PrivilegedFeatureFlags

        fun parse(json: String): PluginApiManifest = parse(JSONObject(json))

        fun parse(obj: JSONObject): PluginApiManifest {
            val flags = mutableMapOf<String, Boolean>()
            obj.optJSONObject("feature_flags")?.let { featureFlags ->
                featureFlags.keys().forEach { key ->
                    flags[key] = featureFlags.optBoolean(key, false)
                }
            }
            return PluginApiManifest(
                apiVersion = obj.optString("api_version", SUPPORTED_API_VERSION),
                name = obj.optString("name"),
                description = obj.optString("description"),
                endpoint = obj.optString("endpoint"),
                featureFlags = flags,
            )
        }
    }
}

internal data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int {
        major.compareTo(other.major).takeIf { it != 0 }?.let { return it }
        minor.compareTo(other.minor).takeIf { it != 0 }?.let { return it }
        return patch.compareTo(other.patch)
    }

    companion object {
        private val Pattern = Regex("""^(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")

        fun parse(value: String): SemVer? {
            val match = Pattern.matchEntire(value.trim()) ?: return null
            return SemVer(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
            )
        }
    }
}
