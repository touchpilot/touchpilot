package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec

fun interface ToolApprovalProvider {
    fun approve(tool: ToolSpec, args: Map<String, String>): Boolean
}

fun ToolSpec.requiresManualApproval(): Boolean {
    return risk == ToolRisk.MEDIUM || risk == ToolRisk.HIGH
}
