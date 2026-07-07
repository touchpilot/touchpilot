package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolExecutionLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-first grant store for MCP servers and local extensions.
 * Nothing is permitted until the user explicitly grants capabilities (default deny).
 */
class ExternalCapabilityPermissionStore(
    private val readJson: () -> String,
    private val writeJson: (String) -> Unit,
) {
    fun allGrants(): List<ExternalCapabilityPermissionGrant> {
        val raw = readJson().trim()
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                parseGrant(obj)?.let { add(it) }
            }
        }
    }

    fun findGrant(target: ExternalCapabilityTarget): ExternalCapabilityPermissionGrant? {
        return allGrants().firstOrNull { it.target.id == target.id }
    }

    fun grant(
        target: ExternalCapabilityTarget,
        actions: Set<ExternalCapabilityAction>,
        featureFlags: Set<String> = emptySet(),
    ): ExternalCapabilityPermissionGrant {
        val grants = allGrants().toMutableList()
        grants.removeAll { it.target.id == target.id }
        val grant = ExternalCapabilityPermissionGrant(
            target = target,
            allowedActions = actions,
            grantedFeatureFlags = featureFlags,
        )
        grants += grant
        save(grants)
        ToolExecutionLog.recordPermissionChange(
            category = permissionCategory(target),
            change = PermissionChangeKind.GRANT,
            targetLabel = target.displayLabel(),
            details = buildString {
                append("actions=${actions.joinToString { it.name }}")
                if (featureFlags.isNotEmpty()) {
                    append("; feature_flags=${featureFlags.sorted().joinToString()}")
                }
            },
        )
        return grant
    }

    fun revoke(target: ExternalCapabilityTarget): Boolean {
        val grants = allGrants()
        if (grants.none { it.target.id == target.id }) return false
        save(grants.filterNot { it.target.id == target.id })
        ToolExecutionLog.recordPermissionChange(
            category = permissionCategory(target),
            change = PermissionChangeKind.REVOKE,
            targetLabel = target.displayLabel(),
        )
        return true
    }

    fun revokeAllForEndpoint(endpoint: String): Int {
        val normalized = endpoint.trim()
        val grants = allGrants()
        val remaining = grants.filterNot {
            it.target.endpoint.trim() == normalized
        }
        val removed = grants.size - remaining.size
        if (removed > 0) save(remaining)
        return removed
    }

    private fun permissionCategory(target: ExternalCapabilityTarget): PermissionCategory {
        return when (target.kind) {
            ExternalCapabilityKind.MCP_SERVER -> PermissionCategory.MCP_SERVER
            ExternalCapabilityKind.LOCAL_EXTENSION -> PermissionCategory.LOCAL_EXTENSION
        }
    }

    private fun save(grants: List<ExternalCapabilityPermissionGrant>) {
        val array = JSONArray()
        grants.forEach { grant ->
            array.put(
                JSONObject().apply {
                    put("kind", grant.target.kind.name)
                    put("endpoint", grant.target.endpoint)
                    put("name", grant.target.name)
                    put(
                        "actions",
                        JSONArray().apply {
                            grant.allowedActions.forEach { put(it.name) }
                        }
                    )
                    put(
                        "feature_flags",
                        JSONArray().apply {
                            grant.grantedFeatureFlags.forEach { put(it) }
                        }
                    )
                }
            )
        }
        writeJson(array.toString())
    }

    private fun parseGrant(obj: JSONObject): ExternalCapabilityPermissionGrant? {
        val kind = runCatching {
            ExternalCapabilityKind.valueOf(obj.optString("kind"))
        }.getOrNull() ?: return null
        val endpoint = obj.optString("endpoint").trim()
        if (endpoint.isBlank()) return null
        val name = obj.optString("name").trim()
        val actions = mutableSetOf<ExternalCapabilityAction>()
        obj.optJSONArray("actions")?.let { array ->
            for (index in 0 until array.length()) {
                runCatching {
                    ExternalCapabilityAction.valueOf(array.optString(index))
                }.getOrNull()?.let { actions += it }
            }
        }
        val flags = mutableSetOf<String>()
        obj.optJSONArray("feature_flags")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let { flags += it }
            }
        }
        return ExternalCapabilityPermissionGrant(
            target = ExternalCapabilityTarget(kind = kind, endpoint = endpoint, name = name),
            allowedActions = actions,
            grantedFeatureFlags = flags,
        )
    }
}
