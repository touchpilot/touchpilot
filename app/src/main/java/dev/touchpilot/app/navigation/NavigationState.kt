package dev.touchpilot.app.navigation

import dev.touchpilot.app.ui.settings.SettingsPanel

data class NavigationState(
    val activeSection: AppSection = AppSection.CHAT,
    val activeSettingsPanel: SettingsPanel? = null,
    val activeRunDetailId: String? = null,
    val selectedSkillId: String? = null,
    val pendingSettingsAnimationDirection: Int = 0
) {
    /**
     * Returns a copy with the active section changed.
     * Clears run detail when switching to non-CHAT/LOGS sections.
     */
    fun withSection(section: AppSection): NavigationState {
        val newRunDetailId = if (section != AppSection.CHAT && section != AppSection.LOGS) {
            null
        } else {
            activeRunDetailId
        }
        return copy(activeSection = section, activeRunDetailId = newRunDetailId)
    }

    /**
     * Returns a copy with the active settings panel changed.
     */
    fun withSettingsPanel(panel: SettingsPanel?): NavigationState {
        return copy(activeSettingsPanel = panel)
    }

    /**
     * Returns a copy with the active run detail ID changed.
     */
    fun withRunDetailId(runDetailId: String?): NavigationState {
        return copy(activeRunDetailId = runDetailId)
    }

    /**
     * Returns a copy with the selected skill changed.
     */
    fun withSelectedSkill(skillId: String?): NavigationState {
        return copy(selectedSkillId = skillId)
    }

    /**
     * Returns a copy with the pending animation direction.
     */
    fun withPendingAnimationDirection(direction: Int): NavigationState {
        return copy(pendingSettingsAnimationDirection = direction)
    }

    /**
     * Returns a copy with animation direction cleared.
     */
    fun clearAnimationDirection(): NavigationState {
        return copy(pendingSettingsAnimationDirection = 0)
    }
}
