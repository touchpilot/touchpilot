package dev.touchpilot.app.memory

import android.content.SharedPreferences

/**
 * SharedPreferences-backed [SkillPreferences]. The active-skill key is the same
 * one MainActivity used before the registry, so existing selections persist.
 */
class SharedPreferencesSkillStore(
    private val preferences: SharedPreferences
) : SkillPreferences {
    override fun disabledSkillIds(): Set<String> {
        // getStringSet may return an internally-owned set; copy before use/mutation.
        return preferences.getStringSet(KEY_DISABLED, emptySet())?.toSet() ?: emptySet()
    }

    override fun setDisabledSkillIds(ids: Set<String>) {
        preferences.edit().putStringSet(KEY_DISABLED, HashSet(ids)).apply()
    }

    override fun activeSkillId(): String? = preferences.getString(KEY_ACTIVE, null)

    override fun setActiveSkillId(id: String?) {
        preferences.edit().apply {
            if (id == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, id)
        }.apply()
    }

    private companion object {
        const val KEY_DISABLED = "disabled_skill_ids"
        const val KEY_ACTIVE = "active_skill"
    }
}
