package dev.touchpilot.app.security

import dev.touchpilot.app.tools.ToolRisk
import dev.touchpilot.app.tools.ToolSpec

data class ToolApprovalRequest(
    val tool: ToolSpec,
    val args: Map<String, String>,
    val policy: PolicyDecision.RequireApproval
)

fun interface ToolApprovalProvider {
    fun approve(request: ToolApprovalRequest): Boolean
}

fun ToolSpec.requiresManualApproval(): Boolean {
    return risk == ToolRisk.MEDIUM || risk == ToolRisk.HIGH
}
