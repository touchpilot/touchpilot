package dev.touchpilot.app.ui.chat

import dev.touchpilot.app.memory.Skill
import dev.touchpilot.app.memory.SkillDetailFormatter

/**
 * Compact presentation of the active skill for the chat header, so a user can
 * always see which skill is scoping the current session (#386). Pure data with
 * a testable factory; risk styling reuses [SkillDetailFormatter] so the header
 * chip matches the Settings and Use surfaces.
 */
data class ActiveSkillHeader(
    val title: String,
    val riskLabel: String,
    val riskAccent: Boolean
) {
    companion object {
        fun from(skill: Skill): ActiveSkillHeader {
            val risk = SkillDetailFormatter.riskPresentation(skill.risk)
            return ActiveSkillHeader(
                title = skill.title.ifBlank { skill.id },
                riskLabel = risk.label,
                riskAccent = risk.accent
            )
        }
    }
}
