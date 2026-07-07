package dev.touchpilot.app.security

import dev.touchpilot.app.tools.AndroidToolCatalog
import dev.touchpilot.app.tools.ToolExecutionLog
import dev.touchpilot.app.tools.ToolRisk
import org.json.JSONArray

/**
 * Local-first revocation store for built-in Android tools. Tools are allowed by
 * default (accessibility + policy); the user can revoke individual tools and
 * re-enable them from Settings → Permissions.
 */
class AndroidToolPermissionStore(
    private val readJson: () -> String,
    private val writeJson: (String) -> Unit,
) {
    companion object {
        @Volatile
        private var active: AndroidToolPermissionStore? = null

        fun isToolAllowed(toolName: String): Boolean {
            return active?.isAllowed(toolName) ?: true
        }

        fun displayLabel(toolName: String): String {
            val normalized = toolName.trim().replace('_', ' ')
            if (normalized.isBlank()) return ""
            return normalized.split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase() else char.toString()
                    }
                }
        }
    }

    init {
        active = this
    }

    fun revokedTools(): Set<String> {
        val raw = readJson().trim()
        if (raw.isBlank()) return emptySet()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptySet()
        return buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    fun isAllowed(toolName: String): Boolean = toolName.trim() !in revokedTools()

    fun revoke(toolName: String) {
        val normalized = toolName.trim()
        if (normalized.isBlank()) return
        val revoked = revokedTools().toMutableSet()
        if (!revoked.add(normalized)) return
        persist(revoked)
        ToolExecutionLog.recordPermissionChange(
            category = PermissionCategory.ANDROID_TOOL,
            change = PermissionChangeKind.REVOKE,
            targetLabel = displayLabel(normalized),
            details = "tool=$normalized",
        )
    }

    fun grant(toolName: String) {
        val normalized = toolName.trim()
        if (normalized.isBlank()) return
        val revoked = revokedTools().toMutableSet()
        if (!revoked.remove(normalized)) return
        persist(revoked)
        ToolExecutionLog.recordPermissionChange(
            category = PermissionCategory.ANDROID_TOOL,
            change = PermissionChangeKind.GRANT,
            targetLabel = displayLabel(normalized),
            details = "tool=$normalized",
        )
    }

    fun revocableTools() = AndroidToolCatalog.initialTools.filter { it.risk != ToolRisk.BLOCKED }

    private fun persist(revoked: Set<String>) {
        val array = JSONArray()
        revoked.sorted().forEach { array.put(it) }
        writeJson(array.toString())
    }
}
