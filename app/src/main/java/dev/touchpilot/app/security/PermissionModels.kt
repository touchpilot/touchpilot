package dev.touchpilot.app.security

/**
 * Trust surfaces called out in issue #378. Kept separate from [ToolSource] so
 * Android tools, skills, and external capabilities can be reviewed independently.
 */
enum class PermissionCategory(val label: String, val auditSource: String) {
    ANDROID_TOOL("Android tool", "android_tool"),
    SKILL("Skill", "skill"),
    LOCAL_EXTENSION("Local extension", "local_extension"),
    MCP_SERVER("MCP server", "mcp"),
}

enum class PermissionChangeKind(val auditName: String) {
    GRANT("grant"),
    REVOKE("revoke"),
    ENABLE("enable"),
    DISABLE("disable"),
}

data class PermissionGrantEntry(
    val category: PermissionCategory,
    val label: String,
    val allows: String,
    val grantedBy: String,
    val revocable: Boolean,
)
