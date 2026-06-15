package dev.touchpilot.app.agent

import dev.touchpilot.app.memory.SkillRisk

enum class SkillActivationSource(val label: String, val wireName: String) {
    MANUAL("Manually active", "manual"),
    MATCHED("Matched from request", "matched");

    companion object {
        fun fromWire(value: String): SkillActivationSource {
            return entries.firstOrNull { it.wireName == value } ?: MANUAL
        }
    }
}

data class SkillUseCardModel(
    val skillId: String,
    val title: String,
    val risk: SkillRisk,
    val allowedTools: Set<String>,
    val activationSource: SkillActivationSource,
    val reason: String,
) {
    val allowedToolsSummary: String
        get() = formatAllowedToolsSummary(allowedTools)

    companion object {
        fun from(event: AgentEvent.SkillActive): SkillUseCardModel {
            return SkillUseCardModel(
                skillId = event.skillId,
                title = event.title,
                risk = event.risk,
                allowedTools = event.allowedTools,
                activationSource = event.activationSource,
                reason = event.reason,
            )
        }

        fun formatAllowedToolsSummary(tools: Set<String>): String {
            if (tools.isEmpty()) return "No tools allowed"
            val sorted = tools.sorted()
            return if (sorted.size <= 3) {
                sorted.joinToString(", ")
            } else {
                "${sorted.size} tools: ${sorted.take(3).joinToString(", ")}…"
            }
        }
    }
}
