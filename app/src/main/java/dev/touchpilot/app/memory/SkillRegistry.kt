package dev.touchpilot.app.memory

/**
 * Local persistence for skill availability and active-skill selection. Kept as
 * an interface so [SkillRegistry] is pure and unit-testable without Android;
 * production wires it to SharedPreferences via [SharedPreferencesSkillStore].
 *
 * Disabled skills are stored as an explicit deny-set, so a newly bundled skill
 * is enabled by default. "Fail closed" here means a *disabled* skill is never
 * matched, selected, or executed — not that skills default to off.
 */
interface SkillPreferences {
    fun disabledSkillIds(): Set<String>
    fun setDisabledSkillIds(ids: Set<String>)
    fun activeSkillId(): String?
    fun setActiveSkillId(id: String?)
}

/**
 * Owns the loaded skill pack plus its enabled/disabled and active-selection
 * state. All lookup and selection goes through here so disabled skills are
 * consistently excluded from matching, selection, and execution, and a stale
 * active selection (pointing at a disabled or removed skill) is cleared instead
 * of silently keeping scope.
 *
 * Lookups are deterministic: they scan the loaded skills in load order and
 * return the first match.
 */
class SkillRegistry(
    private val skills: List<Skill>,
    private val preferences: SkillPreferences
) {
    /** Every loaded skill, regardless of enabled state (for settings/detail UI). */
    fun allSkills(): List<Skill> = skills

    /** Only the skills available for matching, selection, and execution. */
    fun enabledSkills(): List<Skill> {
        val disabled = preferences.disabledSkillIds()
        return skills.filter { it.id !in disabled }
    }

    fun isEnabled(id: String): Boolean = id !in preferences.disabledSkillIds()

    fun setEnabled(id: String, enabled: Boolean) {
        val disabled = preferences.disabledSkillIds().toMutableSet()
        val changed = if (enabled) disabled.remove(id) else disabled.add(id)
        if (changed) {
            preferences.setDisabledSkillIds(disabled)
        }
        // Disabling the active skill must drop the active scope immediately.
        if (!enabled && preferences.activeSkillId() == id) {
            preferences.setActiveSkillId(null)
        }
    }

    fun findById(id: String): Skill? {
        val needle = id.trim().lowercase()
        if (needle.isBlank()) return null
        return enabledSkills().firstOrNull { it.id.trim().lowercase() == needle }
    }

    fun findByTitle(title: String): Skill? {
        val needle = title.trim().lowercase()
        if (needle.isBlank()) return null
        return enabledSkills().firstOrNull { it.title.trim().lowercase() == needle }
    }

    fun findByAlias(alias: String): Skill? {
        val needle = alias.trim().lowercase()
        if (needle.isBlank()) return null
        return enabledSkills().firstOrNull { skill ->
            skill.aliases.any { it.trim().lowercase() == needle }
        }
    }

    /** Resolves a reference against id, then title, then alias — enabled only. */
    fun resolve(reference: String): Skill? {
        return findById(reference) ?: findByTitle(reference) ?: findByAlias(reference)
    }

    /**
     * The active skill, or null if none is selected or the selection has become
     * stale. A stale selection (active id now disabled or missing) is cleared so
     * the agent never continues under scope the user can no longer see.
     */
    fun activeSkill(): Skill? {
        val id = preferences.activeSkillId() ?: return null
        val skill = findById(id)
        if (skill == null) {
            preferences.setActiveSkillId(null)
        }
        return skill
    }

    /**
     * Selects an active skill. Only an enabled, existing skill can be selected;
     * null clears the selection, and any invalid id fails closed to no scope.
     */
    fun setActiveSkill(id: String?) {
        if (id == null) {
            preferences.setActiveSkillId(null)
            return
        }
        // Persist the matched skill's canonical id so a case-variant input does
        // not store a non-canonical active id.
        preferences.setActiveSkillId(findById(id)?.id)
    }
}
