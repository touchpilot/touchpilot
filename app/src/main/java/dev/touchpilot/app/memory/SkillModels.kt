package dev.touchpilot.app.memory

data class Skill(
    val id: String,
    val title: String,
    val markdown: String,
    val allowedTools: Set<String>
)

data class SkillRegistry(
    val installedSkills: List<Skill>,
    val disabledSkillIds: Set<String> = emptySet(),
    val activeSkillId: String? = null
) {
    val enabledSkills: List<Skill> = installedSkills.filter { isEnabled(it.id) }

    val activeSkill: Skill? = activeSkillId
        ?.let(::findInstalled)
        ?.takeIf { isEnabled(it.id) }

    fun isEnabled(skillId: String): Boolean {
        return findInstalled(skillId) != null && skillId !in disabledSkillIds
    }

    fun select(skillId: String?): SkillRegistry {
        val nextActiveSkillId = skillId?.takeIf(::isEnabled)
        return copy(activeSkillId = nextActiveSkillId)
    }

    fun setEnabled(skillId: String, enabled: Boolean): SkillRegistry {
        if (findInstalled(skillId) == null) return this
        val nextDisabled = if (enabled) {
            disabledSkillIds - skillId
        } else {
            disabledSkillIds + skillId
        }
        val nextActive = activeSkillId?.takeUnless { it in nextDisabled }
        return copy(
            disabledSkillIds = nextDisabled,
            activeSkillId = nextActive
        )
    }

    private fun findInstalled(skillId: String): Skill? {
        return installedSkills.firstOrNull { it.id == skillId }
    }
}
